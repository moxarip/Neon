package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GameMode(val wireValue: String, val displayName: String, val subtitle: String) {
    QUICK_MATCH("quick_match", "Quick Match", "3v3 Full 3-Min Arena Match"),
    TOURNAMENT("tournament", "Overdrive Cup", "3-Round Knockout Championship"),
    TRAINING("training", "Training Academy", "Interactive Skate, Pass & Shoot Drills"),
    PRACTICE("practice", "Practice Arena", "Free-Skate Rink & Shot Lab"),
    TIME_CHALLENGE("time_challenge", "Time Challenge", "Score Max Goals in 90 Seconds")
}

enum class Difficulty(val wireValue: String, val label: String, val aiSpeedMul: Float, val reactionSec: Float) {
    EASY("easy", "Easy", 0.72f, 0.42f),
    NORMAL("normal", "Normal", 0.88f, 0.25f),
    HARD("hard", "Hard", 1.02f, 0.14f),
    EXPERT("expert", "Expert", 1.16f, 0.06f)
}

enum class GraphicsQuality(val label: String, val particleScale: Float, val drawCrowd: Boolean, val drawReflections: Boolean) {
    LOW("Low", 0.4f, false, false),
    MEDIUM("Medium", 0.75f, true, false),
    HIGH("High", 1.0f, true, true)
}

enum class ControlLayout(val label: String) {
    STANDARD("Standard"),
    COMPACT("Compact"),
    SWAPPED("Lefty Swap")
}

enum class PlayerRole(val label: String) {
    STRIKER("Striker"),
    DEFENDER("Defender"),
    GOALKEEPER("Guardian")
}

enum class CosmeticCategory(val label: String) {
    OUTFIT("Outfits"),
    SKATE("Skates"),
    TRAIL("Neon Trails"),
    BALL("Energy Balls"),
    GOAL_FX("Goal FX"),
    ARENA("Arenas"),
    CELEBRATION("Celebrations")
}

data class CosmeticItem(
    val id: String,
    val category: CosmeticCategory,
    val name: String,
    val description: String,
    val cost: Int,
    val primaryHex: Long,
    val accentHex: Long
)

object CosmeticCatalog {
    val items: List<CosmeticItem> = listOf(
        // Outfits
        CosmeticItem("outfit_cyan_pulse", CosmeticCategory.OUTFIT, "Cyan Pulse", "Standard Cyber League exo-suit", 0, 0xFF00F0FF, 0xFFF8FAFC),
        CosmeticItem("outfit_violet_nova", CosmeticCategory.OUTFIT, "Violet Nova", "High-voltage amethyst armor", 250, 0xFFA855F7, 0xFF00F0FF),
        CosmeticItem("outfit_solar_flare", CosmeticCategory.OUTFIT, "Solar Overdrive", "Reactor orange & titanium weave", 500, 0xFFFF6B1A, 0xFFF8FAFC),
        CosmeticItem("outfit_arctic_ghost", CosmeticCategory.OUTFIT, "Arctic Phantom", "Stealth pearl & ice-cyan trim", 800, 0xFFE2E8F0, 0xFF00F0FF),

        // Skates
        CosmeticItem("skate_aero", CosmeticCategory.SKATE, "Aero Blade MK-I", "Balanced inline thruster frames", 0, 0xFF00F0FF, 0xFF38BDF8),
        CosmeticItem("skate_maglev", CosmeticCategory.SKATE, "MagLev Twin", "Dual magnetic core rollers", 200, 0xFFA855F7, 0xFFE879F9),
        CosmeticItem("skate_apex", CosmeticCategory.SKATE, "Apex Vector", "Carbon-titanium overdrive runners", 450, 0xFFFF6B1A, 0xFFFACC15),

        // Trails
        CosmeticItem("trail_cyan", CosmeticCategory.TRAIL, "Ion Cyan", "Clean electric cyan slipstream", 0, 0xFF00F0FF, 0xFFFFFFFF),
        CosmeticItem("trail_prism", CosmeticCategory.TRAIL, "Violet Surge", "Plasma violet wake ribbon", 180, 0xFFA855F7, 0xFF00F0FF),
        CosmeticItem("trail_ember", CosmeticCategory.TRAIL, "Overdrive Spark", "Blazing orange photon stream", 400, 0xFFFF6B1A, 0xFFFDE047),

        // Balls
        CosmeticItem("ball_core", CosmeticCategory.BALL, "Pulse Core", "Standard ionized cyan match orb", 0, 0xFF00F0FF, 0xFFFFFFFF),
        CosmeticItem("ball_nebula", CosmeticCategory.BALL, "Nebula Orb", "Swirling violet plasma sphere", 220, 0xFFA855F7, 0xFF38BDF8),
        CosmeticItem("ball_reactor", CosmeticCategory.BALL, "Reactor Sun", "Superheated orange fusion ball", 480, 0xFFFF6B1A, 0xFFFFFFFF),

        // Goal FX
        CosmeticItem("gfx_burst", CosmeticCategory.GOAL_FX, "Shockwave", "Expanding neon shock ring", 0, 0xFF00F0FF, 0xFFA855F7),
        CosmeticItem("gfx_supernova", CosmeticCategory.GOAL_FX, "Supernova", "Multi-tier prism particle flare", 300, 0xFFA855F7, 0xFFFF6B1A),
        CosmeticItem("gfx_overcharge", CosmeticCategory.GOAL_FX, "Overcharge", "Vertical cyber pillar blast", 550, 0xFF00F0FF, 0xFFFF6B1A),

        // Arenas
        CosmeticItem("arena_cyber_dome", CosmeticCategory.ARENA, "Cyber Dome", "Classic Obsidian & Cyan stadium", 0, 0xFF00F0FF, 0xFFA855F7),
        CosmeticItem("arena_hyperion", CosmeticCategory.ARENA, "Hyperion Vault", "Deep violet high-altitude rink", 350, 0xFFA855F7, 0xFF38BDF8),
        CosmeticItem("arena_solaris", CosmeticCategory.ARENA, "Solaris Circuit", "Titanium deck with orange trim", 650, 0xFFFF6B1A, 0xFF00F0FF),

        // Celebrations
        CosmeticItem("celeb_spin", CosmeticCategory.CELEBRATION, "360 Aero Spin", "High-speed victory skate spin", 0, 0xFF00F0FF, 0xFFFFFFFF),
        CosmeticItem("celeb_leap", CosmeticCategory.CELEBRATION, "Overdrive Leap", "Boosted mid-air fist pump", 250, 0xFFA855F7, 0xFF00F0FF),
        CosmeticItem("celeb_drift", CosmeticCategory.CELEBRATION, "Spark Slide", "Low-stance knee-carve victory slide", 420, 0xFFFF6B1A, 0xFFFFFFFF)
    )

    val defaultUnlocked: List<String> = items.filter { it.cost == 0 }.map { it.id }

    fun byId(id: String): CosmeticItem = items.firstOrNull { it.id == id } ?: items.first()
}

data class AppSettings(
    val masterVolume: Float = 0.85f,
    val musicVolume: Float = 0.65f,
    val sfxVolume: Float = 0.90f,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val joystickSensitivity: Float = 1.0f,
    val controlLayout: ControlLayout = ControlLayout.STANDARD,
    val hapticsEnabled: Boolean = true
)

@IgnoreExtraProperties
data class PlayerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "SkaterOne",
    val level: Long = 1L,
    val xp: Long = 0L,
    val credits: Long = 200L,
    val totalMatches: Long = 0L,
    val wins: Long = 0L,
    val losses: Long = 0L,
    val goals: Long = 0L,
    val assists: Long = 0L,
    val saves: Long = 0L,
    val bestTimeChallengeGoals: Long = 0L,
    val tournamentsWon: Long = 0L,
    val unlockedItems: List<String> = CosmeticCatalog.defaultUnlocked,
    val selectedOutfit: String = "outfit_cyan_pulse",
    val selectedSkate: String = "skate_aero",
    val selectedTrail: String = "trail_cyan",
    val selectedBall: String = "ball_core",
    val selectedGoalFx: String = "gfx_burst",
    val selectedArena: String = "arena_cyber_dome",
    val selectedCelebration: String = "celeb_spin",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun xpForNextLevel(): Long = level * 250L

    fun competitiveRating(): Long =
        wins * 100L + goals * 15L + assists * 8L + saves * 10L + tournamentsWon * 250L + bestTimeChallengeGoals * 20L

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "callsign" to callsign.take(24).ifBlank { "SkaterOne" },
        "level" to level,
        "xp" to xp,
        "credits" to credits,
        "totalMatches" to totalMatches,
        "wins" to wins,
        "losses" to losses,
        "goals" to goals,
        "assists" to assists,
        "saves" to saves,
        "bestTimeChallengeGoals" to bestTimeChallengeGoals,
        "tournamentsWon" to tournamentsWon,
        "unlockedItems" to unlockedItems.ifEmpty { CosmeticCatalog.defaultUnlocked },
        "selectedOutfit" to selectedOutfit,
        "selectedSkate" to selectedSkate,
        "selectedTrail" to selectedTrail,
        "selectedBall" to selectedBall,
        "selectedGoalFx" to selectedGoalFx,
        "selectedArena" to selectedArena,
        "selectedCelebration" to selectedCelebration,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "callsign" to callsign.take(24).ifBlank { "SkaterOne" },
        "level" to level,
        "xp" to xp,
        "credits" to credits,
        "totalMatches" to totalMatches,
        "wins" to wins,
        "losses" to losses,
        "goals" to goals,
        "assists" to assists,
        "saves" to saves,
        "bestTimeChallengeGoals" to bestTimeChallengeGoals,
        "tournamentsWon" to tournamentsWon,
        "unlockedItems" to unlockedItems.ifEmpty { CosmeticCatalog.defaultUnlocked },
        "selectedOutfit" to selectedOutfit,
        "selectedSkate" to selectedSkate,
        "selectedTrail" to selectedTrail,
        "selectedBall" to selectedBall,
        "selectedGoalFx" to selectedGoalFx,
        "selectedArena" to selectedArena,
        "selectedCelebration" to selectedCelebration,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val id: String = "",
    val mode: String = GameMode.QUICK_MATCH.wireValue,
    val difficulty: String = Difficulty.NORMAL.wireValue,
    val playerScore: Long = 0L,
    val opponentScore: Long = 0L,
    val won: Boolean = false,
    val goalsScored: Long = 0L,
    val assists: Long = 0L,
    val saves: Long = 0L,
    val xpEarned: Long = 0L,
    val creditsEarned: Long = 0L,
    val overtime: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "mode" to mode,
        "difficulty" to difficulty,
        "playerScore" to playerScore,
        "opponentScore" to opponentScore,
        "won" to won,
        "goalsScored" to goalsScored,
        "assists" to assists,
        "saves" to saves,
        "xpEarned" to xpEarned,
        "creditsEarned" to creditsEarned,
        "overtime" to overtime,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "SkaterOne",
    val rating: Long = 0L,
    val level: Long = 1L,
    val wins: Long = 0L,
    val goals: Long = 0L,
    val bestTimeChallenge: Long = 0L,
    val selectedOutfit: String = "outfit_cyan_pulse",
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "callsign" to callsign.take(24).ifBlank { "SkaterOne" },
        "rating" to rating,
        "level" to level,
        "wins" to wins,
        "goals" to goals,
        "bestTimeChallenge" to bestTimeChallenge,
        "selectedOutfit" to selectedOutfit,
        "isPublic" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "callsign" to callsign.take(24).ifBlank { "SkaterOne" },
        "rating" to rating,
        "level" to level,
        "wins" to wins,
        "goals" to goals,
        "bestTimeChallenge" to bestTimeChallenge,
        "selectedOutfit" to selectedOutfit,
        "isPublic" to true,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class TournamentState(
    val active: Boolean = false,
    val round: Int = 0, // 0 = Quarterfinal, 1 = Semifinal, 2 = Final, 3 = Champion!
    val eliminated: Boolean = false
) {
    fun currentRoundName(): String = when (round) {
        0 -> "Quarterfinal"
        1 -> "Semifinal"
        2 -> "Overdrive Cup Final"
        else -> "Champion"
    }

    fun currentDifficulty(): Difficulty = when (round) {
        0 -> Difficulty.NORMAL
        1 -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }
}
