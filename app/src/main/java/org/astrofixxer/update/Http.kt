package org.astrofixxer.update

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection

class HttpResponse(val status: Int, headers: Map<String, String>, val body: ByteArray) {
    /** Header names lower-cased. */
    val headers: Map<String, String> = headers.mapKeys { it.key.lowercase() }
}

/** One HTTPS GET with a size cap. Throws [java.io.IOException] (timeouts, [TooLargeException], [UnsafeUrlException]) rather than returning a half answer. */
fun interface HttpGet {
    fun get(url: String, accept: String, maxBytes: Int): HttpResponse
}

/**
 * [HttpGet] on the platform's HttpURLConnection. HTTPS only, no cookies or credentials, connect and read timeouts.
 * Redirects are followed by hand, at most [MAX_REDIRECTS], and only to https addresses on GitHub's own hosts
 * (GitHub sends release files to a githubusercontent.com CDN). Sends nothing that identifies the user.
 */
class HttpsGet(
    private val userAgent: String,
    private val connectTimeoutMs: Int = UpdateConfig.CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = UpdateConfig.READ_TIMEOUT_MS,
) : HttpGet {
    override fun get(url: String, accept: String, maxBytes: Int): HttpResponse {
        var uri = UrlPolicy.checkedUri(url)
        for (hop in 0..MAX_REDIRECTS) {
            val conn = uri.toURL().openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", accept)
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                val status = conn.responseCode
                if (status in REDIRECTS) {
                    val location = conn.getHeaderField("Location") ?: throw UnsafeUrlException("redirect without a location")
                    if (hop == MAX_REDIRECTS) throw UnsafeUrlException("too many redirects")
                    val next = try { uri.resolve(location).toString() } catch (e: IllegalArgumentException) { throw UnsafeUrlException("bad redirect address") }
                    uri = UrlPolicy.checkedUri(next)
                    continue
                }
                val headers = HashMap<String, String>()
                for ((k, v) in conn.headerFields) if (k != null && !v.isNullOrEmpty()) headers[k.lowercase()] = v.last()
                val body = if (status in 200..299) {
                    if (conn.contentLengthLong > maxBytes) throw TooLargeException()
                    conn.inputStream.use { readCapped(it, maxBytes) }
                } else {
                    // An error page is only read for a hint (rate-limit wording); a long one is cut off, not an error.
                    (conn.errorStream ?: return HttpResponse(status, headers, ByteArray(0))).use { readUpTo(it, ERROR_BODY_MAX) }
                }
                return HttpResponse(status, headers, body)
            } finally {
                conn.disconnect()
            }
        }
        throw UnsafeUrlException("too many redirects")
    }

    companion object {
        const val MAX_REDIRECTS = 5
        private const val ERROR_BODY_MAX = 4096
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        /** Reads all of [input]; more than [maxBytes] throws [TooLargeException] (so a huge or endless answer can't fill memory). */
        fun readCapped(input: InputStream, maxBytes: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw TooLargeException()
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }

        fun readUpTo(input: InputStream, max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (out.size() < max) {
                val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
