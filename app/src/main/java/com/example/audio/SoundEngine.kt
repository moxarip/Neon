package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class SoundEffect {
    UI_CLICK,
    UNLOCK,
    PASS,
    SHOOT_NORMAL,
    SHOOT_CHARGED,
    BALL_BOUNCE,
    BOOST,
    TACKLE,
    SAVE,
    GOAL_HORN,
    COUNTDOWN_BEEP,
    WHISTLE
}

class SoundEngine {
    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sfxBuffers = HashMap<SoundEffect, ShortArray>()

    @Volatile
    private var masterVol = 0.85f

    @Volatile
    private var musicVol = 0.65f

    @Volatile
    private var sfxVol = 0.90f

    @Volatile
    private var musicModeArena = false

    private var musicJob: Job? = null
    private var musicTrack: AudioTrack? = null

    init {
        buildAllSfxBuffers()
    }

    fun updateSettings(settings: AppSettings) {
        masterVol = settings.masterVolume.coerceIn(0f, 1f)
        musicVol = settings.musicVolume.coerceIn(0f, 1f)
        sfxVol = settings.sfxVolume.coerceIn(0f, 1f)
        updateMusicGain()
    }

    fun setArenaMusic(inMatch: Boolean) {
        musicModeArena = inMatch
    }

    fun startMusic() {
        if (musicJob?.isActive == true) return
        musicJob = scope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

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
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                null
            } ?: return@launch

            musicTrack = track
            updateMusicGain()
            try {
                track.play()
                val chunkSize = 2048
                val buf = ShortArray(chunkSize)
                var sampleIndex = 0L

                // Original futuristic synthwave progression (D minor -> Bb -> C -> A)
                val menuBass = floatArrayOf(73.42f, 58.27f, 65.41f, 55.00f)
                val arenaArp = floatArrayOf(293.66f, 349.23f, 440.00f, 523.25f, 440.00f, 349.23f, 392.00f, 587.33f)

                while (isActive) {
                    val gain = masterVol * musicVol
                    if (gain <= 0.01f) {
                        delay(80)
                        continue
                    }
                    val tempoHz = if (musicModeArena) 4.4f else 2.8f
                    for (i in 0 until chunkSize) {
                        val t = (sampleIndex + i).toDouble() / sampleRate
                        val beat = (t * tempoHz)
                        val step = beat.toInt()
                        val beatFrac = (beat - step).toFloat()
                        val barIdx = (step / 4) % menuBass.size
                        val bassFreq = menuBass[barIdx]
                        val arpFreq = arenaArp[step % arenaArp.size]

                        // Warm saw-like bass + subtle cyber arpeggio
                        val bassEnv = exp(-beatFrac * 3.5f)
                        val bassOsc = (sin(2.0 * PI * bassFreq * t) + 0.45 * sin(4.0 * PI * bassFreq * t)).toFloat() * bassEnv
                        val arpEnv = exp(-beatFrac * 7.0f) * if (musicModeArena) 0.35f else 0.18f
                        val arpOsc = sin(2.0 * PI * arpFreq * t).toFloat() * arpEnv
                        val padOsc = sin(2.0 * PI * (bassFreq * 2f) * t).toFloat() * 0.14f

                        // Subtle kick pulse on every 2nd beat in arena
                        val kick = if (musicModeArena && step % 2 == 0) {
                            val kEnv = exp(-beatFrac * 18.0f)
                            sin(2.0 * PI * (45.0 + 70.0 * kEnv) * beatFrac / tempoHz).toFloat() * kEnv * 0.45f
                        } else 0f

                        val mixed = ((bassOsc * 0.42f + arpOsc + padOsc + kick) * 0.45f).coerceIn(-0.95f, 0.95f)
                        buf[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
                    }
                    sampleIndex += chunkSize
                    track.write(buf, 0, chunkSize)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun updateMusicGain() {
        val vol = (masterVol * musicVol * 0.55f).coerceIn(0f, 1f)
        try {
            musicTrack?.setVolume(vol)
        } catch (_: Exception) {
        }
    }

    fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
    }

    fun play(effect: SoundEffect) {
        val gain = (masterVol * sfxVol).coerceIn(0f, 1f)
        if (gain <= 0.01f) return
        val pcm = sfxBuffers[effect] ?: return
        scope.launch {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.setVolume(gain)
                track.play()
                delay((pcm.size * 1000L / sampleRate) + 40L)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun buildAllSfxBuffers() {
        sfxBuffers[SoundEffect.UI_CLICK] = synthTone(0.06f, 680f, 920f, 0.45f)
        sfxBuffers[SoundEffect.UNLOCK] = synthChord(0.32f, floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f), 0.5f)
        sfxBuffers[SoundEffect.PASS] = synthTone(0.11f, 520f, 840f, 0.55f)
        sfxBuffers[SoundEffect.SHOOT_NORMAL] = synthImpact(0.16f, 340f, 110f, 0.7f)
        sfxBuffers[SoundEffect.SHOOT_CHARGED] = synthImpact(0.30f, 620f, 95f, 0.9f)
        sfxBuffers[SoundEffect.BALL_BOUNCE] = synthTone(0.08f, 240f, 140f, 0.45f)
        sfxBuffers[SoundEffect.BOOST] = synthWhoosh(0.24f, 180f, 520f, 0.55f)
        sfxBuffers[SoundEffect.TACKLE] = synthImpact(0.18f, 210f, 65f, 0.75f)
        sfxBuffers[SoundEffect.SAVE] = synthChord(0.24f, floatArrayOf(440f, 660f), 0.6f)
        sfxBuffers[SoundEffect.GOAL_HORN] = synthGoalHorn()
        sfxBuffers[SoundEffect.COUNTDOWN_BEEP] = synthTone(0.14f, 587.33f, 587.33f, 0.55f)
        sfxBuffers[SoundEffect.WHISTLE] = synthTone(0.32f, 1950f, 2150f, 0.45f)
    }

    private fun synthTone(durationSec: Float, startHz: Float, endHz: Float, amp: Float): ShortArray {
        val n = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            val freq = startHz + (endHz - startHz) * p
            val env = (1f - p) * (1f - p)
            val s = sin(2.0 * PI * freq * (i.toDouble() / sampleRate)).toFloat() * env * amp
            (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthImpact(durationSec: Float, startHz: Float, endHz: Float, amp: Float): ShortArray {
        val n = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val rng = Random(42)
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            val freq = startHz * exp(-p * 3.5f) + endHz
            val env = exp(-p * 6.5f)
            val noise = (rng.nextFloat() * 2f - 1f) * exp(-p * 14f) * 0.45f
            val tone = sin(2.0 * PI * freq * (i.toDouble() / sampleRate)).toFloat()
            val s = (tone * 0.75f + noise) * env * amp
            (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthWhoosh(durationSec: Float, startHz: Float, endHz: Float, amp: Float): ShortArray {
        val n = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val rng = Random(99)
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            val env = sin(PI * p).toFloat()
            val freq = startHz + (endHz - startHz) * p
            val noise = (rng.nextFloat() * 2f - 1f) * 0.3f
            val tone = sin(2.0 * PI * freq * (i.toDouble() / sampleRate)).toFloat() * 0.7f
            val s = (tone + noise) * env * amp
            (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthChord(durationSec: Float, freqs: FloatArray, amp: Float): ShortArray {
        val n = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            val env = exp(-p * 3.8f)
            var sum = 0f
            for (f in freqs) {
                sum += sin(2.0 * PI * f * (i.toDouble() / sampleRate)).toFloat()
            }
            val s = (sum / freqs.size) * env * amp
            (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthGoalHorn(): ShortArray {
        val durationSec = 0.95f
        val n = (sampleRate * durationSec).toInt()
        val rng = Random(7)
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            val t = i.toDouble() / sampleRate
            val hornEnv = if (p < 0.08f) p / 0.08f else exp(-(p - 0.08f) * 2.2f)
            val horn = (sin(2.0 * PI * 146.83 * t) + 0.6 * sin(2.0 * PI * 220.0 * t) + 0.4 * sin(2.0 * PI * 293.66 * t)).toFloat() * 0.38f * hornEnv
            val crowdEnv = sin(PI * p).toFloat() * 0.45f
            val crowd = (rng.nextFloat() * 2f - 1f) * crowdEnv
            val s = (horn + crowd).coerceIn(-0.98f, 0.98f)
            (s * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
