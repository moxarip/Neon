package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.ControlLayout
import com.example.data.model.CosmeticCatalog
import com.example.data.model.Difficulty
import com.example.data.model.GraphicsQuality
import com.example.data.model.PlayerProfile
import com.example.data.model.TournamentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.neonRinkDataStore: DataStore<Preferences> by preferencesDataStore(name = "neon_rink_save")

class LocalStorageManager(private val context: Context) {

    private object Keys {
        val MASTER_VOL = floatPreferencesKey("master_vol")
        val MUSIC_VOL = floatPreferencesKey("music_vol")
        val SFX_VOL = floatPreferencesKey("sfx_vol")
        val GRAPHICS = stringPreferencesKey("graphics_quality")
        val DIFFICULTY = stringPreferencesKey("difficulty")
        val SENSITIVITY = floatPreferencesKey("joystick_sensitivity")
        val CONTROL_LAYOUT = stringPreferencesKey("control_layout")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")

        val TOURNAMENT_ACTIVE = booleanPreferencesKey("tournament_active")
        val TOURNAMENT_ROUND = intPreferencesKey("tournament_round")
        val TOURNAMENT_ELIM = booleanPreferencesKey("tournament_elim")

        val CACHED_PROFILE_JSON = stringPreferencesKey("cached_profile_json")
        val LAST_SAVE_EPOCH = longPreferencesKey("last_save_epoch")
    }

    val settingsFlow: Flow<AppSettings> = context.neonRinkDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                masterVolume = (prefs[Keys.MASTER_VOL] ?: 0.85f).coerceIn(0f, 1f),
                musicVolume = (prefs[Keys.MUSIC_VOL] ?: 0.65f).coerceIn(0f, 1f),
                sfxVolume = (prefs[Keys.SFX_VOL] ?: 0.90f).coerceIn(0f, 1f),
                graphicsQuality = prefs[Keys.GRAPHICS]?.let { name ->
                    GraphicsQuality.entries.firstOrNull { it.name == name }
                } ?: GraphicsQuality.HIGH,
                difficulty = prefs[Keys.DIFFICULTY]?.let { name ->
                    Difficulty.entries.firstOrNull { it.name == name }
                } ?: Difficulty.NORMAL,
                joystickSensitivity = (prefs[Keys.SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 1.8f),
                controlLayout = prefs[Keys.CONTROL_LAYOUT]?.let { name ->
                    ControlLayout.entries.firstOrNull { it.name == name }
                } ?: ControlLayout.STANDARD,
                hapticsEnabled = prefs[Keys.HAPTICS] ?: true
            )
        }

    val tournamentFlow: Flow<TournamentState> = context.neonRinkDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            TournamentState(
                active = prefs[Keys.TOURNAMENT_ACTIVE] ?: false,
                round = (prefs[Keys.TOURNAMENT_ROUND] ?: 0).coerceIn(0, 3),
                eliminated = prefs[Keys.TOURNAMENT_ELIM] ?: false
            )
        }

    val cachedProfileFlow: Flow<PlayerProfile?> = context.neonRinkDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            prefs[Keys.CACHED_PROFILE_JSON]?.let { decodeProfileSafe(it) }
        }

    suspend fun saveSettings(settings: AppSettings) {
        context.neonRinkDataStore.edit { prefs ->
            prefs[Keys.MASTER_VOL] = settings.masterVolume
            prefs[Keys.MUSIC_VOL] = settings.musicVolume
            prefs[Keys.SFX_VOL] = settings.sfxVolume
            prefs[Keys.GRAPHICS] = settings.graphicsQuality.name
            prefs[Keys.DIFFICULTY] = settings.difficulty.name
            prefs[Keys.SENSITIVITY] = settings.joystickSensitivity
            prefs[Keys.CONTROL_LAYOUT] = settings.controlLayout.name
            prefs[Keys.HAPTICS] = settings.hapticsEnabled
        }
    }

    suspend fun saveTournament(state: TournamentState) {
        context.neonRinkDataStore.edit { prefs ->
            prefs[Keys.TOURNAMENT_ACTIVE] = state.active
            prefs[Keys.TOURNAMENT_ROUND] = state.round
            prefs[Keys.TOURNAMENT_ELIM] = state.eliminated
        }
    }

    suspend fun saveProfileCache(profile: PlayerProfile) {
        val json = JSONObject().apply {
            put("userId", profile.userId)
            put("callsign", profile.callsign)
            put("level", profile.level)
            put("xp", profile.xp)
            put("credits", profile.credits)
            put("totalMatches", profile.totalMatches)
            put("wins", profile.wins)
            put("losses", profile.losses)
            put("goals", profile.goals)
            put("assists", profile.assists)
            put("saves", profile.saves)
            put("bestTimeChallengeGoals", profile.bestTimeChallengeGoals)
            put("tournamentsWon", profile.tournamentsWon)
            put("unlockedItems", JSONArray(profile.unlockedItems))
            put("selectedOutfit", profile.selectedOutfit)
            put("selectedSkate", profile.selectedSkate)
            put("selectedTrail", profile.selectedTrail)
            put("selectedBall", profile.selectedBall)
            put("selectedGoalFx", profile.selectedGoalFx)
            put("selectedArena", profile.selectedArena)
            put("selectedCelebration", profile.selectedCelebration)
        }.toString()

        context.neonRinkDataStore.edit { prefs ->
            prefs[Keys.CACHED_PROFILE_JSON] = json
            prefs[Keys.LAST_SAVE_EPOCH] = System.currentTimeMillis()
        }
    }

    private fun decodeProfileSafe(raw: String): PlayerProfile? {
        return try {
            val obj = JSONObject(raw)
            val unlockedArr = obj.optJSONArray("unlockedItems")
            val unlocked = buildList {
                if (unlockedArr != null) {
                    for (i in 0 until unlockedArr.length()) {
                        val item = unlockedArr.optString(i)
                        if (item.isNotBlank()) add(item)
                    }
                }
            }.ifEmpty { CosmeticCatalog.defaultUnlocked }

            PlayerProfile(
                id = obj.optString("userId"),
                userId = obj.optString("userId"),
                callsign = obj.optString("callsign", "SkaterOne").ifBlank { "SkaterOne" },
                level = obj.optLong("level", 1L).coerceAtLeast(1L),
                xp = obj.optLong("xp", 0L).coerceAtLeast(0L),
                credits = obj.optLong("credits", 200L).coerceAtLeast(0L),
                totalMatches = obj.optLong("totalMatches", 0L).coerceAtLeast(0L),
                wins = obj.optLong("wins", 0L).coerceAtLeast(0L),
                losses = obj.optLong("losses", 0L).coerceAtLeast(0L),
                goals = obj.optLong("goals", 0L).coerceAtLeast(0L),
                assists = obj.optLong("assists", 0L).coerceAtLeast(0L),
                saves = obj.optLong("saves", 0L).coerceAtLeast(0L),
                bestTimeChallengeGoals = obj.optLong("bestTimeChallengeGoals", 0L).coerceAtLeast(0L),
                tournamentsWon = obj.optLong("tournamentsWon", 0L).coerceAtLeast(0L),
                unlockedItems = unlocked,
                selectedOutfit = obj.optString("selectedOutfit", "outfit_cyan_pulse"),
                selectedSkate = obj.optString("selectedSkate", "skate_aero"),
                selectedTrail = obj.optString("selectedTrail", "trail_cyan"),
                selectedBall = obj.optString("selectedBall", "ball_core"),
                selectedGoalFx = obj.optString("selectedGoalFx", "gfx_burst"),
                selectedArena = obj.optString("selectedArena", "arena_cyber_dome"),
                selectedCelebration = obj.optString("selectedCelebration", "celeb_spin")
            )
        } catch (_: Exception) {
            null
        }
    }
}
