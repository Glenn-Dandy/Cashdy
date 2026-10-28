package de.kewl.cashdy.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlin.math.max

/**
 * Texterkennung mit Tesseract, freie Software und komplett auf dem Handy, es geht nichts ins
 * Netz. Das deutsche Modell ist das genaue aus tessdata_best (Apache 2.0): im Vergleich mit dem
 * schnellen las es Kassenbons deutlich sicherer.
 */
object TesseractScanner {

    /** Längste Seite des Fotos in Pixeln, größer bringt kaum mehr und kostet viel Zeit. */
    private const val MAX_SIDE = 3000

    fun recognize(ctx: Context, uri: Uri): List<OcrLine> {
        val bitmap = loadBitmap(ctx, uri)
        val api = TessBaseAPI()
        try {
            check(api.init(dataPath(ctx), "deu", TessBaseAPI.OEM_LSTM_ONLY)) { "Tesseract startet nicht" }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setVariable("user_defined_dpi", "300")
            api.setImage(bitmap)
            api.getUTF8Text()
            val lines = mutableListOf<OcrLine>()
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            val it = api.resultIterator ?: return lines
            it.begin()
            do {
                val text = it.getUTF8Text(level)?.trim().orEmpty()
                val r = it.getBoundingRect(level)
                if (text.isNotEmpty()) lines += OcrLine(text, r.left, r.top, r.right, r.bottom)
            } while (it.next(level))
            it.delete()
            return lines
        } finally {
            api.recycle()
            bitmap.recycle()
        }
    }

    /**
     * Tesseract will das Modell als Datei, deshalb aus den Assets kopieren. Passt die Größe
     * nicht (älteres Modell nach einem Update), wird neu kopiert.
     */
    private fun dataPath(ctx: Context): String {
        val base = File(ctx.filesDir, "ocr")
        val file = File(base, "tessdata/deu.traineddata")
        val size = ctx.assets.openFd("tessdata/deu.traineddata").use { it.length }
        if (file.length() != size) {
            file.parentFile!!.mkdirs()
            val tmp = File(file.path + ".tmp")
            ctx.assets.open("tessdata/deu.traineddata").use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.renameTo(file)
        }
        return base.absolutePath
    }

    /** Foto laden, verkleinern und so drehen, wie die Kamera es gemeint hat. */
    private fun loadBitmap(ctx: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Foto lässt sich nicht öffnen")
        val longSide = max(bmp.width, bmp.height)
        if (longSide > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / longSide
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        }
        val orientation = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        return bmp
    }
}
