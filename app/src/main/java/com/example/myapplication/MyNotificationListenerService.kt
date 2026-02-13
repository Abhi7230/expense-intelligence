package com.example.myapplication

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MyNotificationListenerService : NotificationListenerService() {

    private val TAG = "NOTIF_LISTENER"

    // Track recently processed notification keys to avoid duplicates
    // (Android can re-post notifications when they're updated)
    private val recentNotificationKeys = LinkedHashSet<String>()
    private val MAX_RECENT_KEYS = 100  // keep last 100 keys in memory

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName

        // ── FILTER 1: Skip our own notifications (foreground service notification) ──
        if (packageName == applicationContext.packageName) return

        // ── FILTER 2: Skip system UI & keyboard notifications ──
        if (packageName == "com.android.systemui" ||
            packageName.contains("inputmethod", ignoreCase = true)) return

        // ── FILTER 3: Deduplicate — skip if we already processed this exact notification ──
        val notifKey = "${sbn.key}_${sbn.postTime}"
        if (recentNotificationKeys.contains(notifKey)) {
            Log.d(TAG, "⏭️ Skipping duplicate notification: $notifKey")
            return
        }
        recentNotificationKeys.add(notifKey)
        // Trim to prevent unbounded memory growth
        if (recentNotificationKeys.size > MAX_RECENT_KEYS) {
            recentNotificationKeys.remove(recentNotificationKeys.first())
        }

        val extras = sbn.notification.extras
        val title = extras?.getString("android.title") ?: "No Title"
        val text = extras?.getCharSequence("android.text")?.toString() ?: "No Text"
        val time = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            .format(Date(sbn.postTime))

        // ── FILTER 4: Skip empty/useless notifications ──
        if (text == "No Text" || text.isBlank()) return

        // ── FILTER 5: WHITELIST — only process real payment messages ──
        // Instead of blacklisting promo keywords (endless), we WHITELIST:
        // A message must contain an amount AND a payment verb to be a transaction.
        //   "Get ₹201 off"         → has amount, no payment verb → SKIP (promo)
        //   "₹183 paid to Uber"    → has amount + "paid"         → KEEP (expense)
        //   "Rs.183 debited"       → has amount + "debited"      → KEEP (expense)
        //   "Rs.5000 credited"     → has amount + "credited"     → SKIP (income, not expense)
        //
        // NOTE: "credited" and "received" are intentionally EXCLUDED.
        //   Debited = money LEFT your account (expense ✅)
        //   Credited = money CAME INTO your account (income ❌)
        val textLower = text.lowercase()
        val hasAmount = Regex(
            """(?:₹|rs\.?\s?|inr)\s*[\d,]+|[\d,]+\s*(?:₹|rs\.?|inr|rupees?)""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        if (hasAmount) {
            val paymentVerbs = listOf(
                "paid", "sent", "debited", "transferred",
                "payment successful", "payment of",
                "transaction", "txn", "withdrawn",
                "charged", "deducted", "money sent"
            )
            val hasPaymentVerb = paymentVerbs.any { textLower.contains(it) }

            if (!hasPaymentVerb) {
                // UNCERTAIN CASE: has an amount (₹/Rs) but no clear payment verb.
                // Could be a promo ("Get ₹201 off") or a real payment ("₹150 for Swiggy order").
                // Ask AI to verify before skipping.
                Log.d(TAG, "🤔 Uncertain message — asking AI to verify: ${text.take(80)}...")
                val isRealPayment = try {
                    AiInsightEngine.isRealPayment(text)
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ AI verify error: ${e.message} — skipping to be safe")
                    false
                }
                if (!isRealPayment) {
                    Log.d(TAG, "⏭️ AI confirmed: NOT a payment — skipping")
                    return
                }
                Log.d(TAG, "✅ AI confirmed: IS a real payment — keeping")
            }
        }

        // ── FILTER 6: Smart dedup for bank debit SMS ──
        //
        // WHY SMART DEDUP?
        // When you pay via GPay, you often get TWO notifications:
        //   1. GPay: "₹10 paid to Aayush Raj"  ← has merchant name, we want this
        //   2. Bank SMS: "a/c XX1234 debited Rs.10"  ← duplicate
        //
        // BUT sometimes GPay sends a SILENT confirmation (no notification).
        // In that case the bank SMS is the ONLY signal we have. If we blindly
        // skip all bank SMS, we miss those payments entirely.
        //
        // SOLUTION: Check if we already captured a payment with the SAME amount
        // in the last 3 minutes. If yes → skip (duplicate). If no → keep (only signal).
        val isBankDebitSms = textLower.contains("debited") &&
                (textLower.contains("a/c") || textLower.contains("acct") ||
                 textLower.contains("account") || textLower.contains("ending") ||
                 textLower.contains("bank") || textLower.contains("balance"))

        if (isBankDebitSms) {
            val quickParsed = TransactionParser.parse(text)
            if (quickParsed.amount != null) {
                try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val threeMinAgo = sbn.postTime - (3 * 60 * 1000L)
                    val existing = db.notificationDao().findRecentByAmount(quickParsed.amount, threeMinAgo)
                    if (existing != null) {
                        Log.d(TAG, "⏭️ Smart dedup: Bank SMS ₹${quickParsed.amount} already captured from ${existing.app}")
                        return
                    } else {
                        Log.d(TAG, "✅ Bank SMS ₹${quickParsed.amount} is the ONLY signal — keeping it")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Dedup check failed, keeping notification: ${e.message}")
                }
            }
        }

        Log.d(TAG, "=== NOTIFICATION CAPTURED ===")
        Log.d(TAG, "App:   $packageName")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Text:  $text")
        Log.d(TAG, "Time:  $time")
        Log.d(TAG, "============================")

        // Step 5: Parse
        val parsed = TransactionParser.parse(text)

        Log.d(TAG, "--- PARSED RESULT ---")
        Log.d(TAG, "Amount:   ${parsed.amount ?: "not found"}")
        Log.d(TAG, "Merchant: ${parsed.merchant ?: "not found"}")
        Log.d(TAG, "Mode:     ${parsed.mode ?: "not found"}")
        Log.d(TAG, "---------------------")

        // Save + correlate + AI (all on background thread)
        thread {
            try {
                val db = AppDatabase.getDatabase(applicationContext)

                // Save the notification
                val entity = NotificationEntity(
                    app = packageName,
                    title = title,
                    text = text,
                    timestamp = sbn.postTime,
                    amount = parsed.amount,
                    merchant = parsed.merchant,
                    mode = parsed.mode
                )
                val insertedId = db.notificationDao().insertNotification(entity)

                Log.d(TAG, "✅ Saved to DB (id=$insertedId)")

                // ── Step 7 + 8: If this is a payment → correlate → AI ──
                if (parsed.amount != null) {
                    Log.d(TAG, "💰 Payment detected! Running correlation...")

                    // Use the insert ID directly — no need to re-query all transactions
                    val savedEntity = db.notificationDao().getById(insertedId.toInt())

                    if (savedEntity != null) {
                        // ── NEW: Check if merchant is already learned ──
                        val normalizedMerchant = (parsed.merchant ?: "").lowercase().trim()
                        val learnedAlias = if (normalizedMerchant.isNotBlank()) {
                            db.merchantAliasDao().findByName(normalizedMerchant)
                        } else null

                        if (learnedAlias != null) {
                            // Auto-apply learned category — skip correlation!
                            Log.d(TAG, "🧠 Using learned category for '$normalizedMerchant': ${learnedAlias.category}")
                            val finalCategory = learnedAlias.subcategory ?: learnedAlias.category
                            db.notificationDao().updateCorrelation(
                                id = savedEntity.id,
                                category = finalCategory,
                                correlatedApp = null,
                                confidence = "learned"
                            )
                            db.merchantAliasDao().incrementUsage(learnedAlias.id)

                            // If the learned alias has a note, use it as AI insight
                            if (!learnedAlias.userNote.isNullOrBlank()) {
                                db.notificationDao().updateAiInsight(savedEntity.id, learnedAlias.userNote)
                            }
                            return@thread
                        }

                        // Step 7: Correlate
                        val windowStart = sbn.postTime - (10 * 60 * 1000L)
                        val recentUsages = db.appUsageDao().getUsageInWindow(windowStart, sbn.postTime).toMutableList()

                        // ── CRITICAL FIX: Capture the CURRENTLY active app ──
                        // The foreground service only saves a session when the user
                        // SWITCHES AWAY from an app. So if GPay is still on screen
                        // when the notification arrives, it's NOT in the DB yet.
                        // We query UsageStatsManager directly to find what's on screen
                        // right now, and inject a synthetic entry if it's missing.
                        val currentApp = getCurrentForegroundApp()
                        if (currentApp != null) {
                            val alreadyInList = recentUsages.any { it.app == currentApp }
                            if (!alreadyInList) {
                                Log.d(TAG, "📱 Injecting current foreground app: $currentApp")
                                recentUsages.add(
                                    AppUsageEntity(
                                        app = currentApp,
                                        startTime = sbn.postTime - 30_000, // assume opened ~30s ago
                                        endTime = sbn.postTime,            // still open now
                                        duration = 30_000                  // 30 seconds
                                    )
                                )
                            }
                        }

                        val result = CorrelationEngine.correlate(savedEntity, recentUsages)

                        // ── Check popup settings ──
                        val prefs = applicationContext.getSharedPreferences("expense_intelligence_prefs", android.content.Context.MODE_PRIVATE)
                        val popupMode = prefs.getString("popup_mode", "smart") ?: "smart"
                        
                        // Determine if we should show popup
                        // Include "Unknown" category and our own app being in foreground (user was in our app)
                        val isOurOwnApp = result.correlatedApp?.contains("myapplication", ignoreCase = true) == true
                        val isUnknownMerchant = result.correlatedApp == null || 
                                                isOurOwnApp ||
                                                result.category.contains("Offline", ignoreCase = true) ||
                                                result.category.contains("Payment App", ignoreCase = true) ||
                                                result.category.contains("Unknown", ignoreCase = true) ||
                                                result.confidence == "low"
                        
                        val shouldShowPopup = when (popupMode) {
                            "all" -> true  // Show popup for ALL payments
                            else -> isUnknownMerchant  // Smart mode: only for unknown merchants
                        }

                        if (shouldShowPopup) {
                            Log.d(TAG, "🔔 Launching category popup (mode: $popupMode, unknown: $isUnknownMerchant)")

                            // Save with temporary category (user will update via popup)
                            db.notificationDao().updateCorrelation(
                                id = savedEntity.id,
                                category = result.category,
                                correlatedApp = result.correlatedApp,
                                confidence = if (isUnknownMerchant) "low" else result.confidence
                            )

                            // Check if we have permission to display over other apps
                            val canDrawOverlays = android.provider.Settings.canDrawOverlays(applicationContext)
                            
                            if (canDrawOverlays) {
                                // Launch the category popup
                                val popupIntent = Intent(applicationContext, CategoryPopupActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                    putExtra(CategoryPopupActivity.EXTRA_TRANSACTION_ID, savedEntity.id)
                                    putExtra(CategoryPopupActivity.EXTRA_AMOUNT, parsed.amount)
                                    putExtra(CategoryPopupActivity.EXTRA_MERCHANT, parsed.merchant)
                                    putExtra(CategoryPopupActivity.EXTRA_TIMESTAMP, sbn.postTime)
                                }
                                applicationContext.startActivity(popupIntent)
                            } else {
                                // Fallback: Show a notification with action buttons instead
                                Log.d(TAG, "⚠️ No overlay permission — showing notification instead")
                                showCategoryNotification(
                                    transactionId = savedEntity.id,
                                    amount = parsed.amount ?: "?",
                                    merchant = parsed.merchant ?: "Unknown"
                                )
                            }
                            return@thread
                        }

                        db.notificationDao().updateCorrelation(
                            id = savedEntity.id,
                            category = result.category,
                            correlatedApp = result.correlatedApp,
                            confidence = result.confidence
                        )

                        Log.d(TAG, "🧠 CORRELATION COMPLETE:")
                        Log.d(TAG, "   Category: ${result.category}")
                        Log.d(TAG, "   App:      ${result.correlatedApp}")
                        Log.d(TAG, "   Confidence: ${result.confidence}")

                        // Step 8: Ask AI for a "digital memory" description
                        Log.d(TAG, "🤖 Generating AI insight...")
                        val aiResult = AiInsightEngine.generateInsight(
                            transaction = savedEntity,
                            correlationCategory = result.category,
                            correlatedApp = result.correlatedApp,
                            confidence = result.confidence
                        )

                        if (aiResult != null) {
                            db.notificationDao().updateAiInsight(
                                id = savedEntity.id,
                                aiInsight = aiResult.description
                            )

                            Log.d(TAG, "✨ AI INSIGHT SAVED:")
                            Log.d(TAG, "   Description: ${aiResult.description}")
                            Log.d(TAG, "   Subcategory: ${aiResult.subcategory ?: "—"}")
                            Log.d(TAG, "   Necessity:   ${aiResult.necessity ?: "—"}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed: ${e.message}")
            }
        }
    }

    /**
     * Asks Android: "Which app is currently in the foreground (on screen)?"
     * Same logic as MyForegroundService.getCurrentForegroundApp()
     */
    @Suppress("DEPRECATION")
    private fun getCurrentForegroundApp(): String? {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000  // last 10 seconds
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var foregroundApp: String? = null
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.packageName
            }
        }
        return foregroundApp
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        Log.d(TAG, "Notification removed from: ${sbn.packageName}")
    }

    /**
     * Fallback when we can't show the popup overlay.
     * Shows a notification that user can tap to categorize.
     */
    private fun showCategoryNotification(transactionId: Int, amount: String, merchant: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Create notification channel if needed
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "category_prompt",
                    "Category Prompts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Prompts to categorize payments"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // Intent to open the popup activity
            val popupIntent = Intent(applicationContext, CategoryPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CategoryPopupActivity.EXTRA_TRANSACTION_ID, transactionId)
                putExtra(CategoryPopupActivity.EXTRA_AMOUNT, amount)
                putExtra(CategoryPopupActivity.EXTRA_MERCHANT, merchant)
                putExtra(CategoryPopupActivity.EXTRA_TIMESTAMP, System.currentTimeMillis())
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext,
                transactionId,
                popupIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification
            val notification = android.app.Notification.Builder(applicationContext, "category_prompt")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("💸 ₹$amount to $merchant")
                .setContentText("Tap to categorize this payment")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            notificationManager.notify(transactionId, notification)
            Log.d(TAG, "📬 Category prompt notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show category notification: ${e.message}")
        }
    }
}
