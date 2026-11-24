package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.localProgressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gta_race_x_local_progress"
)

data class LocalProgressSnapshot(
    val profile: PlayerProfileDoc,
    val garageCars: Map<String, GarageCarDoc>,
    val careerProgress: Map<String, CareerProgressDoc>
)

/**
 * Local-first persistent player progress store backed by Jetpack DataStore.
 * Instantly saves & loads Cash, XP, Levels, Achievements, Unlocked Vehicles,
 * Visual Customizations, Mechanical Upgrades, Selected Vehicle, and Career Progress
 * on-device, working seamlessly alongside Cloud Firestore sync.
 */
class LocalProgressRepository(private val context: Context) {

    private fun keyForUser(uid: String): Preferences.Key<String> =
        stringPreferencesKey("progress_json_${uid.take(64)}")

    fun observeSnapshot(uid: String): Flow<LocalProgressSnapshot?> {
        val key = keyForUser(uid)
        return context.localProgressDataStore.data.map { prefs ->
            prefs[key]?.let { decodeSnapshot(uid, it) }
        }
    }

    suspend fun loadSnapshot(uid: String): LocalProgressSnapshot? {
        val key = keyForUser(uid)
        val raw = context.localProgressDataStore.data.first()[key] ?: return null
        return decodeSnapshot(uid, raw)
    }

    suspend fun saveSnapshot(uid: String, snapshot: LocalProgressSnapshot) {
        val key = keyForUser(uid)
        val encoded = encodeSnapshot(snapshot)
        context.localProgressDataStore.edit { prefs ->
            prefs[key] = encoded
        }
    }

    suspend fun ensureInitialized(uid: String, displayName: String): LocalProgressSnapshot {
        val existing = loadSnapshot(uid)
        if (existing != null) return existing
        val starterSpec = GameCatalog.carById("vipera_gtx")
        val initial = LocalProgressSnapshot(
            profile = PlayerProfileDoc(
                docId = uid,
                userId = uid,
                displayName = displayName.ifBlank { "Street Racer" }.take(50),
                credits = 15000L,
                xp = 0L,
                level = 1L,
                racesCompleted = 0L,
                racesWon = 0L,
                unlockedAchievements = emptyList(),
                selectedCarId = starterSpec.id
            ),
            garageCars = mapOf(
                starterSpec.id to GarageCarDoc(
                    docId = starterSpec.id,
                    userId = uid,
                    carId = starterSpec.id,
                    paintHex = starterSpec.defaultPaintHex,
                    rimStyle = RimStyle.CHROME.name,
                    underglowHex = starterSpec.defaultUnderglowHex,
                    spoilerStyle = SpoilerStyle.STOCK.name,
                    bodyKitStyle = BodyKitStyle.STREET.name,
                    windowTint = WindowTint.SMOKE.name,
                    headlightStyle = HeadlightStyle.XENON_WHITE.name
                )
            ),
            careerProgress = emptyMap()
        )
        saveSnapshot(uid, initial)
        return initial
    }

    companion object {
        fun encodeSnapshot(snapshot: LocalProgressSnapshot): String {
            val root = JSONObject()
            val p = snapshot.profile
            val profJson = JSONObject().apply {
                put("userId", p.userId)
                put("displayName", p.displayName)
                put("credits", p.credits)
                put("xp", p.xp)
                put("level", p.level)
                put("racesCompleted", p.racesCompleted)
                put("racesWon", p.racesWon)
                put("selectedCarId", p.selectedCarId)
                put("totalDistanceKm", p.totalDistanceKm)
                put("highestDriftCombo", p.highestDriftCombo)
                val achArr = JSONArray()
                p.unlockedAchievements.forEach { achArr.put(it) }
                put("unlockedAchievements", achArr)
            }
            root.put("profile", profJson)

            val carsArr = JSONArray()
            snapshot.garageCars.values.forEach { car ->
                carsArr.put(
                    JSONObject().apply {
                        put("carId", car.carId)
                        put("paintHex", car.paintHex)
                        put("rimStyle", car.rimStyle)
                        put("underglowHex", car.underglowHex)
                        put("spoilerStyle", car.spoilerStyle)
                        put("bodyKitStyle", car.bodyKitStyle)
                        put("windowTint", car.windowTint)
                        put("headlightStyle", car.headlightStyle)
                        put("engineLevel", car.engineLevel)
                        put("turboLevel", car.turboLevel)
                        put("nitroLevel", car.nitroLevel)
                        put("handlingLevel", car.handlingLevel)
                        put("tiresLevel", car.tiresLevel)
                        put("suspensionLevel", car.suspensionLevel)
                        put("brakesLevel", car.brakesLevel)
                    }
                )
            }
            root.put("garageCars", carsArr)

            val careerArr = JSONArray()
            snapshot.careerProgress.values.forEach { ev ->
                careerArr.put(
                    JSONObject().apply {
                        put("eventId", ev.eventId)
                        put("bestTimeMs", ev.bestTimeMs)
                        put("bestDriftScore", ev.bestDriftScore)
                        put("medalTier", ev.medalTier)
                        put("completedCount", ev.completedCount)
                    }
                )
            }
            root.put("careerProgress", careerArr)
            return root.toString()
        }

        fun decodeSnapshot(uid: String, raw: String): LocalProgressSnapshot? {
            return try {
                val root = JSONObject(raw)
                val profJson = root.getJSONObject("profile")
                val achList = mutableListOf<String>()
                val achArr = profJson.optJSONArray("unlockedAchievements")
                if (achArr != null) {
                    for (i in 0 until achArr.length()) {
                        val id = achArr.optString(i)
                        if (id.isNotBlank()) achList.add(id)
                    }
                }
                val xp = profJson.optLong("xp", 0L).coerceAtLeast(0L)
                val profile = PlayerProfileDoc(
                    docId = uid,
                    userId = uid,
                    displayName = profJson.optString("displayName", "Street Racer"),
                    credits = profJson.optLong("credits", 15000L).coerceAtLeast(0L),
                    xp = xp,
                    level = profJson.optLong("level", PlayerProgression.levelForXp(xp).toLong()).coerceIn(1L, 50L),
                    racesCompleted = profJson.optLong("racesCompleted", 0L).coerceAtLeast(0L),
                    racesWon = profJson.optLong("racesWon", 0L).coerceAtLeast(0L),
                    unlockedAchievements = achList,
                    selectedCarId = profJson.optString("selectedCarId", "vipera_gtx"),
                    totalDistanceKm = profJson.optDouble("totalDistanceKm", 0.0).coerceAtLeast(0.0),
                    highestDriftCombo = profJson.optLong("highestDriftCombo", 0L).coerceAtLeast(0L)
                )

                val carsMap = LinkedHashMap<String, GarageCarDoc>()
                val carsArr = root.optJSONArray("garageCars")
                if (carsArr != null) {
                    for (i in 0 until carsArr.length()) {
                        val c = carsArr.getJSONObject(i)
                        val carId = c.optString("carId", "vipera_gtx")
                        val legacyHandling = c.optLong("handlingLevel", 1L).coerceIn(1L, 5L)
                        carsMap[carId] = GarageCarDoc(
                            docId = carId,
                            userId = uid,
                            carId = carId,
                            paintHex = c.optString("paintHex", "#E50914"),
                            rimStyle = c.optString("rimStyle", RimStyle.CHROME.name),
                            underglowHex = c.optString("underglowHex", "#00E5FF"),
                            spoilerStyle = c.optString("spoilerStyle", SpoilerStyle.STOCK.name),
                            bodyKitStyle = c.optString("bodyKitStyle", BodyKitStyle.STREET.name),
                            windowTint = c.optString("windowTint", WindowTint.SMOKE.name),
                            headlightStyle = c.optString("headlightStyle", HeadlightStyle.XENON_WHITE.name),
                            engineLevel = c.optLong("engineLevel", 1L).coerceIn(1L, 5L),
                            turboLevel = c.optLong("turboLevel", 1L).coerceIn(1L, 5L),
                            nitroLevel = c.optLong("nitroLevel", 1L).coerceIn(1L, 5L),
                            handlingLevel = legacyHandling,
                            tiresLevel = c.optLong("tiresLevel", legacyHandling).coerceIn(1L, 5L),
                            suspensionLevel = c.optLong("suspensionLevel", legacyHandling).coerceIn(1L, 5L),
                            brakesLevel = c.optLong("brakesLevel", 1L).coerceIn(1L, 5L)
                        )
                    }
                }

                val careerMap = LinkedHashMap<String, CareerProgressDoc>()
                val careerArr = root.optJSONArray("careerProgress")
                if (careerArr != null) {
                    for (i in 0 until careerArr.length()) {
                        val ev = careerArr.getJSONObject(i)
                        val eventId = ev.optString("eventId")
                        if (eventId.isNotBlank()) {
                            careerMap[eventId] = CareerProgressDoc(
                                docId = eventId,
                                userId = uid,
                                eventId = eventId,
                                bestTimeMs = ev.optLong("bestTimeMs", 0L),
                                bestDriftScore = ev.optLong("bestDriftScore", 0L),
                                medalTier = ev.optString("medalTier", MedalTier.NONE.name),
                                completedCount = ev.optLong("completedCount", 0L)
                            )
                        }
                    }
                }

                LocalProgressSnapshot(profile, carsMap, careerMap)
            } catch (_: Exception) {
                null
            }
        }
    }
}
