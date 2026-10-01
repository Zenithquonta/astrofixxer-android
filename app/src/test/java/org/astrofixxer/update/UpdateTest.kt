package org.astrofixxer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

private const val TAG = "latest-build"
private const val BASE = "https://github.com/Zenithquonta/astrofixxer-android/releases/download/$TAG/"
private const val APP_ID = "org.astrofixxer.preview"
private val APK_BYTES = "pretend this is an apk".toByteArray()
private val APK_SHA = Integrity.sha256Hex(APK_BYTES)

private fun updateJson(
    code: String = "1042", name: String = "0.1.0-preview", appId: String = APP_ID, apk: String = "AstroFixxer.apk",
    sha: String = APK_SHA, size: String = "${APK_BYTES.size}", commit: String = "abcdef1234567", permanent: String = "true",
) = """{"versionCode":$code,"versionName":"$name","applicationId":"$appId","apk":"$apk","sha256":"$sha","size":$size,"commit":"$commit","permanentKey":$permanent}"""

private fun releaseJson(
    tag: String = TAG, updateUrl: String = BASE + "update.json", apkUrl: String = BASE + "AstroFixxer.apk", apkSize: Int = APK_BYTES.size,
    includeUpdate: Boolean = true, updateSize: Int = 300,
): String {
    val assets = mutableListOf<String>()
    assets += """{"name":"notes.txt","size":10,"browser_download_url":"${BASE}notes.txt"}"""
    if (includeUpdate) assets += """{"name":"update.json","size":$updateSize,"browser_download_url":"$updateUrl"}"""
    assets += """{"name":"AstroFixxer.apk","size":$apkSize,"browser_download_url":"$apkUrl"}"""
    return """{"tag_name":"$tag","name":"AstroFixxer","assets":[${assets.joinToString(",")}]}"""
}

/** Answers each URL from a map; a Throwable value is thrown. */
private class FakeHttp(private val answers: MutableMap<String, Any>) : HttpGet {
    val requested = mutableListOf<String>()
    override fun get(url: String, accept: String, maxBytes: Int): HttpResponse {
        requested += url
        return when (val a = answers[url] ?: HttpResponse(404, emptyMap(), ByteArray(0))) {
            is Throwable -> throw a
            is HttpResponse -> a
            else -> error("bad fake")
        }
    }
}

private fun ok(text: String) = HttpResponse(200, emptyMap(), text.toByteArray())
private val LOCAL = LocalBuild(APP_ID, 1, "0.1.0-preview")
private const val API = UpdateConfig.LATEST_RELEASE_URL

private fun check(answers: Map<String, Any>, local: LocalBuild = LOCAL, now: Long = 1_000_000_000_000L): CheckOutcome =
    UpdateChecker(FakeHttp(answers.toMutableMap())) { now }.check(local)

private fun failure(o: CheckOutcome): UpdateProblem = (o as? CheckOutcome.Failed)?.problem ?: fail("expected a failure, got $o").let { error("unreachable") }

class ReleaseParsingTest {
    @Test fun picksUpdateJsonAndIgnoresOtherAssets() {
        val r = UpdateParser.parseRelease(releaseJson())
        assertEquals(TAG, r.tag)
        assertEquals(listOf("notes.txt", "update.json", "AstroFixxer.apk"), r.assets.map { it.name })
        val o = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson())))
        assertTrue(o is CheckOutcome.Available)
    }

    @Test fun assetsWithoutNameOrAddressAreSkipped() {
        val r = UpdateParser.parseRelease("""{"tag_name":"v1","assets":[{"size":1},{"name":"a"},{"name":"b","browser_download_url":"u"},5]}""")
        assertEquals(listOf("b"), r.assets.map { it.name })
    }

    @Test fun releaseWithoutAssetsListIsJustEmpty() {
        assertTrue(UpdateParser.parseRelease("""{"tag_name":"v1"}""").assets.isEmpty())
    }

    @Test fun badReleaseJsonIsMalformed() {
        for (bad in listOf("", "not json", "[]", "{}", """{"tag_name":5}""", """{"tag_name":""}""", """{"tag_name":"v","assets":{}}""")) {
            try { UpdateParser.parseRelease(bad); fail("accepted: $bad") } catch (e: MalformedException) { /* expected */ }
        }
    }

    @Test fun updateJsonFieldsAreParsedAndNormalised() {
        val i = UpdateParser.parseUpdateJson(updateJson(sha = APK_SHA.uppercase(), commit = "ABCDEF1234567"))
        assertEquals(1042L, i.versionCode); assertEquals("0.1.0-preview", i.versionName); assertEquals(APP_ID, i.applicationId)
        assertEquals("AstroFixxer.apk", i.apk); assertEquals(APK_SHA, i.sha256); assertEquals(APK_BYTES.size.toLong(), i.size)
        assertEquals("abcdef1234567", i.commit); assertTrue(i.permanentKey)
        assertEquals("https://github.com/Zenithquonta/astrofixxer-android/blob/abcdef1234567/CHANGELOG.md", i.changelogUrl)
    }

    @Test fun updateJsonWithBadFieldsIsMalformed() {
        val bad = listOf(
            updateJson(code = "0"), updateJson(code = "-3"), updateJson(code = "\"1042\""), updateJson(code = "1042.0"), updateJson(code = "99999999999"),
            updateJson(name = ""), updateJson(name = "a".repeat(65)), updateJson(name = "x\\ny"),
            updateJson(appId = "nodots"), updateJson(appId = "org..x"), updateJson(appId = "org.a b"),
            updateJson(apk = "../evil.apk"), updateJson(apk = "a/b.apk"), updateJson(apk = "x.txt"), updateJson(apk = ".apk"), updateJson(apk = "a%2fb.apk"),
            updateJson(sha = "abc"), updateJson(sha = "z".repeat(64)), updateJson(sha = APK_SHA + "0"),
            updateJson(size = "0"), updateJson(size = "-1"), updateJson(size = "\"5\""), updateJson(size = "${UpdateConfig.MAX_APK_BYTES + 1}"),
            updateJson(permanent = "\"yes\""), updateJson(permanent = "1"),
            "{}", "[]", "nope", "",
        )
        for (b in bad) try { UpdateParser.parseUpdateJson(b); fail("accepted: $b") } catch (e: MalformedException) { /* expected */ }
    }

    @Test fun missingOrOddOptionalFieldsAreSafe() {
        val noCommitNoFlag = """{"versionCode":5,"versionName":"v1","applicationId":"$APP_ID","apk":"a.apk","sha256":"$APK_SHA","size":9}"""
        val i = UpdateParser.parseUpdateJson(noCommitNoFlag)
        assertNull(i.commit)
        assertFalse("a missing flag must not count as a permanent key", i.permanentKey)
        assertTrue(i.changelogUrl.endsWith("/blob/main/CHANGELOG.md"))
        assertNull(UpdateParser.parseUpdateJson(updateJson(commit = "not-hex!")).commit)
        // a commit can't smuggle anything into the changelog link
        assertTrue(UrlPolicy.isRepoLink(UpdateParser.parseUpdateJson(updateJson(commit = "../../x")).changelogUrl))
    }
}

class VersionTest {
    @Test fun newerCodeOfSameAppIsAvailable() {
        val o = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "2"))), LocalBuild(APP_ID, 1, "0.1.0"))
        val info = (o as CheckOutcome.Available).info
        assertEquals(BASE + "AstroFixxer.apk", info.apkUrl); assertEquals(TAG, info.releaseTag)
    }

    @Test fun equalOrOlderCodeIsUpToDate() {
        for (local in listOf(1042L, 1043L, 99999L)) {
            val o = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson())), LocalBuild(APP_ID, local, "0.1.0-preview"))
            assertTrue("local $local", o is CheckOutcome.UpToDate)
        }
    }

    @Test fun versionNameNeverDecidesAnUpdate() {
        // A "newer" name with the same code is not an update; an "older" name with a higher code is.
        val same = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "5", name = "9.9.9"))), LocalBuild(APP_ID, 5, "0.1.0"))
        assertTrue(same is CheckOutcome.UpToDate)
        val newer = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "6", name = "0.0.1"))), LocalBuild(APP_ID, 5, "9.9.9"))
        assertTrue(newer is CheckOutcome.Available)
    }

    @Test fun otherApplicationIdIsRefusedEvenWithHigherCode() {
        val o = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "5000", appId = "org.astrofixxer"))))
        assertEquals("org.astrofixxer", (failure(o) as UpdateProblem.WrongApp).remoteApplicationId)
        // and when it is not newer either
        assertTrue(failure(check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "1", appId = "com.evil.app"))))) is UpdateProblem.WrongApp)
    }

    @Test fun nameHelpersToleratePrefixV() {
        assertEquals("0.2.0", Versions.displayName("v0.2.0")); assertEquals("0.2.0", Versions.displayName(" V0.2.0 "))
        assertEquals("vega", Versions.displayName("vega")); assertEquals("v", Versions.displayName("v"))
        assertTrue(Versions.sameName("v0.2.0-preview", "0.2.0-PREVIEW")); assertFalse(Versions.sameName("0.2.0", "0.2.1"))
        assertTrue(Versions.compareNames("0.10.0", "0.9.0") > 0); assertTrue(Versions.compareNames("v0.1", "0.1.0") == 0)
        assertTrue(Versions.compareNames("1.0.0-preview", "1.0.1") < 0); assertEquals(0, Versions.compareNames("abc", "xyz"))
        assertEquals(0, Versions.compareNames("", "")); assertEquals(0, Versions.compareNames("1..2", "1.2"))
    }
}

class UrlPolicyTest {
    private val good = BASE + "update.json"

    @Test fun acceptsTheReleaseDownloadAddress() {
        assertTrue(UrlPolicy.isReleaseDownloadUrl(good))
        assertTrue(UrlPolicy.isReleaseDownloadUrl("${UpdateConfig.DOWNLOAD_PREFIX}v0.1.0/AstroFixxer-v0.1.0.apk"))
        assertTrue(UrlPolicy.isAssetOfRelease(good, TAG, "update.json"))
    }

    @Test fun rejectsEverythingElse() {
        val p = "Zenithquonta/astrofixxer-android/releases/download"
        val bad = listOf(
            "http://github.com/$p/$TAG/update.json",
            "https://github.com.evil.com/$p/$TAG/update.json",
            "https://github.com@evil.com/$p/$TAG/update.json",
            "https://evil.com/https://github.com/$p/$TAG/update.json",
            "https://github.com/Other/astrofixxer-android/releases/download/$TAG/update.json",
            "https://github.com/Zenithquonta/astrofixxer-android-evil/releases/download/$TAG/update.json",
            "https://github.com/zenithquonta/astrofixxer-android/releases/download/$TAG/update.json",
            "https://github.com/Zenithquonta/astrofixxer-android/releases/download/../../../evil/x/a.apk",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/../update.json",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/..",
            "${UpdateConfig.DOWNLOAD_PREFIX}%2e%2e/update.json",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/%2e%2e%2fx.apk",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/x.apk@evil.com",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG\\..\\x.apk",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/update.json?x=1",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/update.json#x",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/update.json ",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG//update.json",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG/a/b.apk",
            "${UpdateConfig.DOWNLOAD_PREFIX}$TAG",
            "${UpdateConfig.DOWNLOAD_PREFIX}",
            "HTTPS://github.com/$p/$TAG/update.json",
            "https://github.com:8443/$p/$TAG/update.json",
            "file:///sdcard/update.json", "javascript:alert(1)", "", "//github.com/$p/$TAG/update.json",
        )
        for (b in bad) assertFalse("accepted: $b", UrlPolicy.isReleaseDownloadUrl(b))
    }

    @Test fun anAssetMustBeOfThisReleaseAndNameItself() {
        assertFalse(UrlPolicy.isAssetOfRelease(BASE + "update.json", "v9.9.9", "update.json"))
        assertFalse(UrlPolicy.isAssetOfRelease(BASE + "AstroFixxer.apk", TAG, "update.json"))
        assertFalse(UrlPolicy.isAssetOfRelease(BASE + "update.json", "bad/tag", "update.json"))
        assertNull(UrlPolicy.expectedAssetUrl("a b", "x"))
    }

    @Test fun redirectsMayOnlyGoToGitHubHostsOverHttps() {
        for (ok in listOf("https://github.com/x", "https://api.github.com/x", "https://objects.githubusercontent.com/x", "https://release-assets.githubusercontent.com/a?b=c", "https://GitHub.com/x"))
            assertNotNull(ok, UrlPolicy.checkedUri(ok))
        for (bad in listOf(
            "http://github.com/x", "https://github.com.evil.com/x", "https://evilgithub.com/x", "https://githubusercontent.com.evil.com/x",
            "https://evil.com/github.com", "https://user@github.com/x", "https://github.com:81/x", "https://.githubusercontent.com/x",
            "https://github.com\\@evil.com/", "ftp://github.com/x", "https://raw.githubusercontent.com.evil.com/x", "https:///x", "not a url",
        )) try { UrlPolicy.checkedUri(bad); fail("accepted: $bad") } catch (e: UnsafeUrlException) { /* expected */ }
    }

    @Test fun onlyPlainHttpsAddressesReachTheNetwork() {
        val http = HttpsGet("test")
        for (u in listOf("http://api.github.com/x", "https://evil.com/x", "file:///etc/passwd", "https://github.com@evil.com/"))
            try { http.get(u, "*/*", 10); fail("connected to $u") } catch (e: UnsafeUrlException) { /* expected, before any connection */ }
    }

    @Test fun repoLinkForTheBrowser() {
        assertTrue(UrlPolicy.isRepoLink("https://github.com/Zenithquonta/astrofixxer-android/blob/main/CHANGELOG.md"))
        assertFalse(UrlPolicy.isRepoLink("https://github.com/Other/repo/blob/main/CHANGELOG.md"))
        assertFalse(UrlPolicy.isRepoLink("http://github.com/Zenithquonta/astrofixxer-android/x"))
        assertFalse(UrlPolicy.isRepoLink("https://github.com/Zenithquonta/astrofixxer-android/x y"))
    }
}

class SizeCapTest {
    @Test fun releaseWithHostileAddressesIsRefusedNotTrusted() {
        for (evil in listOf("https://evil.com/update.json", "http://github.com/Zenithquonta/astrofixxer-android/releases/download/$TAG/update.json",
            "https://github.com/Zenithquonta/astrofixxer-android/releases/download/other-tag/update.json")) {
            val net = FakeHttp(mutableMapOf(API to ok(releaseJson(updateUrl = evil)), evil to ok(updateJson())))
            val o = UpdateChecker(net).check(LOCAL)
            assertTrue(evil, failure(o) is UpdateProblem.Unsafe)
            assertEquals("must not fetch it", listOf(API), net.requested)
        }
        val o = check(mapOf(API to ok(releaseJson(apkUrl = "https://evil.com/AstroFixxer.apk")), BASE + "update.json" to ok(updateJson(code = "5000"))))
        assertTrue(failure(o) is UpdateProblem.Unsafe)
    }

    @Test fun updateJsonOverTheCapIsRefused() {
        val big = check(mapOf(API to ok(releaseJson(updateSize = UpdateConfig.MAX_UPDATE_JSON_BYTES + 1))))
        assertTrue(failure(big) is UpdateProblem.Unsafe)
        val streamedTooMuch = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to TooLargeException()))
        assertTrue(failure(streamedTooMuch) is UpdateProblem.Unsafe)
    }

    @Test fun readerStopsAtTheCap() {
        assertEquals(10, HttpsGet.readCapped(ByteArrayInputStream(ByteArray(10)), 10).size)
        try { HttpsGet.readCapped(ByteArrayInputStream(ByteArray(11)), 10); fail() } catch (e: TooLargeException) { /* expected */ }
        assertEquals(4, HttpsGet.readUpTo(ByteArrayInputStream(ByteArray(100)), 4).size)
    }

    @Test fun apkSizeMustMatchTheReleaseAssetAndTheCap() {
        val mismatch = check(mapOf(API to ok(releaseJson(apkSize = APK_BYTES.size + 1)), BASE + "update.json" to ok(updateJson(code = "5000"))))
        assertTrue(failure(mismatch) is UpdateProblem.Unsafe)
        val huge = UpdateConfig.MAX_APK_BYTES + 1
        val tooBig = check(mapOf(API to ok(releaseJson(apkSize = huge.toInt())), BASE + "update.json" to ok(updateJson(code = "5000", size = "$huge"))))
        assertTrue(failure(tooBig) is UpdateProblem.Malformed)
    }

    @Test fun apkNamedInUpdateJsonMustExistInTheRelease() {
        val o = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "5000", apk = "Other.apk"))))
        assertTrue(failure(o) is UpdateProblem.Malformed)
    }

    @Test fun copyStopsAtTheLimit() {
        val out = ByteArrayOutputStream()
        try { Integrity.copyAndHash(ByteArrayInputStream(ByteArray(101)), out, 100); fail() } catch (e: TooLargeException) { /* expected */ }
        assertEquals(100L, Integrity.copyAndHash(ByteArrayInputStream(ByteArray(100)), ByteArrayOutputStream(), 100).bytes)
    }
}

class Sha256Test {
    private fun info(sha: String = APK_SHA, size: Long = APK_BYTES.size.toLong()) =
        UpdateInfo(2, "1", APP_ID, "a.apk", sha, size, null, true)

    @Test fun matchAndMismatch() {
        assertTrue(Integrity.sha256Matches(APK_SHA, APK_SHA))
        assertFalse(Integrity.sha256Matches(APK_SHA, Integrity.sha256Hex("other".toByteArray())))
        assertFalse(Integrity.sha256Matches(APK_SHA, ""))
        assertFalse(Integrity.sha256Matches("", ""))
        assertFalse(Integrity.sha256Matches(APK_SHA.dropLast(1), APK_SHA.dropLast(1)))
    }

    @Test fun hexCaseDoesNotMatter() {
        assertTrue(Integrity.sha256Matches(APK_SHA.uppercase(), APK_SHA))
        assertTrue(Integrity.sha256Matches(APK_SHA, APK_SHA.uppercase()))
    }

    @Test fun knownDigest() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Integrity.sha256Hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Integrity.sha256Hex("abc".toByteArray()))
    }

    @Test fun copyAndHashHashesWhatWasWritten() {
        val out = ByteArrayOutputStream()
        val c = Integrity.copyAndHash(ByteArrayInputStream(APK_BYTES), out, 1000)
        assertEquals(APK_BYTES.toList(), out.toByteArray().toList())
        assertEquals(APK_SHA, c.sha256)
        assertNull(Integrity.verify(c, info()))
    }

    @Test fun verifyReportsSizeAndChecksumProblems() {
        val c = Integrity.Copied(APK_BYTES.size.toLong(), APK_SHA)
        assertNull(Integrity.verify(c, info(sha = APK_SHA.uppercase())))
        assertTrue(Integrity.verify(c, info(sha = "0".repeat(64))) is UpdateProblem.ChecksumMismatch)
        assertTrue(Integrity.verify(c, info(size = 5)) is UpdateProblem.SizeMismatch)
        assertTrue(Integrity.verify(Integrity.Copied(APK_BYTES.size.toLong(), "nothex"), info()) is UpdateProblem.ChecksumMismatch)
    }
}

class ErrorMappingTest {
    private val ok = mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "5000")))

    private fun problem(answers: Map<String, Any>, now: Long = 1_000_000_000_000L) = failure(check(answers, now = now))
    private fun status(code: Int, headers: Map<String, String> = emptyMap(), body: String = "") = HttpResponse(code, headers, body.toByteArray())

    @Test fun offlineAndTimeoutAndTls() {
        assertTrue(problem(mapOf(API to UnknownHostException("api.github.com"))) is UpdateProblem.Offline)
        assertTrue(problem(mapOf(API to ConnectException("refused"))) is UpdateProblem.Offline)
        assertTrue(problem(mapOf(API to IOException("Connection reset"))) is UpdateProblem.Offline)
        assertTrue(problem(mapOf(API to SocketTimeoutException("read timed out"))) is UpdateProblem.Timeout)
        assertTrue(problem(mapOf(API to SSLHandshakeException("bad cert"))) is UpdateProblem.TlsFailed)
        assertTrue(problem(mapOf(API to IllegalStateException("boom"))) is UpdateProblem.Unexpected)
        // the same during the second request
        assertTrue(problem(mapOf(API to ok(releaseJson()), BASE + "update.json" to SocketTimeoutException())) is UpdateProblem.Timeout)
        assertTrue(problem(mapOf(API to ok(releaseJson()), BASE + "update.json" to UnknownHostException())) is UpdateProblem.Offline)
    }

    @Test fun rateLimit403WithResetTime() {
        val now = 1_000_000_000_000L
        val reset = now / 1000 + 754
        val p = problem(mapOf(API to status(403, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "$reset"))), now)
        assertEquals(754L, (p as UpdateProblem.RateLimited).retryInSeconds)
    }

    @Test fun rateLimit429WithRetryAfterSecondsOrDate() {
        assertEquals(60L, (problem(mapOf(API to status(429, mapOf("Retry-After" to "60")))) as UpdateProblem.RateLimited).retryInSeconds)
        val now = 1_000_000_000_000L // 2001-09-09T01:46:40Z
        val date = "Sun, 09 Sep 2001 01:48:40 GMT"
        assertEquals(120L, (problem(mapOf(API to status(429, mapOf("Retry-After" to date))), now) as UpdateProblem.RateLimited).retryInSeconds)
    }

    @Test fun rateLimitWithoutAnyTimeStillSaysRateLimited() {
        assertNull((problem(mapOf(API to status(429))) as UpdateProblem.RateLimited).retryInSeconds)
        assertNull((problem(mapOf(API to status(403, body = "API rate limit exceeded for 1.2.3.4"))) as UpdateProblem.RateLimited).retryInSeconds)
        assertNull((problem(mapOf(API to status(429, mapOf("Retry-After" to "soon")))) as UpdateProblem.RateLimited).retryInSeconds)
    }

    @Test fun retryTimeIsClamped() {
        val now = 1_000_000_000_000L
        assertEquals(0L, (problem(mapOf(API to status(403, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "5"))), now) as UpdateProblem.RateLimited).retryInSeconds)
        assertEquals(86_400L, (problem(mapOf(API to status(429, mapOf("Retry-After" to "99999999")))) as UpdateProblem.RateLimited).retryInSeconds)
    }

    @Test fun otherForbiddenIsNotCalledRateLimit() {
        assertEquals(403, (problem(mapOf(API to status(403, mapOf("X-RateLimit-Remaining" to "42"), body = "Forbidden"))) as UpdateProblem.ServerError).code)
        // a stale reset header is ignored when requests remain
        assertNull((problem(mapOf(API to status(429, mapOf("X-RateLimit-Remaining" to "42", "X-RateLimit-Reset" to "9999999999")))) as UpdateProblem.RateLimited).retryInSeconds)
    }

    @Test fun notFoundIsNoRelease() {
        assertTrue(problem(mapOf(API to status(404))) is UpdateProblem.NotFound)
        assertTrue(problem(emptyMap()) is UpdateProblem.NotFound)
    }

    @Test fun otherHttpStatuses() {
        assertEquals(500, (problem(mapOf(API to status(500))) as UpdateProblem.ServerError).code)
        assertEquals(503, (problem(mapOf(API to status(503))) as UpdateProblem.ServerError).code)
        assertEquals(301, (problem(mapOf(API to status(301))) as UpdateProblem.ServerError).code)
        assertEquals(500, (problem(mapOf(API to ok(releaseJson()), BASE + "update.json" to status(500))) as UpdateProblem.ServerError).code)
    }

    @Test fun releaseWithoutUpdateJson() {
        assertTrue(problem(mapOf(API to ok(releaseJson(includeUpdate = false)))) is UpdateProblem.NoUpdateFile)
        assertTrue(problem(mapOf(API to ok("""{"tag_name":"v0.1.0","assets":[]}"""))) is UpdateProblem.NoUpdateFile)
        // update.json listed but gone (404 on download)
        assertTrue(problem(mapOf(API to ok(releaseJson()))) is UpdateProblem.NoUpdateFile)
    }

    @Test fun malformedJson() {
        assertTrue(problem(mapOf(API to ok("<html>oops</html>"))) is UpdateProblem.Malformed)
        assertTrue(problem(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok("{not json"))) is UpdateProblem.Malformed)
        assertTrue(problem(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok("""{"versionCode":"x"}"""))) is UpdateProblem.Malformed)
        assertTrue(problem(mapOf(API to ok(releaseJson()), BASE + "update.json" to HttpResponse(200, emptyMap(), byteArrayOf(0xff.toByte(), 0xfe.toByte())))) is UpdateProblem.Malformed)
    }

    @Test fun successReadsBothFiles() {
        val net = FakeHttp(ok.toMutableMap())
        val o = UpdateChecker(net).check(LOCAL)
        assertTrue(o is CheckOutcome.Available)
        assertEquals(listOf(API, BASE + "update.json"), net.requested)
    }

    @Test fun permanentKeyFalseIsCarried() {
        val o = check(mapOf(API to ok(releaseJson()), BASE + "update.json" to ok(updateJson(code = "5000", permanent = "false"))))
        assertFalse((o as CheckOutcome.Available).info.permanentKey)
    }
}
