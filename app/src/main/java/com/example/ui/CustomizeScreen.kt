package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CosmeticCatalog
import com.example.data.model.CosmeticCategory
import com.example.data.model.CosmeticItem
import com.example.data.model.PlayerProfile
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DarkArena
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.OutlineNeon
import com.example.ui.theme.OverdriveOrange
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VictoryGreen

@Composable
fun CustomizeScreen(
    profile: PlayerProfile,
    onUnlockOrEquip: (CosmeticItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var selectedCategory by remember { mutableStateOf(CosmeticCategory.OUTFIT) }
    val filtered = remember(selectedCategory) {
        CosmeticCatalog.items.filter { it.category == selectedCategory }
    }

    val equippedId = when (selectedCategory) {
        CosmeticCategory.OUTFIT -> profile.selectedOutfit
        CosmeticCategory.SKATE -> profile.selectedSkate
        CosmeticCategory.TRAIL -> profile.selectedTrail
        CosmeticCategory.BALL -> profile.selectedBall
        CosmeticCategory.GOAL_FX -> profile.selectedGoalFx
        CosmeticCategory.ARENA -> profile.selectedArena
        CosmeticCategory.CELEBRATION -> profile.selectedCelebration
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepVoid, DarkArena, DeepVoid)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("customize_back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CrispWhite)
                }
                Text("CUSTOMIZE GARAGE", style = MaterialTheme.typography.headlineMedium, color = CrispWhite)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(OverdriveOrange.copy(alpha = 0.16f))
                    .border(1.dp, OverdriveOrange.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("${profile.credits} CR", style = MaterialTheme.typography.labelLarge, color = OverdriveOrange)
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CosmeticCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.label, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan,
                        selectedLabelColor = DeepVoid,
                        containerColor = SurfaceCard,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { item ->
                val isUnlocked = profile.unlockedItems.contains(item.id)
                val isEquipped = equippedId == item.id
                val canAfford = profile.credits >= item.cost
                val primary = Color(item.primaryHex)
                val accent = Color(item.accentHex)

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isEquipped) 1.5.dp else 1.dp,
                            color = if (isEquipped) ElectricCyan else OutlineNeon,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Neon Color Swatch Emblem
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.35f), accent.copy(alpha = 0.25f))))
                                .border(1.5.dp, primary, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(primary)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleLarge, color = CrispWhite)
                            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }

                        when {
                            isEquipped -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(VictoryGreen.copy(alpha = 0.18f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = VictoryGreen, modifier = Modifier.size(16.dp))
                                    Text("EQUIPPED", style = MaterialTheme.typography.labelLarge, color = VictoryGreen)
                                }
                            }
                            isUnlocked -> {
                                Button(
                                    onClick = { onUnlockOrEquip(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = ElectricCyan),
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("equip_${item.id}")
                                ) {
                                    Text("EQUIP", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = { onUnlockOrEquip(item) },
                                    enabled = canAfford,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OverdriveOrange,
                                        contentColor = DeepVoid,
                                        disabledContainerColor = SurfaceElevated,
                                        disabledContentColor = TextSecondary
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("unlock_${item.id}")
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" ${item.cost} CR", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
