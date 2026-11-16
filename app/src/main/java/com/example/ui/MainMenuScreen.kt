package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Difficulty
import com.example.data.model.GameMode
import com.example.data.model.PlayerProfile
import com.example.data.model.TournamentState
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DarkArena
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OutlineNeon
import com.example.ui.theme.OverdriveOrange
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VictoryGreen

enum class AppRoute {
    MENU,
    CUSTOMIZE,
    LEADERBOARD,
    STATS,
    SETTINGS,
    PLAY
}

@Composable
fun MainMenuScreen(
    profile: PlayerProfile,
    difficulty: Difficulty,
    tournamentState: TournamentState,
    onStartMode: (GameMode, Difficulty) -> Unit,
    onStartOrAdvanceTournament: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DeepVoid, DarkArena, DeepVoid))
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Top Player Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 980.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElectricCyan.copy(alpha = 0.16f))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("LVL ${profile.level}", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                            }
                            Text(profile.callsign, style = MaterialTheme.typography.titleLarge, color = CrispWhite)
                        }
                        Spacer(Modifier.height(6.dp))
                        val xpTarget = profile.xpForNextLevel().coerceAtLeast(1L)
                        LinearProgressIndicator(
                            progress = { (profile.xp.toFloat() / xpTarget.toFloat()).coerceIn(0f, 1f) },
                            color = ElectricCyan,
                            trackColor = SurfaceElevated,
                            modifier = Modifier
                                .width(180.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(OverdriveOrange.copy(alpha = 0.16f))
                                .border(1.dp, OverdriveOrange.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("${profile.credits} CR", style = MaterialTheme.typography.labelLarge, color = OverdriveOrange)
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = TextSecondary)
                        }
                    }
                }
            }

            item {
                // Hero Match Card
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 980.dp)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = "Neon Rink Arena",
                            contentScale = ContentScale.Crop,
                            alpha = 0.55f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(158.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(158.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(DeepVoid.copy(alpha = 0.92f), DeepVoid.copy(alpha = 0.45f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("NEON RINK: OVERDRIVE", style = MaterialTheme.typography.headlineLarge, color = CrispWhite)
                            Text(
                                "3v3 Futuristic Roller Arena • Difficulty: ${difficulty.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { onStartMode(GameMode.QUICK_MATCH, difficulty) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepVoid),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("play_quick_match_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("PLAY QUICK MATCH", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 980.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("GAME MODES", style = MaterialTheme.typography.labelLarge, color = TextSecondary)

                    val cupSubtitle = when {
                        tournamentState.active && !tournamentState.eliminated ->
                            "Active: ${tournamentState.currentRoundName()} (${tournamentState.currentDifficulty().label})"
                        tournamentState.eliminated -> "Eliminated — Tap to Start New Cup"
                        else -> "Win 3 knockout matches for Trophy"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModeCard(
                            icon = Icons.Filled.EmojiEvents,
                            accent = OverdriveOrange,
                            title = "Overdrive Cup",
                            subtitle = cupSubtitle,
                            tag = "mode_tournament",
                            modifier = Modifier.weight(1f),
                            onClick = onStartOrAdvanceTournament
                        )
                        ModeCard(
                            icon = Icons.Filled.Timer,
                            accent = NeonViolet,
                            title = "Time Challenge (90s)",
                            subtitle = "Best: ${profile.bestTimeChallengeGoals} Goals • High-speed rush",
                            tag = "mode_time_challenge",
                            modifier = Modifier.weight(1f),
                            onClick = { onStartMode(GameMode.TIME_CHALLENGE, difficulty) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModeCard(
                            icon = Icons.Filled.School,
                            accent = VictoryGreen,
                            title = "Training Academy",
                            subtitle = "5 drills: Skate, Boost, Pass, Shot & Tackle",
                            tag = "mode_training",
                            modifier = Modifier.weight(1f),
                            onClick = { onStartMode(GameMode.TRAINING, Difficulty.EASY) }
                        )
                        ModeCard(
                            icon = Icons.Filled.SportsHockey,
                            accent = ElectricCyan,
                            title = "Practice Arena",
                            subtitle = "Free-skate lab with shot radar & reset",
                            tag = "mode_practice",
                            modifier = Modifier.weight(1f),
                            onClick = { onStartMode(GameMode.PRACTICE, Difficulty.NORMAL) }
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 980.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("GARAGE & LEAGUE", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        if (maxWidth >= 780.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HubTile(
                                    icon = Icons.Filled.Checkroom,
                                    label = "Customize",
                                    tint = ElectricCyan,
                                    tag = "nav_customize",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigate(AppRoute.CUSTOMIZE) }
                                )
                                HubTile(
                                    icon = Icons.Filled.Leaderboard,
                                    label = "Leaderboard",
                                    tint = OverdriveOrange,
                                    tag = "nav_leaderboard",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigate(AppRoute.LEADERBOARD) }
                                )
                                HubTile(
                                    icon = Icons.Filled.BarChart,
                                    label = "Statistics",
                                    tint = NeonViolet,
                                    tag = "nav_stats",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigate(AppRoute.STATS) }
                                )
                                HubTile(
                                    icon = Icons.Filled.Settings,
                                    label = "Settings",
                                    tint = VictoryGreen,
                                    tag = "nav_settings",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigate(AppRoute.SETTINGS) }
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HubTile(
                                        icon = Icons.Filled.Checkroom,
                                        label = "Customize",
                                        tint = ElectricCyan,
                                        tag = "nav_customize",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(AppRoute.CUSTOMIZE) }
                                    )
                                    HubTile(
                                        icon = Icons.Filled.Leaderboard,
                                        label = "Leaderboard",
                                        tint = OverdriveOrange,
                                        tag = "nav_leaderboard",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(AppRoute.LEADERBOARD) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HubTile(
                                        icon = Icons.Filled.BarChart,
                                        label = "Statistics",
                                        tint = NeonViolet,
                                        tag = "nav_stats",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(AppRoute.STATS) }
                                    )
                                    HubTile(
                                        icon = Icons.Filled.Settings,
                                        label = "Settings",
                                        tint = VictoryGreen,
                                        tag = "nav_settings",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(AppRoute.SETTINGS) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .border(1.dp, OutlineNeon, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CrispWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = "Launch $title", tint = accent)
        }
    }
}

@Composable
private fun HubTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .height(74.dp)
            .border(1.dp, OutlineNeon, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = CrispWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
