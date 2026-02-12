package com.example.myapplication

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MyNotificationListenerService : NotificationListenerService() {

    private val TAG = "NOTIF_LISTENER"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras?.getString("android.title") ?: "No Title"
        val text = extras?.getCharSequence("android.text")?.toString() ?: "No Text"
        val time = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            .format(Date(sbn.postTime))

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
                db.notificationDao().insertNotification(entity)

                val count = db.notificationDao().getCount()
                Log.d(TAG, "✅ Saved to DB! Total notifications stored: $count")

                // ── Step 7 + 8: If this is a payment → correlate → AI ──
                if (parsed.amount != null) {
                    Log.d(TAG, "💰 Payment detected! Running correlation...")

                    val allTransactions = db.notificationDao().getParsedTransactions()
                    val savedEntity = allTransactions.firstOrNull {
                        it.timestamp == sbn.postTime && it.amount == parsed.amount
                    }

                    if (savedEntity != null) {
                        // Step 7: Correlate
                        val windowStart = sbn.postTime - (10 * 60 * 1000L)
                        val recentUsages = db.appUsageDao().getUsageInWindow(windowStart, sbn.postTime)
                        val result = CorrelationEngine.correlate(savedEntity, recentUsages)

                        db.notificationDao().updateCorrelation(
                            id = savedEntity.id,
                            category = result.category,
                            correlatedApp = result.correlatedApp,
                            confidence = result.confidence
                        )

                        Log.d(TAG, "🧠 CORRELATION COMPLETE:")
                        Log.d(TAG, "   Category: ${result.category}")
                        Log.d(TAG, "   App:      ${result.correlatedApp ?: "none (offline)"}")
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        Log.d(TAG, "Notification removed from: ${sbn.packageName}")
    }
}
