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
        "com.ubereats.eats" to AppInfo("Uber Eats", "Food Delivery"),
        "com.dominos.india" to AppInfo("Dominos", "Food Delivery"),
        "com.mcdelivery.ind" to AppInfo("McDelivery", "Food Delivery"),
        "in.swiggy.android.instamart" to AppInfo("Swiggy Instamart", "Food Delivery"),
        "com.magicpin" to AppInfo("magicpin", "Food Delivery"),

        // ─── Transport / Ride ───
        "com.ubercab" to AppInfo("Uber", "Transport"),
        "com.olacabs.customer" to AppInfo("Ola", "Transport"),
        "com.rapido.passenger" to AppInfo("Rapido", "Transport"),
        "in.outerspace.namma_yatri" to AppInfo("Namma Yatri", "Transport"),
        "com.ubercab.driver" to AppInfo("Uber (Driver)", "Transport"),
        "in.porter.consumerapp" to AppInfo("Porter", "Transport"),

        // ─── Payment Apps ───
        "com.google.android.apps.nbu.paisa.user" to AppInfo("Google Pay", "Payment App"),
        "com.phonepe.app" to AppInfo("PhonePe", "Payment App"),
        "net.one97.paytm" to AppInfo("Paytm", "Payment App"),
        "com.mobikwik_new" to AppInfo("MobiKwik", "Payment App"),
        "com.freecharge.android" to AppInfo("FreeCharge", "Payment App"),
        "in.org.npci.upiapp" to AppInfo("BHIM", "Payment App"),

        // ─── Shopping ───
        "com.amazon.mShop.android.shopping" to AppInfo("Amazon", "Shopping"),
        "com.flipkart.android" to AppInfo("Flipkart", "Shopping"),
        "com.myntra.android" to AppInfo("Myntra", "Shopping"),
        "com.meesho.supply" to AppInfo("Meesho", "Shopping"),
        "in.ajio.android" to AppInfo("AJIO", "Shopping"),
        "com.nykaa.app" to AppInfo("Nykaa", "Shopping"),
        "com.lenskart.app" to AppInfo("Lenskart", "Shopping"),
        "com.shopclues.android" to AppInfo("ShopClues", "Shopping"),
        "com.snapdeal.main" to AppInfo("Snapdeal", "Shopping"),
        "com.jio.jiomart" to AppInfo("JioMart", "Shopping"),

        // ─── Finance / Credit ───
        "club.cred" to AppInfo("CRED", "Finance"),
        "com.slice" to AppInfo("Slice", "Finance"),
        "com.bharatpe.app" to AppInfo("BharatPe", "Finance"),

        // ─── Entertainment ───
        "com.google.android.youtube" to AppInfo("YouTube", "Entertainment"),
        "com.netflix.mediaclient" to AppInfo("Netflix", "Entertainment"),
        "in.startv.hotstar" to AppInfo("Hotstar", "Entertainment"),
        "com.spotify.music" to AppInfo("Spotify", "Entertainment"),
        "com.amazon.avod.thirdpartyclient" to AppInfo("Prime Video", "Entertainment"),
        "com.jio.media.jiobeats" to AppInfo("JioSaavn", "Entertainment"),
        "com.graymatrix.did" to AppInfo("Gaana", "Entertainment"),
        "tv.twitch.android.app" to AppInfo("Twitch", "Entertainment"),
        "com.bookmyshow" to AppInfo("BookMyShow", "Entertainment"),

        // ─── Travel ───
        "com.makemytrip" to AppInfo("MakeMyTrip", "Travel"),
        "com.goibibo" to AppInfo("Goibibo", "Travel"),
        "com.irctc.vikalp" to AppInfo("IRCTC", "Travel"),
        "com.cleartrip.android" to AppInfo("Cleartrip", "Travel"),
        "com.yatra.base" to AppInfo("Yatra", "Travel"),
        "com.ixigo.handyapp" to AppInfo("ixigo", "Travel"),
        "com.oyo.consumer" to AppInfo("OYO", "Travel"),
        "com.booking" to AppInfo("Booking.com", "Travel"),
        "com.airbnb.android" to AppInfo("Airbnb", "Travel"),

        // ─── Groceries ───
        "com.bigbasket.mobileapp" to AppInfo("BigBasket", "Groceries"),
        "com.zeptoconsumerapp" to AppInfo("Zepto", "Groceries"),
        "com.grofers.customerapp" to AppInfo("Blinkit", "Groceries"),
        "com.dzo.dunzo" to AppInfo("Dunzo", "Groceries"),
        "com.jiomart.consumer" to AppInfo("JioMart", "Groceries"),

        // ─── Health / Pharmacy ───
        "com.pharmeasy" to AppInfo("PharmEasy", "Healthcare"),
        "com.aranoah.healthkart.plus" to AppInfo("1mg", "Healthcare"),
        "com.netmeds.app" to AppInfo("Netmeds", "Healthcare"),
        "com.practo.fabric" to AppInfo("Practo", "Healthcare"),

        // ─── Bill / Recharge ───
        "com.jio.myjio" to AppInfo("MyJio", "Recharge"),
        "com.mventus.selfcare.activity" to AppInfo("Airtel Thanks", "Recharge")
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
        // Catch ALL launcher apps regardless of manufacturer
        if (packageName.contains("launcher", ignoreCase = true)) return false

        val irrelevantPrefixes = listOf(
            // ── System & UI ──
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.motorola.launcher3",
            "com.motorola.launcher",
            "com.sec.android.app.launcher",  // Samsung
            "com.miui.home",                 // Xiaomi
            "com.oppo.launcher",             // Oppo
            "com.realme.launcher",           // Realme
            "com.oneplus.launcher",          // OnePlus
            "com.huawei.android.launcher",   // Huawei

            // ── Settings & Permissions ──
            "com.android.settings",
            "com.android.settings.intelligence",
            "com.google.android.permissioncontroller",

            // ── Input / Keyboard ──
            "com.google.android.inputmethod",
            "com.samsung.android.honeyboard",  // Samsung keyboard
            "com.swiftkey.inputmethod",        // SwiftKey
            "com.touchtype.swiftkey",          // SwiftKey alt
            "com.moto.ime",                    // Moto keyboard

            // ── Phone / Calls / SMS ──
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.incallui",
            "com.android.mms",
            "com.google.android.apps.messaging",  // Google Messages
            "com.samsung.android.messaging",      // Samsung Messages

            // ── System Services ──
            "com.android.stk",             // SIM Toolkit
            "com.android.providers",        // System providers
            "com.android.vending",          // Play Store
            "com.google.android.gms",       // Google Play Services
            "com.google.android.gsf",       // Google Services Framework
            "com.google.android.deskclock", // Clock
            "com.android.documentsui",      // File picker
            "com.android.gallery3d",        // Stock gallery
            "com.google.android.apps.photos",  // Google Photos (viewing, not purchasing)
            "com.android.camera",           // Camera
            "com.android.camera2",          // Camera 2
            "com.motorola.camera",          // Moto camera
            "com.motorola.camera2",         // Moto camera 2
            "com.sec.android.app.camera",   // Samsung camera
            "com.android.calculator2",      // Calculator
            "com.google.android.calculator",// Google calculator
            "com.android.contacts",         // Contacts
            "com.google.android.contacts",  // Google contacts
            "com.android.bluetooth",        // Bluetooth
            "com.android.nfc",              // NFC
            "com.android.wallpaper",        // Wallpaper picker
            "com.android.printservice",     // Print service
            "com.google.android.packageinstaller", // Package installer
            "com.android.packageinstaller",

            // ── Notification shade / Quick settings ──
            "com.android.server.telecom",
            "com.qualcomm.qti",             // Qualcomm system services
            "com.android.emergency"         // Emergency info
        )
        return irrelevantPrefixes.none { packageName.startsWith(it) }
    }
}

