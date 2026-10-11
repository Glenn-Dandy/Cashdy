package de.kewl.cashdy.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sicherung als JSON-Datei, wahlweise per Dateiauswahl (Downloads, USB, Cloud-App) oder
 * per WebDAV auf eine eigene Nextcloud. Kassenbon-Fotos sind nicht enthalten.
 */
object Backup {
    const val FILE_NAME = "cashdy-backup.json"

    /** Zugangsdaten gehören nicht in die Sicherungsdatei. */
    private fun forExport(d: AppData) = d.copy(settings = d.settings.copy(webdavPass = ""), recorded = emptyList())

    suspend fun exportTo(ctx: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Store.encode(forExport(Store.current)).toByteArray()) }
        }
    }

    suspend fun readFrom(ctx: Context, uri: Uri): Result<AppData> = withContext(Dispatchers.IO) {
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            Store.decode(text)
        }
    }

    /** Beim Wiederherstellen die eigenen WebDAV-Zugangsdaten behalten. */
    fun restore(incoming: AppData) {
        val mine = Store.current.settings
        Store.replaceAll(incoming.copy(settings = incoming.settings.copy(
            webdavUrl = mine.webdavUrl.ifBlank { incoming.settings.webdavUrl },
            webdavUser = mine.webdavUser.ifBlank { incoming.settings.webdavUser },
            webdavPass = mine.webdavPass,
            onboarded = true,
        )))
    }

    private fun davUrl(base: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith(".json")) b else "$b/$FILE_NAME"
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val s = Store.current.settings
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        if (s.webdavUser.isNotBlank()) {
            val token = Base64.encodeToString("${s.webdavUser}:${s.webdavPass}".toByteArray(), Base64.NO_WRAP)
            c.setRequestProperty("Authorization", "Basic $token")
        }
        return c
    }

    suspend fun uploadWebDav(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = Store.current.settings
            require(s.webdavUrl.isNotBlank()) { "Keine WebDAV-Adresse eingetragen" }
            val c = open(davUrl(s.webdavUrl), "PUT")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(Store.encode(forExport(Store.current)).toByteArray()) }
            val code = c.responseCode
            c.disconnect()
            check(code in 200..299) { httpError(code) }
            Store.update { it.copy(settings = it.settings.copy(lastBackupAt = System.currentTimeMillis())) }
        }
    }

    suspend fun downloadWebDav(): Result<AppData> = withContext(Dispatchers.IO) {
        runCatching {
            val s = Store.current.settings
            require(s.webdavUrl.isNotBlank()) { "Keine WebDAV-Adresse eingetragen" }
            val c = open(davUrl(s.webdavUrl), "GET")
            val code = c.responseCode
            check(code in 200..299) { httpError(code) }
            val text = c.inputStream.use { it.readBytes().decodeToString() }
            c.disconnect()
            Store.decode(text)
        }
    }

    private fun httpError(code: Int) = when (code) {
        401 -> "Benutzername oder App-Passwort falsch (401)"
        403 -> "Kein Zugriff auf den Ordner (403)"
        404 -> "Ordner nicht gefunden (404). Gibt es ihn in der Nextcloud?"
        409 -> "Ordner existiert nicht (409). Bitte in der Nextcloud anlegen."
        else -> "Server antwortet mit Fehler $code"
    }
}
