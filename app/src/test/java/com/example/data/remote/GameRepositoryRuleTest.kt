package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MatchRecord
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GameRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileExists_and_saveProfile_succeedsForOwner() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = GameRepository(firestore)

        val profile = withTimeout(TIMEOUT_MS) { repo.ensureProfileExists(uid, "CyanStriker") }
        assertEquals(uid, profile.userId)
        assertEquals("CyanStriker", profile.callsign)

        val updated = profile.copy(wins = 2L, goals = 5L, credits = 450L)
        withTimeout(TIMEOUT_MS) { repo.saveProfile(uid, updated) }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProfile(uid).first { it != null && it.wins == 2L }
        }
        assertEquals(5L, observed?.goals)

        val leaderboard = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeGlobalLeaderboard().first { list -> list.any { it.userId == uid } }
        }
        assertTrue(leaderboard.any { it.userId == uid && it.wins == 2L })
    }

    @Test
    fun recordMatch_createsImmutableHistory_and_crossUserDenied() = runBlocking {
        val aliceUid = signInTestUser("alice2_${UUID.randomUUID()}@test.com")
        val aliceRepo = GameRepository(firestore)
        withTimeout(TIMEOUT_MS) { aliceRepo.ensureProfileExists(aliceUid, "Alice") }

        val matchId = withTimeout(TIMEOUT_MS) {
            aliceRepo.recordMatch(
                aliceUid,
                MatchRecord(
                    playerScore = 3L,
                    opponentScore = 1L,
                    won = true,
                    goalsScored = 2L,
                    assists = 1L,
                    saves = 1L,
                    xpEarned = 180L,
                    creditsEarned = 120L
                )
            )
        }
        assertTrue(matchId.isNotBlank())

        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = GameRepository(firestore)
        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeProfile(aliceUid).first() }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Cross-user profile read should fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun observeLeaderboard_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = GameRepository(firestore)
        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeGlobalLeaderboard().first() }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Unauthenticated leaderboard query should fail with PERMISSION_DENIED", denied)
    }

    private companion object {
        const val TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
