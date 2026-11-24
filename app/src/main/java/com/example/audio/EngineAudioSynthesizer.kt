package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class UiSoundType {
    CLICK,
    CONFIRM,
    UNLOCK,
    COUNTDOWN
}

/**
 * Real-time zero-allocation procedural DSP audio engine for GTA RACE X.
 * Synthesizes and mixes:
 * - Engine RPM multi-harmonic exhaust & throttle acceleration growl
 * - Ceramic braking hiss when decelerating at speed
 * - Modulated rubber tire screech on drift / handbrake
 * - Nitro turbo spool whistle + plasma afterburner roar
 * - Impact-scaled metallic collision crunch & low-end thud
 * - 120 BPM Neon Synthwave background soundtrack (menu groove vs high-energy race drive)
 * - Crisp UI click / confirm / unlock / countdown feedback chimes
 * Controlled via independent Music & SFX toggles and Android pause/resume lifecycle hooks.
 */
class EngineAudioSynthesizer {
    @Volatile var rpmNormalized: Float = 0.15f
    @Volatile var speedNormalized: Float = 0f
    @Volatile var throttleActive: Boolean = false
    @Volatile var brakeActive: Boolean = false
    @Volatile var nitroActive: Boolean = false
    @Volatile var drifting: Boolean = false
    @Volatile var activeRacing: Boolean = false

    private val sfxEnabled = AtomicBoolean(true)
    private val musicEnabled = AtomicBoolean(true)
    private val lifecyclePaused = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    // Lock-free one-shot event triggers from GL / UI threads
    @Volatile private var pendingCollisionIntensity: Float = 0f
    private val pendingUiSound = AtomicReference<UiSoundType?>(null)

    // Internal continuous DSP state (zero per-frame heap allocations)
    private var phaseMain = 0.0
    private var phaseHarm = 0.0
    private var phaseSub = 0.0
    private var phaseTurbo = 0.0
    private var phaseMusicBass = 0.0
    private var phaseMusicArp = 0.0
    private var phaseMusicPad = 0.0
    private var phaseUi = 0.0
    private var smoothedRpm = 0.15
    private var smoothedAccel = 0.0
    private var lpNoise = 0.0
    private var hpPrev = 0.0
    private var collisionEnv = 0.0
    private var collisionPhase = 0.0
    private var uiEnv = 0.0
    private var uiFreqStart = 660.0
    private var uiFreqEnd = 990.0
    private var uiDecayRate = 0.00045
    private var musicSampleClock = 0L
    private val rng = Random(2077)

    fun setEnabled(isEnabled: Boolean) {
        sfxEnabled.set(isEnabled)
    }

    fun setSfxEnabled(isEnabled: Boolean) {
        sfxEnabled.set(isEnabled)
    }

    fun setMusicEnabled(isEnabled: Boolean) {
        musicEnabled.set(isEnabled)
    }

    fun isSfxEnabled(): Boolean = sfxEnabled.get()

    fun isMusicEnabled(): Boolean = musicEnabled.get()

    fun setLifecyclePaused(paused: Boolean) {
        lifecyclePaused.set(paused)
    }

    fun triggerCollision(intensity: Float) {
        val clamped = intensity.coerceIn(0.15f, 1.0f)
        if (clamped > pendingCollisionIntensity) {
            pendingCollisionIntensity = clamped
        }
    }

    fun triggerUiSound(type: UiSoundType) {
        if (!sfxEnabled.get() || lifecyclePaused.get()) return
        pendingUiSound.set(type)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ runAudioLoop() }, "GtaRaceX-Audio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    /**
     * Synthesizes one PCM chunk into [outBuffer]. Exposed internally for deterministic
     * unit verification of every audio channel (engine, accel, brake, screech, nitro,
     * collision, synthwave BGM, and UI SFX) without requiring audio hardware.
     */
    internal fun renderSamples(outBuffer: ShortArray, sampleRate: Int = 22050) {
        val paused = lifecyclePaused.get()
        val playSfx = sfxEnabled.get() && !paused
        val playMusic = musicEnabled.get() && !paused

        // Consume pending one-shot SFX triggers
        val crashTrigger = pendingCollisionIntensity
        if (crashTrigger > 0.01f) {
            pendingCollisionIntensity = 0f
            if (playSfx) {
                collisionEnv = maxOf(collisionEnv, crashTrigger.toDouble())
                collisionPhase = 0.0
            }
        }
        val uiTrigger = pendingUiSound.getAndSet(null)
        if (uiTrigger != null && playSfx) {
            uiEnv = 1.0
            phaseUi = 0.0
            when (uiTrigger) {
                UiSoundType.CLICK -> {
                    uiFreqStart = 740.0; uiFreqEnd = 920.0; uiDecayRate = 0.0014
                }
                UiSoundType.CONFIRM -> {
                    uiFreqStart = 523.25; uiFreqEnd = 1046.5; uiDecayRate = 0.00055
                }
                UiSoundType.UNLOCK -> {
                    uiFreqStart = 440.0; uiFreqEnd = 1318.5; uiDecayRate = 0.00032
                }
                UiSoundType.COUNTDOWN -> {
                    uiFreqStart = 880.0; uiFreqEnd = 880.0; uiDecayRate = 0.00065
                }
            }
        }

        if (!playSfx && !playMusic) {
            outBuffer.fill(0)
            return
        }

        val racing = activeRacing
        val targetRpm = rpmNormalized.coerceIn(0.08f, 1.0f).toDouble()
        val spdNorm = speedNormalized.coerceIn(0f, 1f).toDouble()
        val targetAccel = if (throttleActive || nitroActive) 1.0 else 0.0
        val brakingAtSpeed = brakeActive && spdNorm > 0.07
        val twoPiOverSr = (2.0 * PI) / sampleRate

        // 120 BPM synthwave timing: 16th note = 0.125s
        val samplesPerStep = (sampleRate / 8).coerceAtLeast(1)

        for (i in outBuffer.indices) {
            var mix = 0.0

            // 1. Engine, Acceleration, Braking, Tire Screech, Nitro & Collision SFX
            if (playSfx) {
                smoothedRpm += (targetRpm - smoothedRpm) * 0.0028
                smoothedAccel += (targetAccel - smoothedAccel) * 0.0020

                if (racing) {
                    val engineGain = 0.11 + smoothedAccel * 0.13 + if (nitroActive) 0.05 else 0.0
                    val baseFreq = 50.0 + smoothedRpm * 225.0 + if (nitroActive) 42.0 else 0.0
                    phaseMain = wrapPhase(phaseMain + baseFreq * twoPiOverSr)
                    phaseHarm = wrapPhase(phaseHarm + baseFreq * 1.5 * twoPiOverSr)
                    phaseSub = wrapPhase(phaseSub + baseFreq * 0.5 * twoPiOverSr)

                    val saw = (phaseMain / PI) - 1.0
                    // Acceleration intake growl enriches upper harmonics under load
                    val accelGrowl = sin(phaseMain * 2.0) * 0.24 * smoothedAccel
                    val engineWave = sin(phaseMain) * 0.46 +
                        saw * (0.20 + 0.18 * smoothedAccel) +
                        sin(phaseHarm) * 0.20 +
                        sin(phaseSub) * 0.22 +
                        accelGrowl
                    mix += engineWave * engineGain

                    // White noise generator shared by brake hiss, tire screech, nitro roar & crash
                    val white = rng.nextDouble(-1.0, 1.0)
                    lpNoise = lpNoise * 0.68 + white * 0.32
                    val hpNoise = white - hpPrev
                    hpPrev = white * 0.85

                    // Ceramic braking pad hiss & slight pitch sweep
                    if (brakingAtSpeed) {
                        mix += (hpNoise * 0.14 + sin(phaseMain * 7.5) * 0.05) * (0.35 + 0.65 * spdNorm) * 0.24
                    }

                    // Filtered rubber tire screech during drift or handbrake lockup
                    if (drifting) {
                        val screechTone = sin(phaseMain * 5.8 + 0.6 * sin(phaseHarm * 2.0))
                        mix += (lpNoise * 0.22 * screechTone + hpNoise * 0.06) * 0.26
                    }

                    // Nitro boost: turbine spool whistle + low-mid plasma afterburner roar
                    if (nitroActive) {
                        val turboFreq = 980.0 + smoothedRpm * 740.0
                        phaseTurbo = wrapPhase(phaseTurbo + turboFreq * twoPiOverSr)
                        val whistle = sin(phaseTurbo) * 0.14
                        val afterburner = lpNoise * 0.26
                        mix += (whistle + afterburner) * 0.34
                    }
                }

                // Impact-scaled Collision metallic crunch + sub-bass thud
                if (collisionEnv > 0.004) {
                    val white = rng.nextDouble(-1.0, 1.0)
                    collisionPhase = wrapPhase(collisionPhase + (95.0 + collisionEnv * 140.0) * twoPiOverSr)
                    val thud = sin(collisionPhase) * 0.65
                    val crunch = white * 0.55
                    mix += (thud + crunch) * collisionEnv * 0.48
                    collisionEnv *= 0.99935
                }

                // Crisp UI SFX chime (click / confirm / unlock / countdown)
                if (uiEnv > 0.004) {
                    val curFreq = uiFreqEnd + (uiFreqStart - uiFreqEnd) * uiEnv
                    phaseUi = wrapPhase(phaseUi + curFreq * twoPiOverSr)
                    val tone = sin(phaseUi) * 0.72 + sin(phaseUi * 2.0) * 0.28
                    mix += tone * uiEnv * 0.26
                    uiEnv = (uiEnv - uiDecayRate).coerceAtLeast(0.0)
                }
            }

            // 2. Procedural 120 BPM Neon Synthwave Background Music
            if (playMusic) {
                val step = ((musicSampleClock / samplesPerStep) % 32L).toInt()
                val stepFrac = (musicSampleClock % samplesPerStep).toDouble() / samplesPerStep.toDouble()
                musicSampleClock++

                // D-minor synthwave chord progression: Dm -> Bb -> F -> C (8 steps per bar)
                val bar = step / 8
                val bassFreq = BASS_PROGRESSION_HZ[bar]
                val arpFreq = ARP_NOTES_HZ[bar][step % 4]
                val padFreq = PAD_NOTES_HZ[bar]

                // Driving 16th-note sawtooth sub-bassline
                phaseMusicBass = wrapPhase(phaseMusicBass + bassFreq * twoPiOverSr)
                val bassEnv = exp(-stepFrac * 3.2)
                val bassWave = (((phaseMusicBass / PI) - 1.0) * 0.55 + sin(phaseMusicBass) * 0.45) * bassEnv

                // Shimmering neon arpeggio (brighter & fuller during high-speed racing)
                phaseMusicArp = wrapPhase(phaseMusicArp + arpFreq * twoPiOverSr)
                val arpEnv = exp(-stepFrac * 4.5)
                val arpWave = sin(phaseMusicArp) * arpEnv * if (racing) 0.42 else 0.24

                // Warm lush synthwave pad
                phaseMusicPad = wrapPhase(phaseMusicPad + padFreq * twoPiOverSr)
                val padWave = (sin(phaseMusicPad) + sin(phaseMusicPad * 1.498) * 0.5) * 0.16

                // Punchy 4-on-the-floor kick drum on beats (every 4th 16th-note step) while racing
                var kick = 0.0
                if (racing && (step % 4 == 0)) {
                    val kickEnv = exp(-stepFrac * 9.5)
                    val kickFreq = 44.0 + 96.0 * exp(-stepFrac * 18.0)
                    kick = sin(stepFrac * kickFreq * 0.45) * kickEnv * 0.52
                }

                val musicLevel = if (racing) 0.16 + 0.04 * spdNorm else 0.13
                mix += (bassWave * 0.46 + arpWave + padWave + kick) * musicLevel
            }

            val clamped = mix.coerceIn(-0.95, 0.95)
            outBuffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun wrapPhase(p: Double): Double {
        var r = p
        val twoPi = 2.0 * PI
        if (r >= twoPi) r -= twoPi
        return r
    }

    private fun runAudioLoop() {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 2048))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.w("EngineAudio", "AudioTrack unavailable: ${e.message}")
            return
        }

        val chunkSize = 512
        val samples = ShortArray(chunkSize)

        try {
            track.play()
            while (running.get()) {
                renderSamples(samples, sampleRate)
                track.write(samples, 0, chunkSize)
            }
        } catch (_: InterruptedException) {
            // Normal shutdown
        } catch (e: Exception) {
            Log.w("EngineAudio", "Audio loop terminated: ${e.message}")
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        // D2, Bb1, F2, C2 bass roots
        private val BASS_PROGRESSION_HZ = doubleArrayOf(73.42, 58.27, 87.31, 65.41)
        // Lush synthwave pad roots
        private val PAD_NOTES_HZ = doubleArrayOf(293.66, 233.08, 349.23, 261.63)
        // Neon arpeggio patterns per bar
        private val ARP_NOTES_HZ = arrayOf(
            doubleArrayOf(293.66, 349.23, 440.00, 587.33), // Dm
            doubleArrayOf(233.08, 293.66, 349.23, 466.16), // Bb
            doubleArrayOf(349.23, 440.00, 523.25, 698.46), // F
            doubleArrayOf(261.63, 329.63, 392.00, 523.25)  // C
        )
    }
}
