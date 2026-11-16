package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffect
import com.example.audio.SoundEngine
import com.example.data.local.LocalStorageManager
import com.example.data.model.AppSettings
import com.example.data.model.CosmeticCategory
import com.example.data.model.CosmeticItem
import com.example.data.model.Difficulty
import com.example.data.model.GameMode
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchRecord
import com.example.data.model.PlayerProfile
import com.example.data.model.TournamentState
import com.example.data.remote.GameRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class MatchResultSummary(
    val mode: GameMode,
    val difficulty: Difficulty,
    val cyanScore: Int,
    val violetScore: Int,
    val won: Boolean,
    val overtime: Boolean,
    val goals: Int,
    val assists: Int,
    val saves: Int,
    val xpEarned: Long,
    val creditsEarned: Long,
    val leveledUp: Boolean,
    val newLevel: Long,
    val tournamentWonCup: Boolean = false
)

class NeonRinkViewModel(
    private val repository: GameRepository,
    private val localStorage: LocalStorageManager,
    val soundEngine: SoundEngine,
    private val currentUserId: String,
    private val defaultCallsign: String
) : ViewModel() {

    val settings: StateFlow<AppSettings> = localStorage.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettings())

    val tournamentState: StateFlow<TournamentState> = localStorage.tournamentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TournamentState())

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    val profileState: StateFlow<UiState<PlayerProfile>> = combine(
        repository.observeProfile(currentUserId)
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                Log.w(TAG, "Profile observe error", fsErr ?: error)
                emit(null)
            },
        localStorage.cachedProfileFlow
    ) { remote, cached ->
        val resolved = remote ?: cached
        if (resolved != null) {
            UiState.Success(resolved)
        } else {
            UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> =
        repository.observeGlobalLeaderboard()
            .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                Log.w(TAG, "Leaderboard observe error", fsErr ?: error)
                emit(UiState.Error((fsErr ?: error).message ?: "Failed to load leaderboard"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val matchHistoryState: StateFlow<UiState<List<MatchRecord>>> =
        repository.observeMatchHistory(currentUserId)
            .map<List<MatchRecord>, UiState<List<MatchRecord>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                Log.w(TAG, "Match history error", fsErr ?: error)
                emit(UiState.Error((fsErr ?: error).message ?: "Failed to load history"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            settings.collect { soundEngine.updateSettings(it) }
        }
        viewModelScope.launch {
            try {
                val ensured = repository.ensureProfileExists(currentUserId, defaultCallsign)
                localStorage.saveProfileCache(ensured)
            } catch (e: Exception) {
                Log.w(TAG, "Initial profile ensure failed; using local cache if available", e)
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        soundEngine.updateSettings(newSettings)
        viewModelScope.launch {
            localStorage.saveSettings(newSettings)
        }
    }

    fun updateCallsign(rawName: String) {
        val cleaned = rawName.trim().replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(24)
        if (cleaned.length < 2) return
        val current = (profileState.value as? UiState.Success)?.data ?: return
        val updated = current.copy(callsign = cleaned)
        persistProfile(updated)
    }

    fun unlockOrEquip(item: CosmeticItem) {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        val isUnlocked = current.unlockedItems.contains(item.id)
        if (!isUnlocked) {
            if (current.credits < item.cost) return
            soundEngine.play(SoundEffect.UNLOCK)
            val newUnlocked = (current.unlockedItems + item.id).distinct()
            val equipped = applyEquip(current.copy(credits = current.credits - item.cost, unlockedItems = newUnlocked), item)
            persistProfile(equipped)
        } else {
            soundEngine.play(SoundEffect.UI_CLICK)
            val equipped = applyEquip(current, item)
            persistProfile(equipped)
        }
    }

    private fun applyEquip(profile: PlayerProfile, item: CosmeticItem): PlayerProfile = when (item.category) {
        CosmeticCategory.OUTFIT -> profile.copy(selectedOutfit = item.id)
        CosmeticCategory.SKATE -> profile.copy(selectedSkate = item.id)
        CosmeticCategory.TRAIL -> profile.copy(selectedTrail = item.id)
        CosmeticCategory.BALL -> profile.copy(selectedBall = item.id)
        CosmeticCategory.GOAL_FX -> profile.copy(selectedGoalFx = item.id)
        CosmeticCategory.ARENA -> profile.copy(selectedArena = item.id)
        CosmeticCategory.CELEBRATION -> profile.copy(selectedCelebration = item.id)
    }

    fun startNewTournament() {
        viewModelScope.launch {
            localStorage.saveTournament(TournamentState(active = true, round = 0, eliminated = false))
        }
    }

    fun resetTournament() {
        viewModelScope.launch {
            localStorage.saveTournament(TournamentState(active = false, round = 0, eliminated = false))
        }
    }

    fun completeMatch(
        mode: GameMode,
        difficulty: Difficulty,
        cyanScore: Int,
        violetScore: Int,
        goals: Int,
        assists: Int,
        saves: Int,
        overtime: Boolean
    ): MatchResultSummary {
        val won = when (mode) {
            GameMode.TIME_CHALLENGE -> cyanScore > 0
            else -> cyanScore > violetScore
        }

        val diffMultiplier = when (difficulty) {
            Difficulty.EASY -> 0.85f
            Difficulty.NORMAL -> 1.0f
            Difficulty.HARD -> 1.25f
            Difficulty.EXPERT -> 1.55f
        }

        val baseXp = when (mode) {
            GameMode.PRACTICE -> 20L
            GameMode.TRAINING -> 150L
            GameMode.TIME_CHALLENGE -> 60L + cyanScore * 35L
            GameMode.QUICK_MATCH, GameMode.TOURNAMENT ->
                (if (won) 140L else 65L) + goals * 30L + assists * 18L + saves * 20L
        }
        val baseCredits = when (mode) {
            GameMode.PRACTICE -> 15L
            GameMode.TRAINING -> 120L
            GameMode.TIME_CHALLENGE -> 40L + cyanScore * 25L
            GameMode.QUICK_MATCH, GameMode.TOURNAMENT ->
                (if (won) 110L else 45L) + goals * 20L + assists * 12L + saves * 14L
        }

        var xpEarned = (baseXp * diffMultiplier).toLong().coerceAtLeast(10L)
        var creditsEarned = (baseCredits * diffMultiplier).toLong().coerceAtLeast(10L)

        var cupWon = false
        if (mode == GameMode.TOURNAMENT) {
            val tState = tournamentState.value
            if (won) {
                if (tState.round >= 2) {
                    cupWon = true
                    xpEarned += 350L
                    creditsEarned += 400L
                    viewModelScope.launch {
                        localStorage.saveTournament(TournamentState(active = false, round = 3, eliminated = false))
                    }
                } else {
                    viewModelScope.launch {
                        localStorage.saveTournament(TournamentState(active = true, round = tState.round + 1, eliminated = false))
                    }
                }
            } else {
                viewModelScope.launch {
                    localStorage.saveTournament(TournamentState(active = true, round = tState.round, eliminated = true))
                }
            }
        }

        val current = (profileState.value as? UiState.Success)?.data
            ?: PlayerProfile(id = currentUserId, userId = currentUserId, callsign = defaultCallsign)

        var totalXp = current.xp + xpEarned
        var lvl = current.level
        var leveledUp = false
        while (totalXp >= lvl * 250L) {
            totalXp -= lvl * 250L
            lvl++
            leveledUp = true
        }

        val isRankedMode = mode == GameMode.QUICK_MATCH || mode == GameMode.TOURNAMENT
        val updatedProfile = current.copy(
            level = lvl,
            xp = totalXp,
            credits = current.credits + creditsEarned,
            totalMatches = current.totalMatches + if (isRankedMode || mode == GameMode.TIME_CHALLENGE) 1L else 0L,
            wins = current.wins + if (isRankedMode && won) 1L else 0L,
            losses = current.losses + if (isRankedMode && !won) 1L else 0L,
            goals = current.goals + goals.toLong(),
            assists = current.assists + assists.toLong(),
            saves = current.saves + saves.toLong(),
            bestTimeChallengeGoals = if (mode == GameMode.TIME_CHALLENGE) {
                maxOf(current.bestTimeChallengeGoals, cyanScore.toLong())
            } else {
                current.bestTimeChallengeGoals
            },
            tournamentsWon = current.tournamentsWon + if (cupWon) 1L else 0L
        )

        persistProfile(updatedProfile)

        if (mode == GameMode.QUICK_MATCH || mode == GameMode.TOURNAMENT || mode == GameMode.TIME_CHALLENGE) {
            viewModelScope.launch {
                try {
                    repository.recordMatch(
                        currentUserId,
                        MatchRecord(
                            mode = mode.wireValue,
                            difficulty = difficulty.wireValue,
                            playerScore = cyanScore.toLong(),
                            opponentScore = violetScore.toLong(),
                            won = won,
                            goalsScored = goals.toLong(),
                            assists = assists.toLong(),
                            saves = saves.toLong(),
                            xpEarned = xpEarned,
                            creditsEarned = creditsEarned,
                            overtime = overtime
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record match history remotely", e)
                }
            }
        }

        return MatchResultSummary(
            mode = mode,
            difficulty = difficulty,
            cyanScore = cyanScore,
            violetScore = violetScore,
            won = won,
            overtime = overtime,
            goals = goals,
            assists = assists,
            saves = saves,
            xpEarned = xpEarned,
            creditsEarned = creditsEarned,
            leveledUp = leveledUp,
            newLevel = lvl,
            tournamentWonCup = cupWon
        )
    }

    private fun persistProfile(profile: PlayerProfile) {
        viewModelScope.launch {
            localStorage.saveProfileCache(profile)
            try {
                repository.saveProfile(currentUserId, profile)
                _profileError.value = null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync profile to Firestore", e)
                _profileError.value = "Offline mode: saved locally"
            }
        }
    }

    private companion object {
        const val TAG = "NeonRinkVM"
    }
}
