# Expense Intelligence

**AI-powered personal expense tracker for Android** that automatically detects payments from your phone's notifications, correlates them with app usage behavior, and generates intelligent spending insights — all running locally on your device.

## What It Does

Most expense trackers make you manually log every purchase. This app does the opposite — it **listens to your phone's notifications** (GPay, PhonePe, bank SMS, etc.), automatically extracts transaction details, and uses AI to understand **what you bought, when, and why**.

### Core Features

- **Auto-capture payments** — Reads payment notifications from UPI apps, banking apps, and SMS in real-time
- **Smart parsing** — Extracts amount, merchant name, and payment mode using regex-based parsing
- **Behavioral correlation** — Tracks which apps you were using before a payment to understand context (e.g., you were on Zomato → payment detected → categorized as "Food Delivery")
- **AI-powered insights** — Uses Groq AI (Llama 3.3 70B) to generate human-readable descriptions like *"Late evening street food dinner from a local stall"*
- **Need vs Want classification** — Automatically tags transactions as necessities or discretionary spending
- **Daily dashboard** — Visual spending summary with category breakdown and proportion bar
- **Weekly behavioral analysis** — AI analyzes your spending patterns across the week

## Architecture

```
Payment Notification → NotificationListenerService → TransactionParser (regex)
                                                            ↓
                                                   Room Database (local)
                                                            ↓
                                          CorrelationEngine (app usage matching)
                                                            ↓
                                              Groq AI (digital memory generation)
                                                            ↓
                                                  Jetpack Compose Dashboard
```

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| Notification Capture | `MyNotificationListenerService.kt` | Reads all notifications, filters payment ones |
| App Usage Tracking | `MyForegroundService.kt` | Polls `UsageStatsManager` every 5s to detect app switches |
| Transaction Parser | `TransactionParser.kt` | Regex extraction of amount, merchant, payment mode |
| Correlation Engine | `CorrelationEngine.kt` | Links payments to preceding app usage sessions |
| AI Engine | `AiInsightEngine.kt` | Calls Groq API for natural language descriptions |
| Insight Generator | `InsightGenerator.kt` | Aggregates daily/weekly summaries, calls AI for patterns |
| App Knowledge Base | `AppKnowledgeBase.kt` | Maps package names to friendly names and categories |
| Local Database | `AppDatabase.kt` | Room DB with `notifications` and `app_usage` tables |
| Dashboard UI | `MainActivity.kt` | Jetpack Compose dark-themed dashboard |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Database**: Room (local SQLite)
- **AI**: Groq API (Llama 3.3 70B) — free tier, 14,400 requests/day
- **Background**: ForegroundService + NotificationListenerService
- **Build**: Gradle with KSP for Room annotation processing

## Setup

### Prerequisites
- Android Studio (Arctic Fox or later)
- Android device/emulator running API 24+ (Android 7.0+)
- A [Groq API key](https://console.groq.com/keys) (free)

### Steps

1. **Clone the repo**
   ```bash
   git clone https://github.com/YOUR_USERNAME/expense-intelligence.git
   cd expense-intelligence
   ```

2. **Add your API key** — Open `local.properties` and add:
   ```properties
   GROQ_API_KEY=your_groq_api_key_here
   ```
   > This file is in `.gitignore` and will never be committed.

3. **Build and run** — Open in Android Studio → Select your device → Run

4. **Grant permissions** on the device:
   - **Notification Access**: Settings → Notification Listener → Enable for "Expense Intelligence"
   - **Usage Access**: Settings → Usage Access → Enable for "Expense Intelligence"
   - **Notification permission** (Android 13+): Allow when prompted

5. **Start the service** — Tap the Settings card → Start button

## Permissions

| Permission | Why |
|------------|-----|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read payment notifications from other apps |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep tracking service alive in background |
| `PACKAGE_USAGE_STATS` | Detect which app was open before a payment |
| `POST_NOTIFICATIONS` | Show the persistent service notification |
| `INTERNET` | Call Groq AI API for insight generation |

## How It Works (Technical Deep Dive)

1. **Notification arrives** (e.g., "₹250 paid to Swiggy via UPI")
2. `NotificationListenerService` captures it and passes the text to `TransactionParser`
3. Parser extracts: `amount=250`, `merchant=Swiggy`, `mode=UPI`
4. Data is saved to Room database
5. `CorrelationEngine` checks `app_usage` table: "What app was the user on in the last 10 minutes?"
6. Finds Swiggy was open for 4 minutes → category: "Food Delivery", confidence: "high"
7. `AiInsightEngine` sends the context to Groq → AI returns: *"Evening food delivery order from Swiggy, likely dinner"*
8. Everything is stored and displayed on the dashboard

## Screenshots

> Add screenshots of your app here after building it!

## Contributing

Pull requests are welcome. For major changes, please open an issue first.

## License

[MIT](LICENSE)

