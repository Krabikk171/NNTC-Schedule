package ru.krabikk.nntcschedule

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONException
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
 * v3.1.0 predates update.json. Later eligible releases require valid metadata.
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

    private val metadataClient by lazy { OkHttpClient.Builder().followSslRedirects(false).build() }

    @Suppress("DEPRECATION")
    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
    }

    suspend fun check(context: Context, channel: AppUpdateChannel): AppUpdateOffer? =
        withContext(Dispatchers.IO) {
            val networks = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = networks.getNetworkCapabilities(networks.activeNetwork)
            checkReleases(channel, installedVersionCode(context),
                online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                read = ::fetchText)
        }

    internal suspend fun checkReleases(
        channel: AppUpdateChannel,
        installedCode: Long,
        online: Boolean = true,
        timeoutMillis: Long = 18_000,
        read: suspend (String, Int) -> String
    ): AppUpdateOffer? {
        currentCoroutineContext().ensureActive()
        if (!online) throw IOException("Нет подтверждённого подключения к интернету. Проверьте сеть и повторите попытку.")
        val offers = withTimeoutOrNull(timeoutMillis) { readOffers(channel, read) }
            ?: throw IOException("GitHub не ответил вовремя. Повторите проверку.")
        return AppUpdatePolicy.chooseUpdate(offers, installedCode, channel)
    }

    private suspend fun readOffers(
        channel: AppUpdateChannel,
        read: suspend (String, Int) -> String
    ): List<AppUpdateOffer> {
        val stable = channel == AppUpdateChannel.STABLE
        suspend fun readMetadata(url: String, manifest: Boolean): String {
            try {
                return read(url, if (manifest) MAX_MANIFEST_BYTES else MAX_METADATA_BYTES)
            } catch (e: java.net.UnknownHostException) {
                throw IOException("Не удалось подключиться к GitHub. Проверьте подключение к интернету.", e)
            } catch (e: java.net.ConnectException) {
                throw IOException("Нет соединения с GitHub. Проверьте сеть и повторите попытку.", e)
            } catch (e: IOException) {
                val source = if (manifest) "загрузить update.json" else "получить список выпусков GitHub"
                throw IOException("Не удалось $source: ${e.message}", e)
            }
        }

        try {
            val json = readMetadata(if (stable) STABLE_API else RELEASES_API, false)
            val releases = if (stable) JSONArray().put(JSONObject(json)) else JSONArray(json)
            val offers = mutableListOf<AppUpdateOffer>()
            for (index in 0 until minOf(releases.length(), 20)) {
                currentCoroutineContext().ensureActive()
                val release = releases.getJSONObject(index)
                if (release.getBoolean("draft") || (stable && release.getBoolean("prerelease"))) continue
                val tag = release.getString("tag_name")
                if (!AppUpdatePolicy.validTag(tag)) throw JSONException("Некорректный тег выпуска")
                val assets = release.getJSONArray("assets")
                val manifest = findAsset(assets, "update.json")
                if (manifest == null) {
                    if (predatesUpdateManifest(tag) && (0 until assets.length()).none {
                            assets.getJSONObject(it).optString("name") == "update.json"
                        }) continue
                    throw JSONException("Для $tag недоступен update.json")
                }
                val url = manifest.getString("browser_download_url")
                if (!isOfficialAssetUrl(url, tag, "update.json")) throw JSONException("Некорректный адрес update.json")
                offers += parseOffer(release, JSONObject(readMetadata(url, true)))
                    ?: throw JSONException("Неверные сведения об APK выпуска $tag")
            }
            return offers
        } catch (e: JSONException) {
            throw IllegalArgumentException("Повреждены метаданные выпуска GitHub: ${e.message}", e)
        }
    }

    // The update manifest was introduced after the 3.1.0 release.
    private fun predatesUpdateManifest(tag: String): Boolean {
        val parts = tag.removePrefix("v").split('.', '-', limit = 4)
        val major = parts[0].toIntOrNull() ?: return false
        val minor = parts[1].toIntOrNull() ?: return false
        val patch = parts[2].toIntOrNull() ?: return false
        return major < 3 || (major == 3 && (minor < 1 || (minor == 1 && patch == 0)))
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

    private suspend fun fetchText(url: String, maxBytes: Int): String =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NNTCSchedule-Android-Updater/3.1.1")
                .build()
            val call = metadataClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (it.code != 200) throw IOException("GitHub HTTP ${it.code}")
                            val source = it.body.source()
                            if (it.body.contentLength() > maxBytes || source.request(maxBytes + 1L)) {
                                throw JSONException("Слишком большой ответ")
                            }
                            source.readUtf8()
                        }
                        continuation.resume(text)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
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
            ) throw IOException("Размер или контрольная сумма загруженного APK не совпадает.")
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
    suspend fun requestSystemInstall(
        context: Context, file: File, expectedCode: Long, expectedSha256: String
    ): Boolean {
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.parentFile?.canonicalFile !=
                File(context.cacheDir, "app_updates").canonicalFile
            ) throw IOException("Файл обновления недоступен. Загрузите APK повторно.")
            verifyChecksum(file, expectedSha256)
            verifyApk(context, file, expectedCode)
        }
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
            ?: throw IOException("Android не удалось прочитать пакет обновления.")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            archive.longVersionCode else archive.versionCode.toLong()
        if (archive.packageName != context.packageName || archiveVersion != expectedCode ||
            archiveVersion <= installedVersionCode(context)
        ) throw IOException("Пакет или код версии APK не соответствует обновлению.")
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
        ) throw IOException("Подпись APK отличается от подписи установленного приложения.")
    }

    internal fun verifyChecksum(file: File, expected: String) {
        if (!AppUpdatePolicy.validDigest(expected) || sha256File(file) != expected) {
            throw IOException("Контрольная сумма APK не совпадает. Загрузите обновление заново.")
        }
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
