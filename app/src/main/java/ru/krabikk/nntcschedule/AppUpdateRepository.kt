package ru.krabikk.nntcschedule

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * GitHub Releases are the only update origin. Each eligible release must contain:
 * - the signed APK asset and GitHub's sha256 asset digest;
 * - update.json with packageName, versionName, versionCode and apkAssetName.
 *
 * If any required field is absent or inconsistent, the release is ignored.
 */
object AppUpdateRepository {
    private const val RELEASES_API =
        "https://api.github.com/repos/Krabikk171/NNTC-Schedule/releases?per_page=40"
    private const val STABLE_API =
        "https://api.github.com/repos/Krabikk171/NNTC-Schedule/releases/latest"
    private const val RELEASE_URL_PREFIX =
        "https://github.com/Krabikk171/NNTC-Schedule/releases/download/"
    private const val MIME_APK = "application/vnd.android.package-archive"
    private const val MAX_METADATA_BYTES = 3 * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 32 * 1024

    @Suppress("DEPRECATION")
    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
    }

    suspend fun check(context: Context, channel: AppUpdateChannel): AppUpdateOffer? =
        withContext(Dispatchers.IO) {
            val releases = if (channel == AppUpdateChannel.STABLE) {
                JSONArray().put(JSONObject(fetchText(STABLE_API, MAX_METADATA_BYTES)))
            } else {
                JSONArray(fetchText(RELEASES_API, MAX_METADATA_BYTES))
            }
            val offers = mutableListOf<AppUpdateOffer>()
            // Stable takes one release; Beta examines only the latest 20.
            for (index in 0 until minOf(releases.length(), 20)) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", true)) continue
                val prerelease = release.optBoolean("prerelease", false)
                if (channel == AppUpdateChannel.STABLE && prerelease) continue
                val tag = release.optString("tag_name")
                if (!AppUpdatePolicy.validTag(tag)) continue
                val assets = release.optJSONArray("assets") ?: continue
                val manifestAsset = findAsset(assets, "update.json") ?: continue
                val manifestUrl = manifestAsset.optString("browser_download_url")
                if (!isOfficialAssetUrl(manifestUrl, tag, "update.json")) continue
                try {
                    val manifest = JSONObject(fetchText(manifestUrl, MAX_MANIFEST_BYTES))
                    parseOffer(release, manifest)?.let(offers::add)
                } catch (_: Exception) {
                    // A malformed historical release must not block newer valid releases.
                }
            }
            AppUpdatePolicy.chooseUpdate(offers, installedVersionCode(context), channel)
        }

    internal fun parseOffer(release: JSONObject, manifest: JSONObject): AppUpdateOffer? {
        val tag = release.optString("tag_name")
        if (!AppUpdatePolicy.validTag(tag) || release.optBoolean("draft", true)) return null
        val versionName = manifest.optString("versionName")
        val versionCode = manifest.optLong("versionCode", 0L)
        val apkName = manifest.optString("apkAssetName")
        val packageName = manifest.optString("packageName")
        if (versionName != tag.removePrefix("v") || versionCode <= 0L ||
            packageName != AppUpdatePolicy.APPLICATION_ID ||
            !apkName.matches(Regex("^NNTC-Schedule-v[a-zA-Z0-9.-]+\\.apk$"))
        ) return null
        val assets = release.optJSONArray("assets") ?: return null
        val apk = findAsset(assets, apkName) ?: return null
        val apkUrl = apk.optString("browser_download_url")
        val digest = apk.optString("digest").removePrefix("sha256:")
        val size = apk.optLong("size", 0L)
        if (!isOfficialAssetUrl(apkUrl, tag, apkName) ||
            !apk.optString("digest").startsWith("sha256:") ||
            !AppUpdatePolicy.validDigest(digest) ||
            size !in 1..AppUpdatePolicy.MAX_APK_BYTES ||
            apk.optString("state") != "uploaded"
        ) return null
        val page = release.optString("html_url")
        if (page != "https://github.com/Krabikk171/NNTC-Schedule/releases/tag/$tag") return null
        return AppUpdateOffer(
            tag = tag,
            versionName = versionName,
            versionCode = versionCode,
            description = release.optString("body").take(4_000),
            isPrerelease = release.optBoolean("prerelease", false),
            releasePageUrl = page,
            apkDownloadUrl = apkUrl,
            apkSha256 = digest.lowercase(Locale.ROOT),
            apkSizeBytes = size
        )
    }

    private fun findAsset(assets: JSONArray, name: String): JSONObject? =
        (0 until assets.length()).asSequence().mapNotNull(assets::optJSONObject)
            .firstOrNull { it.optString("name") == name && it.optString("state") == "uploaded" }

    private fun isOfficialAssetUrl(url: String, tag: String, name: String): Boolean =
        url == "$RELEASE_URL_PREFIX$tag/$name"

    private fun fetchText(url: String, maxBytes: Int): String {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) throw IOException("GitHub HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > maxBytes) throw IOException("Metadata too large")
            val stream = connection.inputStream
            return stream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) throw IOException("Metadata too large")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val parsed = URL(url)
        if (parsed.protocol != "https") throw IOException("HTTPS required")
        return (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NNTCSchedule-Android-Updater/3.1.1")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
    }

    suspend fun downloadAndVerify(
        context: Context,
        offer: AppUpdateOffer,
        onProgress: suspend (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (!isOfficialAssetUrl(offer.apkDownloadUrl, offer.tag,
                "NNTC-Schedule-${offer.tag}.apk") &&
            !isOfficialAssetUrl(offer.apkDownloadUrl, offer.tag,
                "NNTC-Schedule-${offer.versionName}.apk")
        ) throw IOException("Unofficial APK URL")
        if (!AppUpdatePolicy.validDigest(offer.apkSha256) ||
            offer.apkSizeBytes !in 1..AppUpdatePolicy.MAX_APK_BYTES
        ) throw IOException("Invalid APK metadata")

        val updatesDir = File(context.cacheDir, "app_updates").apply { mkdirs() }
        // PackageManager expects an APK archive; retain .apk suffix even for the temporary file.
        val partial = File(updatesDir, "partial-${offer.versionCode}.apk")
        val finalFile = File(updatesDir, "update-${offer.versionCode}.apk")
        val connection = open(offer.apkDownloadUrl)
        try {
            if (connection.responseCode != 200) throw IOException("APK HTTP ${connection.responseCode}")
            // GitHub redirects release files to its asset CDN. Reject HTTP or unknown hosts.
            if (connection.url.protocol != "https" || connection.url.host !in setOf(
                    "github.com", "release-assets.githubusercontent.com",
                    "objects.githubusercontent.com", "github-releases.githubusercontent.com"
                )) throw IOException("Untrusted APK download redirect")
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArray(64 * 1024)
            var total = 0L
            var lastPercent = -1
            connection.inputStream.use { source ->
                FileOutputStream(partial).use { target ->
                    while (true) {
                        val count = source.read(bytes)
                        if (count < 0) break
                        total += count
                        if (total > AppUpdatePolicy.MAX_APK_BYTES || total > offer.apkSizeBytes) {
                            throw IOException("APK exceeds expected size")
                        }
                        target.write(bytes, 0, count)
                        digest.update(bytes, 0, count)
                        val percent = (100L * total / offer.apkSizeBytes).toInt()
                        if (percent != lastPercent && percent % 2 == 0) {
                            lastPercent = percent
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                    target.fd.sync()
                }
            }
            if (total != offer.apkSizeBytes ||
                digest.digest().hex() != offer.apkSha256
            ) throw IOException("APK SHA-256 / size mismatch")
            verifyApk(context, partial, offer.versionCode)
            if (finalFile.exists() && !finalFile.delete()) throw IOException("Cannot replace old APK")
            if (!partial.renameTo(finalFile)) throw IOException("Cannot save verified APK")
            withContext(Dispatchers.Main) { onProgress(100) }
            finalFile
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /** Returns false if the user must first allow installation from this source. */
    fun requestSystemInstall(
        context: Context, file: File, expectedCode: Long, expectedSha256: String
    ): Boolean {
        if (!file.isFile || file.parentFile?.canonicalFile !=
            File(context.cacheDir, "app_updates").canonicalFile
        ) throw IOException("Invalid APK path")
        if (!AppUpdatePolicy.validDigest(expectedSha256) || sha256File(file) != expectedSha256) {
            throw IOException("Saved APK SHA-256 mismatch")
        }
        verifyApk(context, file, expectedCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updater", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = android.content.ClipData.newRawUri("APK update", uri)
            }
        )
        return true
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, file: File, expectedCode: Long) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("Android cannot read APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            archive.longVersionCode else archive.versionCode.toLong()
        if (archive.packageName != context.packageName || archiveVersion != expectedCode ||
            archiveVersion <= installedVersionCode(context)
        ) throw IOException("Wrong package or versionCode")
        fun certificates(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info.signingInfo?.apkContentsSigners?.toList().orEmpty()
            else info.signatures?.toList().orEmpty()
            return signatures.map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).hex()
            }.toSet()
        }
        val currentSigners = certificates(installed)
        val downloadedSigners = certificates(archive)
        if (currentSigners.isEmpty() || downloadedSigners.isEmpty() ||
            currentSigners != downloadedSigners
        ) throw IOException("APK is signed with a different key")
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex(): String = joinToString("") {
        "%02x".format(Locale.ROOT, it.toInt() and 0xff)
    }
}
