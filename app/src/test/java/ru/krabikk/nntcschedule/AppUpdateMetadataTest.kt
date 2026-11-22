package ru.krabikk.nntcschedule

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.UnknownHostException

class AppUpdateMetadataTest {
    private val hash = "1234567890abcdef".repeat(4)
    private val tag = "v3.1.1"
    private val assetName = "NNTC-Schedule-v3.1.1.apk"
    private val prefix = "https://github.com/Krabikk171/NNTC-Schedule/releases/download/$tag/"

    private fun release(digest: String = "sha256:$hash", url: String = "$prefix$assetName") = JSONObject(
        """{
            "tag_name":"$tag", "draft":false, "prerelease":false,
            "html_url":"https://github.com/Krabikk171/NNTC-Schedule/releases/tag/$tag",
            "body":"Новые возможности",
            "assets":[{
              "name":"$assetName", "state":"uploaded", "size":150000,
              "digest":"$digest", "browser_download_url":"$url"
            }]
        }""".trimIndent()
    )

    private fun manifest(code: Long = 36) = JSONObject(
        """{
            "versionName":"3.1.1", "versionCode":$code,
            "packageName":"ru.krabikk.nntcschedule",
            "apkAssetName":"$assetName"
        }""".trimIndent()
    )

    @Test fun acceptsValidRelease() {
        val offer = AppUpdateRepository.parseOffer(release(), manifest())
        assertEquals(36L, offer?.versionCode)
        assertEquals(hash, offer?.apkSha256)
    }

    @Test fun rejectsUnknownSignerOriginOrMissingDigest() {
        assertNull(AppUpdateRepository.parseOffer(release(digest = ""), manifest()))
        assertNull(AppUpdateRepository.parseOffer(
            release(url = "https://example.com/malicious.apk"), manifest()))
    }

    @Test fun rejectsInvalidPackageOrVersionCode() {
        assertNull(AppUpdateRepository.parseOffer(release(), manifest(0)))
        assertNull(AppUpdateRepository.parseOffer(release(), manifest().put("packageName", "other.package")))
    }

    @get:Rule val folder = TemporaryFolder()

    private fun check(code: Long = 35, read: (String) -> String): AppUpdateOffer? = runBlocking {
        AppUpdateRepository.checkReleases(AppUpdateChannel.STABLE, code) { url, _ -> read(url) }
    }

    private fun releaseWithManifest(): String {
        val json = release()
        json.getJSONArray("assets").put(JSONObject()
            .put("name", "update.json").put("state", "uploaded")
            .put("browser_download_url", prefix + "update.json"))
        return json.toString()
    }

    @Test fun offlineCheckSkipsNetworkAndNextCheckCanSucceed() {
        assertThrows(IOException::class.java) {
            runBlocking {
                AppUpdateRepository.checkReleases(AppUpdateChannel.STABLE, 36, online = false) { _, _ ->
                    error("Offline check attempted network access")
                }
            }
        }
        assertNull(check(36) { if (it.endsWith("update.json")) manifest().toString() else releaseWithManifest() })
    }

    @Test(timeout = 3000) fun deadlineCancelsPendingManifestAndReportsRetryableError() {
        var cancelled = false
        val failure = assertThrows(IOException::class.java) {
            runBlocking {
                AppUpdateRepository.checkReleases(AppUpdateChannel.STABLE, 36, timeoutMillis = 150) { url, _ ->
                    if (url.endsWith("update.json")) {
                        try { kotlinx.coroutines.awaitCancellation() } finally { cancelled = true }
                    }
                    releaseWithManifest()
                }
            }
        }
        assertTrue(cancelled)
        assertTrue(failure.message.orEmpty().contains("вовремя"))
    }

    @Test fun installedVersionIsCurrent() {
        assertNull(check(36) { if (it.endsWith("update.json")) manifest().toString() else releaseWithManifest() })
    }

    @Test fun legacyReleaseWithoutManifestIsSkipped() {
        for (legacy in listOf("v3.0.0", "v3.1.0-beta.1", "v3.1.0")) {
            assertNull(check { release().put("tag_name", legacy).toString() })
        }
    }

    @Test fun laterReleaseRequiresManifest() {
        assertThrows(IllegalArgumentException::class.java) { check { release().toString() } }
    }

    @Test fun networkFailureCanBeRetried() {
        val error = assertThrows(IOException::class.java) { check { throw UnknownHostException() } }
        assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is UnknownHostException })
        assertEquals(36L, check { if (it.endsWith("update.json")) manifest().toString() else releaseWithManifest() }?.versionCode)
    }

    @Test fun releaseListHttpFailureIsReported() {
        val error = assertThrows(IOException::class.java) { check { throw IOException("GitHub HTTP 503") } }
        assertTrue(error.message.orEmpty().contains("список выпусков GitHub"))
        assertTrue(error.message.orEmpty().contains("503"))
    }

    @Test fun manifestTransferFailureIsReported() {
        val error = assertThrows(IOException::class.java) {
            check { if (it.endsWith("update.json")) throw IOException("HTTP 503") else releaseWithManifest() }
        }
        assertTrue(error.message.orEmpty().contains("update.json"))
    }

    @Test fun brokenManifestIsReported() {
        assertThrows(IllegalArgumentException::class.java) {
            check { if (it.endsWith("update.json")) "{" else releaseWithManifest() }
        }
    }

    @Test fun manifestReadPropagatesCancellation() {
        val cancelled = CancellationException("stopped")
        val actual = assertThrows(CancellationException::class.java) {
            check { if (it.endsWith("update.json")) throw cancelled else releaseWithManifest() }
        }
        assertTrue(generateSequence<Throwable>(actual) { it.cause }.any { it === cancelled })
    }

    @Test fun checksumMismatchStopsVerification() {
        val file = folder.newFile()
        file.writeText("damaged")
        assertThrows(IOException::class.java) { AppUpdateRepository.verifyChecksum(file, hash) }
    }
}
