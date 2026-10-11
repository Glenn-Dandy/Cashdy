package de.kewl.cashdy.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.kewl.cashdy.data.Store
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/** Texterkennung mit ML Kit, das Modell steckt in der App, es geht nichts ins Netz. */
object ReceiptScanner {
    suspend fun scan(ctx: Context, uri: Uri): Result<ReceiptInfo> = runCatching {
        val image = InputImage.fromFilePath(ctx, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = suspendCancellableCoroutine<Result<Text>> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(Result.success(it)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }.getOrThrow()
        recognizer.close()
        val lines = text.textBlocks.flatMap { b ->
            b.lines.mapNotNull { l ->
                val box = l.boundingBox ?: return@mapNotNull null
                OcrLine(l.text, box.left, box.top, box.right, box.bottom, l.angle)
            }
        }
        // Im Entwicklermodus landet die Rohausgabe im Log, um neue Bons als Testfall aufzunehmen.
        if (Store.current.settings.devMode) {
            // Zeile für Zeile, eine Logzeile ist auf rund 4000 Zeichen begrenzt.
            lines.forEach { Log.i("CashdyOcr", Json.encodeToString(OcrLine.serializer(), it)) }
        }
        ReceiptParser.parse(ReceiptLayout.rows(lines).joinToString("\n"))
    }
}
