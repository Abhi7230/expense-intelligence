package com.example.myapplication

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay
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
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ExpenseDashboard(modifier = Modifier.padding(innerPadding))
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
// COLOR PALETTE
// ═══════════════════════════════════════════════════════════════
private val GradientStart = Color(0xFF1A1A2E)
private val GradientMid = Color(0xFF16213E)
private val GradientEnd = Color(0xFF0F3460)
private val AccentGreen = Color(0xFF00E676)
private val AccentPurple = Color(0xFFBB86FC)
private val AccentRed = Color(0xFFFF5252)
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
        lower.contains("transport") || lower.contains("travel") -> Color(0xFF42A5F5)  // Blue
        lower.contains("shopping") -> Color(0xFFEC407A)    // Pink
        lower.contains("entertainment") -> Color(0xFFAB47BC) // Purple
        lower.contains("bill") || lower.contains("recharge") -> Color(0xFF26A69A) // Teal
        lower.contains("grocery") -> Color(0xFF66BB6A)     // Green
        lower.contains("health") || lower.contains("medicine") -> Color(0xFFEF5350) // Red
        else -> Color(0xFF78909C)                          // Blue-grey default
    }
}

private fun getCategoryIcon(category: String): String {
    val lower = category.lowercase()
    return when {
        lower.contains("food") -> "\uD83C\uDF54"           // burger
        lower.contains("transport") || lower.contains("travel") -> "\uD83D\uDE95" // taxi
        lower.contains("shopping") -> "\uD83D\uDED2"       // cart
        lower.contains("entertainment") -> "\uD83C\uDFAC"  // clapper
        lower.contains("bill") || lower.contains("recharge") -> "\uD83D\uDCB3" // card
        lower.contains("grocery") -> "\uD83E\uDD66"        // broccoli
        lower.contains("health") || lower.contains("medicine") -> "\uD83D\uDC8A" // pill
        else -> "\uD83D\uDCB0"                             // money bag
    }
}

// ═══════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ExpenseDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var dailySummary by remember { mutableStateOf<InsightGenerator.DailySummary?>(null) }
    var weeklyInsight by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isWeeklyLoading by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }

    // ── Auto-load daily summary on first open ──
    LaunchedEffect(Unit) {
        isLoading = true
        thread {
            try {
                val db = AppDatabase.getDatabase(context)
                val summary = InsightGenerator.generateDailySummary(db.notificationDao())
                dailySummary = summary
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── SECTION 1: Gradient Header ──
        item {
            GradientHeader()
        }

        // ── SECTION 2: Collapsible Settings ──
        item {
            CollapsibleSettingsCard(
                context = context,
                isExpanded = settingsExpanded,
                onToggle = { settingsExpanded = !settingsExpanded }
            )
        }

        // ── SECTION 3: Refresh Button ──
        item {
            Button(
                onClick = {
                    isLoading = true
                    thread {
                        val db = AppDatabase.getDatabase(context)
                        val summary = InsightGenerator.generateDailySummary(db.notificationDao())
                        dailySummary = summary
                        isLoading = false
                    }
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
                    Text(
                        "\uD83D\uDD04  Refresh Today's Summary",
                        color = AccentGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // ── SECTION 4: Daily Summary Card (animated) ──
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

        // ── SECTION 5: Spend Bar ──
        if (dailySummary != null && dailySummary!!.categories.isNotEmpty()) {
            item {
                SpendProportionBar(dailySummary!!)
            }
        }

        // ── SECTION 6: Transaction List ──
        if (dailySummary != null && dailySummary!!.categories.isNotEmpty()) {
            item {
                Text(
                    text = "\uD83D\uDCCB Today's Transactions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val allItems = dailySummary!!.categories.flatMap { cat ->
                cat.items.map { item -> cat.category to item }
            }

            items(allItems) { (category, item) ->
                TransactionCard(category, item)
            }
        }

        // ── SECTION 7: Empty state ──
        if (dailySummary != null && dailySummary!!.transactionCount == 0 && !isLoading) {
            item {
                EmptyStateCard()
            }
        }

        // ── SECTION 8: Weekly Insight Button + Result ──
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
}

// ═══════════════════════════════════════════════════════════════
// GRADIENT HEADER — app title, date, status
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
            Text(
                text = "\uD83D\uDCB0 Expense Intelligence",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = today,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
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
// COLLAPSIBLE SETTINGS — tap to expand/collapse
// ═══════════════════════════════════════════════════════════════
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CollapsibleSettingsCard(context: Context, isExpanded: Boolean, onToggle: () -> Unit) {
    val hasUsage = hasUsageStatsPermission(context)

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (hasUsage) AccentGreen else AccentRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasUsage) "Usage Access granted" else "Usage Access needed",
                    fontSize = 13.sp,
                    color = if (hasUsage) AccentGreen.copy(alpha = 0.8f) else AccentRed.copy(alpha = 0.8f)
                )
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

                    // Permission grant button
                    if (!hasUsage) {
                        Spacer(modifier = Modifier.height(16.dp))
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

                    // Notification listener hint
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Notification Listener Settings", color = AccentPurple)
                    }
                }
            }
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
                                // Colored dot
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

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                summary.categories.forEach { cat ->
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
                            text = "${cat.category.take(8)} $pct%",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
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
fun TransactionCard(category: String, item: InsightGenerator.TransactionItem) {
    val catColor = getCategoryColor(category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Colored left bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(110.dp)
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
                    Text(
                        text = "\u20B9${String.format("%.0f", item.amount)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
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
