package org.astrofixxer.update

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException

/**
 * The update check: newest release on GitHub -> its update.json -> compare with this build. Blocking; call it off the main thread.
 * Nothing about the user is sent (no token, no id): two plain GETs to GitHub.
 */
class UpdateChecker(private val http: HttpGet, private val nowMillis: () -> Long = System::currentTimeMillis) {
    private class Stop(val problem: UpdateProblem) : Exception()

    fun check(local: LocalBuild): CheckOutcome = try {
        val release = fetchRelease()
        val asset = release.assets.firstOrNull { it.name == UpdateConfig.UPDATE_JSON_NAME } ?: throw Stop(UpdateProblem.NoUpdateFile)
        requireOwnAsset(release, asset)
        if (asset.size > UpdateConfig.MAX_UPDATE_JSON_BYTES) throw Stop(UpdateProblem.Unsafe("update.json is too large"))
        val info = parseInfo(fetch(asset.url, "application/json", UpdateConfig.MAX_UPDATE_JSON_BYTES, missing = UpdateProblem.NoUpdateFile))
        when {
            info.applicationId != local.applicationId -> throw Stop(UpdateProblem.WrongApp(info.applicationId))
            info.versionCode > local.versionCode -> CheckOutcome.Available(withApkUrl(release, info))
            else -> CheckOutcome.UpToDate(info.versionName, info.versionCode)
        }
    } catch (e: Stop) {
        CheckOutcome.Failed(e.problem)
    }

    private fun parseInfo(bytes: ByteArray): UpdateInfo = try {
        UpdateParser.parseUpdateJson(String(bytes, Charsets.UTF_8))
    } catch (e: MalformedException) {
        throw Stop(UpdateProblem.Malformed(e.message ?: "update.json"))
    }

    private fun fetchRelease(): Release {
        val bytes = fetch(UpdateConfig.LATEST_RELEASE_URL, "application/vnd.github+json", UpdateConfig.MAX_RELEASE_JSON_BYTES, missing = UpdateProblem.NotFound)
        return try {
            UpdateParser.parseRelease(String(bytes, Charsets.UTF_8))
        } catch (e: MalformedException) {
            throw Stop(UpdateProblem.Malformed(e.message ?: "release"))
        }
    }

    /** The APK is looked up by name in the same release, and its address and size must be that release's own. */
    private fun withApkUrl(release: Release, info: UpdateInfo): UpdateInfo {
        val apk = release.assets.firstOrNull { it.name == info.apk } ?: throw Stop(UpdateProblem.Malformed("the release has no ${info.apk}"))
        requireOwnAsset(release, apk)
        if (apk.size != info.size) throw Stop(UpdateProblem.Unsafe("APK size differs from update.json"))
        return info.copy(apkUrl = apk.url, releaseTag = release.tag)
    }

    private fun requireOwnAsset(release: Release, asset: ReleaseAsset) {
        if (!UrlPolicy.isAssetOfRelease(asset.url, release.tag, asset.name)) throw Stop(UpdateProblem.Unsafe("${asset.name} is not from this release"))
    }

    /** GET [url]; a 200 gives the body, anything else or any I/O failure becomes a [Stop] with a matching problem. */
    private fun fetch(url: String, accept: String, maxBytes: Int, missing: UpdateProblem): ByteArray {
        val r = try {
            http.get(url, accept, maxBytes)
        } catch (e: SocketTimeoutException) {
            throw Stop(UpdateProblem.Timeout)
        } catch (e: UnsafeUrlException) {
            throw Stop(UpdateProblem.Unsafe(e.message ?: "address"))
        } catch (e: TooLargeException) {
            throw Stop(UpdateProblem.Unsafe("the answer is too large"))
        } catch (e: SSLException) {
            throw Stop(UpdateProblem.TlsFailed)
        } catch (e: IOException) {
            throw Stop(UpdateProblem.Offline)
        } catch (e: RuntimeException) {
            throw Stop(UpdateProblem.Unexpected(e.javaClass.simpleName))
        }
        return when (r.status) {
            200 -> r.body
            404 -> throw Stop(missing)
            403, 429 -> throw Stop(rateLimit(r) ?: UpdateProblem.ServerError(r.status))
            else -> throw Stop(UpdateProblem.ServerError(r.status))
        }
    }

    /** A 429, or a 403 that looks like GitHub's request limit, with the time to try again when the headers give it. */
    private fun rateLimit(r: HttpResponse): UpdateProblem.RateLimited? {
        val remaining = r.headers["x-ratelimit-remaining"]?.trim()
        val retryAfter = r.headers["retry-after"]?.trim()
        val says = String(r.body, Charsets.UTF_8).lowercase().contains("rate limit")
        if (r.status == 403 && remaining != "0" && retryAfter == null && !says) return null
        val now = nowMillis()
        val seconds: Long? = retryAfter?.let { v ->
            v.toLongOrNull() ?: runCatching { (ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now) / 1000 }.getOrNull()
        } ?: r.headers["x-ratelimit-reset"]?.trim()?.toLongOrNull()?.takeIf { remaining == null || remaining == "0" }?.let { it - now / 1000 }
        return UpdateProblem.RateLimited(seconds?.coerceIn(0L, 86_400L))
    }
}
