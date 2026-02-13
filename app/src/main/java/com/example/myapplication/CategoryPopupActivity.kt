package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.concurrent.thread

/**
 * A transparent overlay activity that appears when an offline/unknown payment is detected.
 * User can quickly categorize the payment and optionally add a note.
 *
 * FLOW:
 * 1. NotificationListenerService detects an offline payment (no app correlated)
 * 2. This activity is launched as an overlay
 * 3. User selects a category (time-based suggestions shown first)
 * 4. We save the category AND learn the merchant for future auto-categorization
 */
class CategoryPopupActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_MERCHANT = "merchant"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: "?"
        val merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: "Unknown"
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())

        setContent {
            CategoryPopupScreen(
                transactionId = transactionId,
                amount = amount,
                merchant = merchant,
                timestamp = timestamp,
                onDismiss = { finish() },
                onCategorySaved = { category, subcategory, note ->
                    saveCategoryAndLearn(transactionId, merchant, category, subcategory, note)
                    finish()
                }
            )
        }
    }

    private fun saveCategoryAndLearn(
        transactionId: Int,
        merchant: String,
        category: String,
        subcategory: String?,
        note: String?
    ) {
        thread {
            val db = AppDatabase.getDatabase(applicationContext)

            // 1. Update the transaction with user's category
            val finalCategory = subcategory ?: category
            db.notificationDao().updateCorrelation(
                id = transactionId,
                category = finalCategory,
                correlatedApp = null,
                confidence = "user"
            )

            // 2. Save the merchant alias for future auto-categorization
            val normalizedName = merchant.lowercase().trim()
            val existing = db.merchantAliasDao().findByName(normalizedName)

            if (existing != null) {
                // Update existing alias
                db.merchantAliasDao().insert(
                    existing.copy(
                        category = category,
                        subcategory = subcategory,
                        userNote = note ?: existing.userNote,
                        timesUsed = existing.timesUsed + 1,
                        lastUsedAt = System.currentTimeMillis()
                    )
                )
            } else {
                // Create new alias
                db.merchantAliasDao().insert(
                    MerchantAliasEntity(
                        merchantName = merchant,
                        normalizedName = normalizedName,
                        category = category,
                        subcategory = subcategory,
                        userNote = note
                    )
                )
            }

            // 3. If user provided a note, update the AI insight field with it
            if (!note.isNullOrBlank()) {
                db.notificationDao().updateAiInsight(transactionId, note)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// COLOR PALETTE (matching main app theme)
// ═══════════════════════════════════════════════════════════════
private val AccentGreen = Color(0xFF00E676)
private val SplitwiseGreen = Color(0xFF5BC5A7)  // Splitwise brand color
private val SurfaceCard = Color(0xFF2A2A3E)
private val SurfaceDark = Color(0xFF1E1E2E)
private val TextPrimary = Color(0xFFECECEC)
private val TextSecondary = Color(0xFFA0A0B0)
private val TextMuted = Color(0xFF6C6C7E)

// ═══════════════════════════════════════════════════════════════
// COMPOSE UI FOR THE POPUP
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPopupScreen(
    transactionId: Int,
    amount: String,
    merchant: String,
    timestamp: Long,
    onDismiss: () -> Unit,
    onCategorySaved: (category: String, subcategory: String?, note: String?) -> Unit
) {
    val suggestions = remember { TimeSuggestionEngine.getTopSuggestions(timestamp, 9) }
    var selectedSuggestion by remember { mutableStateOf<TimeSuggestionEngine.CategorySuggestion?>(null) }
    var userNote by remember { mutableStateOf("") }
    var showNoteField by remember { mutableStateOf(false) }

    // Semi-transparent dark background that dismisses on tap
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // The actual popup card — prevent click-through
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume click */ },
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "💸 Quick Categorize",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Transaction info
                Text(
                    text = "₹$amount",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
                Text(
                    text = "to $merchant",
                    fontSize = 16.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Category prompt
                Text(
                    text = "What was this for?",
                    fontSize = 14.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 220.dp)
                ) {
                    items(suggestions) { suggestion ->
                        CategoryChip(
                            suggestion = suggestion,
                            isSelected = selectedSuggestion == suggestion,
                            onClick = { selectedSuggestion = suggestion }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional note field
                if (showNoteField) {
                    OutlinedTextField(
                        value = userNote,
                        onValueChange = { userNote = it.take(50) },
                        placeholder = { Text("Add a quick note...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = SurfaceCard,
                            cursorColor = AccentGreen
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    TextButton(onClick = { showNoteField = true }) {
                        Text("➕ Add note (optional)", color = TextMuted, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons - Row 1: Skip & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextMuted
                        )
                    ) {
                        Text("Skip")
                    }

                    Button(
                        onClick = {
                            selectedSuggestion?.let {
                                onCategorySaved(
                                    it.category,
                                    it.subcategory,
                                    userNote.ifBlank { null }
                                )
                            }
                        },
                        enabled = selectedSuggestion != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            disabledContainerColor = SurfaceCard
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Save",
                            color = if (selectedSuggestion != null) Color.Black else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── SPLITWISE BUTTON ──
                val context = LocalContext.current
                Button(
                    onClick = {
                        // First save the category if selected
                        selectedSuggestion?.let {
                            onCategorySaved(
                                it.category,
                                it.subcategory,
                                userNote.ifBlank { null }
                            )
                        }
                        
                        // Open Splitwise app with pre-filled data
                        val description = if (userNote.isNotBlank()) {
                            "$merchant - $userNote"
                        } else if (selectedSuggestion != null) {
                            "$merchant (${selectedSuggestion!!.subcategory})"
                        } else {
                            merchant
                        }
                        
                        // Try to open Splitwise app directly
                        try {
                            val splitwiseIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("splitwise://addexpense")
                                putExtra("cost", amount)
                                putExtra("description", description)
                            }
                            
                            // Check if Splitwise is installed
                            val packageManager = context.packageManager
                            val splitwisePackage = "com.Splitwise.SplitwiseMobile"
                            
                            try {
                                packageManager.getPackageInfo(splitwisePackage, 0)
                                // Splitwise is installed - open it
                                val launchIntent = packageManager.getLaunchIntentForPackage(splitwisePackage)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                    Toast.makeText(
                                        context,
                                        "📋 Add expense: ₹$amount for $description",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                // Splitwise not installed - open Play Store
                                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://play.google.com/store/apps/details?id=$splitwisePackage")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(playStoreIntent)
                                Toast.makeText(context, "Install Splitwise to split expenses!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Splitwise", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SplitwiseGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "💚",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Add to Splitwise",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Hint text
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "We'll remember this for next time! 🧠",
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    suggestion: TimeSuggestionEngine.CategorySuggestion,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) AccentGreen else SurfaceCard
    val textColor = if (isSelected) Color.Black else TextPrimary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = suggestion.emoji, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = suggestion.subcategory.take(12),
                fontSize = 11.sp,
                color = textColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

