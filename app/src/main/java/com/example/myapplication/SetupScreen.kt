package com.example.myapplication

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════
// COLORS (reuse from main palette)
// ═══════════════════════════════════════════════════════════════
private val SetupBg = Color(0xFF1A1A2E)
private val SetupCard = Color(0xFF2A2A3E)
private val SetupAccentGreen = Color(0xFF00E676)
private val SetupAccentPurple = Color(0xFFBB86FC)
private val SetupAccentRed = Color(0xFFFF5252)
private val SetupAccentBlue = Color(0xFF42A5F5)
private val SetupAccentOrange = Color(0xFFFF9800)
private val SetupTextPrimary = Color(0xFFECECEC)
private val SetupTextSecondary = Color(0xFFA0A0B0)
private val SetupTextMuted = Color(0xFF6C6C7E)

// Total number of setup steps
private const val TOTAL_STEPS = 6

/**
 * The main Setup Wizard composable.
 * STEP ORDER (user-requested):
 *   0. Welcome + Privacy
 *   1. Play Protect (disable scanning so sideloaded app isn't blocked)
 *   2. App Settings (Allow Restricted Settings + Unrestricted Battery)
 *   3. Notification Access
 *   4. Usage Access
 *   5. All Set!
 */
@Composable
fun SetupWizard(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    // Periodically re-check permissions so the UI updates when user returns from settings
    var notifListenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var usageAccessEnabled by remember { mutableStateOf(hasUsagePermission(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }

    LaunchedEffect(currentStep) {
        while (true) {
            delay(1000)
            notifListenerEnabled = isNotificationListenerEnabled(context)
            usageAccessEnabled = hasUsagePermission(context)
            batteryUnrestricted = isBatteryUnrestricted(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SetupBg)
    ) {
        // ── Progress indicator ──
        SetupProgressBar(currentStep)

        // ── Step content (animated transitions) ──
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            modifier = Modifier.weight(1f),
            label = "stepTransition"
        ) { step ->
            when (step) {
                0 -> WelcomeStep()
                1 -> PlayProtectStep(context)
                2 -> AppSettingsStep(batteryUnrestricted, context)
                3 -> NotificationAccessStep(notifListenerEnabled, context)
                4 -> UsageAccessStep(usageAccessEnabled, context)
                5 -> AllSetStep(context, onSetupComplete)
            }
        }

        // ── Navigation buttons ──
        SetupNavButtons(
            currentStep = currentStep,
            totalSteps = TOTAL_STEPS,
            onBack = { if (currentStep > 0) currentStep-- },
            onNext = { if (currentStep < TOTAL_STEPS - 1) currentStep++ },
            onFinish = onSetupComplete
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// PROGRESS BAR
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SetupProgressBar(currentStep: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Setup",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = SetupTextMuted
            )
            Text(
                text = "${currentStep + 1} of $TOTAL_STEPS",
                fontSize = 14.sp,
                color = SetupTextMuted
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 0 until TOTAL_STEPS) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= currentStep) SetupAccentGreen
                            else SetupTextMuted.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// STEP 0: WELCOME + PRIVACY
// ═══════════════════════════════════════════════════════════════
@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F3460), Color(0xFF1A1A2E))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\uD83D\uDCB0", fontSize = 48.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Expense Intelligence",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = SetupTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "AI-Powered Personal Finance Tracker",
            fontSize = 15.sp,
            color = SetupAccentPurple,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureRow("\uD83D\uDD14", "Captures payment notifications automatically")
        FeatureRow("\uD83E\uDDE0", "AI understands what you bought and why")
        FeatureRow("\uD83D\uDCCA", "Smart insights on your spending patterns")
        FeatureRow("\uD83D\uDD12", "100% private — everything stays on your phone")

        Spacer(modifier = Modifier.height(24.dp))

        PrivacyDisclaimerCard()
    }
}

// ═══════════════════════════════════════════════════════════════
// STEP 1: PLAY PROTECT  (must be done first!)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PlayProtectStep(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "\uD83D\uDEE1\uFE0F", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Disable Play Protect",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SetupTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Since this app is sideloaded (not from Play Store), Google Play Protect may block it or remove permissions. You must disable scanning first.",
            fontSize = 15.sp,
            color = SetupTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Warning card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SetupAccentRed.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\u26A0\uFE0F", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Do this FIRST!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SetupAccentRed
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "If you skip this step, Play Protect may silently revoke permissions you grant in later steps.",
                    fontSize = 13.sp,
                    color = SetupTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // Open Play Store → Play Protect
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://play-protect-settings"))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Fallback: open Play Store
                    val intent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
                    if (intent != null) context.startActivity(intent)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SetupAccentGreen),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "Open Play Store",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        InstructionCard(
            title = "How to disable Play Protect scanning:",
            steps = listOf(
                "Open the Google Play Store app",
                "Tap your Profile icon (top right)",
                "Tap \"Play Protect\"",
                "Tap the Settings icon (\u2699\uFE0F) in the top right",
                "Turn OFF \"Scan apps with Play Protect\"",
                "Confirm by tapping \"Turn off\"",
                "Come back here and tap Next"
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SetupAccentBlue.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "\uD83D\uDCA1 You can turn Play Protect back ON after completing all setup steps.",
                    fontSize = 13.sp,
                    color = SetupAccentBlue,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// STEP 2: APP SETTINGS (Restricted Settings + Battery)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AppSettingsStep(isBatteryUnrestricted: Boolean, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "\uD83D\uDD13", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "App Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SetupTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Two important settings to configure before enabling permissions.",
            fontSize = 15.sp,
            color = SetupTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── PART A: Allow Restricted Settings ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SetupCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83D\uDD13", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "1. Allow Restricted Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SetupAccentOrange
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Without this, permission toggles will be greyed out in the next steps.",
                    fontSize = 13.sp,
                    color = SetupTextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Steps
                val steps = listOf(
                    "Go to Settings → Apps",
                    "Find \"Expense Intelligence\" (or \"My Application\")",
                    "Tap ⋮ (three dots) in the top right corner",
                    "Tap \"Allow restricted settings\"",
                )
                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 13.sp,
                            color = SetupAccentOrange,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = SetupTextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open App Info", color = SetupAccentOrange)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── PART B: Unrestricted Battery ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SetupCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83D\uDD0B", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "2. Unrestricted Battery Access",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SetupAccentPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status badge
                val bgColor = if (isBatteryUnrestricted) SetupAccentGreen.copy(alpha = 0.15f) else SetupAccentRed.copy(alpha = 0.15f)
                val textColor = if (isBatteryUnrestricted) SetupAccentGreen else SetupAccentRed
                val icon = if (isBatteryUnrestricted) "\u2705" else "\u274C"
                val label = if (isBatteryUnrestricted) "Unrestricted" else "Not Set"

                Card(
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = icon, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Without this, Android will kill the background service and stop capturing payments.",
                    fontSize = 13.sp,
                    color = SetupTextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                val batterySteps = listOf(
                    "Tap the button below to open Battery settings",
                    "Select \"Unrestricted\" (not \"Optimized\" or \"Restricted\")",
                    "Come back here — the status updates automatically"
                )
                batterySteps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 13.sp,
                            color = SetupAccentPurple,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = SetupTextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        try {
                            // Try to open battery optimization settings directly for our app
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback: open general battery optimization settings
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SetupAccentPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Open Battery Settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// STEP 3: NOTIFICATION ACCESS
// ═══════════════════════════════════════════════════════════════
@Composable
private fun NotificationAccessStep(isEnabled: Boolean, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "\uD83D\uDD14", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enable Notification Access",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SetupTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This allows the app to read payment notifications from GPay, PhonePe, banks, and other apps.",
            fontSize = 15.sp,
            color = SetupTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        PermissionStatusBadge(isEnabled)

        Spacer(modifier = Modifier.height(24.dp))

        if (!isEnabled) {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SetupAccentGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Open Notification Settings",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            InstructionCard(
                title = "How to enable:",
                steps = listOf(
                    "Tap the button above to open settings",
                    "Find \"Expense Intelligence\" in the list",
                    "Toggle it ON",
                    "If greyed out: Go back to Step 2 and \"Allow restricted settings\" first",
                    "Come back here — the status will update automatically"
                )
            )
        } else {
            Text(
                text = "You're all set! Notification access is enabled.",
                fontSize = 15.sp,
                color = SetupAccentGreen,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SetupAccentOrange.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\u26A0\uFE0F", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Important!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SetupAccentOrange
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Make sure notifications are turned ON for your payment apps (GPay, PhonePe, bank apps, SMS). If their notifications are silenced, we can't capture payments.",
                    fontSize = 13.sp,
                    color = SetupTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// STEP 4: USAGE ACCESS
// ═══════════════════════════════════════════════════════════════
@Composable
private fun UsageAccessStep(isEnabled: Boolean, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "\uD83D\uDCF2", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enable Usage Access",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SetupTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This allows the app to see which app you used before a payment — so we know if ₹250 was a Zomato order or an Uber ride.",
            fontSize = 15.sp,
            color = SetupTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        PermissionStatusBadge(isEnabled)

        Spacer(modifier = Modifier.height(24.dp))

        if (!isEnabled) {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SetupAccentGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Open Usage Access Settings",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            InstructionCard(
                title = "How to enable:",
                steps = listOf(
                    "Tap the button above to open settings",
                    "Find \"Expense Intelligence\" in the list",
                    "Toggle it ON",
                    "If greyed out: Go back to Step 2 and \"Allow restricted settings\" first",
                    "Come back here — the status will update automatically"
                )
            )
        } else {
            Text(
                text = "Usage access is enabled! We can now correlate payments with apps.",
                fontSize = 15.sp,
                color = SetupAccentGreen,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// STEP 5: ALL SET!
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AllSetStep(context: Context, onSetupComplete: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "allSetPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "\u2705",
            fontSize = (56 * scale).sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "You're All Set!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = SetupAccentGreen
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The app will now start monitoring your payments and building your expense intelligence.",
            fontSize = 15.sp,
            color = SetupTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SetupCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "What happens next?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SetupTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                NextStepRow("1\uFE0F\u20E3", "The background service will start automatically")
                NextStepRow("2\uFE0F\u20E3", "Make a payment (GPay, PhonePe, etc.)")
                NextStepRow("3\uFE0F\u20E3", "The app captures the notification")
                NextStepRow("4\uFE0F\u20E3", "AI analyzes what you bought and why")
                NextStepRow("5\uFE0F\u20E3", "Check your dashboard for insights!")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reminder card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SetupAccentBlue.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "\uD83D\uDCA1 You can now re-enable Google Play Protect if you disabled it earlier. The permissions are already saved.",
                    fontSize = 13.sp,
                    color = SetupAccentBlue,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val intent = Intent(context, MyForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                onSetupComplete()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SetupAccentGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "\uD83D\uDE80  Start Tracking!",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FeatureRow(icon: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            color = SetupTextSecondary,
            lineHeight = 20.sp
        )
    }
}

/**
 * Privacy disclaimer card — used in both setup wizard and dashboard settings.
 */
@Composable
fun PrivacyDisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D1E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83D\uDEE1\uFE0F", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Your Privacy Matters",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SetupAccentGreen
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            PrivacyBullet("\uD83D\uDCF1", "All data stored locally on YOUR phone only — we use Room (local database), not any cloud server")
            PrivacyBullet("\uD83D\uDEAB", "We NEVER collect, store, or share your personal or financial data with anyone")
            PrivacyBullet("\uD83E\uDD16", "The only internet call is to AI (Groq) — only a brief text summary is sent, never your full data or bank details")
            PrivacyBullet("\uD83D\uDD14", "Notification access reads ONLY payment notifications — not personal messages, chats, or social media")
            PrivacyBullet("\uD83D\uDCF2", "Usage access detects which app you used before a payment — to show context like \"Zomato order\" instead of just \"₹250 paid\"")
            PrivacyBullet("\uD83D\uDDD1\uFE0F", "Uninstall the app = ALL your data is permanently deleted. You can also clear data from Settings anytime")
            PrivacyBullet("\uD83D\uDD11", "No accounts, no login, no tracking, no analytics. Your phone is your private expense diary")

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SetupAccentGreen.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This app is open source. You can verify everything by reading the code.",
                fontSize = 12.sp,
                color = SetupTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PrivacyBullet(icon: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = SetupTextSecondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun PermissionStatusBadge(isEnabled: Boolean) {
    val bgColor = if (isEnabled) SetupAccentGreen.copy(alpha = 0.15f) else SetupAccentRed.copy(alpha = 0.15f)
    val textColor = if (isEnabled) SetupAccentGreen else SetupAccentRed
    val icon = if (isEnabled) "\u2705" else "\u274C"
    val label = if (isEnabled) "Enabled" else "Not Enabled"

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun InstructionCard(title: String, steps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SetupCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = SetupTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${index + 1}.",
                        fontSize = 13.sp,
                        color = SetupAccentGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = step,
                        fontSize = 13.sp,
                        color = SetupTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NextStepRow(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = number, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = SetupTextSecondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SetupNavButtons(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep > 0) {
            TextButton(onClick = onBack) {
                Text(
                    "\u2190  Back",
                    color = SetupTextMuted,
                    fontSize = 15.sp
                )
            }
        } else {
            Spacer(modifier = Modifier.width(80.dp))
        }

        if (currentStep < totalSteps - 1) {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = SetupAccentGreen.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Next  \u2192",
                    color = SetupAccentGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        } else {
            Spacer(modifier = Modifier.width(80.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// PERMISSION CHECK HELPERS
// ═══════════════════════════════════════════════════════════════

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (flat.isNullOrEmpty()) return false
    val myComponent = ComponentName(context, MyNotificationListenerService::class.java).flattenToString()
    return flat.contains(myComponent)
}

fun hasUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * Checks if battery optimization is disabled (unrestricted) for our app.
 */
fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
