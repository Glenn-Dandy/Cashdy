package de.kewl.cashdy.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Texterkennung mit ML Kit, das Modell steckt in der App, es geht nichts ins Netz. */
object ReceiptScanner {
    suspend fun scan(ctx: Context, uri: Uri): Result<ReceiptInfo> = runCatching {
        val image = InputImage.fromFilePath(ctx, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(Result.success(it.text)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }.getOrThrow()
        recognizer.close()
        ReceiptParser.parse(text)
    }
}
