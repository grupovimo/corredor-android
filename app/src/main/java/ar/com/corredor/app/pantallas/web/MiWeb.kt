package ar.com.corredor.app.pantallas.web

import ar.com.corredor.app.app.PantallaApilada

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BotonBarraIcono
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import kotlin.math.roundToInt

// Clon de Pantallas/Web/MiWeb.swift.

// MARK: - Mi web

/** Una parte de la web tocada en la vista previa. */
data class ZonaWeb(val clave: String)

@Serializable
data class BarrioWeb(val nombre: String, val cantidad: Int? = null, val texto: String? = null)

/** Los nombres de las secciones del inicio (MiWeb.nombreSeccion). */
object MiWeb {
    fun nombreSeccion(t: String?): String = mapOf(
        "portada" to "Portada con buscador", "destacadas" to "Destacadas", "barrios" to "Barrios", "emprendimiento" to "Emprendimiento", "temporada" to "Temporada",
        "texto" to "Texto", "testimonios" to "Testimonios", "equipo" to "Equipo", "tasacion" to "Tasá tu propiedad", "contacto" to "Contacto",
    )[t ?: ""] ?: (t ?: "Sección")

    fun nombrePagina(x: JsonElement): String {
        x["titulo"].texto?.let { return it }
        return mapOf("inicio" to "Inicio", "propiedades" to "Comprar · Alquilar", "temporada" to "Temporada", "emprendimientos" to "Emprendimientos",
            "tasaciones" to "Tasá tu propiedad", "nosotros" to "Nosotros")[x["id"].texto ?: ""] ?: "Página"
    }

    val plantillas = listOf(Triple("barrio", "Barrio", "Buscador sobre foto, destacadas, barrios."), Triple("costa", "Costa", "Calendario y tarifas al frente."),
        Triple("clasica", "Clásica", "Sobria, listado primero."), Triple("emprendimiento", "Emprendimiento", "Una web por proyecto."))
}

/** Un número de un JSON libre, con punto de miles (Informe.num de iOS). */
private fun num(v: JsonElement?): String = v.numero?.let { Formato.numero(it.toInt()) } ?: "0"

/** LabeledContent de SwiftUI: el título a la izquierda y el valor en tinta 2 a la derecha. */
@Composable
private fun FilaValor(titulo: String, valor: String, alTocar: (() -> Unit)? = null) {
    FilaLista(alTocar) {
        Texto(titulo, Estilo.body16, C.texto, Modifier.weight(1f))
        Texto(valor, Estilo.body16, C.texto2, Modifier.weight(1f, fill = false), alinear = TextAlign.End, lineas = 1)
    }
}

/** Un Toggle de Form con su título. */
@Composable
private fun FilaInterruptor(titulo: String, prendido: Boolean, habilitado: Boolean = true, alCambiar: (Boolean) -> Unit) {
    FilaLista(habilitado = habilitado) {
        Texto(titulo, Estilo.body16, C.texto, Modifier.weight(1f))
        Llave(prendido, alCambiar, color = C.accion, habilitado = habilitado)
    }
}

/** Mi web: arriba la web real con los cambios (tocás una parte y se abre su editor), el logo y la
 *  portada, el link para compartir, la plantilla, los colores, la frase, nosotros, contacto y los
 *  textos de cada barrio. Todo se guarda solo en el borrador (el mismo que el panel web) y sale a la
 *  web con «Publicar»; los barrios y el dominio, al instante (no son de la marca). */
@Composable
fun MiWeb() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var web by remember { mutableStateOf<String?>(null) }
    var marca by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    /** La marca entera como la guarda la API (logo, `web` del editor…): PATCH la reemplaza toda, así
     *  que se manda completa con los textos cambiados encima. */
    var completa by remember { mutableStateOf<Map<String, JsonElement>>(emptyMap()) }
    val barrios = remember { mutableStateListOf<BarrioWeb>() }
    var guardado by remember { mutableStateOf<Job?>(null) }
    var guardadas by remember { mutableStateOf<JsonElement?>(null) }
    var dominio by remember { mutableStateOf<JsonElement?>(null) }
    var dominioNuevo by remember { mutableStateOf("") }
    var reescribiendo by remember { mutableStateOf<String?>(null) }
    var paginaNueva by remember { mutableStateOf(false) }
    var editandoSeccion by remember { mutableStateOf<Int?>(null) }
    // El borrador: el `en` del último guardado (para no pisar lo de otro), si hay cambios sin publicar,
    // la vista previa y lo que se tocó en ella.
    var en by remember { mutableStateOf<String?>(null) }
    var hayBorrador by remember { mutableStateOf(false) }
    var previa by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableStateOf(0) }
    var zona by remember { mutableStateOf<ZonaWeb?>(null) }
    var publicando by remember { mutableStateOf(false) }
    var versiones by remember { mutableStateOf(false) }
    var plantillas by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    var fotoPortada by remember { mutableStateOf(false) }
    var editandoPagina by remember { mutableStateOf<Int?>(null) }
    var legales by remember { mutableStateOf<JsonElement?>(null) }
    var informe by remember { mutableStateOf<JsonElement?>(null) }
    var errorDominio by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }
    var nombreInmo by remember { mutableStateOf("") }
    var barrioFoco by remember { mutableStateOf<String?>(null) }
    // Deshacer y rehacer (50 pasos): la marca entera antes de cada cambio.
    val pasado = remember { mutableStateListOf<Map<String, JsonElement>>() }
    val futuro = remember { mutableStateListOf<Map<String, JsonElement>>() }
    var ultimoPaso by remember { mutableStateOf(Instant.EPOCH) }
    /** «Sumar sección acá» del menú de una sección (el submenú de iOS). */
    var sumarDespuesDe by remember { mutableStateOf<Int?>(null) }

    val puede = estado.esDuenio

    fun webDe(): Map<String, JsonElement> = completa["web"].objeto ?: emptyMap()

    val secciones: List<JsonElement> = webDe()["inicio"].lista ?: listOf(
        jObjeto(mapOf("id" to jTexto("portada"), "tipo" to jTexto("portada"))), jObjeto(mapOf("id" to jTexto("destacadas"), "tipo" to jTexto("destacadas"))),
        jObjeto(mapOf("id" to jTexto("barrios"), "tipo" to jTexto("barrios"))),
    )
    val paginas: List<JsonElement> = webDe()["paginas"].lista
        ?: listOf("inicio", "propiedades", "temporada", "emprendimientos", "tasaciones", "nosotros").map { jObjeto(mapOf("id" to jTexto(it))) }
    val portada: Map<String, JsonElement> = webDe()["portada"].objeto ?: emptyMap()
    val veloActual: Double = portada["velo"].numero ?: 45.0

    // MARK: Deshacer, quitar, duplicar y el dominio

    /** La marca entera con lo que se está escribiendo encima. */
    fun actual(): Map<String, JsonElement> {
        val m = completa.toMutableMap()
        for ((k, v) in marca) m.poner(k, v)
        return m
    }

    /** Guarda el paso para deshacer (al escribir, uno cada tanto; los demás cambios, siempre). */
    fun recordar(forzar: Boolean = false) {
        if (!forzar && Instant.now().toEpochMilli() - ultimoPaso.toEpochMilli() <= 1500) return
        ultimoPaso = Instant.now()
        pasado.add(actual())
        if (pasado.size > 50) pasado.removeAt(0)
        futuro.clear()
    }

    suspend fun traerBorrador() {
        val b = try { BorradorWeb.leer(estado.api) } catch (e: CancellationException) { throw e } catch (e: Exception) { return }
        if (!(b.en != en || b.hayBorrador != hayBorrador || completa.isEmpty())) return
        completa = b.marca
        marca = b.marca.mapNotNull { (k, v) -> v.texto?.let { k to it } }.toMap()
        en = b.en
        hayBorrador = b.hayBorrador
        recarga += 1
    }

    /** Guarda el borrador (no publica): enseguida con `ya`, o un ratito después de dejar de escribir. */
    fun programar(ya: Boolean = false) {
        guardado?.cancel()
        hayBorrador = true
        lateinit var esta: Job
        esta = alcance.launch(start = CoroutineStart.LAZY) {
            if (!ya) delay(900)
            val m = completa.toMutableMap()
            for ((k, v) in marca) m.poner(k, v)
            // Lo que se borró del todo (un texto vaciado) también sale.
            for (k in completa.keys) if (completa[k].texto != null && marca[k] == null) m.remove(k)
            try {
                val b = BorradorWeb.guardar(estado.api, m, en)
                completa = b.marca
                en = b.en
                hayBorrador = b.hayBorrador
                recarga += 1
            } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                if (e.codigo == "borrador_cambiado") {
                    estado.avisar("Tu web se cambió desde otro lado. Trajimos lo último.", ok = false)
                    traerBorrador()
                } else {
                    estado.sugerir("${e.detalle} · No se guardó", "Reintentar") { programar(ya = true) }
                }
            } catch (e: Exception) {
                estado.sugerir("No se guardó · No se guardó", "Reintentar") { programar(ya = true) }
            }
            if (guardado === esta) guardado = null
        }
        guardado = esta
        esta.start()
    }

    fun aplicar(m: Map<String, JsonElement>) {
        completa = m
        marca = m.mapNotNull { (k, v) -> v.texto?.let { k to it } }.toMap()
        programar(ya = true)
    }

    fun deshacer() {
        val m = pasado.removeLastOrNull() ?: return
        futuro.add(actual())
        aplicar(m)
    }

    fun rehacer() {
        val m = futuro.removeLastOrNull() ?: return
        pasado.add(actual())
        aplicar(m)
    }

    fun conWeb(f: (MutableMap<String, JsonElement>) -> Unit) {
        recordar(forzar = true)
        val w = webDe().toMutableMap()
        f(w)
        completa = completa + ("web" to JsonObject(w))
        programar()
    }

    fun conPortada(f: (MutableMap<String, JsonElement>) -> Unit) {
        conWeb { w ->
            val p = w["portada"].objeto?.toMutableMap() ?: mutableMapOf()
            f(p)
            w["portada"] = JsonObject(p)
        }
    }

    fun ocultar(i: Int, oculta: Boolean) {
        val l = secciones.toMutableList()
        val o = l[i].objeto?.toMutableMap() ?: return
        if (oculta) o["oculta"] = jBool(true) else o.remove("oculta")
        l[i] = JsonObject(o)
        conWeb { it["inicio"] = jLista(l) }
    }

    fun mover(desde: Int, hacia: Int) {
        val l = secciones.toMutableList()
        if (desde !in l.indices || hacia !in l.indices) return
        l.add(hacia, l.removeAt(desde))
        // La portada siempre primera.
        val i = l.indexOfFirst { it["tipo"].texto == "portada" }
        if (i > 0) l.add(0, l.removeAt(i))
        Haptico.impacto()
        conWeb { it["inicio"] = jLista(l) }
    }

    fun variante(v: String) {
        conWeb { w ->
            val p = w["portada"].objeto?.toMutableMap() ?: mutableMapOf()
            p["variante"] = jTexto(v)
            w["portada"] = JsonObject(p)
        }
    }

    fun ocultarPagina(i: Int, oculta: Boolean) {
        val l = paginas.toMutableList()
        val o = l[i].objeto?.toMutableMap() ?: return
        if (oculta) o["oculta"] = jBool(true) else o.remove("oculta")
        l[i] = JsonObject(o)
        conWeb { it["paginas"] = jLista(l) }
    }

    fun quitarPagina(i: Int) {
        val l = paginas.toMutableList()
        val antes = l.toList()
        l.removeAt(i)
        conWeb { it["paginas"] = jLista(l) }
        estado.avisar("Página quitada", deshacer = { conWeb { it["paginas"] = jLista(antes) } })
    }

    fun sumarPagina(titulo: String) {
        EditorWeb.problemaPagina(paginas, titulo)?.let { return estado.avisar(it, ok = false) }
        val ruta = EditorWeb.rutaDe(titulo)
        val l = paginas.toMutableList()
        l.add(jObjeto(mapOf("id" to jTexto("p-$ruta"), "titulo" to jTexto(titulo), "ruta" to jTexto(ruta), "texto" to jTexto(""))))
        conWeb { it["paginas"] = jLista(l) }
    }

    fun sumarSeccion(tipo: String, despuesDe: Int? = null) {
        val l = secciones.toMutableList()
        if (tipo !in EditorWeb.tiposParaSumar(l)) {
            return estado.avisar(if (l.size >= EditorWeb.maxSecciones) "Hasta ${EditorWeb.maxSecciones} secciones." else "Esa sección ya está.", ok = false)
        }
        val o = mutableMapOf("id" to jTexto(EditorWeb.idNuevo(l, tipo)), "tipo" to jTexto(tipo))
        if (tipo == "texto") { o["titulo"] = jTexto("Un título"); o["texto"] = jTexto("") }
        if (tipo == "testimonios") o["items"] = jLista(emptyList())
        l.add(despuesDe?.let { minOf(l.size, it + 1) } ?: l.size, JsonObject(o))
        conWeb { it["inicio"] = jLista(l) }
        estado.avisar("Sumaste «${MiWeb.nombreSeccion(tipo)}»")
    }

    fun quitarSeccion(i: Int) {
        val l = secciones.toMutableList()
        if (i !in l.indices || l[i]["tipo"].texto == "portada") return
        val antes = l.toList()
        val nombre = MiWeb.nombreSeccion(l[i]["tipo"].texto)
        l.removeAt(i)
        conWeb { it["inicio"] = jLista(l) }
        estado.avisar("Quitaste «$nombre»", deshacer = { conWeb { it["inicio"] = jLista(antes) } })
    }

    fun duplicarSeccion(i: Int) {
        val l = secciones.toMutableList()
        if (i !in l.indices || l[i]["tipo"].texto != "texto" || l.size >= EditorWeb.maxSecciones) return
        val o = l[i].objeto?.toMutableMap() ?: return
        o["id"] = jTexto(EditorWeb.idNuevo(l, "texto"))
        l.add(i + 1, JsonObject(o))
        conWeb { it["inicio"] = jLista(l) }
        estado.avisar("Duplicaste la sección de texto")
    }

    suspend fun cargarDominio() { dominio = estado.api.opcional<JsonElement>("/v1/inmobiliaria/dominio") }

    @Serializable data class CuerpoDominio(val dominio: String)

    suspend fun ponerDominio(d: String) {
        errorDominio = null
        try {
            estado.api.pedirConCampos("PATCH", "/v1/inmobiliaria", CuerpoDominio(d.trim().lowercase()))
            dominioNuevo = ""
            cargarDominio()
        } catch (e: CancellationException) { throw e } catch (e: ErrorCampos) {
            // Con errores por campo (ErrorConCampos de iOS), el del dominio; un 409 o 400, su detalle.
            when {
                e.campos.isNotEmpty() -> errorDominio = e.campos["dominio"] ?: e.api.detalle
                e.api.estado == 409 || e.api.estado == 400 -> errorDominio = e.api.detalle
                else -> estado.avisar(e.api)
            }
        } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun verificar() {
        verificando = true
        try {
            estado.api.hacer("POST", "/v1/inmobiliaria/dominio/verificar")
            cargarDominio()
            estado.avisar("¡Listo! Tu web ya responde en tu dominio.")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { verificando = false }
    }

    suspend fun quitarDominio(antes: String) {
        ponerDominio("")
        if (dominio["dominio"].texto == null) {
            estado.avisar("Quitaste el dominio propio", deshacer = { alcance.launch { ponerDominio(antes) } })
        }
    }

    @Serializable data class CuerpoBarrio(val texto: String)

    suspend fun guardarBarrio(b: BarrioWeb) {
        try {
            estado.api.hacer("PUT", "/v1/web/barrios/${b.nombre}", CuerpoBarrio(b.texto ?: ""))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Lo tocado en la vista previa: su editor (o, una sección, la de «Página de inicio»). */
    fun tocar(z: String) {
        if (z.startsWith("seccion:")) {
            val id = z.drop(8)
            val i = secciones.indexOfFirst { it["id"].texto == id }
            if (i < 0) return
            when (secciones[i]["tipo"].texto) {
                "texto", "testimonios" -> editandoSeccion = i
                "portada" -> zona = ZonaWeb("portada")
                else -> estado.avisar("Esa sección se ordena o se apaga en «Página de inicio», más abajo.")
            }
        } else {
            zona = ZonaWeb(z)
        }
    }

    suspend fun publicar() {
        publicando = true
        try {
            guardado?.join()
            BorradorWeb.publicar(estado.api)
            hayBorrador = false
            en = null
            Haptico.exito()
            estado.avisar("Cambios publicados en tu web")
            estado.cargarEstructura()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { publicando = false }
    }

    suspend fun descartar() {
        guardado?.cancel()
        guardado = null
        val antes = actual()
        try {
            BorradorWeb.descartar(estado.api)
            en = null
            completa = emptyMap()
            traerBorrador()
            estado.avisar("Volviste a la web publicada", deshacer = { aplicar(antes) })
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    @Serializable data class CuerpoIA(val campo: String, val texto: String)
    @Serializable data class RespuestaIA(val texto: String)

    @Composable
    fun botonIA(k: String) {
        FilaLista({
            alcance.launch {
                reescribiendo = k
                try {
                    val antes = marca[k] ?: ""
                    val r: RespuestaIA = estado.api.enviar("POST", "/v1/web/reescribir", CuerpoIA(k, antes))
                    marca = marca + (k to r.texto)
                    programar()
                    estado.avisar("Reescrito", deshacer = { marca = marca + (k to antes); programar() })
                } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { reescribiendo = null }
            }
        }, habilitado = puede && reescribiendo == null && (marca[k] ?: "").isNotEmpty()) {
            ConIcono(if (reescribiendo == k) "Reescribiendo…" else "Reescribir con IA", "sparkles", color = C.avisoTinta)
        }
    }

    @Composable
    fun campo(k: String, placeholder: String, teclado: KeyboardType = KeyboardType.Text, lineas: Int = 1, lineasMaximas: Int = lineas) {
        FilaLista {
            Entrada(marca[k] ?: "", { recordar(); marca = marca + (k to it); programar() }, placeholder, Modifier.weight(1f),
                estilo = Estilo.body16, teclado = teclado, lineas = lineas, lineasMaximas = lineasMaximas, habilitado = puede)
        }
    }

    /** El ColorPicker de un color de la marca. */
    @Composable
    fun color(nombre: String, k: String, def: Int) {
        val actualHex = Colores.rgb(marca[k]?.replace("#", ""))?.hexColor ?: def.hexColor
        FilaLista(habilitado = puede) {
            Box(Modifier.weight(1f)) {
                SelectorDeColor(nombre, actualHex) { h ->
                    if (!puede) return@SelectorDeColor
                    recordar()
                    marca = marca + (k to h.lowercase())
                    programar()
                }
            }
        }
    }

    // Lo cambiado desde la computadora (o el alta) se trae, si acá no hay nada esperando guardarse.
    LaunchedEffect(estado.version) { if (guardado == null && !publicando) traerBorrador() }
    LaunchedEffect(Unit) {
        @Serializable data class I(val web: String? = null, val nombre: String? = null, val legales: JsonElement? = null)
        estado.api.opcional<I>("/v1/inmobiliaria")?.let { i -> web = i.web; nombreInmo = i.nombre ?: ""; legales = i.legales }
        informe = estado.api.opcional<JsonElement>("/v1/informe", listOf("dias" to "7"))
        traerBorrador()
        if (estado.esDuenio) previa = try { BorradorWeb.urlPrevia(estado.api, estado.inmobiliaria?.slug ?: "") } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        barrios.clear(); barrios.addAll(estado.api.opcional<List<BarrioWeb>>("/v1/web/barrios") ?: emptyList())
        guardadas = estado.api.opcional<JsonElement>("/v1/web/guardadas", listOf("dias" to "7"))
        cargarDominio()
    }

    PantallaApilada("Mi web", acciones = if (estado.esDuenio) ({
        Box(Modifier.alpha(if (pasado.isEmpty()) 0.4f else 1f)) { BotonBarraIcono("arrow.uturn.backward", "Deshacer") { if (pasado.isNotEmpty()) deshacer() } }
        Box(Modifier.alpha(if (futuro.isEmpty()) 0.4f else 1f)) { BotonBarraIcono("arrow.uturn.forward", "Rehacer") { if (futuro.isNotEmpty()) rehacer() } }
    }) else null) {
        Column(Modifier.fillMaxSize()) {
            ListaAgrupada(Modifier.weight(1f)) {
                // MARK: Arriba
                Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) { MarcoPrevia(previa, recarga) { tocar(it) } }
                SeccionLista("Logo y portada", "El logo va en tu web, en los recibos y en la app.") {
                    Column(Modifier.deshabilitado(!puede), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SubirImagenMarca("Subí tu logo", marca["logoUrl"]) { u, _ -> marca = marca + ("logoUrl" to u); programar(ya = true) }
                        SubirImagenMarca("Subí la foto de portada", marca["portadaUrl"], ancha = true) { u, _ -> marca = marca + ("portadaUrl" to u); programar(ya = true) }
                    }
                }
                if (!estado.esDuenio) {
                    SeccionLista { FilaLista { ConIcono("La web la cambia el dueño o la dueña. Vos la podés ver como queda.", "lock", Estilo.footnote, C.texto2) } }
                }
                web?.let { w ->
                    SeccionLista {
                        FilaLista({ abrirUrl(ctx, w) }) { ConIcono(w.replace("https://", ""), "globe") }
                        Separador()
                        FilaLista({ Afuera.whatsapp(null, texto = "Mirá las propiedades de ${nombreInmo.trim().ifEmpty { "la inmobiliaria" }}: $w") }) {
                            ConIcono("Mandar por WhatsApp", "bubble.left")
                        }
                        Separador()
                        FilaLista({ estado.compartirTexto("Mirá las propiedades de $nombreInmo\n$w") }) { ConIcono("Compartir tu web", "square.and.arrow.up") }
                        Separador()
                        FilaLista({
                            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(nombreInmo, w))
                            estado.avisar("Copiaste el link de tu web")
                        }) { ConIcono("Copiar el link", "doc.on.doc") }
                        Separador()
                        FilaLista({ qr = true }) { ConIcono("El QR de tu web", "qrcode") }
                    }
                }
                informe?.let { i ->
                    SeccionLista("Esta semana") {
                        FilaValor("Visitas a tus propiedades", num(i["totalVistas"]))
                        Separador()
                        val o = i["origenes"].lista?.firstOrNull { it["origen"].texto == "Web propia" }
                        FilaValor("Consultas desde tu web", if (o != null) num(o["cantidad"]) else "0")
                    }
                }

                // MARK: Página
                SeccionLista("Plantilla") {
                    val id = completa["plantilla"].texto ?: "barrio"
                    FilaValor("En uso", EditorWeb.plantillas.firstOrNull { it.id == id }?.nombre ?: id)
                    Separador()
                    FilaLista({ plantillas = true }) { ConIcono("Ver las plantillas", "rectangle.3.group") }
                    Separador()
                    FilaLista({ versiones = true }) { ConIcono("Versiones publicadas", "clock.arrow.circlepath") }
                }
                SeccionLista("Página de inicio", "Arrastrá para ordenar. Lo apagado no se ve en tu web.") {
                    secciones.forEachIndexed { i, x ->
                        if (i > 0) Separador()
                        val tipo = x["tipo"].texto
                        val acciones = buildList {
                            if (tipo == "texto") add(AccionFila("Duplicar", "plus.square.on.square", C.accion) { duplicarSeccion(i) })
                            if (tipo != "portada") add(AccionFila("Quitar", "trash", C.error) { quitarSeccion(i) })
                        }
                        val menu = buildList {
                            if (tipo == "texto") add(OpcionDeMenu("Duplicar", "plus.square.on.square") { duplicarSeccion(i) })
                            add(OpcionDeMenu("Sumar sección acá", "plus.rectangle.on.rectangle") { sumarDespuesDe = i })
                            if (tipo != "portada") add(OpcionDeMenu("Quitar", "trash", destructiva = true) { quitarSeccion(i) })
                        }
                        FilaDeslizable(derecha = if (puede) acciones else emptyList()) {
                            MenuContextual(if (puede) menu else emptyList(), alTocar = {}) {
                                FilaLista(habilitado = puede) {
                                    // El modo edición (.editMode .active + onMove): subir y bajar en vez de arrastrar.
                                    if (tipo != "portada" && puede) {
                                        Column {
                                            Box(Modifier.size(24.dp, 18.dp).clickable(enabled = i > 1) { mover(i, i - 1) }
                                                .semantics { contentDescription = "Subir" }.alpha(if (i > 1) 1f else 0.3f), contentAlignment = Alignment.Center) { Icono("chevron.up", 12f, C.texto3) }
                                            Box(Modifier.size(24.dp, 18.dp).clickable(enabled = i < secciones.size - 1) { mover(i, i + 1) }
                                                .semantics { contentDescription = "Bajar" }.alpha(if (i < secciones.size - 1) 1f else 0.3f), contentAlignment = Alignment.Center) { Icono("chevron.down", 12f, C.texto3) }
                                        }
                                    } else Box(Modifier.width(24.dp))
                                    Texto(MiWeb.nombreSeccion(tipo), Estilo.body16, C.texto, Modifier.weight(1f))
                                    Llave(x["oculta"].bool != true, { on -> ocultar(i, !on) }, color = C.accion, habilitado = puede && tipo != "portada")
                                }
                            }
                        }
                    }
                    Separador()
                    FilaInterruptor("Buscador en la portada (Comprar · Alquilar · Temporada)", portada["buscador"].bool != false, puede) { v -> conPortada { it["buscador"] = jBool(v) } }
                    Separador()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).deshabilitado(!puede), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val v = veloActual
                        val noche = EditorWeb.noche(marca["colorPrimario"])
                        val variante = portada["variante"].texto ?: "foto"
                        val seLee = if (variante == "foto") EditorWeb.seLeeConVelo(noche, v)
                            else Colores.contraste(Colores.rgb(marca["colorPrimario"]) ?: 0x16443B, Colores.rgb(marca["colorFondo"]) ?: 0xFAF8F3) >= 4.5
                        Texto("Velo sobre la foto · ${v.toInt()} %", Estilo.subhead, C.texto)
                        Deslizador(v.toFloat(), { n -> conPortada { it["velo"] = jNumero(((n / 5).roundToInt() * 5).toDouble()) } }, 0f..90f, 5f)
                        if (seLee) {
                            ConIcono("El título se lee bien (contraste AA)", "checkmark.circle", Estilo.footnote, C.okTinta)
                        } else if (variante == "foto") {
                            val m = EditorWeb.veloMinimo(noche)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Texto("El título cuesta leerse.", Estilo.footnote, C.avisoTinta)
                                Texto("Subir el velo a $m %", Estilo.footnoteFuerte, C.accion, Modifier.clickable { conPortada { it["velo"] = jNumero(m.toDouble()) } })
                            }
                        } else {
                            Texto("El título puede costar leerse: probá un color más oscuro", Estilo.footnote, C.avisoTinta)
                        }
                    }
                    Separador()
                    FilaLista({ fotoPortada = true }, habilitado = puede) { ConIcono("Usar la foto de una propiedad", "photo.on.rectangle") }
                    Separador()
                    FilaLista(habilitado = puede) {
                        Texto("Portada", Estilo.body16, C.texto, Modifier.weight(1f))
                        SelectorMenu(listOf("foto" to "Foto de fondo", "arriba" to "Foto arriba", "partida" to "Partida"),
                            webDe()["portada"]["variante"].texto ?: "foto", { if (puede) variante(it) })
                    }
                }
                SeccionLista("Sumar a la página de inicio") {
                    val tipos = EditorWeb.tiposParaSumar(secciones)
                    if (tipos.isEmpty() || !puede) {
                        FilaLista(habilitado = false) { ConIcono("Sumar sección", "plus.rectangle.on.rectangle") }
                    } else {
                        MenuBoton(tipos.map { t -> OpcionDeMenu(MiWeb.nombreSeccion(t), "") { sumarSeccion(t) } }, "Sumar sección") {
                            FilaLista { ConIcono("Sumar sección", "plus.rectangle.on.rectangle") }
                        }
                    }
                    secciones.withIndex().filter { (it.value["tipo"].texto ?: "") in listOf("texto", "testimonios") }.forEach { (i, x) ->
                        Separador()
                        FilaValor(MiWeb.nombreSeccion(x["tipo"].texto), x["titulo"].texto ?: "Editar") { if (puede) editandoSeccion = i }
                    }
                }
                SeccionLista("Páginas", "Las propias viven en tu web con su link. Lo apagado no sale en el menú.") {
                    paginas.forEachIndexed { i, x ->
                        if (i > 0) Separador()
                        val id = x["id"].texto ?: ""
                        FilaDeslizable(derecha = if (id.startsWith("p-") && puede) listOf(AccionFila("Quitar", "trash", C.error) { quitarPagina(i) }) else emptyList()) {
                            FilaInterruptor(MiWeb.nombrePagina(x), x["oculta"].bool != true, puede && id != "inicio" && id != "propiedades") { on -> ocultarPagina(i, !on) }
                        }
                        if (id.startsWith("p-")) {
                            Separador()
                            FilaLista({ editandoPagina = i }, habilitado = puede) { ConIcono("Editar «${MiWeb.nombrePagina(x)}»", "pencil") }
                        }
                    }
                    Separador()
                    FilaLista({ paginaNueva = true }, habilitado = puede) { ConIcono("Nueva página", "plus") }
                }
                guardadas?.let { g ->
                    SeccionLista("Guardadas esta semana") {
                        FilaValor("Corazones", num(g["favoritos"]))
                        Separador()
                        FilaValor("Búsquedas con alerta", num(g["busquedas"]))
                        val m = g["masGuardada"]
                        val t = m["titulo"].texto
                        if (m != null && t != null) {
                            Separador()
                            FilaValor("La más guardada", t) { m["id"].texto?.let { estado.abrir(AFichaPropiedad(id = it)) } }
                        }
                    }
                }

                // MARK: Datos
                if (estado.esDuenio) {
                    // El dominio propio en tres pasos, con los registros DNS para copiar.
                    val d = dominio["dominio"].texto
                    val verificado = dominio["verificado"].bool == true
                    SeccionLista("Dominio propio",
                        if (d == null) "Hoy tu web está en ${(web ?: "").replace("https://", "")}. Si tenés un dominio (de NIC.ar u otro), la ponemos ahí en tres pasos." else null) {
                        if (d != null) {
                            FilaValor(d, if (verificado) "Conectado" else "Verificando")
                            Separador()
                            FilaLista { ConIcono("1. Lo compraste en NIC.ar u otro registro", "checkmark.circle.fill", color = C.okTinta) }
                            val registros = dominio["registros"].lista ?: emptyList()
                            val principal = registros.firstOrNull()
                            Separador()
                            FilaLista {
                                ConIcono("2. Cargaste ${if (principal["tipo"].texto == "A") "el registro A" else "el CNAME"} ${principal["valor"].texto ?: ""}",
                                    if (verificado) "checkmark.circle.fill" else "circle", color = if (verificado) C.okTinta else C.texto)
                            }
                            if (!verificado) {
                                registros.forEach { r ->
                                    Separador()
                                    FilaLista({
                                        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("DNS", r["valor"].texto ?: ""))
                                        estado.avisar("Copiado")
                                    }) {
                                        val e = Estilo(13f, Peso.regular)
                                        Texto(r["tipo"].texto ?: "", e, C.accion, Modifier.width(56.dp), monoespaciado = true)
                                        Texto(r["nombre"].texto ?: "", e, C.accion, Modifier.width(60.dp), monoespaciado = true)
                                        Texto(r["valor"].texto ?: "", e, C.accion, Modifier.weight(1f), lineas = 1, monoespaciado = true)
                                        Icono("doc.on.doc", 13f, C.accion)
                                    }
                                }
                                Separador()
                                FilaLista { ConIcono("3. Esperando que se propague · suele tardar hasta 2 h", "hourglass", color = C.texto2) }
                                Separador()
                                FilaLista({ alcance.launch { verificar() } }, habilitado = !verificando) {
                                    Texto(if (verificando) "Verificando…" else "Verificar ahora", Estilo.body16, C.accion)
                                }
                            } else {
                                Separador()
                                FilaLista({ abrirUrl(ctx, "https://$d") }) { ConIcono("3. Tu web responde en $d", "checkmark.circle.fill") }
                            }
                            Separador()
                            FilaLista({ alcance.launch { quitarDominio(d) } }) { Texto("Quitar", Estilo.body16, C.error) }
                        } else {
                            FilaLista {
                                Entrada(dominioNuevo, { dominioNuevo = it }, "tuinmobiliaria.com.ar", Modifier.weight(1f), estilo = Estilo.body16,
                                    teclado = KeyboardType.Uri, sinCorrector = true, mayusculas = KeyboardCapitalization.None)
                            }
                            errorDominio?.let { Separador(); FilaLista { MensajeError(it) } }
                            Separador()
                            FilaLista({ alcance.launch { ponerDominio(dominioNuevo) } }, habilitado = dominioNuevo.contains(".")) {
                                Texto("Conectar", Estilo.body16, C.accion)
                            }
                        }
                    }
                }
                SeccionLista("Datos legales del pie") {
                    val l = legales
                    if (l != null && (l["razonSocial"].texto != null || l["cuit"].texto != null || l["matricula"].texto != null)) {
                        l["razonSocial"].texto?.let { FilaValor("Razón social", it); Separador() }
                        l["cuit"].texto?.let { FilaValor("CUIT", it); Separador() }
                        l["matricula"].texto?.let { m -> FilaValor("Matrícula", listOfNotNull(l["colegio"].texto, m).joinToString(" ")); Separador() }
                    } else {
                        FilaLista { Texto("Faltan la razón social, el CUIT y la matrícula. En CABA y Provincia el corredor tiene que exhibirlos.", Estilo.footnote, C.avisoTinta) }
                        Separador()
                    }
                    FilaLista({ estado.abrir(ASeccion(clave = "configuracion")) }, habilitado = puede) { Texto("Editar", Estilo.body16, C.accion) }
                }
                SeccionLista("Marca") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditorWeb.colores.forEach { c ->
                            val elegido = (marca["colorPrimario"] ?: "").lowercase() == c
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Colores.color(Colores.rgb(c) ?: 0))
                                .border(if (elegido) 3.dp else 0.dp, if (elegido) C.oro else Color.Transparent, CircleShape)
                                .clickable(enabled = puede) { recordar(); marca = marca + ("colorPrimario" to c); programar() }
                                .semantics { contentDescription = "Color $c" })
                        }
                    }
                    Separador()
                    color("Color principal", "colorPrimario", 0x16443B)
                    // «Lo oscurecimos un poco…» si el color no llega a contraste AA con el blanco.
                    Colores.rgb(marca["colorPrimario"])?.let { p ->
                        FilaLista {
                            if (Colores.contraste(p, 0xFFFFFF) < 4.5) {
                                ConIcono("Lo oscurecimos un poco para que botones y textos lleguen a contraste AA", "slider.horizontal.3", Estilo.footnote, C.avisoTinta)
                            } else {
                                ConIcono("Contraste AA en botones y textos", "checkmark.circle", Estilo.footnote, C.okTinta)
                            }
                        }
                    }
                    Separador()
                    color("Color de acento", "colorSecundario", 0xF2BB57)
                    Separador()
                    color("Fondo", "colorFondo", 0xFAF8F3)
                    Colores.rgb(marca["colorFondo"])?.let { f ->
                        if (Colores.contraste(f, 0x18201D) < 7) {
                            FilaLista { ConIcono("El fondo tiene que ser claro para que el texto se lea: usamos el papel", "slider.horizontal.3", Estilo.footnote, C.avisoTinta) }
                        }
                    }
                    Separador()
                    campo("frase", "Frase de la portada")
                    Separador()
                    botonIA("frase")
                }
                SeccionLista("Nosotros") {
                    campo("nosotrosTitulo", "Título")
                    Separador()
                    campo("nosotros", "Quiénes son, desde cuándo, en qué barrios trabajan.", lineas = 3, lineasMaximas = 8)
                    Separador()
                    botonIA("nosotros")
                }
                SeccionLista("Contacto") {
                    campo("whatsapp", "WhatsApp", KeyboardType.Phone); Separador()
                    campo("telefono", "Teléfono", KeyboardType.Phone); Separador()
                    campo("email", "Mail", KeyboardType.Email); Separador()
                    campo("direccion", "Dirección"); Separador()
                    campo("horario", "Horario")
                }
                if (barrios.isNotEmpty()) {
                    SeccionLista("Barrios", "Lo que contás de cada uno sale en su página de tu web.") {
                        barrios.forEachIndexed { i, b ->
                            if (i > 0) Separador()
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Texto(b.nombre, Estilo.filaLista, C.texto)
                                Entrada(b.texto ?: "", { t -> barrios[i] = b.copy(texto = t.take(600)) }, "Cómo es vivir en ${b.nombre}: plazas, transporte, colegios.",
                                    estilo = Estilo.body16, lineasMaximas = 8, habilitado = puede,
                                    alEnfocar = { f ->
                                        if (f) barrioFoco = b.nombre
                                        else if (barrioFoco == b.nombre) {
                                            barrioFoco = null
                                            barrios.firstOrNull { it.nombre == b.nombre }?.let { alcance.launch { guardarBarrio(it) } }
                                        }
                                    },
                                    alEnviar = { barrios.firstOrNull { it.nombre == b.nombre }?.let { alcance.launch { guardarBarrio(it) } } })
                            }
                        }
                    }
                }
            }
            if (estado.esDuenio) {
                // «Publicar cambios» y «Descartar», siempre a mano mientras haya cambios sin publicar.
                Row(
                    Modifier.fillMaxWidth().background(C.elevado.copy(alpha = 0.94f)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Texto(if (hayBorrador) "Cambios sin publicar" else "Igual a la publicada", Estilo.subheadFuerte, C.texto)
                        Texto(if (hayBorrador) "Se guardan solos; salen con «Publicar»" else "Lo que cambies se guarda aparte", Estilo.footnote, C.texto2)
                    }
                    if (hayBorrador) {
                        Texto("Descartar", Estilo.subheadFuerte, C.texto2, Modifier.alpha(if (publicando) 0.4f else 1f).clickable(enabled = !publicando) { alcance.launch { descartar() } })
                        BotonPrincipal(if (publicando) "Publicando…" else "Publicar", { alcance.launch { publicar() } }, alto = 44.dp, lleno = false, habilitado = !publicando)
                    }
                }
            }
        }
    }

    // MARK: Hojas
    Hoja(versiones, { versiones = false }) { HojaVersionesWeb({ completa = emptyMap(); alcance.launch { traerBorrador() } }) { versiones = false } }
    Hoja(plantillas, { plantillas = false }) { HojaPlantillas({ completa = emptyMap(); alcance.launch { traerBorrador() } }) { plantillas = false } }
    Hoja(qr && web != null, { qr = false }, listOf(Altura.media)) { web?.let { HojaQRWeb(it, nombreInmo) } }
    Hoja(fotoPortada, { fotoPortada = false }) {
        ElegirFotoPortada({ u -> recordar(); marca = marca + ("portadaUrl" to u); programar(ya = true) }) { fotoPortada = false }
    }
    Hoja(editandoPagina != null, { editandoPagina = null }) {
        val i = editandoPagina ?: 0
        EditorPaginaWeb(paginas.getOrNull(i) ?: JsonObject(emptyMap()), web, { t, texto ->
            val l = paginas.toMutableList()
            val o = l.getOrNull(i).objeto?.toMutableMap() ?: return@EditorPaginaWeb
            o["titulo"] = jTexto(t)
            o["texto"] = jTexto(texto)
            l[i] = JsonObject(o)
            conWeb { it["paginas"] = jLista(l) }
        }) { editandoPagina = null }
    }
    Hoja(zona != null, { zona = null }, listOf(Altura.media, Altura.grande)) {
        zona?.let { z -> EditorZonaWeb(z.clave, marca, { marca = it }, { programar(ya = true) }) { zona = null } }
    }
    Hoja(paginaNueva, { paginaNueva = false }) { NuevaPaginaWeb(paginas, web, { t -> sumarPagina(t) }) { paginaNueva = false } }
    Hoja(editandoSeccion != null, { editandoSeccion = null }) {
        val i = editandoSeccion ?: 0
        secciones.getOrNull(i)?.let { s ->
            EditorSeccionWeb(s, { nueva ->
                val l = secciones.toMutableList()
                l[i] = nueva
                conWeb { it["inicio"] = jLista(l) }
            }) { editandoSeccion = null }
        }
    }
    // El submenú «Sumar sección acá» del menú de una sección.
    ar.com.corredor.app.tema.Confirmacion(
        visible = sumarDespuesDe != null, titulo = "Sumar sección acá",
        opciones = EditorWeb.tiposParaSumar(secciones).map { t -> OpcionConfirmacion(MiWeb.nombreSeccion(t)) { sumarDespuesDe?.let { sumarSeccion(t, despuesDe = it) } } },
        alCerrar = { sumarDespuesDe = null },
    )
}

/** Editar una sección de texto o de testimonios del inicio de la web (con «Reescribir con IA»). */
@Composable
fun EditorSeccionWeb(seccion: JsonElement, guardar: (JsonElement) -> Unit, cerrar: () -> Unit) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var titulo by remember(seccion) { mutableStateOf(seccion["titulo"].texto ?: "") }
    var texto by remember(seccion) { mutableStateOf(seccion["texto"].texto ?: "") }
    val items = remember(seccion) {
        mutableStateListOf<Pair<String, String>>().apply {
            seccion["items"].lista?.let { l -> addAll(l.map { (it["texto"].texto ?: "") to (it["autor"].texto ?: "") }) }
        }
    }
    val testimonios = seccion["tipo"].texto == "testimonios"

    @Serializable data class CuerpoReescribir(val campo: String = "texto", val texto: String)
    @Serializable data class RespuestaReescribir(val texto: String)

    Column(Modifier.fillMaxSize()) {
        BarraHoja("", izquierda = { BotonHoja("Cancelar") { cerrar() } }, derecha = {
            BotonHoja("Listo", fuerte = true) {
                val o = seccion.objeto?.toMutableMap()
                if (o == null) { cerrar(); return@BotonHoja }
                if (titulo.isEmpty()) o.remove("titulo") else o["titulo"] = jTexto(titulo)
                if (testimonios) {
                    o["items"] = jLista(items.filter { it.first.isNotEmpty() }.map { jObjeto(mapOf("texto" to jTexto(it.first), "autor" to jTexto(it.second))) })
                } else o["texto"] = jTexto(texto)
                guardar(JsonObject(o))
                cerrar()
            }
        })
        ListaAgrupada {
            SeccionLista {
                FilaLista { Entrada(titulo, { titulo = it }, "Título", Modifier.weight(1f), estilo = Estilo.body16) }
            }
            if (testimonios) {
                SeccionLista {
                    items.forEachIndexed { i, it ->
                        if (i > 0) Separador()
                        FilaDeslizable(derecha = listOf(AccionFila("Eliminar", "trash", C.error) { items.removeAt(i) })) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Entrada(it.first, { t -> items[i] = t to items[i].second }, "Lo que dijo", estilo = Estilo.body16, lineasMaximas = 6)
                                Entrada(it.second, { t -> items[i] = items[i].first to t }, "Quién", estilo = Estilo.body16)
                            }
                        }
                    }
                    if (items.isNotEmpty()) Separador()
                    FilaLista({ items.add("" to "") }, habilitado = items.size < 6) { ConIcono("Sumar testimonio", "plus") }
                    if (items.size >= 6) { Separador(); FilaLista { Texto("Hasta 6 testimonios.", Estilo.footnote, C.texto2) } }
                }
            } else {
                SeccionLista {
                    FilaLista { Entrada(texto, { texto = it }, "Texto", Modifier.weight(1f), estilo = Estilo.body16, lineas = 4, lineasMaximas = 14) }
                    Separador()
                    FilaLista({
                        alcance.launch {
                            try {
                                val r: RespuestaReescribir = estado.api.enviar("POST", "/v1/web/reescribir", CuerpoReescribir(texto = texto))
                                texto = r.texto
                            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                        }
                    }, habilitado = texto.isNotEmpty()) { ConIcono("Reescribir con IA", "sparkles") }
                }
            }
        }
    }
}
