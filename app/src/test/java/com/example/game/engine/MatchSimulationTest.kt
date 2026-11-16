package com.example.game.engine

import com.example.data.model.Difficulty
import com.example.data.model.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSimulationTest {

    @Test
    fun skatingMovement_and_boost_acceleratesUserSkater() {
        val sim = MatchSimulation(GameMode.PRACTICE, Difficulty.NORMAL, {}, {})
        val user = sim.players.first { it.isUserControlled }
        val startX = user.pos.x

        sim.joyX = 1.0f
        sim.joyY = 0.0f
        repeat(30) { sim.step(1f / 60f) }

        assertTrue("Skater should skate forward along +X", user.pos.x > startX + 1.0f)

        val energyBefore = user.energy
        sim.triggerBoost()
        sim.step(1f / 60f)
        assertTrue("Boost should consume energy", user.energy < energyBefore)
        assertTrue("Boost timer should be active", user.boostTimer > 0f)
    }

    @Test
    fun passing_and_chargedShot_launchesBallTowardGoal() {
        val sim = MatchSimulation(GameMode.QUICK_MATCH, Difficulty.NORMAL, {}, {})
        // Advance past countdown
        repeat(220) { sim.step(1f / 60f) }
        assertEquals(MatchPhase.PLAYING, sim.phase)

        val user = sim.players.first { it.isUserControlled }
        sim.ownerId = user.id
        user.pos.set(10f, 0f, 0f)
        sim.ballPos.set(11f, MatchSimulation.BALL_RADIUS, 0f)

        sim.onShootPressed()
        repeat(45) { sim.step(1f / 60f) } // Hold 0.75s charge
        sim.onShootReleased()
        sim.step(1f / 60f)

        assertEquals(-1, sim.ownerId)
        assertTrue("Charged shot should launch ball with high +X velocity", sim.ballVel.x > 25f)
    }

    @Test
    fun goalDetection_scoresAndTriggersReplay() {
        val sim = MatchSimulation(GameMode.QUICK_MATCH, Difficulty.NORMAL, {}, {})
        repeat(220) { sim.step(1f / 60f) }

        sim.ownerId = -1
        sim.ballPos.set(MatchSimulation.RINK_HALF_LEN + 0.1f, 1.0f, 0f)
        sim.ballVel.set(22f, 0f, 0f)
        sim.step(1f / 60f)

        assertEquals(1, sim.cyanScore)
        assertEquals(MatchPhase.GOAL_REPLAY, sim.phase)
    }

    @Test
    fun tiedAtZeroTime_entersSuddenDeathOvertime() {
        val sim = MatchSimulation(GameMode.QUICK_MATCH, Difficulty.NORMAL, {}, {})
        repeat(220) { sim.step(1f / 60f) }

        sim.matchTimerSec = 0.01f
        sim.step(1f / 30f)

        assertTrue(sim.isOvertime)
        assertEquals(MatchPhase.OVERTIME, sim.phase)
    }
}
