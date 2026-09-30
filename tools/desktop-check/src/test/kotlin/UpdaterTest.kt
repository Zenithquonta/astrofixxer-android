import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.ComposeUiTest
import org.astrofixxer.ui.I18n
import org.astrofixxer.ui.UpdateStatus
import org.astrofixxer.ui.Updater
import org.astrofixxer.update.UpdateInfo
import org.astrofixxer.update.UpdateProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** An updater that records what the screen asked for and moves to the state a real one would show. */
class FakeUpdater(initial: UpdateStatus = UpdateStatus.Idle) : Updater("0.1.0-preview", 12) {
    val calls = mutableListOf<String>()
    init { status = initial }
    override fun check() { calls += "check"; status = UpdateStatus.Checking }
    override fun download(info: UpdateInfo) { calls += "download:${info.versionCode}"; status = UpdateStatus.Downloading(info, 10_000_000, 40_000_000) }
    override fun cancelDownload() { calls += "cancel"; (status as? UpdateStatus.Downloading)?.let { status = UpdateStatus.Available(it.info) } }
    override fun install() { calls += "install" }
    override fun openInstallSettings() { calls += "settings" }
    override fun openLink(url: String) { calls += "link:$url" }
}

val updateInfo = UpdateInfo(15, "0.2.0-preview", "org.astrofixxer.preview", "AstroFixxer.apk", "a".repeat(64), 41_943_040, "abcdef1", true,
    "https://github.com/Zenithquonta/astrofixxer-android/releases/download/latest-build/AstroFixxer.apk", "latest-build")
val tempKeyInfo = updateInfo.copy(permanentKey = false)

/** Every updater state, by name, for the tests and the layout audit. */
val updaterStates: List<Pair<String, UpdateStatus>> = listOf(
    "idle" to UpdateStatus.Idle,
    "checking" to UpdateStatus.Checking,
    "up-to-date" to UpdateStatus.UpToDate("0.1.0-preview", 12),
    "available" to UpdateStatus.Available(updateInfo),
    "available-temp-key" to UpdateStatus.Available(tempKeyInfo),
    "available-same-name" to UpdateStatus.Available(updateInfo.copy(versionName = "v0.1.0-preview")),
    "downloading" to UpdateStatus.Downloading(updateInfo, 10_485_760, 41_943_040),
    "downloading-unknown-size" to UpdateStatus.Downloading(updateInfo, 0, -1),
    "verifying" to UpdateStatus.Verifying(updateInfo),
    "ready" to UpdateStatus.ReadyToInstall(updateInfo),
    "ready-temp-key" to UpdateStatus.ReadyToInstall(tempKeyInfo),
    "needs-permission" to UpdateStatus.NeedsInstallPermission(updateInfo),
    "error-offline" to UpdateStatus.Failed(UpdateProblem.Offline),
    "error-timeout" to UpdateStatus.Failed(UpdateProblem.Timeout),
    "error-rate-limit" to UpdateStatus.Failed(UpdateProblem.RateLimited(754)),
    "error-rate-limit-unknown" to UpdateStatus.Failed(UpdateProblem.RateLimited(null)),
    "error-no-release" to UpdateStatus.Failed(UpdateProblem.NotFound),
    "error-no-update-file" to UpdateStatus.Failed(UpdateProblem.NoUpdateFile),
    "error-malformed" to UpdateStatus.Failed(UpdateProblem.Malformed("x")),
    "error-wrong-app" to UpdateStatus.Failed(UpdateProblem.WrongApp("org.astrofixxer")),
    "error-unsafe" to UpdateStatus.Failed(UpdateProblem.Unsafe("x")),
    "error-tls" to UpdateStatus.Failed(UpdateProblem.TlsFailed),
    "error-server" to UpdateStatus.Failed(UpdateProblem.ServerError(503)),
    "error-download" to UpdateStatus.Failed(UpdateProblem.DownloadFailed(1002), updateInfo),
    "error-cancelled" to UpdateStatus.Failed(UpdateProblem.DownloadCancelled, updateInfo),
    "error-low-storage" to UpdateStatus.Failed(UpdateProblem.LowStorage(100_000_000), updateInfo),
    "error-size" to UpdateStatus.Failed(UpdateProblem.SizeMismatch, updateInfo),
    "error-checksum" to UpdateStatus.Failed(UpdateProblem.ChecksumMismatch, updateInfo),
    "error-file-missing" to UpdateStatus.Failed(UpdateProblem.FileMissing, updateInfo),
    "error-no-installer" to UpdateStatus.Failed(UpdateProblem.NoInstaller),
    "error-unexpected" to UpdateStatus.Failed(UpdateProblem.Unexpected("IllegalStateException")),
)

@OptIn(ExperimentalTestApi::class)
class UpdaterTest {
    private fun button(label: String) = hasText(label) and hasClickAction()

    /** Sky & viewing → More, scrolled to the updater block. */
    private fun ComposeUiTest.openUpdates() {
        onNode(button(I18n.t("Sky"))).tap()
        tab(I18n.t("More"))
        onNode(hasText(I18n.t("App updates"))).performScrollTo()
    }

    /** Raw text as written (English), so a Hindi screen that still shows the English sentence is caught. */
    private fun ComposeUiTest.has(text: String) = onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun ComposeUiTest.press(label: String) { onNode(button(I18n.t(label))).performScrollTo().tap(); waitForIdle() }

    /** Shows [status] and returns the fake, with the updater block on screen. */
    private fun ComposeUiTest.show(status: UpdateStatus): FakeUpdater {
        val fake = FakeUpdater(status)
        setContent { AppScreen(Fixtures.state(), updater = fake) }
        openUpdates()
        return fake
    }

    @Test fun blockIsHiddenWithoutAnUpdater() = phoneTest {
        setContent { AppScreen(Fixtures.state()) }
        onNode(button(I18n.t("Sky"))).tap()
        tab(I18n.t("More"))
        assertTrue(!has("App updates"))
        assertTrue(has("Reset all"))
    }

    @Test fun everyStateRendersItsText() = phoneTest {
        val expected = mapOf(
            "idle" to listOf("Installed: 0.1.0-preview (build 12)", "Check for updates", "Nothing about you is sent"),
            "checking" to listOf("Checking for updates…"),
            "up-to-date" to listOf("You have the latest version.", "Check again"),
            "available" to listOf("Update available: 0.2.0-preview (build 15)", "Download size: 40.0 MB", "What's new (CHANGELOG)", "Download and install"),
            "available-same-name" to listOf("Update available: 0.1.0-preview (build 15)", "A newer build of the version you have."),
            "available-temp-key" to listOf("can't be installed over yours", "lose your saved lists", "Continue anyway"),
            "downloading" to listOf("Downloading… 25%", "Cancel"),
            "downloading-unknown-size" to listOf("Downloading…", "Cancel"),
            "verifying" to listOf("Checking that the download is intact…"),
            "ready" to listOf("checked and ready", "Install"),
            "ready-temp-key" to listOf("uninstall AstroFixxer first", "Install"),
            "needs-permission" to listOf("needs your permission", "Open settings"),
            "error-offline" to listOf("Can't reach GitHub", "Check for updates"),
            "error-timeout" to listOf("took too long"),
            "error-rate-limit" to listOf("too many requests. Try again in about 13 min."),
            "error-rate-limit-unknown" to listOf("too many requests. Try again later."),
            "error-no-release" to listOf("No release was found"),
            "error-no-update-file" to listOf("has no update information", "GitHub Releases page"),
            "error-malformed" to listOf("wasn't in the expected form"),
            "error-wrong-app" to listOf("different app"),
            "error-unsafe" to listOf("looked unsafe"),
            "error-tls" to listOf("secure connection to GitHub failed"),
            "error-server" to listOf("error 503"),
            "error-download" to listOf("download failed (code 1002)", "Try again"),
            "error-cancelled" to listOf("download was cancelled", "Try again"),
            "error-low-storage" to listOf("Not enough free space. About 96 MB"),
            "error-size" to listOf("wrong size", "Nothing was installed"),
            "error-checksum" to listOf("doesn't match its checksum", "Nothing was installed"),
            "error-file-missing" to listOf("file is gone"),
            "error-no-installer" to listOf("no app that can install"),
            "error-unexpected" to listOf("Something went wrong (IllegalStateException)"),
        )
        assertEquals("every state has expectations", updaterStates.map { it.first }.toSet(), expected.keys)
        for ((name, status) in updaterStates) for (lang in listOf("en", "hi")) {
            I18n.language = lang
            try {
                val t = FakeUpdater(status)
                setContent { AppScreen(Fixtures.state(), updater = t) }
                openUpdates()
                for (text in expected.getValue(name)) {
                    // English: the sentence is on screen as written. Hindi: that English sentence is gone (translated).
                    if (lang == "en") assertTrue("$name: missing \"$text\"", has(text)) else assertTrue("$name: still English in Hindi: \"$text\"", !has(text))
                }
            } finally {
                I18n.language = "en"
            }
        }
    }

    @Test fun checkButtonAsksTheHostToCheck() = phoneTest {
        val fake = show(UpdateStatus.Idle)
        press("Check for updates")
        assertEquals(listOf("check"), fake.calls)
        assertTrue(has("Checking for updates…"))
    }

    @Test fun downloadAndInstallStartsTheDownload() = phoneTest {
        val fake = show(UpdateStatus.Available(updateInfo))
        press("Download and install")
        assertEquals(listOf("download:15"), fake.calls)
        assertTrue(has("Downloading… 25%"))
        press("Cancel")
        assertEquals(listOf("download:15", "cancel"), fake.calls)
        assertTrue("back to the offer after Cancel", has("Download and install"))
    }

    @Test fun whatsNewOpensTheChangelogAtThatCommit() = phoneTest {
        val fake = show(UpdateStatus.Available(updateInfo))
        press("What's new (CHANGELOG)")
        assertEquals(listOf("link:https://github.com/Zenithquonta/astrofixxer-android/blob/abcdef1/CHANGELOG.md"), fake.calls)
    }

    @Test fun temporaryKeyNeedsADeliberateSecondTap() = phoneTest {
        val fake = show(UpdateStatus.Available(tempKeyInfo))
        assertTrue("no one-tap install", !has("Download and install"))
        assertTrue(!has("Download anyway"))
        press("Continue anyway")
        assertTrue(fake.calls.isEmpty())
        assertTrue(has("Only continue if you accept losing your saved lists."))
        press("Download anyway")
        assertEquals(listOf("download:15"), fake.calls)
    }

    @Test fun readyNeedsPermissionAndFailureButtons() = phoneTest {
        val ready = show(UpdateStatus.ReadyToInstall(updateInfo))
        press("Install")
        assertEquals(listOf("install"), ready.calls)
    }

    @Test fun permissionButtonOpensSettings() = phoneTest {
        val fake = show(UpdateStatus.NeedsInstallPermission(updateInfo))
        press("Open settings")
        assertEquals(listOf("settings"), fake.calls)
    }

    @Test fun failedDownloadOffersRetryOfTheSameUpdate() = phoneTest {
        val fake = show(UpdateStatus.Failed(UpdateProblem.ChecksumMismatch, updateInfo))
        press("Try again")
        assertEquals(listOf("download:15"), fake.calls)
    }

    @Test fun failedCheckOffersACheckAgain() = phoneTest {
        val fake = show(UpdateStatus.Failed(UpdateProblem.Offline))
        press("Check for updates")
        assertEquals(listOf("check"), fake.calls)
        assertTrue(!has("Try again"))
    }

    @Test fun upToDateCanBeCheckedAgain() = phoneTest {
        val fake = show(UpdateStatus.UpToDate("0.1.0-preview", 12))
        press("Check again")
        assertEquals(listOf("check"), fake.calls)
    }
}
