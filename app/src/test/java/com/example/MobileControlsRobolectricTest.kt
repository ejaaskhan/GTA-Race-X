package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.data.CameraMode
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.TelemetrySnapshot
import com.example.data.DeviceSettings
import com.example.engine.ControlInput
import com.example.ui.GameUiState
import com.example.ui.GameplayHudOverlay
import com.example.ui.theme.GtaRaceXTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h412dp-land-xhdpi")
class MobileControlsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `all mobile driving controls support simultaneous multi-touch and release cleanly`() {
        val controlInput = ControlInput()
        var pauseClicks = 0
        var cameraClicks = 0
        var recoverClicks = 0

        val telemetry = TelemetrySnapshot(
            speedKmh = 184,
            engineRpm = 6420,
            rpmNormalized = 0.74f,
            gearLabel = "4",
            gearShiftFlash = false,
            handbrakeActive = false,
            nitroNormalized = 0.85f,
            mode = GameMode.FREE_ROAM,
            activeEventTitle = "FREE ROAM",
            districtName = "MIDTOWN STRIP",
            fps = 60
        )

        composeRule.setContent {
            GtaRaceXTheme {
                GameplayHudOverlay(
                    state = GameUiState(
                        screen = GameScreen.RACING,
                        settings = DeviceSettings(cameraMode = CameraMode.CHASE, showFpsCounter = true)
                    ),
                    telemetry = telemetry,
                    controlInput = controlInput,
                    onTogglePause = { pauseClicks++ },
                    onCycleCamera = { cameraClicks++ },
                    onRecoverCar = { recoverClicks++ },
                    onRestart = {},
                    onExitToMenu = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Verify all required HUD readouts & touch controls are rendered
        composeRule.onNodeWithTag("steer_left_button").assertIsDisplayed()
        composeRule.onNodeWithTag("steer_right_button").assertIsDisplayed()
        composeRule.onNodeWithTag("gas_button").assertIsDisplayed()
        composeRule.onNodeWithTag("brake_button").assertIsDisplayed()
        composeRule.onNodeWithTag("handbrake_button").assertIsDisplayed()
        composeRule.onNodeWithTag("nitro_button").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_camera_button").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_speed_text").assertTextEquals("184")
        composeRule.onNodeWithTag("hud_rpm_text").assertTextEquals("6420")
        composeRule.onNodeWithTag("hud_gear_text").assertTextEquals("4")
        composeRule.onNodeWithTag("hud_nitro_text").assertTextEquals("85%")

        // Capture button center coordinates in root space for true simultaneous 4-finger multi-touch
        val leftCenter = composeRule.onNodeWithTag("steer_left_button").fetchSemanticsNode().boundsInRoot.center
        val gasCenter = composeRule.onNodeWithTag("gas_button").fetchSemanticsNode().boundsInRoot.center
        val handbrakeCenter = composeRule.onNodeWithTag("handbrake_button").fetchSemanticsNode().boundsInRoot.center
        val nitroCenter = composeRule.onNodeWithTag("nitro_button").fetchSemanticsNode().boundsInRoot.center

        // Press Left Steer + Gas + Handbrake + Nitro simultaneously using 4 distinct pointer IDs
        composeRule.onRoot().performTouchInput {
            down(pointerId = 0, position = leftCenter)
            down(pointerId = 1, position = gasCenter)
            down(pointerId = 2, position = handbrakeCenter)
            down(pointerId = 3, position = nitroCenter)
        }
        composeRule.waitForIdle()

        assertTrue("Steer Left must be active during simultaneous touch", controlInput.steerLeft)
        assertTrue("Gas must be active during simultaneous touch", controlInput.throttle)
        assertTrue("Handbrake must be active during simultaneous touch", controlInput.handbrake)
        assertTrue("Nitro must be active during simultaneous touch", controlInput.nitro)
        assertFalse("Steer Right must remain inactive", controlInput.steerRight)
        assertFalse("Brake must remain inactive", controlInput.brake)

        // Release only Handbrake & Nitro while keeping Left Steer + Gas held
        composeRule.onRoot().performTouchInput {
            up(pointerId = 2)
            up(pointerId = 3)
        }
        composeRule.waitForIdle()

        assertTrue("Steer Left must stay held when other fingers lift", controlInput.steerLeft)
        assertTrue("Gas must stay held when other fingers lift", controlInput.throttle)
        assertFalse("Handbrake must release cleanly", controlInput.handbrake)
        assertFalse("Nitro must release cleanly", controlInput.nitro)

        // Release remaining fingers
        composeRule.onRoot().performTouchInput {
            up(pointerId = 0)
            up(pointerId = 1)
        }
        composeRule.waitForIdle()

        assertFalse("Steer Left must reset on finger up", controlInput.steerLeft)
        assertFalse("Gas must reset on finger up", controlInput.throttle)

        // Test Steer Right + Brake simultaneously
        val rightCenter = composeRule.onNodeWithTag("steer_right_button").fetchSemanticsNode().boundsInRoot.center
        val brakeCenter = composeRule.onNodeWithTag("brake_button").fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            down(pointerId = 4, position = rightCenter)
            down(pointerId = 5, position = brakeCenter)
        }
        composeRule.waitForIdle()

        assertTrue("Steer Right must activate simultaneously with Brake", controlInput.steerRight)
        assertTrue("Brake must activate simultaneously with Steer Right", controlInput.brake)

        composeRule.onRoot().performTouchInput {
            up(pointerId = 4)
            up(pointerId = 5)
        }
        composeRule.waitForIdle()
        assertFalse(controlInput.steerRight)
        assertFalse(controlInput.brake)

        // Verify Pause, Camera Switch, and Recover HUD actions
        composeRule.onNodeWithTag("hud_camera_button").performClick()
        composeRule.onNodeWithTag("hud_recover_button").performClick()
        composeRule.onNodeWithTag("hud_pause_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, cameraClicks)
        assertEquals(1, recoverClicks)
        assertEquals(1, pauseClicks)
    }
}
