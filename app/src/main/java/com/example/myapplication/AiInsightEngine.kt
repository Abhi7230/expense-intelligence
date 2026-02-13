package com.example.myapplication

import android.util.Log
import com.example.myapplication.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Calls Groq AI to generate a human-readable "digital memory"
 * from a payment transaction.
 *
 * WHY GROQ?
 * - Free tier: 30 requests/minute, 14,400 requests/day
 * - Super fast (runs on custom LPU hardware)
 * - Uses OpenAI-compatible API format (industry standard)
 *
 * HOW IT WORKS:
 * 1. We build a prompt describing the payment
 * 2. Send it to Groq via HTTP POST (OpenAI-compatible format)
 * 3. Groq returns a JSON response with the AI's message
 * 4. We parse it and return an AiInsight object
 */
object AiInsightEngine {

    private val TAG = "AI_INSIGHT"

    // API key injected via BuildConfig from local.properties (never in source code)
    private val GROQ_API_KEY: String = BuildConfig.GROQ_API_KEY

    // Groq API endpoint (OpenAI-compatible)
    private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

    // Model to use — llama-3.3-70b-versatile is powerful and free
    private const val MODEL = "llama-3.3-70b-versatile"

    /**
     * The result from the AI.
     */
    data class AiInsight(
        val description: String,    // "Late evening street food dinner from a local stall"
        val subcategory: String?,   // "Street Food", "Cab Ride", "Online Shopping"
        val necessity: String?      // "want" or "need"
    )

    /**
     * LIGHTWEIGHT AI CHECK: Is this notification a real payment?
     *
     * Used for "uncertain" messages — ones that contain an amount (₹/Rs)
     * but DON'T have a clear payment verb like "paid" or "debited".
     *
     * Example uncertain messages:
     *   "Get ₹201 off on purchase"     → AI should say: NO (promo)
     *   "₹150 for your Swiggy order"   → AI should say: YES (payment)
     *   "Save ₹500 on your next ride"  → AI should say: NO (ad)
     *
     * Uses the smaller, faster llama-3.1-8b-instant model to minimize latency.
     * Returns true if AI thinks it's a real payment, false otherwise.
     * Falls back to false (skip) on any error.
     */
    fun isRealPayment(notificationText: String): Boolean {
        if (GROQ_API_KEY.isBlank() || GROQ_API_KEY == "PASTE_YOUR_GROQ_KEY_HERE") {
            Log.w(TAG, "⚠️ No API key — can't verify, skipping uncertain notification")
            return false
        }

        val prompt = """
You are a payment detection system. Given a phone notification message, determine if it describes an ACTUAL payment/expense (money leaving the user's account) or NOT (advertisement, offer, cashback, promotional message, income).

Notification: "$notificationText"

Rules:
- "paid", "sent", "debited", "charged" = REAL payment → YES
- "off", "cashback", "offer", "discount", "earn", "win", "reward" = NOT a payment → NO
- "credited", "received" = income, NOT expense → NO
- If unsure, say NO

Respond with ONLY one word: YES or NO
        """.trim()

        return try {
            Log.d(TAG, "🔍 AI verifying uncertain notification: ${notificationText.take(60)}...")
            val url = URL(GROQ_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val body = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")  // smaller, faster model for quick checks
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.0)   // deterministic — we want consistent YES/NO
                put("max_tokens", 5)      // only need "YES" or "NO"
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            if (connection.responseCode != 200) {
                Log.e(TAG, "❌ AI verify failed: HTTP ${connection.responseCode}")
                return false
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
            val json = JSONObject(response)
            val answer = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .uppercase()

            val isPayment = answer.contains("YES")
            Log.d(TAG, "🔍 AI verdict: $answer → ${if (isPayment) "KEEP ✅" else "SKIP ❌"}")
            isPayment
        } catch (e: Exception) {
            Log.e(TAG, "❌ AI verification failed: ${e.message} — skipping to be safe")
            false
        }
    }

    /**
     * Main function: generates an AI insight for a payment.
     * Returns null if API key is missing or call fails.
     */
    fun generateInsight(
        transaction: NotificationEntity,
        correlationCategory: String,
        correlatedApp: String?,
        confidence: String
    ): AiInsight? {

        if (GROQ_API_KEY.isBlank() || GROQ_API_KEY == "PASTE_YOUR_GROQ_KEY_HERE") {
            Log.w(TAG, "⚠️ No API key — skipping AI insight. Set GROQ_API_KEY in AiInsightEngine.kt")
            return null
        }

        // Format the time nicely: "09:20 PM, Tuesday"
        val time = SimpleDateFormat("hh:mm a, EEEE", Locale.getDefault())
            .format(Date(transaction.timestamp))

        // Build the prompt
        val prompt = buildPrompt(transaction, time, correlationCategory, correlatedApp, confidence)

        Log.d(TAG, "🤖 Sending to Groq AI...")

        return try {
            val response = callGroqApi(prompt)
            Log.d(TAG, "🤖 AI raw response: $response")
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ AI call failed: ${e.message}")
            null
        }
    }

    /**
     * Constructs the prompt that tells the AI exactly what we need.
     */
    private fun buildPrompt(
        transaction: NotificationEntity,
        time: String,
        category: String,
        correlatedApp: String?,
        confidence: String
    ): String {
        return """
You are a personal expense analyst for an Indian user. Given a payment transaction, generate a brief, insightful description of what this purchase was likely for.

Transaction details:
- Amount: ₹${transaction.amount ?: "unknown"}
- Merchant: ${transaction.merchant ?: "unknown"}
- Raw notification: "${transaction.text}"
- Time: $time
- Payment mode: ${transaction.mode ?: "unknown"}
- App used before payment: ${correlatedApp ?: "none (likely offline/in-person)"}
- Detected category: $category
- Correlation confidence: $confidence

Respond with ONLY a JSON object (no markdown, no backticks, no explanation):
{"description": "one-line human description of the purchase", "subcategory": "specific subcategory like Street Food, Cab Ride, Online Shopping, etc.", "necessity": "need or want"}
        """.trim()
    }

    /**
     * Makes the actual HTTP call to Groq's OpenAI-compatible API.
     *
     * GROQ vs GEMINI format difference:
     * - Gemini uses: { "contents": [{ "parts": [{ "text": "..." }] }] }
     * - Groq uses:   { "model": "...", "messages": [{ "role": "user", "content": "..." }] }
     *
     * Groq follows the OpenAI standard, which is used by most AI providers.
     */
    private fun callGroqApi(prompt: String): String {
        val url = URL(GROQ_URL)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        // Groq uses Bearer token auth (API key in the Authorization header)
        connection.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        // Build the JSON body in OpenAI format
        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)     // Low temperature = more focused/consistent answers
            put("max_tokens", 200)      // We only need a short JSON response
        }

        // Send the request
        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(body.toString())
        writer.flush()
        writer.close()

        // Check response code
        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorStream = connection.errorStream
            val error = errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $error")
        }

        // Read the response
        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val response = reader.readText()
        reader.close()

        // Extract the AI's text from Groq's response
        // Groq response format:
        // { "choices": [{ "message": { "content": "..." } }] }
        val jsonResponse = JSONObject(response)
        val choices = jsonResponse.getJSONArray("choices")
        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.getJSONObject("message")
        val content = message.getString("content")

        return content.trim()
    }

    /**
     * Parses the AI's response text into our AiInsight data class.
     */
    private fun parseResponse(response: String): AiInsight {
        return try {
            // Clean up — sometimes AI wraps JSON in markdown backticks
            val cleaned = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleaned)
            AiInsight(
                description = json.optString("description", "Payment recorded"),
                subcategory = json.optString("subcategory", null),
                necessity = json.optString("necessity", null)
            )
        } catch (e: Exception) {
            // If JSON parsing fails, just use the raw text as description
            Log.w(TAG, "Couldn't parse AI JSON, using raw response")
            AiInsight(
                description = response.take(200),
                subcategory = null,
                necessity = null
            )
        }
    }
}
