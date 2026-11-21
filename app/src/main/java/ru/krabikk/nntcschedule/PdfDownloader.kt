package ru.krabikk.nntcschedule

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Результат загрузки; не подменяем страницу Nextcloud или HTML настоящим PDF. */
data class PdfDownloadResult(val bytes: ByteArray? = null, val error: String? = null)

object PdfDownloader {
    private const val TAG = "PDF_DOWNLOADER"

    // Публичная ссылка на документ второго корпуса. Файл на стороне колледжа
    // может обновляться без изменения URL.
    private const val PDF_URL =
        "https://cloud.nntc.nnov.ru/index.php/s/bATtPnHfoyFyzB5/download/%D0%A0%D0%B0%D1%81%D0%BF%D0%B8%D1%81%D0%B0%D0%BD%D0%B8%D0%B5%20%D0%B7%D0%B0%D0%BD%D1%8F%D1%82%D0%B8%D0%B9%20%D1%83%D1%87%D0%B5%D0%B1%D0%BD%D1%8B%D0%B9%20%D0%BA%D0%BE%D1%80%D0%BF%D1%83%D1%81%20%E2%84%962.pdf"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun downloadPdf(): PdfDownloadResult {
        val request = Request.Builder()
            .url(PDF_URL)
            .header("Accept", "application/pdf")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return PdfDownloadResult(error = "Сервер колледжа вернул HTTP ${response.code}. Повторите проверку позже.")
                }
                val body = response.body
                    ?: return PdfDownloadResult(error = "Сервер расписания вернул пустой ответ.")
                val bytes = body.bytes()
                if (bytes.size < 8 || !bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(37, 80, 68, 70))) {
                    Log.e(TAG, "По ссылке пришёл не PDF: HTTP ${response.code}, type=${body.contentType()}, bytes=${bytes.size}")
                    return PdfDownloadResult(error = "По ссылке расписания пришёл не PDF (возможно, страница облака или ошибка доступа).")
                }
                Log.d(TAG, "PDF загружен: ${bytes.size} байт")
                PdfDownloadResult(bytes = bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось получить PDF", e)
            PdfDownloadResult(error = "Ошибка загрузки расписания: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }
}
