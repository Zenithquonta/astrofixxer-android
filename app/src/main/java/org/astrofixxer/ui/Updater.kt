package org.astrofixxer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.astrofixxer.update.UpdateInfo
import org.astrofixxer.update.UpdateProblem

/** What the "App updates" block shows. */
sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    class UpToDate(val versionName: String, val versionCode: Long) : UpdateStatus()
    class Available(val info: UpdateInfo) : UpdateStatus()
    /** [total] is -1 while the size isn't known yet. */
    class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateStatus()
    class Verifying(val info: UpdateInfo) : UpdateStatus()
    class ReadyToInstall(val info: UpdateInfo) : UpdateStatus()
    class NeedsInstallPermission(val info: UpdateInfo) : UpdateStatus()
    /** [retry] is the update to download again after a failed download; null means "check again". */
    class Failed(val problem: UpdateProblem, val retry: UpdateInfo? = null) : UpdateStatus()
}

/**
 * The updater as the screen sees it, without Android: the host (MainActivity's Android updater, or a fake in tests)
 * subclasses it, does the work and sets [status]. Only the preview and debug builds have one; a null updater hides the block.
 */
abstract class Updater(val versionName: String, val versionCode: Long) {
    /** Snapshot state, so the screen follows every change. Set from the main thread. */
    var status by mutableStateOf<UpdateStatus>(UpdateStatus.Idle)

    /** "Check for updates": the only thing that contacts GitHub. */
    abstract fun check()
    /** "Download and install" (also "Try again" after a failed download). Only meaningful for an [UpdateStatus.Available] or a failed download. */
    abstract fun download(info: UpdateInfo)
    abstract fun cancelDownload()
    /** Starts Android's installer for the checked file; asks for the install permission first if needed. */
    abstract fun install()
    /** Opens Android's "install unknown apps" screen for this app. */
    abstract fun openInstallSettings()
    /** Opens a link (the CHANGELOG) in the browser. */
    abstract fun openLink(url: String)
}
