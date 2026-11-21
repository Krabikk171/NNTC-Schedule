package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    private val checksum = "a".repeat(64)

    private fun offer(code: Long, prerelease: Boolean = false, sha: String = checksum) =
        AppUpdateOffer(
            tag = "v3.1.$code", versionName = "3.1.$code", versionCode = code,
            description = "", isPrerelease = prerelease,
            releasePageUrl = "https://github.com/Krabikk171/NNTC-Schedule/releases/tag/v3.1.$code",
            apkDownloadUrl = "https://github.com/Krabikk171/NNTC-Schedule/releases/download/v3.1.$code/NNTC-Schedule-v3.1.$code.apk",
            apkSha256 = sha, apkSizeBytes = 100
        )

    @Test fun stableNeverSelectsPrerelease() {
        val result = AppUpdatePolicy.chooseUpdate(listOf(offer(37, true), offer(36)), 35, AppUpdateChannel.STABLE)
        assertEquals(36, result?.versionCode)
    }

    @Test fun betaSelectsHighestAndDoesNotDowngrade() {
        assertEquals(37, AppUpdatePolicy.chooseUpdate(
            listOf(offer(36), offer(37, true)), 35, AppUpdateChannel.BETA)?.versionCode)
        assertNull(AppUpdatePolicy.chooseUpdate(listOf(offer(37)), 37, AppUpdateChannel.BETA))
    }

    @Test fun rejectsMalformedChecksumsAndOversizedApks() {
        assertNull(AppUpdatePolicy.chooseUpdate(listOf(offer(36, sha = "bad")), 35, AppUpdateChannel.STABLE))
        assertTrue(AppUpdatePolicy.validDigest(checksum))
        assertFalse(AppUpdatePolicy.validDigest("xyz"))
        assertFalse(AppUpdatePolicy.validTag("v../malicious"))
    }

    @Test fun channelDefaultIsStable() {
        assertEquals(AppUpdateChannel.STABLE, AppUpdateChannel.fromValue("unknown"))
        assertEquals(AppUpdateChannel.BETA, AppUpdateChannel.fromValue("beta"))
    }
}
