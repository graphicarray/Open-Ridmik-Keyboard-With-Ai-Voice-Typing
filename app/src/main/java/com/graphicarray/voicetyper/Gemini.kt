package com.graphicarray.voicetyper

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    // English mode: the output is ALWAYS English. Bangla speech is translated.
    private const val EN_RULES = "The speaker may talk in English, Bangla (Bengali), or a mix of both. " +
        "Your output must ALWAYS be in English only. If the speaker uses Bangla (Bengali) words or sentences, " +
        "translate them into natural English. Never output Bengali script, and never write Bangla words in " +
        "English letters (no Banglish, no transliteration). "
    // Bangla mode: the output is ALWAYS Bangla. Never translated to English.
    private const val BN_RULES = "The speaker is speaking Bangla (Bengali). Write the output in Bengali script (বাংলা). " +
        "Never translate it into English. If the speaker says English words, keep those words in English letters. "
    private const val CLEAN = "Write down what the speaker said, then clean it up: remove filler words, false starts and " +
        "repetitions; fix grammar, spelling and punctuation; keep the speaker's meaning and tone. "
    private const val RAW = "Write down everything the speaker said as faithfully as possible, without removing words. Add normal punctuation. "
    private const val TAIL = "Output only the final text, nothing else. If there is no speech, output nothing."

    /** Blocking call. Run off the main thread. Throws Exception with a readable message. */
    fun run(key: String, model: String, mode: String, lang: String, wav: ByteArray): String {
        val prompt = (if (lang == "bn") BN_RULES else EN_RULES) + (if (mode == "cleanup") CLEAN else RAW) + TAIL +
            (if (lang == "bn") " The final text must be in Bangla." else " The final text must be in English only.")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "audio/wav")
                    .put("data", Base64.encodeToString(wav, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("temperature", 0))
        val json = request("$BASE/models/$model:generateContent", key, body.toString())
        val parts = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString().trim()
    }

    /** Lists Gemini models that support generateContent (names without the "models/" prefix). */
    fun listModels(key: String): List<String> {
        val out = ArrayList<String>()
        var token = ""
        do {
            val url = "$BASE/models?pageSize=1000" + if (token.isNotEmpty()) "&pageToken=$token" else ""
            val json = request(url, key, null)
            val arr = json.optJSONArray("models") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
                val skip = listOf("image", "tts", "embedding", "imagen", "veo", "live", "robotics", "computer-use", "aqa")
                if (ok && name.startsWith("gemini") && skip.none { name.contains(it) }) out.add(name)
            }
            token = json.optString("nextPageToken", "")
        } while (token.isNotEmpty())
        return out.distinct().sortedDescending()
    }

    private fun request(url: String, key: String, body: String?): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 90000
            c.setRequestProperty("x-goog-api-key", key)
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val json = JSONObject(stream.bufferedReader().readText())
            if (json.has("error")) throw Exception(json.getJSONObject("error").optString("message", "Gemini error"))
            return json
        } finally { c.disconnect() }
    }
}
