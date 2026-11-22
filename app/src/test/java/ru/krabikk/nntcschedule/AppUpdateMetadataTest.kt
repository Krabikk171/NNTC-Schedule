package ru.krabikk.nntcschedule

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
