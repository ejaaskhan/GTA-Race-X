package com.example.engine

import com.example.data.TimeOfDayPreset
import com.example.data.WeatherPreset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic open-world atmosphere engine:
 * - Continuous Day/Night cycle (Dawn -> Day -> Sunset -> Midnight) with manual overrides
 * - Dynamic Weather states (Clear, Fog, Rain) with smooth transitions
 * - Computes live directional sun/moon vectors, sky/fog colors, ambient hemisphere light,
 *   building window night illumination, headlight power, wet-road reflections, and wet tire grip.
 */
class EnvironmentSystem {
    // 0.0..24.0 hours (starts at dramatic neon night 22.0h and smoothly cycles)
    var timeHours: Float = 22.0f
    private var weatherCycleSec: Float = 0f

    // Continuous blended atmosphere scalars
    var rainIntensity: Float = 0f       // 0.0 = dry, 1.0 = heavy rain
    var fogBoost: Float = 0f            // 0.0 = clear air, 1.0 = dense fog
    var roadWetness: Float = 0.25f      // 0.0 = dry, 1.0 = soaked asphalt (lowers tire grip)
    var nightFactor: Float = 1.0f       // 0.0 = bright noon, 1.0 = midnight

    // Live shader lighting uniforms
    var skyR: Float = 0.035f
    var skyG: Float = 0.045f
    var skyB: Float = 0.085f

    var fogR: Float = 0.035f
    var fogG: Float = 0.045f
    var fogB: Float = 0.085f
    var fogDensityMultiplier: Float = 1.0f

    var sunDirX: Float = -0.45f
    var sunDirY: Float = 0.82f
    var sunDirZ: Float = 0.35f

    var sunColorR: Float = 0.68f
    var sunColorG: Float = 0.78f
    var sunColorB: Float = 1.0f
    var sunIntensity: Float = 0.72f

    var skyAmbR: Float = 0.16f
    var skyAmbG: Float = 0.21f
    var skyAmbB: Float = 0.34f

    var groundAmbR: Float = 0.12f
    var groundAmbG: Float = 0.08f
    var groundAmbB: Float = 0.14f

    var headlightPower: Float = 1.0f
    var wetRoadSpec: Float = 0.9f
    var tireGripMultiplier: Float = 1.0f

    var timeOfDayLabel: String = "NIGHT"
    var weatherLabel: String = "CLEAR"

    companion object {
        // Full 24-hour cycle takes 180 real seconds in AUTO mode (1 game hour every 7.5s)
        private const val HOURS_PER_SEC = 24f / 180f
        // Weather transitions smoothly every ~45 seconds in AUTO mode
        private const val WEATHER_CYCLE_PERIOD = 135f
    }

    fun update(dt: Float, timePreset: TimeOfDayPreset, weatherPreset: WeatherPreset) {
        val h = dt.coerceIn(0.001f, 0.05f)

        // 1. Update Time of Day
        timeHours = when (timePreset) {
            TimeOfDayPreset.AUTO -> (timeHours + h * HOURS_PER_SEC) % 24f
            TimeOfDayPreset.DAWN -> 6.3f
            TimeOfDayPreset.DAY -> 13.0f
            TimeOfDayPreset.SUNSET -> 18.2f
            TimeOfDayPreset.NIGHT -> 23.0f
        }

        // 2. Determine target Weather state
        val targetRain: Float
        val targetFog: Float
        when (weatherPreset) {
            WeatherPreset.AUTO -> {
                weatherCycleSec = (weatherCycleSec + h) % WEATHER_CYCLE_PERIOD
                when {
                    weatherCycleSec < 55f -> {
                        targetRain = 0f; targetFog = 0f; weatherLabel = "CLEAR"
                    }
                    weatherCycleSec < 92f -> {
                        targetRain = 0f; targetFog = 0.85f; weatherLabel = "FOG"
                    }
                    else -> {
                        targetRain = 1.0f; targetFog = 0.45f; weatherLabel = "RAIN"
                    }
                }
            }
            WeatherPreset.CLEAR -> {
                targetRain = 0f; targetFog = 0f; weatherLabel = "CLEAR"
            }
            WeatherPreset.FOG -> {
                targetRain = 0f; targetFog = 1.0f; weatherLabel = "FOG"
            }
            WeatherPreset.RAIN -> {
                targetRain = 1.0f; targetFog = 0.50f; weatherLabel = "RAIN"
            }
        }

        val weatherLerp = (h * 1.8f).coerceAtMost(1f)
        rainIntensity += (targetRain - rainIntensity) * weatherLerp
        fogBoost += (targetFog - fogBoost) * weatherLerp

        // Asphalt dries gradually after rain stops, soaks quickly during rain
        val targetWet = if (rainIntensity > 0.1f) (0.35f + rainIntensity * 0.65f) else 0.15f * nightFactor
        roadWetness += (targetWet - roadWetness) * (h * 0.9f).coerceAtMost(1f)

        // Confirmed wet-road drift physics: heavy rain lowers tire friction up to 24%
        tireGripMultiplier = (1.0f - roadWetness * 0.24f).coerceIn(0.74f, 1.0f)

        // 3. Evaluate Sun/Moon & Sky palette from timeHours
        timeOfDayLabel = when {
            timeHours in 5.2f..7.8f -> "DAWN"
            timeHours in 7.8f..16.8f -> "DAY"
            timeHours in 16.8f..19.5f -> "SUNSET"
            else -> "NIGHT"
        }

        // Smooth solar elevation: peaks at 12:00 (+1), lowest at 0:00 (-1)
        val sunAngle = ((timeHours - 6f) / 24f) * 2f * PI.toFloat()
        val sunElev = sin(sunAngle)
        val sunHoriz = cos(sunAngle)

        // Day factor (0 at night, 1 at bright midday) and golden-hour dusk/dawn factor
        val dayFactor = ((sunElev + 0.15f) / 0.65f).coerceIn(0f, 1f)
        nightFactor = (1f - dayFactor).coerceIn(0f, 1f)
        val goldenFactor = (1f - kotlin.math.abs(sunElev - 0.08f) * 3.2f).coerceIn(0f, 1f)

        // Key light direction (sun during day, moon high overhead at night)
        if (dayFactor > 0.15f) {
            sunDirX = sunHoriz * 0.78f
            sunDirY = sunElev.coerceAtLeast(0.22f)
            sunDirZ = 0.38f
        } else {
            sunDirX = -0.42f
            sunDirY = 0.82f
            sunDirZ = 0.36f
        }

        // Blend sky clear color: midnight indigo -> golden sunset/dawn -> crisp daytime blue -> storm overcast
        var sr = lerp(0.035f, 0.34f, dayFactor) + goldenFactor * 0.38f
        var sg = lerp(0.045f, 0.56f, dayFactor) + goldenFactor * 0.14f
        var sb = lerp(0.085f, 0.86f, dayFactor) - goldenFactor * 0.18f

        // Desaturate and darken sky under heavy fog or rain storm
        val stormDark = (rainIntensity * 0.45f + fogBoost * 0.20f).coerceIn(0f, 0.65f)
        val overcastGrey = lerp(0.10f, 0.48f, dayFactor)
        sr = lerp(sr, overcastGrey, stormDark)
        sg = lerp(sg, overcastGrey * 1.03f, stormDark)
        sb = lerp(sb, overcastGrey * 1.12f, stormDark)

        skyR = sr.coerceIn(0.02f, 0.92f)
        skyG = sg.coerceIn(0.03f, 0.92f)
        skyB = sb.coerceIn(0.06f, 0.96f)

        fogR = lerp(skyR, overcastGrey * 0.95f, fogBoost * 0.5f)
        fogG = lerp(skyG, overcastGrey * 0.98f, fogBoost * 0.5f)
        fogB = lerp(skyB, overcastGrey * 1.05f, fogBoost * 0.5f)
        fogDensityMultiplier = 1.0f + fogBoost * 1.85f + rainIntensity * 0.75f

        // Sun / Moon diffuse color & intensity
        sunColorR = (lerp(0.66f, 1.00f, dayFactor) + goldenFactor * 0.12f).coerceAtMost(1.05f)
        sunColorG = (lerp(0.76f, 0.96f, dayFactor) - goldenFactor * 0.16f).coerceIn(0.45f, 1.0f)
        sunColorB = (lerp(1.00f, 0.88f, dayFactor) - goldenFactor * 0.36f).coerceIn(0.28f, 1.0f)
        sunIntensity = lerp(0.68f, 1.18f, dayFactor) * (1f - rainIntensity * 0.30f)

        // Hemisphere ambient lighting
        skyAmbR = lerp(0.16f, 0.46f, dayFactor) + goldenFactor * 0.14f
        skyAmbG = lerp(0.21f, 0.54f, dayFactor) + goldenFactor * 0.05f
        skyAmbB = lerp(0.34f, 0.66f, dayFactor) - goldenFactor * 0.06f

        groundAmbR = lerp(0.12f, 0.24f, dayFactor)
        groundAmbG = lerp(0.08f, 0.22f, dayFactor)
        groundAmbB = lerp(0.14f, 0.24f, dayFactor)

        // Headlights shine brightly at night, dusk, or rain/fog
        headlightPower = (nightFactor * 1.0f + rainIntensity * 0.45f + fogBoost * 0.35f).coerceIn(0.15f, 1.25f)
        wetRoadSpec = (0.30f + nightFactor * 0.55f + roadWetness * 1.15f).coerceIn(0.25f, 2.0f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
