package ar.com.corredor.app.pantallas.inicio

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mensajes.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

// Clon de Pantallas/Inicio/Cuenta.swift.

// MARK: - 12A Avisos

object Avisos {
    /** La acción que pide un texto antes de mandarse (responder desde el aviso). */
    data class Escribir(val aviso: AvisoCentro, val accion: AvisoCentro.Accion) {
        val id: String get() = aviso.id + (accion.accion ?: "")
    }

    /** Lo que abre el push (`?aviso=<id>&responder=<acción>` en la web): el aviso arriba y, si pide
     *  texto, la hoja ya abierta. app/Push.kt lo deja con `Avisos.abrir(...)`. (id, responder) */
    var foco: Pair<String, String?>? = null

    /** Para el push: abre los avisos con ese aviso arriba (y la hoja de respuesta si `responder`). */
    fun abrir(estado: Estado, aviso: String, responder: String?) {
        foco = aviso to responder
        estado.ir(Ruta(Pestana.inicio, AAvisos()))
    }

    val tipos: List<Pair<String, String>> = listOf(
        "consulta" to "Consultas", "whatsapp" to "WhatsApp", "mercadolibre" to "Mercado Libre", "visita" to "Visitas",
        "cobro" to "Cobros", "resumen" to "Resumen", "operacion" to "Operaciones",
    )

    /** Ícono y tono de cada tipo (cobro rojo, Mercado Libre y visitas oro, la web azul, el resto verde). */
    fun icono(tipo: String?): Pair<String, Tono> = when (tipo) {
        "whatsapp" -> "bubble.left" to Tono.verde
        "mercadolibre" -> "storefront" to Tono.oro
        "consulta", "tasacion" -> "globe" to Tono.azul
        "visita" -> "calendar" to Tono.oro
        "cobro" -> "dollarsign.circle" to Tono.rojo
        "resumen" -> "sun.max" to Tono.neutro
        "operacion" -> "rectangle.split.3x1" to Tono.verde
        "mira" -> "viewfinder" to Tono.oro
        else -> "bell" to Tono.neutro
    }
}

/** 12A Avisos (con el segmentado «Avisos · Configurar»), como el centro de avisos del panel: «Para
 *  hacer» o «Todo», por tipo, de a 30 con «Ver más»; cada aviso con quién lo tiene que resolver y sus
 *  acciones, y las que piden texto con la hoja para escribir. */
@Composable
fun Avisos() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var seg by remember { mutableIntStateOf(0) }
    var vista by remember { mutableStateOf("para_hacer") }
    var tipo by remember { mutableStateOf<String?>(null) }
    var avisos by remember { mutableStateOf<List<AvisoCentro>?>(null) }
    var enfocado by remember { mutableStateOf<AvisoCentro?>(null) }
    var siguiente by remember { mutableStateOf<String?>(null) }
    var noLeidos by remember { mutableIntStateOf(0) }
    var paraHacer by remember { mutableIntStateOf(0) }
    var cargandoMas by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    var escribiendo by remember { mutableStateOf<Avisos.Escribir?>(null) }

    val visibles: List<AvisoCentro> = run {
        val l = listOfNotNull(enfocado) + (avisos ?: emptyList()).filter { it.id != enfocado?.id }
        if (vista == "para_hacer") l.filter { !it.estaHecho || it.id == enfocado?.id } else l
    }

    fun marcarLocal(ids: List<String>, f: (AvisoCentro) -> AvisoCentro) {
        val l = avisos ?: return
        avisos = l.map { if (it.id in ids) f(it) else it }
        val e = enfocado
        if (e != null && e.id in ids) enfocado = f(e)
    }

    suspend fun cargar() {
        try {
            val q = mutableListOf("limite" to "30", "vista" to vista)
            tipo?.let { q.add("tipo" to it) }
            val l: PaginaAvisos = estado.api.get("/v1/avisos", q)
            avisos = l.avisos
            siguiente = l.siguiente
            noLeidos = l.noLeidos ?: 0
            paraHacer = l.paraHacer ?: 0
            estado.avisosPendientes = noLeidos
            error = null
            val f = Avisos.foco
            if (f != null) {
                Avisos.foco = null
                var a: AvisoCentro? = l.avisos.firstOrNull { it.id == f.first }
                if (a == null) a = estado.api.opcional<AvisoCentro>("/v1/avisos/${f.first}")
                if (a != null) {
                    if (l.avisos.none { it.id == a.id }) enfocado = a
                    val r = f.second
                    val x = a.acciones?.firstOrNull { it.accion == r }
                    if (r != null && !a.estaHecho && x != null) escribiendo = Avisos.Escribir(a, x)
                }
            }
        } catch (e: ErrorApi) {
            if (avisos == null) error = e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun verMas() {
        val s = siguiente ?: return
        cargandoMas = true
        val q = mutableListOf("limite" to "30", "vista" to vista, "antes" to s)
        tipo?.let { q.add("tipo" to it) }
        try {
            val l: PaginaAvisos = estado.api.get("/v1/avisos", q)
            avisos = (avisos ?: emptyList()) + l.avisos
            siguiente = l.siguiente
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisarReintentar("No pudimos traer más") { alcance.launch { verMas() } }
        } finally {
            cargandoMas = false
        }
    }

    suspend fun leido(a: AvisoCentro) {
        try {
            estado.api.hacer("POST", "/v1/avisos/${a.id}/leido")
            marcarLocal(listOf(a.id)) { it.copy(leido = true, leidoEn = Instant.now()) }
            noLeidos = maxOf(0, noLeidos - 1)
            estado.avisosPendientes = noLeidos
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    suspend fun marcarNoLeido(ids: List<String>) {
        for (id in ids) {
            try {
                estado.api.hacer("DELETE", "/v1/avisos/$id/leido")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estado.avisar(e); return
            }
        }
    }

    /** «Marcar todo leído» (sólo el tipo elegido, si hay uno), con Deshacer. */
    suspend fun todoLeido() {
        val antes = (avisos ?: emptyList()).filter { !it.estaLeido }.map { it.id }
        @Serializable data class Cuerpo(val tipos: List<String>)
        marcarLocal(antes) { it.copy(leido = true, leidoEn = Instant.now()) }
        try {
            estado.api.hacer("POST", "/v1/avisos/leidos", Cuerpo(tipos = tipo?.let { listOf(it) } ?: emptyList()))
            estado.avisar(if (antes.size == 1) "Marcaste 1 aviso como leído" else "Marcaste ${antes.size} avisos como leídos") {
                alcance.launch {
                    marcarNoLeido(antes)
                    estado.version += 1
                }
            }
            cargar()
            Insignias.compartidas.refrescar(estado)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            marcarLocal(antes) { it.copy(leido = false, leidoEn = null) }
            estado.avisarReintentar(Estado.texto(e, si = "No se guardó")) { alcance.launch { todoLeido() } }
        }
    }

    /** Una acción del aviso, como la hace el panel (avisos/[id]/accion): con ruta se llama a la API con
     *  su método y su cuerpo (o {"texto": …} si pide escribir); sin ruta, se abre su link. Si sale bien
     *  queda hecho (si pedía algo y no estaba hecho) o leído. Devuelve el error para la hoja. */
    suspend fun hacer(a: AvisoCentro, x: AvisoCentro.Accion, texto: String? = null): String? {
        val r = x.ruta?.nonEmpty
        if (r == null) {
            (x.url ?: a.url)?.let { u -> Ruta.de(u)?.let { estado.ir(it) } }
            return null
        }
        if (x.entrada == "texto" && texto == null) {
            escribiendo = Avisos.Escribir(a, x)
            return null
        }
        val cuerpo: String = when {
            x.entrada == "texto" -> JsonObject(mapOf("texto" to JsonPrimitive(texto ?: ""))).toString()
            x.cuerpo != null -> Api.json.encodeToString(x.cuerpo).let { if (it == "null") "{}" else it }
            else -> "{}"
        }
        val metodo = (x.metodo ?: "POST").uppercase()
        if (metodo !in listOf("POST", "PUT", "PATCH", "DELETE") || !r.startsWith("/v1/")) {
            estado.avisar("Acción desconocida.", ok = false); return null
        }
        ocupado = a.id + (x.accion ?: "")
        try {
            estado.api.pedir(metodo, r, cuerpo = cuerpo.toByteArray())
            if (a.accionable == true && !a.estaHecho) runCatching { estado.api.hacer("POST", "/v1/avisos/${a.id}/hecho") }
            else runCatching { estado.api.hacer("POST", "/v1/avisos/${a.id}/leido") }
            marcarLocal(listOf(a.id)) { it.copy(hecho = true, hechoEn = Instant.now(), leido = true, leidoEn = Instant.now()) }
            escribiendo = null
            val ruta = a.url?.let { Ruta.de(it) }
            if (ruta != null) {
                estado.sugerir("${x.titulo ?: "Listo"} · listo", "Ver") { estado.ir(ruta) }
            } else {
                estado.avisar("${x.titulo ?: "Listo"} · listo")
            }
            Insignias.compartidas.refrescar(estado)
            return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val t = Estado.texto(e, si = "No salió. Probá de nuevo.")
            if (escribiendo != null) return t
            estado.avisarReintentar(t) { alcance.launch { hacer(a, x, texto) } }
            return t
        } finally {
            ocupado = null
        }
    }

    fun abrir(a: AvisoCentro) {
        if (!a.estaLeido) alcance.launch { leido(a) }
        a.url?.let { u -> Ruta.de(u)?.let { estado.ir(it) } }
    }

    /** El título con lo que nombra (persona o propiedad) en negrita. */
    fun titulo(a: AvisoCentro): AnnotatedString {
        val t = a.titulo ?: ""
        val nombres = listOf("contactoNombre", "nombre", "propiedad", "direccion").mapNotNull { a.datos?.get(it)?.texto } + listOfNotNull(a.actorNombre)
        var base = AnnotatedString(t)
        for (n in nombres.filter { it.length > 2 }.sortedByDescending { it.length }) {
            val i = t.indexOf(n)
            if (i >= 0) {
                base = buildAnnotatedString {
                    append(t.substring(0, i))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(n) }
                    append(t.substring(i + n.length))
                }
                break
            }
        }
        val c = a.cuerpo?.nonEmpty
        if (c != null && a.tipo != "operacion") base = base + AnnotatedString((if (a.tipo == "mercadolibre") ": " else " · ") + c)
        return base
    }

    /** «Sofía no respondió · 2 h» o «sin asignar» (sólo lo que pide responder y está sin hacer). */
    fun quien(a: AvisoCentro): Pair<String, Boolean>? {
        if (!(a.accionable == true && !a.estaHecho && (a.tipo ?: "") in listOf("mercadolibre", "whatsapp", "consulta"))) return null
        val r = a.responsableNombre?.nonEmpty ?: return "sin asignar" to false
        val hace = a.creadoEn?.let { Formato.hace(it).replace("hace ", "") } ?: ""
        return "${Charla.primerNombre(r)} ${if (a.tipo == "mercadolibre") "no la respondió" else "no respondió"} · $hace" to true
    }

    fun cuando(a: AvisoCentro): String {
        val f = a.creadoEn ?: return ""
        if (esHoyEnBA(f)) return Formato.horaDe(f)
        if (esAyerEnBA(f)) return "ayer"
        return Formato.hace(f)
    }

    fun grupos(l: List<AvisoCentro>): List<Pair<String, List<AvisoCentro>>> {
        val hoy = mutableListOf<AvisoCentro>()
        val ayer = mutableListOf<AvisoCentro>()
        val antes = mutableListOf<AvisoCentro>()
        for (a in l) {
            val f = a.creadoEn ?: Instant.now()
            if (esHoyEnBA(f)) hoy.add(a) else if (esAyerEnBA(f)) ayer.add(a) else antes.add(a)
        }
        return listOf("Hoy" to hoy.toList(), "Ayer" to ayer.toList(), "Antes" to antes.toList()).filter { it.second.isNotEmpty() }
    }

    LaunchedEffect(estado.version, vista, tipo) { cargar() }

    @Composable
    fun BotonAccionAviso(a: AvisoCentro, x: AvisoCentro.Accion, primera: Boolean) {
        Tocable(alTocar = { alcance.launch { hacer(a, x) } }, habilitado = ocupado == null) {
            Row(
                Modifier.height(34.dp).clip(CircleShape).background(if (primera) C.accion else C.superficie)
                    .border(1.dp, if (primera) Color.Transparent else C.linea2, CircleShape).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ocupado == a.id + (x.accion ?: "")) Girando(if (primera) C.sobreAccion else C.accion, 12.dp)
                Texto(x.titulo ?: "Ver", Estilo(14f, Peso.semibold), if (primera) C.sobreAccion else C.accion)
            }
        }
    }

    @Composable
    fun Fila(a: AvisoCentro, primera: Boolean) {
        val (icono, tono) = Avisos.icono(a.tipo)
        val pendiente = a.accionable == true && !a.estaHecho
        val acciones = if (pendiente) (a.acciones ?: emptyList()) else emptyList()
        val conCara = a.tipo == "operacion" && a.actorNombre != null
        val menu = buildList {
            if (a.estaLeido) {
                add(OpcionDeMenu("Marcar como no leído", "envelope.badge") { alcance.launch { marcarNoLeido(listOf(a.id)); cargar() } })
            } else {
                add(OpcionDeMenu("Marcar como leído", "envelope.open") { alcance.launch { leido(a) } })
            }
            a.url?.let { u -> Ruta.de(u)?.let { r -> add(OpcionDeMenu("Abrir", "arrow.up.right") { estado.ir(r) }) } }
        }
        MenuContextual(opciones = menu, alTocar = { abrir(a) }) {
            Row(
                Modifier.fillMaxWidth().lineaArriba(!primera).background(if (a.estaLeido) Color.Transparent else C.okFondo.copy(alpha = C.okFondo.alpha * 0.35f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
            ) {
                if (conCara) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(C.okFondo), contentAlignment = Alignment.Center) {
                        Texto(Formato.iniciales(a.actorNombre ?: ""), Estilo(13f, Peso.bold), C.okTinta)
                    }
                } else {
                    IconoTono(icono = icono, tono = tono, tam = 38.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        TextoRico(titulo(a), Estilo(15f, if (a.estaLeido) Peso.regular else Peso.semibold), if (!pendiente && a.tipo == "operacion") C.texto2 else C.texto)
                        quien(a)?.let { q -> Texto(q.first, Estilo(12f, Peso.semibold), if (q.second) C.avisoTinta else C.texto3) }
                        if (a.estaHecho) ConIcono("Hecho", "checkmark", Estilo(12f, Peso.semibold), C.okTinta)
                    }
                    if (acciones.isNotEmpty() && estado.escribe) {
                        FlowLayout(espacio = 6.dp, modifier = Modifier.padding(top = 4.dp)) {
                            acciones.forEachIndexed { i, x -> BotonAccionAviso(a, x, primera = i == 0) }
                        }
                    }
                }
                Texto(cuando(a), Estilo(12f, Peso.regular), C.texto3)
            }
        }
    }

    PantallaApilada(
        titulo = "Avisos",
        acciones = {
            if (seg == 0 && noLeidos > 0) {
                Texto("Marcar todo leído", Estilo.body, C.accion, Modifier.clickable { alcance.launch { todoLeido() } })
            }
        },
    ) {
        Refrescable(alRefrescar = { cargar() }) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Segmentado(opciones = listOf(0 to "Avisos", 1 to "Configurar"), elegido = seg, alElegir = { seg = it })
                if (seg == 1) {
                    ConfigAvisos.Contenido()
                } else {
                    // Filtros
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipFiltro(texto = "Para hacer" + (if (paraHacer > 0) " $paraHacer" else ""), activo = vista == "para_hacer", hacer = { vista = "para_hacer" })
                            ChipFiltro(texto = "Todo", activo = vista == "todo", hacer = { vista = "todo" })
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Avisos.tipos.forEach { (t, n) ->
                                key(t) {
                                    val (icono, _) = Avisos.icono(t)
                                    val elegido = tipo == t
                                    Tocable(alTocar = { Haptico.seleccion(); tipo = if (tipo == t) null else t }, modifier = Modifier.semantics { selected = elegido }) {
                                        Row(
                                            Modifier.height(30.dp).clip(CircleShape).background(if (elegido) C.accion else C.hundida).padding(horizontal = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icono(icono, 13f, if (elegido) C.sobreAccion else C.texto2)
                                            Texto(n, Estilo(13f, Peso.semibold), if (elegido) C.sobreAccion else C.texto2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Contenido
                    val e = error
                    if (avisos != null) {
                        val l = visibles
                        if (l.isEmpty()) {
                            Box(Modifier.padding(top = 30.dp)) {
                                EstadoVacio(
                                    icono = "bell", titulo = if (vista == "para_hacer") "No hay nada para hacer" else "Todavía no hay avisos",
                                    texto = if (vista == "para_hacer") "Cuando entre una consulta, un WhatsApp o venza un cobro, aparece acá con la acción."
                                    else "Un aviso existe sólo si hay algo que hacer, y trae la acción.",
                                    accion = if (vista == "para_hacer") ("Ver todo" to { vista = "todo" }) else null,
                                    secundaria = "Configurar avisos" to { seg = 1 },
                                )
                            }
                        }
                        grupos(l).forEach { (titulo, lista) ->
                            key(titulo) {
                                Texto(titulo.uppercase(Formato.argentina), Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.padding(horizontal = 4.dp))
                                Column(Modifier.fillMaxWidth().tarjeta()) {
                                    lista.forEachIndexed { i, a -> key(a.id) { Fila(a, primera = i == 0) } }
                                }
                            }
                        }
                        if (siguiente != null) {
                            BotonTexto(if (cargandoMas) "Trayendo…" else "Ver más", { alcance.launch { verMas() } }, habilitado = !cargandoMas)
                        }
                    } else if (e != null) {
                        EstadoError(que = "tus avisos", error = e) { alcance.launch { cargar() } }
                    } else {
                        EsqueletoFilas(filas = 6)
                    }
                }
            }
        }
    }

    var ultimoEscribir by remember { mutableStateOf<Avisos.Escribir?>(null) }
    if (escribiendo != null) ultimoEscribir = escribiendo
    Hoja(visible = escribiendo != null, alCerrar = { escribiendo = null }, alturas = listOf(Altura.media, Altura.grande)) {
        ultimoEscribir?.let { e ->
            key(e.id) {
                HojaResponderAviso(aviso = e.aviso, accion = e.accion) { t -> hacer(e.aviso, e.accion, texto = t) }
            }
        }
    }
}

/** La hoja para responder desde el aviso (el WhatsApp o la pregunta de Mercado Libre): con la
 *  sugerencia ya escrita, la ayuda de ML y el error en la misma hoja. */
@Composable
fun HojaResponderAviso(aviso: AvisoCentro, accion: AvisoCentro.Accion, mandar: suspend (String) -> String?) {
    val cerrar = LocalCerrarHoja.current
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var texto by remember { mutableStateOf(aviso.datos?.get("sugerencia")?.texto ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var mandando by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoTono(icono = Avisos.icono(aviso.tipo).first, tono = Tono.verde, tam = 36.dp)
            Texto(aviso.titulo ?: "Responder", Estilo.tituloHojaChico, C.texto)
        }
        aviso.cuerpo?.nonEmpty?.let { Texto(it, Estilo.subhead, C.texto2) }
        Etiquetado("Tu respuesta") {
            CampoCorredor(texto, { texto = it }, lineasMin = 4, lineasMax = 8)
        }
        val e = error
        if (e != null) MensajeError(texto = e)
        else if (aviso.tipo == "mercadolibre") Texto("Mercado Libre no deja poner teléfonos.", Estilo.footnote, C.texto3)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Cancelar", { cerrar() }, Modifier.weight(1f))
            val ruta = aviso.url?.let { Ruta.de(it) }
            if (ruta != null) BotonSecundario("Abrir", { cerrar(); estado.ir(ruta) }, Modifier.weight(1f))
            BotonPrincipal(
                if (mandando) "Mandando…" else (accion.titulo ?: "Responder"),
                {
                    alcance.launch {
                        mandando = true
                        error = mandar(texto.trim())
                        mandando = false
                    }
                },
                Modifier.weight(1f),
                habilitado = texto.trim().isNotEmpty() && !mandando,
            )
        }
    }
}

// MARK: - 12B Configurar avisos

/** 12B Configurar avisos: este dispositivo (probar, apagar), por tipo al celular y por mail, y el
 *  horario de silencio. */
@Composable
fun ConfigAvisos() {
    PantallaApilada(titulo = "Configurar avisos") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            ConfigAvisos.Contenido()
        }
    }
}

object ConfigAvisos {
    @Serializable
    data class Vista(val tipos: List<Canales>, val silencio: Silencio) {
        @Serializable data class Canales(val tipo: String, val nombre: String? = null, val push: Boolean, val mail: Boolean, val sinPush: Boolean? = null)
        @Serializable data class Silencio(val activo: Boolean, val desde: Int, val hasta: Int, val domingos: Boolean)
    }

    /** Apagados a mano en este teléfono (app/Estado no vuelve a registrar el token si está prendido). La misma clave que
     *  `Estado.pushApagado` (el perfil y Configurar avisos apagan lo mismo). */
    fun apagadosAca(estado: Estado): Boolean = estado.pushApagado
    fun ponerApagadosAca(estado: Estado, v: Boolean) { estado.pushApagado = v }

    /** El permiso de avisos del sistema (UNAuthorizationStatus en iOS): «autorizado», «negado» o «sinPedir». */
    private fun permisoDe(ctx: android.content.Context): String = when {
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() -> "autorizado"
        ajustes(ctx).getBoolean("pidioAvisos", false) -> "negado"
        else -> "sinPedir"
    }

    @Composable
    fun Contenido() {
        val estado = LocalEstado.current
        val ctx = LocalContext.current
        val alcance = rememberCoroutineScope()
        var v by remember { mutableStateOf<Vista?>(null) }
        var error by remember { mutableStateOf<ErrorApi?>(null) }
        var permiso by remember { mutableStateOf<String?>(null) }
        var apagados by remember { mutableStateOf(apagadosAca(estado)) }
        var horario by remember { mutableStateOf(false) }

        suspend fun cargar() {
            permiso = permisoDe(ctx)
            try {
                v = estado.api.get("/v1/avisos/preferencias")
                error = null
            } catch (e: ErrorApi) {
                if (v == null) error = e
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        /** Se ve al instante y se guarda; si falla, vuelve como estaba y avisa. */
        fun guardar(nuevo: Vista) {
            val antes = v
            v = nuevo
            alcance.launch {
                try {
                    estado.api.hacer("PUT", "/v1/avisos/preferencias", nuevo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    v = antes
                    estado.avisar(Estado.texto(e, si = "No se guardó"), ok = false)
                }
            }
        }

        fun tocar(i: Int, push: Boolean) {
            val x = v ?: return
            if (i !in x.tipos.indices) return
            val t = x.tipos.toMutableList()
            t[i] = if (push) t[i].copy(push = !t[i].push) else t[i].copy(mail = !t[i].mail)
            guardar(x.copy(tipos = t))
        }

        suspend fun activar() {
            ponerApagadosAca(estado, false)
            apagados = false
            ajustes(ctx).edit().putBoolean("pidioAvisos", true).apply()
            estado.pedirPermisoPush()
            permiso = permisoDe(ctx)
        }

        suspend fun probar() {
            try {
                estado.api.hacer("POST", "/v1/push/prueba")
                estado.avisar("Mandamos uno de prueba")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estado.avisar("No salió la prueba", ok = false)
            }
        }

        suspend fun apagar() {
            val t = estado.tokenPush
            if (t == null) {
                ponerApagadosAca(estado, true)
                apagados = true
                return
            }
            try {
                estado.api.hacer("DELETE", "/v1/push/dispositivos/$t")
                ponerApagadosAca(estado, true)
                apagados = true
                estado.avisar("Este dispositivo ya no recibe avisos") {
                    alcance.launch {
                        ponerApagadosAca(estado, false)
                        apagados = false
                        estado.registrarTokenPush(t)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estado.avisar(e)
            }
        }

        LaunchedEffect(Unit) { cargar() }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Este dispositivo
            val activo = permiso == "autorizado"
            val textoDisp = if (permiso == "negado") "Están bloqueados en este dispositivo."
            else (if (activo && !apagados) "Llegan a este dispositivo." else "Activalos para que lleguen a este dispositivo.")
            Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoTono(icono = if (activo && !apagados) "bell.badge" else "bell.slash", tono = if (activo && !apagados) Tono.verde else Tono.neutro, tam = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("Avisos en este dispositivo", Estilo.filaLista, C.texto)
                    Texto(textoDisp, Estilo.footnote, C.texto2)
                }
                if (permiso == "negado") {
                    BotonTonal("Ver cómo", {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                } else if (!activo || apagados) {
                    BotonTonal("Activar", { alcance.launch { activar() } })
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BotonTonal("Probar", { alcance.launch { probar() } })
                        Texto("Apagar", Estilo.footnoteFuerte, C.error, Modifier.clickable { alcance.launch { apagar() } })
                    }
                }
            }
            val x = v
            val e = error
            if (x != null) {
                // La tabla: por tipo, al celular y por mail.
                Column(Modifier.fillMaxWidth().tarjeta()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.width(58.dp), contentAlignment = Alignment.Center) { Texto("Celular", Estilo(12f, Peso.bold), C.texto2) }
                        Box(Modifier.width(58.dp), contentAlignment = Alignment.Center) { Texto("Mail", Estilo(12f, Peso.bold), C.texto2) }
                    }
                    x.tipos.forEachIndexed { i, c ->
                        key(c.tipo) {
                            Row(Modifier.fillMaxWidth().lineaArriba().heightIn(min = 54.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Texto(c.nombre ?: c.tipo, Estilo(16f, Peso.medium), C.texto, Modifier.weight(1f))
                                if (c.sinPush == true) Spacer(Modifier.width(58.dp)) else Casilla(c.push, "${c.nombre ?: c.tipo} por celular") { tocar(i, push = true) }
                                Casilla(c.mail, "${c.nombre ?: c.tipo} por mail") { tocar(i, push = false) }
                            }
                        }
                    }
                }
                // El silencio
                Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icono("moon.fill", 22f, C.oro)
                    Tocable(alTocar = { horario = true }, modifier = Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto("Silencio de ${x.silencio.desde} a ${x.silencio.hasta}", Estilo(16f, Peso.semibold), C.texto)
                            Texto(Formato.sinViudas((if (x.silencio.domingos) "y domingos. " else "") + "Lo urgente espera a la mañana."), Estilo.footnote, C.texto2)
                        }
                    }
                    Llave(prendido = x.silencio.activo, alCambiar = { n ->
                        val y = v ?: return@Llave
                        guardar(y.copy(silencio = y.silencio.copy(activo = n)))
                    }, color = hex(0x2F9E6F))
                }
            } else if (e != null) {
                EstadoError(que = "tus avisos", error = e) { alcance.launch { cargar() } }
            } else {
                EsqueletoFilas(filas = 6)
            }
        }

        Hoja(visible = horario, alCerrar = { horario = false }, alturas = listOf(Altura.media)) {
            val s = v?.silencio
            if (s != null) {
                HorarioSilencio(desde = s.desde, hasta = s.hasta, domingos = s.domingos) { d, h, dom ->
                    val n = v ?: return@HorarioSilencio
                    guardar(n.copy(silencio = Vista.Silencio(activo = true, desde = d, hasta = h, domingos = dom)))
                }
            }
        }
    }

    @Composable
    private fun Casilla(on: Boolean, etiqueta: String, hacer: () -> Unit) {
        Tocable(alTocar = { Haptico.seleccion(); hacer() }, descripcion = etiqueta, modifier = Modifier.semantics { selected = on }) {
            Box(Modifier.size(58.dp, 44.dp), contentAlignment = Alignment.Center) {
                Icono(if (on) "checkmark.square.fill" else "square", 24f, if (on) C.accion else C.texto3)
            }
        }
    }
}

/** El horario de silencio: desde, hasta y los domingos. */
@Composable
fun HorarioSilencio(desde: Int, hasta: Int, domingos: Boolean, guardar: (Int, Int, Boolean) -> Unit) {
    val cerrar = LocalCerrarHoja.current
    val desdeInicial = desde
    val hastaInicial = hasta
    val domingosInicial = domingos
    var desde by remember { mutableIntStateOf(desdeInicial) }
    var hasta by remember { mutableIntStateOf(hastaInicial) }
    var domingos by remember { mutableStateOf(domingosInicial) }
    val horas = (0 until 24).map { it to "$it h" }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        ConIcono("Horario de silencio", "moon", Estilo.tituloHojaChico, C.texto)
        Texto(Formato.sinViudas("Los avisos al celular esperan a que termine. El centro de avisos los guarda igual."), Estilo.subhead, C.texto2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { Etiquetado("Desde") { SelectorMenu(opciones = horas, elegido = desde, alElegir = { desde = it }, descripcion = "Desde") } }
            Box(Modifier.weight(1f)) { Etiquetado("Hasta") { SelectorMenu(opciones = horas, elegido = hasta, alElegir = { hasta = it }, descripcion = "Hasta") } }
        }
        Interruptor(texto = "Los domingos, todo el día", prendido = domingos, alCambiar = { domingos = it })
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Cancelar", { cerrar() }, Modifier.weight(1f))
            BotonPrincipal("Guardar", { guardar(desde, hasta, domingos); cerrar() }, Modifier.weight(1f))
        }
    }
}

/** Borrar la cuenta (lo pide Apple; Google Play también): se confirma escribiendo BORRAR. */
@Composable
fun BorrarCuenta() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var borrando by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(C.fondo).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Texto("¿Borrás tu cuenta?", Estilo.titulo, C.texto)
        Texto(
            Formato.sinViudas("Se eliminan tu nombre, tu mail, tu teléfono y tus sesiones. Las operaciones, contratos y conversaciones quedan en la inmobiliaria, sin tu nombre. Esto no se puede deshacer."),
            Estilo.subhead, C.texto2,
        )
        Texto("Términos y privacidad", Estilo.subheadFuerte, C.accion, Modifier.clickable { abrirUrl(ctx, Api.web().trimEnd('/') + "/terminos") })
        CampoCorredor(texto, { texto = it }, placeholder = "Escribí BORRAR", mayusculas = KeyboardCapitalization.Characters)
        BotonDestructivo("Borrar mi cuenta", {
            borrando = true
            alcance.launch {
                try {
                    estado.api.hacer("DELETE", "/v1/auth/cuenta")
                    estado.salir()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    estado.avisar(e)
                }
                borrando = false
            }
        }, habilitado = texto == "BORRAR" && !borrando)
        Spacer(Modifier.weight(1f))
    }
}
