package com.kawaiimusic.app.net

import org.json.JSONObject
import java.io.IOException

/**
 * Kotlin port of the request layer of the `innertube` Python library (MIT, Tom Bulled).
 *
 * Context values (client names/ids, API keys, referer) come from that library's config.py.
 * Browsing goes through the YouTube Music web client (WEB_REMIX); stream URLs come from the
 * `player` endpoint using a chain of app-style clients, falling back when one is refused.
 */
object InnerTube {
    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1/"
    private const val API_BASE = "https://youtubei.googleapis.com/youtubei/v1/"

    // WEB_REMIX (client_id 67) from innertube/config.py
    private const val WEB_REMIX_ID = "67"
    private const val WEB_REMIX_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val WEB_REMIX_VERSION = "1.20250310.01.00"
    private const val REFERER_MUSIC = "https://music.youtube.com/"
    private const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @Volatile private var visitorData: String? = null

    /** POST to a YouTube Music (WEB_REMIX) endpoint such as "search" or "browse". */
    fun web(endpoint: String, body: JSONObject = JSONObject()): JSONObject {
        body.put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", "WEB_REMIX")
                    .put("clientVersion", WEB_REMIX_VERSION)
                    .put("hl", "en")
                    .put("gl", "US")
            )
        )
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "User-Agent" to WEB_UA,
            "Origin" to "https://music.youtube.com",
            "Referer" to REFERER_MUSIC,
            "X-Goog-Api-Format-Version" to "1",
            "X-YouTube-Client-Name" to WEB_REMIX_ID,
            "X-YouTube-Client-Version" to WEB_REMIX_VERSION,
        )
        visitorData?.let { headers["X-Goog-Visitor-Id"] = it }
        val res = Http.request(
            "$MUSIC_BASE$endpoint?prettyPrint=false&key=$WEB_REMIX_KEY",
            method = "POST",
            headers = headers,
            body = body.toString(),
        )
        if (res.code !in 200..299) throw IOException("YouTube Music returned HTTP ${res.code}")
        val json = JSONObject(res.body)
        json.optJSONObject("responseContext")?.optString("visitorData")
            ?.takeIf { it.isNotEmpty() }?.let { visitorData = it }
        return json
    }

    // ---- stream resolution ----------------------------------------------------------------

    class PlayerClient(
        val name: String,
        val version: String,
        val id: Int,
        val userAgent: String,
        val apiKey: String? = null,
        val extra: Map<String, Any> = emptyMap(),
    )

    private val playerClients = listOf(
        PlayerClient(
            name = "ANDROID_VR", version = "1.61.48", id = 28,
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extra = mapOf(
                "deviceMake" to "Oculus", "deviceModel" to "Quest 3",
                "osName" to "Android", "osVersion" to "12L", "androidSdkVersion" to 32,
            ),
        ),
        PlayerClient(
            name = "ANDROID_MUSIC", version = "7.27.52", id = 21,
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip",
            apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            extra = mapOf("osName" to "Android", "osVersion" to "11", "androidSdkVersion" to 30),
        ),
        PlayerClient(
            name = "IOS", version = "20.10.4", id = 5,
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            extra = mapOf(
                "deviceMake" to "Apple", "deviceModel" to "iPhone16,2",
                "osName" to "iPhone", "osVersion" to "18.3.2.22D82",
            ),
        ),
        PlayerClient(
            name = "ANDROID", version = "20.10.38", id = 3,
            userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
            apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            extra = mapOf("osName" to "Android", "osVersion" to "11", "androidSdkVersion" to 30),
        ),
    )

    data class StreamInfo(
        val url: String,
        val userAgent: String,
        val contentLength: Long,
        val expiresAtMs: Long,
    )

    /** Finds a playable audio stream for [videoId], trying each client until one works. */
    fun resolveStream(videoId: String): StreamInfo {
        var lastError: Exception? = null
        for (client in playerClients) {
            try {
                val info = tryClient(client, videoId)
                if (info != null) return info
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("No playable stream for $videoId", lastError)
    }

    private fun tryClient(client: PlayerClient, videoId: String): StreamInfo? {
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", "en")
            .put("gl", "US")
        client.extra.forEach { (k, v) -> clientJson.put(k, v) }
        val body = JSONObject()
            .put("context", JSONObject().put("client", clientJson))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)

        val keyParam = client.apiKey?.let { "&key=$it" } ?: ""
        val res = Http.request(
            "${API_BASE}player?prettyPrint=false$keyParam",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json",
                "User-Agent" to client.userAgent,
                "X-Goog-Api-Format-Version" to "1",
                "X-YouTube-Client-Name" to client.id.toString(),
                "X-YouTube-Client-Version" to client.version,
            ),
            body = body.toString(),
        )
        if (res.code !in 200..299) return null
        val json = JSONObject(res.body)
        if (json.optJSONObject("playabilityStatus")?.optString("status") != "OK") return null

        val streaming = json.optJSONObject("streamingData") ?: return null
        val formats = streaming.optJSONArray("adaptiveFormats") ?: return null

        var best: JSONObject? = null
        var bestScore = -1L
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            if (!mime.startsWith("audio/") || f.optString("url").isEmpty()) continue
            // Prefer AAC/MP4 (widely supported), then the highest bitrate.
            val score = (if (mime.startsWith("audio/mp4")) 1_000_000_000L else 0L) + f.optLong("bitrate")
            if (score > bestScore) { best = f; bestScore = score }
        }
        val chosen = best ?: return null
        val url = chosen.getString("url")

        val status = try { Http.probe(url, client.userAgent) } catch (_: Exception) { -1 }
        if (status != 200 && status != 206) return null

        val ttlSec = streaming.optString("expiresInSeconds").toLongOrNull() ?: 3600L
        return StreamInfo(
            url = url,
            userAgent = client.userAgent,
            contentLength = chosen.optString("contentLength").toLongOrNull() ?: 0L,
            expiresAtMs = System.currentTimeMillis() + (ttlSec * 800L).coerceAtMost(4 * 3600_000L),
        )
    }
}
