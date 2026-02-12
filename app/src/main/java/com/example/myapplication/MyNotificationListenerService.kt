package com.example.myapplication

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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

        // ── FILTER 5: Smart dedup for bank debit/credit SMS ──
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
        val textLower = text.lowercase()
        val isBankDebitSms = (textLower.contains("debited") || textLower.contains("credited")) &&
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
}
