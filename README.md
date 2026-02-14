# Expense Intelligence

**AI-powered personal expense tracker for Android** that automatically detects payments from your phone's notifications, categorizes them intelligently, and lets you split expenses with friends — all running locally on your device.

## ✨ What It Does

Most expense trackers make you manually log every purchase. This app does the opposite — it **listens to your phone's notifications** (GPay, PhonePe, bank SMS, etc.), automatically extracts transaction details, and uses AI to understand **what you bought, when, and why**.

### Core Features

| Feature | Description |
|---------|-------------|
| 🔔 **Auto-capture payments** | Reads payment notifications from UPI apps, banking apps, and SMS in real-time |
| 🧠 **AI-powered insights** | Uses Groq AI (Llama 3.3 70B) to generate descriptions like *"Late evening street food dinner"* |
| 🔍 **AI payment verification** | Uses a lightweight model (Llama 3.1 8B) to filter out promos, ads, and cashback notifications |
| 📊 **Behavioral correlation** | Tracks which apps you were using before a payment to understand context |
| 💡 **Smart category popup** | Shows a popup when you receive a payment, letting you categorize unknown merchants |
| 🧠 **Merchant learning** | Remembers how you categorize merchants and auto-categorizes future payments |
| 💚 **In-app Splitwise** | Full OAuth integration — select a group, pick members, preview split, and create expenses without leaving the app |
| 📅 **Time period filters** | View transactions for Today, This Week, This Month, or All Time |
| 🏷️ **Need vs Want** | Automatically tags transactions as necessities or discretionary spending |
| 🔁 **Subscription detection** | Identifies recurring payments from the same merchant |
| 🧭 **Setup Wizard** | Guided 6-step onboarding that walks you through every permission and setting |

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
│  🍕 Swiggy          ₹350   [📤] 12:30 PM  │
│  ☕ Chai Wala        ₹50          10:15 AM  │
│  🚕 Uber            ₹150          8:00 AM  │
└─────────────────────────────────────────────┘
```

## 🏗️ Architecture

```
First Launch → Setup Wizard (6-step guided onboarding)
                   ↓
      ┌────────────┴────────────┐
      ↓                         ↓
  Grant Permissions       Configure Settings
  (Notif, Usage,         (Play Protect, Battery,
   Overlay)               Restricted Settings)
                   ↓
           Start ForegroundService
                   ↓
Payment Notification → NotificationListenerService → TransactionParser
                                   ↓
                        ┌──────────┴──────────┐
                        ↓                      ↓
               AI Payment Check        Check Learned Merchants
              (is this a real          (MerchantAliasDao)
               payment?)                       ↓
                        ↓               Known merchant?
                  Room Database         ↓ Yes → Auto-categorize
                        ↓               ↓ No  → Show Category Popup
              CorrelationEngine                ↓
                        ↓               User Selection
            ┌───────────┴───────────┐          ↓
            ↓                       ↓   Learn Merchant
    Known App?                Unknown Merchant?
    (AppKnowledgeBase:         (GPay to friend)
     Zomato, Uber, etc.)
            ↓                       ↓
    Auto-categorize          Popup / AI Categorize
            ↓                       ↓
        Groq AI ←────────── Category + Context
            ↓
    "Digital Memory"
    (description + subcategory + need/want)
            ↓
    ────────────────────────────────
                    ↓
         Jetpack Compose Dashboard
         ├── Time Period Filters
         ├── Category Spend Bar
         ├── Top Apps by Spending
         ├── Transaction Cards (with delete + split)
         └── Weekly AI Insights

         Splitwise Flow:
         Transaction Card → 📤 Split → Bottom Sheet
              ↓
         Select Group → Select Members → Preview Split
              ↓
         Splitwise API (OAuth) → Expense Created ✅
```

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| Setup Wizard | `SetupScreen.kt` | 6-step guided onboarding (Play Protect, permissions, battery) |
| Notification Capture | `MyNotificationListenerService.kt` | Reads notifications, filters payments, triggers popup |
| App Usage Tracking | `MyForegroundService.kt` | Polls `UsageStatsManager` every 5s |
| Transaction Parser | `TransactionParser.kt` | Regex extraction of amount, merchant, mode from Indian payment notifications |
| Correlation Engine | `CorrelationEngine.kt` | Links payments to app usage sessions using a scoring algorithm |
| App Knowledge Base | `AppKnowledgeBase.kt` | Maps 100+ Android package names → friendly name + category |
| AI Engine | `AiInsightEngine.kt` | Groq API for descriptions + lightweight payment verification |
| Category Popup | `CategoryPopupActivity.kt` | Overlay popup for manual categorization with time-based suggestions |
| Time Suggestions | `TimeSuggestionEngine.kt` | Suggests categories based on time of day |
| Merchant Learning | `MerchantAliasDao.kt` | Stores user's category preferences per merchant |
| Subscription Detector | `SubscriptionDetector.kt` | Identifies recurring payments |
| Insight Generator | `InsightGenerator.kt` | Aggregates summaries with time period support + weekly AI insights |
| Splitwise Manager | `SplitwiseManager.kt` | Full OAuth login, group fetching, expense creation via Splitwise API |
| Split Bottom Sheet | `SplitBottomSheet.kt` | In-app UI for selecting group, members, and creating a split |
| Database | `AppDatabase.kt` | Room DB (v7) with migrations — notifications, usage, merchant aliases, subscriptions |
| Dashboard UI | `MainActivity.kt` | Jetpack Compose dark-themed dashboard with all sections |

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 (dark theme with gradient cards)
- **Database**: Room (local SQLite, v7 with migrations)
- **AI**: Groq API — Llama 3.3 70B (insights) + Llama 3.1 8B (payment verification) — free tier, 14,400 requests/day
- **Splitwise**: OAuth 2.0 + REST API for expense splitting
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

4. **Follow the Setup Wizard** — The app launches a guided 6-step wizard on first run:

   | Step | What It Does |
   |------|-------------|
   | 0. Welcome | Overview of features + privacy disclaimer |
   | 1. Play Protect | Guides you to disable Play Protect scanning (required for sideloaded apps) |
   | 2. App Settings | "Allow restricted settings" + set battery to "Unrestricted" |
   | 3. Notification Access | Enable notification listener for payment capture |
   | 4. Usage Access | Enable usage stats for app correlation |
   | 5. All Set! | Starts the background service and opens the dashboard |

   > 💡 The wizard auto-detects permission status and updates in real-time as you toggle settings.

## 🔐 Permissions

| Permission | Why |
|------------|-----|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read payment notifications |
| `FOREGROUND_SERVICE` | Keep tracking service alive |
| `PACKAGE_USAGE_STATS` | Detect which app was open before payment |
| `SYSTEM_ALERT_WINDOW` | Show category popup over other apps |
| `POST_NOTIFICATIONS` | Show service notification |
| `INTERNET` | Call Groq AI API + Splitwise API |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Android from killing the background service |

## ⚙️ Settings

The app includes configurable settings accessible from the collapsible Settings card on the dashboard:

| Setting | Options | Description |
|---------|---------|-------------|
| **Popup Mode** | All / Smart | Show popup for every payment or only unknown merchants |
| **Time Filter** | Today / Week / Month / All | Filter transactions by time period |
| **Splitwise** | Connect / Disconnect | OAuth login to your Splitwise account |
| **Background Service** | Start / Stop | Control the foreground service |
| **Re-run Setup** | — | Re-launch the setup wizard at any time |
| **Delete All Data** | — | Permanently erase all stored data (in Privacy & Data section) |

## 🔄 How It Works

### Payment Flow (Known App)
1. You order food on Swiggy
2. Payment notification: "₹350 paid to Swiggy"
3. AI verifies it's a real payment (not a promo)
4. App detects Swiggy was in foreground → Category: "Food Delivery"
5. AI generates: *"Evening food delivery, likely dinner"* + tags it as "Want"
6. Saved to dashboard ✅

### Payment Flow (Unknown Merchant)
1. You pay ₹50 to "Ramesh Kumar" via GPay
2. Popup appears: "What was this for?"
3. Time-based suggestions appear (e.g., breakfast in morning, dinner at night)
4. You select "Food → Chai/Coffee"
5. App learns: "Ramesh Kumar" = Chai
6. Next time → Auto-categorized! 🧠

### Splitwise Integration
1. Connect Splitwise via OAuth in Settings
2. On any transaction, tap the 📤 button
3. Bottom sheet opens: select a group → pick members to split with
4. Preview shows per-person amount
5. Tap "Split It!" → expense created directly in Splitwise via API

### AI Payment Verification
Not every notification with a ₹ sign is a real payment. The app uses a lightweight AI model to filter:
- ✅ "₹183 paid to Uber India" → Real payment → **KEEP**
- ❌ "Get ₹201 off on purchase" → Promo → **SKIP**
- ❌ "₹500 credited to your account" → Income → **SKIP**

## 📊 Features in Detail

### Setup Wizard
On first launch, a 6-step animated wizard guides you through:
1. **Welcome** — Feature overview + detailed privacy disclaimer
2. **Play Protect** — Disable scanning (otherwise permissions get silently revoked)
3. **App Settings** — Allow restricted settings + unrestricted battery
4. **Notification Access** — With troubleshooting tips if toggle is greyed out
5. **Usage Access** — With explanation of why it's needed
6. **All Set!** — Auto-starts the background service

Each step has real-time permission status badges that update as you return from settings.

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

### App Knowledge Base
100+ Indian apps pre-mapped with friendly names and categories:
- Food Delivery: Zomato, Swiggy, Dominos, etc.
- Transport: Uber, Ola, Rapido, Namma Yatri
- Shopping: Amazon, Flipkart, Myntra, Meesho
- Payment: GPay, PhonePe, Paytm, BHIM
- And more: Travel, Groceries, Healthcare, Entertainment, Finance, Recharge

### Top Spending Apps
A ranked leaderboard card showing which apps you spend the most through, with:
- Gold/Silver/Bronze medal emojis for top 3
- Transaction count per app
- Proportional spending bars

### Spending Breakdown Bar
A visual proportional bar showing category-wise spend distribution with color-coded legends.

## 🔒 Privacy

- 📱 All data stored locally on YOUR phone — Room database, no cloud
- 🚫 We NEVER collect, store, or share your personal or financial data
- 🤖 The only internet calls are to Groq AI (brief text summaries) and Splitwise API (if you connect it)
- 🔔 Notification access reads ONLY payment notifications — not personal messages
- 🗑️ Uninstall the app = ALL data permanently deleted
- 🔑 No accounts, no login (except optional Splitwise), no tracking, no analytics

## 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first.

## 📄 License

[MIT](LICENSE)

---

**Built with ❤️ using Kotlin, Jetpack Compose, and Groq AI**
