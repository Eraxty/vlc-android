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
        CINEMATIC_SWELL,
        EMOTIONAL_SWELL,
        AFTERMATH,
        AFTERMATH_DEBRIS,
        COLLAPSE,
        TICKING,
        JUMPSCARE,
        TENSION_BUILD,
        GUNSHOT,
        EXPLOSION,
        RUMBLE,
        SHOCKWAVE,
        HEARTBEAT,
        FOOTSTEP,
        WHOOSH,
        BASS_DROP,
        SILENCE,
        HEAVYFOOTSTEP,
        TENSIONPULSE,
        METALLIC_HIT,
        ACCELERATION,
        POWERLOSS,
        DISTANT_THUNDER
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


    
///////////////////////////////// libs for vibration templates/////////////////
    fun cinematicSwell(
    vibrator: Vibrator,
    riseIntensity: Float = 0.6f,
    peakIntensity: Float = 0.8f,
    fallIntensity: Float = 0.4f,
    peakDelayMs: Int = 60
    ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                riseIntensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                peakIntensity,
                peakDelayMs
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                fallIntensity,
                40
            )
            .compose()
        )
    }

    fun emotionalSwell(vibrator: Vibrator, intensity: Float = 0.5f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                intensity * 0.6f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                intensity,
                120
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                intensity * 0.2f,
                150
            )
            .compose()
    )
    }


    fun aftermath(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                0.2f,
                200
            )
            .compose()
    )
    }

    fun aftermathDebris(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                0.3f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                0.2f,
                120
            )
            .compose()
    )
    }


    fun heartbeat(vibrator: Vibrator, intensity: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                intensity * 0.5f,
                120
            )
            .compose()
    )
    }

    fun collapse(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                0.8f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                0.6f,
                60
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                0.5f,
                120
            )
            .compose()
    )
    }

    fun ticking(vibrator: Vibrator, intensity: Float = 0.4f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                intensity
            )
            .compose()
    )
    }

    fun whoosh(vibrator: Vibrator, intensity: Float = 0.6f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val i = intensity.coerceIn(0.3f, 0.8f)

    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                i * 0.7f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                i,
                30
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                i * 0.5f,
                40
            )
            .compose()
    )
}


    fun jumpScare(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                1f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                0.8f,
                10
            )
            .compose()
    )
    }

    fun tensionBuild(vibrator: Vibrator, intensity: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                intensity * 0.6f,
                30
            )
            .compose()
    )
    }

    fun rumble(vibrator: Vibrator, intensity: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                intensity.coerceIn(0.3f, 0.7f)
            )
            .compose()
    )
    }

    fun gunshot(vibrator: Vibrator, intensity: Float = 0.9f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                0.8f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                intensity,
                10
            )
            .compose()
    )
    }

    fun explosion(vibrator: Vibrator, intensity: Float = 1f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                0.7f,
                20
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                0.5f,
                80
            )
            .compose()
    )
    }

    fun shockwave(vibrator: Vibrator, intensity: Float = 0.7f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                intensity * 0.8f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                intensity,
                50
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                intensity * 0.4f,
                60
            )
            .compose()
    )
    }

    fun bassDrop(vibrator: Vibrator, intensity: Float = 1f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                intensity * 0.7f,
                40
            )
            .compose()
    )
    }
    fun heavyFootstep(vibrator: Vibrator, intensity: Float = 0.8f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                intensity * 0.5f,
                40
            )
            .compose()
    )
    }
    fun tensionPulse(vibrator: Vibrator, intensity: Float = 0.6f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                intensity * 0.7f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                intensity,
                80
            )
            .compose()
    )
    }

    fun metallicHit(vibrator: Vibrator, intensity: Float = 0.7f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                intensity * 0.6f,
                30
            )
            .compose()
    )
    }
    fun acceleration(vibrator: Vibrator, intensity: Float = 0.7f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                intensity * 0.6f
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                intensity,
                100
            )
            .compose()
    )
    }

    fun powerLoss(vibrator: Vibrator, intensity: Float = 0.6f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                intensity * 0.5f,
                30
            )
            .compose()
    )
    }

    fun distantThunder(vibrator: Vibrator, intensity: Float = 0.4f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                intensity
            )
            .addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                intensity * 0.3f,
                120
            )
            .compose()
    )
    }

///////////////////////////// /////




    fun loadSrtText(srtText: String) {
        synchronized(cues) {
            cues.clear()
            val lines = srtText.lines()
            android.util.Log.d("VibeSubManager", "loadSrtText: total lines = ${lines.size}")
            var idx = 0
     
            while (idx < lines.size && lines[idx].isBlank()) idx++

            var vibEnabled = false

      
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
         
                val stereoMatch = stereoRegex.matchEntire(line)
                if (stereoMatch != null ) {
                    val startMs = parseSrtTime(stereoMatch.groupValues[1])
                    val endMs = parseSrtTime(stereoMatch.groupValues[2])
                    val tags = stereoMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: "SILENCE"
                    val tagStr = stereoMatch.groupValues[3]
                                .takeIf { it.isNotEmpty() }
                                ?: "SILENCE"
                    val params = stereoMatch.groups["params"]
                                    ?.value
                                    ?.split(',')
                                    ?.map { it.trim().toFloat() }
                                    ?: emptyList()
                    
                    
                    if (endMs > startMs) {
                       cues.add(VibCue(
                       startMs = startMs,
                       endMs = endMs,
                       tag = VibTag.valueOf(tagStr),
                       params = params
                        ))
                    }
                    continue
                }
                
            }
            cues.sortBy { it.startMs }

            android.util.Log.d(
                "VibeSubManager",
                "Loaded ${cues.size} vibration cues"
            )
        }
    }


    fun loadSrtFromUri(uri: Uri) {
        val text = try {
            context.applicationContext.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
        } catch (e: Exception) {
            null
        }
        text?.let { loadSrtText(it) }
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

    private fun playPattern(cue:VibCue){

    val p = cue.params

    when (cue.tag) {

        VibTag.CINEMATIC_SWELL -> {
            cinematicSwell(
                vibrator,
                riseIntensity = p.getOrElse(0) { 0.6f },
                peakIntensity = p.getOrElse(1) { 0.8f },
                fallIntensity = p.getOrElse(2) { 0.4f },
                peakDelayMs   = p.getOrElse(3) { 60f }.toInt()
            )
        }

        VibTag.EMOTIONAL_SWELL -> {
            emotionalSwell(
                vibrator,
                intensity = p.getOrElse(0) { 0.5f }
            )
        }

        VibTag.EXPLOSION -> {
            explosion(
                vibrator,
                intensity = p.getOrElse(0) { 1f }
            )
        }

        VibTag.SHOCKWAVE -> {
            shockwave(
                vibrator,
                intensity = p.getOrElse(0) { 0.7f }
            )
        }

        VibTag.BASS_DROP -> {
            bassDrop(
                vibrator,
                intensity = p.getOrElse(0) { 1f }
            )
        }

        VibTag.RUMBLE -> {
            rumble(
                vibrator,
                intensity = p.getOrElse(0) { 0.5f }
            )
        }

        VibTag.HEARTBEAT -> {
            heartbeat(
                vibrator,
                intensity = p.getOrElse(0) { 0.6f }
            )
        }

        VibTag.TENSION_BUILD -> {
            tensionBuild(
                vibrator,
                intensity = p.getOrElse(0) { 0.6f }
            )
        }

        VibTag.TENSIONPULSE -> {
            tensionPulse(
                vibrator,
                intensity = p.getOrElse(0) { 0.6f }
            )
        }

        VibTag.JUMPSCARE -> {
            jumpScare(vibrator)
        }

        VibTag.GUNSHOT -> {
            gunshot(
                vibrator,
                intensity = p.getOrElse(0) { 0.9f }
            )
        }

        VibTag.FOOTSTEP,
        VibTag.HEAVYFOOTSTEP -> {
            heavyFootstep(
                vibrator,
                intensity = p.getOrElse(0) { 0.8f }
            )
        }

        VibTag.METALLIC_HIT -> {
            metallicHit(
                vibrator,
                intensity = p.getOrElse(0) { 0.7f }
            )
        }

        VibTag.COLLAPSE -> {
            collapse(vibrator)
        }

        VibTag.AFTERMATH -> {
            aftermath(vibrator)
        }

        VibTag.AFTERMATH_DEBRIS -> {
            aftermathDebris(vibrator)
        }

        VibTag.WHOOSH -> {
            whoosh(
                vibrator,
                intensity = p.getOrElse(0) { 0.6f }
            )
        }

        VibTag.ACCELERATION -> {
            acceleration(
                vibrator,
                intensity = p.getOrElse(0) { 0.7f }
            )
        }

        VibTag.POWERLOSS -> {
            powerLoss(
                vibrator,
                intensity = p.getOrElse(0) { 0.6f }
            )
        }

        VibTag.TICKING -> {
            ticking(
                vibrator,
                intensity = p.getOrElse(0) { 0.4f }
            )
        }

        VibTag.DISTANT_THUNDER -> {
            distantThunder(
                vibrator,
                intensity = p.getOrElse(0) { 0.4f }
            )
        }

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
