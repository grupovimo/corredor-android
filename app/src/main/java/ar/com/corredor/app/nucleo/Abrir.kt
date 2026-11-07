package ar.com.corredor.app.nucleo

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Lo del sistema que usan muchas pantallas (openURL, UIPasteboard, mailto:, ShareLink de un archivo, QuickLook e
// impresión de iOS), con el contexto a mano. `Estado.abrirUrl`/`copiar`/`compartirArchivo` pasan por acá.

/** El FileProvider de la app (res/xml/archivos.xml: cache/compartir). */
const val PROVEEDOR_ARCHIVOS = "ar.com.corredor.app.archivos"

/** openURL de un link cualquiera (https, wa.me, mailto:). `tel:` abre el marcador (llamar directo pide permiso). */
fun abrirUrl(ctx: Context, url: String) {
    val u = Uri.parse(url)
    val i = if (u.scheme == "tel") Intent(Intent.ACTION_DIAL, u) else Intent(Intent.ACTION_VIEW, u)
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Link(destination: mailto:) de iOS. */
fun abrirMail(ctx: Context, mail: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** UIPasteboard.general.string = texto. */
fun copiar(ctx: Context, texto: String) {
    runCatching { ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Corredor", texto)) }
}

/** ShareLink de un texto (o un link): la hoja de compartir del sistema. */
fun compartirTexto(ctx: Context, texto: String, asunto: String? = null) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    asunto?.let { i.putExtra(Intent.EXTRA_SUBJECT, it) }
    runCatching { ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** El archivo dentro de cache/compartir (lo único que expone el FileProvider): si está en otro lado, lo copia. */
fun archivoCompartible(ctx: Context, archivo: File): File {
    val carpeta = File(ctx.cacheDir, "compartir").apply { mkdirs() }
    if (archivo.parentFile?.canonicalPath == carpeta.canonicalPath) return archivo
    return File(carpeta, archivo.name).also { archivo.copyTo(it, overwrite = true) }
}

/** Un archivo nuevo en cache/compartir con ese nombre (sin barras). */
fun archivoTemporal(ctx: Context, nombre: String): File =
    File(File(ctx.cacheDir, "compartir").apply { mkdirs() }, nombre.replace("/", "-"))

/** ShareLink de un archivo (un PDF, una placa, un CSV). */
fun compartirArchivo(ctx: Context, archivo: File, tipo: String, texto: String? = null) {
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, PROVEEDOR_ARCHIVOS, archivoCompartible(ctx, archivo))
        val i = Intent(Intent.ACTION_SEND).setType(tipo).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        texto?.let { i.putExtra(Intent.EXTRA_TEXT, it) }
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** La vista previa del sistema (QuickLook / `.vistaPrevia` de Nucleo/Impresos.swift): abre el archivo (PDF, Excel,
 *  foto) con la app que lo muestra, que ya trae compartir e imprimir. Si no hay ninguna, lo comparte. */
fun verArchivo(ctx: Context, archivo: File, tipo: String = tipoDe(archivo)) {
    val f = runCatching { archivoCompartible(ctx, archivo) }.getOrNull() ?: return
    val abrio = runCatching {
        val uri = FileProvider.getUriForFile(ctx, PROVEEDOR_ARCHIVOS, f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, tipo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(Intent.createChooser(i, f.nameWithoutExtension).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!abrio) compartirArchivo(ctx, f, tipo)
}

/** El tipo MIME por la extensión (pdf, xlsx, jpg…). */
fun tipoDe(f: File): String =
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "csv" -> "text/csv"
        else -> "application/octet-stream"
    }

/** Imprimir un PDF (UIPrintInteractionController en iOS) con el PrintManager del sistema. */
fun imprimirPdf(ctx: Context, archivo: File) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    pm.print(archivo.nameWithoutExtension, object : PrintDocumentAdapter() {
        override fun onLayout(viejos: PrintAttributes?, nuevos: PrintAttributes?, cancelar: CancellationSignal?, r: LayoutResultCallback, extras: Bundle?) {
            if (cancelar?.isCanceled == true) { r.onLayoutCancelled(); return }
            r.onLayoutFinished(PrintDocumentInfo.Builder(archivo.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }

        override fun onWrite(paginas: Array<out PageRange>?, destino: ParcelFileDescriptor?, cancelar: CancellationSignal?, r: WriteResultCallback) {
            try {
                archivo.inputStream().use { i -> FileOutputStream(destino?.fileDescriptor).use { o -> i.copyTo(o) } }
                r.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                r.onWriteFailed(e.message)
            }
        }
    }, null)
}

/** La Activity detrás de un contexto de Compose (para los lanzadores de Google Play: Billing, el escáner). */
fun Context.actividad(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** `String.capitalized` de Swift: cada palabra con mayúscula («venta temporal» → «Venta Temporal»). */
fun String.capitalizado(): String =
    split(" ").joinToString(" ") { w -> w.lowercase(Formato.argentina).replaceFirstChar { it.titlecase(Formato.argentina) } }

/** Los bytes de un archivo de la API (un PDF, un Excel, una foto del CSV): `api.pedir` con `Accept: *\/*` y la sesión.
 *  Los errores, como los de `pedir`. */
suspend fun Api.bajar(ruta: String, query: Consulta = emptyList()): ByteArray {
    val r = Request.Builder().url(url(ruta, query)).header("Accept", "*/*")
    token?.let { r.header("Authorization", "Bearer $it") }
    return try {
        withContext(Dispatchers.IO) {
            http.newCall(r.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ErrorApi(resp.code, "archivo", "No se pudo abrir", "No pudimos traer el archivo.")
                resp.body?.bytes() ?: ByteArray(0)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw ErrorApi.sinRed
    }
}
