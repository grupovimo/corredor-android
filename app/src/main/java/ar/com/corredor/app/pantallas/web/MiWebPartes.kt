package ar.com.corredor.app.pantallas.web

import ar.com.corredor.app.pantallas.propiedades.FichaImpresa
import ar.com.corredor.app.pantallas.propiedades.Propiedad

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.tema.*
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

// Clon de Pantallas/Web/MiWebPartes.swift.
// Lo que hace el editor de la web (web: lib/web/editor.ts, lib/sitio/armado.ts): las reglas para
// sumar, duplicar y quitar secciones y páginas, y medir si el título se lee sobre el velo.

object EditorWeb {
    const val maxSecciones = 16
    const val maxPaginasPropias = 6
    val tiposSeccion = listOf("portada", "destacadas", "barrios", "emprendimiento", "temporada", "texto", "testimonios", "equipo", "tasacion", "contacto")
    val paginasFijas = listOf("inicio", "propiedades", "temporada", "emprendimientos", "tasaciones", "nosotros")
    val rutasTomadas = listOf("propiedades", "lotes", "locales", "temporarios", "emprendimientos", "barrios", "tasaciones", "nosotros", "contacto",
        "favoritos", "alertas", "privacidad", "terminos", "expensas", "sueldos", "recibo", "og", "robots.txt", "sitemap.xml",
        "p", "api", "panel", "guardadas")

    /** «Cómo trabajamos» → «como-trabajamos». */
    fun rutaDe(titulo: String): String {
        val sin = Normalizer.normalize(titulo.lowercase(Formato.argentina), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return sin.replace("ñ", "n").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString("-")
    }

    /** Las secciones que se pueden sumar: el texto, siempre; las demás, si no están ya (hasta 16). */
    fun tiposParaSumar(inicio: List<JsonElement>): List<String> {
        if (inicio.size >= maxSecciones) return emptyList()
        return tiposSeccion.filter { t -> t != "portada" && (t == "texto" || inicio.none { it["tipo"].texto == t }) }
    }

    fun idNuevo(inicio: List<JsonElement>, tipo: String): String {
        if (tipo != "texto") return tipo
        var n = 1
        while (inicio.any { it["id"].texto == "texto-$n" }) n += 1
        return "texto-$n"
    }

    /** Por qué no sirve el nombre de una página nueva, o null si sirve. */
    fun problemaPagina(paginas: List<JsonElement>, titulo: String): String? {
        val t = titulo.trim()
        if (t.length < 2) return "Escribí el nombre de la página."
        if (t.length > 40) return "Hasta 40 letras."
        val ruta = rutaDe(t)
        if (ruta.isEmpty()) return "Usá letras o números."
        if (ruta in rutasTomadas || ruta in paginasFijas) return "Ese nombre ya lo usa una página de tu web."
        if (paginas.any { it["ruta"].texto == ruta }) return "Ya tenés una página con ese nombre."
        if (paginas.count { (it["id"].texto ?: "").startsWith("p-") } >= maxPaginasPropias) return "Hasta $maxPaginasPropias páginas propias."
        return null
    }

    /** El color «noche» de la marca (el velo de la portada): el primario oscurecido. */
    fun noche(primario: String?): Int {
        val p = Colores.rgb(primario) ?: 0x16443B
        return Colores.mezclar(p, 0x000000, 0.4)
    }

    /** ¿El título blanco se lee sobre la foto con ese velo? (peor caso: una foto blanca). */
    fun seLeeConVelo(noche: Int, velo: Double): Boolean =
        Colores.contraste(Colores.mezclar(0xFFFFFF, noche, max(0.0, min(90.0, velo)) / 100), 0xFFFFFF) >= 4.5

    /** El velo mínimo para que el título llegue a AA (o 90 si ni así). */
    fun veloMinimo(noche: Int): Int {
        for (v in 0..90 step 5) if (seLeeConVelo(noche, v.toDouble())) return v
        return 90
    }

    data class Plantilla(val id: String, val nombre: String, val texto: String, val grupo: String)

    /** Las plantillas (web: lib/web/marca.ts PLANTILLAS) con su grupo. */
    val plantillas = listOf(
        Plantilla("barrio", "Barrio", "Buscador sobre foto, destacadas, barrios.", "ciudad"),
        Plantilla("costa", "Costa", "Calendario de disponibilidad y tarifas al frente.", "costa"),
        Plantilla("clasica", "Clásica", "Sobria, listado primero. Para carteras grandes.", "ciudad"),
        Plantilla("emprendimiento", "Emprendimiento", "Una web por proyecto, con masterplan.", "emprendimientos"),
    )

    /** Los colores sugeridos (40E). */
    val colores = listOf("#1e3a5f", "#7c2d12", "#1f5a2a", "#4a2c5a")
}

/** Una versión publicada de la web (GET /v1/web/versiones). */
@Serializable
data class VersionWeb(
    val id: String,
    val creadaEn: String? = null,
    val autor: String? = null,
    val desdeId: String? = null,
    val plantilla: String? = null,
)

private fun cuandoDe(s: String?): String? = s?.let { Fecha.iso(it) }?.let { Formato.cuando(it) }

/** «Versiones publicadas»: cada vez que publicás queda una. Volver a una la publica de nuevo (y se
 *  descarta el borrador). */
@Composable
fun HojaVersionesWeb(alVolver: () -> Unit, cerrar: () -> Unit) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<VersionWeb>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var volviendo by remember { mutableStateOf<String?>(null) }

    suspend fun cargar() {
        try { lista = estado.api.get<List<VersionWeb>>("/v1/web/versiones"); error = null } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (_: Exception) {}
    }

    suspend fun volver(v: VersionWeb) {
        volviendo = v.id
        try {
            estado.api.hacer("POST", "/v1/web/versiones/${v.id}/restaurar")
            try { BorradorWeb.descartar(estado.api) } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (e.estado != 404) estado.avisar(e) } catch (e: Exception) { estado.avisar(e) }
            estado.cargarEstructura()
            estado.avisar("Volviste a la versión del ${cuandoDe(v.creadaEn) ?: ""}")
            alVolver()
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { volviendo = null }
    }

    LaunchedEffect(Unit) { cargar() }
    Column(Modifier.fillMaxSize()) {
        BarraHoja("Versiones publicadas", derecha = { BotonHoja("Listo", fuerte = true) { cerrar() } })
        ListaAgrupada {
            SeccionLista { FilaLista { Texto("Cada vez que publicás queda una. Volver a una la publica de nuevo.", Estilo.footnote, C.texto2) } }
            val l = lista
            val e = error
            if (l != null) {
                SeccionLista {
                    if (l.isEmpty()) FilaLista { Texto("Todavía no publicaste la web.", Estilo.body, C.texto2) }
                    l.forEachIndexed { i, v ->
                        if (i > 0) Separador()
                        FilaLista {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Texto(if (i == 0) "La de ahora" else (cuandoDe(v.creadaEn) ?: ""), Estilo.filaLista, C.texto)
                                Texto(listOfNotNull(if (i == 0) cuandoDe(v.creadaEn) else null, v.autor,
                                    v.plantilla?.let { p -> "plantilla ${EditorWeb.plantillas.firstOrNull { it.id == p }?.nombre ?: p}" })
                                    .joinToString(" · "), Estilo.footnote, C.texto2)
                            }
                            if (i > 0 && estado.esDuenio) {
                                BotonTonal(if (volviendo == v.id) "Volviendo…" else "Volver a esta", { alcance.launch { volver(v) } }, alto = 32.dp, habilitado = volviendo == null)
                            }
                        }
                    }
                }
            } else if (e != null) {
                EstadoError("las versiones", e) { alcance.launch { cargar() } }
            } else {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Rueda() }
            }
        }
    }
}

/** Una página propia de la web: el título, el texto (con «Reescribir con IA») y la vista previa. */
@Composable
fun EditorPaginaWeb(pagina: JsonElement, web: String?, guardar: (String, String) -> Unit, cerrar: () -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var titulo by remember(pagina) { mutableStateOf(pagina["titulo"].texto ?: "") }
    var texto by remember(pagina) { mutableStateOf(pagina["texto"].texto ?: "") }
    var reescribiendo by remember { mutableStateOf(false) }
    val ruta = pagina["ruta"].texto ?: EditorWeb.rutaDe(titulo)

    @Serializable data class CuerpoReescribir(val campo: String = "texto", val texto: String)
    @Serializable data class RespuestaReescribir(val texto: String)

    fun reescribir() = alcance.launch {
        reescribiendo = true
        val antes = texto
        try {
            val r: RespuestaReescribir = estado.api.enviar("POST", "/v1/web/reescribir", CuerpoReescribir(texto = texto))
            texto = r.texto
            estado.avisar("Reescrito", deshacer = { texto = antes })
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { reescribiendo = false }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(titulo.takeIf { it.isNotBlank() } ?: "Página",
            izquierda = { BotonHoja("Cancelar") { cerrar() } },
            derecha = { BotonHoja("Listo", fuerte = true, habilitado = titulo.trim().length >= 2) { guardar(titulo, texto); cerrar() } })
        ListaAgrupada {
            SeccionLista("Título") { FilaLista { Entrada(titulo, { titulo = it }, "Servicios", Modifier.weight(1f), estilo = Estilo.body16) } }
            SeccionLista("Texto") {
                FilaLista { Entrada(texto, { texto = it }, "Lo que querés contar en esta página", Modifier.weight(1f), estilo = Estilo.body16, lineas = 5, lineasMaximas = 16) }
                Separador()
                FilaLista({ reescribir() }, habilitado = texto.isNotEmpty() && !reescribiendo) {
                    ConIcono(if (reescribiendo) "Reescribiendo…" else "Reescribir con IA", "sparkles", color = C.avisoTinta)
                }
            }
            if (web != null) {
                val u = "$web/$ruta"
                SeccionLista {
                    FilaLista({ abrirUrl(ctx, u) }) { ConIcono("Vista previa · ${u.replace("https://", "")}", "safari") }
                }
            }
        }
    }
}

/** «Nueva página»: el nombre (2 a 40 letras, que no use una ruta tomada) y dónde va a estar. */
@Composable
fun NuevaPaginaWeb(paginas: List<JsonElement>, web: String?, crear: (String) -> Unit, cerrar: () -> Unit) {
    var titulo by remember { mutableStateOf("") }
    val problema = if (titulo.isEmpty()) null else EditorWeb.problemaPagina(paginas, titulo)
    Column(Modifier.fillMaxSize()) {
        BarraHoja("Nueva página",
            izquierda = { BotonHoja("Cancelar") { cerrar() } },
            derecha = { BotonHoja("Crear página", fuerte = true, habilitado = titulo.isNotEmpty() && problema == null) { crear(titulo.trim()); cerrar() } })
        ListaAgrupada {
            SeccionLista("Nombre de la página", "Una página tuya con un título y un texto: servicios, cómo trabajamos, preguntas.") {
                FilaLista { Entrada(titulo, { titulo = it }, "Servicios", Modifier.weight(1f), estilo = Estilo.body16) }
                if (problema != null) {
                    Separador(); FilaLista { MensajeError(problema) }
                } else if (web != null && titulo.trim().isNotEmpty()) {
                    Separador()
                    FilaLista { Texto("Va a estar en ${web.replace("https://", "")}/${EditorWeb.rutaDe(titulo)}", Estilo.footnote, C.texto2) }
                }
            }
        }
    }
}

/** La foto de portada sacada de una propiedad publicada. */
@Composable
fun ElegirFotoPortada(elegir: (String) -> Unit, cerrar: () -> Unit) {
    val estado = LocalEstado.current
    var props by remember { mutableStateOf<List<Propiedad>?>(null) }
    LaunchedEffect(Unit) {
        props = estado.api.opcional<List<Propiedad>>("/v1/propiedades", listOf("publicada" to "si", "limite" to "24")) ?: emptyList()
    }
    Column(Modifier.fillMaxSize()) {
        BarraHoja("Foto de una propiedad", izquierda = { BotonHoja("Cancelar") { cerrar() } })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val p = props
            if (p != null) {
                val conFotos = p.filter { !it.fotosUrl.isNullOrEmpty() }
                if (conFotos.isEmpty()) {
                    Box(Modifier.padding(top = 40.dp)) { EstadoVacio("photo", "Ninguna publicada tiene fotos", accion = "Volver" to { cerrar() }) }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    conFotos.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { x ->
                                Tocable({ x.fotosUrl?.firstOrNull()?.let { elegir(it); cerrar() } }, Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))) {
                                        Foto(x.miniatura, Modifier.matchParentSize())
                                        Texto(x.direccion ?: x.nombre, Estilo(11f, Peso.bold), Color.White,
                                            Modifier.align(Alignment.BottomStart).padding(6.dp).clip(Capsula).background(Color.Black.copy(alpha = 0.45f)).padding(6.dp), lineas = 1)
                                    }
                                }
                            }
                            if (fila.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { Rueda() }
            }
        }
    }
}

/** Plantillas: el filtro por tipo y aplicar una a la web publicada (tu contenido queda como estaba),
 *  con deshacer. */
@Composable
fun HojaPlantillas(alCambiar: () -> Unit, cerrar: () -> Unit) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var grupo by remember { mutableStateOf("todas") }
    var enUso by remember { mutableStateOf("barrio") }
    var marca by remember { mutableStateOf<JsonElement?>(null) }
    var aplicando by remember { mutableStateOf<String?>(null) }

    @Serializable data class I(val marca: JsonElement? = null)
    @Serializable data class Cu(val marca: JsonElement)

    suspend fun poner(id: String) {
        val o = (marca as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        o["plantilla"] = JsonPrimitive(id)
        estado.api.hacer("PATCH", "/v1/inmobiliaria", Cu(JsonObject(o)))
        marca = JsonObject(o)
        enUso = id
        alCambiar()
    }

    suspend fun usar(id: String) {
        if (!estado.esDuenio || id == enUso) return
        val antes = enUso
        aplicando = id
        try {
            poner(id)
            estado.avisar("Plantilla ${EditorWeb.plantillas.firstOrNull { it.id == id }?.nombre ?: id}: tu contenido queda como estaba", deshacer = {
                alcance.launch { try { poner(antes) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } }
            })
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { aplicando = null }
    }

    LaunchedEffect(Unit) {
        // GET /v1/inmobiliaria (Inmobiliaria.marca).
        estado.api.opcional<I>("/v1/inmobiliaria")?.let { i ->
            marca = i.marca
            enUso = i.marca["plantilla"].texto ?: "barrio"
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja("Elegí cómo arranca tu web", derecha = { BotonHoja("Listo", fuerte = true) { cerrar() } })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Texto("Se ven con tus propiedades y tu logo, no con fotos de muestra.", Estilo.subhead, C.texto2)
            Segmentado(listOf("todas" to "Todas", "ciudad" to "Ciudad", "costa" to "Costa", "emprendimientos" to "Emprendimientos"), grupo, { grupo = it })
            EditorWeb.plantillas.filter { grupo == "todas" || it.grupo == grupo }.forEach { p ->
                Tocable({ alcance.launch { usar(p.id) } }, habilitado = estado.esDuenio && aplicando == null) {
                    Row(Modifier.fillMaxWidth().tarjeta(relleno = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconoTono(
                            when (p.id) { "costa" -> "beach.umbrella"; "emprendimiento" -> "building.2"; "clasica" -> "list.bullet.rectangle"; else -> "magnifyingglass" },
                            if (p.id == enUso) Tono.verde else Tono.neutro,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(p.nombre, Estilo.filaLista, C.texto)
                            Texto(p.texto, Estilo.footnote, C.texto2)
                        }
                        if (aplicando == p.id) Rueda() else if (p.id == enUso) Texto("En uso", Estilo.footnoteFuerte, C.okTinta)
                    }
                }
            }
            if (!estado.esDuenio) Texto("La web la cambia el dueño o la dueña. Vos la podés ver como queda.", Estilo.footnote, C.texto2)
        }
    }
}

/** El QR (FichaImpresa.qr de iOS, el mismo de los impresos de las propiedades). */
fun qrWeb(texto: String): Bitmap? = FichaImpresa.qr(texto)

/** El QR de la web, para la vidriera o las tarjetas. */
@Composable
fun HojaQRWeb(web: String, nombre: String) {
    val ctx = LocalContext.current
    val q = remember(web) { qrWeb(web) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Grabber()
        Texto("El QR de tu web", Estilo.tituloHojaChico, C.texto)
        if (q != null) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
                Image(q.asImageBitmap(), null, Modifier.size(220.dp), filterQuality = FilterQuality.None)
            }
            BotonPrincipal("Compartir el QR", {
                // ShareLink(item: Image, preview: «QR de …»): la imagen por el FileProvider.
                runCatching {
                    val f = File(File(ctx.cacheDir, "compartir").apply { mkdirs() }, "qr-web.png")
                    f.outputStream().use { q.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = FileProvider.getUriForFile(ctx, "ar.com.corredor.app.archivos", f)
                    val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_TITLE, "QR de $nombre").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(i, "QR de $nombre").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, simbolo = "square.and.arrow.up")
        }
        Texto("Para la vidriera o las tarjetas: abre ${web.replace("https://", "")}.", Estilo.footnote, C.texto2, alinear = TextAlign.Center)
    }
}

