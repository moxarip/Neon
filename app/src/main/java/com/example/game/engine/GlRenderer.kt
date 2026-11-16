package com.example.game.engine

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.example.data.model.CosmeticCatalog
import com.example.data.model.GameMode
import com.example.data.model.GraphicsQuality
import com.example.data.model.PlayerProfile
import com.example.data.model.PlayerRole
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class GlRenderer(
    private val simulation: MatchSimulation,
    @Volatile var profile: PlayerProfile,
    @Volatile var quality: GraphicsQuality,
    private val onHudSnapshot: (MatchHudSnapshot) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var isPaused: Boolean = false

    private val shader = ShaderProgram()
    private lateinit var boxMesh: Mesh
    private lateinit var sphereMesh: Mesh
    private lateinit var cylMesh: Mesh
    private lateinit var ringMesh: Mesh

    private val proj = Mat4()
    private val view = Mat4()
    private val vp = Mat4()
    private val model = Mat4()
    private val mvp = Mat4()

    private var lastFrameNs = System.nanoTime()
    private var hudFrameCounter = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.024f, 0.031f, 0.078f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        shader.compile()
        boxMesh = MeshBuilder.createBox()
        sphereMesh = MeshBuilder.createSphere(12, 16)
        cylMesh = MeshBuilder.createCylinder(14)
        ringMesh = MeshBuilder.createRing(32, 0.80f)
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        proj.setPerspective(46f, aspect, 0.5f, 140f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        if (!isPaused) {
            simulation.step(dt)
        }

        hudFrameCounter++
        if (hudFrameCounter >= 3) {
            hudFrameCounter = 0
            onHudSnapshot(simulation.snapshotHud())
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        shader.use()

        val cam = simulation.cameraPos
        val tgt = simulation.cameraTarget
        view.setLookAt(cam.x, cam.y, cam.z, tgt.x, tgt.y, tgt.z, 0f, 1f, 0f)
        vp.multiply(proj, view)

        GLES30.glUniform3f(shader.uLightDirLoc, 0.32f, 0.92f, 0.25f)
        GLES30.glUniform3f(shader.uCamPosLoc, cam.x, cam.y, cam.z)

        val arenaTheme = CosmeticCatalog.byId(profile.selectedArena)
        val (ar, ag, ab) = hexToRgb(arenaTheme.primaryHex)
        val (vr, vg, vb) = hexToRgb(arenaTheme.accentHex)

        // 1. Metallic arena floor
        drawSolid(boxMesh, 0f, -0.15f, 0f, 0f, 0f, 0f, 54f, 0.3f, 32f, 0.05f, 0.07f, 0.14f, 0.0f, 1f, 0.45f)

        // Optional floor reflections on HIGH quality
        if (quality.drawReflections) {
            for (p in simulation.players) {
                val pr = if (p.team == 0) 0f else 0.72f
                val pg = if (p.team == 0) 0.85f else 0.30f
                val pb = if (p.team == 0) 1f else 0.96f
                drawSolid(cylMesh, p.pos.x, 0.01f, p.pos.z, 0f, 0f, 0f, 1.3f, 0.02f, 1.3f, pr, pg, pb, 0.4f, 0.22f, 0.1f)
            }
        }

        // 2. Glowing neon rink floor markings
        drawRinkMarkings(ar, ag, ab, vr, vg, vb)

        // 3. Dasher walls, LED ribbon boards, crowd silhouettes
        drawStadiumEnvironment(ar, ag, ab, vr, vg, vb)

        // 4. Goals (Cyan Home at -X, Violet Away at +X)
        drawGoal(-MatchSimulation.RINK_HALF_LEN, 0f, 0.94f, 1f, isRight = false)
        drawGoal(MatchSimulation.RINK_HALF_LEN, 0.68f, 0.33f, 0.97f, isRight = true)

        // 5. Training Gate (when in Training Drill 0)
        if (simulation.mode == GameMode.TRAINING && simulation.trainingStep == 0) {
            val gPos = simulation.trainingGatePos
            val pulse = 1.9f + sin(simulation.simTime * 5f) * 0.15f
            drawSolid(ringMesh, gPos.x, 0.06f, gPos.z, 0f, simulation.simTime * 90f, 0f, pulse, 1f, pulse, 0f, 1f, 0.85f, 0.95f, 0.9f, 0.2f)
            drawSolid(cylMesh, gPos.x, 1.4f, gPos.z, 0f, 0f, 0f, 0.22f, 2.8f, 0.22f, 0f, 1f, 0.85f, 0.9f, 0.45f, 0.2f)
        }

        // 6. Articulated 3D Roller-Skating Players
        for (p in simulation.players) {
            drawSkater(p)
        }

        // 7. Ball Trail + Energy Ball
        drawBallAndTrail()

        // 8. 3D Particles (Goal Explosions, Skate Sparks, Impacts)
        drawParticles()
    }

    private fun drawRinkMarkings(ar: Float, ag: Float, ab: Float, vr: Float, vg: Float, vb: Float) {
        val y = 0.02f
        val hl = MatchSimulation.RINK_HALF_LEN - 0.6f
        val hw = MatchSimulation.RINK_HALF_WID - 0.6f

        // Center line & circle
        drawSolid(boxMesh, 0f, y, 0f, 0f, 0f, 0f, 0.24f, 0.02f, hw * 2f, ar, ag, ab, 0.85f, 0.9f, 0.2f)
        drawSolid(ringMesh, 0f, y, 0f, 0f, 0f, 0f, 4.4f, 1f, 4.4f, ar, ag, ab, 0.85f, 0.9f, 0.2f)
        drawSolid(ringMesh, 0f, y, 0f, 0f, 0f, 0f, 1.3f, 1f, 1.3f, vr, vg, vb, 0.85f, 0.85f, 0.2f)

        // Attack zone lines
        drawSolid(boxMesh, -9.5f, y, 0f, 0f, 0f, 0f, 0.16f, 0.02f, hw * 2f, ar, ag, ab, 0.6f, 0.6f, 0.1f)
        drawSolid(boxMesh,  9.5f, y, 0f, 0f, 0f, 0f, 0.16f, 0.02f, hw * 2f, vr, vg, vb, 0.6f, 0.6f, 0.1f)

        // Goal creases
        drawSolid(ringMesh, -MatchSimulation.RINK_HALF_LEN, y, 0f, 0f, 0f, 0f, 4.2f, 1f, 4.2f, 0f, 0.94f, 1f, 0.8f, 0.7f, 0.2f)
        drawSolid(ringMesh,  MatchSimulation.RINK_HALF_LEN, y, 0f, 0f, 0f, 0f, 4.2f, 1f, 4.2f, 0.68f, 0.33f, 0.97f, 0.8f, 0.7f, 0.2f)

        // Longitudinal lane accents
        drawSolid(boxMesh, 0f, y, -hw, 0f, 0f, 0f, hl * 2f, 0.02f, 0.18f, ar, ag, ab, 0.75f, 0.8f, 0.2f)
        drawSolid(boxMesh, 0f, y,  hw, 0f, 0f, 0f, hl * 2f, 0.02f, 0.18f, ar, ag, ab, 0.75f, 0.8f, 0.2f)
    }

    private fun drawStadiumEnvironment(ar: Float, ag: Float, ab: Float, vr: Float, vg: Float, vb: Float) {
        val hl = MatchSimulation.RINK_HALF_LEN
        val hw = MatchSimulation.RINK_HALF_WID

        // Side dasher walls (translucent energy glass + glowing rail)
        drawSolid(boxMesh, 0f, 0.75f, -hw - 0.15f, 0f, 0f, 0f, hl * 2f - 6f, 1.5f, 0.24f, 0.12f, 0.18f, 0.35f, 0.15f, 0.55f, 0.7f)
        drawSolid(boxMesh, 0f, 1.55f, -hw - 0.15f, 0f, 0f, 0f, hl * 2f - 6f, 0.14f, 0.3f, ar, ag, ab, 0.95f, 1f, 0.2f)

        drawSolid(boxMesh, 0f, 0.75f, hw + 0.15f, 0f, 0f, 0f, hl * 2f - 6f, 1.5f, 0.24f, 0.12f, 0.18f, 0.35f, 0.15f, 0.40f, 0.7f)
        drawSolid(boxMesh, 0f, 1.55f, hw + 0.15f, 0f, 0f, 0f, hl * 2f - 6f, 0.14f, 0.3f, ar, ag, ab, 0.95f, 1f, 0.2f)

        // End walls left & right of goals
        val sideSeg = hw - MatchSimulation.GOAL_HALF_WIDTH - 2.2f
        val sideCenterZ = MatchSimulation.GOAL_HALF_WIDTH + sideSeg * 0.5f
        for (sz in floatArrayOf(-sideCenterZ, sideCenterZ)) {
            drawSolid(boxMesh, -hl - 0.15f, 0.85f, sz, 0f, 0f, 0f, 0.24f, 1.7f, sideSeg, 0.12f, 0.18f, 0.35f, 0.15f, 0.55f, 0.6f)
            drawSolid(boxMesh, -hl - 0.15f, 1.75f, sz, 0f, 0f, 0f, 0.3f, 0.14f, sideSeg, 0f, 0.94f, 1f, 0.9f, 1f, 0.2f)

            drawSolid(boxMesh,  hl + 0.15f, 0.85f, sz, 0f, 0f, 0f, 0.24f, 1.7f, sideSeg, 0.12f, 0.18f, 0.35f, 0.15f, 0.55f, 0.6f)
            drawSolid(boxMesh,  hl + 0.15f, 1.75f, sz, 0f, 0f, 0f, 0.3f, 0.14f, sideSeg, 0.68f, 0.33f, 0.97f, 0.9f, 1f, 0.2f)
        }

        // Corner curved pillars
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                drawSolid(cylMesh, sx * (hl - 1.6f), 0.95f, sz * (hw - 1.6f), 0f, 0f, 0f, 1.1f, 1.9f, 1.1f, vr, vg, vb, 0.55f, 0.7f, 0.6f)
            }
        }

        // Animated LED Stadium Ribbon Boards along back stand (-Z)
        val ledSegments = if (quality == GraphicsQuality.LOW) 6 else 12
        val segWidth = (hl * 2f) / ledSegments
        for (i in 0 until ledSegments) {
            val x = -hl + segWidth * (i + 0.5f)
            val wave = sin(simulation.simTime * 3.5f - i * 0.7f) * 0.5f + 0.5f
            val r = ar * wave + vr * (1f - wave)
            val g = ag * wave + vg * (1f - wave)
            val b = ab * wave + vb * (1f - wave)
            drawSolid(boxMesh, x, 4.2f, -hw - 3.6f, 0f, 0f, 0f, segWidth * 0.88f, 1.1f, 0.3f, r, g, b, 0.92f, 1f, 0.1f)
        }

        // Animated Crowd Silhouettes (Medium / High)
        if (quality.drawCrowd) {
            val crowdCount = if (quality == GraphicsQuality.HIGH) 20 else 10
            val cheerSpeed = if (simulation.phase == MatchPhase.GOAL_REPLAY) 12f else 3.2f
            for (i in 0 until crowdCount) {
                val x = -hl + 2f + i * ((hl * 2f - 4f) / crowdCount)
                val hop = abs(sin(simulation.simTime * cheerSpeed + i * 1.1f)) *
                    (if (simulation.phase == MatchPhase.GOAL_REPLAY) 0.55f else 0.14f)
                val cr = if (i % 2 == 0) ar else vr
                val cg = if (i % 2 == 0) ag else vg
                val cb = if (i % 2 == 0) ab else vb
                drawSolid(cylMesh, x, 2.7f + hop, -hw - 2.3f, 0f, 0f, 0f, 0.55f, 1.1f, 0.55f, 0.08f, 0.10f, 0.18f, 0.05f, 1f, 0.2f)
                drawSolid(sphereMesh, x, 3.4f + hop, -hw - 2.3f, 0f, 0f, 0f, 0.42f, 0.42f, 0.42f, cr, cg, cb, 0.65f, 1f, 0.1f)
            }
        }
    }

    private fun drawGoal(goalX: Float, r: Float, g: Float, b: Float, isRight: Boolean) {
        val hw = MatchSimulation.GOAL_HALF_WIDTH
        val h = MatchSimulation.GOAL_HEIGHT
        val d = MatchSimulation.GOAL_DEPTH
        val dir = if (isRight) 1f else -1f
        val flash = if (simulation.phase == MatchPhase.GOAL_REPLAY) 0.6f + 0.4f * sin(simulation.simTime * 18f) else 0.9f

        // Left & right goalposts
        drawSolid(cylMesh, goalX, h * 0.5f, -hw, 0f, 0f, 0f, 0.24f, h, 0.24f, r, g, b, flash, 1f, 0.5f)
        drawSolid(cylMesh, goalX, h * 0.5f,  hw, 0f, 0f, 0f, 0.24f, h, 0.24f, r, g, b, flash, 1f, 0.5f)
        // Crossbar
        drawSolid(cylMesh, goalX, h, 0f, 0f, 0f, 0f, 0.24f, 0.24f, hw * 2f + 0.24f, r, g, b, flash, 1f, 0.5f)
        // Back energy net box
        drawSolid(boxMesh, goalX + dir * (d * 0.5f), h * 0.5f, 0f, 0f, 0f, 0f, d, h, hw * 2f, r, g, b, 0.35f, 0.22f, 0.1f)
        // Top goal beacon bar
        drawSolid(boxMesh, goalX + dir * 0.3f, h + 0.35f, 0f, 0f, 0f, 0f, 0.5f, 0.25f, 2.2f, 1f, 0.45f, 0.1f, flash, 1f, 0.3f)
    }

    private fun drawSkater(p: PlayerEntity) {
        val outfit = if (p.team == 0) CosmeticCatalog.byId(profile.selectedOutfit) else null
        val (tr, tg, tb) = if (outfit != null) hexToRgb(outfit.primaryHex) else Triple(0.66f, 0.33f, 0.97f)
        val (ar, ag, ab) = if (outfit != null) hexToRgb(outfit.accentHex) else Triple(1.0f, 0.42f, 0.10f)

        val skate = if (p.team == 0) CosmeticCatalog.byId(profile.selectedSkate) else null
        val (sr, sg, sb) = if (skate != null) hexToRgb(skate.primaryHex) else Triple(1.0f, 0.42f, 0.10f)

        val yawDeg = -Math.toDegrees(p.facingAngle.toDouble()).toFloat()
        val leanDeg = Math.toDegrees(p.leanAngle.toDouble()).toFloat()
        val celebJump = if (p.celebrateTimer > 0f) abs(sin(p.celebrateTimer * 7f)) * 0.9f else 0f
        val celebSpin = if (p.celebrateTimer > 0f && profile.selectedCelebration == "celeb_spin") p.celebrateTimer * 520f else 0f
        val baseY = celebJump

        // Active user / ball carrier ground ring
        if (p.isUserControlled) {
            val ringScale = if (simulation.ownerId == p.id) 1.55f else 1.25f
            drawSolid(ringMesh, p.pos.x, 0.04f, p.pos.z, 0f, 0f, 0f, ringScale, 1f, ringScale, 0f, 0.96f, 1f, 0.95f, 0.85f, 0.1f)
        } else if (simulation.ownerId == p.id) {
            drawSolid(ringMesh, p.pos.x, 0.04f, p.pos.z, 0f, 0f, 0f, 1.35f, 1f, 1.35f, 1f, 0.45f, 0.1f, 0.9f, 0.8f, 0.1f)
        }

        // Animated leg stride offset
        val stride = sin(p.stridePhase) * 0.24f
        val fwdX = cos(p.facingAngle)
        val fwdZ = sin(p.facingAngle)
        val rightX = -fwdZ
        val rightZ = fwdX

        // Left & Right Roller Skates (boots + dual glowing wheels)
        for (side in floatArrayOf(-1f, 1f)) {
            val sOff = side * stride
            val sx = p.pos.x + rightX * (side * 0.23f) + fwdX * sOff
            val sz = p.pos.z + rightZ * (side * 0.23f) + fwdZ * sOff
            // Skate chassis
            drawSolid(boxMesh, sx, baseY + 0.18f, sz, 0f, yawDeg + celebSpin, 0f, 0.56f, 0.18f, 0.18f, 0.14f, 0.17f, 0.26f, 0.1f, 1f, 0.7f)
            // Neon wheels
            drawSolid(cylMesh, sx + fwdX * 0.16f, baseY + 0.09f, sz + fwdZ * 0.16f, 90f, yawDeg + celebSpin, 0f, 0.17f, 0.10f, 0.17f, sr, sg, sb, 0.95f, 1f, 0.2f)
            drawSolid(cylMesh, sx - fwdX * 0.16f, baseY + 0.09f, sz - fwdZ * 0.16f, 90f, yawDeg + celebSpin, 0f, 0.17f, 0.10f, 0.17f, sr, sg, sb, 0.95f, 1f, 0.2f)
            // Leg
            drawSolid(cylMesh, sx, baseY + 0.56f, sz, 0f, yawDeg + celebSpin, leanDeg, 0.20f, 0.62f, 0.20f, 0.16f, 0.20f, 0.34f, 0.1f, 1f, 0.4f)
        }

        // Armored Torso
        val torsoScale = if (p.role == PlayerRole.GOALKEEPER) 0.72f else 0.58f
        drawSolid(boxMesh, p.pos.x, baseY + 1.15f, p.pos.z, 0f, yawDeg + celebSpin, leanDeg, 0.46f, 0.72f, torsoScale, tr, tg, tb, 0.45f, 1f, 0.75f)
        // Neon Chest Core / Jersey Stripe
        drawSolid(boxMesh, p.pos.x + fwdX * 0.20f, baseY + 1.18f, p.pos.z + fwdZ * 0.20f, 0f, yawDeg + celebSpin, leanDeg, 0.12f, 0.38f, 0.32f, ar, ag, ab, 0.95f, 1f, 0.2f)

        // Shoulder Pads
        drawSolid(boxMesh, p.pos.x, baseY + 1.44f, p.pos.z, 0f, yawDeg + celebSpin, leanDeg, 0.48f, 0.20f, torsoScale + 0.24f, 0.15f, 0.18f, 0.30f, 0.2f, 1f, 0.8f)

        // Helmet + Glowing Cyber Visor
        drawSolid(sphereMesh, p.pos.x, baseY + 1.78f, p.pos.z, 0f, yawDeg + celebSpin, 0f, 0.46f, 0.46f, 0.46f, 0.92f, 0.95f, 1f, 0.2f, 1f, 0.9f)
        drawSolid(boxMesh, p.pos.x + fwdX * 0.18f, baseY + 1.79f, p.pos.z + fwdZ * 0.18f, 0f, yawDeg + celebSpin, 0f, 0.14f, 0.14f, 0.34f, tr, tg, tb, 0.98f, 1f, 0.2f)

        // Boost jet flame behind skates
        if (p.boostTimer > 0f) {
            drawSolid(sphereMesh, p.pos.x - fwdX * 0.65f, baseY + 0.65f, p.pos.z - fwdZ * 0.65f, 0f, 0f, 0f, 0.45f, 0.35f, 0.45f, 1f, 0.45f, 0.1f, 1f, 0.85f, 0.1f)
        }
    }

    private fun drawBallAndTrail() {
        val ballCosmetic = CosmeticCatalog.byId(profile.selectedBall)
        val (br, bg, bb) = hexToRgb(ballCosmetic.primaryHex)
        val trailCosmetic = CosmeticCatalog.byId(profile.selectedTrail)
        val (tr, tg, tb) = hexToRgb(trailCosmetic.primaryHex)

        // Ball Trail
        val cap = simulation.ballTrail.size
        for (i in 0 until cap) {
            val idx = (simulation.trailHead + i) % cap
            val pt = simulation.ballTrail[idx]
            if (pt.y < -1f) continue
            val frac = (i + 1).toFloat() / cap
            val size = MatchSimulation.BALL_RADIUS * 1.45f * frac
            drawSolid(sphereMesh, pt.x, pt.y, pt.z, 0f, 0f, 0f, size, size, size, tr, tg, tb, 0.85f, frac * 0.45f, 0.1f)
        }

        // Floor shadow / halo under ball
        val b = simulation.ballPos
        drawSolid(ringMesh, b.x, 0.03f, b.z, 0f, 0f, 0f, 0.85f, 1f, 0.85f, br, bg, bb, 0.9f, 0.65f, 0.1f)

        // Glowing Energy Ball + rotating orbital ring
        val d = MatchSimulation.BALL_RADIUS * 2f
        drawSolid(sphereMesh, b.x, b.y, b.z, 0f, simulation.simTime * 180f, 0f, d, d, d, br, bg, bb, 0.98f, 1f, 0.9f)
        drawSolid(ringMesh, b.x, b.y, b.z, 35f, simulation.simTime * 260f, 20f, d * 0.72f, 1f, d * 0.72f, 1f, 1f, 1f, 0.95f, 0.8f, 0.1f)
    }

    private fun drawParticles() {
        val maxDraw = (simulation.particles.size * quality.particleScale).toInt()
        for (i in 0 until maxDraw) {
            val pt = simulation.particles[i]
            if (!pt.active) continue
            val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            drawSolid(boxMesh, pt.pos.x, pt.pos.y, pt.pos.z, 0f, 0f, 0f, pt.size, pt.size, pt.size, pt.r, pt.g, pt.b, 0.98f, alpha, 0.1f)
        }
    }

    private fun drawSolid(
        mesh: Mesh,
        x: Float, y: Float, z: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float,
        emissive: Float, alpha: Float, specular: Float
    ) {
        model.setIdentity()
        model.translate(x, y, z)
        if (ry != 0f) model.rotateY(ry)
        if (rx != 0f) model.rotateX(rx)
        if (rz != 0f) model.rotateZ(rz)
        model.scale(sx, sy, sz)
        mvp.multiply(vp, model)

        GLES30.glUniformMatrix4fv(shader.uMvpLoc, 1, false, mvp.data, 0)
        GLES30.glUniformMatrix4fv(shader.uModelLoc, 1, false, model.data, 0)
        GLES30.glUniform3f(shader.uColorLoc, r, g, b)
        GLES30.glUniform1f(shader.uEmissiveLoc, emissive)
        GLES30.glUniform1f(shader.uAlphaLoc, alpha)
        GLES30.glUniform1f(shader.uSpecularLoc, specular)
        mesh.draw()
    }

    private fun hexToRgb(hex: Long): Triple<Float, Float, Float> {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return Triple(r, g, b)
    }
}
