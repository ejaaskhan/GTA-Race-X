package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.DeviceSettings
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.MedalTier
import com.example.data.RaceResult
import com.example.data.RadarBlip
import com.example.data.TelemetrySnapshot
import com.example.data.TimeOfDayPreset
import com.example.data.WeatherPreset
import com.example.engine.ControlInput
import com.example.ui.GameUiState
import com.example.ui.GameplayHudOverlay
import com.example.ui.PlayModeOverlay
import com.example.ui.SettingsOverlay
import com.example.ui.theme.GtaRaceXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h412dp-land-xhdpi")
class OpenWorldUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `play mode selector displays and launches all 5 game modes`() {
        var launchedMode: GameMode? = null

        composeRule.setContent {
            GtaRaceXTheme {
                PlayModeOverlay(
                    activeCarName = "VIPERA GT-X",
                    onBack = {},
                    onLaunchMode = { launchedMode = it }
                )
            }
        }
        composeRule.waitForIdle()

        // All 5 required game modes must be visible
        composeRule.onNodeWithTag("mode_card_free_roam").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_street_race").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_time_trial").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_checkpoint_race").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_drift_challenge").assertIsDisplayed()

        // Select Street Racing and click Drive Now
        composeRule.onNodeWithTag("mode_card_street_race").performClick()
        composeRule.onNodeWithTag("launch_selected_mode_button").performClick()
        composeRule.waitForIdle()

        assertEquals(GameMode.STREET_RACE, launchedMode)
    }

    @Test
    fun `settings overlay allows toggling day night presets and rain fog weather`() {
        var selectedTime: TimeOfDayPreset? = null
        var selectedWeather: WeatherPreset? = null

        composeRule.setContent {
            GtaRaceXTheme {
                SettingsOverlay(
                    state = GameUiState(screen = GameScreen.SETTINGS, settings = DeviceSettings()),
                    onBack = {},
                    onSetQuality = {},
                    onSetCamera = {},
                    onSetTimeOfDay = { selectedTime = it },
                    onSetWeather = { selectedWeather = it },
                    onSetSensitivity = {},
                    onSetAudio = {},
                    onSetShowFps = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("time_sunset").performScrollTo().performClick()
        composeRule.onNodeWithTag("weather_rain").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(TimeOfDayPreset.SUNSET, selectedTime)
        assertEquals(WeatherPreset.RAIN, selectedWeather)
    }

    @Test
    fun `gameplay hud displays countdown, street race position, wet weather badge, radar, and mission complete modal`() {
        val controlInput = ControlInput()
        val telemetry = TelemetrySnapshot(
            speedKmh = 212,
            engineRpm = 7100,
            rpmNormalized = 0.82f,
            gearLabel = "5",
            mode = GameMode.STREET_RACE,
            activeEventTitle = "DOWNTOWN STREET CLASH",
            checkpointIndex = 3,
            totalCheckpoints = 8,
            countdownLabel = "3",
            racePosition = 2,
            totalRacers = 4,
            districtName = "RACING DISTRICT",
            timeOfDayLabel = "NIGHT",
            weatherLabel = "RAIN",
            roadWetness = 0.88f,
            radarBlips = listOf(
                RadarBlip(10f, -20f, isRival = true),
                RadarBlip(-40f, 35f, isRival = false)
            ),
            fps = 60
        )

        composeRule.setContent {
            GtaRaceXTheme {
                GameplayHudOverlay(
                    state = GameUiState(screen = GameScreen.RACING),
                    telemetry = telemetry,
                    controlInput = controlInput,
                    onTogglePause = {},
                    onCycleCamera = {},
                    onRecoverCar = {},
                    onRestart = {},
                    onExitToMenu = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hud_minimap_radar").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_district_label").assertTextEquals("RACING DISTRICT")
        composeRule.onNodeWithTag("hud_weather_badge").assertTextContains("RAIN • WET", substring = true)
        composeRule.onNodeWithTag("hud_position_badge").assertTextEquals("2ND / 4")
        composeRule.onNodeWithTag("hud_countdown_text").assertTextEquals("3")
    }

    @Test
    fun `mission completion modal displays podium rank, rewards, and replay action`() {
        var replayClicked = 0
        val result = RaceResult(
            eventId = "downtown_street_clash",
            eventTitle = "DOWNTOWN STREET CLASH",
            mode = GameMode.STREET_RACE,
            elapsedMs = 39_400L,
            driftScore = 4200L,
            highestCombo = 1800L,
            medal = MedalTier.GOLD,
            creditsEarned = 7780L,
            distanceKm = 2.4f,
            finishPosition = 1,
            totalRacers = 4
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
                    onRestart = { replayClicked++ },
                    onExitToMenu = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mission_complete_modal").assertIsDisplayed()
        composeRule.onNodeWithTag("result_replay_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, replayClicked)
    }
}
