package com.example.game.engine

import com.example.audio.SoundEffect
import com.example.data.model.Difficulty
import com.example.data.model.GameMode
import com.example.data.model.PlayerRole
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class MatchPhase {
    COUNTDOWN,
    PLAYING,
    GOAL_REPLAY,
    OVERTIME,
    ENDED
}

data class PlayerEntity(
    val id: Int,
    val team: Int, // 0 = Cyan (User team), 1 = Violet (Opponent)
    val role: PlayerRole,
    val isUserControlled: Boolean,
    val pos: Vec3 = Vec3(),
    val vel: Vec3 = Vec3(),
    var facingAngle: Float = 0f,
    var leanAngle: Float = 0f,
    var stridePhase: Float = 0f,
    var energy: Float = 100f,
    var boostTimer: Float = 0f,
    var tackleTimer: Float = 0f,
    var tackleCooldown: Float = 0f,
    var stunTimer: Float = 0f,
    var celebrateTimer: Float = 0f,
    var aiDecisionTimer: Float = 0f,
    var aiDesiredDirX: Float = 0f,
    var aiDesiredDirZ: Float = 0f
)

data class Particle(
    var active: Boolean = false,
    val pos: Vec3 = Vec3(),
    val vel: Vec3 = Vec3(),
    var r: Float = 0f,
    var g: Float = 1f,
    var b: Float = 1f,
    var life: Float = 0f,
    var maxLife: Float = 1f,
    var size: Float = 0.2f
)

data class MatchHudSnapshot(
    val phase: MatchPhase = MatchPhase.COUNTDOWN,
    val cyanScore: Int = 0,
    val violetScore: Int = 0,
    val timeRemainingSec: Float = 180f,
    val countdownSec: Int = 3,
    val energy: Float = 100f,
    val chargeProgress: Float = 0f,
    val tackleCooldownFrac: Float = 0f,
    val userHasBall: Boolean = false,
    val carrierLabel: String = "LOOSE BALL",
    val carrierTeam: Int = -1,
    val goalBannerText: String? = null,
    val bannerColorTeam: Int = 0,
    val lastShotSpeedKmh: Int = 0,
    val userGoals: Int = 0,
    val userAssists: Int = 0,
    val userSaves: Int = 0,
    val isOvertime: Boolean = false,
    val trainingStep: Int = 0,
    val trainingInstruction: String = "",
    val trainingCompleted: Boolean = false
)

class MatchSimulation(
    val mode: GameMode,
    val difficulty: Difficulty,
    private val onSound: (SoundEffect) -> Unit,
    private val onHaptic: (Boolean) -> Unit
) {
    companion object {
        const val RINK_HALF_LEN = 26f
        const val RINK_HALF_WID = 15f
        const val CORNER_RADIUS = 4.5f
        const val GOAL_HALF_WIDTH = 3.4f
        const val GOAL_HEIGHT = 2.8f
        const val GOAL_DEPTH = 2.0f
        const val BALL_RADIUS = 0.52f
        const val SKATER_RADIUS = 0.72f
    }

    val players = mutableListOf<PlayerEntity>()
    val ballPos = Vec3(0f, BALL_RADIUS, 0f)
    val ballVel = Vec3(0f, 0f, 0f)
    var ballSpin = 0f
    var ownerId = -1
    var lastTouchTeam = -1
    var lastCyanTouchId = -1
    var prevCyanTouchId = -1

    private val trailCapacity = 20
    val ballTrail = Array(trailCapacity) { Vec3(0f, -10f, 0f) }
    var trailHead = 0

    val particles = Array(180) { Particle() }
    private var particleCursor = 0

    var phase = MatchPhase.COUNTDOWN
        private set
    var cyanScore = 0
    var violetScore = 0
    var matchTimerSec = when (mode) {
        GameMode.TIME_CHALLENGE -> 90f
        GameMode.PRACTICE, GameMode.TRAINING -> 999f
        else -> 180f
    }
    var isOvertime = false
    private var countdownTimer = 3.2f
    private var goalReplayTimer = 0f
    private var pickupLockTimer = 0f
    private var lastScoringTeam = 0
    private var goalBannerText: String? = null
    private var lastShotSpeedKmh = 0

    // Match user stats
    var userGoals = 0
    var userAssists = 0
    var userSaves = 0

    // Camera & juice state
    var cameraShake = 0f
    var timeScale = 1f
    var simTime = 0f
    val cameraPos = Vec3(0f, 18f, 22f)
    val cameraTarget = Vec3(0f, 0.8f, 0f)
    var goalReplayAngle = 0f

    // Training state
    var trainingStep = 0 // 0=Skate Gate, 1=Boost, 2=Pass, 3=Charged Shot Goal, 4=Tackle Steal, 5=Done
    var trainingGatePos = Vec3(6f, 0f, 0f)
    private var trainingGatesCleared = 0
    private var trainingCompleted = false

    // Input state (written from UI thread, read by GL tick)
    @Volatile var joyX = 0f
    @Volatile var joyY = 0f
    @Volatile var isChargingShot = false
    private var shotChargeTime = 0f
    @Volatile private var pendingPass = false
    @Volatile private var pendingBoost = false
    @Volatile private var pendingTackle = false
    @Volatile private var pendingResetPractice = false
    @Volatile var joystickSensitivity = 1.0f

    private var lastBeepSec = 4
    private var bounceSoundCooldown = 0f

    init {
        setupRoster()
        resetPositionsForKickoff()
        if (mode == GameMode.PRACTICE || mode == GameMode.TRAINING) {
            phase = MatchPhase.PLAYING
            countdownTimer = 0f
        }
    }

    private fun setupRoster() {
        players.clear()
        when (mode) {
            GameMode.TIME_CHALLENGE -> {
                players.add(PlayerEntity(0, 0, PlayerRole.STRIKER, isUserControlled = true))
                players.add(PlayerEntity(1, 0, PlayerRole.DEFENDER, isUserControlled = false))
                players.add(PlayerEntity(2, 1, PlayerRole.DEFENDER, isUserControlled = false))
                players.add(PlayerEntity(3, 1, PlayerRole.GOALKEEPER, isUserControlled = false))
            }
            GameMode.PRACTICE -> {
                players.add(PlayerEntity(0, 0, PlayerRole.STRIKER, isUserControlled = true))
                players.add(PlayerEntity(1, 0, PlayerRole.DEFENDER, isUserControlled = false))
                players.add(PlayerEntity(2, 1, PlayerRole.GOALKEEPER, isUserControlled = false))
            }
            GameMode.TRAINING -> {
                players.add(PlayerEntity(0, 0, PlayerRole.STRIKER, isUserControlled = true))
                players.add(PlayerEntity(1, 0, PlayerRole.DEFENDER, isUserControlled = false))
                players.add(PlayerEntity(2, 1, PlayerRole.DEFENDER, isUserControlled = false))
                players.add(PlayerEntity(3, 1, PlayerRole.GOALKEEPER, isUserControlled = false))
            }
            else -> {
                // Full 3v3
                players.add(PlayerEntity(0, 0, PlayerRole.STRIKER, isUserControlled = true))
                players.add(PlayerEntity(1, 0, PlayerRole.DEFENDER, isUserControlled = false))
                players.add(PlayerEntity(2, 0, PlayerRole.GOALKEEPER, isUserControlled = false))
                players.add(PlayerEntity(3, 1, PlayerRole.STRIKER, isUserControlled = false))
                players.add(PlayerEntity(4, 1, PlayerRole.DEFENDER, isUserControlled = false))
                players.add(PlayerEntity(5, 1, PlayerRole.GOALKEEPER, isUserControlled = false))
            }
        }
    }

    fun resetPositionsForKickoff() {
        ownerId = -1
        pickupLockTimer = 0.25f
        ballPos.set(0f, BALL_RADIUS + 1.2f, 0f)
        ballVel.set(0f, 0f, 0f)
        for (i in 0 until trailCapacity) {
            ballTrail[i].set(0f, -10f, 0f)
        }

        for (p in players) {
            p.vel.set(0f, 0f, 0f)
            p.leanAngle = 0f
            p.stunTimer = 0f
            p.boostTimer = 0f
            p.tackleTimer = 0f
            p.celebrateTimer = 0f
            p.energy = 100f
            val sign = if (p.team == 0) -1f else 1f
            p.facingAngle = if (p.team == 0) 0f else PI.toFloat()
            when (p.role) {
                PlayerRole.STRIKER -> p.pos.set(sign * 4.5f, 0f, 0f)
                PlayerRole.DEFENDER -> p.pos.set(sign * 12.5f, 0f, if (p.team == 0) -4.2f else 4.2f)
                PlayerRole.GOALKEEPER -> p.pos.set(sign * 23.8f, 0f, 0f)
            }
        }

        if (mode == GameMode.TRAINING && trainingStep == 4) {
            // Put ball on opponent defender for tackle drill
            val opp = players.firstOrNull { it.team == 1 && it.role == PlayerRole.DEFENDER }
            if (opp != null) {
                opp.pos.set(4f, 0f, 0f)
                ownerId = opp.id
            }
        }
    }

    fun triggerPass() {
        pendingPass = true
    }

    fun triggerBoost() {
        pendingBoost = true
    }

    fun triggerTackle() {
        pendingTackle = true
    }

    fun triggerPracticeReset() {
        pendingResetPractice = true
    }

    fun onShootPressed() {
        isChargingShot = true
        shotChargeTime = 0f
    }

    fun onShootReleased() {
        if (!isChargingShot) return
        isChargingShot = false
        val user = players.firstOrNull { it.isUserControlled } ?: return
        if (phase != MatchPhase.PLAYING && phase != MatchPhase.OVERTIME) return
        if (ownerId == user.id) {
            executeShot(user, (shotChargeTime / 0.85f).coerceIn(0f, 1f))
        }
        shotChargeTime = 0f
    }

    fun step(rawDt: Float) {
        val dt = rawDt.coerceIn(0.001f, 0.045f)
        simTime += dt

        if (pendingResetPractice) {
            pendingResetPractice = false
            resetPositionsForKickoff()
        }

        bounceSoundCooldown = max(0f, bounceSoundCooldown - dt)
        cameraShake = max(0f, cameraShake - dt * 2.8f)

        updateParticles(dt)

        when (phase) {
            MatchPhase.COUNTDOWN -> {
                countdownTimer -= dt
                val sec = countdownTimer.toInt() + 1
                if (sec in 1..3 && sec != lastBeepSec) {
                    lastBeepSec = sec
                    onSound(SoundEffect.COUNTDOWN_BEEP)
                }
                if (countdownTimer <= 0f) {
                    phase = MatchPhase.PLAYING
                    onSound(SoundEffect.WHISTLE)
                    goalBannerText = null
                }
                updateCamera(dt)
                return
            }

            MatchPhase.GOAL_REPLAY -> {
                goalReplayTimer -= dt
                timeScale = 0.36f
                val scaledDt = dt * timeScale
                stepBallPhysics(scaledDt, allowGoalTrigger = false)
                for (p in players) {
                    if (p.team == lastScoringTeam) {
                        p.celebrateTimer += dt
                    }
                }
                goalReplayAngle += dt * 0.9f
                updateCamera(dt)
                if (goalReplayTimer <= 0f) {
                    timeScale = 1f
                    goalBannerText = null
                    if (isOvertime) {
                        phase = MatchPhase.ENDED
                        onSound(SoundEffect.WHISTLE)
                    } else {
                        phase = MatchPhase.PLAYING
                        resetPositionsForKickoff()
                        onSound(SoundEffect.WHISTLE)
                    }
                }
                return
            }

            MatchPhase.ENDED -> {
                updateCamera(dt)
                return
            }

            MatchPhase.PLAYING, MatchPhase.OVERTIME -> {
                timeScale = 1f
            }
        }

        // Match clock
        if (mode != GameMode.PRACTICE && mode != GameMode.TRAINING) {
            if (phase == MatchPhase.PLAYING) {
                matchTimerSec -= dt
                if (matchTimerSec <= 0f) {
                    matchTimerSec = 0f
                    if (mode != GameMode.TIME_CHALLENGE && cyanScore == violetScore) {
                        isOvertime = true
                        phase = MatchPhase.OVERTIME
                        goalBannerText = "OVERTIME - GOLDEN GOAL!"
                        onSound(SoundEffect.WHISTLE)
                        resetPositionsForKickoff()
                    } else {
                        phase = MatchPhase.ENDED
                        onSound(SoundEffect.WHISTLE)
                        return
                    }
                }
            } else if (phase == MatchPhase.OVERTIME) {
                matchTimerSec += dt
            }
        }

        if (pickupLockTimer > 0f) pickupLockTimer -= dt

        // Handle user charge & buttons
        val user = players.firstOrNull { it.isUserControlled }
        if (user != null) {
            if (isChargingShot && ownerId == user.id) {
                shotChargeTime = min(1.0f, shotChargeTime + dt)
            }
            if (pendingBoost) {
                pendingBoost = false
                tryBoost(user)
            }
            if (pendingTackle) {
                pendingTackle = false
                tryTackle(user)
            }
            if (pendingPass) {
                pendingPass = false
                if (ownerId == user.id) {
                    executePass(user)
                } else {
                    // Quick switch / dash toward ball if no possession
                    tryTackle(user)
                }
            }
        }

        // Step players (User + AI)
        for (p in players) {
            if (p.isUserControlled) {
                stepUserSkater(p, dt)
            } else {
                stepAiSkater(p, dt)
            }
        }

        resolvePlayerCollisions()
        stepBallPhysics(dt, allowGoalTrigger = true)
        stepTrainingDrillChecks()
        updateCamera(dt)
    }

    private fun tryBoost(p: PlayerEntity) {
        if (p.energy >= 24f && p.stunTimer <= 0f) {
            p.energy = max(0f, p.energy - 26f)
            p.boostTimer = 0.65f
            val fwdX = cos(p.facingAngle)
            val fwdZ = sin(p.facingAngle)
            p.vel.x += fwdX * 7.5f
            p.vel.z += fwdZ * 7.5f
            onSound(SoundEffect.BOOST)
            spawnBurst(p.pos.x, 0.35f, p.pos.z, 10, 0f, 0.95f, 1f, 5.5f)
            if (p.isUserControlled && mode == GameMode.TRAINING && trainingStep == 1) {
                trainingStep = 2
                onSound(SoundEffect.UNLOCK)
            }
        }
    }

    private fun tryTackle(p: PlayerEntity) {
        if (p.tackleCooldown > 0f || p.stunTimer > 0f) return
        p.tackleTimer = 0.28f
        p.tackleCooldown = 1.25f
        val fwdX = cos(p.facingAngle)
        val fwdZ = sin(p.facingAngle)
        p.vel.x = fwdX * 18.5f
        p.vel.z = fwdZ * 18.5f
        onSound(SoundEffect.TACKLE)
        spawnBurst(p.pos.x, 0.5f, p.pos.z, 8, 1f, 0.55f, 0.15f, 4.5f)
    }

    private fun stepUserSkater(p: PlayerEntity, dt: Float) {
        updatePlayerTimers(p, dt)
        val mag = sqrt(joyX * joyX + joyY * joyY)
        val sens = joystickSensitivity
        val inputX = if (mag > 0.08f) (joyX / max(1f, mag)) * min(1f, mag * sens) else 0f
        val inputZ = if (mag > 0.08f) (joyY / max(1f, mag)) * min(1f, mag * sens) else 0f
        applySkatingPhysics(p, inputX, inputZ, dt)
    }

    private fun stepAiSkater(p: PlayerEntity, dt: Float) {
        updatePlayerTimers(p, dt)
        if (mode == GameMode.TRAINING && trainingStep < 4 && p.team == 1 && p.role == PlayerRole.DEFENDER) {
            // Stay passive on sideline during early drills
            applySkatingPhysics(p, 0f, 0f, dt)
            return
        }

        p.aiDecisionTimer -= dt
        val speedScale = if (p.team == 1) difficulty.aiSpeedMul else 0.94f

        if (p.aiDecisionTimer <= 0f) {
            p.aiDecisionTimer = if (p.team == 1) difficulty.reactionSec else 0.14f
            computeAiDecision(p)
        }

        applySkatingPhysics(p, p.aiDesiredDirX * speedScale, p.aiDesiredDirZ * speedScale, dt)
    }

    private fun computeAiDecision(p: PlayerEntity) {
        val ownGoalX = if (p.team == 0) -RINK_HALF_LEN else RINK_HALF_LEN
        val oppGoalX = -ownGoalX
        val attackDir = if (p.team == 0) 1f else -1f

        val owner = players.firstOrNull { it.id == ownerId }
        val teamHasBall = owner?.team == p.team
        val oppHasBall = owner != null && owner.team != p.team

        // If AI holds the ball
        if (ownerId == p.id) {
            if (p.role == PlayerRole.GOALKEEPER) {
                // Immediately pass out to open teammate
                executePass(p)
                return
            }

            val distToGoal = abs(oppGoalX - p.pos.x)
            val nearestEnemy = players.filter { it.team != p.team }
                .minByOrNull { it.pos.distanceXZ(p.pos) }
            val enemyPressure = nearestEnemy?.pos?.distanceXZ(p.pos) ?: 99f

            // Shoot when in range with lane
            if (distToGoal < 14.5f && abs(p.pos.z) < 7.5f) {
                val charge = if (difficulty == Difficulty.HARD || difficulty == Difficulty.EXPERT) 0.72f else 0.4f
                executeShot(p, charge)
                return
            }

            // Pass under heavy pressure if a forward teammate is open
            if (enemyPressure < 3.4f) {
                val openMate = players.firstOrNull {
                    it.team == p.team && it.id != p.id && it.role != PlayerRole.GOALKEEPER &&
                        (it.pos.x - p.pos.x) * attackDir > 1.5f
                }
                if (openMate != null) {
                    executePass(p)
                    return
                }
            }

            // Skate toward opponent goal while weaving around nearest defender
            var tx = oppGoalX - attackDir * 5f
            var tz = p.pos.z * 0.5f
            if (nearestEnemy != null && enemyPressure < 5.5f) {
                tz += if (p.pos.z >= nearestEnemy.pos.z) 3.8f else -3.8f
            }
            steerToward(p, tx, tz.coerceIn(-RINK_HALF_WID + 2.5f, RINK_HALF_WID - 2.5f))
            return
        }

        // Role logic without the ball
        when (p.role) {
            PlayerRole.GOALKEEPER -> {
                val creaseX = ownGoalX - attackDir * -2.2f
                val targetZ = ballPos.z.coerceIn(-GOAL_HALF_WIDTH + 0.4f, GOAL_HALF_WIDTH - 0.4f)
                // If ball is very close inside box, rush out to sweep!
                if (abs(ballPos.x - ownGoalX) < 5.5f && abs(ballPos.z) < 5.0f && ownerId == -1) {
                    steerToward(p, ballPos.x, ballPos.z)
                } else {
                    steerToward(p, creaseX, targetZ)
                }
            }

            PlayerRole.DEFENDER -> {
                if (owner != null && owner.team != p.team) {
                    // Intercept between ball carrier and own goal
                    val interceptX = Vec3.lerp(owner.pos.x, ownGoalX, 0.32f)
                    val interceptZ = Vec3.lerp(owner.pos.z, 0f, 0.25f)
                    steerToward(p, interceptX, interceptZ)
                    if (owner.pos.distanceXZ(p.pos) < 2.9f && p.team == 1 && difficulty != Difficulty.EASY) {
                        tryTackle(p)
                    }
                } else if (owner != null && owner.team == p.team) {
                    // Support behind/beside carrier
                    val supportX = (owner.pos.x - attackDir * 6.5f).coerceIn(-18f, 18f)
                    val supportZ = if (owner.pos.z > 0f) -5.5f else 5.5f
                    steerToward(p, supportX, supportZ)
                } else {
                    // Loose ball: chase if closest defender
                    val closestMate = players.filter { it.team == p.team && it.role != PlayerRole.GOALKEEPER }
                        .minByOrNull { it.pos.distanceXZ(ballPos) }
                    if (closestMate?.id == p.id) {
                        steerToward(p, ballPos.x, ballPos.z)
                    } else {
                        steerToward(p, ownGoalX + attackDir * 9f, ballPos.z * 0.45f)
                    }
                }
            }

            PlayerRole.STRIKER -> {
                if (owner != null && owner.team == p.team) {
                    // Attacking run into opposite lane
                    val runX = (owner.pos.x + attackDir * 7.5f).coerceIn(-21f, 21f)
                    val runZ = if (owner.pos.z >= 0f) -6.2f else 6.2f
                    steerToward(p, runX, runZ)
                } else {
                    // Press ball / carrier
                    val targetX = ballPos.x + ballVel.x * 0.25f
                    val targetZ = ballPos.z + ballVel.z * 0.25f
                    steerToward(p, targetX, targetZ)
                    if (owner != null && owner.team != p.team && owner.pos.distanceXZ(p.pos) < 2.8f && difficulty.aiSpeedMul >= 1.0f) {
                        tryTackle(p)
                    }
                }
            }
        }

        // Anti-clustering separation from teammates
        for (other in players) {
            if (other.id != p.id && other.team == p.team) {
                val dx = p.pos.x - other.pos.x
                val dz = p.pos.z - other.pos.z
                val dist = sqrt(dx * dx + dz * dz)
                if (dist in 0.01f..2.6f) {
                    p.aiDesiredDirX += (dx / dist) * 0.65f
                    p.aiDesiredDirZ += (dz / dist) * 0.65f
                }
            }
        }
        val len = sqrt(p.aiDesiredDirX * p.aiDesiredDirX + p.aiDesiredDirZ * p.aiDesiredDirZ)
        if (len > 1f) {
            p.aiDesiredDirX /= len
            p.aiDesiredDirZ /= len
        }
    }

    private fun steerToward(p: PlayerEntity, tx: Float, tz: Float) {
        val dx = tx - p.pos.x
        val dz = tz - p.pos.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.45f) {
            p.aiDesiredDirX = 0f
            p.aiDesiredDirZ = 0f
        } else {
            p.aiDesiredDirX = dx / dist
            p.aiDesiredDirZ = dz / dist
        }
    }

    private fun updatePlayerTimers(p: PlayerEntity, dt: Float) {
        if (p.boostTimer > 0f) p.boostTimer = max(0f, p.boostTimer - dt)
        if (p.tackleTimer > 0f) p.tackleTimer = max(0f, p.tackleTimer - dt)
        if (p.tackleCooldown > 0f) p.tackleCooldown = max(0f, p.tackleCooldown - dt)
        if (p.stunTimer > 0f) p.stunTimer = max(0f, p.stunTimer - dt)
        if (p.boostTimer <= 0f) {
            p.energy = min(100f, p.energy + dt * 16f)
        }
    }

    private fun applySkatingPhysics(p: PlayerEntity, inputX: Float, inputZ: Float, dt: Float) {
        if (p.stunTimer > 0f) {
            p.vel.x *= (1f - dt * 5f)
            p.vel.z *= (1f - dt * 5f)
        } else if (p.tackleTimer <= 0f) {
            val maxSpd = if (p.boostTimer > 0f) 19.5f else 13.2f
            val accel = if (p.boostTimer > 0f) 42f else 30f
            val inputMag = sqrt(inputX * inputX + inputZ * inputZ)
            if (inputMag > 0.05f) {
                p.vel.x += inputX * accel * dt
                p.vel.z += inputZ * accel * dt
                val targetAngle = atan2(inputZ, inputX)
                val prevFacing = p.facingAngle
                p.facingAngle = Vec3.lerpAngle(p.facingAngle, targetAngle, min(1f, dt * 10f))
                val turnDelta = ((p.facingAngle - prevFacing + 3f * PI.toFloat()) % (2f * PI.toFloat())) - PI.toFloat()
                p.leanAngle = Vec3.lerp(p.leanAngle, (turnDelta * 8f).coerceIn(-0.42f, 0.42f), dt * 12f)
            } else {
                p.leanAngle = Vec3.lerp(p.leanAngle, 0f, dt * 8f)
            }

            // Ice/neon-deck skating drag
            val drag = if (inputMag > 0.05f) 2.1f else 3.2f
            p.vel.x -= p.vel.x * drag * dt
            p.vel.z -= p.vel.z * drag * dt

            val spd = p.vel.lengthXZ()
            if (spd > maxSpd) {
                val scale = maxSpd / spd
                p.vel.x *= scale
                p.vel.z *= scale
            }
        }

        p.pos.x += p.vel.x * dt
        p.pos.z += p.vel.z * dt

        val spd = p.vel.lengthXZ()
        if (spd > 0.6f) {
            p.stridePhase += spd * dt * 0.9f
        }

        constrainEntityToRink(p.pos, p.vel, SKATER_RADIUS, restitution = 0.35f)
    }

    private fun resolvePlayerCollisions() {
        for (i in 0 until players.size) {
            for (j in i + 1 until players.size) {
                val a = players[i]
                val b = players[j]
                val dx = b.pos.x - a.pos.x
                val dz = b.pos.z - a.pos.z
                val dist = sqrt(dx * dx + dz * dz)
                val minDist = SKATER_RADIUS * 2f
                if (dist in 0.001f..minDist) {
                    val nx = dx / dist
                    val nz = dz / dist
                    val overlap = (minDist - dist) * 0.5f
                    a.pos.x -= nx * overlap
                    a.pos.z -= nz * overlap
                    b.pos.x += nx * overlap
                    b.pos.z += nz * overlap

                    // Tackle steal check
                    if (a.team != b.team) {
                        if (a.tackleTimer > 0f && ownerId == b.id) {
                            stealBall(a, b)
                        } else if (b.tackleTimer > 0f && ownerId == a.id) {
                            stealBall(b, a)
                        }
                    }

                    // Elastic bump
                    val relVx = b.vel.x - a.vel.x
                    val relVz = b.vel.z - a.vel.z
                    val sepVel = relVx * nx + relVz * nz
                    if (sepVel < 0f) {
                        val impulse = -sepVel * 0.65f
                        a.vel.x -= nx * impulse
                        a.vel.z -= nz * impulse
                        b.vel.x += nx * impulse
                        b.vel.z += nz * impulse
                    }
                }
            }
        }
    }

    private fun stealBall(tackler: PlayerEntity, victim: PlayerEntity) {
        ownerId = tackler.id
        victim.stunTimer = 0.55f
        pickupLockTimer = 0.25f
        lastTouchTeam = tackler.team
        if (tackler.team == 0) {
            prevCyanTouchId = lastCyanTouchId
            lastCyanTouchId = tackler.id
        }
        cameraShake = 0.35f
        onSound(SoundEffect.TACKLE)
        onHaptic(false)
        spawnBurst(victim.pos.x, 0.7f, victim.pos.z, 16, 1f, 0.5f, 0.1f, 7f)
        if (tackler.isUserControlled && mode == GameMode.TRAINING && trainingStep == 4) {
            trainingStep = 5
            trainingCompleted = true
            onSound(SoundEffect.UNLOCK)
        }
    }

    private fun executePass(passer: PlayerEntity) {
        val mates = players.filter { it.team == passer.team && it.id != passer.id }
        if (mates.isEmpty()) {
            executeShot(passer, 0.2f)
            return
        }

        // Choose teammate aligned with input direction if user, or best open forward teammate
        val aimX = if (passer.isUserControlled && abs(joyX) + abs(joyY) > 0.2f) joyX else cos(passer.facingAngle)
        val aimZ = if (passer.isUserControlled && abs(joyX) + abs(joyY) > 0.2f) joyY else sin(passer.facingAngle)
        val aimLen = sqrt(aimX * aimX + aimZ * aimZ).coerceAtLeast(0.01f)
        val nAimX = aimX / aimLen
        val nAimZ = aimZ / aimLen

        val target = mates.maxByOrNull { mate ->
            val dx = mate.pos.x - passer.pos.x
            val dz = mate.pos.z - passer.pos.z
            val d = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
            val dot = (dx / d) * nAimX + (dz / d) * nAimZ
            val forwardBonus = if (mate.role == PlayerRole.STRIKER) 0.25f else 0f
            dot + forwardBonus - d * 0.015f
        } ?: mates.first()

        // Lead target slightly
        val leadX = target.pos.x + target.vel.x * 0.35f
        val leadZ = target.pos.z + target.vel.z * 0.35f
        val dx = leadX - ballPos.x
        val dz = leadZ - ballPos.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
        val speed = (18f + dist * 0.55f).coerceIn(18f, 27f)

        ownerId = -1
        pickupLockTimer = 0.22f
        ballVel.set((dx / dist) * speed, 2.2f, (dz / dist) * speed)
        passer.facingAngle = atan2(dz, dx)
        onSound(SoundEffect.PASS)
        spawnBurst(ballPos.x, ballPos.y, ballPos.z, 8, 0f, 0.95f, 1f, 4f)

        if (passer.isUserControlled && mode == GameMode.TRAINING && trainingStep == 2) {
            trainingStep = 3
            onSound(SoundEffect.UNLOCK)
        }
    }

    private fun executeShot(shooter: PlayerEntity, charge: Float) {
        val targetGoalX = if (shooter.team == 0) RINK_HALF_LEN else -RINK_HALF_LEN
        val aimBiasZ = if (shooter.isUserControlled) joyY * 2.5f else (Random.nextFloat() - 0.5f) * 4.4f
        val targetZ = aimBiasZ.coerceIn(-GOAL_HALF_WIDTH + 0.6f, GOAL_HALF_WIDTH - 0.6f)

        val dx = targetGoalX - ballPos.x
        val dz = targetZ - ballPos.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)

        val isCharged = charge >= 0.35f
        val speed = 26f + charge * 18f
        val loft = 3.2f + charge * 4.4f

        ownerId = -1
        pickupLockTimer = 0.24f
        ballVel.set((dx / dist) * speed, loft, (dz / dist) * speed)
        ballSpin = if (isCharged) 14f else 6f
        shooter.facingAngle = atan2(dz, dx)

        lastShotSpeedKmh = (speed * 3.4f).toInt()

        if (isCharged) {
            cameraShake = 0.45f
            onSound(SoundEffect.SHOOT_CHARGED)
            onHaptic(true)
            spawnBurst(ballPos.x, ballPos.y, ballPos.z, 22, 1f, 0.45f, 0.1f, 9.5f)
        } else {
            cameraShake = 0.18f
            onSound(SoundEffect.SHOOT_NORMAL)
            spawnBurst(ballPos.x, ballPos.y, ballPos.z, 10, 0f, 0.95f, 1f, 5.5f)
        }
    }

    private fun stepBallPhysics(dt: Float, allowGoalTrigger: Boolean) {
        val owner = players.firstOrNull { it.id == ownerId }
        if (owner != null) {
            // Dribble smoothly in front of owner's skates
            val leadDist = 1.15f
            val targetX = owner.pos.x + cos(owner.facingAngle) * leadDist
            val targetZ = owner.pos.z + sin(owner.facingAngle) * leadDist
            ballPos.x = Vec3.lerp(ballPos.x, targetX, min(1f, dt * 22f))
            ballPos.z = Vec3.lerp(ballPos.z, targetZ, min(1f, dt * 22f))
            ballPos.y = BALL_RADIUS + abs(sin(simTime * 8f)) * 0.08f
            ballVel.set(owner.vel)
        } else {
            // Free flight physics
            ballVel.y -= 18.5f * dt // Gravity
            ballPos.x += ballVel.x * dt
            ballPos.y += ballVel.y * dt
            ballPos.z += ballVel.z * dt

            // Floor bounce
            if (ballPos.y < BALL_RADIUS) {
                ballPos.y = BALL_RADIUS
                if (ballVel.y < -1.8f) {
                    ballVel.y = -ballVel.y * 0.72f
                    if (bounceSoundCooldown <= 0f) {
                        onSound(SoundEffect.BALL_BOUNCE)
                        bounceSoundCooldown = 0.14f
                    }
                } else {
                    ballVel.y = 0f
                }
                // Ice friction
                ballVel.x -= ballVel.x * 1.15f * dt
                ballVel.z -= ballVel.z * 1.15f * dt
            } else {
                // Air drag
                ballVel.x -= ballVel.x * 0.28f * dt
                ballVel.z -= ballVel.z * 0.28f * dt
            }

            if (allowGoalTrigger) {
                // Check goal entry before wall bounce!
                if (abs(ballPos.z) < GOAL_HALF_WIDTH - BALL_RADIUS * 0.4f && ballPos.y < GOAL_HEIGHT) {
                    if (ballPos.x > RINK_HALF_LEN + BALL_RADIUS * 0.4f) {
                        onGoalScored(scoringTeam = 0)
                        return
                    } else if (ballPos.x < -RINK_HALF_LEN - BALL_RADIUS * 0.4f) {
                        onGoalScored(scoringTeam = 1)
                        return
                    }
                }

                // Check goalkeeper save / skater pickup or deflection
                for (p in players) {
                    if (p.stunTimer > 0f) continue
                    val dx = ballPos.x - p.pos.x
                    val dz = ballPos.z - p.pos.z
                    val distXZ = sqrt(dx * dx + dz * dz)
                    val reach = if (p.role == PlayerRole.GOALKEEPER) 1.55f else 1.28f
                    if (distXZ < reach && ballPos.y < 2.25f) {
                        val ballSpeed = ballVel.length()
                        if (p.role == PlayerRole.GOALKEEPER && ballSpeed > 15f && ((p.team == 0 && ballVel.x < -4f) || (p.team == 1 && ballVel.x > 4f))) {
                            // Goalkeeper save rebound!
                            val nx = if (p.team == 0) 1f else -1f
                            ballVel.x = nx * (ballSpeed * 0.48f).coerceAtLeast(8f)
                            ballVel.z = (dz / max(0.1f, distXZ)) * 10f
                            ballVel.y = 4.0f
                            pickupLockTimer = 0.28f
                            onSound(SoundEffect.SAVE)
                            spawnBurst(ballPos.x, ballPos.y, ballPos.z, 18, 0.7f, 0.35f, 1f, 7f)
                            if (p.team == 0) userSaves++
                            goalBannerText = "MEGA SAVE!"
                        } else if (pickupLockTimer <= 0f) {
                            // Pick up ball
                            ownerId = p.id
                            lastTouchTeam = p.team
                            if (p.team == 0) {
                                if (lastCyanTouchId != p.id) {
                                    prevCyanTouchId = lastCyanTouchId
                                    lastCyanTouchId = p.id
                                }
                            }
                            break
                        }
                    }
                }
            }

            // Rink walls & goal net bounds
            if (allowGoalTrigger) {
                constrainBallToRink()
            } else {
                // Inside goal net during replay
                ballPos.x = ballPos.x.coerceIn(-RINK_HALF_LEN - GOAL_DEPTH + BALL_RADIUS, RINK_HALF_LEN + GOAL_DEPTH - BALL_RADIUS)
                ballPos.z = ballPos.z.coerceIn(-GOAL_HALF_WIDTH + BALL_RADIUS, GOAL_HALF_WIDTH - BALL_RADIUS)
                ballVel.x *= 0.92f
                ballVel.z *= 0.92f
            }
        }

        // Record ball trail
        ballTrail[trailHead].set(ballPos)
        trailHead = (trailHead + 1) % trailCapacity
    }

    private fun constrainBallToRink() {
        val inGoalZ = abs(ballPos.z) < GOAL_HALF_WIDTH - BALL_RADIUS * 0.3f && ballPos.y < GOAL_HEIGHT
        if (!inGoalZ) {
            val bounced = constrainEntityToRink(ballPos, ballVel, BALL_RADIUS, restitution = 0.78f)
            if (bounced && bounceSoundCooldown <= 0f) {
                onSound(SoundEffect.BALL_BOUNCE)
                bounceSoundCooldown = 0.14f
            }
        }
    }

    private fun constrainEntityToRink(pos: Vec3, vel: Vec3, radius: Float, restitution: Float): Boolean {
        var bounced = false
        val innerX = RINK_HALF_LEN - CORNER_RADIUS
        val innerZ = RINK_HALF_WID - CORNER_RADIUS

        if (abs(pos.x) > innerX && abs(pos.z) > innerZ) {
            // Rounded corner collision
            val cx = if (pos.x > 0f) innerX else -innerX
            val cz = if (pos.z > 0f) innerZ else -innerZ
            val dx = pos.x - cx
            val dz = pos.z - cz
            val dist = sqrt(dx * dx + dz * dz)
            val maxR = CORNER_RADIUS - radius
            if (dist > maxR && dist > 0.001f) {
                val nx = dx / dist
                val nz = dz / dist
                pos.x = cx + nx * maxR
                pos.z = cz + nz * maxR
                val dot = vel.x * nx + vel.z * nz
                if (dot > 0f) {
                    vel.x -= (1f + restitution) * dot * nx
                    vel.z -= (1f + restitution) * dot * nz
                    bounced = true
                }
            }
        } else {
            val maxX = RINK_HALF_LEN - radius
            val maxZ = RINK_HALF_WID - radius
            if (pos.x > maxX) {
                pos.x = maxX
                if (vel.x > 0f) { vel.x = -vel.x * restitution; bounced = true }
            } else if (pos.x < -maxX) {
                pos.x = -maxX
                if (vel.x < 0f) { vel.x = -vel.x * restitution; bounced = true }
            }
            if (pos.z > maxZ) {
                pos.z = maxZ
                if (vel.z > 0f) { vel.z = -vel.z * restitution; bounced = true }
            } else if (pos.z < -maxZ) {
                pos.z = -maxZ
                if (vel.z < 0f) { vel.z = -vel.z * restitution; bounced = true }
            }
        }
        return bounced
    }

    private fun onGoalScored(scoringTeam: Int) {
        lastScoringTeam = scoringTeam
        if (scoringTeam == 0) {
            cyanScore++
            if (lastCyanTouchId == 0) userGoals++ else if (prevCyanTouchId == 0) userAssists++
            goalBannerText = if (isOvertime) "GOLDEN GOAL! VICTORY!" else "CYAN OVERDRIVE GOAL!"
        } else {
            violetScore++
            goalBannerText = if (isOvertime) "OVERTIME GOAL - VIOLET" else "VIOLET STRIKE GOAL!"
        }

        cameraShake = 0.85f
        onSound(SoundEffect.GOAL_HORN)
        onHaptic(true)

        val r = if (scoringTeam == 0) 0f else 0.72f
        val g = if (scoringTeam == 0) 0.94f else 0.32f
        val b = 1f
        spawnBurst(ballPos.x, 1.4f, ballPos.z, 65, r, g, b, 15f)
        spawnBurst(ballPos.x, 1.4f, ballPos.z, 25, 1f, 0.45f, 0.1f, 11f)

        if (mode == GameMode.PRACTICE) {
            // Instant reset for continuous practice
            resetPositionsForKickoff()
            return
        }

        if (mode == GameMode.TRAINING) {
            if (trainingStep == 3 && scoringTeam == 0) {
                trainingStep = 4
                onSound(SoundEffect.UNLOCK)
            }
            resetPositionsForKickoff()
            return
        }

        if (mode == GameMode.TIME_CHALLENGE) {
            resetPositionsForKickoff()
            return
        }

        phase = MatchPhase.GOAL_REPLAY
        goalReplayTimer = 2.5f
        goalReplayAngle = 0f
    }

    private fun stepTrainingDrillChecks() {
        if (mode != GameMode.TRAINING) return
        val user = players.firstOrNull { it.isUserControlled } ?: return
        if (trainingStep == 0) {
            if (user.pos.distanceXZ(trainingGatePos) < 2.2f) {
                trainingGatesCleared++
                onSound(SoundEffect.PASS)
                spawnBurst(trainingGatePos.x, 1f, trainingGatePos.z, 18, 0f, 1f, 0.9f, 6f)
                if (trainingGatesCleared >= 3) {
                    trainingStep = 1
                    onSound(SoundEffect.UNLOCK)
                } else {
                    trainingGatePos.set(
                        if (trainingGatesCleared == 1) -6f else 0f,
                        0f,
                        if (trainingGatesCleared == 1) 6.5f else -6.5f
                    )
                }
            }
        }
    }

    private fun spawnBurst(x: Float, y: Float, z: Float, count: Int, r: Float, g: Float, b: Float, speed: Float) {
        for (i in 0 until count) {
            val p = particles[particleCursor]
            particleCursor = (particleCursor + 1) % particles.size
            val theta = Random.nextFloat() * 2f * PI.toFloat()
            val phi = Random.nextFloat() * PI.toFloat() * 0.65f
            val spd = speed * (0.45f + Random.nextFloat() * 0.75f)
            p.active = true
            p.pos.set(x, y, z)
            p.vel.set(cos(theta) * cos(phi) * spd, sin(phi) * spd + 1.5f, sin(theta) * cos(phi) * spd)
            p.r = r; p.g = g; p.b = b
            p.maxLife = 0.45f + Random.nextFloat() * 0.55f
            p.life = p.maxLife
            p.size = 0.16f + Random.nextFloat() * 0.22f
        }
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
                continue
            }
            p.vel.y -= 12f * dt
            p.pos.x += p.vel.x * dt
            p.pos.y = max(0.05f, p.pos.y + p.vel.y * dt)
            p.pos.z += p.vel.z * dt
        }
    }

    private fun updateCamera(dt: Float) {
        if (phase == MatchPhase.GOAL_REPLAY) {
            val radius = 11.5f
            val desiredX = ballPos.x + cos(goalReplayAngle) * radius
            val desiredZ = ballPos.z + sin(goalReplayAngle) * radius
            cameraPos.x = Vec3.lerp(cameraPos.x, desiredX.coerceIn(-28f, 28f), dt * 4.5f)
            cameraPos.y = Vec3.lerp(cameraPos.y, 6.2f, dt * 4.5f)
            cameraPos.z = Vec3.lerp(cameraPos.z, desiredZ.coerceIn(-18f, 18f), dt * 4.5f)
            cameraTarget.x = Vec3.lerp(cameraTarget.x, ballPos.x, dt * 6f)
            cameraTarget.y = Vec3.lerp(cameraTarget.y, 1.2f, dt * 6f)
            cameraTarget.z = Vec3.lerp(cameraTarget.z, ballPos.z, dt * 6f)
            return
        }

        val user = players.firstOrNull { it.isUserControlled }
        val focusX = if (user != null) Vec3.lerp(ballPos.x, user.pos.x, 0.42f) else ballPos.x
        val focusZ = if (user != null) Vec3.lerp(ballPos.z, user.pos.z, 0.42f) else ballPos.z

        // Elevated isometric / broadcast third-person sports angle
        val desiredCamX = (focusX * 0.78f).coerceIn(-18f, 18f)
        val desiredCamY = 18.5f
        val desiredCamZ = (focusZ * 0.55f + 22.5f).coerceIn(14f, 31f)

        val shakeX = if (cameraShake > 0.01f) (Random.nextFloat() - 0.5f) * cameraShake * 0.9f else 0f
        val shakeY = if (cameraShake > 0.01f) (Random.nextFloat() - 0.5f) * cameraShake * 0.7f else 0f

        cameraPos.x = Vec3.lerp(cameraPos.x, desiredCamX + shakeX, min(1f, dt * 5.2f))
        cameraPos.y = Vec3.lerp(cameraPos.y, desiredCamY + shakeY, min(1f, dt * 5.2f))
        cameraPos.z = Vec3.lerp(cameraPos.z, desiredCamZ, min(1f, dt * 5.2f))

        cameraTarget.x = Vec3.lerp(cameraTarget.x, focusX * 0.85f, min(1f, dt * 6.5f))
        cameraTarget.y = 0.8f
        cameraTarget.z = Vec3.lerp(cameraTarget.z, focusZ * 0.65f, min(1f, dt * 6.5f))
    }

    fun snapshotHud(): MatchHudSnapshot {
        val user = players.firstOrNull { it.isUserControlled }
        val owner = players.firstOrNull { it.id == ownerId }
        val carrierLabel = when {
            owner == null -> "LOOSE ORB"
            owner.isUserControlled -> "YOU (${owner.role.label.uppercase()})"
            owner.team == 0 -> "ALLY ${owner.role.label.uppercase()}"
            else -> "RIVAL ${owner.role.label.uppercase()}"
        }
        val trainingText = when (trainingStep) {
            0 -> "DRILL 1/5: Skate through glowing Cyan Gate (${trainingGatesCleared}/3)"
            1 -> "DRILL 2/5: Tap BOOST to engage thruster sprint"
            2 -> "DRILL 3/5: Pick up ball & tap PASS to your teammate"
            3 -> "DRILL 4/5: Hold SHOOT and release a Charged Goal!"
            4 -> "DRILL 5/5: Tap TACKLE near the Violet Defender to steal!"
            else -> "TRAINING COMPLETE! Free skate or exit anytime."
        }
        return MatchHudSnapshot(
            phase = phase,
            cyanScore = cyanScore,
            violetScore = violetScore,
            timeRemainingSec = matchTimerSec,
            countdownSec = (countdownTimer.toInt() + 1).coerceIn(1, 3),
            energy = user?.energy ?: 100f,
            chargeProgress = if (isChargingShot && ownerId == user?.id) (shotChargeTime / 0.85f).coerceIn(0f, 1f) else 0f,
            tackleCooldownFrac = ((user?.tackleCooldown ?: 0f) / 1.25f).coerceIn(0f, 1f),
            userHasBall = ownerId == user?.id,
            carrierLabel = carrierLabel,
            carrierTeam = owner?.team ?: -1,
            goalBannerText = goalBannerText,
            bannerColorTeam = lastScoringTeam,
            lastShotSpeedKmh = lastShotSpeedKmh,
            userGoals = userGoals,
            userAssists = userAssists,
            userSaves = userSaves,
            isOvertime = isOvertime,
            trainingStep = trainingStep,
            trainingInstruction = trainingText,
            trainingCompleted = trainingCompleted
        )
    }
}
