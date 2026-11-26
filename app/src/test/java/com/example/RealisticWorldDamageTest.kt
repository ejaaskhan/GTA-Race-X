package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.ActiveCarBuild
import com.example.data.CameraMode
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.RadarBlip
import com.example.data.RimStyle
import com.example.data.TelemetrySnapshot
import com.example.engine.CityWorld
import com.example.engine.ControlInput
import com.example.engine.DrivingVfxListener
import com.example.engine.DrivingVfxSystem
import com.example.engine.VehiclePhysics
import com.example.ui.GameUiState
import com.example.ui.GameplayHudOverlay
import com.example.ui.SplashOverlay
import com.example.ui.theme.GtaRaceXTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h412dp-land-xhdpi")
class RealisticWorldDamageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun defaultCarBuild(): ActiveCarBuild {
        val spec = GameCatalog.cars.first()
        return ActiveCarBuild(
            spec = spec,
            paintHex = spec.defaultPaintHex,
            rimStyle = RimStyle.CHROME,
            underglowHex = spec.defaultUnderglowHex,
            engineLevel = 1,
            nitroLevel = 1,
            handlingLevel = 1,
            brakesLevel = 1
        )
    }

    @Test
    fun `splash screen displays Game Created by Dev E combined with GTA Race X branding`() {
        composeRule.setContent {
            GtaRaceXTheme {
                SplashOverlay(onFinished = {}, autoFinishDelayMs = 0L)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_title").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_tagline").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_creator_credit")
            .assertIsDisplayed()
            .assertTextEquals("Game Created by Dev. E")
    }

    @Test
    fun `expanded open world maps Coastal Highway, Mountain Hills, City Park, elevated hills, and 4 Repair Bays`() {
        // Expanded world districts
        assertEquals("COASTAL HIGHWAY", CityWorld.getDistrictName(CityWorld.COASTAL_X, 40f))
        assertEquals("MOUNTAIN HILLS", CityWorld.getDistrictName(CityWorld.MOUNTAIN_X, -200f))
        assertEquals("MOUNTAIN HILLS", CityWorld.getDistrictName(0f, CityWorld.MOUNTAIN_Z))
        assertEquals("CITY PARK", CityWorld.getDistrictName(0f, 540f))

        // Core 7 districts remain preserved
        assertEquals("DOWNTOWN", CityWorld.getDistrictName(0f, 0f))
        assertEquals("RESIDENTIAL DISTRICT", CityWorld.getDistrictName(-260f, 260f))
        assertEquals("INDUSTRIAL DISTRICT", CityWorld.getDistrictName(240f, 240f))
        assertEquals("HIGHWAY", CityWorld.getDistrictName(CityWorld.SKYWAY_X, 0f))
        assertEquals("BRIDGE", CityWorld.getDistrictName(0f, CityWorld.BRIDGE_Z))
        assertEquals("TUNNEL", CityWorld.getDistrictName(CityWorld.TUNNEL_X, 0f))
        assertEquals("RACING DISTRICT", CityWorld.getDistrictName(-260f, -260f))

        // Mountain Hills terrain rises smoothly above flat downtown
        val mountainElev = CityWorld.getGroundHeight(CityWorld.MOUNTAIN_X, -200f)
        val northMountainElev = CityWorld.getGroundHeight(80f, CityWorld.MOUNTAIN_Z)
        assertTrue("Mountain Hills road must have elevated height (got $mountainElev)", mountainElev > 5f)
        assertTrue("Northern Mountain ridge must have elevated height (got $northMountainElev)", northMountainElev > 4f)
        assertEquals(0f, CityWorld.getGroundHeight(0f, 0f), 0.01f)

        // 4 glowing Repair Bays defined and detectable
        assertEquals(4, CityWorld.REPAIR_BAYS.size)
        for (bay in CityWorld.REPAIR_BAYS) {
            assertNotNull("Bay ${bay.id} must be detected at its center", CityWorld.findRepairBay(bay.x, bay.z))
        }
        assertNull("Downtown center should not trigger a repair bay", CityWorld.findRepairBay(0f, 0f))
    }

    @Test
    fun `progressive crash damage lowers speed, emits pooled smoke and debris, and repairs via bay or instant repair`() {
        val car = defaultCarBuild()
        val vfxPool = DrivingVfxSystem()
        var repairCallbacks = 0
        val listener = object : DrivingVfxListener {
            override fun onTireSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float) {}
            override fun onSkidMark(prevX: Float, prevY: Float, prevZ: Float, currX: Float, currY: Float, currZ: Float, rightX: Float, rightZ: Float) {}
            override fun onCollisionSpark(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {
                vfxPool.emitCollisionSparks(wx, wy, wz, nx, nz, intensity)
            }
            override fun onCrashDebris(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {
                vfxPool.emitCrashDebris(wx, wy, wz, nx, nz, intensity)
            }
            override fun onEngineSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float, damageSeverity: Float) {
                vfxPool.emitEngineSmoke(wx, wy, wz, carVx, carVz, damageSeverity)
            }
            override fun onRepairCompleted() {
                repairCallbacks++
            }
        }

        // 1. Measure pristine top speed after 4 seconds of full throttle
        val pristine = VehiclePhysics().apply {
            screenMode = GameScreen.RACING
            startSession(GameMode.FREE_ROAM, null)
        }
        val throttleInput = ControlInput().apply { throttle = true }
        repeat(240) {
            pristine.step(1f / 60f, throttleInput, car, 1.0f, CameraMode.CHASE, emptyList(), listener)
        }
        val pristineSpeed = pristine.speedMs

        // 2. Inflict heavy front crashes on a second car to verify progressive damage, debris & smoke
        val damaged = VehiclePhysics().apply {
            screenMode = GameScreen.RACING
            startSession(GameMode.FREE_ROAM, null)
        }
        repeat(4) {
            damaged.registerImpactDamage(
                contactX = damaged.x,
                contactZ = damaged.z - 2.2f,
                nx = 0f,
                nz = 1f,
                impactSpeed = 30f,
                vfxListener = listener
            )
        }

        assertTrue("Repeated heavy impacts must reduce health below 55% (got ${damaged.healthNormalized})", damaged.healthNormalized < 0.55f)
        assertTrue("Frontal crashes must record frontDamage (got ${damaged.frontDamage})", damaged.frontDamage > 0.5f)
        assertTrue("Severe crashes must emit pooled crash debris", vfxPool.activeDebrisCount() > 0)
        assertTrue("Heavy crash must raise crimson HUD impact flash", damaged.impactFlashAlpha > 0.2f)

        // Drive damaged car for 4 seconds: engine smoke must emit and top speed must be penalized
        repeat(240) {
            damaged.step(1f / 60f, throttleInput, car, 1.0f, CameraMode.CHASE, emptyList(), listener)
        }
        assertTrue("Damaged car below 55% health must emit hood engine smoke", vfxPool.activeEngineSmokeCount() > 0)
        assertTrue(
            "Heavily damaged car must reach lower speed than pristine car (damaged=${damaged.speedMs}, pristine=$pristineSpeed)",
            damaged.speedMs < pristineSpeed - 3.5f
        )
        assertTrue("Particle pool must remain within MAX_PARTICLES budget", vfxPool.activeParticleCount() <= DrivingVfxSystem.MAX_PARTICLES)

        // 3. Drive into a CityWorld Repair Bay to verify automatic 100% restoration
        val bay = CityWorld.REPAIR_BAYS.first()
        damaged.x = bay.x
        damaged.z = bay.z
        damaged.step(1f / 60f, ControlInput(), car, 1.0f, CameraMode.CHASE, emptyList(), listener)

        assertEquals("Driving into a Repair Bay must restore 100% health", 1.0f, damaged.healthNormalized, 0.001f)
        assertEquals("Repair Bay must clear frontDamage", 0f, damaged.frontDamage, 0.001f)
        assertEquals("INTACT", damaged.damageStatusLabel)
        assertTrue("Repair Bay must trigger onRepairCompleted audio callback", repairCallbacks >= 1)

        // 4. Verify direct repairCar() also restores 100% after damage
        damaged.registerImpactDamage(damaged.x + 1.1f, damaged.z, -1f, 0f, 24f, listener)
        assertTrue(damaged.healthNormalized < 1.0f)
        assertTrue(damaged.rightDamage > 0f)
        damaged.repairCar(vfxListener = listener, showNotice = true)
        assertEquals(1.0f, damaged.healthNormalized, 0.001f)
        assertEquals(0f, damaged.rightDamage, 0.001f)
    }

    @Test
    fun `gameplay hud renders chassis integrity bar, damage status, impact flash, repair notice, and quick repair button`() {
        var repairClicks = 0
        val telemetry = TelemetrySnapshot(
            speedKmh = 148,
            districtName = "COASTAL HIGHWAY",
            healthNormalized = 0.42f,
            frontDamage = 0.72f,
            leftDamage = 0.30f,
            impactFlashAlpha = 0.65f,
            damageStatusLabel = "SMOKING",
            repairedNoticeActive = true,
            radarBlips = listOf(RadarBlip(120f, 120f, isRival = false, isRepairBay = true))
        )

        composeRule.setContent {
            GtaRaceXTheme {
                GameplayHudOverlay(
                    state = GameUiState(screen = GameScreen.RACING),
                    telemetry = telemetry,
                    controlInput = ControlInput(),
                    onTogglePause = {},
                    onCycleCamera = {},
                    onRecoverCar = {},
                    onRestart = {},
                    onExitToMenu = {},
                    onRepairCar = { repairClicks++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hud_health_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_damage_status_label").assertTextContains("SMOKING", substring = true)
        composeRule.onNodeWithTag("hud_health_pct_text").assertTextEquals("42%")
        composeRule.onNodeWithTag("hud_impact_flash").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_repaired_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_district_label").assertTextEquals("COASTAL HIGHWAY")

        composeRule.onNodeWithTag("hud_repair_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, repairClicks)

        // Tapping the realistic GPS mini-map opens the interactive Full Satellite World Map
        composeRule.onNodeWithTag("hud_minimap_radar").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hud_full_map_modal").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_full_map_close_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hud_full_map_modal").assertDoesNotExist()
    }
}
