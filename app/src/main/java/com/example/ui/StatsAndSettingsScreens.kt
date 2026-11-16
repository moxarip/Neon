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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.ControlLayout
import com.example.data.model.Difficulty
import com.example.data.model.GraphicsQuality
import com.example.data.model.MatchRecord
import com.example.data.model.PlayerProfile
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkArena
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OutlineNeon
import com.example.ui.theme.OverdriveOrange
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VictoryGreen

@Composable
fun StatsScreen(
    profile: PlayerProfile,
    historyState: UiState<List<MatchRecord>>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val winRate = if (profile.totalMatches > 0) {
        ((profile.wins.toFloat() / profile.totalMatches.toFloat()) * 100f).toInt()
    } else 0

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
                modifier = Modifier.minimumInteractiveComponentSize().testTag("stats_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CrispWhite)
            }
            Text("CAREER TELEMETRY", style = MaterialTheme.typography.headlineMedium, color = CrispWhite)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("MATCHES", "${profile.totalMatches}", ElectricCyan, Modifier.weight(1f))
                    StatTile("WIN RATE", "$winRate%", VictoryGreen, Modifier.weight(1f))
                    StatTile("RATING", "${profile.competitiveRating()}", OverdriveOrange, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("WINS / LOSSES", "${profile.wins} / ${profile.losses}", CrispWhite, Modifier.weight(1f))
                    StatTile("GOALS", "${profile.goals}", ElectricCyan, Modifier.weight(1f))
                    StatTile("ASSISTS", "${profile.assists}", NeonViolet, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("SAVES", "${profile.saves}", VictoryGreen, Modifier.weight(1f))
                    StatTile("CUPS WON", "${profile.tournamentsWon}", OverdriveOrange, Modifier.weight(1f))
                    StatTile("TIME BEST", "${profile.bestTimeChallengeGoals}", NeonViolet, Modifier.weight(1f))
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("RECENT MATCHES", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }

            when (historyState) {
                is UiState.Success -> {
                    if (historyState.data.isEmpty()) {
                        item {
                            Text("Complete a match to populate match history.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        items(historyState.data) { rec ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().border(1.dp, OutlineNeon, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${rec.mode.replace('_', ' ').uppercase()} • ${rec.difficulty.uppercase()}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = CrispWhite
                                        )
                                        Text(
                                            text = "${rec.goalsScored}G  ${rec.assists}A  ${rec.saves}SV • +${rec.xpEarned} XP",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${rec.playerScore} - ${rec.opponentScore}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = if (rec.won) VictoryGreen else DangerRed
                                        )
                                        Text(
                                            text = if (rec.won) "WIN" else "LOSS",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (rec.won) VictoryGreen else DangerRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Loading -> item { Text("Loading match logs...", color = TextSecondary) }
                is UiState.Error -> item { Text(historyState.message, color = DangerRed) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, OutlineNeon, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    currentCallsign: String,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateCallsign: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var callsignInput by remember(currentCallsign) { mutableStateOf(currentCallsign) }

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
                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CrispWhite)
            }
            Text("SETTINGS", style = MaterialTheme.typography.headlineMedium, color = CrispWhite)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Callsign Card
            SettingsCard("PLAYER CALLSIGN") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = callsignInput,
                        onValueChange = { if (it.length <= 24) callsignInput = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Display Callsign") }
                    )
                    Button(
                        onClick = { onUpdateCallsign(callsignInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepVoid),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("save_callsign_button")
                    ) {
                        Text("SAVE", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Audio Card
            SettingsCard("AUDIO MIXER") {
                SliderRow("Master Volume", settings.masterVolume) {
                    onUpdateSettings(settings.copy(masterVolume = it))
                }
                SliderRow("Music Volume", settings.musicVolume) {
                    onUpdateSettings(settings.copy(musicVolume = it))
                }
                SliderRow("SFX Volume", settings.sfxVolume) {
                    onUpdateSettings(settings.copy(sfxVolume = it))
                }
            }

            // Gameplay & AI Difficulty
            SettingsCard("AI DIFFICULTY") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = settings.difficulty == diff,
                            onClick = { onUpdateSettings(settings.copy(difficulty = diff)) },
                            label = { Text(diff.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = DeepVoid
                            )
                        )
                    }
                }
            }

            // Graphics Quality
            SettingsCard("GRAPHICS QUALITY (60 FPS OPTIMIZATION)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GraphicsQuality.entries.forEach { q ->
                        FilterChip(
                            selected = settings.graphicsQuality == q,
                            onClick = { onUpdateSettings(settings.copy(graphicsQuality = q)) },
                            label = { Text(q.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonViolet,
                                selectedLabelColor = CrispWhite
                            )
                        )
                    }
                }
            }

            // Controls
            SettingsCard("TOUCH CONTROLS & HAPTICS") {
                SliderRow("Joystick Sensitivity", (settings.joystickSensitivity - 0.5f) / 1.3f, display = "${"%.1f".format(settings.joystickSensitivity)}x") {
                    onUpdateSettings(settings.copy(joystickSensitivity = 0.5f + it * 1.3f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ControlLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = settings.controlLayout == layout,
                            onClick = { onUpdateSettings(settings.copy(controlLayout = layout)) },
                            label = { Text(layout.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OverdriveOrange,
                                selectedLabelColor = DeepVoid
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibration / Haptic Impact", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(hapticsEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DeepVoid, checkedTrackColor = ElectricCyan)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, OutlineNeon, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
            content()
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, display: String = "${(value * 100).toInt()}%", onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
            Text(display, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
        )
    }
}
