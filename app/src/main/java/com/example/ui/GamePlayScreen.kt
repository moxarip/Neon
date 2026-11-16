package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.audio.SoundEngine
import com.example.data.model.AppSettings
import com.example.data.model.ControlLayout
import com.example.data.model.Difficulty
import com.example.data.model.GameMode
import com.example.data.model.PlayerProfile
import com.example.game.engine.GlRenderer
import com.example.game.engine.MatchHudSnapshot
import com.example.game.engine.MatchPhase
import com.example.game.engine.MatchSimulation
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DangerRed
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
import kotlin.math.sqrt

@Composable
fun GamePlayScreen(
    mode: GameMode,
    difficulty: Difficulty,
    profile: PlayerProfile,
    settings: AppSettings,
    soundEngine: SoundEngine,
    tournamentRoundName: String?,
    onFinishMatch: (
        cyanScore: Int,
        violetScore: Int,
        goals: Int,
        assists: Int,
        saves: Int,
        overtime: Boolean
    ) -> MatchResultSummary,
    onRematch: () -> Unit,
    onNextTournamentRound: () -> Unit,
    onOpenSettingsFromPause: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keep landscape orientation for gameplay and menus
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        soundEngine.setArenaMusic(true)
        onDispose {
            soundEngine.setArenaMusic(false)
        }
    }

    var matchRunKey by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var resultSummary by remember { mutableStateOf<MatchResultSummary?>(null) }
    var hud by remember { mutableStateOf(MatchHudSnapshot()) }

    val simulation = remember(matchRunKey, mode, difficulty) {
        MatchSimulation(
            mode = mode,
            difficulty = difficulty,
            onSound = { sfx -> soundEngine.play(sfx) },
            onHaptic = { strong ->
                if (settings.hapticsEnabled) triggerHaptic(context, strong)
            }
        )
    }

    LaunchedEffect(settings.joystickSensitivity, simulation) {
        simulation.joystickSensitivity = settings.joystickSensitivity
    }

    val renderer = remember(simulation) {
        GlRenderer(
            simulation = simulation,
            profile = profile,
            quality = settings.graphicsQuality,
            onHudSnapshot = { snap -> hud = snap }
        )
    }

    LaunchedEffect(profile, settings.graphicsQuality, isPaused, renderer) {
        renderer.profile = profile
        renderer.quality = settings.graphicsQuality
        renderer.isPaused = isPaused
    }

    // Record match result once when ENDED
    LaunchedEffect(hud.phase, matchRunKey) {
        if (hud.phase == MatchPhase.ENDED && resultSummary == null) {
            resultSummary = onFinishMatch(
                hud.cyanScore,
                hud.violetScore,
                hud.userGoals,
                hud.userAssists,
                hud.userSaves,
                hud.isOvertime
            )
        }
    }

    BackHandler {
        if (resultSummary != null) {
            onExitToMenu()
        } else {
            isPaused = !isPaused
        }
    }

    val glSurfaceView = remember(renderer) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceView.onPause()
                    if (resultSummary == null) isPaused = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepVoid)) {
        // 3D OpenGL ES Viewport
        AndroidView(
            factory = { glSurfaceView },
            update = { /* Renderer updates via volatile fields */ },
            modifier = Modifier.fillMaxSize()
        )

        // Match HUD Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top Scoreboard & Mode Telemetry
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Possession & Speed Telemetry
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val carrierColor = when (hud.carrierTeam) {
                        0 -> ElectricCyan
                        1 -> NeonViolet
                        else -> TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DeepVoid.copy(alpha = 0.72f))
                            .border(1.dp, carrierColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(hud.carrierLabel, style = MaterialTheme.typography.labelMedium, color = carrierColor)
                    }
                    if (hud.lastShotSpeedKmh > 0) {
                        Text(
                            text = "SHOT ${hud.lastShotSpeedKmh} KM/H",
                            style = MaterialTheme.typography.labelMedium,
                            color = OverdriveOrange
                        )
                    }
                }

                // Center Scoreboard
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(DeepVoid.copy(alpha = 0.80f))
                            .border(1.dp, OutlineNeon, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("CYAN", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                        Text("${hud.cyanScore}", style = MaterialTheme.typography.headlineMedium, color = CrispWhite)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hud.isOvertime) OverdriveOrange.copy(alpha = 0.22f) else SurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            val timerLabel = when {
                                mode == GameMode.PRACTICE -> "FREE"
                                mode == GameMode.TRAINING -> "DRILL"
                                hud.isOvertime -> "OT ${formatClock(hud.timeRemainingSec)}"
                                else -> formatClock(hud.timeRemainingSec)
                            }
                            Text(
                                text = timerLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hud.isOvertime) OverdriveOrange else CrispWhite
                            )
                        }
                        if (mode != GameMode.TIME_CHALLENGE && mode != GameMode.PRACTICE) {
                            Text("${hud.violetScore}", style = MaterialTheme.typography.headlineMedium, color = CrispWhite)
                            Text("VIOLET", style = MaterialTheme.typography.labelLarge, color = NeonViolet)
                        }
                    }

                    if (tournamentRoundName != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(tournamentRoundName.uppercase(), style = MaterialTheme.typography.labelMedium, color = OverdriveOrange)
                    }
                }

                // Top-right Pause & Practice Reset
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mode == GameMode.PRACTICE) {
                        OutlinedButton(
                            onClick = { simulation.triggerPracticeReset() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(40.dp).testTag("practice_reset_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("RESET", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        }
                    }
                    IconButton(
                        onClick = { isPaused = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DeepVoid.copy(alpha = 0.72f))
                            .border(1.dp, OutlineNeon, CircleShape)
                            .minimumInteractiveComponentSize()
                            .testTag("pause_button")
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = CrispWhite)
                    }
                }
            }

            // Training Instruction Banner
            if (mode == GameMode.TRAINING) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 62.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeepVoid.copy(alpha = 0.85f))
                        .border(1.dp, VictoryGreen.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(hud.trainingInstruction, style = MaterialTheme.typography.labelLarge, color = VictoryGreen)
                }
            }

            // Countdown Overlay
            if (hud.phase == MatchPhase.COUNTDOWN) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Text(
                        text = "${hud.countdownSec}",
                        style = MaterialTheme.typography.displayLarge,
                        color = ElectricCyan
                    )
                }
            }

            // Goal / Save Announcement Banner
            AnimatedVisibility(
                visible = hud.goalBannerText != null,
                modifier = Modifier.align(Alignment.Center)
            ) {
                val bannerColor = if (hud.bannerColorTeam == 0) ElectricCyan else NeonViolet
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeepVoid.copy(alpha = 0.88f))
                        .border(2.dp, bannerColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = hud.goalBannerText ?: "",
                        style = MaterialTheme.typography.headlineLarge,
                        color = bannerColor
                    )
                }
            }

            // Bottom Touchscreen Controls
            if (!isPaused && resultSummary == null) {
                val swapped = settings.controlLayout == ControlLayout.SWAPPED
                val btnScale = if (settings.controlLayout == ControlLayout.COMPACT) 0.84f else 1.0f

                val joystickComposable: @Composable () -> Unit = {
                    VirtualJoystick(
                        size = (132 * btnScale).dp,
                        onVector = { vx, vy ->
                            simulation.joyX = vx
                            simulation.joyY = vy
                        }
                    )
                }

                val actionClusterComposable: @Composable () -> Unit = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Energy Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = OverdriveOrange, modifier = Modifier.size(16.dp))
                            LinearProgressIndicator(
                                progress = { (hud.energy / 100f).coerceIn(0f, 1f) },
                                color = OverdriveOrange,
                                trackColor = DeepVoid.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .width((124 * btnScale).dp)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionCircleButton(
                                    label = "TACKLE",
                                    color = DangerRed,
                                    size = (64 * btnScale).dp,
                                    cooldownFrac = hud.tackleCooldownFrac,
                                    tag = "btn_tackle",
                                    onTap = { simulation.triggerTackle() }
                                )
                                ActionCircleButton(
                                    label = "PASS",
                                    color = ElectricCyan,
                                    size = (70 * btnScale).dp,
                                    cooldownFrac = 0f,
                                    tag = "btn_pass",
                                    onTap = { simulation.triggerPass() }
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionCircleButton(
                                    label = "BOOST",
                                    color = NeonViolet,
                                    size = (64 * btnScale).dp,
                                    cooldownFrac = if (hud.energy < 24f) 1f else 0f,
                                    tag = "btn_boost",
                                    onTap = { simulation.triggerBoost() }
                                )
                                ShootHoldButton(
                                    size = (82 * btnScale).dp,
                                    chargeFrac = hud.chargeProgress,
                                    onPress = { simulation.onShootPressed() },
                                    onRelease = { simulation.onShootReleased() }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (swapped) {
                        actionClusterComposable()
                        joystickComposable()
                    } else {
                        joystickComposable()
                        actionClusterComposable()
                    }
                }
            }
        }

        // Pause Overlay
        if (isPaused && resultSummary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepVoid.copy(alpha = 0.80f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .border(1.5.dp, ElectricCyan, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("MATCH PAUSED", style = MaterialTheme.typography.headlineMedium, color = CrispWhite)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { isPaused = false },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepVoid),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("pause_resume")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("RESUME", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = {
                                    isPaused = false
                                    resultSummary = null
                                    matchRunKey++
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("pause_restart")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = CrispWhite)
                                Spacer(Modifier.width(6.dp))
                                Text("RESTART", style = MaterialTheme.typography.labelLarge, color = CrispWhite)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenSettingsFromPause,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("pause_settings")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, tint = CrispWhite)
                                Spacer(Modifier.width(6.dp))
                                Text("SETTINGS", style = MaterialTheme.typography.labelLarge, color = CrispWhite)
                            }
                            OutlinedButton(
                                onClick = onExitToMenu,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("pause_quit")
                            ) {
                                Text("QUIT MATCH", style = MaterialTheme.typography.labelLarge, color = DangerRed)
                            }
                        }
                    }
                }
            }
        }

        // Results Screen Overlay
        val summary = resultSummary
        if (summary != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepVoid.copy(alpha = 0.88f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .widthIn(max = 540.dp)
                        .border(2.dp, if (summary.won) VictoryGreen else DangerRed, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (summary.tournamentWonCup) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = OverdriveOrange, modifier = Modifier.size(36.dp))
                            Text("OVERDRIVE CUP CHAMPION!", style = MaterialTheme.typography.headlineMedium, color = OverdriveOrange)
                        } else {
                            Text(
                                text = if (summary.won) "VICTORY!" else "DEFEAT",
                                style = MaterialTheme.typography.headlineLarge,
                                color = if (summary.won) VictoryGreen else DangerRed
                            )
                        }

                        Text(
                            text = "${summary.cyanScore}  -  ${summary.violetScore}" + if (summary.overtime) " (OT)" else "",
                            style = MaterialTheme.typography.headlineMedium,
                            color = CrispWhite
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ResultStat("GOALS", "${summary.goals}", ElectricCyan)
                            ResultStat("ASSISTS", "${summary.assists}", NeonViolet)
                            ResultStat("SAVES", "${summary.saves}", VictoryGreen)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text("+${summary.xpEarned} XP", style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                            Text("+${summary.creditsEarned} CR", style = MaterialTheme.typography.titleLarge, color = OverdriveOrange)
                        }

                        if (summary.leveledUp) {
                            Text("LEVEL UP! NOW LEVEL ${summary.newLevel}", style = MaterialTheme.typography.labelLarge, color = VictoryGreen)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExitToMenu,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("results_menu")
                            ) {
                                Text("MAIN MENU", style = MaterialTheme.typography.labelLarge, color = CrispWhite)
                            }
                            if (summary.mode == GameMode.TOURNAMENT && summary.won && !summary.tournamentWonCup) {
                                Button(
                                    onClick = onNextTournamentRound,
                                    colors = ButtonDefaults.buttonColors(containerColor = OverdriveOrange, contentColor = DeepVoid),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("results_next_round")
                                ) {
                                    Text("NEXT ROUND", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        resultSummary = null
                                        matchRunKey++
                                        onRematch()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepVoid),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("results_rematch")
                                ) {
                                    Text("REMATCH", style = MaterialTheme.typography.labelLarge)
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
private fun ResultStat(label: String, valStr: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valStr, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun VirtualJoystick(
    size: Dp,
    onVector: (Float, Float) -> Unit
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(size)
            .testTag("virtual_joystick")
            .pointerInput(Unit) {
                val maxR = this.size.width * 0.36f
                val centerX = this.size.width * 0.5f
                val centerY = this.size.height * 0.5f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val ptr = event.changes.firstOrNull { it.id == down.id }
                        if (ptr == null || !ptr.pressed) {
                            active = false
                            knobX = 0f
                            knobY = 0f
                            onVector(0f, 0f)
                        } else {
                            ptr.consume()
                            val dx = ptr.position.x - centerX
                            val dy = ptr.position.y - centerY
                            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                            val clamped = dist.coerceAtMost(maxR)
                            val nx = (dx / dist) * (clamped / maxR)
                            val ny = (dy / dist) * (clamped / maxR)
                            knobX = (dx / dist) * clamped
                            knobY = (dy / dist) * clamped
                            onVector(nx, ny)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension * 0.48f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(color = DeepVoid.copy(alpha = 0.50f), radius = r, center = c)
            drawCircle(color = ElectricCyan.copy(alpha = 0.55f), radius = r, center = c, style = Stroke(width = 3f))
            drawCircle(
                brush = Brush.radialGradient(listOf(ElectricCyan.copy(alpha = 0.9f), ElectricCyan.copy(alpha = 0.35f))),
                radius = r * 0.38f,
                center = Offset(c.x + knobX, c.y + knobY)
            )
        }
    }
}

@Composable
private fun ActionCircleButton(
    label: String,
    color: Color,
    size: Dp,
    cooldownFrac: Float,
    tag: String,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(DeepVoid.copy(alpha = 0.65f))
            .border(2.dp, color.copy(alpha = if (cooldownFrac > 0.05f) 0.35f else 0.9f), CircleShape)
            .testTag(tag)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onTap()
                    waitForUpOrCancellation()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (cooldownFrac > 0.02f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = color.copy(alpha = 0.45f),
                    startAngle = -90f,
                    sweepAngle = 360f * (1f - cooldownFrac),
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (cooldownFrac > 0.05f) TextSecondary else CrispWhite
        )
    }
}

@Composable
private fun ShootHoldButton(
    size: Dp,
    chargeFrac: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(OverdriveOrange.copy(alpha = 0.28f + chargeFrac * 0.35f))
            .border(2.5.dp, OverdriveOrange, CircleShape)
            .testTag("btn_shoot")
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (chargeFrac > 0.02f) {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                drawArc(
                    color = CrispWhite,
                    startAngle = -90f,
                    sweepAngle = 360f * chargeFrac,
                    useCenter = false,
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SHOOT", style = MaterialTheme.typography.labelLarge, color = CrispWhite, fontWeight = FontWeight.ExtraBold)
            if (chargeFrac > 0.1f) {
                Text("${(chargeFrac * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
            }
        }
    }
}

private fun formatClock(secFloat: Float): String {
    val total = secFloat.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

@Suppress("DEPRECATION")
private fun triggerHaptic(context: Context, strong: Boolean) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(if (strong) 65L else 30L, if (strong) 220 else 130)
            )
        } else {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(if (strong) 65L else 30L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v?.vibrate(if (strong) 65L else 30L)
            }
        }
    } catch (_: Exception) {
    }
}
