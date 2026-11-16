package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.CosmeticCatalog
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchRecord
import com.example.data.model.PlayerProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GameRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeProfile(userId: String): Flow<PlayerProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) {
                    null
                } else {
                    snapshot.toObject(
                        PlayerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun ensureProfileExists(userId: String, defaultCallsign: String): PlayerProfile {
        val docRef = db.collection("users").document(userId)
        val sanitizedCallsign = defaultCallsign.trim().replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .take(24)
            .let { if (it.length >= 2) it else "Skater_${userId.take(5)}" }

        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: PlayerProfile(id = userId, userId = userId, callsign = sanitizedCallsign)
                syncLeaderboardEntry(userId, existing)
                existing
            } else {
                val initial = PlayerProfile(
                    id = userId,
                    userId = userId,
                    callsign = sanitizedCallsign,
                    unlockedItems = CosmeticCatalog.defaultUnlocked
                )
                docRef.set(initial.toCreateMap(userId)).await()
                syncLeaderboardEntry(userId, initial)
                initial
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun saveProfile(userId: String, profile: PlayerProfile) {
        val docRef = db.collection("users").document(userId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap(userId)).await()
            }
            syncLeaderboardEntry(userId, profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    private suspend fun syncLeaderboardEntry(userId: String, profile: PlayerProfile) {
        val lbRef = db.collection("leaderboard").document(userId)
        val entry = LeaderboardEntry(
            id = userId,
            userId = userId,
            callsign = profile.callsign,
            rating = profile.competitiveRating(),
            level = profile.level,
            wins = profile.wins,
            goals = profile.goals,
            bestTimeChallenge = profile.bestTimeChallengeGoals,
            selectedOutfit = profile.selectedOutfit,
            isPublic = true
        )
        try {
            val existing = lbRef.get().await()
            if (existing.exists()) {
                lbRef.update(entry.toUpdateMap()).await()
            } else {
                lbRef.set(entry.toCreateMap(userId)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, lbRef.path)
            throw e
        }
    }

    suspend fun recordMatch(userId: String, record: MatchRecord): String {
        val matchId = "m_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(userId).collection("matches").document(matchId)
        return try {
            docRef.set(record.toCreateMap()).await()
            matchId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeMatchHistory(userId: String): Flow<List<MatchRecord>> {
        val path = "users/$userId/matches"
        return db.collection("users").document(userId).collection("matches")
            .limit(30)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    MatchRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeGlobalLeaderboard(): Flow<List<LeaderboardEntry>> {
        val path = "leaderboard"
        return db.collection(path)
            .whereEqualTo("isPublic", true)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    LeaderboardEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<LeaderboardEntry> { it.rating }
                        .thenByDescending { it.wins }
                        .thenByDescending { it.goals }
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                throw error
            }
    }
}
