package de.kewl.cashdy.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import de.kewl.cashdy.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Liest einen Kassenbon: Texterkennung mit Tesseract, dann Auswertung. */
object ReceiptScanner {
    suspend fun scan(ctx: Context, uri: Uri): Result<ReceiptInfo> = runCatching {
        // Tesseract rechnet ein paar Sekunden am Stück, das darf den Bildschirm nicht blockieren.
        val lines = withContext(Dispatchers.Default) { TesseractScanner.recognize(ctx, uri) }
        // Im Entwicklermodus landet die Rohausgabe im Log, um neue Bons als Testfall aufzunehmen.
        if (Store.current.settings.devMode) {
            // Zeile für Zeile, eine Logzeile ist auf rund 4000 Zeichen begrenzt.
            lines.forEach { Log.i("CashdyOcr", Json.encodeToString(OcrLine.serializer(), it)) }
        }
        ReceiptParser.parse(ReceiptLayout.rows(lines).joinToString("\n"))
    }
}
