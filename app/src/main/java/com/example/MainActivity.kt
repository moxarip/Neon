package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.SoundEffect
import com.example.audio.SoundEngine
import com.example.data.local.LocalStorageManager
import com.example.data.model.Difficulty
import com.example.data.model.GameMode
import com.example.data.model.PlayerProfile
import com.example.data.remote.GameRepository
import com.example.ui.AppRoute
import com.example.ui.AuthScreen
import com.example.ui.CustomizeScreen
import com.example.ui.GamePlayScreen
import com.example.ui.LeaderboardScreen
import com.example.ui.MainMenuScreen
import com.example.ui.NeonRinkViewModel
import com.example.ui.SettingsScreen
import com.example.ui.StatsScreen
import com.example.ui.UiState
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonRinkTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var soundEngine: SoundEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        soundEngine = SoundEngine().apply { startMusic() }

        setContent {
            NeonRinkTheme {
                AppNavigation(soundEngine = soundEngine)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundEngine.isInitialized) {
            soundEngine.stopMusic()
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    soundEngine: SoundEngine,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates automatically */ })
    } else {
        key(user.uid) {
            val defaultCallsign = user.displayName?.takeIf { it.isNotBlank() } ?: "Skater_${user.uid.take(5)}"
            NeonRinkApp(
                currentUserId = user.uid,
                defaultCallsign = defaultCallsign,
                soundEngine = soundEngine
            )
        }
    }
}

@Composable
fun NeonRinkApp(
    currentUserId: String,
    defaultCallsign: String,
    soundEngine: SoundEngine
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val vm: NeonRinkViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NeonRinkViewModel(
                    repository = GameRepository(db),
                    localStorage = LocalStorageManager(app),
                    soundEngine = soundEngine,
                    currentUserId = currentUserId,
                    defaultCallsign = defaultCallsign
                )
            }
        }
    )

    val profileState by vm.profileState.collectAsStateWithLifecycle()
    val leaderboardState by vm.leaderboardState.collectAsStateWithLifecycle()
    val historyState by vm.matchHistoryState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tournamentState by vm.tournamentState.collectAsStateWithLifecycle()

    var route by remember { mutableStateOf(AppRoute.MENU) }
    var activeMode by remember { mutableStateOf(GameMode.QUICK_MATCH) }
    var activeDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var matchInstanceId by remember { mutableIntStateOf(0) }

    val profile = (profileState as? UiState.Success)?.data
        ?: PlayerProfile(id = currentUserId, userId = currentUserId, callsign = defaultCallsign)

    if (profileState is UiState.Loading && route == AppRoute.MENU) {
        // Still allow instant interaction once ensured; show subtle loader only briefly if needed
        Box(modifier = Modifier.fillMaxSize().background(DeepVoid), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ElectricCyan)
        }
        return
    }

    when (route) {
        AppRoute.MENU -> {
            MainMenuScreen(
                profile = profile,
                difficulty = settings.difficulty,
                tournamentState = tournamentState,
                onStartMode = { mode, diff ->
                    soundEngine.play(SoundEffect.UI_CLICK)
                    activeMode = mode
                    activeDifficulty = diff
                    matchInstanceId++
                    route = AppRoute.PLAY
                },
                onStartOrAdvanceTournament = {
                    soundEngine.play(SoundEffect.UI_CLICK)
                    if (!tournamentState.active || tournamentState.eliminated || tournamentState.round >= 3) {
                        vm.startNewTournament()
                        activeMode = GameMode.TOURNAMENT
                        activeDifficulty = Difficulty.NORMAL
                    } else {
                        activeMode = GameMode.TOURNAMENT
                        activeDifficulty = tournamentState.currentDifficulty()
                    }
                    matchInstanceId++
                    route = AppRoute.PLAY
                },
                onNavigate = { target ->
                    soundEngine.play(SoundEffect.UI_CLICK)
                    route = target
                },
                onSignOut = {
                    soundEngine.play(SoundEffect.UI_CLICK)
                    scope.launch {
                        withContext(NonCancellable) {
                            try {
                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                            } catch (e: Exception) {
                                Log.w("Auth", "Failed to clear credential state: ${e.message}")
                            } finally {
                                Firebase.auth.signOut()
                            }
                        }
                    }
                }
            )
        }

        AppRoute.CUSTOMIZE -> {
            CustomizeScreen(
                profile = profile,
                onUnlockOrEquip = { item -> vm.unlockOrEquip(item) },
                onBack = {
                    soundEngine.play(SoundEffect.UI_CLICK)
                    route = AppRoute.MENU
                }
            )
        }

        AppRoute.LEADERBOARD -> {
            LeaderboardScreen(
                state = leaderboardState,
                currentUserId = currentUserId,
                onBack = {
                    soundEngine.play(SoundEffect.UI_CLICK)
                    route = AppRoute.MENU
                }
            )
        }

        AppRoute.STATS -> {
            StatsScreen(
                profile = profile,
                historyState = historyState,
                onBack = {
                    soundEngine.play(SoundEffect.UI_CLICK)
                    route = AppRoute.MENU
                }
            )
        }

        AppRoute.SETTINGS -> {
            SettingsScreen(
                settings = settings,
                currentCallsign = profile.callsign,
                onUpdateSettings = { vm.updateSettings(it) },
                onUpdateCallsign = {
                    soundEngine.play(SoundEffect.UI_CLICK)
                    vm.updateCallsign(it)
                },
                onBack = {
                    soundEngine.play(SoundEffect.UI_CLICK)
                    route = AppRoute.MENU
                }
            )
        }

        AppRoute.PLAY -> {
            key(matchInstanceId) {
                GamePlayScreen(
                    mode = activeMode,
                    difficulty = activeDifficulty,
                    profile = profile,
                    settings = settings,
                    soundEngine = soundEngine,
                    tournamentRoundName = if (activeMode == GameMode.TOURNAMENT) tournamentState.currentRoundName() else null,
                    onFinishMatch = { cs, vs, g, a, s, ot ->
                        vm.completeMatch(activeMode, activeDifficulty, cs, vs, g, a, s, ot)
                    },
                    onRematch = {
                        matchInstanceId++
                    },
                    onNextTournamentRound = {
                        activeDifficulty = tournamentState.currentDifficulty()
                        matchInstanceId++
                    },
                    onOpenSettingsFromPause = {
                        route = AppRoute.SETTINGS
                    },
                    onExitToMenu = {
                        soundEngine.play(SoundEffect.UI_CLICK)
                        route = AppRoute.MENU
                    }
                )
            }
        }
    }
}
