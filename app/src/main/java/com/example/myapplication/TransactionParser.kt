package com.example.myapplication

/**
 * Holds the structured result of parsing a notification message.
 * All fields are nullable because not every notification contains payment info.
 */
data class ParsedTransaction(
    val amount: String?,     // e.g., "183", "1,460.00"
    val merchant: String?,   // e.g., "Uber India", "RAMESH FAST FOOD"
    val mode: String?        // e.g., "UPI", "Card", "Net Banking"
)

/**
 * Parses raw notification text and extracts structured payment data.
 *
 * HOW IT WORKS (read this carefully):
 * ────────────────────────────────────
 * Indian payment notifications follow common patterns. Here are real examples:
 *
 *   "₹183 paid to Uber India using UPI"
 *   "Payment of Rs.120.00 to RAMESH FAST FOOD via UPI"
 *   "Sent ₹500 to Amit Kumar on Google Pay"
 *   "INR 1,460.00 debited from A/c XX2341 to RELIANCE RETAIL"
 *   "Rs 247 paid to Zomato Ltd UPI Ref: 423456789"
 *   "You paid ₹89.00 to Rapido Bike Taxi"
 *   "Dear Customer, Rs.2500 has been debited from your account for UPI txn to SWIGGY"
 *   "Paid ₹350 for order on Swiggy"
 *   "Money sent! ₹200 to rahul@okaxis"
 *
 * STRATEGY:
 *   1) AMOUNT  → Look for ₹ / Rs / Rs. / INR followed by digits (with optional comma and decimals)
 *   2) MERCHANT → Look for "to <NAME>" or "for <NAME>" patterns
 *   3) MODE    → Look for keywords: UPI, NEFT, IMPS, Card, Net Banking, Google Pay, PhonePe, etc.
 */
object TransactionParser {

    // ──────────────────────────────────────────────────────────
    // REGEX 1: AMOUNT
    // ──────────────────────────────────────────────────────────
    // Matches (currency BEFORE number):
    //   ₹183  |  Rs.120.00  |  Rs 247  |  INR 1,460.00  |  Rs.2500
    // Matches (currency/keyword AFTER number):
    //   183 rupees  |  1,460.00 INR  |  200 Rs
    //
    // Two capture groups:
    //   Group 1 = number when currency symbol is BEFORE the amount
    //   Group 2 = number when currency keyword is AFTER the amount
    //
    private val amountRegex = Regex(
        """(?:₹|Rs\.?\s?|INR)\s*([\d,]+\.?\d*)|(\d[\d,]*\.?\d*)\s*(?:₹|Rs\.?|INR|rupees?)""",
        RegexOption.IGNORE_CASE
    )

    // ──────────────────────────────────────────────────────────
    // REGEX 2: MERCHANT (who you paid)
    // ──────────────────────────────────────────────────────────
    // Matches the name after "to" or "for" in phrases like:
    //   "paid to Uber India using UPI"
    //   "to RAMESH FAST FOOD via UPI"
    //   "for order on Swiggy"
    //   "to rahul@okaxis"
    //
    // Breakdown:
    //   (?:to|for)\s+      → "to " or "for " (with one or more spaces)
    //   (.+?)              → Capture the merchant name (lazy/non-greedy)
    //   (?:\s+(?:using|via|on|UPI|Ref|$)) → Stop capturing when we hit these keywords or end of string
    //
    // Why lazy (.+?) ?  Because greedy (.+) would eat "Uber India using UPI" entirely.
    // We want to STOP at "using" / "via" / "on" etc.
    //
    private val merchantRegex = Regex(
        """(?:paid |sent |debited .+?|payment .+?)?(?:to|for)\s+(.+?)(?:\s+(?:using|via|on|through|UPI|Ref|ref|$))""",
        RegexOption.IGNORE_CASE
    )

    // Bank SMS pattern: "for UPI txn to MERCHANT" or "UPI-MERCHANT" or "to VPA merchant@bank"
    // This catches the last "to MERCHANT" in bank SMS messages
    private val bankMerchantRegex = Regex(
        """(?:txn|transaction|transfer)\s+(?:to|for)\s+([A-Za-z][\w\s@.\-]+?)(?:\s+(?:on|Ref|ref|Avl|avl|\d{2}[-/])|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Fallback: Sometimes merchant name comes after "to" at the end of the string
    // e.g., "₹200 to Rahul Kumar"
    private val merchantFallbackRegex = Regex(
        """(?:to|for)\s+([A-Za-z@][\w\s@.]+)$""",
        RegexOption.IGNORE_CASE
    )

    // ──────────────────────────────────────────────────────────
    // REGEX 3: PAYMENT MODE
    // ──────────────────────────────────────────────────────────
    // Simple keyword search. We look for any of these words in the text:
    //
    private val modeRegex = Regex(
        """(UPI|NEFT|IMPS|RTGS|Net Banking|Debit Card|Credit Card|Card|Google Pay|GPay|PhonePe|Paytm|Amazon Pay)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Main function: takes raw notification text, returns structured data.
     * If nothing is found, fields will be null.
     */
    fun parse(text: String): ParsedTransaction {
        // --- EXTRACT AMOUNT ---
        val amountMatch = amountRegex.find(text)
        // Group 1 = number when currency is BEFORE (₹183)
        // Group 2 = number when currency is AFTER  (183 rupees)
        val amount = if (amountMatch != null) {
            amountMatch.groupValues[1].takeIf { it.isNotBlank() }
                ?: amountMatch.groupValues[2].takeIf { it.isNotBlank() }
        } else null

        // --- EXTRACT MERCHANT ---
        val merchantMatch = merchantRegex.find(text)
        var merchant = merchantMatch?.groupValues?.get(1)?.trim()

        // If primary regex caught "UPI txn to SWIGGY" → merchant = "UPI txn to SWIGGY"
        // Check if it contains "txn to" and re-extract just the merchant
        if (merchant != null && merchant.contains(Regex("""(?:txn|transaction)\s+to\s""", RegexOption.IGNORE_CASE))) {
            val bankMatch = bankMerchantRegex.find(text)
            if (bankMatch != null) {
                merchant = bankMatch.groupValues[1].trim()
            }
        }

        // If primary regex didn't find anything, try bank SMS pattern, then fallback
        if (merchant.isNullOrBlank()) {
            val bankMatch = bankMerchantRegex.find(text)
            merchant = bankMatch?.groupValues?.get(1)?.trim()
        }
        if (merchant.isNullOrBlank()) {
            val fallback = merchantFallbackRegex.find(text)
            merchant = fallback?.groupValues?.get(1)?.trim()
        }

        // Clean up merchant: remove trailing punctuation and common noise words
        merchant = merchant
            ?.replace(Regex("""[.\-,;:!]+$"""), "")  // strip trailing punctuation
            ?.replace(Regex("""\s+"""), " ")           // collapse multiple spaces
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        // --- EXTRACT MODE ---
        val modeMatch = modeRegex.find(text)
        val mode = modeMatch?.groupValues?.get(1)

        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            mode = mode
        )
    }
}

