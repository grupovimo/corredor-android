package ar.com.corredor.app.nucleo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Las fotos de la cámara o la galería, achicadas y en JPEG antes de subirlas (UIGraphicsImageRenderer + jpegData en
 *  iOS). La misma pieza que Tu Local Web. */
object Fotos {
    /** Un archivo vacío para que la cámara deje la foto (UIImagePickerController .camera). */
    fun archivoCamara(ctx: Context): Pair<File, Uri> {
        val f = archivoParaCompartir(ctx, "camara-${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(ctx, PROVEEDOR_ARCHIVOS, f)
    }

    /** La imagen, con el lado mayor en `lado` como mucho, derecha y en JPEG con `calidad`. */
    fun achicar(ctx: Context, uri: Uri, lado: Int = 1600, calidad: Int = 85): ByteArray? = runCatching {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                val m = maxOf(info.size.width, info.size.height)
                if (m > lado) d.setTargetSize(info.size.width * lado / m, info.size.height * lado / m)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val crudo = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val giro = ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val m = maxOf(crudo.width, crudo.height)
            val e = minOf(1f, lado.toFloat() / m)
            val matriz = Matrix().apply { postScale(e, e); postRotate(giro.toFloat()) }
            Bitmap.createBitmap(crudo, 0, 0, crudo.width, crudo.height, matriz, true)
        }
        ByteArrayOutputStream().use { s -> bmp.compress(Bitmap.CompressFormat.JPEG, calidad, s); s.toByteArray() }
    }.getOrNull()
}
