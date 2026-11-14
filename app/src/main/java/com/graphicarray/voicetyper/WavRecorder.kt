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

    @SuppressLint("MissingPermission")
    fun start(maxSeconds: Int, onLimit: () -> Unit): Boolean {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        rec = r; out.reset(); running = true; r.startRecording()
        thread = Thread {
            val buf = ByteArray(4096)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) out.write(buf, 0, n)
                if (out.size() > rate * 2 * maxSeconds) { running = false; onLimit() }
            }
        }.also { it.start() }
        return true
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
