package com.graphicarray.voicetyper

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16 kHz mono 16-bit PCM recorder that returns a WAV file (accepted by Gemini as audio/wav). */
class WavRecorder {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val out = ByteArrayOutputStream()
    private val rate = 16000

    /**
     * Start recording. [onLimit] fires at [maxSeconds].
     * If [silenceSeconds] > 0, [onSilence] fires once after that long with no voice.
     * Its argument tells if any voice was heard before the quiet.
     */
    @SuppressLint("MissingPermission")
    fun start(
        maxSeconds: Int, onLimit: () -> Unit,
        silenceSeconds: Float = 0f, onSilence: (heardVoice: Boolean) -> Unit = {}
    ): Boolean {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        rec = r; out.reset(); running = true; r.startRecording()
        thread = Thread {
            val buf = ByteArray(4096)
            val silenceMs = (silenceSeconds * 1000).toLong()
            var lastVoice = System.currentTimeMillis()
            var heard = false
            var floor = 300.0   // noise floor, learned while we listen
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) {
                    out.write(buf, 0, n)
                    if (silenceMs > 0) {
                        val lvl = rms(buf, n)
                        // floor drops fast, rises slow: loud speech does not lift it
                        floor = if (lvl < floor) lvl else floor + (lvl - floor) * 0.01
                        val now = System.currentTimeMillis()
                        if (lvl > maxOf(500.0, floor * 2.5)) { lastVoice = now; heard = true }
                        else if (now - lastVoice > silenceMs) { running = false; onSilence(heard); break }
                    }
                }
                if (out.size() > rate * 2 * maxSeconds) { running = false; onLimit() }
            }
        }.also { it.start() }
        return true
    }

    private fun rms(b: ByteArray, n: Int): Double {
        var sum = 0.0
        var i = 0
        val cnt = n / 2
        while (i + 1 < n) {
            val v = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += v.toDouble() * v
            i += 2
        }
        return if (cnt == 0) 0.0 else Math.sqrt(sum / cnt)
    }

    fun stop(): ByteArray {
        running = false
        thread?.join()
        try { rec?.stop() } catch (_: Exception) {}
        rec?.release(); rec = null
        val pcm = out.toByteArray()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size)
        return h.array() + pcm
    }
}
