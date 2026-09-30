package org.astrofixxer.host

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astrofixxer.BuildConfig
import org.astrofixxer.ui.UpdateStatus
import org.astrofixxer.ui.Updater
import org.astrofixxer.ui.t
import org.astrofixxer.update.CheckOutcome
import org.astrofixxer.update.HttpsGet
import org.astrofixxer.update.Integrity
import org.astrofixxer.update.LocalBuild
import org.astrofixxer.update.TooLargeException
import org.astrofixxer.update.UpdateChecker
import org.astrofixxer.update.UpdateInfo
import org.astrofixxer.update.UpdateProblem
import org.astrofixxer.update.UrlPolicy
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The in-app updater for preview and debug builds (Google Play forbids self-updating apps, so [BuildConfig.UPDATER_ENABLED]
 * is false there and MainActivity never creates this). It runs only when the user taps a button.
 *
 * Download: DownloadManager into the app's own external folder (no storage permission), visible notification, progress
 * polled from DownloadManager.query. Then the file is copied into the app's private folder while its SHA-256 is computed,
 * so what is verified is exactly what gets installed and no other app can swap it in between. A file that fails the
 * size or checksum check is deleted. Install: Android's own installer through ACTION_VIEW, which always asks the user
 * and refuses an APK signed with a different key.
 *
 * Not exercised on a device by the tests in this repository: see docs and the pull request notes.
 */
class AndroidUpdater(private val activity: ComponentActivity) :
    Updater(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val prefs by lazy { activity.getSharedPreferences("astrofixxer_update", android.content.Context.MODE_PRIVATE) }
    private val downloads: DownloadManager? by lazy { activity.getSystemService(DownloadManager::class.java) }
    /** Private folder holding the verified APK, served to the installer through the FileProvider declared in the preview/debug manifests. */
    private val privateDir get() = File(activity.filesDir, "updates")
    private val externalDir get() = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    private val local = LocalBuild(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME)

    /** Deletes leftovers of earlier updates (installed, abandoned, or interrupted by the app closing). A download waits for it. */
    private val startupCleanup = scope.launch(Dispatchers.IO) { cleanStale() }

    private val busy get() = status is UpdateStatus.Checking || status is UpdateStatus.Downloading || status is UpdateStatus.Verifying

    override fun check() {
        if (busy) return
        status = UpdateStatus.Checking
        job = scope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) { UpdateChecker(HttpsGet("AstroFixxer/${BuildConfig.VERSION_NAME} (in-app updater)")).check(local) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CheckOutcome.Failed(UpdateProblem.Unexpected(e.javaClass.simpleName))
            }
            status = when (outcome) {
                is CheckOutcome.Available -> UpdateStatus.Available(outcome.info)
                is CheckOutcome.UpToDate -> UpdateStatus.UpToDate(outcome.remoteVersionName, outcome.remoteVersionCode)
                is CheckOutcome.Failed -> UpdateStatus.Failed(outcome.problem)
            }
        }
    }

    override fun download(info: UpdateInfo) {
        if (busy) return
        // The screen hands back what the check returned; still, never fetch anything that isn't this release's own asset.
        if (UrlPolicy.expectedAssetUrl(info.releaseTag, info.apk) != info.apkUrl || !UrlPolicy.isReleaseDownloadUrl(info.apkUrl) ||
            info.size !in 1..org.astrofixxer.update.UpdateConfig.MAX_APK_BYTES || !Integrity.isSha256Hex(info.sha256)) {
            status = UpdateStatus.Failed(UpdateProblem.Unsafe("download address"))
            return
        }
        status = UpdateStatus.Downloading(info, 0, -1)
        job = scope.launch {
            try {
                startupCleanup.join()
                val problem = withContext(Dispatchers.IO) { cleanStale(); lowStorage(info) }
                if (problem != null) {
                    status = UpdateStatus.Failed(problem, info)
                    return@launch
                }
                val id = enqueue(info) ?: run { status = UpdateStatus.Failed(UpdateProblem.DownloadFailed(0), info); return@launch }
                val done = pollUntilDone(info, id)
                if (done != null) {
                    withContext(Dispatchers.IO) { removeDownload(id) }
                    status = UpdateStatus.Failed(done, info)
                    return@launch
                }
                status = UpdateStatus.Verifying(info)
                val bad = withContext(Dispatchers.IO) { copyVerified(info, id).also { removeDownload(id) } }
                if (bad != null) status = UpdateStatus.Failed(bad, info)
                else {
                    status = UpdateStatus.ReadyToInstall(info)
                    install()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = UpdateStatus.Failed(UpdateProblem.Unexpected(e.javaClass.simpleName), info)
            }
        }
    }

    override fun cancelDownload() {
        val info = (status as? UpdateStatus.Downloading)?.info ?: return
        job?.cancel()
        job = null
        val id = prefs.getLong(KEY_ID, -1)
        scope.launch(Dispatchers.IO) { removeDownload(id) }
        status = UpdateStatus.Available(info)
    }

    override fun install() {
        val info = when (val s = status) {
            is UpdateStatus.ReadyToInstall -> s.info
            is UpdateStatus.NeedsInstallPermission -> s.info
            else -> return
        }
        if (!activity.packageManager.canRequestPackageInstalls()) {
            status = UpdateStatus.NeedsInstallPermission(info)
            return
        }
        status = UpdateStatus.Verifying(info)
        job = scope.launch {
            try {
                // Check the private copy again just before handing it over (cheap, and it survives a long wait on this screen).
                val file = File(privateDir, apkName(info))
                val bad = withContext(Dispatchers.IO) { verifyFile(file, info) }
                if (bad != null) {
                    file.delete()
                    status = UpdateStatus.Failed(bad, info)
                    return@launch
                }
                status = UpdateStatus.ReadyToInstall(info)
                val uri = FileProvider.getUriForFile(activity, "${BuildConfig.APPLICATION_ID}.updates", file)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    activity.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    status = UpdateStatus.Failed(UpdateProblem.NoInstaller)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = UpdateStatus.Failed(UpdateProblem.Unexpected(e.javaClass.simpleName), info)
            }
        }
    }

    override fun openInstallSettings() {
        val pkg = Uri.parse("package:${activity.packageName}")
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg))
        } catch (e: ActivityNotFoundException) {
            try { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) } catch (e2: ActivityNotFoundException) { /* nothing to open */ }
        }
    }

    override fun openLink(url: String) {
        if (!UrlPolicy.isRepoLink(url)) return
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: ActivityNotFoundException) { /* no browser */ }
    }

    /** The user came back (perhaps from the install-permission screen): carry on with the install if it is now allowed. */
    fun onResume() {
        if (status is UpdateStatus.NeedsInstallPermission && activity.packageManager.canRequestPackageInstalls()) install()
    }

    fun close() {
        scope.cancel()
    }

    // ---------------------------------------------------------------- download plumbing

    private fun apkName(info: UpdateInfo) = "AstroFixxer-update-${info.versionCode}.apk"

    private fun lowStorage(info: UpdateInfo): UpdateProblem? {
        // The download and the private copy briefly both exist, plus some room to spare.
        val needed = 2 * info.size + 16L * 1024 * 1024
        val free = listOfNotNull(externalDir, activity.filesDir).minOf { runCatching { StatFs(it.path).availableBytes }.getOrDefault(0L) }
        return if (free < needed) UpdateProblem.LowStorage(needed) else null
    }

    private fun enqueue(info: UpdateInfo): Long? {
        val dm = downloads ?: return null
        return try {
            // VISIBILITY_VISIBLE: shown while downloading only. A "completed" notification would open the unchecked file itself.
            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle(t("AstroFixxer update"))
                .setDescription(info.versionName)
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, apkName(info))
            dm.enqueue(request).also { prefs.edit().putLong(KEY_ID, it).apply() }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) { // no external storage to download into
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** Shows progress until the download ends. Returns null on success, otherwise why it didn't. */
    private suspend fun pollUntilDone(info: UpdateInfo, id: Long): UpdateProblem? {
        val dm = downloads ?: return UpdateProblem.DownloadFailed(0)
        while (true) {
            val step = withContext(Dispatchers.IO) { queryProgress(dm, id, info) }
            when (step) {
                is Step.Running -> status = UpdateStatus.Downloading(info, step.done, step.total)
                Step.Done -> return null
                is Step.Problem -> return step.problem
            }
            delay(500)
        }
    }

    private sealed class Step {
        class Running(val done: Long, val total: Long) : Step()
        object Done : Step()
        class Problem(val problem: UpdateProblem) : Step()
    }

    private fun queryProgress(dm: DownloadManager, id: Long, info: UpdateInfo): Step {
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return Step.Problem(UpdateProblem.DownloadFailed(0))
        cursor.use { c ->
            // No row: the user removed the download from the notification or the Downloads app.
            if (!c.moveToFirst()) return Step.Problem(UpdateProblem.DownloadCancelled)
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (done > info.size || total > info.size) return Step.Problem(UpdateProblem.SizeMismatch)
            return when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> Step.Done
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    Step.Problem(if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) UpdateProblem.LowStorage(2 * info.size) else UpdateProblem.DownloadFailed(reason))
                }
                else -> Step.Running(done, total) // pending, running, or paused (waiting for a connection)
            }
        }
    }

    /**
     * Copies the finished download into the private folder, hashing while copying, and keeps the copy only when its size
     * and SHA-256 are what the release said. Returns null on success, otherwise why not (the copy is deleted).
     */
    private fun copyVerified(info: UpdateInfo, id: Long): UpdateProblem? {
        val dm = downloads ?: return UpdateProblem.DownloadFailed(0)
        privateDir.mkdirs()
        val part = File(privateDir, apkName(info) + ".part")
        val target = File(privateDir, apkName(info))
        return try {
            val copied = ParcelFileDescriptor.AutoCloseInputStream(dm.openDownloadedFile(id)).use { input ->
                part.outputStream().use { out -> Integrity.copyAndHash(input, out, info.size) }
            }
            val bad = Integrity.verify(copied, info)
            if (bad != null) { part.delete(); bad }
            else if (!(target.delete() || !target.exists()) || !part.renameTo(target)) { part.delete(); UpdateProblem.FileMissing }
            else null
        } catch (e: TooLargeException) {
            part.delete(); UpdateProblem.SizeMismatch
        } catch (e: FileNotFoundException) {
            part.delete(); UpdateProblem.FileMissing
        } catch (e: IOException) {
            part.delete()
            if (e.message?.contains("ENOSPC") == true || e.message?.contains("No space left") == true) UpdateProblem.LowStorage(2 * info.size) else UpdateProblem.DownloadFailed(0)
        }
    }

    private fun verifyFile(file: File, info: UpdateInfo): UpdateProblem? {
        if (!file.isFile) return UpdateProblem.FileMissing
        return try {
            val copied = file.inputStream().use { Integrity.copyAndHash(it, DISCARD, info.size) }
            Integrity.verify(copied, info)
        } catch (e: TooLargeException) {
            UpdateProblem.SizeMismatch
        } catch (e: IOException) {
            UpdateProblem.FileMissing
        }
    }

    private fun removeDownload(id: Long) {
        if (id >= 0) runCatching { downloads?.remove(id) }
        prefs.edit().remove(KEY_ID).apply()
    }

    /** Deletes older update files and any download of ours DownloadManager still holds. */
    private fun cleanStale() {
        removeDownload(prefs.getLong(KEY_ID, -1))
        for (dir in listOfNotNull(privateDir, externalDir)) dir.listFiles()?.forEach { if (it.name.startsWith("AstroFixxer-update-")) it.delete() }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val KEY_ID = "download_id"
        /** Hashing the private copy needs to read it, not write it anywhere. */
        val DISCARD = object : java.io.OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
        }
    }
}
