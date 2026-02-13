package com.example.myapplication

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val TAG = "LIFECYCLE"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate Called")

        // Handle Splitwise OAuth callback
        handleSplitwiseCallback(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSplitwiseCallback(intent)
    }

    private fun handleSplitwiseCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "expenseintel" && uri.host == "splitwise") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                Log.d(TAG, "🔗 Splitwise OAuth callback received")
                CoroutineScope(Dispatchers.Main).launch {
                    val success = SplitwiseManager.handleCallback(this@MainActivity, code)
                    Toast.makeText(
                        this@MainActivity,
                        if (success) "✅ Connected to Splitwise!" else "❌ Splitwise login failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onStart() { super.onStart(); Log.d(TAG, "onStart") }
    override fun onResume() { super.onResume(); Log.d(TAG, "onResume") }
    override fun onPause() { super.onPause(); Log.d(TAG, "onPause") }
    override fun onStop() { super.onStop(); Log.d(TAG, "onStop") }
    override fun onDestroy() { super.onDestroy(); Log.d(TAG, "onDestroy") }
    override fun onRestart() { super.onRestart(); Log.d(TAG, "onRestart") }
}

// ═══════════════════════════════════════════════════════════════
// APP ROOT — decides: Setup Wizard or Dashboard
// ═══════════════════════════════════════════════════════════════
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("expense_intelligence_prefs", Context.MODE_PRIVATE)
    }
    var setupCompleted by remember {
        mutableStateOf(prefs.getBoolean("setup_completed", false))
    }

    if (setupCompleted) {
        ExpenseDashboard(
            modifier = modifier,
            onRerunSetup = {
                // Allow user to re-run setup from settings
                prefs.edit().putBoolean("setup_completed", false).apply()
                setupCompleted = false
            }
        )
    } else {
        SetupWizard(
            onSetupComplete = {
                prefs.edit().putBoolean("setup_completed", true).apply()
                setupCompleted = true
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// COLOR PALETTE
// ═══════════════════════════════════════════════════════════════
private val GradientStart = Color(0xFF1A1A2E)
private val GradientMid = Color(0xFF16213E)
private val GradientEnd = Color(0xFF0F3460)
private val AccentGreen = Color(0xFF00E676)
private val AccentPurple = Color(0xFFBB86FC)
private val AccentRed = Color(0xFFFF5252)
private val AccentGold = Color(0xFFFFD54F)
private val SurfaceDark = Color(0xFF1E1E2E)
private val SurfaceCard = Color(0xFF2A2A3E)
private val TextPrimary = Color(0xFFECECEC)
private val TextSecondary = Color(0xFFA0A0B0)
private val TextMuted = Color(0xFF6C6C7E)

// ═══════════════════════════════════════════════════════════════
// CATEGORY COLORS — maps category names to specific colors
// ═══════════════════════════════════════════════════════════════
private fun getCategoryColor(category: String): Color {
    val lower = category.lowercase()
    return when {
        lower.contains("food") -> Color(0xFFFF9800)       // Orange
        lower.contains("transport") -> Color(0xFF42A5F5)   // Blue
        lower.contains("travel") -> Color(0xFF29B6F6)      // Light Blue
        lower.contains("shopping") -> Color(0xFFEC407A)    // Pink
        lower.contains("entertainment") -> Color(0xFFAB47BC) // Purple
        lower.contains("bill") || lower.contains("recharge") || lower.contains("utility") -> Color(0xFF26A69A) // Teal
        lower.contains("grocery") -> Color(0xFF66BB6A)     // Green
        lower.contains("health") || lower.contains("medicine") -> Color(0xFFEF5350) // Red
        lower.contains("finance") || lower.contains("payment") -> Color(0xFF5C6BC0) // Indigo
        lower.contains("education") -> Color(0xFF7E57C2)   // Deep Purple
        lower.contains("rent") || lower.contains("housing") -> Color(0xFF8D6E63) // Brown
        lower.contains("personal") || lower.contains("salon") -> Color(0xFFE91E63) // Deep Pink
        lower.contains("offline") -> Color(0xFF546E7A)     // Blue-grey darker
        else -> Color(0xFF78909C)                          // Blue-grey default
    }
}

private fun getCategoryIcon(category: String): String {
    val lower = category.lowercase()
    return when {
        lower.contains("food") -> "\uD83C\uDF54"           // burger
        lower.contains("transport") -> "\uD83D\uDE95"      // taxi
        lower.contains("travel") -> "\u2708\uFE0F"         // airplane
        lower.contains("shopping") -> "\uD83D\uDED2"       // cart
        lower.contains("entertainment") -> "\uD83C\uDFAC"  // clapper
        lower.contains("bill") || lower.contains("recharge") || lower.contains("utility") -> "\uD83D\uDCB3" // card
        lower.contains("grocery") -> "\uD83E\uDD66"        // broccoli
        lower.contains("health") || lower.contains("medicine") -> "\uD83D\uDC8A" // pill
        lower.contains("finance") || lower.contains("payment") -> "\uD83C\uDFE6" // bank
        lower.contains("education") -> "\uD83D\uDCDA"      // books
        lower.contains("rent") || lower.contains("housing") -> "\uD83C\uDFE0" // house
        lower.contains("personal") || lower.contains("salon") -> "\uD83D\uDC87" // haircut
        lower.contains("offline") -> "\uD83C\uDFEA"        // convenience store
        else -> "\uD83D\uDCB0"                             // money bag
    }
}

private fun getRankEmoji(rank: Int): String {
    return when (rank) {
        0 -> "\uD83E\uDD47"  // gold medal
        1 -> "\uD83E\uDD48"  // silver medal
        2 -> "\uD83E\uDD49"  // bronze medal
        else -> "${rank + 1}."
    }
}

// ═══════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ExpenseDashboard(modifier: Modifier = Modifier, onRerunSetup: () -> Unit = {}) {
    val context = LocalContext.current

    var dailySummary by remember { mutableStateOf<InsightGenerator.DailySummary?>(null) }
    var weeklyInsight by remember { mutableStateOf<String?>(null) }
    var topApps by remember { mutableStateOf<List<InsightGenerator.AppSpending>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isWeeklyLoading by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }
    
    // ── Time Period Filter ──
    var selectedPeriod by remember { mutableStateOf(InsightGenerator.TimePeriod.TODAY) }

    // ── Splitwise bottom sheet state ──
    var showSplitSheet by remember { mutableStateOf(false) }
    var splitAmount by remember { mutableStateOf(0.0) }
    var splitMerchant by remember { mutableStateOf("") }
    var splitDescription by remember { mutableStateOf("") }

    // ── Load data based on selected time period ──
    fun loadDataForPeriod(period: InsightGenerator.TimePeriod) {
        isLoading = true
        thread {
            try {
                val db = AppDatabase.getDatabase(context)

                // ── CLEANUP: Fix old badly-correlated transactions (only on first load) ──
                if (period == InsightGenerator.TimePeriod.TODAY) {
                    val badEntries = db.notificationDao().getBadlyCorrelatedTransactions()
                    if (badEntries.isNotEmpty()) {
                        Log.d("DASHBOARD", "🧹 Cleaning up ${badEntries.size} badly-correlated entries...")
                        for (tx in badEntries) {
                            val windowStart = tx.timestamp - (10 * 60 * 1000L)
                            val recentUsages = db.appUsageDao().getUsageInWindow(windowStart, tx.timestamp)
                            val result = CorrelationEngine.correlate(tx, recentUsages)
                            db.notificationDao().updateCorrelation(
                                id = tx.id,
                                category = result.category,
                                correlatedApp = result.correlatedApp,
                                confidence = result.confidence
                            )
                            Log.d("DASHBOARD", "  Fixed #${tx.id}: ${result.category} (was: ${tx.category})")
                        }
                    }
                }

                val summary = InsightGenerator.generateSummaryForPeriod(db.notificationDao(), period)
                val apps = InsightGenerator.getTopAppsBySpending(db.notificationDao())
                dailySummary = summary
                topApps = apps
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    // ── Auto-load on first open and when period changes ──
    LaunchedEffect(selectedPeriod) {
        loadDataForPeriod(selectedPeriod)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── SECTION 1: Gradient Header with Logo ──
        item {
            GradientHeader()
        }

        // ── SECTION 1.5: Time Period Filter Chips ──
        item {
            TimePeriodFilterRow(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { period ->
                    selectedPeriod = period
                }
            )
        }

        // ── SECTION 2: Collapsible Settings ──
        item {
            CollapsibleSettingsCard(
                context = context,
                isExpanded = settingsExpanded,
                onToggle = { settingsExpanded = !settingsExpanded },
                onRerunSetup = onRerunSetup
            )
        }

        // ── SECTION 3: Privacy & Data (collapsible) ──
        item {
            CollapsiblePrivacyCard(
                isExpanded = privacyExpanded,
                onToggle = { privacyExpanded = !privacyExpanded },
                context = context
            )
        }

        // ── SECTION 4: Refresh Button ──
        item {
            Button(
                onClick = {
                    loadDataForPeriod(selectedPeriod)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AccentGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Loading...", color = AccentGreen, fontWeight = FontWeight.SemiBold)
                } else {
                    val periodLabel = when (selectedPeriod) {
                        InsightGenerator.TimePeriod.TODAY -> "Today"
                        InsightGenerator.TimePeriod.THIS_WEEK -> "This Week"
                        InsightGenerator.TimePeriod.THIS_MONTH -> "This Month"
                        InsightGenerator.TimePeriod.ALL -> "All Time"
                    }
                    Text(
                        "\uD83D\uDD04  Refresh $periodLabel",
                        color = AccentGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // ── SECTION 5: Daily Summary Card (animated) ──
        item {
            AnimatedVisibility(
                visible = dailySummary != null,
                enter = fadeIn(tween(500)) + expandVertically(tween(400)),
            ) {
                if (dailySummary != null) {
                    DailySummaryCard(dailySummary!!)
                }
            }
        }

        // ── SECTION 6: Spend Bar ──
        if (dailySummary != null && dailySummary!!.categories.isNotEmpty()) {
            item {
                SpendProportionBar(dailySummary!!)
            }
        }

        // ── SECTION 7: Top Apps by Spending ──
        if (topApps.isNotEmpty()) {
            item {
                TopAppsBySpendingCard(topApps)
            }
        }

        // ── SECTION 8: Transaction List ──
        if (dailySummary != null && dailySummary!!.categories.isNotEmpty()) {
            item {
                val periodLabel = when (selectedPeriod) {
                    InsightGenerator.TimePeriod.TODAY -> "Today's"
                    InsightGenerator.TimePeriod.THIS_WEEK -> "This Week's"
                    InsightGenerator.TimePeriod.THIS_MONTH -> "This Month's"
                    InsightGenerator.TimePeriod.ALL -> "All"
                }
                Text(
                    text = "\uD83D\uDCCB $periodLabel Transactions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val allItems = dailySummary!!.categories.flatMap { cat ->
                cat.items.map { item -> cat.category to item }
            }.sortedByDescending { it.second.timestamp }  // newest first

            items(allItems) { (category, item) ->
                TransactionCard(
                    category = category,
                    item = item,
                    onDelete = {
                        // Delete from DB + refresh the UI
                        thread {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                db.notificationDao().deleteById(item.dbId)
                                Log.d("DASHBOARD", "🗑️ Deleted transaction #${item.dbId}")

                                // Refresh summaries
                                val summary = InsightGenerator.generateDailySummary(db.notificationDao())
                                val apps = InsightGenerator.getTopAppsBySpending(db.notificationDao())
                                dailySummary = summary
                                topApps = apps
                            } catch (e: Exception) {
                                Log.e("DASHBOARD", "❌ Delete failed: ${e.message}")
                            }
                        }
                    },
                    onSplit = {
                        // Open split bottom sheet with this transaction's details
                        splitAmount = item.amount
                        splitMerchant = item.merchant
                        splitDescription = item.aiInsight ?: item.merchant
                        showSplitSheet = true
                    }
                )
            }
        }

        // ── SECTION 9: Empty state ──
        if (dailySummary != null && dailySummary!!.transactionCount == 0 && !isLoading) {
            item {
                EmptyStateCard()
            }
        }

        // ── SECTION 10: Weekly Insight Button + Result ──
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    isWeeklyLoading = true
                    thread {
                        val db = AppDatabase.getDatabase(context)
                        val insight = InsightGenerator.generateWeeklySummary(db.notificationDao())
                        weeklyInsight = insight
                        isWeeklyLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isWeeklyLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AccentPurple,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ShimmerDots()
                } else {
                    Text(
                        "\uD83E\uDDE0  Generate Weekly Insights",
                        color = AccentPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        if (weeklyInsight != null) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(500)) + expandVertically(tween(400)),
                ) {
                    WeeklyInsightCard(weeklyInsight!!)
                }
            }
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }

    // ── Splitwise Bottom Sheet ──
    if (showSplitSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSplitSheet = false },
            containerColor = Color.Transparent
        ) {
            SplitBottomSheet(
                amount = splitAmount,
                merchant = splitMerchant,
                description = splitDescription,
                onDismiss = { showSplitSheet = false },
                onSplitCreated = { showSplitSheet = false }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// GRADIENT HEADER — with app logo, date, status
// ═══════════════════════════════════════════════════════════════
@Composable
fun GradientHeader() {
    val today = remember {
        SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date())
    }

    // Pulsing alpha for the "Monitoring" indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientMid, GradientEnd)
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // App Logo
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    AccentGreen.copy(alpha = 0.3f),
                                    Color(0xFF0F3460)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\uD83D\uDCB0",
                        fontSize = 22.sp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Expense Intelligence",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = today,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Monitoring active",
                    fontSize = 12.sp,
                    color = AccentGreen.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// TIME PERIOD FILTER ROW — Today, This Week, This Month, All
// ═══════════════════════════════════════════════════════════════
@Composable
fun TimePeriodFilterRow(
    selectedPeriod: InsightGenerator.TimePeriod,
    onPeriodSelected: (InsightGenerator.TimePeriod) -> Unit
) {
    val periods = listOf(
        InsightGenerator.TimePeriod.TODAY to "Today",
        InsightGenerator.TimePeriod.THIS_WEEK to "Week",
        InsightGenerator.TimePeriod.THIS_MONTH to "Month",
        InsightGenerator.TimePeriod.ALL to "All"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        periods.forEach { (period, label) ->
            val isSelected = selectedPeriod == period
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) AccentGreen else SurfaceCard
                    )
                    .clickable { onPeriodSelected(period) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.Black else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// COLLAPSIBLE SETTINGS — tap to expand/collapse
// ═══════════════════════════════════════════════════════════════
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CollapsibleSettingsCard(
    context: Context,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRerunSetup: () -> Unit = {}
) {
    val hasUsage = hasUsageStatsPermission(context)
    val hasNotifListener = isNotificationListenerEnabled(context)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\u2699\uFE0F",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Settings & Controls",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (isExpanded) "\u25B2" else "\u25BC",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            // Permission status summary (always visible)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hasNotifListener) AccentGreen else AccentRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasNotifListener) "Notifications" else "Notif. needed",
                        fontSize = 12.sp,
                        color = if (hasNotifListener) AccentGreen.copy(alpha = 0.8f) else AccentRed.copy(alpha = 0.8f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hasUsage) AccentGreen else AccentRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasUsage) "Usage Access" else "Usage needed",
                        fontSize = 12.sp,
                        color = if (hasUsage) AccentGreen.copy(alpha = 0.8f) else AccentRed.copy(alpha = 0.8f)
                    )
                }
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Service controls
                    Text(
                        "Background Service",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(context, MyForegroundService::class.java)
                                context.startForegroundService(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("\u25B6  Start", color = AccentGreen)
                        }
                        Button(
                            onClick = {
                                val intent = Intent(context, MyForegroundService::class.java)
                                context.stopService(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("\u25A0  Stop", color = AccentRed)
                        }
                    }

                    // Permission grant buttons
                    if (!hasNotifListener) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Notification Access", color = AccentPurple)
                        }
                    }

                    if (!hasUsage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Usage Access Permission", color = AccentPurple)
                        }
                    }

                    // Overlay permission check (required for popup when app is in background)
                    val hasOverlayPermission = Settings.canDrawOverlays(context)
                    if (!hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⚠️ Enable Display Over Other Apps", color = AccentGold)
                        }
                        Text(
                            text = "Required for category popup when app is in background",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Notification listener hint (always shown)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Notification Listener Settings", color = AccentPurple)
                    }

                    // Re-run Setup button
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRerunSetup,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("\uD83D\uDD04  Re-run Setup Wizard", color = TextMuted)
                    }

                    // ── Splitwise Integration ──
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

                    val isSplitwiseConnected = SplitwiseManager.isLoggedIn(context)

                    Text(
                        "Splitwise Integration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isSplitwiseConnected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connected to Splitwise", color = AccentGreen, fontSize = 13.sp)
                            }
                            TextButton(onClick = {
                                SplitwiseManager.logout(context)
                                Toast.makeText(context, "Disconnected from Splitwise", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Disconnect", color = AccentRed, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = { SplitwiseManager.startLogin(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5BC5A7).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔗 Connect Splitwise", color = Color(0xFF5BC5A7))
                        }
                        Text(
                            text = "Split expenses with friends directly from transactions",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // ── Popup Settings ──
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Category Popup",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Get current popup mode from SharedPreferences
                    val prefs = context.getSharedPreferences("expense_intelligence_prefs", Context.MODE_PRIVATE)
                    var popupMode by remember {
                        mutableStateOf(prefs.getString("popup_mode", "smart") ?: "smart")
                    }

                    // Option A: All Payments
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (popupMode == "all") AccentPurple.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                popupMode = "all"
                                prefs.edit().putString("popup_mode", "all").apply()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (popupMode == "all") AccentPurple else SurfaceCard)
                                .padding(4.dp)
                        ) {
                            if (popupMode == "all") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("All Payments", color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("Show popup for every payment", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option B: Smart (Unknown merchants only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (popupMode == "smart") AccentPurple.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                popupMode = "smart"
                                prefs.edit().putString("popup_mode", "smart").apply()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (popupMode == "smart") AccentPurple else SurfaceCard)
                                .padding(4.dp)
                        ) {
                            if (popupMode == "smart") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Unknown Merchants Only", color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("Skip popup for known apps (Zomato, Uber, etc.)", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// COLLAPSIBLE PRIVACY & DATA CARD
// ═══════════════════════════════════════════════════════════════
@Composable
fun CollapsiblePrivacyCard(isExpanded: Boolean, onToggle: () -> Unit, context: Context) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83D\uDEE1\uFE0F", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Privacy & Data",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (isExpanded) "\u25B2" else "\u25BC",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            // Quick summary (always visible)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\uD83D\uDD12 All data stored locally • No cloud • No tracking",
                fontSize = 12.sp,
                color = AccentGreen.copy(alpha = 0.7f)
            )

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Full privacy disclaimer
                    PrivacyDisclaimerCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Delete All Data button
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "\uD83D\uDDD1\uFE0F  Delete All My Data",
                            color = AccentRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "This permanently erases all stored transactions and usage data.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                thread {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        db.clearAllTables()
                    } catch (_: Exception) {}
                }
                Toast.makeText(context, "All data deleted!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                "\u26A0\uFE0F Delete All Data?",
                color = AccentRed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "This will permanently delete ALL your stored transactions, app usage data, AI insights, and weekly summaries. This action cannot be undone.",
                color = TextSecondary,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Delete Everything", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════
// TOP APPS BY SPENDING — ranked list card
// ═══════════════════════════════════════════════════════════════
@Composable
fun TopAppsBySpendingCard(topApps: List<InsightGenerator.AppSpending>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83C\uDFC6", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Top Spending Apps",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "All-time spending by app",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Find max for proportion bar
                val maxSpent = topApps.maxOfOrNull { it.totalSpent } ?: 1.0

                topApps.forEachIndexed { index, app ->
                    TopAppRow(
                        rank = index,
                        app = app,
                        maxSpent = maxSpent
                    )
                    if (index < topApps.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAppRow(rank: Int, app: InsightGenerator.AppSpending, maxSpent: Double) {
    val catColor = getCategoryColor(app.category)
    val catIcon = getCategoryIcon(app.category)
    val proportion = (app.totalSpent / maxSpent).toFloat().coerceIn(0.05f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = getRankEmoji(rank),
                    fontSize = if (rank < 3) 20.sp else 14.sp,
                    modifier = Modifier.width(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = catIcon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = app.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "${app.transactionCount} transaction${if (app.transactionCount > 1) "s" else ""} • ${app.category}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                text = "\u20B9${String.format("%.0f", app.totalSpent)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (rank == 0) AccentGold else Color.White
            )
        }

        // Mini proportion bar
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(proportion)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(catColor.copy(alpha = 0.7f))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// DAILY SUMMARY CARD — gradient background, category breakdown
// ═══════════════════════════════════════════════════════════════
@Composable
fun DailySummaryCard(summary: InsightGenerator.DailySummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (summary.totalSpent > 0)
                        "\u20B9${String.format("%.0f", summary.totalSpent)}"
                    else "No spending",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (summary.transactionCount > 0)
                        "spent today \u2022 ${summary.transactionCount} transaction(s)"
                    else "recorded today",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                if (summary.categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    summary.categories.forEach { cat ->
                        val catColor = getCategoryColor(cat.category)
                        val catIcon = getCategoryIcon(cat.category)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$catIcon ${cat.category}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            Text(
                                text = "\u20B9${String.format("%.0f", cat.total)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SPEND PROPORTION BAR — visual breakdown of spend categories
// ═══════════════════════════════════════════════════════════════
@Composable
fun SpendProportionBar(summary: InsightGenerator.DailySummary) {
    if (summary.totalSpent <= 0) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spending Breakdown",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Proportional colored bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                summary.categories.forEach { cat ->
                    val fraction = (cat.total / summary.totalSpent).toFloat()
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(fraction)
                                .height(12.dp)
                                .background(getCategoryColor(cat.category))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend — wraps to next line if too many categories
            // Using Column with Row chunks to prevent horizontal overflow
            val legendChunks = summary.categories.chunked(3) // 3 items per row
            legendChunks.forEach { chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    chunk.forEach { cat ->
                        val pct = ((cat.total / summary.totalSpent) * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(getCategoryColor(cat.category))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${cat.category.take(10)} $pct%",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// TRANSACTION CARD — colored side bar, necessity badge
// ═══════════════════════════════════════════════════════════════
@Composable
fun TransactionCard(
    category: String,
    item: InsightGenerator.TransactionItem,
    onDelete: () -> Unit = {},
    onSplit: () -> Unit = {}
) {
    val context = LocalContext.current
    val catColor = getCategoryColor(category)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isSplitwiseConnected = remember { SplitwiseManager.isLoggedIn(context) }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceCard,
            title = {
                Text("Delete Transaction?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Remove ₹${String.format("%.0f", item.amount)} to ${item.merchant} from your records?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)  // Allows fillMaxHeight on children
        ) {
            // Colored left bar — stretches to match the content height
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        catColor,
                        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.merchant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\u20B9${String.format("%.0f", item.amount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                        // 📤 Split button (only show if Splitwise connected)
                        if (isSplitwiseConnected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📤",
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSplit() }
                                    .background(
                                        Color(0xFF5BC5A7).copy(alpha = 0.15f),
                                        CircleShape
                                    )
                                    .padding(6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        // 🗑️ Delete button
                        Text(
                            text = "✕",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showDeleteConfirm = true }
                                .background(
                                    TextMuted.copy(alpha = 0.1f),
                                    CircleShape
                                )
                                .padding(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // AI description
                if (item.aiInsight != null) {
                    Text(
                        text = item.aiInsight,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Category chip
                        Text(
                            text = "${getCategoryIcon(category)} $category",
                            fontSize = 11.sp,
                            color = catColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(
                                    catColor.copy(alpha = 0.15f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )

                        // Necessity badge
                        if (item.necessity != null) {
                            val isNeed = item.necessity.lowercase() == "need"
                            val badgeColor = if (isNeed) Color(0xFF26A69A) else Color(0xFFFF7043)
                            Text(
                                text = if (isNeed) "NEED" else "WANT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier
                                    .background(
                                        badgeColor.copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = item.time,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// EMPTY STATE — shown when no transactions today
// ═══════════════════════════════════════════════════════════════
@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uD83D\uDCED",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No spending recorded today",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Text(
                text = "Your transactions will appear here as they're detected",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// WEEKLY AI INSIGHTS — per-insight rows with dividers
// ═══════════════════════════════════════════════════════════════
@Composable
fun WeeklyInsightCard(insight: String) {
    val insightLines = remember(insight) {
        insight.split("\n").filter { it.isNotBlank() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF311B92), Color(0xFF4A148C), Color(0xFF6A1B9A))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "\uD83E\uDDE0 Weekly Behavioral Insights",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                insightLines.forEachIndexed { index, line ->
                    Text(
                        text = line,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (index < insightLines.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SHIMMER DOTS — animated "AI analyzing..." text
// ═══════════════════════════════════════════════════════════════
@Composable
fun ShimmerDots() {
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = (dotCount % 3) + 1
        }
    }

    Text(
        text = "AI Analyzing" + ".".repeat(dotCount),
        color = AccentPurple,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )
}

// ═══════════════════════════════════════════════════════════════
// HELPER: Check Usage Stats Permission
// ═══════════════════════════════════════════════════════════════
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}
