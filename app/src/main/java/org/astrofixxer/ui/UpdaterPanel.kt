package org.astrofixxer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astrofixxer.update.UpdateInfo
import org.astrofixxer.update.UpdateProblem
import org.astrofixxer.update.Versions
import java.util.Locale

/** Sky & viewing → More → "App updates". Every state is plain text and full-width buttons, so it reads the same in night mode. */
@Composable
internal fun UpdaterPanel(updater: Updater) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("App updates"), color = c.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(t("Installed: %s (build %d)").format(Versions.displayName(updater.versionName), updater.versionCode), color = c.onSurface, fontSize = 14.sp)
        when (val s = updater.status) {
            UpdateStatus.Idle -> {
                Note(t("Checks GitHub only when you tap the button. Nothing about you is sent."))
                Action(t("Check for updates"), updater::check)
            }
            UpdateStatus.Checking -> Text(t("Checking for updates…"), color = c.onSurface, fontSize = 14.sp)
            is UpdateStatus.UpToDate -> {
                Text(t("You have the latest version."), color = c.onSurface, fontSize = 14.sp)
                SecondaryAction(t("Check again"), updater::check)
            }
            is UpdateStatus.Available -> AvailableBlock(updater, s.info)
            is UpdateStatus.Downloading -> {
                val known = s.total > 0
                Text(if (known) t("Downloading… %d%%").format((s.downloaded * 100 / s.total).coerceIn(0, 100)) else t("Downloading…"),
                    color = c.onSurface, fontSize = 14.sp)
                LinearProgressIndicator(progress = if (known) (s.downloaded.toFloat() / s.total).coerceIn(0f, 1f) else 0f, modifier = Modifier.fillMaxWidth())
                SecondaryAction(t("Cancel"), updater::cancelDownload)
            }
            is UpdateStatus.Verifying -> Text(t("Checking that the download is intact…"), color = c.onSurface, fontSize = 14.sp)
            is UpdateStatus.ReadyToInstall -> {
                Text(t("The download is checked and ready. Android will ask you to confirm the install."), color = c.onSurface, fontSize = 14.sp)
                if (!s.info.permanentKey) Warning(t("If Android says the app can't be installed, this build has a different signature: uninstall AstroFixxer first. Saved lists will be lost."))
                Action(t("Install"), updater::install)
            }
            is UpdateStatus.NeedsInstallPermission -> {
                Text(t("Android needs your permission before AstroFixxer can install updates. Tap the button, allow it for AstroFixxer, then come back."),
                    color = c.onSurface, fontSize = 14.sp)
                Action(t("Open settings"), updater::openInstallSettings)
            }
            is UpdateStatus.Failed -> {
                Warning(problemText(s.problem))
                val retry = s.retry
                if (retry != null) Action(t("Try again")) { updater.download(retry) } else Action(t("Check for updates"), updater::check)
            }
        }
    }
}

@Composable
private fun AvailableBlock(updater: Updater, info: UpdateInfo) {
    val c = MaterialTheme.colorScheme
    // A build that can't install over the user's copy needs a deliberate second tap.
    var understood by remember(info) { mutableStateOf(false) }
    Text(t("Update available: %s (build %d)").format(Versions.displayName(info.versionName), info.versionCode), color = c.onSurface, fontSize = 14.sp,
        fontWeight = FontWeight.Bold)
    if (Versions.sameName(info.versionName, updater.versionName)) Note(t("A newer build of the version you have."))
    Text(t("Download size: %s MB").format(megabytes(info.size)), color = c.onSurfaceVariant, fontSize = 14.sp)
    TextButton(onClick = { updater.openLink(info.changelogUrl) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("What's new (CHANGELOG)")) }
    if (info.permanentKey) {
        Action(t("Download and install")) { updater.download(info) }
    } else {
        Warning(t("This build can't be installed over yours. It is signed with a temporary key, so Android will refuse it. To use it you would have to uninstall AstroFixxer first, and you would lose your saved lists."))
        if (!understood) SecondaryAction(t("Continue anyway")) { understood = true }
        else {
            Note(t("Only continue if you accept losing your saved lists."))
            Action(t("Download anyway")) { updater.download(info) }
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

@Composable
private fun Warning(text: String) = Text(text, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)

@Composable
private fun Action(label: String, onClick: () -> Unit) =
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(label) }

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(label) }

private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1_048_576.0)

/** One plain sentence per problem (each has a Hindi entry in [I18n]). */
internal fun problemText(p: UpdateProblem): String = when (p) {
    UpdateProblem.Offline -> t("Can't reach GitHub. Check your internet connection and try again.")
    UpdateProblem.Timeout -> t("GitHub took too long to answer. Try again in a moment.")
    is UpdateProblem.RateLimited -> p.retryInSeconds?.let { t("GitHub says there were too many requests. Try again in about %d min.").format(maxOf(1L, (it + 59) / 60)) }
        ?: t("GitHub says there were too many requests. Try again later.")
    UpdateProblem.NotFound -> t("No release was found on GitHub.")
    UpdateProblem.NoUpdateFile -> t("The newest release has no update information, so it can't be installed from here. Get it from the GitHub Releases page.")
    is UpdateProblem.Malformed -> t("GitHub's answer wasn't in the expected form. Try again later.")
    is UpdateProblem.WrongApp -> t("The newest release is for a different app, so it was not offered.")
    is UpdateProblem.Unsafe -> t("The update was refused because its download address or size looked unsafe.")
    UpdateProblem.TlsFailed -> t("The secure connection to GitHub failed. Check your network and the date and time, then try again.")
    is UpdateProblem.ServerError -> t("GitHub answered with error %d. Try again later.").format(p.code)
    is UpdateProblem.DownloadFailed -> t("The download failed (code %d). Try again.").format(p.code)
    UpdateProblem.DownloadCancelled -> t("The download was cancelled.")
    is UpdateProblem.LowStorage -> t("Not enough free space. About %d MB is needed.").format((p.neededBytes + 1_048_575) / 1_048_576)
    UpdateProblem.SizeMismatch -> t("The downloaded file is the wrong size, so it was deleted. Nothing was installed.")
    UpdateProblem.ChecksumMismatch -> t("The downloaded file doesn't match its checksum, so it was deleted. Nothing was installed.")
    UpdateProblem.FileMissing -> t("The downloaded file is gone. Download it again.")
    UpdateProblem.NoInstaller -> t("This phone has no app that can install updates.")
    is UpdateProblem.Unexpected -> t("Something went wrong (%s).").format(p.detail)
}
