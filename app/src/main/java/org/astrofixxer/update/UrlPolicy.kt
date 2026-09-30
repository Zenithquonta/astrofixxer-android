package org.astrofixxer.update

import java.net.URI

/** Which addresses the updater will fetch. Everything else is refused. */
object UrlPolicy {
    /** Characters allowed in a release tag or asset name. No slashes, percent signs, colons, spaces or "@". */
    private val SEGMENT = Regex("[A-Za-z0-9._+-]{1,100}")

    fun isSafeSegment(s: String): Boolean = SEGMENT.matches(s) && s != "." && s != ".."

    /**
     * True only for exactly `https://github.com/<repo>/releases/download/<tag>/<asset>` with a plain tag and asset name.
     * A lookalike host (`github.com.evil.com`), a `user@host` trick, another repository, http, a query, a fragment,
     * a backslash, percent-encoding, or `..` all fail.
     */
    fun isReleaseDownloadUrl(url: String): Boolean {
        if (!url.startsWith(UpdateConfig.DOWNLOAD_PREFIX)) return false
        val rest = url.substring(UpdateConfig.DOWNLOAD_PREFIX.length)
        val parts = rest.split('/')
        return parts.size == 2 && parts.all { isSafeSegment(it) }
    }

    /** The one address a release asset may have: the same release's tag and the asset's own name. */
    fun expectedAssetUrl(tag: String, assetName: String): String? =
        if (isSafeSegment(tag) && isSafeSegment(assetName)) UpdateConfig.DOWNLOAD_PREFIX + tag + "/" + assetName else null

    /** True when [url] is the asset's [expectedAssetUrl] and also passes [isReleaseDownloadUrl]. */
    fun isAssetOfRelease(url: String, tag: String, assetName: String): Boolean =
        isReleaseDownloadUrl(url) && url == expectedAssetUrl(tag, assetName)

    /** GitHub's own hosts: the API, the site, and the CDN release files are redirected to. */
    fun isGitHubHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "github.com" || h == "api.github.com" || (h.endsWith(".githubusercontent.com") && h.length > ".githubusercontent.com".length)
    }

    /** Parses [url], requiring https, a GitHub host, no user info and the default port. Throws [UnsafeUrlException]. */
    fun checkedUri(url: String): URI {
        val uri = try { URI(url) } catch (e: java.net.URISyntaxException) { throw UnsafeUrlException("not a valid address") }
        if (uri.scheme?.lowercase() != "https") throw UnsafeUrlException("not https")
        if (uri.userInfo != null) throw UnsafeUrlException("address has user info")
        if (uri.port != -1 && uri.port != 443) throw UnsafeUrlException("unexpected port")
        if (!isGitHubHost(uri.host)) throw UnsafeUrlException("not a GitHub host")
        return uri
    }

    /** True for the CHANGELOG link the app may open in a browser. */
    fun isRepoLink(url: String): Boolean =
        runCatching { checkedUri(url) }.isSuccess && url.startsWith(UpdateConfig.CHANGELOG_URL_PREFIX) && !url.contains('\\') && !url.contains(' ')
}

class UnsafeUrlException(message: String) : java.io.IOException(message)
class TooLargeException : java.io.IOException("response too large")
