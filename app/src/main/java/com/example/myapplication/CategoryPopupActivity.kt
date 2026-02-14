package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
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

                // ── SPLITWISE INTEGRATION ──
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val isSplitwiseLoggedIn = remember { SplitwiseManager.isLoggedIn(context) }
                var showSplitOptions by remember { mutableStateOf(false) }
                var groups by remember { mutableStateOf<List<SplitwiseManager.SplitwiseGroup>>(emptyList()) }
                var selectedGroup by remember { mutableStateOf<SplitwiseManager.SplitwiseGroup?>(null) }
                var selectedMembers by remember { mutableStateOf<Set<Long>>(emptySet()) }
                var isLoadingGroups by remember { mutableStateOf(false) }
                var isCreatingExpense by remember { mutableStateOf(false) }
                val currentUserId = remember { SplitwiseManager.getCurrentUserId(context) }

                if (isSplitwiseLoggedIn) {
                    if (!showSplitOptions) {
                        // Show "Add to Splitwise" button
                        Button(
                            onClick = {
                                showSplitOptions = true
                                isLoadingGroups = true
                                scope.launch {
                                    groups = SplitwiseManager.getGroups(context)
                                    isLoadingGroups = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SplitwiseGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("💚 Add to Splitwise", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Show group & member selection inline
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Split with:", color = TextSecondary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                if (isLoadingGroups) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                                        color = SplitwiseGreen
                                    )
                                } else if (groups.isEmpty()) {
                                    Text("No groups found", color = TextMuted, fontSize = 13.sp)
                                } else {
                                    // Group chips
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(groups) { group ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (selectedGroup?.id == group.id) SplitwiseGreen else SurfaceDark)
                                                    .clickable {
                                                        selectedGroup = group
                                                        selectedMembers = emptySet()
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    group.name,
                                                    color = if (selectedGroup?.id == group.id) Color.White else TextPrimary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    // Member chips (if group selected)
                                    if (selectedGroup != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val otherMembers = selectedGroup!!.members.filter { it.id != currentUserId }
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(otherMembers) { member ->
                                                val isSelected = member.id in selectedMembers
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(if (isSelected) SplitwiseGreen else SurfaceDark)
                                                        .clickable {
                                                            selectedMembers = if (isSelected) selectedMembers - member.id else selectedMembers + member.id
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        member.displayName,
                                                        color = if (isSelected) Color.White else TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Split It button
                        Button(
                            onClick = {
                                scope.launch {
                                    isCreatingExpense = true
                                    // Save category first
                                    selectedSuggestion?.let {
                                        onCategorySaved(it.category, it.subcategory, userNote.ifBlank { null })
                                    }
                                    
                                    val description = if (userNote.isNotBlank()) {
                                        "$merchant - $userNote"
                                    } else if (selectedSuggestion != null) {
                                        "$merchant (${selectedSuggestion!!.subcategory})"
                                    } else {
                                        merchant
                                    }
                                    
                                    val amountDouble = amount.replace(",", "").toDoubleOrNull() ?: 0.0
                                    val result = SplitwiseManager.createExpense(
                                        context = context,
                                        description = description,
                                        amount = amountDouble,
                                        groupId = selectedGroup!!.id,
                                        splitWithIds = selectedMembers.toList(),
                                        paidByUserId = currentUserId
                                    )
                                    
                                    isCreatingExpense = false
                                    
                                    if (result.success) {
                                        Toast.makeText(context, "✅ Added to Splitwise!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "❌ ${result.errorMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = selectedGroup != null && selectedMembers.isNotEmpty() && !isCreatingExpense,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SplitwiseGreen,
                                disabledContainerColor = SurfaceCard
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCreatingExpense) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    "Split It!",
                                    color = if (selectedGroup != null && selectedMembers.isNotEmpty()) Color.White else TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // Not logged in - show connect button
                    OutlinedButton(
                        onClick = { SplitwiseManager.startLogin(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔗 Connect Splitwise", color = TextSecondary)
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

