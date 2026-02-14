package com.example.myapplication

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
// COLOR PALETTE
// ═══════════════════════════════════════════════════════════════
private val AccentGreen = Color(0xFF00E676)
private val AccentPurple = Color(0xFFBB86FC)
private val SurfaceCard = Color(0xFF2A2A3E)
private val SurfaceDark = Color(0xFF1E1E2E)
private val TextPrimary = Color(0xFFECECEC)
private val TextSecondary = Color(0xFFA0A0B0)
private val TextMuted = Color(0xFF6C6C7E)

/**
 * Bottom sheet UI for splitting an expense on Splitwise.
 *
 * FLOW:
 * 1. Load user's Splitwise groups
 * 2. User selects a group
 * 3. User selects which members to split with
 * 4. Shows per-person amount preview
 * 5. User taps "Split It!" → creates expense in Splitwise
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitBottomSheet(
    amount: Double,
    merchant: String,
    description: String,
    onDismiss: () -> Unit,
    onSplitCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<SplitwiseManager.SplitwiseGroup>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<SplitwiseManager.SplitwiseGroup?>(null) }
    var selectedMembers by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentUserId = remember { SplitwiseManager.getCurrentUserId(context) }

    // Load groups on appear
    LaunchedEffect(Unit) {
        isLoading = true
        groups = SplitwiseManager.getGroups(context)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(20.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TextMuted)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📤 Split on Splitwise",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "₹${String.format("%.0f", amount)}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGreen
            )
        }

        Text(
            text = description.ifBlank { merchant },
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Content
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentGreen)
                }
            }

            groups.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👥", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No groups found",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Create a group in Splitwise first",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            else -> {
                // Group selector
                Text(
                    "Select Group",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(groups) { group ->
                        val isSelected = selectedGroup?.id == group.id
                        GroupItem(
                            group = group,
                            isSelected = isSelected,
                            onClick = {
                                selectedGroup = group
                                selectedMembers = emptySet()  // reset selections
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Member selector (if group selected)
                if (selectedGroup != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Split with",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter out current user from the list
                    val otherMembers = selectedGroup!!.members.filter { it.id != currentUserId }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(otherMembers) { member ->
                            val isSelected = member.id in selectedMembers

                            MemberChip(
                                member = member,
                                isSelected = isSelected,
                                onClick = {
                                    selectedMembers = if (isSelected) {
                                        selectedMembers - member.id
                                    } else {
                                        selectedMembers + member.id
                                    }
                                }
                            )
                        }
                    }

                    // Show split preview
                    if (selectedMembers.isNotEmpty()) {
                        val perPerson = amount / (selectedMembers.size + 1)
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Per person (${selectedMembers.size + 1} people)",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "₹${String.format("%.0f", perPerson)}",
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "❌ $error",
                color = Color(0xFFFF5252),
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }

            Button(
                onClick = {
                    scope.launch {
                        isCreating = true
                        errorMessage = null

                        val result = SplitwiseManager.createExpense(
                            context = context,
                            description = description.ifBlank { merchant },
                            amount = amount,
                            groupId = selectedGroup!!.id,
                            splitWithIds = selectedMembers.toList(),
                            paidByUserId = currentUserId
                        )

                        isCreating = false

                        if (result.success) {
                            Toast.makeText(context, "✅ Added to Splitwise! ID: ${result.expenseId}", Toast.LENGTH_LONG).show()
                            onSplitCreated()
                        } else {
                            // Show the actual error - full raw response for debugging
                            val debugMsg = "UserID: $currentUserId | Group: ${selectedGroup!!.id} | Error: ${result.rawResponse ?: result.errorMessage}"
                            errorMessage = debugMsg
                        }
                    }
                },
                enabled = selectedGroup != null && selectedMembers.isNotEmpty() && !isCreating,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    disabledContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Split It!",
                        color = if (selectedGroup != null && selectedMembers.isNotEmpty()) Color.White else TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupItem(
    group: SplitwiseManager.SplitwiseGroup,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AccentPurple.copy(alpha = 0.2f) else SurfaceCard)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "👥", fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.name,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${group.members.size} members",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        if (isSelected) {
            Text("✓", color = AccentPurple, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MemberChip(
    member: SplitwiseManager.SplitwiseMember,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) AccentPurple else SurfaceCard)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = member.displayName,
            color = if (isSelected) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

