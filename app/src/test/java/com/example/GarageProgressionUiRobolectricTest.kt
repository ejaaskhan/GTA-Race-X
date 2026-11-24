package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActiveCarBuild
import com.example.data.BodyKitStyle
import com.example.data.CareerProgressDoc
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.GarageCarDoc
import com.example.data.HeadlightStyle
import com.example.data.LocalProgressRepository
import com.example.data.LocalProgressSnapshot
import com.example.data.MedalTier
import com.example.data.PlayerProfileDoc
import com.example.data.RaceResult
import com.example.data.RimStyle
import com.example.data.SpoilerStyle
import com.example.data.TelemetrySnapshot
import com.example.data.WindowTint
import com.example.engine.ControlInput
import com.example.ui.GameUiState
import com.example.ui.GameplayHudOverlay
import com.example.ui.GarageOverlay
import com.example.ui.MainMenuOverlay
import com.example.ui.ProfileOverlay
import com.example.ui.theme.GtaRaceXTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h412dp-land-xhdpi")
class GarageProgressionUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun starterState(
        level: Long = 1L,
        credits: Long = 25000L,
        previewCarId: String = "vipera_gtx",
        ownedIds: List<String> = listOf("vipera_gtx")
    ): GameUiState {
        val spec = GameCatalog.carById(previewCarId)
        val garageMap = ownedIds.associateWith { id ->
            val s = GameCatalog.carById(id)
            GarageCarDoc(
                docId = id,
                userId = "test_uid",
                carId = id,
                paintHex = s.defaultPaintHex,
                rimStyle = RimStyle.CHROME.name,
                underglowHex = s.defaultUnderglowHex
            )
        }
        val build = ActiveCarBuild(
            spec = spec,
            paintHex = spec.defaultPaintHex,
            rimStyle = RimStyle.CHROME,
            underglowHex = spec.defaultUnderglowHex,
            engineLevel = 1,
            nitroLevel = 1,
            handlingLevel = 1,
            brakesLevel = 1
        )
        return GameUiState(
            screen = GameScreen.GARAGE,
            profile = PlayerProfileDoc(
                docId = "test_uid",
                userId = "test_uid",
                displayName = "Velocity Ace",
                credits = credits,
                xp = 1250L,
                level = level,
                racesCompleted = 4L,
                racesWon = 2L,
                unlockedAchievements = listOf("first_victory", "style_icon"),
                selectedCarId = ownedIds.first(),
                totalDistanceKm = 12.4,
                highestDriftCombo = 3200L
            ),
            garageCars = garageMap,
            activeCarBuild = build
        )
    }

    @Test
    fun `garage overlay lists all 5 vehicles, 6-stat telemetry card, and dual-path level unlock CTA`() {
        var unlockedCarId: String? = null
        // Player is Level 3 -> Muscle Car (titan_v8, unlockLevel=3) is CLAIM FREE!
        val state = starterState(level = 3L, credits = 5000L, previewCarId = "titan_v8")

        composeRule.setContent {
            GtaRaceXTheme {
                GarageOverlay(
                    state = state,
                    onBack = {},
                    onOpenProfile = {},
                    onPreviewCar = {},
                    onEquipCar = {},
                    onUnlockCar = { unlockedCarId = it },
                    onUpdateVisuals = { _, _, _, _, _, _, _ -> },
                    onUpgradeStat = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 6-stat telemetry card visible
        composeRule.onNodeWithTag("garage_stats_card").assertIsDisplayed()

        // All 5 vehicle rows exist in the Garage roster
        composeRule.onNodeWithTag("garage_car_vipera_gtx").assertIsDisplayed()
        composeRule.onNodeWithTag("garage_car_apex_kuro").assertIsDisplayed()
        composeRule.onNodeWithTag("garage_car_titan_v8").assertIsDisplayed()
        composeRule.onNodeWithTag("garage_vehicles_list")
            .performScrollToNode(hasTestTag("garage_car_solstice_v10"))
        composeRule.onNodeWithTag("garage_car_solstice_v10").assertIsDisplayed()
        composeRule.onNodeWithTag("garage_vehicles_list")
            .performScrollToNode(hasTestTag("garage_car_spectre_rs"))
        composeRule.onNodeWithTag("garage_car_spectre_rs").assertIsDisplayed()

        // Level-eligible locked vehicle shows CLAIM FREE and is enabled even with low credits
        composeRule.onNodeWithTag("garage_equip_unlock_button")
            .assertIsEnabled()
            .assertTextContains("CLAIM FREE", substring = true)
            .performClick()
        composeRule.waitForIdle()

        assertEquals("titan_v8", unlockedCarId)
    }

    @Test
    fun `garage customize and upgrades tabs expose all 6 visual options and all 6 upgrade tracks`() {
        var lastSpoiler: SpoilerStyle? = null
        var lastBodyKit: BodyKitStyle? = null
        var lastTint: WindowTint? = null
        var lastHeadlight: HeadlightStyle? = null
        val upgradedTracks = mutableListOf<String>()

        composeRule.setContent {
            GtaRaceXTheme {
                GarageOverlay(
                    state = starterState(credits = 50000L),
                    onBack = {},
                    onOpenProfile = {},
                    onPreviewCar = {},
                    onEquipCar = {},
                    onUnlockCar = {},
                    onUpdateVisuals = { _, _, _, sp, kit, tint, hl ->
                        if (sp != null) lastSpoiler = sp
                        if (kit != null) lastBodyKit = kit
                        if (tint != null) lastTint = tint
                        if (hl != null) lastHeadlight = hl
                    },
                    onUpgradeStat = { upgradedTracks.add(it) }
                )
            }
        }
        composeRule.waitForIdle()

        // Switch to CUSTOMIZE tab and exercise spoiler, body kit, window tint, headlights
        composeRule.onNodeWithTag("garage_tab_style").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("garage_customize_list").performScrollToNode(hasTestTag("spoiler_gt_wing"))
        composeRule.onNodeWithTag("spoiler_gt_wing").performClick()
        composeRule.onNodeWithTag("garage_customize_list").performScrollToNode(hasTestTag("bodykit_widebody"))
        composeRule.onNodeWithTag("bodykit_widebody").performClick()
        composeRule.onNodeWithTag("garage_customize_list").performScrollToNode(hasTestTag("tint_midnight"))
        composeRule.onNodeWithTag("tint_midnight").performClick()
        composeRule.onNodeWithTag("garage_customize_list").performScrollToNode(hasTestTag("headlight_ice_blue"))
        composeRule.onNodeWithTag("headlight_ice_blue").performClick()
        composeRule.waitForIdle()

        assertEquals(SpoilerStyle.GT_WING, lastSpoiler)
        assertEquals(BodyKitStyle.WIDEBODY, lastBodyKit)
        assertEquals(WindowTint.MIDNIGHT, lastTint)
        assertEquals(HeadlightStyle.ICE_BLUE, lastHeadlight)

        // Switch to UPGRADES tab and tap all 6 mechanical upgrade buttons
        composeRule.onNodeWithTag("garage_tab_tuning").performClick()
        composeRule.waitForIdle()

        listOf("engine", "turbo", "brakes", "tires", "suspension", "nitro").forEach { key ->
            composeRule.onNodeWithTag("garage_upgrades_list").performScrollToNode(hasTestTag("upgrade_$key"))
            composeRule.onNodeWithTag("upgrade_$key").performClick()
        }
        composeRule.waitForIdle()

        assertEquals(listOf("ENGINE", "TURBO", "BRAKES", "TIRES", "SUSPENSION", "NITRO"), upgradedTracks)
    }

    @Test
    fun `profile overlay displays level, xp, collection, and achievements`() {
        var inspectedCar: String? = null
        val state = starterState(level = 4L, ownedIds = listOf("vipera_gtx", "apex_kuro", "titan_v8"))

        composeRule.setContent {
            GtaRaceXTheme {
                ProfileOverlay(
                    state = state,
                    onBack = {},
                    onInspectCarInGarage = { inspectedCar = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_level_badge").assertTextContains("LVL 4", substring = true)
        composeRule.onNodeWithTag("profile_xp_text").assertTextContains("1,250", substring = true)
        composeRule.onNodeWithTag("profile_collection_count").assertTextContains("3 / 5", substring = true)
        composeRule.onNodeWithTag("profile_achievements_count").assertTextContains("2 / 10", substring = true)
        composeRule.onNodeWithTag("achievement_first_victory").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_car_titan_v8").performClick()
        composeRule.waitForIdle()
        assertEquals("titan_v8", inspectedCar)
    }

    @Test
    fun `main menu driver badge navigates to profile screen`() {
        var navigatedScreen: GameScreen? = null
        val state = starterState(level = 4L, ownedIds = listOf("vipera_gtx", "apex_kuro", "titan_v8"))

        composeRule.setContent {
            GtaRaceXTheme {
                MainMenuOverlay(
                    state = state.copy(screen = GameScreen.MAIN_MENU),
                    onNavigate = { navigatedScreen = it }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("menu_profile_button").performClick()
        composeRule.waitForIdle()
        assertEquals(GameScreen.PROFILE, navigatedScreen)
    }

    @Test
    fun `race result modal displays earned XP, level-up badge, and unlocked achievements`() {
        val result = RaceResult(
            eventId = "downtown_street_clash",
            eventTitle = "DOWNTOWN STREET CLASH",
            mode = GameMode.STREET_RACE,
            elapsedMs = 39_100L,
            driftScore = 4400L,
            highestCombo = 2100L,
            medal = MedalTier.GOLD,
            creditsEarned = 8200L,
            distanceKm = 2.6f,
            finishPosition = 1,
            totalRacers = 4,
            xpEarned = 695L,
            leveledUpTo = 3,
            newlyUnlockedAchievements = listOf("FIRST VICTORY")
        )

        composeRule.setContent {
            GtaRaceXTheme {
                GameplayHudOverlay(
                    state = GameUiState(screen = GameScreen.RACING, lastRaceResult = result),
                    telemetry = TelemetrySnapshot(mode = GameMode.STREET_RACE),
                    controlInput = ControlInput(),
                    onTogglePause = {},
                    onCycleCamera = {},
                    onRecoverCar = {},
                    onRestart = {},
                    onExitToMenu = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("result_xp_text").assertTextContains("+695 XP", substring = true)
        composeRule.onNodeWithTag("result_level_up_badge").assertTextContains("LEVEL UP • LVL 3", substring = true)
        composeRule.onNodeWithTag("result_achievements_badge").assertTextContains("FIRST VICTORY", substring = true)
    }

    @Test
    fun `local progress repository persists money, xp, unlocked cars, customizations, upgrades, and career`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LocalProgressRepository(context)
        val uid = "local_save_test_user"

        val snapshot = LocalProgressSnapshot(
            profile = PlayerProfileDoc(
                docId = uid,
                userId = uid,
                displayName = "Midnight King",
                credits = 64250L,
                xp = 2350L,
                level = 5L,
                racesCompleted = 8L,
                racesWon = 5L,
                unlockedAchievements = listOf("first_victory", "rising_star", "style_icon"),
                selectedCarId = "titan_v8",
                totalDistanceKm = 24.5,
                highestDriftCombo = 5400L
            ),
            garageCars = mapOf(
                "vipera_gtx" to GarageCarDoc(docId = "vipera_gtx", userId = uid, carId = "vipera_gtx"),
                "titan_v8" to GarageCarDoc(
                    docId = "titan_v8",
                    userId = uid,
                    carId = "titan_v8",
                    paintHex = "#FF6D00",
                    rimStyle = RimStyle.BRONZE.name,
                    underglowHex = "#FFB800",
                    spoilerStyle = SpoilerStyle.LIP.name,
                    bodyKitStyle = BodyKitStyle.WIDEBODY.name,
                    windowTint = WindowTint.MIDNIGHT.name,
                    headlightStyle = HeadlightStyle.SOLAR_AMBER.name,
                    engineLevel = 4L,
                    turboLevel = 3L,
                    brakesLevel = 5L,
                    tiresLevel = 4L,
                    suspensionLevel = 3L,
                    nitroLevel = 5L,
                    handlingLevel = 4L
                )
            ),
            careerProgress = mapOf(
                "downtown_street_clash" to CareerProgressDoc(
                    docId = "downtown_street_clash",
                    userId = uid,
                    eventId = "downtown_street_clash",
                    bestTimeMs = 37400L,
                    bestDriftScore = 5100L,
                    medalTier = MedalTier.GOLD.name,
                    completedCount = 3L
                )
            )
        )

        repo.saveSnapshot(uid, snapshot)
        val loaded = repo.loadSnapshot(uid)
        assertNotNull(loaded)
        loaded!!

        assertEquals(64250L, loaded.profile.credits)
        assertEquals(2350L, loaded.profile.xp)
        assertEquals(5L, loaded.profile.level)
        assertEquals("titan_v8", loaded.profile.selectedCarId)
        assertEquals(3, loaded.profile.unlockedAchievements.size)

        val muscle = loaded.garageCars["titan_v8"]
        assertNotNull(muscle)
        muscle!!
        assertEquals(RimStyle.BRONZE.name, muscle.rimStyle)
        assertEquals(SpoilerStyle.LIP.name, muscle.spoilerStyle)
        assertEquals(BodyKitStyle.WIDEBODY.name, muscle.bodyKitStyle)
        assertEquals(WindowTint.MIDNIGHT.name, muscle.windowTint)
        assertEquals(HeadlightStyle.SOLAR_AMBER.name, muscle.headlightStyle)
        assertEquals(4L, muscle.engineLevel)
        assertEquals(3L, muscle.turboLevel)
        assertEquals(5L, muscle.brakesLevel)
        assertEquals(4L, muscle.tiresLevel)
        assertEquals(3L, muscle.suspensionLevel)
        assertEquals(5L, muscle.nitroLevel)

        val career = loaded.careerProgress["downtown_street_clash"]
        assertNotNull(career)
        assertEquals(MedalTier.GOLD.name, career!!.medalTier)
        assertEquals(37400L, career.bestTimeMs)
    }
}
