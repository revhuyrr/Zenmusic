package com.kawaiimusic.app.net

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Plain HttpURLConnection downloader that NewPipeExtractor uses for every request it makes. */
private class NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = request.httpMethod()
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.instanceFollowRedirects = true

            var hasUserAgent = false
            for ((name, values) in request.headers()) {
                if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
                conn.setRequestProperty(name, values.joinToString(", "))
            }
            if (!hasUserAgent) conn.setRequestProperty("User-Agent", WEB_UA)

            val body = request.dataToSend()
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }

            val code = conn.responseCode
            if (code == 429) throw ReCaptchaException("reCAPTCHA challenge requested", request.url())

            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val headers = HashMap<String, List<String>>()
            for ((k, v) in conn.headerFields) if (k != null) headers[k] = v
            return Response(code, conn.responseMessage ?: "", headers, text, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}

/** Resolves audio stream URLs with NewPipeExtractor (handles signature + throttling decoding). */
object NewPipeStreams {
    @Volatile private var ready = false

    private fun ensureInit() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            NewPipe.init(NewPipeDownloader(), Localization("en", "US"), ContentCountry("US"))
            ready = true
        }
    }

    // YouTube checks that the user agent matches the client the URL was minted for (`c=` param).
    private fun userAgentFor(url: String): String {
        val client = Regex("[?&]c=([A-Z_]+)").find(url)?.groupValues?.get(1)
        return when (client) {
            "IOS" -> "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
            "ANDROID" -> "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip"
            "ANDROID_VR" -> "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            "ANDROID_MUSIC" -> "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
            else -> NewPipeDownloader.WEB_UA
        }
    }

    fun resolve(videoId: String): InnerTube.StreamInfo {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube as YoutubeService, "https://www.youtube.com/watch?v=$videoId")

        val candidates = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && !it.content.isNullOrEmpty()
        }
        if (candidates.isEmpty()) throw IOException("NewPipe found no progressive audio stream for $videoId")

        // Prefer AAC/MP4, then the highest bitrate.
        val best = candidates.maxByOrNull { s ->
            val mp4 = if (s.format?.mimeType?.startsWith("audio/mp4") == true) 1_000_000_000L else 0L
            mp4 + s.averageBitrate.toLong()
        }!!

        val url = best.content
        val length = best.itagItem?.contentLength ?: -1L
        return InnerTube.StreamInfo(
            url = url,
            userAgent = userAgentFor(url),
            contentLength = if (length > 0) length else 0L,
            expiresAtMs = System.currentTimeMillis() + 2 * 3600_000L,
        )
    }
}
