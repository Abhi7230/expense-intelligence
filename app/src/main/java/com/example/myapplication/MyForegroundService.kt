package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class MyForegroundService : Service() {

    private val TAG = "FOREGROUND_SERVICE"

    @Volatile
    private var isRunning = false

    // ─── Step 6: Track which app is currently in the foreground ───
    // These variables remember the "current state" between polling cycles.
    //
    // Think of it like a security guard checking every 5 seconds:
    //   "Who's in the building right now?"
    // If the person changed since last check, write down when the previous person left.

    private var lastForegroundApp: String? = null   // package name of the app currently on screen
    private var lastAppStartTime: Long = 0L          // when that app came to the foreground

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        isRunning = true
        startMyForeground()
        startPolling()   // renamed from startLogging — it now does real work
    }

    private fun startMyForeground() {
        val channelId = "my_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Expense Tracker Running")
            .setContentText("Monitoring app usage...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    // ─── THE CORE POLLING LOOP ───
    // Every 5 seconds: ask the OS "which app is on screen right now?"
    // If it changed since last check → save the previous app's session to the database.
    private fun startPolling() {
        thread {
            while (isRunning) {
                try {
                    val currentApp = getCurrentForegroundApp()

                    if (currentApp != null && currentApp != lastForegroundApp) {
                        // The user switched apps!
                        Log.d(TAG, "App switch detected: ${lastForegroundApp ?: "none"} → $currentApp")

                        // Save the PREVIOUS app's session (if there was one)
                        if (lastForegroundApp != null && lastAppStartTime > 0L) {
                            val now = System.currentTimeMillis()
                            val duration = now - lastAppStartTime

                            // Only save if the session was at least 2 seconds
                            // (filters out quick flashes / system transitions)
                            if (duration >= 2000) {
                                val entity = AppUsageEntity(
                                    app = lastForegroundApp!!,
                                    startTime = lastAppStartTime,
                                    endTime = now,
                                    duration = duration
                                )

                                val db = AppDatabase.getDatabase(applicationContext)
                                db.appUsageDao().insertUsage(entity)

                                val seconds = duration / 1000
                                Log.d(TAG, "✅ Saved usage: ${lastForegroundApp} for ${seconds}s")

                                val count = db.appUsageDao().getCount()
                                Log.d(TAG, "Total app usage sessions: $count")
                            }
                        }

                        // Update tracking to the NEW app
                        lastForegroundApp = currentApp
                        lastAppStartTime = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in polling: ${e.message}")
                }

                Thread.sleep(5000)  // Poll every 5 seconds
            }
            Log.d(TAG, "Polling thread stopped cleanly")
        }
    }

    /**
     * Asks Android: "Which app is currently in the foreground (on screen)?"
     *
     * HOW IT WORKS:
     * UsageStatsManager keeps a log of "events" — every time an app comes to the
     * foreground or goes to the background, Android records it.
     *
     * We ask for events from the last 10 seconds, then find the LATEST
     * MOVE_TO_FOREGROUND event — that's the app currently on screen.
     *
     * Think of it like reading a visitor log book:
     *   "8:00 PM — Zomato entered"
     *   "8:06 PM — Zomato left"
     *   "8:06 PM — WhatsApp entered"
     *   → WhatsApp is currently here.
     */
    private fun getCurrentForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000  // look at events from the last 10 seconds

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var foregroundApp: String? = null

        // Walk through all events in the last 10 seconds
        // The LAST "MOVE_TO_FOREGROUND" event = the app currently on screen
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.packageName
            }
        }

        return foregroundApp
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        // Save the last app's session before the service dies
        if (lastForegroundApp != null && lastAppStartTime > 0L) {
            val now = System.currentTimeMillis()
            val duration = now - lastAppStartTime
            if (duration >= 2000) {
                thread {
                    try {
                        val entity = AppUsageEntity(
                            app = lastForegroundApp!!,
                            startTime = lastAppStartTime,
                            endTime = now,
                            duration = duration
                        )
                        val db = AppDatabase.getDatabase(applicationContext)
                        db.appUsageDao().insertUsage(entity)
                        Log.d(TAG, "✅ Saved final session: ${lastForegroundApp} for ${duration / 1000}s")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save final session: ${e.message}")
                    }
                }
            }
        }

        Log.d(TAG, "Service Destroyed")
    }
}
