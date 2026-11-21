package ru.krabikk.nntcschedule

/** Channels refer to APK updates, never to PDF timetable updates. */
enum class AppUpdateChannel(val value: String) {
    STABLE("stable"), BETA("beta");

    companion object {
        fun fromValue(value: String?): AppUpdateChannel =
            entries.firstOrNull { it.value == value } ?: STABLE
    }
}

data class AppUpdateOffer(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val description: String,
    val isPrerelease: Boolean,
    val releasePageUrl: String,
    val apkDownloadUrl: String,
    val apkSha256: String,
    val apkSizeBytes: Long
)

object AppUpdatePolicy {
    const val MAX_APK_BYTES: Long = 200L * 1024L * 1024L
    const val APPLICATION_ID = "ru.krabikk.nntcschedule"
    private val SHA_256 = Regex("^[a-fA-F0-9]{64}$")
    private val TAG = Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][a-zA-Z0-9.-]+)?$")

    fun validDigest(value: String): Boolean = SHA_256.matches(value)
    fun validTag(value: String): Boolean = TAG.matches(value) && !value.contains("..")

    fun chooseUpdate(
        offers: List<AppUpdateOffer>,
        installedVersionCode: Long,
        channel: AppUpdateChannel
    ): AppUpdateOffer? = offers.asSequence()
        .filter { it.versionCode > installedVersionCode }
        .filter { channel == AppUpdateChannel.BETA || !it.isPrerelease }
        .filter { validDigest(it.apkSha256) && it.apkSizeBytes in 1..MAX_APK_BYTES }
        .maxByOrNull { it.versionCode }
}
