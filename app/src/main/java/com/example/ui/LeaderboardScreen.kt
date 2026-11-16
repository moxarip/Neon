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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.LeaderboardEntry
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DarkArena
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OutlineNeon
import com.example.ui.theme.OverdriveOrange
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextSecondary

@Composable
fun LeaderboardScreen(
    state: UiState<List<LeaderboardEntry>>,
    currentUserId: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepVoid, DarkArena, DeepVoid)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("leaderboard_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CrispWhite)
            }
            Text("GLOBAL LEADERBOARD", style = MaterialTheme.typography.headlineMedium, color = CrispWhite)
        }

        Spacer(Modifier.height(10.dp))

        when (state) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }
            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = OverdriveOrange, modifier = Modifier.size(48.dp))
                            Text("No ranked skaters yet. Play a match to claim #1!", color = TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.data, key = { _, e -> e.userId }) { idx, entry ->
                            val rank = idx + 1
                            val isMe = entry.userId == currentUserId
                            val rankColor = when (rank) {
                                1 -> OverdriveOrange
                                2 -> ElectricCyan
                                3 -> NeonViolet
                                else -> TextSecondary
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMe) ElectricCyan.copy(alpha = 0.12f) else SurfaceCard
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isMe) 1.5.dp else 1.dp,
                                        color = if (isMe) ElectricCyan else OutlineNeon,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(rankColor.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("#$rank", style = MaterialTheme.typography.labelLarge, color = rankColor)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isMe) "${entry.callsign} (YOU)" else entry.callsign,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = CrispWhite
                                        )
                                        Text(
                                            text = "LVL ${entry.level} • ${entry.wins}W • ${entry.goals}G • TA ${entry.bestTimeChallenge}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${entry.rating}", style = MaterialTheme.typography.headlineMedium, color = ElectricCyan)
                                        Text("RATING", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
