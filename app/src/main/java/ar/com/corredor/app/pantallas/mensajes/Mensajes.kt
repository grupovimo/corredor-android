package ar.com.corredor.app.pantallas.mensajes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ar.com.corredor.app.R
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.inicio.*
import ar.com.corredor.app.pantallas.mas.MasRaiz
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Clon de Pantallas/Mensajes/Mensajes.swift.

/** Los destinos de Mensajes: el chat de WhatsApp y la pregunta de Mercado Libre (los `navigationDestination` de iOS).
 *  app/Registro.kt lo llama con cada destino apilado: pinta la pantalla y devuelve true si es suyo. */
@Composable
fun destinosMensajes(d: Any): Boolean {
    when (d) {
        is AChat -> Chat(id = d.id)
        is APreguntaML -> RespuestaML(id = d.id)
        else -> return false
    }
    return true
}

/** Una fila de la bandeja: WhatsApp, Mercado Libre o la web. */
data class FilaBandeja(
    val id: String,
    val canal: String,
    val nombre: String,
    val texto: String,
    val cuando: Instant?,
    val propiedad: String?,
    val nuevo: Boolean,
    val mio: Boolean,
    val sinDueno: Boolean,
    val fuera: Boolean,
    val ir: Any,
    val contactoId: String? = null,
    /** La conversación de WhatsApp (para los datos de la fila y «Tomar»). */
    val conversacion: Conversacion? = null,
)

/** Las vistas de la bandeja (los chips del celular de la web): se piden filtradas a la API. */
enum class VistaBandeja {
    pendientes, mios, sinDueno, fuera, todas;

    val texto: String
        get() = when (this) {
            pendientes -> "Sin responder"
            mios -> "Míos"
            sinDueno -> "Sin dueño"
            fuera -> "Fuera de 24 h"
            todas -> "Todos"
        }

    val query: List<Pair<String, String>>
        get() = when (this) {
            pendientes -> listOf("esperando" to "si")
            mios -> listOf("agente" to "yo")
            sinDueno -> listOf("agente" to "libre")
            fuera -> listOf("ventana" to "fuera")
            todas -> emptyList()
        }

    fun cuenta(c: CuentasBandeja?): Int? {
        if (c == null) return null
        return when (this) {
            pendientes -> c.esperando
            mios -> c.mias
            sinDueno -> c.sinDueno
            fuera -> c.fueraDeVentana
            todas -> c.todas
        }
    }
}

object MensajesRaiz {
    @Serializable
    data class EstadoML(val conectada: Boolean? = null, val preguntasSinResponder: Int? = null)
}

/** 6A Bandeja: WhatsApp (con las vistas resueltas en la API), las preguntas de Mercado Libre y las
 *  consultas de la web; el estado del número, las plantillas aprobadas y los otros canales. */
@Composable
fun MensajesRaiz() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var vista by remember { mutableStateOf<VistaBandeja?>(null) }
    var q by remember { mutableStateOf("") }
    var conversaciones by remember { mutableStateOf<List<Conversacion>?>(null) }
    var otras by remember { mutableStateOf<List<FilaBandeja>>(emptyList()) }
    var cuentas by remember { mutableStateOf<CuentasBandeja?>(null) }
    var numero by remember { mutableStateOf<NumeroWA?>(null) }
    var plantillas by remember { mutableStateOf<List<PlantillaWA>>(emptyList()) }
    var ml by remember { mutableStateOf<MensajesRaiz.EstadoML?>(null) }
    var soloML by remember { mutableStateOf(false) }
    var tomadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nuevo by remember { mutableStateOf(false) }

    val elegida: VistaBandeja = vista ?: (if ((cuentas?.esperando ?: 0) > 0) VistaBandeja.pendientes else VistaBandeja.todas)

    suspend fun cargar() {
        numero = estado.api.opcional<NumeroWA>("/v1/whatsapp/numero")
        cuentas = estado.api.opcional<CuentasBandeja>("/v1/whatsapp/conversaciones/cuentas")
        try {
            val query = mutableListOf("limite" to "60") + elegida.query
            val t = q.nonEmpty
            val qq = if (t != null) query + ("q" to t.take(120)) else query
            conversaciones = estado.api.get("/v1/whatsapp/conversaciones", qq)
            error = null
        } catch (e: ErrorApi) {
            // Sin WhatsApp conectado la bandeja igual muestra Mercado Libre y la web.
            if (conversaciones == null) { if (numero?.conectado == false) conversaciones = emptyList() else error = e }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        val preguntas: List<PreguntaML> = estado.api.opcional<List<PreguntaML>>("/v1/mercadolibre/preguntas", listOf("limite" to "50")) ?: emptyList()
        val paginaWeb: PaginaAvisos? = estado.api.opcional("/v1/avisos", listOf("vista" to "para_hacer", "tipo" to "consulta", "limite" to "30"))
        val web = paginaWeb?.avisos ?: emptyList()
        val o = preguntas.map { p ->
            FilaBandeja(
                id = "ml" + p.id, canal = "ml", nombre = p.compradorApodo ?: "Mercado Libre", texto = p.texto ?: "", cuando = p.creadaEn, propiedad = p.propiedad,
                nuevo = p.estado == "sin_responder" || p.estado == "UNANSWERED", mio = true, sinDueno = false, fuera = false, ir = APreguntaML(id = p.id),
            )
        }.toMutableList()
        o += web.map { a ->
            val destino = a.url?.let { Ruta.de(it)?.destino }
            FilaBandeja(
                id = "web" + a.id, canal = "web", nombre = a.titulo ?: "Consulta desde tu web", texto = a.cuerpo ?: "", cuando = a.creadoEn, propiedad = null,
                nuevo = !a.estaHecho, mio = false, sinDueno = true, fuera = false,
                ir = destino ?: AAvisos(),
                contactoId = (destino as? AFichaContacto)?.id,
            )
        }
        otras = o
        if (numero?.conectado == true && plantillas.isEmpty()) plantillas = estado.api.opcional<List<PlantillaWA>>("/v1/whatsapp/plantillas") ?: emptyList()
        ml = estado.api.opcional<MensajesRaiz.EstadoML>("/v1/mercadolibre")
        Insignias.compartidas.refrescar(estado)
    }

    /** Tomar una sin dueño: queda de la persona al toque; si falla, vuelve y avisa con «Reintentar». */
    suspend fun tomarConversacion(c: Conversacion) {
        @Serializable data class Cuerpo(val agenteId: String?)
        tomadas = tomadas + c.id
        try {
            estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/${c.id}", Cuerpo(agenteId = estado.usuario?.id))
            Haptico.exito()
            estado.avisar("Tomaste la conversación con ${c.quien}") {
                alcance.launch {
                    try {
                        estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/${c.id}", JsonObject(mapOf("agenteId" to JsonNull)))
                        tomadas = tomadas - c.id
                        estado.version += 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        estado.avisar(e)
                    }
                }
            }
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tomadas = tomadas - c.id
            estado.avisarReintentar(Estado.texto(e, si = "No se guardó")) { alcance.launch { tomarConversacion(c) } }
        }
    }

    suspend fun tomarConsulta(contacto: String) {
        @Serializable data class C(val agenteId: String?)
        try {
            estado.api.hacer("POST", "/v1/consultas/$contacto/asignar", C(agenteId = estado.usuario?.id))
            estado.avisar("La consulta es tuya")
            cargar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    LaunchedEffect(estado.version, elegida, q) {
        if (q.isNotEmpty()) delay(250)
        cargar()
    }

    /** Las filas de la vista: las conversaciones que dio la API, y las preguntas de ML y las consultas
     *  de la web que van en esa vista. */
    val filas: List<FilaBandeja> = run {
        val yo = estado.usuario?.id
        val wa: List<FilaBandeja> = if (soloML) emptyList() else (conversaciones ?: emptyList()).map { c0 ->
            val c = if (c0.id in tomadas && c0.agenteId == null) c0.copy(agenteId = yo, agenteNombre = estado.usuario?.completo) else c0
            FilaBandeja(
                id = "wa" + c.id, canal = "wa", nombre = c.quien, texto = c.ultimoTexto ?: "", cuando = c.ultimoMensajeEn, propiedad = c.propiedadTitulo,
                nuevo = c.esperando ?: ((c.noLeidos ?: 0) > 0), mio = c.agenteId == yo, sinDueno = c.agenteId == null, fuera = c.fuera,
                ir = AChat(id = c.id), contactoId = c.contactoId, conversacion = c,
            )
        }
        val f = MasRaiz.normal(q)
        val extra = otras.filter { x ->
            val enVista = if (soloML) x.canal == "ml" else when (elegida) {
                VistaBandeja.pendientes -> x.nuevo
                VistaBandeja.sinDueno -> x.sinDueno
                VistaBandeja.todas -> true
                VistaBandeja.mios, VistaBandeja.fuera -> false
            }
            enVista && (f.isEmpty() || MasRaiz.normal(x.nombre + " " + x.texto + " " + (x.propiedad ?: "")).contains(f))
        }
        (wa + extra).sortedByDescending { it.cuando ?: Instant.MIN }
    }

    fun cuando(x: FilaBandeja): String {
        val f = x.cuando ?: return ""
        if (esHoyEnBA(f)) {
            return if (Duration.between(f, Instant.now()).seconds < 6 * 3600) Formato.hace(f).replace("hace ", "") else Formato.horaDe(f)
        }
        if (esAyerEnBA(f)) return if (x.fuera) "ayer ${Formato.horaDe(f)}" else "ayer"
        return Formato.dia(f)
    }

    @Composable
    fun Etiqueta(t: String, f: Color, c: Color) {
        Box(Modifier.height(22.dp).clip(RoundedCornerShape(6.dp)).background(f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Texto(t, Estilo(12f, Peso.semibold), c, lineas = 1)
        }
    }

    @Composable
    fun Mini(n: String) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(C.avisoFondo), contentAlignment = Alignment.Center) {
            Texto(Formato.iniciales(n), Estilo(10f, Peso.bold), C.avisoTinta)
        }
    }

    @Composable
    fun Avatar(x: FilaBandeja) {
        Box(Modifier.size(51.dp)) {
            if (x.conversacion?.sinNombre == true) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(C.azulFondo), contentAlignment = Alignment.Center) { Texto("+54", Estilo(13f, Peso.bold), C.azulTinta) }
            } else {
                Box(Modifier.size(48.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) { Texto(Formato.iniciales(x.nombre), Estilo(15f, Peso.bold), C.texto) }
            }
            Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(C.superficie), contentAlignment = Alignment.Center) {
                when (x.canal) {
                    "wa" -> Image(painterResource(R.drawable.logo_whatsapp), null, Modifier.size(16.dp), contentScale = ContentScale.Fit)
                    "ml" -> Image(painterResource(R.drawable.logo_mercadolibre), null, Modifier.size(16.dp), contentScale = ContentScale.Fit)
                    else -> Icono("globe", 14f, C.azulTinta)
                }
            }
        }
    }

    /** Lo de abajo de la fila, como la web (FilaConversacion): número nuevo, más de 24 h, sin dueño, la
     *  propiedad con las iniciales de quien la tiene, o «de Martín». */
    @Composable
    fun Meta(x: FilaBandeja) {
        val c = x.conversacion
        if (c != null) {
            val p = c.propiedadTitulo
            val a = c.agenteNombre
            when {
                c.sinNombre -> Texto("número nuevo · crear contacto", Estilo.footnote, C.azulTinta)
                c.fuera && c.esperando == true -> Row(
                    Modifier.height(22.dp).clip(RoundedCornerShape(6.dp)).background(C.avisoFondo).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icono("clock", 12f, C.avisoTinta)
                    Texto("más de 24 h · plantilla", Estilo(12f, Peso.semibold), C.avisoTinta)
                }
                c.agenteId == null -> Texto("sin dueño" + (p?.let { " · $it" } ?: ""), Estilo.footnote, C.avisoTinta, lineas = 1)
                p != null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (a != null) Mini(a)
                    Etiqueta(p, C.hundida, C.texto2)
                }
                a != null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mini(a)
                    Texto("de ${Charla.primerNombre(a)}", Estilo.footnote, C.texto2)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                x.propiedad?.let { Etiqueta(it, C.hundida, C.texto2) }
                if (x.sinDueno) Etiqueta("Sin dueño", C.azulFondo, C.azulTinta)
            }
        }
    }

    @Composable
    fun Fila(x: FilaBandeja, primera: Boolean) {
        val c = x.conversacion
        val espera = c?.esperando ?: x.nuevo
        val colorHora: Color = if (espera) (if (x.fuera) C.error else C.accion) else C.texto3
        val previa = c?.let { (if (it.esperando == true) "" else "Vos: ") + (it.ultimoTexto ?: "") } ?: x.texto
        val sinLeer = c?.noLeidos ?: (if (x.nuevo) 1 else 0)
        val menu = buildList {
            if (c != null && c.agenteId == null && estado.escribe) add(OpcionDeMenu("Tomar", "person.crop.circle.badge.checkmark") { alcance.launch { tomarConversacion(c) } })
            val cid = x.contactoId
            if (x.canal == "web" && x.sinDueno && estado.escribe && cid != null) {
                add(OpcionDeMenu("Tomarla yo", "person.crop.circle.badge.checkmark") { alcance.launch { tomarConsulta(cid) } })
            }
        }
        MenuContextual(opciones = menu, alTocar = { estado.abrir(x.ir) }) {
            Row(
                Modifier.fillMaxWidth().lineaArriba(!primera).padding(horizontal = 14.dp, vertical = 12.dp).alpha(if (espera) 1f else 0.82f),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Avatar(x)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            Texto(x.nombre, Estilo(16f, if (espera) Peso.bold else Peso.medium), C.texto, Modifier.weight(1f), lineas = 1)
                            Texto(cuando(x), Estilo.footnote, colorHora)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Texto(previa, Estilo.texto14, C.texto2, Modifier.weight(1f), lineas = 1)
                            if (sinLeer > 0) {
                                if (c != null) InsigniaRoja(n = sinLeer) else Box(Modifier.size(10.dp).clip(CircleShape).background(C.accion))
                            }
                        }
                        Box(Modifier.padding(top = 2.dp)) { Meta(x) }
                    }
                }
                if (c != null && c.agenteId == null && estado.escribe) {
                    Box(Modifier.semanticsDescripcion("Tomar la conversación con ${x.nombre}")) {
                        BotonTonal("Tomar", { alcance.launch { tomarConversacion(c) } })
                    }
                }
            }
        }
    }

    @Composable
    fun Canal(icono: String, tono: Tono, t: String, d: String, cuenta: Int, primera: Boolean, hacer: () -> Unit) {
        Tocable(alTocar = hacer, modifier = Modifier.lineaArriba(!primera)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoTono(icono = icono, tono = tono, tam = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(t, Estilo.filaLista, C.texto)
                    Texto(d, Estilo.footnote, C.texto2)
                }
                if (cuenta > 0) InsigniaRoja(n = cuenta)
                Chevron()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FondoApp()
        Refrescable(alRefrescar = { cargar() }) {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CabeceraPestana(
                    titulo = "Mensajes",
                    acciones = if (estado.escribe) listOf(AccionCabecera(icono = "square.and.pencil", etiqueta = "Mensaje nuevo", hacer = { nuevo = true })) else emptyList(),
                )
                Box(Modifier.padding(horizontal = 16.dp)) { Buscador(placeholder = "Nombre, número o mensaje…", texto = q, alCambiar = { q = it }) }
                if (numero?.conectado == true) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VistaBandeja.entries.forEach { v ->
                            key(v) {
                                val n = v.cuenta(cuentas) ?: 0
                                ChipFiltro(texto = v.texto + (if (n > 0 && v != VistaBandeja.todas) " $n" else ""), activo = elegida == v && !soloML, hacer = {
                                    soloML = false
                                    vista = v
                                })
                            }
                        }
                    }
                }
                numero?.let { n -> if (n.caido) Box(Modifier.padding(horizontal = 16.dp)) { NumeroCaido(numero = n) } }
                if (numero?.conectado == false) Box(Modifier.padding(horizontal = 16.dp)) { SinWhatsApp() }
                // La lista
                val e = error
                if (conversaciones != null) {
                    val l = filas
                    if (l.isEmpty()) {
                        Box(Modifier.padding(top = 20.dp)) {
                            if (q.isNotEmpty()) {
                                EstadoVacio(
                                    icono = "magnifyingglass", titulo = "No encontramos «$q»", texto = "Probá con el nombre, el número o lo que escribió.",
                                    accion = "Borrar la búsqueda" to { q = "" },
                                )
                            } else if (numero?.conectado == false) {
                                // Nada: ya está «Sin WhatsApp» arriba.
                            } else {
                                val verTodas: Pair<String, () -> Unit> = "Ver todas" to { soloML = false; vista = VistaBandeja.todas }
                                val conectar: Pair<String, () -> Unit>? = if (estado.esDuenio) ("Conectar WhatsApp" to { estado.abrir(ASeccion(clave = "integraciones")) }) else null
                                EstadoVacio(
                                    icono = "bubble.left.and.bubble.right",
                                    titulo = if (elegida == VistaBandeja.pendientes && !soloML) "No hay nadie esperando respuesta" else "No hay conversaciones acá",
                                    texto = "WhatsApp, Mercado Libre y tu web entran acá.",
                                    accion = if (elegida != VistaBandeja.todas || soloML) verTodas else conectar,
                                )
                            }
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().tarjeta()) {
                            l.forEachIndexed { i, x -> key(x.id) { Fila(x, primera = i == 0) } }
                        }
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError(que = "los mensajes", error = e) { alcance.launch { cargar() } } }
                } else {
                    Box(Modifier.padding(horizontal = 16.dp)) { EsqueletoFilas(filas = 6) }
                }
                // Las plantillas aprobadas con sus usos y los otros canales (el panel de sección de la web).
                if (plantillas.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TituloBloque(texto = "Plantillas aprobadas")
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            plantillas.take(4).forEachIndexed { i, p ->
                                key(p.id) {
                                    Row(Modifier.fillMaxWidth().lineaArriba(i > 0).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconoTono(icono = "text.bubble", tono = Tono.neutro, tam = 36.dp)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Texto(Charla.nombrePlantilla(p.nombre), Estilo.filaLista, C.texto)
                                            Texto(if ((p.usos ?: 0) > 0) "usada ${if (p.usos == 1) "1 vez" else "${p.usos ?: 0} veces"}" else "sin usar todavía", Estilo.footnote, C.texto2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TituloBloque(texto = "Otros canales")
                    Column(Modifier.fillMaxWidth().tarjeta()) {
                        Canal("storefront", Tono.oro, "Mercado Libre", if (ml?.conectada == true) "preguntas" else "sin conectar", cuenta = ml?.preguntasSinResponder ?: 0, primera = true) {
                            if (ml?.conectada == true) soloML = true else estado.abrir(ASeccion(clave = "mercadolibre"))
                        }
                        Canal("megaphone", Tono.azul, "Difusión a portales", "Zonaprop, Argenprop", cuenta = 0, primera = false) {
                            estado.abrir(ASeccion(clave = "difusion"))
                        }
                    }
                }
            }
        }
    }

    Hoja(visible = nuevo, alCerrar = { nuevo = false }, alturas = listOf(Altura.media, Altura.grande)) { MensajeNuevo() }
}

/** El texto que lee TalkBack (accessibilityLabel). */
internal fun Modifier.semanticsDescripcion(d: String): Modifier =
    this.then(Modifier.semantics(mergeDescendants = true) { contentDescription = d })

/** El número caído en Meta (desconectado, restringido o limitado), con el motivo, cuándo y la salida
 *  para el dueño (web: lib/mensajes/NumeroCaido.svelte). */
@Composable
fun NumeroCaido(numero: NumeroWA) {
    val estado = LocalEstado.current
    val cuando: String = numero.estadoEn?.let { f ->
        if (esHoyEnBA(f)) "a las ${Formato.horaDe(f)}"
        else if (esAyerEnBA(f)) "ayer a las ${Formato.horaDe(f)}"
        else "el ${Formato.fechaLarga(f)}"
    } ?: ""
    data class Pieza(val icono: String, val titulo: String, val texto: String, val boton: String, val oro: Boolean)
    val p = when (numero.estado) {
        "restringido" -> Pieza(
            "nosign", "Meta restringió el WhatsApp${if (cuando.isEmpty()) "" else " $cuando"}",
            "Lo que mandes no sale hasta que Meta lo resuelva; lo que te escriban entra igual.", "Revisar", false,
        )
        "limitado" -> {
            val desde = if (cuando.isEmpty()) "" else " desde " + (if (cuando.startsWith("a las")) "las" + cuando.drop(5) else cuando)
            Pieza(
                "gauge.with.dots.needle.33percent", "WhatsApp está limitado$desde",
                "Por ahora salen menos mensajes por día. Contestá a tiempo y sin repetir plantillas para que vuelva a la normalidad.", "Ver el número", true,
            )
        }
        else -> Pieza(
            "link", "WhatsApp se desconectó${if (cuando.isEmpty()) "" else " $cuando"}",
            "Los mensajes que lleguen mientras tanto no se pierden: entran cuando reconectes.", "Reconectar", false,
        )
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (p.oro) C.avisoFondo else C.errorFondo).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Icono(p.icono, 20f, if (p.oro) C.avisoTinta else C.errorTinta)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Texto(p.titulo, Estilo.subheadFuerte, C.texto)
            Texto(Formato.sinViudas((numero.estadoMotivo?.let { "$it " } ?: "") + p.texto), Estilo.texto14, C.texto2)
            if (estado.esDuenio) {
                Box(Modifier.padding(top = 4.dp)) { BotonTonal(p.boton, { estado.abrir(ASeccion(clave = "integraciones")) }) }
            }
        }
    }
}

/** Sin WhatsApp conectado (web: lib/mensajes/SinWhatsApp.svelte): la conversación fantasma y la salida. */
@Composable
fun SinWhatsApp() {
    val estado = LocalEstado.current
    val linea2 = C.linea2
    Column(Modifier.fillMaxWidth().tarjeta().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().drawBehind {
                drawRoundRect(
                    linea2, cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(C.hundida).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Texto("Hola, ¿sigue disponible el 3 amb?", Estilo.texto14, C.texto2)
            }
            Texto("vinculada a la propiedad", Estilo.footnote, C.texto3)
        }
        Texto("Las consultas van a entrar acá, ordenadas por propiedad", Estilo.headline, C.texto)
        Texto(Formato.sinViudas("El número sigue andando en tu teléfono. El equipo lo comparte acá."), Estilo.subhead, C.texto2)
        if (estado.esDuenio) {
            BotonPrincipal("Conectar WhatsApp", { estado.abrir(ASeccion(clave = "integraciones")) }, contenido = {
                Image(painterResource(R.drawable.logo_whatsapp), null, Modifier.size(18.dp))
                Texto("Conectar WhatsApp", Estilo.headline, C.sobreAccion, lineas = 1)
            })
        }
    }
}

/** Mensaje nuevo: elegir un contacto y abrir (o crear) la conversación de WhatsApp. */
@Composable
fun MensajeNuevo() {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Resultado>>(emptyList()) }

    LaunchedEffect(q) {
        if (q.length < 2) { res = emptyList(); return@LaunchedEffect }
        delay(220)
        res = estado.api.opcional<List<Resultado>>("/v1/buscar", listOf("tipo" to "contacto", "q" to q)) ?: emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(titulo = "Mensaje nuevo", cancelar = "Cancelar", alCancelar = { cerrar() })
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) { Buscador(placeholder = "Nombre o teléfono", texto = q, alCambiar = { q = it }) }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                res.forEachIndexed { i, r ->
                    key(r.id) {
                        Tocable(alTocar = {
                            alcance.launch {
                                @Serializable data class Cuerpo(val contactoId: String)
                                @Serializable data class Conv(val id: String)
                                try {
                                    val c: Conv = estado.api.enviar("POST", "/v1/whatsapp/conversaciones", Cuerpo(contactoId = r.id))
                                    cerrar()
                                    estado.abrir(AChat(id = c.id), en = Pestana.mensajes)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    estado.avisar(e)
                                }
                            }
                        }, modifier = Modifier.lineaArriba(i > 0)) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                                Texto(r.titulo, Estilo.filaLista, C.texto)
                                r.subtitulo?.let { Texto(it, Estilo.footnote, C.texto2) }
                            }
                        }
                    }
                }
            }
            if (q.length >= 2 && res.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EstadoVacio(icono = "person.crop.circle.badge.questionmark", titulo = "No encontramos «$q»", texto = "Probá con el nombre o el teléfono.")
                }
            } else if (q.length < 2) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EstadoVacio(icono = "magnifyingglass", titulo = "¿A quién le escribís?", texto = "Buscá el contacto por nombre o teléfono.")
                }
            }
        }
    }
}

/** Un mensaje de WhatsApp (whatsapp.Mensaje). */
@Serializable
data class MensajeWA(
    val id: String,
    val conversacionId: String? = null,
    val direccion: String,
    val tipo: String? = null,
    val texto: String? = null,
    val plantilla: PlantillaUsada? = null,
    val mediaTipo: String? = null,
    val archivoId: String? = null,
    val archivoUrl: String? = null,
    val estado: String? = null,
    val error: String? = null,
    val autorId: String? = null,
    val ocurrioEn: FechaApi,
) {
    @Serializable data class PlantillaUsada(val nombre: String? = null, val parametros: List<String>? = null)
    val saliente: Boolean get() = direccion == "saliente" || direccion == "out"
    /** Mandado desde acá y todavía sin respuesta de la API. */
    val local: Boolean get() = id.startsWith("tmp-")
}

/** Una plantilla aprobada en Meta, con cuántas veces se mandó. */
@Serializable
data class PlantillaWA(
    val nombre: String,
    val idioma: String? = null,
    val categoria: String? = null,
    val cuerpo: String? = null,
    val parametros: Int? = null,
    val usos: Int? = null,
) {
    val id: String get() = nombre
}

@Serializable
data class RespuestaGuardada(val id: String, val titulo: String, val texto: String, val usos: Int? = null)

/** Elegir una propiedad de la cartera (vincular un chat, una visita, un contrato): las últimas
 *  tocadas y, al escribir, la búsqueda del servidor (calle, barrio o código). */
@Composable
fun ElegirPropiedad(alElegir: (Propiedad) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var props by remember { mutableStateOf<List<Propiedad>>(emptyList()) }
    var encontradas by remember { mutableStateOf<List<Resultado>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }

    suspend fun elegir(r: Resultado) {
        try {
            val p: Propiedad = estado.api.get("/v1/propiedades/${r.id}")
            alElegir(p)
            cerrar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    LaunchedEffect(Unit) {
        props = estado.api.opcional<List<Propiedad>>("/v1/propiedades", listOf("limite" to "200", "orden" to "actualizada")) ?: emptyList()
        cargando = false
    }
    LaunchedEffect(q) {
        if (q.length < 2) { encontradas = emptyList(); return@LaunchedEffect }
        delay(200)
        cargando = true
        val r: List<Resultado> = estado.api.opcional<List<Resultado>>("/v1/buscar", listOf("tipo" to "propiedad", "q" to q)) ?: emptyList()
        encontradas = r.filter { it.tipo == "propiedad" }
        cargando = false
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(titulo = "Elegí la propiedad", cancelar = "Cancelar", alCancelar = { cerrar() })
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) { Buscador(placeholder = "Calle, barrio o código…", texto = q, alCambiar = { q = it }) }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (q.length >= 2) {
                    encontradas.forEachIndexed { i, r ->
                        key(r.id) {
                            Tocable(alTocar = { alcance.launch { elegir(r) } }, modifier = Modifier.lineaArriba(i > 0)) {
                                Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                                    Texto(r.titulo, Estilo.filaLista, C.texto)
                                    r.subtitulo?.let { Texto(it, Estilo.footnote, C.texto2) }
                                }
                            }
                        }
                    }
                } else {
                    props.forEachIndexed { i, p ->
                        key(p.id) {
                            Tocable(alTocar = { alElegir(p); cerrar() }, modifier = Modifier.lineaArriba(i > 0)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) { Foto(url = p.miniatura, modifier = Modifier.matchParentSize()) }
                                    Column {
                                        Texto(p.nombre, Estilo.filaLista, C.texto)
                                        Texto(p.precioTexto, Estilo.footnote, C.texto2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (q.length >= 2 && encontradas.isEmpty() && !cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoVacio(icono = "magnifyingglass", titulo = "No encontramos nada con «$q».") }
            } else if (q.length < 2 && props.isEmpty() && !cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EstadoVacio(
                        icono = "building.2", titulo = "Todavía no hay propiedades", texto = "Cargá la primera y la vinculás acá.",
                        accion = "Cargar propiedad" to { cerrar(); estado.abrir(AAltaPropiedad()) },
                    )
                }
            }
        }
    }
}

/** Una nota de voz: play, ondas y la duración (AVPlayer en iOS; MediaPlayer acá). */
@Composable
fun AudioMensaje(url: String?) {
    val ctx = LocalContext.current
    var reproductor by remember { mutableStateOf<MediaPlayer?>(null) }
    var sonando by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { reproductor?.release(); reproductor = null } }
    Row(Modifier.width(196.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Tocable(alTocar = {
            if (reproductor == null && url != null) {
                reproductor = runCatching {
                    MediaPlayer().apply {
                        setDataSource(url)
                        setOnCompletionListener { sonando = false }
                        prepare()
                    }
                }.getOrNull()
            }
            val r = reproductor
            if (sonando) r?.pause() else { r?.seekTo(0); r?.start() }
            sonando = !sonando
        }, descripcion = if (sonando) "Pausar" else "Escuchar") {
            Icono(if (sonando) "pause.circle.fill" else "play.circle.fill", 30f, C.accion)
        }
        Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(22) { i -> Box(Modifier.size(3.dp, (6 + (i * 7) % 18).dp).clip(CircleShape).background(C.texto3)) }
        }
    }
}

/** Graba una nota de voz mientras se mantiene apretado el micrófono (AVAudioRecorder en iOS; MediaRecorder acá). */
class Grabadora {
    var grabando by mutableStateOf(false)
        private set
    private var grabador: MediaRecorder? = null
    private var url: File? = null
    private var desde = 0L

    /** Empieza si ya hay permiso del micrófono; si no, devuelve false para que la pantalla lo pida. */
    fun empezar(ctx: Context): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val carpeta = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val u = File(carpeta, "nota-${UUID.randomUUID()}.m4a")
        url = u
        grabador = runCatching {
            @Suppress("DEPRECATION")
            (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setOutputFile(u.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
        if (grabador == null) return true
        desde = SystemClock.elapsedRealtime()
        grabando = true
        Haptico.impacto()
        return true
    }

    fun terminar(): File? {
        if (!grabando) return null
        val duracion = (SystemClock.elapsedRealtime() - desde) / 1000.0
        runCatching { grabador?.stop() }
        grabador?.release()
        grabador = null
        grabando = false
        return if (duracion > 0.8) url else null
    }
}

/** 6D Pregunta de Mercado Libre: la propiedad, la pregunta y la respuesta armada con los datos de la
 *  ficha (sin teléfonos ni mails, que ML no permite). */
@Composable
fun RespuestaML(id: String) {
    val estado = LocalEstado.current
    val cerrar = rememberVolver()
    val alcance = rememberCoroutineScope()
    var p by remember { mutableStateOf<PreguntaML?>(null) }
    var prop by remember { mutableStateOf<Propiedad?>(null) }
    var texto by remember { mutableStateOf("") }
    var fuentes by remember { mutableStateOf<List<String>>(emptyList()) }
    var enviando by remember { mutableStateOf(false) }
    /** «3 amb · 74 m² · Martín · 214 visitas esta semana», como la tarjeta de la pregunta en el panel. */
    var linea by remember { mutableStateOf("") }

    val prohibido = Regex("""\d{6,}|@|\b(\d{2,4}[\s-]?){3,}\b""").containsMatchIn(texto)

    suspend fun responder() {
        enviando = true
        @Serializable data class Cuerpo(val texto: String)
        try {
            estado.cola.enviar("POST", "/v1/mercadolibre/preguntas/$id/respuesta", Cuerpo(texto = texto), etiqueta = "Respuesta en Mercado Libre")
            estado.version += 1
            estado.avisar("Respondida en Mercado Libre")
            cerrar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            enviando = false
        }
    }

    LaunchedEffect(Unit) {
        val d = runCatching { String(estado.api.pedir("GET", "/v1/mercadolibre/preguntas", listOf("limite" to "200")), Charsets.UTF_8) }.getOrNull() ?: ""
        p = runCatching { Api.json.decodeFromString<List<PreguntaML>>(d) }.getOrElse { emptyList() }.firstOrNull { it.id == id }
        val semana = runCatching { Api.json.decodeFromString<List<PreguntaListaML>>(d) }.getOrElse { emptyList() }.firstOrNull { it.id == id }?.visitasSemana
        val pid = p?.propiedadId
        if (pid != null) {
            prop = estado.api.opcional<Propiedad>("/v1/propiedades/$pid")
            val equipo: List<Miembro> = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
            val pubs: List<PublicacionLista> = runCatching { estado.api.todas<PublicacionLista>("/v1/publicaciones", listOf("portal" to "mercadolibre")) { it.id } }.getOrElse { emptyList() }
            val total = pubs.firstOrNull { it.propiedadId == pid }?.visitas
            val agente = prop?.agenteId?.let { a -> equipo.firstOrNull { it.usuarioId == a }?.completo?.split(" ")?.firstOrNull() }
            linea = listOfNotNull(
                prop?.ambientes?.let { "$it amb" },
                prop?.superficie?.let { "${Formato.numero(it.toInt())} m²" },
                agente,
                (semana ?: pubs.firstOrNull { it.propiedadId == pid }?.visitasSemana)?.let { "${Formato.numero(it)} visitas esta semana" }
                    ?: total?.let { "${Formato.numero(it)} visitas" },
            ).joinToString(" · ")
        }
        @Serializable data class Sug(val sugerencia: String? = null, val fuentes: List<String>? = null)
        if (p?.respuesta == null) {
            val s = estado.api.opcional<Sug>("/v1/mercadolibre/preguntas/$id/sugerencia")
            if (s != null) {
                if (texto.isEmpty()) texto = s.sugerencia ?: ""
                fuentes = s.fuentes ?: emptyList()
            }
        }
    }

    PantallaApilada(titulo = "Mercado Libre") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val pr = prop
            if (pr != null) {
                Tocable(alTocar = { estado.abrir(AFichaPropiedad(id = pr.id)) }) {
                    Row(Modifier.fillMaxWidth().tarjeta().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) { Foto(url = pr.miniatura, modifier = Modifier.matchParentSize()) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(pr.nombre, Estilo(16f, Peso.bold), C.texto)
                            val op = pr.operacionTexto.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                            Texto("$op · ${pr.precioTexto}", Estilo.texto14, C.texto2)
                            if (linea.isNotEmpty()) Texto(linea, Estilo.footnote, C.texto2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.logo_mercadolibre), null, Modifier.height(14.dp), contentScale = ContentScale.Fit)
                                Texto("Publicada en Mercado Libre", Estilo(12f, Peso.regular), C.texto2)
                            }
                        }
                        Chevron()
                    }
                }
            }
            val pq = p
            if (pq != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Texto("${pq.compradorApodo ?: "Alguien"} preguntó ${pq.creadaEn?.let { Formato.hace(it) } ?: ""}", Estilo.footnote, C.texto2)
                    val forma = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp)
                    Box(Modifier.sombraE2().clip(forma).background(C.superficie).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Texto(pq.texto ?: "", Estilo.body16, C.texto)
                    }
                }
                val r = pq.respuesta
                if (r != null) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.okFondo).padding(14.dp)) {
                        ConIcono("Respondida: $r", "checkmark.circle", Estilo.subhead, C.okTinta)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConIcono("Respuesta armada con los datos de la ficha", "sparkles", Estilo(13f, Peso.bold), C.avisoTinta)
                        val estilo = Estilo.body16
                        BasicTextField(
                            value = texto, onValueChange = { texto = it }, minLines = 3, maxLines = 10,
                            textStyle = estilo.ts().copy(color = C.texto, lineHeight = (estilo.tamano * 1.2f + 3f).sp),
                            cursorBrush = SolidColor(C.accion), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { campo ->
                                Box {
                                    if (texto.isEmpty()) Texto("Escribí la respuesta", estilo, C.texto3)
                                    campo()
                                }
                            },
                        )
                        if (fuentes.isNotEmpty()) {
                            FlowLayout(espacio = 6.dp) {
                                fuentes.forEach { f ->
                                    Box(Modifier.height(30.dp).clip(CircleShape).background(C.hundida).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                        Texto(f, Estilo(13f, Peso.semibold), C.texto)
                                    }
                                }
                            }
                        }
                        Texto(
                            if (prohibido) "Sacá teléfonos y mails: Mercado Libre no los permite." else "Mercado Libre no permite teléfonos ni mails en las respuestas.",
                            Estilo(12f, if (prohibido) Peso.bold else Peso.regular), if (prohibido) C.error else C.texto3,
                        )
                    }
                    val habilitado = texto.trim().isNotEmpty() && !prohibido && !enviando
                    Tocable(alTocar = { alcance.launch { responder() } }, habilitado = habilitado) {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (habilitado) 1f else 0.5f).clip(RoundedCornerShape(14.dp)).background(C.accion),
                            contentAlignment = Alignment.Center,
                        ) { Texto("Responder en Mercado Libre", Estilo(17f, Peso.semibold), C.sobreAccion) }
                    }
                }
            }
        }
    }
}

/** Acomoda chips en renglones (los datos usados, los filtros que no entran en una fila). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowLayout(espacio: Dp = 8.dp, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(espacio), verticalArrangement = Arrangement.spacedBy(espacio)) { contenido() }
}
