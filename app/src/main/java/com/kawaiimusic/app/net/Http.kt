package com.kawaiimusic.app.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal blocking HTTP helper (call from Dispatchers.IO / loader threads only). */
object Http {
    data class Response(val code: Int, val body: String)

    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 20_000,
    ): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Response(code, text)
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Status code of a tiny ranged GET, used to check that a stream URL really plays. */
    fun probe(url: String, userAgent: String): Int {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Range", "bytes=0-1")
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
