package ar.com.corredor.app.pantallas.documentos

// Clon de Pantallas/Documentos/Documentos.swift.

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.IOException
import java.time.Instant

// MARK: - Documentos

@Serializable
data class DocumentoAPI(
    val id: String,
    val archivoId: String? = null,
    val nombre: String,
    val tipo: String? = null,
    val bytes: Int? = null,
    val subidoPor: String? = null,
    val creadoEn: FechaApi? = null,
    val url: String? = null,
)

/** Los documentos de un contrato o de una propiedad: verlos, subir un PDF o una foto, y borrarlos.
 *  `de`: «contrato» o «propiedad». */
@Composable
fun DocumentosLista(de: String, id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var lista by remember { mutableStateOf<List<DocumentoAPI>>(emptyList()) }
    var cargado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var subiendo by remember { mutableStateOf(false) }

    @Serializable data class CuerpoDocumento(val archivoId: String, val nombre: String, val contratoId: String? = null, val propiedadId: String? = null)

    suspend fun cargar() {
        try {
            lista = estado.api.get("/v1/documentos", query = listOf(de to id))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
        cargado = true
    }

    /** Sacar va a la papelera al toque, con deshacer (se vuelve a sumar el mismo archivo). */
    suspend fun sacar(d: DocumentoAPI) {
        try {
            estado.api.hacer("DELETE", "/v1/documentos/${d.id}")
            lista = lista.filter { it.id != d.id }
            estado.avisar("Sacaste «${d.nombre}»") {
                estado.tarea {
                    val a = d.archivoId ?: return@tarea
                    try {
                        estado.api.hacer("POST", "/v1/documentos", CuerpoDocumento(a, d.nombre, if (de == "contrato") id else null, if (de == "propiedad") id else null))
                        cargar()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun subir(datos: ByteArray, nombre: String, tipo: String) {
        if (datos.size > (25 shl 20)) { estado.avisar("El archivo pasa de 25 MB.", ok = false); return }
        subiendo = true
        try {
            val a = estado.api.subir(datos, nombre = nombre, tipo = tipo, uso = "documento")
            estado.api.hacer("POST", "/v1/documentos", CuerpoDocumento(a.id, nombre, if (de == "contrato") id else null, if (de == "propiedad") id else null))
            cargar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { subiendo = false }
    }

    /** Lo baja con la sesión de la app y lo muestra con la vista previa (compartir, imprimir). */
    suspend fun abrir(d: DocumentoAPI) {
        val a = d.archivoId ?: return
        try {
            val datos = estado.api.bajar("/v1/archivos/$a/contenido")
            val f = archivoParaCompartir(ctx, d.nombre)
            withContext(Dispatchers.IO) { f.writeBytes(datos) }
            verArchivo(ctx, f, if (d.tipo?.contains("pdf") == true || d.nombre.lowercase().endsWith(".pdf")) "application/pdf" else (d.tipo ?: "image/*"))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    // fileImporter de iOS: un PDF o una imagen.
    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        estado.tarea {
            try {
                val d = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: throw IOException()
                val nombre = nombreDe(ctx, u) ?: "Documento"
                subir(d, nombre, if (nombre.substringAfterLast('.', "").lowercase() == "pdf") "application/pdf" else "image/jpeg")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        }
    }
    // PhotosPicker de iOS.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        estado.tarea {
            val d = withContext(Dispatchers.IO) { Fotos.achicar(ctx, u, lado = 4096, calidad = 85) }
            if (d != null) subir(d, "Foto.jpg", "image/jpeg") else estado.avisar("Algo salió mal. Probá de nuevo.", ok = false)
        }
    }
    val escanear = EscanerDocumentos { pdf -> estado.tarea { subir(pdf, "Escaneo ${Formato.diaISO(Instant.now())}.pdf", "application/pdf") } }
    val pedirArchivo = { elegirArchivo.launch(arrayOf("application/pdf", "image/*")) }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Documentos", acciones = if (estado.escribe) ({
        MenuBoton(buildList {
            if (escanear != null) add(OpcionDeMenu("Escanear con la cámara", "doc.viewfinder") { escanear() })
            add(OpcionDeMenu("Un archivo (PDF)", "doc") { pedirArchivo() })
            add(OpcionDeMenu("Una foto", "photo") { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        }, "Sumar") { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("plus", 19f, C.primarioTexto) } }
    }) else null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (estado.escribe) VistaConsejo(ConsejoEscanear())
            val err = error
            if (err != null && lista.isEmpty()) {
                EstadoError("los documentos", err) { estado.tarea { cargar() } }
            } else if (!cargado) {
                EsqueletoFilas(filas = 3)
            } else if (lista.isEmpty()) {
                EstadoVacio(
                    icono = "folder", titulo = "Sin documentos", texto = "El contrato firmado, el DNI, la escritura, los planos: todo a mano.",
                    accion = if (estado.escribe) ("Subir un PDF" to { pedirArchivo() }) else null,
                )
            }
            if (lista.isNotEmpty()) Column(Modifier.fillMaxWidth().tarjeta()) {
                lista.forEachIndexed { i, d ->
                    if (i > 0) Separador(inset = 56.dp)
                    FilaDeslizable(derecha = if (estado.escribe) listOf(AccionFila("Sacar", "trash", C.error) { estado.tarea { sacar(d) } }) else emptyList()) {
                        Tocable({ estado.tarea { abrir(d) } }) { _ ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                    Icono(if (d.tipo?.contains("pdf") == true) "doc.richtext" else "photo", 17f, C.azulTinta)
                                }
                                Column {
                                    Texto(d.nombre, Estilo.filaLista, C.texto)
                                    Texto(
                                        listOfNotNull(d.subidoPor, d.creadoEn?.let { Formato.cuando(it) }, d.bytes?.let { android.text.format.Formatter.formatShortFileSize(ctx, it.toLong()) })
                                            .joinToString(" · "),
                                        Estilo.footnote, C.texto2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (subiendo) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { Rueda() }
        }
    }
}

/** El escáner de documentos (VisionKit en el iPhone; en Android, el de ML Kit): recorta y endereza cada hoja; al
 *  terminar, las páginas salen en un solo PDF. Devuelve cómo abrirlo, o null si el teléfono no lo tiene
 *  (`VNDocumentCameraViewController.isSupported`). */
// El escáner de Google Play services (play-services-mlkit-document-scanner, en app/build.gradle.kts) da el PDF hecho.
@Composable
fun EscanerDocumentos(listo: (ByteArray) -> Unit): (() -> Unit)? {
    val ctx = LocalContext.current
    val alListo by rememberUpdatedState(listo)
    val actividad = remember(ctx) { ctx.actividad() }
    val lanzador: ActivityResultLauncher<IntentSenderRequest> = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data) ?: return@rememberLauncherForActivityResult
        val pdf = res.pdf ?: return@rememberLauncherForActivityResult
        if (pdf.pageCount <= 0) return@rememberLauncherForActivityResult
        val datos = runCatching { ctx.contentResolver.openInputStream(pdf.uri)?.use { it.readBytes() } }.getOrNull() ?: return@rememberLauncherForActivityResult
        alListo(datos)
    }
    if (actividad == null) return null
    return {
        val opciones = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opciones).getStartScanIntent(actividad)
            .addOnSuccessListener { s -> lanzador.launch(IntentSenderRequest.Builder(s).build()) }
    }
}

/** El nombre del archivo elegido (lastPathComponent en iOS). */
private fun nombreDe(ctx: Context, u: Uri): String? = runCatching {
    ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: u.lastPathSegment
