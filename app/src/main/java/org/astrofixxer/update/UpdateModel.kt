package org.astrofixxer.update

/** Where updates come from, and the limits that keep a bad answer from doing harm. Pure Kotlin: no Android imports. */
object UpdateConfig {
    const val REPO = "Zenithquonta/astrofixxer-android"
    /** The newest release marked Latest: the rolling "latest-build" preview (tag releases are published with --latest=false). */
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
    /** Every download, update.json and the APK alike, must be a browser_download_url under this prefix. */
    const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    const val CHANGELOG_URL_PREFIX = "https://github.com/$REPO/"
    const val UPDATE_JSON_NAME = "update.json"

    const val MAX_RELEASE_JSON_BYTES = 2 * 1024 * 1024
    const val MAX_UPDATE_JSON_BYTES = 64 * 1024
    const val MAX_APK_BYTES = 200L * 1024 * 1024

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 15_000
}

/** The running app, as the update check needs to know it. */
class LocalBuild(val applicationId: String, val versionCode: Long, val versionName: String)

/** What update.json says about the newest build, plus where its APK is (taken from the release's own asset list, never from update.json). */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val applicationId: String,
    /** Asset name of the APK in the same release. */
    val apk: String,
    /** Lower-case hex SHA-256 of the APK. */
    val sha256: String,
    val size: Long,
    /** Git commit the build came from (hex), or null. */
    val commit: String?,
    /** True when the build is signed with the permanent preview key, so it installs over the user's copy. False: throwaway debug key. */
    val permanentKey: Boolean,
    val apkUrl: String = "",
    val releaseTag: String = "",
) {
    /** CHANGELOG.md as it was at this build's commit (a build without a known commit shows the newest one). */
    val changelogUrl: String get() = UpdateConfig.CHANGELOG_URL_PREFIX + "blob/" + (commit ?: "main") + "/CHANGELOG.md"
}

sealed class CheckOutcome {
    class Available(val info: UpdateInfo) : CheckOutcome()
    class UpToDate(val remoteVersionName: String, val remoteVersionCode: Long) : CheckOutcome()
    class Failed(val problem: UpdateProblem) : CheckOutcome()
}

/** Everything that can go wrong, checking or downloading. The screen turns each into a plain sentence. */
sealed class UpdateProblem {
    object Offline : UpdateProblem()
    object Timeout : UpdateProblem()
    /** GitHub's request limit. [retryInSeconds] is when the response headers say to try again, if they do. */
    class RateLimited(val retryInSeconds: Long?) : UpdateProblem()
    /** No release exists (404). */
    object NotFound : UpdateProblem()
    /** The newest release has no update.json (for example a tag release). */
    object NoUpdateFile : UpdateProblem()
    class Malformed(val detail: String) : UpdateProblem()
    /** The release is for another app (its applicationId differs from this one). */
    class WrongApp(val remoteApplicationId: String) : UpdateProblem()
    /** A download address, size or redirect the updater refuses to trust. */
    class Unsafe(val detail: String) : UpdateProblem()
    object TlsFailed : UpdateProblem()
    class ServerError(val code: Int) : UpdateProblem()
    // Download and install.
    class DownloadFailed(val code: Int) : UpdateProblem()
    object DownloadCancelled : UpdateProblem()
    class LowStorage(val neededBytes: Long) : UpdateProblem()
    object SizeMismatch : UpdateProblem()
    object ChecksumMismatch : UpdateProblem()
    object FileMissing : UpdateProblem()
    object NoInstaller : UpdateProblem()
    class Unexpected(val detail: String) : UpdateProblem()
}

/** Version names are for display only; whether an update exists is decided by versionCode alone. */
object Versions {
    /** "v0.2.0" and "0.2.0" are the same name. */
    fun displayName(name: String): String {
        val n = name.trim()
        return if (n.length > 1 && (n[0] == 'v' || n[0] == 'V') && n[1].isDigit()) n.substring(1) else n
    }

    fun sameName(a: String, b: String): Boolean = displayName(a).equals(displayName(b), ignoreCase = true)

    /**
     * Compares the dotted numbers of two names ("0.10.0" is newer than "0.9.0"); a suffix such as "-preview" is ignored.
     * Returns 0 for names it can't read as numbers. Display only.
     */
    fun compareNames(a: String, b: String): Int {
        val pa = numbers(a)
        val pb = numbers(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrNull(i) ?: 0L).compareTo(pb.getOrNull(i) ?: 0L)
            if (c != 0) return c
        }
        return 0
    }

    private fun numbers(name: String): List<Long> {
        val core = displayName(name).takeWhile { it.isDigit() || it == '.' }
        return core.split('.').filter { it.isNotEmpty() }.map { it.take(18).toLong() }
    }
}
