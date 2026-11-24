package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudProgressRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) {
            "User must be signed in with Google before accessing Firestore."
        }
        return uid
    }

    suspend fun ensurePlayerInitialized(): Result<Unit> {
        var currentPath = "users"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            currentPath = userRef.path
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                val rawName = auth.currentUser?.displayName?.trim().orEmpty()
                val safeName = rawName.ifBlank { "Street Racer" }.take(50)
                val profilePayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "credits" to 15000L,
                    "xp" to 0L,
                    "level" to 1L,
                    "racesCompleted" to 0L,
                    "racesWon" to 0L,
                    "unlockedAchievements" to emptyList<String>(),
                    "selectedCarId" to "vipera_gtx",
                    "totalDistanceKm" to 0.0,
                    "highestDriftCombo" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(profilePayload).await()
            }

            val starterCarRef = userRef.collection("garageCars").document("vipera_gtx")
            currentPath = starterCarRef.path
            val starterSnap = starterCarRef.get().await()
            if (!starterSnap.exists()) {
                val starterSpec = GameCatalog.carById("vipera_gtx")
                val carPayload = mapOf(
                    "userId" to uid,
                    "carId" to starterSpec.id,
                    "paintHex" to starterSpec.defaultPaintHex,
                    "rimStyle" to RimStyle.CHROME.name,
                    "underglowHex" to starterSpec.defaultUnderglowHex,
                    "spoilerStyle" to SpoilerStyle.STOCK.name,
                    "bodyKitStyle" to BodyKitStyle.STREET.name,
                    "windowTint" to WindowTint.SMOKE.name,
                    "headlightStyle" to HeadlightStyle.XENON_WHITE.name,
                    "engineLevel" to 1L,
                    "turboLevel" to 1L,
                    "nitroLevel" to 1L,
                    "handlingLevel" to 1L,
                    "tiresLevel" to 1L,
                    "suspensionLevel" to 1L,
                    "brakesLevel" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                starterCarRef.set(carPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    fun observeProfile(uid: String): Flow<PlayerProfileDoc?> = flow {
        require(uid.isNotBlank()) { "UID must not be blank" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        PlayerProfileDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeGarageCars(uid: String): Flow<List<GarageCarDoc>> = flow {
        require(uid.isNotBlank()) { "UID must not be blank" }
        val path = "users/$uid/garageCars"
        emitAll(
            db.collection("users").document(uid).collection("garageCars")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GarageCarDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCareerProgress(uid: String): Flow<List<CareerProgressDoc>> = flow {
        require(uid.isNotBlank()) { "UID must not be blank" }
        val path = "users/$uid/careerProgress"
        emitAll(
            db.collection("users").document(uid).collection("careerProgress")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CareerProgressDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun selectCar(carId: String): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            ref.update(
                mapOf(
                    "selectedCarId" to carId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unlockAndSelectCar(
        spec: CarSpec,
        newCredits: Long,
        newXp: Long,
        newLevel: Int,
        unlockedAchievements: List<String>
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(newCredits >= 0L) { "Credits cannot be negative" }
            val userRef = db.collection("users").document(uid)
            val carRef = userRef.collection("garageCars").document(spec.id)
            docPath = carRef.path

            val batch = db.batch()
            batch.set(
                carRef,
                mapOf(
                    "userId" to uid,
                    "carId" to spec.id,
                    "paintHex" to spec.defaultPaintHex,
                    "rimStyle" to RimStyle.CHROME.name,
                    "underglowHex" to spec.defaultUnderglowHex,
                    "spoilerStyle" to SpoilerStyle.STOCK.name,
                    "bodyKitStyle" to BodyKitStyle.STREET.name,
                    "windowTint" to WindowTint.SMOKE.name,
                    "headlightStyle" to HeadlightStyle.XENON_WHITE.name,
                    "engineLevel" to 1L,
                    "turboLevel" to 1L,
                    "nitroLevel" to 1L,
                    "handlingLevel" to 1L,
                    "tiresLevel" to 1L,
                    "suspensionLevel" to 1L,
                    "brakesLevel" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "credits" to newCredits.coerceIn(0L, 100_000_000L),
                    "xp" to newXp.coerceIn(0L, 100_000_000L),
                    "level" to newLevel.coerceIn(1, 50).toLong(),
                    "unlockedAchievements" to unlockedAchievements.take(32),
                    "selectedCarId" to spec.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCarVisuals(
        carId: String,
        paintHex: String,
        rimStyle: RimStyle,
        underglowHex: String,
        spoilerStyle: SpoilerStyle,
        bodyKitStyle: BodyKitStyle,
        windowTint: WindowTint,
        headlightStyle: HeadlightStyle,
        updatedProfileRewards: PlayerProfileDoc? = null
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            val carRef = userRef.collection("garageCars").document(carId)
            docPath = carRef.path

            val batch = db.batch()
            batch.update(
                carRef,
                mapOf(
                    "paintHex" to paintHex,
                    "rimStyle" to rimStyle.name,
                    "underglowHex" to underglowHex,
                    "spoilerStyle" to spoilerStyle.name,
                    "bodyKitStyle" to bodyKitStyle.name,
                    "windowTint" to windowTint.name,
                    "headlightStyle" to headlightStyle.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (updatedProfileRewards != null) {
                batch.update(
                    userRef,
                    mapOf(
                        "credits" to updatedProfileRewards.credits.coerceIn(0L, 100_000_000L),
                        "xp" to updatedProfileRewards.xp.coerceIn(0L, 100_000_000L),
                        "level" to updatedProfileRewards.level.coerceIn(1L, 50L),
                        "unlockedAchievements" to updatedProfileRewards.unlockedAchievements.take(32),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upgradeCar(
        carId: String,
        engineLevel: Int,
        turboLevel: Int,
        nitroLevel: Int,
        tiresLevel: Int,
        suspensionLevel: Int,
        brakesLevel: Int,
        updatedProfile: PlayerProfileDoc
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            require(updatedProfile.credits >= 0L) { "Credits cannot be negative" }
            val userRef = db.collection("users").document(uid)
            val carRef = userRef.collection("garageCars").document(carId)
            docPath = carRef.path

            val clampedTires = tiresLevel.coerceIn(1, 5)
            val clampedSusp = suspensionLevel.coerceIn(1, 5)
            val compatHandling = maxOf(clampedTires, clampedSusp)

            val batch = db.batch()
            batch.update(
                carRef,
                mapOf(
                    "engineLevel" to engineLevel.coerceIn(1, 5).toLong(),
                    "turboLevel" to turboLevel.coerceIn(1, 5).toLong(),
                    "nitroLevel" to nitroLevel.coerceIn(1, 5).toLong(),
                    "handlingLevel" to compatHandling.toLong(),
                    "tiresLevel" to clampedTires.toLong(),
                    "suspensionLevel" to clampedSusp.toLong(),
                    "brakesLevel" to brakesLevel.coerceIn(1, 5).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "credits" to updatedProfile.credits.coerceIn(0L, 100_000_000L),
                    "xp" to updatedProfile.xp.coerceIn(0L, 100_000_000L),
                    "level" to updatedProfile.level.coerceIn(1L, 50L),
                    "unlockedAchievements" to updatedProfile.unlockedAchievements.take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordSessionResult(
        result: RaceResult,
        updatedProfile: PlayerProfileDoc,
        existingProgress: CareerProgressDoc?
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            docPath = userRef.path

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "credits" to updatedProfile.credits.coerceIn(0L, 100_000_000L),
                    "xp" to updatedProfile.xp.coerceIn(0L, 100_000_000L),
                    "level" to updatedProfile.level.coerceIn(1L, 50L),
                    "racesCompleted" to updatedProfile.racesCompleted.coerceIn(0L, 1_000_000L),
                    "racesWon" to updatedProfile.racesWon.coerceIn(0L, 1_000_000L),
                    "unlockedAchievements" to updatedProfile.unlockedAchievements.take(32),
                    "totalDistanceKm" to updatedProfile.totalDistanceKm.coerceIn(0.0, 1_000_000.0),
                    "highestDriftCombo" to updatedProfile.highestDriftCombo.coerceIn(0L, 10_000_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val eventId = result.eventId
            if (!eventId.isNullOrBlank()) {
                val eventRef = userRef.collection("careerProgress").document(eventId)
                docPath = eventRef.path
                val prevMedal = MedalTier.fromRaw(existingProgress?.medalTier ?: "NONE")
                val bestMedal = if (result.medal.rank >= prevMedal.rank) result.medal else prevMedal
                val bestTime = when {
                    result.mode == GameMode.DRIFT_CHALLENGE -> existingProgress?.bestTimeMs ?: 0L
                    existingProgress == null || existingProgress.bestTimeMs == 0L -> result.elapsedMs
                    else -> minOf(existingProgress.bestTimeMs, result.elapsedMs)
                }.coerceIn(0L, 3_600_000L)
                val bestDrift = maxOf(existingProgress?.bestDriftScore ?: 0L, result.driftScore)
                    .coerceIn(0L, 10_000_000L)
                val nextCount = ((existingProgress?.completedCount ?: 0L) + 1L).coerceIn(0L, 100_000L)

                if (existingProgress == null) {
                    batch.set(
                        eventRef,
                        mapOf(
                            "userId" to uid,
                            "eventId" to eventId,
                            "bestTimeMs" to bestTime,
                            "bestDriftScore" to bestDrift,
                            "medalTier" to bestMedal.name,
                            "completedCount" to nextCount,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.update(
                        eventRef,
                        mapOf(
                            "bestTimeMs" to bestTime,
                            "bestDriftScore" to bestDrift,
                            "medalTier" to bestMedal.name,
                            "completedCount" to nextCount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
