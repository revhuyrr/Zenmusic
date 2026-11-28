package com.kawaiimusic.app.net

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object StreamRepository {
    private val cache = ConcurrentHashMap<String, InnerTube.StreamInfo>()

    /** Short description of the most recent failure, shown in the app so problems are visible. */
    @Volatile var lastError: String? = null

    fun resolve(videoId: String): InnerTube.StreamInfo {
        cache[videoId]?.let { if (it.expiresAtMs > System.currentTimeMillis()) return it }
        // NewPipeExtractor first (it decodes YouTube's signature / throttling parameters);
        // the plain client chain is the fallback.
        val info = try {
            NewPipeStreams.resolve(videoId)
        } catch (first: Exception) {
            try {
                InnerTube.resolveStream(videoId)
            } catch (second: Exception) {
                lastError = "${first.javaClass.simpleName}: ${first.message?.take(90)}"
                second.addSuppressed(first)
                throw second
            }
        }
        lastError = null
        cache[videoId] = info
        return info
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }
}

/**
 * ExoPlayer is given placeholder URIs (https://music.youtube.com/watch?v=ID). This resolver
 * swaps in the real stream URL just before the bytes are requested, on the loader thread.
 */
@UnstableApi
class YoutubeResolver : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.host != "music.youtube.com" || uri.path != "/watch") return dataSpec
        val id = uri.getQueryParameter("v") ?: throw IOException("Missing video id")
        val stream = StreamRepository.resolve(id)

        val builder = Uri.parse(stream.url).buildUpon()
        if (stream.contentLength > 0) builder.appendQueryParameter("range", "0-${stream.contentLength}")

        val headers = HashMap(dataSpec.httpRequestHeaders)
        headers["User-Agent"] = stream.userAgent
        return dataSpec.buildUpon().setUri(builder.build()).setHttpRequestHeaders(headers).build()
    }
}
