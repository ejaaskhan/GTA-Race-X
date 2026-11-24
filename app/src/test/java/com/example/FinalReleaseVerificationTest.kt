package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.EngineAudioSynthesizer
import com.example.audio.UiSoundType
import com.example.data.DeviceSettings
import com.example.data.GameCatalog
import com.example.data.GameScreen
import com.example.data.GraphicsQuality
import com.example.data.SettingsRepository
import com.example.engine.ControlInput
import com.example.engine.DrivingVfxSystem
import com.example.engine.VehiclePhysics
import com.example.ui.GameUiState
import com.example.ui.SettingsOverlay
import com.example.ui.SplashOverlay
import com.example.ui.theme.GtaRaceXTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h412dp-land-xhdpi")
class FinalReleaseVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun meanAbsEnergy(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) sum += abs(s.toInt())
        return sum / samples.size
    }

    @Test
    fun `procedural audio engine synthesizes all vehicle, collision, music, and UI channels and honors pause and mute`() {
        val synth = EngineAudioSynthesizer()
        val buf = ShortArray(2048)

        // 1. Synthwave BGM alone (SFX muted)
        synth.setSfxEnabled(false)
        synth.setMusicEnabled(true)
        synth.renderSamples(buf)
        assertTrue("Synthwave BGM should output audible PCM", meanAbsEnergy(buf) > 50.0)

        // 2. UI SFX alone (Music muted) across all UiSoundTypes
        synth.setMusicEnabled(false)
        synth.setSfxEnabled(true)
        for (type in UiSoundType.entries) {
            synth.triggerUiSound(type)
            synth.renderSamples(buf)
            assertTrue("UI SFX $type should output audible PCM", meanAbsEnergy(buf) > 40.0)
        }

        // 3. Collision crunch/thud even in idle menu
        synth.activeRacing = false
        synth.triggerCollision(0.95f)
        synth.renderSamples(buf)
        assertTrue("Collision trigger should produce audible crunch/thud", meanAbsEnergy(buf) > 80.0)

        // 4. Engine idle vs full acceleration + braking + drift screech + nitro
        synth.activeRacing = true
        synth.rpmNormalized = 0.25f
        synth.speedNormalized = 0.2f
        synth.throttleActive = false
        synth.renderSamples(buf)
        val idleEnergy = meanAbsEnergy(buf)
        assertTrue("Engine idle should produce PCM energy", idleEnergy > 40.0)

        synth.rpmNormalized = 0.90f
        synth.speedNormalized = 0.85f
        synth.throttleActive = true
        synth.nitroActive = true
        synth.drifting = true
        synth.brakeActive = true
        // Let smoothing settle across 2 chunks
        synth.renderSamples(buf)
        synth.renderSamples(buf)
        val fullLoadEnergy = meanAbsEnergy(buf)
        assertTrue("Full-load accel + nitro + drift + brake should exceed idle energy", fullLoadEnergy > idleEnergy)

        // 5. Lifecycle pause guarantees zero PCM output
        synth.setMusicEnabled(true)
        synth.setSfxEnabled(true)
        synth.setLifecyclePaused(true)
        synth.renderSamples(buf)
        assertEquals(0.0, meanAbsEnergy(buf), 0.0001)

        // 6. Resume restores output; muting both Music & SFX silences output
        synth.setLifecyclePaused(false)
        synth.setMusicEnabled(false)
        synth.setSfxEnabled(false)
        synth.renderSamples(buf)
        assertEquals(0.0, meanAbsEnergy(buf), 0.0001)
    }

    @Test
    fun `graphics quality presets scale draw distance, LOD, reflections, rain, and pooled VFX budgets`() {
        val low = GraphicsQuality.LOW
        val med = GraphicsQuality.MEDIUM
        val high = GraphicsQuality.HIGH

        assertTrue(low.drawDistance < med.drawDistance && med.drawDistance < high.drawDistance)
        assertTrue(low.maxParticles < med.maxParticles && med.maxParticles < high.maxParticles)
        assertTrue(low.reflectionStrength < med.reflectionStrength && med.reflectionStrength < high.reflectionStrength)
        assertTrue(low.lodDetailDistance < med.lodDetailDistance && med.lodDetailDistance < high.lodDetailDistance)
        assertTrue(low.rainDensityScale < med.rainDensityScale && med.rainDensityScale < high.rainDensityScale)
        assertTrue(high.maxParticles <= DrivingVfxSystem.MAX_PARTICLES)

        // Verify pooled VFX emission never exceeds configured LOW budget and spawns nitro particles
        val vfx = DrivingVfxSystem()
        vfx.particleBudget = low.maxParticles
        repeat(80) {
            vfx.emitNitroFlame(0f, 0.5f, 10f, 0f, 1f, 0f, -25f)
            vfx.emitTireSmoke(0f, 0.2f, 10f, 0f, -25f)
            vfx.emitCollisionSparks(0f, 0.6f, 10f, 1f, 0f, 0.85f)
        }
        assertTrue("Active particles must stay within LOW budget", vfx.activeParticleCount() <= low.maxParticles)
        assertTrue("Active particles should be non-zero after emission", vfx.activeParticleCount() > 0)
        vfx.clear()
        repeat(10) {
            vfx.emitNitroFlame(0f, 0.5f, 10f, 0f, 1f, 0f, -35f)
        }
        assertTrue("Nitro exhaust pool should contain active NITRO_FLAME particles", vfx.activeNitroParticleCount() == 10)
        vfx.update(0.016f)
        vfx.clear()
        assertEquals(0, vfx.activeParticleCount())
    }

    @Test
    fun `cinematic splash-to-3D launch fly-in smoothly transitions camera from aerial sweep to showroom framing`() {
        val physics = VehiclePhysics()
        physics.screenMode = GameScreen.MAIN_MENU
        physics.triggerLaunchFlyIn()

        assertEquals(0f, physics.launchFlyInProgress, 0.001f)
        val startCamY = physics.camY
        val startFov = physics.camFovDeg
        assertTrue("Fly-in should start from elevated aerial altitude", startCamY > 8f)
        assertTrue("Fly-in should start with wide cinematic FOV", startFov >= 64f)

        val controlInput = ControlInput()
        val build = com.example.data.ActiveCarBuild(
            spec = GameCatalog.cars.first(),
            paintHex = "#E50914",
            rimStyle = com.example.data.RimStyle.CHROME,
            underglowHex = "#00E5FF",
            engineLevel = 1,
            nitroLevel = 1,
            handlingLevel = 1,
            brakesLevel = 1
        )
        repeat(150) {
            physics.step(
                dt = 0.016f,
                input = controlInput,
                carBuild = build,
                steeringSensitivity = 1.0f,
                cameraMode = com.example.data.CameraMode.CHASE,
                colliders = emptyList()
            )
        }

        assertEquals(1f, physics.launchFlyInProgress, 0.001f)
        assertTrue("Fly-in should settle down near car eye level", physics.camY < 4.5f)
        assertTrue("Fly-in should settle FOV near showroom 50 deg", physics.camFovDeg < 54f)
    }

    @Test
    fun `splash overlay displays GTA RACE X and DRIVE BEYOND LIMITS tagline and supports tap to launch`() {
        var finished = false
        composeRule.setContent {
            GtaRaceXTheme {
                SplashOverlay(
                    onFinished = { finished = true },
                    autoFinishDelayMs = 0L
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_title").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_tagline").assertIsDisplayed()
            .assertTextContains("DRIVE BEYOND LIMITS.")

        assertFalse(finished)
        composeRule.onNodeWithTag("splash_screen").performClick()
        composeRule.waitForIdle()
        assertTrue("Tapping splash screen should immediately launch into 3D menu", finished)
    }

    @Test
    fun `settings overlay exposes Low Medium High presets and independent Music and SFX switches`() {
        var selectedQuality: GraphicsQuality? = null
        var musicState: Boolean? = null
        var sfxState: Boolean? = null

        composeRule.setContent {
            GtaRaceXTheme {
                SettingsOverlay(
                    state = GameUiState(
                        screen = GameScreen.SETTINGS,
                        settings = DeviceSettings(
                            graphicsQuality = GraphicsQuality.HIGH,
                            musicEnabled = true,
                            sfxEnabled = true
                        )
                    ),
                    onBack = {},
                    onSetQuality = { selectedQuality = it },
                    onSetCamera = {},
                    onSetSensitivity = {},
                    onSetAudio = {},
                    onSetMusic = { musicState = it },
                    onSetSfx = { sfxState = it },
                    onSetShowFps = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(GraphicsQuality.HIGH.summary).assertIsDisplayed()
        composeRule.onNodeWithTag("quality_low").performClick()
        composeRule.onNodeWithTag("music_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("audio_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(GraphicsQuality.LOW, selectedQuality)
        assertEquals(false, musicState)
        assertEquals(false, sfxState)
    }

    @Test
    fun `settings repository persists Music SFX and GraphicsQuality in DataStore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = SettingsRepository(context)

        repo.setGraphicsQuality(GraphicsQuality.LOW)
        repo.setMusicEnabled(false)
        repo.setSfxEnabled(false)

        val saved = repo.settingsFlow.first()
        assertEquals(GraphicsQuality.LOW, saved.graphicsQuality)
        assertFalse(saved.musicEnabled)
        assertFalse(saved.sfxEnabled)
        assertFalse(saved.engineAudioEnabled)

        // Restore defaults
        repo.setGraphicsQuality(GraphicsQuality.HIGH)
        repo.setMusicEnabled(true)
        repo.setSfxEnabled(true)
        val restored = repo.settingsFlow.first()
        assertEquals(GraphicsQuality.HIGH, restored.graphicsQuality)
        assertTrue(restored.musicEnabled)
        assertTrue(restored.sfxEnabled)
    }
}
