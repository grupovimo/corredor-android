package ar.com.corredor.app.pantallas.cobranzas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@Serializable
data class RendicionAPI(
    val id: String,
    val contratoId: String? = null,
    val periodo: String,
    val moneda: String? = null,
    val cobrado: Double? = null,
    val comisionPorcentaje: Double? = null,
    val comision: Double? = null,
    val gastos: Double? = null,
    val neto: Double? = null,
    val estado: String? = null,
    val notas: String? = null,
    val compartida: Boolean? = null,
    val numero: Int? = null,
    /** RFC 3339 (Date en iOS; se lee con instanteDe). */
    val emitidaEn: String? = null,
    val items: List<Item>? = null,
) {
    @Serializable
    data class Item(
        val id: String,
        val descripcion: String,
        val monto: Double,
        /** El comprobante del gasto (factura, ticket). */
        val archivoId: String? = null,
    )

    companion object {
        fun estadoTexto(e: String?): String =
            mapOf("pendiente" to "Borrador para revisar", "liquidada" to "Emitida", "pagada" to "Transferida")[e ?: ""] ?: "Borrador"

        /** «0012». */
        fun numeroTexto(n: Int?): String? = n?.let { String.format("%04d", it) }
    }
}

/** Lo que ve el propietario por el link y lo que se imprime (GET …/rendiciones/{rid}/imprimir). */
@Serializable
data class ComprobanteRendicion(
    val inmobiliaria: Inmo? = null,
    val periodo: String,
    val moneda: String,
    val propiedadTitulo: String? = null,
    val propiedadDireccion: String? = null,
    val propietario: String? = null,
    val inquilino: String? = null,
    val cobrado: Double,
    val comisionPorcentaje: Double? = null,
    val comision: Double? = null,
    val items: List<RendicionAPI.Item>? = null,
    val gastos: Double? = null,
    val neto: Double,
    val estado: String,
    val notas: String? = null,
    val pagadoEl: String? = null,
    val proximo: Proximo? = null,
    val numero: Int? = null,
) {
    @Serializable data class Inmo(val nombre: String)
    @Serializable data class Proximo(val indice: String, val fecha: String, val monto: Double? = null, val estimado: Boolean? = null)
}

@Serializable private data class CuerpoGasto(val descripcion: String, val monto: Double, val archivoId: String? = null)
@Serializable private data class CuerpoComprobante(val archivoId: String? = null)
@Serializable private data class CuerpoEstadoRendicion(val estado: String)
@Serializable private data class LinkRendicion(val url: String)
@Serializable private data class InfoArchivoRendicion(val nombre: String? = null, val tipo: String? = null)

/** El nombre de un archivo elegido (lastPathComponent en iOS). */
private fun nombreDe(ctx: Context, u: Uri): String =
    runCatching {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: (u.lastPathSegment ?: "archivo")

/** La rendición al propietario (24E): se arma sola con lo cobrado; se revisa, se suma un gasto si hubo
 *  (con su comprobante) y se emite. Emitir es lo único que pide confirmación (le llega a otra persona). */
@Composable
fun RendicionVista(
    contrato: String,
    id: String,
    /** Abre «Así la ve <propietario>» al entrar (`?vista=1`). */
    verComoPropietario: Boolean = false,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var r by remember { mutableStateOf<RendicionAPI?>(null) }
    var comp by remember { mutableStateOf<ComprobanteRendicion?>(null) }
    var k by remember { mutableStateOf<ContratoAPI?>(null) }
    var propietario by remember { mutableStateOf<FichaContrato.Persona?>(null) }
    var otras by remember { mutableStateOf<List<RendicionAPI>>(emptyList()) }
    var listas by remember { mutableStateOf<List<RendicionesListas.Lista>>(emptyList()) }
    var sacados by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    // Sumar un gasto.
    var sumando by remember { mutableStateOf(false) }
    var descripcion by remember { mutableStateOf("") }
    var montoTexto by remember { mutableStateOf("") }
    var comprobante by remember { mutableStateOf<Archivo?>(null) }
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var guardando by remember { mutableStateOf(false) }
    // Comprobantes.
    var adjuntandoA by remember { mutableStateOf<String?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    // Emitir y mandar.
    var confirmar by remember { mutableStateOf(false) }
    var emitiendo by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    var texto by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf(false) }
    var vista by remember { mutableStateOf(false) }
    var vistaHecha by remember { mutableStateOf(false) }

    val ruta = "/v1/contratos/$contrato/rendiciones/$id"
    val nombre = propietario?.nombre ?: comp?.propietario ?: "el propietario"
    val n = TextosCobro.primerNombre(nombre)
    val mes = TextosCobro.nombreMes(r?.periodo ?: "")
    val editable = r?.estado == "pendiente" && estado.escribe
    val items = (r?.items ?: emptyList()).filter { it.id !in sacados }
    val neto = (r?.neto ?: 0.0) + (r?.items ?: emptyList()).filter { it.id in sacados }.sumOf { it.monto }
    val moneda = r?.moneda ?: "ARS"
    /** TextField(value: $monto, format: .number) con .decimalPad: acepta la coma. */
    val monto: Double? = montoTexto.replace(".", "").replace(",", ".").toDoubleOrNull()

    suspend fun cargar() {
        try {
            val x: RendicionAPI = estado.api.get(ruta)
            comp = estado.api.opcional("$ruta/imprimir")
            val c: ContratoAPI? = estado.api.opcional("/v1/contratos/$contrato")
            k = c
            otras = estado.api.opcional("/v1/contratos/$contrato/rendiciones") ?: emptyList()
            c?.propietarioId?.let { p -> propietario = estado.api.opcional("/v1/contactos/$p") }
            listas = estado.api.opcional("/v1/rendiciones/listas", listOf("mes" to x.periodo.take(7))) ?: emptyList()
            sacados = emptySet()
            r = x
            error = null
            if (verComoPropietario && !vistaHecha) { vistaHecha = true; vista = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            if (r == null) error = e
        } catch (e: Exception) {
        }
    }

    suspend fun sumar() {
        val e = mutableMapOf<String, String>()
        val d = descripcion.trim()
        if (d.isEmpty()) e["descripcion"] = "Poné qué se descuenta."
        if (!((monto ?: 0.0) > 0)) e["monto"] = "Poné un monto mayor a cero."
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        try {
            val nueva: RendicionAPI = estado.api.enviar("POST", "$ruta/items", CuerpoGasto(d, monto ?: 0.0, comprobante?.id))
            val item = nueva.items?.lastOrNull()
            r = nueva
            estado.avisar("Sumaste «$d»", deshacer = {
                if (item != null) {
                    estado.tarea {
                        try { estado.api.hacer("DELETE", "$ruta/items/${item.id}") } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) { estado.avisar(e2) }
                        estado.version += 1
                    }
                }
            })
            descripcion = ""
            montoTexto = ""
            comprobante = null
            sumando = false
            estado.version += 1
        } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) { estado.avisar(e2) } finally { guardando = false }
    }

    /** Sacar un gasto: «Deshacer» lo vuelve a sumar igual, con su comprobante. */
    fun sacar(it: RendicionAPI.Item) {
        sacados = sacados + it.id
        val pedido = CompletableDeferred<Boolean>()
        estado.tarea {
            try { estado.api.hacer("DELETE", "$ruta/items/${it.id}"); pedido.complete(true) } catch (e: Exception) {
                sacados = sacados - it.id
                estado.avisar("No se sacó · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
                pedido.complete(false)
            }
        }
        estado.avisar("Sacaste «${it.descripcion}»", deshacer = {
            estado.tarea {
                if (!pedido.await()) return@tarea
                try { estado.api.hacer("POST", "$ruta/items", CuerpoGasto(it.descripcion, it.monto, it.archivoId)) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                estado.version += 1
            }
        })
        estado.tarea { if (pedido.await()) estado.version += 1 }
    }

    /** El comprobante del gasto nuevo, o de uno ya cargado (PATCH …/items/{item} {archivoId}). */
    suspend fun subirComprobante(datos: ByteArray, nombreArchivo: String, tipo: String) {
        val destino = adjuntandoA ?: return
        subiendo = true
        try {
            val a = estado.api.subir(datos, nombre = nombreArchivo, tipo = tipo, uso = "documento")
            if (destino == "nuevo") {
                comprobante = a
            } else {
                estado.api.hacer("PATCH", "$ruta/items/$destino", CuerpoComprobante(a.id))
                estado.version += 1
                estado.avisar("Comprobante adjunto")
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar("No se adjuntó · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
        } finally {
            subiendo = false
            adjuntandoA = null
        }
    }

    suspend fun subirArchivo(u: Uri) {
        val d = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }
        if (d == null) { estado.avisar("No se pudo leer el archivo.", ok = false); return }
        val nombreArchivo = nombreDe(ctx, u)
        subirComprobante(d, nombreArchivo, if (nombreArchivo.substringAfterLast('.', "").lowercase() == "pdf") "application/pdf" else "image/jpeg")
    }

    suspend fun ver(archivo: String, nombreArchivo: String) {
        try {
            val info: InfoArchivoRendicion? = estado.api.opcional("/v1/archivos/$archivo")
            val esPdf = info?.tipo?.contains("pdf") == true
            val f = Documentos.archivo("/v1/archivos/$archivo/contenido", nombre = info?.nombre ?: "$nombreArchivo.${if (esPdf) "pdf" else "jpg"}", api = estado.api)
            verArchivo(ctx, f, if (esPdf) "application/pdf" else "image/*")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun emitir() {
        emitiendo = true
        try {
            val l: LinkRendicion = estado.api.enviar("POST", "$ruta/emitir", emptyMap<String, String>())
            emitiendo = false
            confirmar = false
            url = l.url
            texto = TextosCobro.rendicion(nombre, r?.periodo ?: "", r?.neto ?: 0.0, moneda, l.url)
            enviado = false
            Haptico.exito()
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitiendo = false
            confirmar = false
            val detalle = (e as? ErrorApi)?.detalle ?: ""
            estado.sugerir("No se emitió · $detalle", "Reintentar") { confirmar = true }
        }
    }

    /** Un link nuevo (el anterior deja de andar): para volver a mandarlo. */
    suspend fun linkNuevo() {
        try {
            val l: LinkRendicion = estado.api.enviar("POST", "$ruta/compartir", emptyMap<String, String>())
            url = l.url
            texto = TextosCobro.rendicion(nombre, r?.periodo ?: "", r?.neto ?: 0.0, moneda, l.url)
            enviado = false
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar("No pudimos armar el link · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
        }
    }

    suspend fun transferida() {
        try {
            estado.api.hacer("PATCH", ruta, CuerpoEstadoRendicion("pagada"))
            estado.version += 1
            estado.avisar("Transferencia a $n marcada como hecha", deshacer = {
                estado.tarea {
                    try { estado.api.hacer("PATCH", ruta, CuerpoEstadoRendicion("liquidada")) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    estado.version += 1
                }
            })
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar("No se guardó · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
        }
    }

    suspend fun pdf() {
        val c = comp ?: return
        Documentos.rendicion(c, items = items, neto = neto, api = estado.api)?.let { verArchivo(ctx, it) }
    }

    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) alcance.launch { subirArchivo(u) } else adjuntandoA = null
    }
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) { adjuntandoA = null; return@rememberLauncherForActivityResult }
        alcance.launch {
            val d = withContext(Dispatchers.IO) {
                runCatching {
                    val crudo = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@runCatching null
                    BitmapFactory.decodeByteArray(crudo, 0, crudo.size)?.let { b ->
                        ByteArrayOutputStream().also { o -> b.compress(Bitmap.CompressFormat.JPEG, 85, o) }.toByteArray()
                    } ?: crudo
                }.getOrNull()
            }
            if (d != null) subirComprobante(d, "Comprobante.jpg", "image/jpeg") else adjuntandoA = null
        }
    }
    fun opcionesAdjuntar(destino: String) = listOf(
        OpcionDeMenu("Un archivo (PDF)", "doc") { adjuntandoA = destino; elegirArchivo.launch(arrayOf("application/pdf", "image/*")) },
        OpcionDeMenu("Una foto", "photo") { adjuntandoA = destino; elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Rendición", acciones = {
        Box(Modifier.size(44.dp).clickable { alcance.launch { pdf() } }.semantics { contentDescription = "PDF de la rendición" }, contentAlignment = Alignment.Center) {
            Icono("printer", 19f, C.accion)
        }
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val x = r
            if (x != null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Cabecera
                    val estadoTxt = if (x.estado == "liquidada") " · emitida" else if (x.estado == "pagada") " · transferida" else ""
                    val titulo = RendicionAPI.numeroTexto(x.numero)?.let { "Rendición N.º $it · $mes" } ?: "Rendición de $mes"
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Texto(titulo + estadoTxt, Estilo.footnote, C.texto2)
                        Texto(nombre, Estilo.titulo, C.texto)
                        Texto(comp?.propiedadTitulo ?: k?.propiedadTitulo ?: "", Estilo.subhead, C.texto2)
                    }
                    // Tabla
                    Column(Modifier.fillMaxWidth().tarjeta()) {
                        LineaRendicion("Alquiler cobrado${comp?.inquilino?.let { " a $it" } ?: ""}", Formato.numero((x.cobrado ?: 0.0).roundToInt()), primera = true)
                        x.comision?.let { c -> if (c > 0) LineaRendicion("Administración ${TextosCobro.numero(x.comisionPorcentaje ?: 0.0)} %", "−" + Formato.numero(c.roundToInt()), gris = true) }
                        items.forEach { it ->
                            LineaFila()
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Texto(it.descripcion, Estilo.subhead, C.texto2)
                                val a = it.archivoId
                                if (a != null) {
                                    Box(Modifier.clickable { alcance.launch { ver(a, "Comprobante ${it.descripcion}") } }.semantics { contentDescription = "Ver el comprobante" }) {
                                        Icono("doc.text.fill", 15f, C.azulTinta)
                                    }
                                } else if (editable) {
                                    MenuBoton(opcionesAdjuntar(it.id), "Adjuntar el comprobante de «${it.descripcion}»") { Icono("paperclip", 15f, C.texto3) }
                                }
                                Relleno()
                                Texto("−" + Formato.numero(it.monto.roundToInt()), Estilo.subhead, C.texto)
                                if (editable) {
                                    Box(Modifier.clickable { sacar(it) }.semantics { contentDescription = "Sacar «${it.descripcion}»" }) {
                                        Icono("xmark.circle.fill", 15f, C.texto3)
                                    }
                                }
                            }
                        }
                        if (editable) {
                            LineaFila()
                            if (sumando) {
                                // Formulario del gasto
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Etiquetado("Qué se descuenta") {
                                        CampoCobro(descripcion, { t -> descripcion = if (t.length > 200) t.take(200) else t }, "Arreglo de canilla · Plomería Rossi", Modifier.fillMaxWidth())
                                        errores["descripcion"]?.let { e -> Texto(e, Estilo.footnote, C.errorTinta) }
                                    }
                                    Etiquetado("Monto") {
                                        CampoCobro(montoTexto, { t -> montoTexto = t.filter { it.isDigit() || it == ',' || it == '.' } }, moneda, Modifier.fillMaxWidth(),
                                            teclado = KeyboardType.Decimal)
                                        errores["monto"]?.let { e -> Texto(e, Estilo.footnote, C.errorTinta) }
                                    }
                                    val cp = comprobante
                                    if (cp != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Etiqueta(cp.nombre ?: "Comprobante", "doc.text.fill", Estilo.subhead, C.texto)
                                            Relleno()
                                            BotonLink("Sacar", Estilo.subheadFuerte, C.error) { comprobante = null }
                                        }
                                    } else {
                                        MenuBoton(if (subiendo) emptyList() else opcionesAdjuntar("nuevo"), "El comprobante") {
                                            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.hundida), contentAlignment = Alignment.Center) {
                                                Etiqueta(if (subiendo) "Subiendo…" else "El comprobante: tocá para elegir", "paperclip", Estilo.subhead, C.accion)
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BotonHundido("Cancelar", { sumando = false; errores = emptyMap() }, Modifier.weight(1f))
                                        if (guardando) {
                                            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.accion), contentAlignment = Alignment.Center) {
                                                CargandoCobro(C.sobreAccion)
                                            }
                                        } else {
                                            BotonAccion("Sumar", { alcance.launch { sumar() } }, Modifier.weight(1f), habilitado = !(guardando || subiendo))
                                        }
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { sumando = true }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Etiqueta("Sumar gasto o ajuste", "plus", Estilo.subheadFuerte, C.accion)
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.linea2))
                        Row(Modifier.fillMaxWidth().padding(14.dp)) {
                            Texto("A transferir a $n", Estilo(17f, Peso.bold), C.texto)
                            Relleno()
                            Texto(Formato.plata(neto, moneda), Estilo(17f, Peso.bold), C.texto)
                        }
                    }
                    // Al emitir, emitida
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.crema2).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (x.estado == "pendiente" && estado.esDuenio) {
                            Texto("Al emitir", Estilo.headline, C.texto)
                            PuntoRendicion("lock", "Queda emitida y no se puede editar. Si hay un error, se corrige con una nota en la próxima.")
                            PuntoRendicion("bubble.left", "Le mandamos el link por WhatsApp a $n.")
                            PuntoRendicion("building.columns", "Te queda pendiente «Transferir ${Formato.plata(neto, moneda)}» hasta que la marques hecha.")
                            BotonPrincipal("Emitir y mandar", { confirmar = true })
                            BotonHundido("Ver como la ve $n", { vista = true })
                            // «Quedan N rendiciones listas este mes», al próximo contrato.
                            val otrasListas = listas.filter { it.contratoId != contrato }
                            otrasListas.firstOrNull()?.let { primera ->
                                Box(Modifier.fillMaxWidth().clickable { estado.abrir(AFichaContrato(id = primera.contratoId)) }, contentAlignment = Alignment.Center) {
                                    Texto("Quedan ${if (otrasListas.size == 1) "1 rendición lista" else "${otrasListas.size} rendiciones listas"} este mes",
                                        Estilo.subheadFuerte, C.accion)
                                }
                            }
                        } else if (x.estado == "pendiente") {
                            if (estado.escribe) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icono("lock", 20f, C.texto2)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Texto("Las rendiciones las emite la dueña", Estilo.subheadFuerte, C.texto)
                                        Texto("Como agente podés prepararlas y dejarlas listas. La dueña las revisa y las manda.", Estilo.footnote, C.texto2)
                                    }
                                }
                            }
                            BotonHundido("Ver como la ve $n", { vista = true })
                        } else {
                            Texto(if (x.estado == "pagada") "Transferida" else "Emitida", Estilo.headline, C.texto)
                            val u = url
                            if (u != null) {
                                MensajeWhatsApp(propietario?.id ?: k?.propietarioId, propietario?.telefono, nombre, texto, { texto = it }, "rendicion",
                                    listOf(n, mes, Formato.plata(x.neto ?: 0.0, moneda), u), "Quedó en la ficha de $n.", enviado, { enviado = it },
                                    alListo = { url = null })
                            } else {
                                PuntoRendicion(if (x.estado == "pagada") "checkmark.circle" else "building.columns",
                                    if (x.estado == "pagada") "Le transferiste ${Formato.plata(x.neto ?: 0.0, moneda)} a $n." else "Falta transferirle ${Formato.plata(x.neto ?: 0.0, moneda)} a $n.")
                                if (x.estado == "liquidada" && estado.escribe) {
                                    BotonPrincipal("Ya la transferí", { alcance.launch { transferida() } }, simbolo = "checkmark")
                                }
                                if (estado.escribe) {
                                    BotonHundido("Mandar el link de nuevo", { alcance.launch { linkNuevo() } }, simbolo = "bubble.left")
                                }
                                BotonTexto("Ver como la ve $n", { vista = true })
                            }
                        }
                    }
                    // Panel de la sección
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (otras.isNotEmpty()) {
                            TituloBloque("Rendiciones de este contrato")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                otras.take(6).forEachIndexed { i, o ->
                                    if (i > 0) LineaFila()
                                    Row(Modifier.fillMaxWidth().clickable { if (o.id != id) estado.abrir(ARendicion(contrato = contrato, id = o.id)) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                            Icono("doc.plaintext", 17f, if (o.estado == "pendiente") C.oroTinta else C.texto2)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Texto(Formato.mayuscula(TextosCobro.nombreMes(o.periodo)), Estilo.filaLista, C.texto)
                                            Texto(if (o.estado == "pendiente") "para revisar" else if (o.estado == "liquidada") "emitida" else "transferida", Estilo.footnote, C.texto2)
                                        }
                                        if (o.id == id) Icono("checkmark", 17f, C.accion) else Chevron()
                                    }
                                }
                            }
                        }
                        TituloBloque("Contrato")
                        Row(Modifier.fillMaxWidth().tarjeta().clickable { estado.abrir(AFichaContrato(id = contrato)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("doc.text", 17f, C.texto2) }
                            Texto(k?.propiedadTitulo ?: "Contrato", Estilo.filaLista, C.texto, Modifier.weight(1f))
                            Chevron()
                        }
                    }
                }
            } else if (error != null) {
                Box(Modifier.padding(top = 40.dp)) { EstadoError("la rendición", error!!) { alcance.launch { cargar() } } }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CargandoCobro() }
            }
        }
    }

    // Confirmar
    Hoja(confirmar, { if (!emitiendo) confirmar = false }, alturas = listOf(Altura.media, Altura.grande)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Etiqueta("Vas a emitir $mes", "checkmark.seal", Estilo.tituloHojaChico, C.texto)
            Texto("Le llega a $nombre y ya no se puede editar.", Estilo.subhead, C.texto2)
            Column(Modifier.fillMaxWidth().tarjeta()) {
                LineaRendicion(comp?.propiedadTitulo ?: k?.propiedadTitulo ?: "", "", primera = true)
                LineaRendicion("Cobrado", Formato.numero((r?.cobrado ?: 0.0).roundToInt()))
                r?.comision?.let { c -> if (c > 0) LineaRendicion("Administración", "−" + Formato.numero(c.roundToInt()), gris = true) }
                items.forEach { LineaRendicion(it.descripcion, "−" + Formato.numero(it.monto.roundToInt()), gris = true) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.linea2))
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Texto("A transferir", Estilo(17f, Peso.bold), C.texto)
                    Relleno()
                    Texto(Formato.plata(neto, moneda), Estilo(17f, Peso.bold), C.texto)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonHundido("Cancelar", { confirmar = false }, Modifier.weight(1f))
                BotonAccion(if (emitiendo) "Emitiendo…" else "Emitir y mandar", { alcance.launch { emitir() } }, Modifier.weight(1f), habilitado = !emitiendo)
            }
        }
    }
    // Así la ve el propietario
    Hoja(vista, { vista = false }) {
        Column(Modifier.fillMaxSize().background(C.fondo)) {
            BarraHoja("Así la ve $n", cancelar = null, alCancelar = {}, confirmar = "Listo", alConfirmar = { vista = false })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                comp?.let { c -> Box(Modifier.padding(16.dp)) { RendicionPublica(c, items, neto) } }
            }
        }
    }
}

@Composable
private fun LineaRendicion(l: String, v: String, primera: Boolean = false, gris: Boolean = false) {
    Column {
        if (!primera) LineaFila()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Texto(l, Estilo.subhead, if (gris) C.texto2 else C.texto, Modifier.weight(1f, fill = false))
            Relleno()
            Texto(v, Estilo.subhead, C.texto)
        }
    }
}

@Composable
private fun PuntoRendicion(icono: String, t: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { Icono(icono, 16f, C.texto2) }
        Texto(t, Estilo.subhead, C.texto)
    }
}

/** Lo que ve el propietario al tocar el link (24 «Link público de la rendición»): el neto grande en la
 *  tarjeta verde, el detalle y el próximo ajuste. */
@Composable
fun RendicionPublica(c: ComprobanteRendicion, items: List<RendicionAPI.Item>, neto: Double) {
    val nombre = TextosCobro.primerNombre(c.propietario)
    val mes = TextosCobro.nombreMes(c.periodo)
    val inmo = c.inmobiliaria?.nombre ?: ""
    val pie = run {
        var t = c.propiedadTitulo ?: ""
        val i = c.inquilino; val p = c.pagadoEl
        t += if (i != null && p != null) " · $i pagó el ${TextosCobro.fechaCortita(p)}." else "."
        val px = c.proximo; val m = px?.monto
        if (px != null && m != null) {
            val fecha = Fecha.fecha(px.fecha)?.let { Formato.diaCompleto(it).split(" ").drop(1).joinToString(" ") } ?: px.fecha
            t += " El alquiler ajusta por ${TextosCobro.nombreIndice[px.indice] ?: "índice"} el $fecha a ${Formato.plata(m, c.moneda)}${if (px.estimado == true) " (estimado)" else ""}."
        }
        c.notas?.takeIf { it.isNotBlank() }?.let { t += "\n$it" }
        t
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(C.verde700), contentAlignment = Alignment.Center) {
                Texto(inmo.take(1).uppercase(), Estilo(15f, Peso.bold), C.oro)
            }
            Column {
                Texto(inmo, Estilo.subheadFuerte, C.texto)
                Texto("Rendición de $mes", Estilo.caption, C.texto2)
            }
        }
        // C.verde700 con el GranoClaro en mosaico.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).texturaVerde(C.verde700).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto("Hola${if (nombre.isEmpty()) "" else " $nombre"}, te ${if (c.estado == "pagada") "transferimos" else "vamos a transferir"}", Estilo.subhead, Color.White)
            Texto(Formato.plata(neto, c.moneda), Estilo(34f, Peso.bold, tracking = -0.03f), Color.White)
            Texto("a tu cuenta", Estilo.footnote, Color.White)
        }
        Column(Modifier.fillMaxWidth().tarjeta()) {
            FilaPublica("Alquiler${c.inquilino?.let { " cobrado a $it" } ?: ""}", Formato.numero(c.cobrado.roundToInt()), primera = true)
            c.comision?.let { x -> if (x > 0) FilaPublica("Administración ${TextosCobro.numero(c.comisionPorcentaje ?: 0.0)} %", "−" + Formato.numero(x.roundToInt())) }
            items.forEach { FilaPublica(it.descripcion + (if (it.archivoId == null) "" else " · comprobante adjunto"), "−" + Formato.numero(it.monto.roundToInt())) }
        }
        Texto(pie, Estilo.footnote, C.texto2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Imagen(Imagenes.simbolo, Modifier.height(10.dp))
            TextoRico(buildAnnotatedString {
                append("Hecha con ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Corredor") }
            }, Estilo(11f, Peso.regular), C.texto3)
        }
    }
}

@Composable
private fun FilaPublica(l: String, v: String, primera: Boolean = false) {
    Column {
        if (!primera) LineaFila()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Texto(l, Estilo.subhead, C.texto2, Modifier.weight(1f, fill = false))
            Relleno()
            Texto(v, Estilo.subhead, C.texto)
        }
    }
}
