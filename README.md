# Expense Intelligence

**AI-powered personal expense tracker for Android** that automatically detects payments from your phone's notifications, categorizes them intelligently, and lets you split expenses with friends — all running locally on your device.

## ✨ What It Does

Most expense trackers make you manually log every purchase. This app does the opposite — it **listens to your phone's notifications** (GPay, PhonePe, bank SMS, etc.), automatically extracts transaction details, and uses AI to understand **what you bought, when, and why**.

### Core Features

| Feature | Description |
|---------|-------------|
| 🔔 **Auto-capture payments** | Reads payment notifications from UPI apps, banking apps, and SMS in real-time |
| 🧠 **AI-powered insights** | Uses Groq AI (Llama 3.3 70B) to generate descriptions like *"Late evening street food dinner"* |
| 📊 **Behavioral correlation** | Tracks which apps you were using before a payment to understand context |
| 💡 **Smart category popup** | Shows a popup when you receive a payment, letting you categorize unknown merchants |
| 🧠 **Merchant learning** | Remembers how you categorize merchants and auto-categorizes future payments |
| 💚 **Quick Splitwise** | One-tap button to add any expense to Splitwise |
| 📅 **Time period filters** | View transactions for Today, This Week, This Month, or All Time |
| 🏷️ **Need vs Want** | Automatically tags transactions as necessities or discretionary spending |

## 📱 Screenshots

```
┌─────────────────────────────────────────────┐
│         💰 Expense Intelligence             │
│         Friday, 14 Feb 2025                 │
├─────────────────────────────────────────────┤
│  [Today]  │  Week  │  Month  │  All        │
├─────────────────────────────────────────────┤
│  💸 Today's Spending: ₹1,250               │
│  📊 5 transactions                          │
├─────────────────────────────────────────────┤
│  🍕 Swiggy          ₹350   [Split] 12:30 PM│
│  ☕ Chai Wala        ₹50           10:15 AM│
│  🚕 Uber            ₹150            8:00 AM│
└─────────────────────────────────────────────┘
```

## 🏗️ Architecture

```
Payment Notification → NotificationListenerService → TransactionParser
                                   ↓
                        ┌──────────┴──────────┐
                        ↓                      ↓
              Room Database            Check Learned Merchants
                        ↓                      ↓
              CorrelationEngine ←──── MerchantAliasDao
                        ↓
            ┌───────────┴───────────┐
            ↓                       ↓
    Known App?                Unknown Merchant?
    (Zomato, Uber)            (GPay to friend)
            ↓                       ↓
    Auto-categorize          Show Category Popup
            ↓                       ↓
        Groq AI ←────────── User Selection
            ↓                       ↓
    "Digital Memory"         Learn Merchant
            ↓                       ↓
    ────────────────────────────────
                    ↓
         Jetpack Compose Dashboard
```

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| Notification Capture | `MyNotificationListenerService.kt` | Reads notifications, filters payments, triggers popup |
| App Usage Tracking | `MyForegroundService.kt` | Polls `UsageStatsManager` every 5s |
| Transaction Parser | `TransactionParser.kt` | Regex extraction of amount, merchant, mode |
| Correlation Engine | `CorrelationEngine.kt` | Links payments to app usage sessions |
| AI Engine | `AiInsightEngine.kt` | Groq API for natural language descriptions |
| Category Popup | `CategoryPopupActivity.kt` | Overlay popup for manual categorization |
| Time Suggestions | `TimeSuggestionEngine.kt` | Suggests categories based on time of day |
| Merchant Learning | `MerchantAliasDao.kt` | Stores user's category preferences per merchant |
| Subscription Detector | `SubscriptionDetector.kt` | Identifies recurring payments |
| Insight Generator | `InsightGenerator.kt` | Aggregates summaries with time period support |
| Dashboard UI | `MainActivity.kt` | Jetpack Compose dark-themed dashboard |

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Database**: Room (local SQLite)
- **AI**: Groq API (Llama 3.3 70B) — free tier, 14,400 requests/day
- **Background**: ForegroundService + NotificationListenerService
- **Build**: Gradle with KSP for Room annotation processing

## 🚀 Setup

### Prerequisites
- Android Studio (Arctic Fox or later)
- Android device/emulator running API 26+ (Android 8.0+)
- A [Groq API key](https://console.groq.com/keys) (free)
- *(Optional)* [Splitwise app credentials](https://secure.splitwise.com/apps) for expense splitting

### Steps

1. **Clone the repo**
   ```bash
   git clone https://github.com/YOUR_USERNAME/expense-intelligence.git
   cd expense-intelligence
   ```

2. **Configure API keys**
   
   Copy the example file:
   ```bash
   cp local.properties.example local.properties
   ```
   
   Edit `local.properties` and add your keys:
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   GROQ_API_KEY=your_groq_api_key_here
   
   # Optional: For Splitwise integration
   SPLITWISE_CLIENT_ID=your_client_id
   SPLITWISE_CLIENT_SECRET=your_client_secret
   ```

3. **Build and run** — Open in Android Studio → Select your device → Run

4. **Grant permissions** on the device:
   - **Notification Access**: Settings → Notification Listener → Enable
   - **Usage Access**: Settings → Usage Access → Enable
   - **Display Over Other Apps**: Settings → Special Access → Enable *(for popup)*
   - **Notification permission** (Android 13+): Allow when prompted

5. **Start the service** — Tap Settings card → Start button

## 🔐 Permissions

| Permission | Why |
|------------|-----|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read payment notifications |
| `FOREGROUND_SERVICE` | Keep tracking service alive |
| `PACKAGE_USAGE_STATS` | Detect which app was open before payment |
| `SYSTEM_ALERT_WINDOW` | Show category popup over other apps |
| `POST_NOTIFICATIONS` | Show service notification |
| `INTERNET` | Call Groq AI API |

## ⚙️ Settings

The app includes configurable settings:

| Setting | Options | Description |
|---------|---------|-------------|
| **Popup Mode** | All / Smart | Show popup for every payment or only unknown merchants |
| **Time Filter** | Today / Week / Month / All | Filter transactions by time period |
| **Splitwise** | Connect / Disconnect | Link your Splitwise account |

## 🔄 How It Works

### Payment Flow (Known App)
1. You order food on Swiggy
2. Payment notification: "₹350 paid to Swiggy"
3. App detects Swiggy was in foreground → Category: "Food Delivery"
4. AI generates: *"Evening food delivery, likely dinner"*
5. Saved to dashboard ✅

### Payment Flow (Unknown Merchant)
1. You pay ₹50 to "Ramesh Kumar" via GPay
2. Popup appears: "What was this for?"
3. You select "Food → Chai/Coffee"
4. App learns: "Ramesh Kumar" = Chai
5. Next time → Auto-categorized! 🧠

### Splitwise Integration
1. Connect Splitwise in Settings
2. On any transaction, tap the 💚 button
3. Splitwise opens with amount + description pre-filled
4. Select friends to split with

## 📊 Features in Detail

### Time Period Filters
```
┌─────────────────────────────────────────────┐
│  [Today]  │  Week  │  Month  │  All        │
└─────────────────────────────────────────────┘
```
Switch between different time periods to see:
- **Today**: Today's transactions and spending
- **Week**: Monday to Sunday of current week
- **Month**: 1st of month to today
- **All**: Complete transaction history

### Category Popup
When you receive a payment to an unknown merchant, a popup appears with:
- Time-based suggestions (breakfast in morning, dinner in evening)
- Quick category chips (Food, Transport, Shopping, etc.)
- Optional note field
- "Add to Splitwise" button

### Merchant Learning
The app remembers your categorizations:
- First time: "Chai Wala" → You select "Food/Chai"
- Next time: "Chai Wala" → Auto-categorized as "Food/Chai" ✅

## 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first.

## 📄 License

[MIT](LICENSE)

---

**Built with ❤️ using Kotlin, Jetpack Compose, and Groq AI**
