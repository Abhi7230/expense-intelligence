package com.example.myapplication

/**
 * A lookup table that maps Android package names to human-friendly info.
 *
 * WHY THIS EXISTS:
 * When UsageStatsManager tells us "com.application.zomato was used",
 * we need to know that means "Zomato" and it's a "Food Delivery" app.
 *
 * Think of it as a phone contact list:
 *   +91-9876543210 → "Mom"
 * Same idea:
 *   "com.application.zomato" → "Zomato" (Food Delivery)
 *
 * You'll keep adding entries as you discover more apps on your phone.
 */
object AppKnowledgeBase {

    data class AppInfo(
        val friendlyName: String,   // e.g., "Zomato"
        val category: String        // e.g., "Food Delivery"
    )

    private val knownApps = mapOf(
        // ─── Food Delivery ───
        "com.application.zomato" to AppInfo("Zomato", "Food Delivery"),
        "in.swiggy.android" to AppInfo("Swiggy", "Food Delivery"),
        "com.done.faasos" to AppInfo("EatSure", "Food Delivery"),

        // ─── Transport / Ride ───
        "com.ubercab" to AppInfo("Uber", "Transport"),
        "com.olacabs.customer" to AppInfo("Ola", "Transport"),
        "com.rapido.passenger" to AppInfo("Rapido", "Transport"),
        "in.outerspace.namma_yatri" to AppInfo("Namma Yatri", "Transport"),

        // ─── Payment Apps ───
        "com.google.android.apps.nbu.paisa.user" to AppInfo("Google Pay", "Payment App"),
        "com.phonepe.app" to AppInfo("PhonePe", "Payment App"),
        "net.one97.paytm" to AppInfo("Paytm", "Payment App"),

        // ─── Shopping ───
        "com.amazon.mShop.android.shopping" to AppInfo("Amazon", "Shopping"),
        "com.flipkart.android" to AppInfo("Flipkart", "Shopping"),
        "com.myntra.android" to AppInfo("Myntra", "Shopping"),
        "club.cred" to AppInfo("CRED", "Finance"),

        // ─── Entertainment ───
        "com.google.android.youtube" to AppInfo("YouTube", "Entertainment"),
        "com.netflix.mediaclient" to AppInfo("Netflix", "Entertainment"),
        "in.startv.hotstar" to AppInfo("Hotstar", "Entertainment"),

        // ─── Travel ───
        "com.makemytrip" to AppInfo("MakeMyTrip", "Travel"),
        "com.goibibo" to AppInfo("Goibibo", "Travel"),
        "com.irctc.vikalp" to AppInfo("IRCTC", "Travel"),

        // ─── Groceries ───
        "com.bigbasket.mobileapp" to AppInfo("BigBasket", "Groceries"),
        "com.zeptoconsumerapp" to AppInfo("Zepto", "Groceries"),
        "com.grofers.customerapp" to AppInfo("Blinkit", "Groceries")
    )

    /**
     * Look up a package name → returns AppInfo or null if unknown.
     */
    fun getAppInfo(packageName: String): AppInfo? {
        return knownApps[packageName]
    }

    /**
     * Filters out system/utility apps that are NOT useful for correlation.
     * We don't care that the user had their keyboard or launcher open.
     */
    fun isRelevantApp(packageName: String): Boolean {
        val irrelevantPrefixes = listOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.settings",
            "com.google.android.inputmethod",
            "com.google.android.permissioncontroller",
            "com.android.vending",          // Play Store
            "com.google.android.gms",       // Google Play Services
            "com.google.android.deskclock", // Clock
            "com.android.dialer",
            "com.google.android.dialer"
        )
        return irrelevantPrefixes.none { packageName.startsWith(it) }
    }
}

