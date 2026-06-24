package org.videolan.vlc.media
import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.*


class VibrationSubtitleManager(
    private val context: Context,
    private val getPlayerTimeMs: () -> Long,
    private val isPlaying: () -> Boolean,
    private val onNeedUiUpdate: (() -> Unit)? = null
) {
    private val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var audioTrack: android.media.AudioTrack? = null
    private var audioGeneratorJob: Job? = null

    private var pollIntervalMs: Long = 100L
    private var waveformStepMs: Long = 50L

    private val running = AtomicBoolean(false)
    private var pollRunnable: Runnable? = null
    private var activeCue: VibCue? = null
    private val cues = mutableListOf<VibCue>()
    private var activeJob: Job? = null

    enum class VibTag {
        // Impacts
        SMALL_CLICK,
        CLICK,
        HEAVY_CLICK,
        TAP,
        DOUBLE_TAP,
        TRIPLE_TAP,

        // Explosions
        SMALL_BOOM,
        BOOM,
        BIG_BOOM,
        MASSIVE_EXPLOSION,
        SHOCKWAVE,

        // Weapons
        PISTOL_SHOT,
        REVOLVER_SHOT,
        RIFLE_SHOT,
        SHOTGUN_BLAST,
        MACHINE_GUN,
        BURST_FIRE,
        SNIPER_SHOT,
        ROCKET_LAUNCH,
        SWORD_CLASH,
        LASER_BLAST,

        // Vehicles
        CAR_HIT,
        CAR_CRASH,
        TRUCK_CRASH,
        AIRPLANE_CRASH,
        HELICOPTER_CRASH,
        TRAIN_IMPACT,
        ENGINE_IDLE,
        ENGINE_REVVING,

        // Human
        HEARTBEAT_SLOW,
        HEARTBEAT_NORMAL,
        HEARTBEAT_FAST,
        PANIC_HEARTBEAT,
        BREATHING_SLOW,
        BREATHING_FAST,
        FOOTSTEP_LIGHT,
        FOOTSTEP_HEAVY,
        RUNNING,

        // Environment
        THUNDER,
        EARTHQUAKE,
        BUILDING_COLLAPSE,
        ROCK_FALL,
        WATER_SPLASH,
        WAVES,
        WIND_GUST,
        RAIN_DROPLET,

        // Tension
        TENSION_LOW,
        TENSION_MEDIUM,
        TENSION_HIGH,
        SUSPENSE_RISE,
        JUMPSCARE,

        // Technology
        NOTIFICATION_LIGHT,
        NOTIFICATION_HEAVY,
        ERROR,
        SUCCESS,
        POWER_UP,
        POWER_DOWN,
        BATTERY_LOW,
        // Legacy tags (inbuilt functions to improve)
        CINEMATIC_SWELL,
        EMOTIONAL_SWELL,
        AFTERMATH,
        AFTERMATH_DEBRIS,
        COLLAPSE,
        TICKING,
        JUMPSCARE_LEGACY,
        TENSION_BUILD,
        GUNSHOT,
        EXPLOSION_LEGACY,
        RUMBLE,
        SHOCKWAVE_LEGACY,
        HEARTBEAT_LEGACY,
        FOOTSTEP_LEGACY,
        WHOOSH,
        BASS_DROP,
        SILENCE_LEGACY,
        HEAVYFOOTSTEP,
        TENSIONPULSE,
        METALLIC_HIT,
        ACCELERATION,
        POWERLOSS,
        DISTANT_THUNDER

        // Silence / System
        SILENCE
    }

data class VibCue(
    val startMs: Long,
    val endMs: Long,
    val tag: VibTag,
    val params: List<Float> = emptyList()
)
    private val hapticScope = CoroutineScope(
    Dispatchers.Default + SupervisorJob()
)


    
    private fun isCompositionSupported(vararg primitives: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            vibrator.areAllPrimitivesSupported(*primitives)
        } catch (e: Throwable) {
            false
        }
    }

    private fun fallbackVibrate(durationMs: Long, intensity: Float) {
        android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
        val amplitude = (intensity.coerceIn(0f, 1f) * 255).roundToInt().coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            vibrator.vibrate(durationMs)
        }
    }

    // ==========================================
    // IMPACTS
    // ==========================================

    /**
     * SMALL_CLICK: A very light, subtle tactility, like a physical scroll wheel notch.
     */
    fun smallClick(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f)
                    .compose()
            )
        } else {
            fallbackVibrate(10, 0.25f)
        }
    }

    /**
     * CLICK: A crisp, standard physical button press.
     */
    fun click(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .compose()
            )
        } else {
            fallbackVibrate(20, 0.6f)
        }
    }

    /**
     * HEAVY_CLICK: A deep, heavy mechanical switch press.
     */
    fun heavyClick(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f, 5)
                    .compose()
            )
        } else {
            fallbackVibrate(35, 1.0f)
        }
    }

    /**
     * TAP: A soft tap on a glass screen, clean and distinct.
     */
    fun tap(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f)
                    .compose()
            )
        } else {
            fallbackVibrate(15, 0.4f)
        }
    }

    /**
     * DOUBLE_TAP: Two quick taps in rapid succession.
     */
    fun doubleTap(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 80)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 60, 15), intArrayOf(0, 150, 0, 150), -1))
            } else {
                fallbackVibrate(90, 0.6f)
            }
        }
    }

    /**
     * TRIPLE_TAP: Three rapid taps, like a fast notification alert.
     */
    fun tripleTap(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 70)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 140)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 58, 12, 58, 12), intArrayOf(0, 120, 0, 120, 0, 120), -1))
            } else {
                fallbackVibrate(150, 0.5f)
            }
        }
    }

    // ==========================================
    // EXPLOSIONS
    // ==========================================

    /**
     * SMALL_BOOM: A mild, low-frequency rumble, like a distant detonation.
     */
    fun smallBoom(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f)
                    .compose()
            )
        } else {
            fallbackVibrate(80, 0.45f)
        }
    }

    /**
     * BOOM: A sudden, sharp blast, strong impact followed by a short decay.
     */
    fun boom(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.5f, 20)
                    .compose()
            )
        } else {
            fallbackVibrate(150, 0.8f)
        }
    }

    /**
     * BIG_BOOM: A massive shock wave followed by rumbling.
     */
    fun bigBoom(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 40)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.7f, 80)
                    .compose()
            )
        } else {
            fallbackVibrate(300, 0.95f)
        }
    }

    /**
     * MASSIVE_EXPLOSION: A violent blast that shakes the device with heavy vibration.
     */
    fun massiveExplosion(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 1.0f, 50)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.9f, 150)
                    .compose()
            )
        } else {
            fallbackVibrate(500, 1.0f)
        }
    }

    /**
     * SHOCKWAVE: A rapid build-up and extremely sharp release wave, pushing outward.
     */
    fun shockwave(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.4f, 120)
                    .compose()
            )
        } else {
            fallbackVibrate(180, 0.8f)
        }
    }

    // ==========================================
    // WEAPONS
    // ==========================================

    /**
     * PISTOL_SHOT: A sharp, crisp, instantaneous discharge.
     */
    fun pistolShot(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
                    .compose()
            )
        } else {
            fallbackVibrate(40, 0.8f)
        }
    }

    /**
     * REVOLVER_SHOT: A heavier, slightly longer kickback than a pistol.
     */
    fun revolverShot(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 5)
                    .compose()
            )
        } else {
            fallbackVibrate(60, 0.9f)
        }
    }

    /**
     * RIFLE_SHOT: A powerful, high-velocity kick with a sharp snap.
     */
    fun rifleShot(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f, 10)
                    .compose()
            )
        } else {
            fallbackVibrate(75, 0.95f)
        }
    }

    /**
     * SHOTGUN_BLAST: An overwhelming, wide-spread double-kick of raw power.
     */
    fun shotgunBlast(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 20)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f, 50)
                    .compose()
            )
        } else {
            fallbackVibrate(140, 1.0f)
        }
    }

    /**
     * MACHINE_GUN: Rapid, repeating light strikes with very short gaps.
     */
    fun machineGun(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .compose()
            )
        } else {
            fallbackVibrate(25, 0.65f)
        }
    }

    /**
     * BURST_FIRE: A quick burst of three rounds in rapid succession.
     */
    fun burstFire(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 60)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 120)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 25, 35, 25, 35, 25), intArrayOf(0, 200, 0, 200, 0, 200), -1))
            } else {
                fallbackVibrate(180, 0.8f)
            }
        }
    }

    /**
     * SNIPER_SHOT: A massive single impact followed by a slow, fading recoil wave.
     */
    fun sniperShot(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 5)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.5f, 40)
                    .compose()
            )
        } else {
            fallbackVibrate(180, 1.0f)
        }
    }

    /**
     * ROCKET_LAUNCH: A heavy backward push (whoosh) followed by a low-frequency hum.
     */
    fun rocketLaunch(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_SPIN
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.6f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.8f, 80)
                    .compose()
            )
        } else {
            fallbackVibrate(250, 0.75f)
        }
    }

    /**
     * SWORD_CLASH: A bright, sharp metallic ringing sensation.
     */
    fun swordClash(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 40)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 25, 10), intArrayOf(0, 200, 0, 150), -1))
            } else {
                fallbackVibrate(50, 0.8f)
            }
        }
    }

    /**
     * LASER_BLAST: A futuristic charging snap, high pitch to quick dissipation.
     */
    fun laserBlast(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 60)
                    .compose()
            )
        } else {
            fallbackVibrate(100, 0.7f)
        }
    }

    // ==========================================
    // VEHICLES
    // ==========================================

    /**
     * CAR_HIT: A medium, solid physical impact.
     */
    fun carHit(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 10)
                    .compose()
            )
        } else {
            fallbackVibrate(90, 0.65f)
        }
    }

    /**
     * CAR_CRASH: A violent crush of glass, metal, and heavy impact.
     */
    fun carCrash(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.8f, 30)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 100)
                    .compose()
            )
        } else {
            fallbackVibrate(400, 0.85f)
        }
    }

    /**
     * TRUCK_CRASH: A massive, sustained, heavy-weight crushing impact.
     */
    fun truckCrash(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_SPIN
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 80)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 1.0f, 160)
                    .compose()
            )
        } else {
            fallbackVibrate(600, 1.0f)
        }
    }

    /**
     * AIRPLANE_CRASH: An catastrophic, earth-shaking destruction sequence.
     */
    fun airplaneCrash(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_THUD
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 1.0f, 200)
                    .compose()
            )
        } else {
            fallbackVibrate(1000, 1.0f)
        }
    }

    /**
     * HELICOPTER_CRASH: Rhythmic rotor thuds ending in a final heavy explosion.
     */
    fun helicopterCrash(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f, 200)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 300)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 40, 60, 150), intArrayOf(0, 150, 0, 150, 0, 150, 0, 255), -1))
            } else {
                fallbackVibrate(800, 0.8f)
            }
        }
    }

    /**
     * TRAIN_IMPACT: An unstoppable, heavy metal-on-metal collision.
     */
    fun trainImpact(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_SPIN
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 20)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.9f, 120)
                    .compose()
            )
        } else {
            fallbackVibrate(750, 0.9f)
        }
    }

    /**
     * ENGINE_IDLE: A low-intensity, rhythmic hum representing a running motor.
     */
    fun engineIdle(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.2f)
                    .compose()
            )
        } else {
            fallbackVibrate(15, 0.15f)
        }
    }

    /**
     * ENGINE_REVVING: A smooth sweep upwards in intensity, mimicking acceleration.
     */
    fun engineRevving(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_SPIN
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.5f, 100)
                    .compose()
            )
        } else {
            fallbackVibrate(300, 0.7f)
        }
    }

    // ==========================================
    // HUMAN
    // ==========================================

    /**
     * HEARTBEAT_SLOW: Rhythmic, slow double-pulses (lub-dub) of a calm heart.
     */
    fun heartbeatSlow(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.4f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.2f, 120)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 120, 30), intArrayOf(0, 80, 0, 40), -1))
            } else {
                fallbackVibrate(200, 0.3f)
            }
        }
    }

    /**
     * HEARTBEAT_NORMAL: Rhythmic double-pulses of a healthy heart.
     */
    fun heartbeatNormal(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f, 100)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 100, 30), intArrayOf(0, 110, 0, 50), -1))
            } else {
                fallbackVibrate(180, 0.45f)
            }
        }
    }

    /**
     * HEARTBEAT_FAST: Rapid double-pulses, indicating excitement or physical exertion.
     */
    fun heartbeatFast(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 80)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 25), intArrayOf(0, 160, 0, 70), -1))
            } else {
                fallbackVibrate(150, 0.6f)
            }
        }
    }

    /**
     * PANIC_HEARTBEAT: Loud, heavy, extremely fast double-pulses.
     */
    fun panicHeartbeat(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 60)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 30), intArrayOf(0, 220, 0, 100), -1))
            } else {
                fallbackVibrate(130, 0.85f)
            }
        }
    }

    /**
     * BREATHING_SLOW: Gentle rise and fall, mimicking inhalation/exhalation.
     */
    fun breathingSlow(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.3f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.15f, 200)
                    .compose()
            )
        } else {
            fallbackVibrate(400, 0.2f)
        }
    }

    /**
     * BREATHING_FAST: Rapid, shallow rise and fall, indicating hyperventilation.
     */
    fun breathingFast(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.3f, 120)
                    .compose()
            )
        } else {
            fallbackVibrate(200, 0.4f)
        }
    }

    /**
     * FOOTSTEP_LIGHT: A light heel-toe tap on a hard surface.
     */
    fun footstepLight(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                    .compose()
            )
        } else {
            fallbackVibrate(15, 0.3f)
        }
    }

    /**
     * FOOTSTEP_HEAVY: A heavy, solid stamp of a boot.
     */
    fun footstepHeavy(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                    .compose()
            )
        } else {
            fallbackVibrate(40, 0.7f)
        }
    }

    /**
     * RUNNING: Rhythmic, rapid impacts repeating.
     */
    fun running(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f)
                    .compose()
            )
        } else {
            fallbackVibrate(35, 0.55f)
        }
    }

    // ==========================================
    // ENVIRONMENT
    // ==========================================

    /**
     * THUNDER: Distant rolling rumble that crests and decays slowly.
     */
    fun thunder(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.4f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.5f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.3f, 300)
                    .compose()
            )
        } else {
            fallbackVibrate(500, 0.4f)
        }
    }

    /**
     * EARTHQUAKE: Sustained, intense chaotic shaking.
     */
    fun earthquake(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_THUD
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.9f, 200)
                    .compose()
            )
        } else {
            fallbackVibrate(750, 0.8f)
        }
    }

    /**
     * BUILDING_COLLAPSE: Massive crashes interspersed with minor rubble hits.
     */
    fun buildingCollapse(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 80)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 150)
                    .compose()
            )
        } else {
            fallbackVibrate(800, 0.85f)
        }
    }

    /**
     * ROCK_FALL: A barrage of sudden, distinct impacts of varying sizes.
     */
    fun rockFall(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 90)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 180)
                    .compose()
            )
        } else {
            fallbackVibrate(400, 0.5f)
        }
    }

    /**
     * WATER_SPLASH: A soft, dispersing expansion wave.
     */
    fun waterSplash(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.4f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.2f, 80)
                    .compose()
            )
        } else {
            fallbackVibrate(100, 0.3f)
        }
    }

    /**
     * WAVES: Rhythmic rolling swells like ocean tides.
     */
    fun waves(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.3f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.2f, 200)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.1f, 400)
                    .compose()
            )
        } else {
            fallbackVibrate(800, 0.25f)
        }
    }

    /**
     * WIND_GUST: A long, gentle sweeping breeze.
     */
    fun windGust(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.3f)
                    .compose()
            )
        } else {
            fallbackVibrate(600, 0.15f)
        }
    }

    /**
     * RAIN_DROPLET: Tiny, rapid, isolated impacts on a surface.
     */
    fun rainDroplet(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.2f)
                    .compose()
            )
        } else {
            fallbackVibrate(8, 0.15f)
        }
    }

    // ==========================================
    // TENSION
    // ==========================================

    /**
     * TENSION_LOW: An ominous, low-intensity pulse.
     */
    fun tensionLow(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.3f)
                    .compose()
            )
        } else {
            fallbackVibrate(100, 0.2f)
        }
    }

    /**
     * TENSION_MEDIUM: A more pronounced, pulsing tension cue.
     */
    fun tensionMedium(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.2f, 100)
                    .compose()
            )
        } else {
            fallbackVibrate(180, 0.4f)
        }
    }

    /**
     * TENSION_HIGH: Intense, rapid, heavy pulsing.
     */
    fun tensionHigh(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f, 50)
                    .compose()
            )
        } else {
            fallbackVibrate(220, 0.7f)
        }
    }

    /**
     * SUSPENSE_RISE: A long, swelling crescendo that builds to a peak.
     */
    fun suspenseRise(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_SPIN
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.6f, 150)
                    .compose()
            )
        } else {
            fallbackVibrate(400, 0.65f)
        }
    }

    /**
     * JUMPSCARE: A violent, sudden shock.
     */
    fun jumpScare(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 5)
                    .compose()
            )
        } else {
            fallbackVibrate(120, 1.0f)
        }
    }

    // ==========================================
    // TECHNOLOGY
    // ==========================================

    /**
     * NOTIFICATION_LIGHT: A friendly, clean double-click.
     */
    fun notificationLight(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f, 100)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 85, 10), intArrayOf(0, 100, 0, 80), -1))
            } else {
                fallbackVibrate(110, 0.4f)
            }
        }
    }

    /**
     * NOTIFICATION_HEAVY: A strong, urgent double-buzz.
     */
    fun notificationHeavy(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 120)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 70, 50), intArrayOf(0, 200, 0, 200), -1))
            } else {
                fallbackVibrate(170, 0.8f)
            }
        }
    }

    /**
     * ERROR: A harsh triple buzz, signaling failure.
     */
    fun error(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f, 200)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60, 40, 60), intArrayOf(0, 220, 0, 220, 0, 220), -1))
            } else {
                fallbackVibrate(260, 0.9f)
            }
        }
    }

    /**
     * SUCCESS: A satisfying ascending chime.
     */
    fun success(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 80)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 65, 20), intArrayOf(0, 100, 0, 180), -1))
            } else {
                fallbackVibrate(100, 0.6f)
            }
        }
    }

    /**
     * POWER_UP: Ramping frequency building power.
     */
    fun powerUp(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_SPIN
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.9f, 100)
                    .compose()
            )
        } else {
            fallbackVibrate(350, 0.75f)
        }
    }

    /**
     * POWER_DOWN: Decrescendo winding down.
     */
    fun powerDown(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.4f, 100)
                    .compose()
            )
        } else {
            fallbackVibrate(300, 0.65f)
        }
    }

    /**
     * BATTERY_LOW: A distinctive warning double-pulse with a pause.
     */
    fun batteryLow(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isCompositionSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.4f, 150)
                    .compose()
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("VibeDebug", "Vibrator.vibrate invoked")
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 45, 105, 30), intArrayOf(0, 150, 0, 90), -1))
            } else {
                fallbackVibrate(180, 0.55f)
            }
        }
    }

    //




    fun loadSrtText(srtText: String, path: String = "Unknown") {
        synchronized(cues) {
            cues.clear()
            val lines = srtText.lines()
            android.util.Log.d("VibeSubManager", "loadSrtText: total lines = ${lines.size}")
            var idx = 0
     
            while (idx < lines.size && lines[idx].isBlank()) idx++

            var vibEnabled = true

            while (idx < lines.size && lines[idx].trim().startsWith("//")) {
                val header = lines[idx].trim().removePrefix("//").trim()
                android.util.Log.d("VibeSubManager", "Found header: $header")
                
                when {
                    header.startsWith("VIB:", ignoreCase = true) -> {
                        vibEnabled = header.substringAfter(":").trim().equals("yes", ignoreCase = true)
                        android.util.Log.d("VibeSubManager", "VIB header found, enabled = $vibEnabled")
                    }
                }
                idx++
            }

            if (!vibEnabled) {
                android.util.Log.d("VibeSubManager", "Vibration not enabled in SRT, skipping")
                return
            }
            

        
            // Stereo format: HH:MM:SS,mmm --> HH:MM:SS,mmm [tags] [params]
            val stereoRegex = Regex("""^(?<start>\d{2}:\d{2}:\d{2},\d{3})\s*-->\s*(?<end>\d{2}:\d{2}:\d{2},\d{3})(?:\s+(?<tag>[A-Z_]+)(?:\s+(?<params>(?:-?\d+(?:\.\d+)?)(?:\s*,\s*-?\d+(?:\.\d+)?){0,3}))?)?$""")
            for (line in lines) {
         
                val stereoMatch = stereoRegex.matchEntire(line.trim())
                if (stereoMatch != null ) {
                    val startMs = parseSrtTime(stereoMatch.groupValues[1])
                    val endMs = parseSrtTime(stereoMatch.groupValues[2])
                    var tagStr = stereoMatch.groups["tag"]?.value
                                ?.takeIf { it.isNotEmpty() }
                                ?: "SILENCE"
                    
                    tagStr = when (tagStr) {
                        "CINEMATIC_SWELL" -> "SUSPENSE_RISE"
                        "EMOTIONAL_SWELL" -> "TENSION_MEDIUM"
                        "AFTERMATH" -> "NOTIFICATION_LIGHT"
                        "AFTERMATH_DEBRIS" -> "ROCK_FALL"
                        "COLLAPSE" -> "BUILDING_COLLAPSE"
                        "TICKING" -> "HEARTBEAT_SLOW"
                        "TENSION_BUILD" -> "SUSPENSE_RISE"
                        "GUNSHOT" -> "RIFLE_SHOT"
                        "EXPLOSION" -> "BOOM"
                        "RUMBLE" -> "EARTHQUAKE"
                        "HEARTBEAT" -> "HEARTBEAT_NORMAL"
                        "FOOTSTEP" -> "FOOTSTEP_LIGHT"
                        "WHOOSH" -> "WIND_GUST"
                        "BASS_DROP" -> "BIG_BOOM"
                        "HEAVYFOOTSTEP" -> "FOOTSTEP_HEAVY"
                        "TENSIONPULSE" -> "TENSION_MEDIUM"
                        "METALLIC_HIT" -> "CLICK"
                        "ACCELERATION" -> "ENGINE_REVVING"
                        "POWERLOSS" -> "POWER_DOWN"
                        "DISTANT_THUNDER" -> "THUNDER"
                        else -> tagStr
                    }
                    
                    val params = stereoMatch.groups["params"]
                                    ?.value
                                    ?.split(',')
                                    ?.mapNotNull { it.trim().toFloatOrNull() }
                                    ?: emptyList()
                    
                    val tag = try {
                        VibTag.valueOf(tagStr)
                    } catch (e: IllegalArgumentException) {
                        VibTag.SILENCE
                    }
                    
                    if (endMs > startMs) {
                       cues.add(VibCue(
                       startMs = startMs,
                       endMs = endMs,
                       tag = tag,
                       params = params
                        ))
                    }
                    continue
                }
                
            }
            cues.sortBy { it.startMs }

            android.util.Log.d("VibeDebug", "Subtitle path: $path")
            android.util.Log.d("VibeDebug", "Subtitle contents length: ${srtText.length}")
            android.util.Log.d("VibeDebug", "Cues parsed: ${cues.size}")
            if (cues.isNotEmpty()) {
                android.util.Log.d("VibeDebug", "First parsed cue: ${cues[0]}")
            } else {
                android.util.Log.d("VibeDebug", "First parsed cue: None")
            }
        }
    }


    fun loadSrtFromUri(uri: Uri) {
        val text = try {
            context.applicationContext.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
        } catch (e: Exception) {
            null
        }
        text?.let { loadSrtText(it, uri.toString()) }
    }


    fun start() {
        if (running.getAndSet(true)) {
            android.util.Log.d("VibeSubManager", "start() called but already running")
            return 
        }
        android.util.Log.d("VibeSubManager", "Starting vibration manager with ${cues.size} cues")
        pollRunnable = object : Runnable {
            override fun run() {
                try {
                    poll()
                } finally {
                   
                    if (running.get()) handler.postDelayed(this, pollIntervalMs)
                }
            }
        }
    
        handler.post(pollRunnable!!)
    }


    fun stop() {
        if (!running.getAndSet(false)) return
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
        cancelActiveVibration()
    }


    fun syncNow() {
        handler.post { poll() }
    }


    fun setPollInterval(ms: Long) {
        pollIntervalMs = max(20L, ms)
    }

    fun setWaveformStepMs(ms: Long) {
        waveformStepMs = max(10L, ms)
    }


   
    
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t



    private fun parseSrtTime(t: String): Long {
        val parts = t.split(':')
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secParts = parts[2].split(',')
        val seconds = secParts[0].toLongOrNull() ?: 0L
        val ms = secParts.getOrNull(1)?.toLongOrNull() ?: 0L
        return (((hours * 60 + minutes) * 60) + seconds) * 1000 + ms
    }

    private fun poll() {
        android.util.Log.d("VibeDebug", "Poll running")
      
        if (!shouldAllowVibrate()) {
            cancelActiveVibration()
            return
        }

        val time = try { getPlayerTimeMs() } catch (e: Exception) { 0L }
        val playing = try { isPlaying() } catch (e: Exception) { false }

       
        val cue = synchronized(cues) {
          
            var chosen: VibCue? = null
            for (c in cues) {
                if (c.startMs <= time && c.endMs > time) {
                    chosen = c
                    break
                }
                if (c.startMs > time) break
            }
            chosen
        }

        if (cue != null && playing) {
            android.util.Log.d("VibeDebug", "Active cue found")
            val switched = activeCue?.let { it.startMs != cue.startMs || it.endMs != cue.endMs } ?: true
            if (switched) {
                cancelActiveVibration()
                activeCue = cue
                startVibrationForCue(cue)
                onNeedUiUpdate?.invoke()
            }
         
        } else {
       
            if (activeCue != null) {
                cancelActiveVibration()
                activeCue = null
                onNeedUiUpdate?.invoke()
            }
        }
    }

    private fun playPattern(cue: VibCue) {
        android.util.Log.d("VibeDebug", "playPattern called")
        val p = cue.params

        when (cue.tag) {
            VibTag.SMALL_CLICK -> smallClick(vibrator)
            VibTag.CLICK -> click(vibrator)
            VibTag.HEAVY_CLICK -> heavyClick(vibrator)
            VibTag.TAP -> tap(vibrator)
            VibTag.DOUBLE_TAP -> doubleTap(vibrator)
            VibTag.TRIPLE_TAP -> tripleTap(vibrator)

            VibTag.SMALL_BOOM -> smallBoom(vibrator)
            VibTag.BOOM -> boom(vibrator)
            VibTag.BIG_BOOM -> bigBoom(vibrator)
            VibTag.MASSIVE_EXPLOSION -> massiveExplosion(vibrator)
            VibTag.SHOCKWAVE -> shockwave(vibrator)

            VibTag.PISTOL_SHOT -> pistolShot(vibrator)
            VibTag.REVOLVER_SHOT -> revolverShot(vibrator)
            VibTag.RIFLE_SHOT -> rifleShot(vibrator)
            VibTag.SHOTGUN_BLAST -> shotgunBlast(vibrator)
            VibTag.MACHINE_GUN -> machineGun(vibrator)
            VibTag.BURST_FIRE -> burstFire(vibrator)
            VibTag.SNIPER_SHOT -> sniperShot(vibrator)
            VibTag.ROCKET_LAUNCH -> rocketLaunch(vibrator)
            VibTag.SWORD_CLASH -> swordClash(vibrator)
            VibTag.LASER_BLAST -> laserBlast(vibrator)

            VibTag.CAR_HIT -> carHit(vibrator)
            VibTag.CAR_CRASH -> carCrash(vibrator)
            VibTag.TRUCK_CRASH -> truckCrash(vibrator)
            VibTag.AIRPLANE_CRASH -> airplaneCrash(vibrator)
            VibTag.HELICOPTER_CRASH -> helicopterCrash(vibrator)
            VibTag.TRAIN_IMPACT -> trainImpact(vibrator)
            VibTag.ENGINE_IDLE -> engineIdle(vibrator)
            VibTag.ENGINE_REVVING -> engineRevving(vibrator)

            VibTag.HEARTBEAT_SLOW -> heartbeatSlow(vibrator)
            VibTag.HEARTBEAT_NORMAL -> heartbeatNormal(vibrator)
            VibTag.HEARTBEAT_FAST -> heartbeatFast(vibrator)
            VibTag.PANIC_HEARTBEAT -> panicHeartbeat(vibrator)
            VibTag.BREATHING_SLOW -> breathingSlow(vibrator)
            VibTag.BREATHING_FAST -> breathingFast(vibrator)
            VibTag.FOOTSTEP_LIGHT -> footstepLight(vibrator)
            VibTag.FOOTSTEP_HEAVY -> footstepHeavy(vibrator)
            VibTag.RUNNING -> running(vibrator)

            VibTag.THUNDER -> thunder(vibrator)
            VibTag.EARTHQUAKE -> earthquake(vibrator)
            VibTag.BUILDING_COLLAPSE -> buildingCollapse(vibrator)
            VibTag.ROCK_FALL -> rockFall(vibrator)
            VibTag.WATER_SPLASH -> waterSplash(vibrator)
            VibTag.WAVES -> waves(vibrator)
            VibTag.WIND_GUST -> windGust(vibrator)
            VibTag.RAIN_DROPLET -> rainDroplet(vibrator)

            VibTag.TENSION_LOW -> tensionLow(vibrator)
            VibTag.TENSION_MEDIUM -> tensionMedium(vibrator)
            VibTag.TENSION_HIGH -> tensionHigh(vibrator)
            VibTag.SUSPENSE_RISE -> suspenseRise(vibrator)
            VibTag.JUMPSCARE -> jumpScare(vibrator)

            VibTag.NOTIFICATION_LIGHT -> notificationLight(vibrator)
            VibTag.NOTIFICATION_HEAVY -> notificationHeavy(vibrator)
            VibTag.ERROR -> error(vibrator)
            VibTag.SUCCESS -> success(vibrator)
            VibTag.POWER_UP -> powerUp(vibrator)
            VibTag.POWER_DOWN -> powerDown(vibrator)
            VibTag.BATTERY_LOW -> batteryLow(vibrator)

            VibTag.SILENCE -> {
                android.util.Log.d("VibeSubManager", "Silence cue – no vibration")
            }
        }
    }

private fun startVibrationForCue(cue: VibCue) {

    activeJob?.cancel()

    val DELAY_INTERVAL_MS = 150L

    activeJob = hapticScope.launch {
        while (isActive && getPlayerTimeMs() < cue.endMs) {

            // hop to main ONLY for vibrator
            withContext(Dispatchers.Main) {
                playPattern(cue)
            }

            delay(DELAY_INTERVAL_MS)
        }
    }
}


    // private fun fallbackPatternVibration(cue: VibCue, startAmpNow: Int, remaining: Long) {
    //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //         try {

    //             val avgIntensity = (startAmpNow + ((cue.leftEndIntensity + cue.rightEndIntensity) / 2)) / 2
    //             val intensityRatio = avgIntensity / 255.0
                
               
    //             val cycleMs = 100L
    //             val onTime = (cycleMs * intensityRatio).toLong().coerceIn(10L, cycleMs)
    //             val offTime = cycleMs - onTime
                
    //             val cycles = max(1, (remaining / cycleMs).toInt())
    //             val pattern = mutableListOf<Long>()
    //             val amplitudes = mutableListOf<Int>()
                
        
    //             pattern.add(0)
    //             amplitudes.add(0)
                
    //             for (i in 0 until cycles) {
    //                 pattern.add(onTime)
    //                 amplitudes.add(255)
    //                 if (offTime > 0) {
    //                     pattern.add(offTime)
    //                     amplitudes.add(0) 
    //                 }
    //             }
                
    //             android.util.Log.d("VibeSubManager", "Pattern vibration: intensity=$avgIntensity, onTime=$onTime, offTime=$offTime, cycles=$cycles")
                
    //             val effect = VibrationEffect.createWaveform(pattern.toLongArray(), amplitudes.toIntArray(), -1)
    //             vibrator.vibrate(effect)
    //         } catch (e: Exception) {
    //             android.util.Log.e("VibeSubManager", "Pattern vibration failed", e)
           
    //             try { vibrator.vibrate(remaining) } catch (_: Exception) {}
    //         }
    //     } else {
           
    //         try { vibrator.vibrate(remaining) } catch (_: Exception) {}
    //     }
    // }

private fun cancelActiveVibration() {
    activeJob?.cancel()
    activeJob = null

    handler.post {
        vibrator.cancel()
    }
}

    private fun lerp(a: Int, b: Int, t: Double): Double = a + (b - a) * t

    private fun Double.coerceIn(minVal: Double, maxVal: Double) = max(minVal, min(maxVal, this))

    private fun Int.coerceIn(minVal: Int, maxVal: Int) = max(minVal, min(maxVal, this))

    private fun shouldAllowVibrate(): Boolean {
        try {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
           
            return when (am.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> false
                else -> true
            }
        } catch (e: Exception) {
            return true
        }
    }
}
