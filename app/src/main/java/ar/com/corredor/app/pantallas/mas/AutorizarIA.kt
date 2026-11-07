package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Clon de Pantallas/Mas/AutorizarIA.swift.

/** `GET /v1/ia/solicitudes/{id}` (mcp.Solicitud). */
@Serializable
data class SolicitudIA(
    val cliente: Cliente,
    val inmobiliaria: Inmo,
    val rol: String? = null,
    val habilitada: Boolean? = null,
    val pedidos: List<String>? = null,
    val permitidos: List<String>? = null,
    val permisos: List<Permiso>? = null,
    val nunca: String? = null,
    val aviso: String? = null,
) {
    @Serializable data class Cliente(val nombre: String, val sitio: String? = null, val logo: String? = null, val verificado: Boolean? = null, val vuelta: String? = null)
    @Serializable data class Inmo(val id: String? = null, val slug: String? = null, val nombre: String)
    @Serializable data class Permiso(val permiso: String, val titulo: String, val detalle: String) { val id: String get() = permiso }
}

/** Lo estático de AutorizarIA (en iOS, AutorizarIA.Fase, AutorizarIA.Solicitud, iconos y nombres). */
object AutorizarIA {
    sealed class Fase {
        data object cargando : Fase()
        data class lista(val s: SolicitudIA) : Fase()
        data object ajena : Fase()
        data object vencida : Fase()
        data class error(val m: String) : Fase()
    }

    val iconos: Map<String, Pair<String, @Composable () -> Color>> = mapOf(
        "leer" to ("eye" to @Composable { C.okTinta }), "borradores" to ("square.and.pencil" to @Composable { C.okTinta }),
        "agendar" to ("calendar" to @Composable { C.oroTinta }), "mover" to ("rectangle.split.3x1" to @Composable { C.oroTinta }),
    )
    val nombres = mapOf("agendar" to "agendar visitas", "mover" to "mover operaciones", "borradores" to "armar borradores")
}

/** Una imagen de los assets por su nombre (Image("Simbolo") en iOS); no rompe si todavía no está en res/drawable. */
@Composable
internal fun ImagenAsset(nombre: String, modifier: Modifier = Modifier) {
    // PARA EL NÚCLEO: el símbolo de Corredor tiene que estar en res/drawable como «simbolo».
    val ctx = LocalContext.current
    val id = remember(nombre) { ctx.resources.getIdentifier(nombre, "drawable", ctx.packageName) }
    if (id != 0) Image(painterResource(id), null, modifier, contentScale = ContentScale.Fit) else Box(modifier)
}

/** El consentimiento de «Corredor en tu IA» (conectar-ia/autorizar de la web): el asistente pide entrar a la
 *  inmobiliaria; la persona elige qué le deja hacer y autoriza o cancela. Al decidir, vuelve al asistente con la URL
 *  que arma la API. Llega por el link `…/conectar-ia/autorizar?solicitud=sol_…`. */
@Composable
fun AutorizarIA(solicitud: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var fase by remember { mutableStateOf<AutorizarIA.Fase>(AutorizarIA.Fase.cargando) }
    var elegidos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var enviando by remember { mutableStateOf<String?>(null) }
    var errorEnvio by remember { mutableStateOf<String?>(null) }

    suspend fun cargar() {
        if (!Regex("^sol_[A-Za-z0-9_-]{10,100}$").containsMatchIn(solicitud)) { fase = AutorizarIA.Fase.vencida; return }
        fase = AutorizarIA.Fase.cargando
        try {
            val s: SolicitudIA = estado.api.get("/v1/ia/solicitudes/$solicitud")
            // Lo opcional arranca prendido si lo pidió el asistente (se puede apagar); «leer» va siempre.
            elegidos = (s.permisos ?: emptyList()).map { it.permiso }.filter { it != "leer" }.toSet()
            fase = AutorizarIA.Fase.lista(s)
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            fase = if (e.estado == 403) AutorizarIA.Fase.ajena
            else if (e.estado == 404 || e.estado == 410) AutorizarIA.Fase.vencida
            else AutorizarIA.Fase.error(e.detalle.ifEmpty { "Probá de nuevo en un rato." })
        } catch (e: Exception) { fase = AutorizarIA.Fase.error("Probá de nuevo en un rato.") }
    }

    @Serializable data class Cuerpo(val permisos: List<String>)
    @Serializable data class Vuelta(val redireccion: String)

    suspend fun decidir(que: String) {
        enviando = que
        errorEnvio = null
        try {
            val v: Vuelta = estado.api.enviar("POST", "/v1/ia/solicitudes/$solicitud/$que", Cuerpo(if (que == "aprobar") elegidos.toList() else emptyList()))
            if (v.redireccion.isNotEmpty()) abrirUrl(ctx, v.redireccion)
            estado.ir(Ruta(Pestana.inicio))
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            errorEnvio = if (e.codigo == "ia_apagada") "La dueña o el dueño tiene que habilitar Corredor en tu IA en Configuración." else e.detalle.ifEmpty { e.titulo }
        } catch (e: Exception) { estado.avisar(e) } finally { enviando = null }
    }

    @Composable
    fun cabecera() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImagenAsset("simbolo", Modifier.size(48.dp))
                val f = fase
                if (f is AutorizarIA.Fase.lista) {
                    Icono("arrow.left.arrow.right", 17f, C.texto3)
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(C.superficie), contentAlignment = Alignment.Center) {
                        val l = f.s.cliente.logo
                        if (!l.isNullOrEmpty()) AsyncImage(l, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Texto(f.s.cliente.nombre.take(1), Estilo(20f, Peso.bold), C.texto)
                    }
                }
            }
            val f = fase
            if (f is AutorizarIA.Fase.lista) {
                Texto("${f.s.cliente.nombre} quiere conectarse a ${f.s.inmobiliaria.nombre}", Estilo.titulo3, C.texto)
                Texto(Formato.sinViudas("Tu asistente pasa a saber de tus propiedades, contactos, contratos y cobros."), Estilo.subhead, C.texto2)
            } else {
                Texto("Corredor en tu IA", Estilo.titulo3, C.texto)
            }
        }
    }

    @Composable
    fun permiso(p: SolicitudIA.Permiso) {
        val ic = AutorizarIA.iconos[p.permiso] ?: AutorizarIA.iconos.getValue("leer")
        Column {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconoFila(ic.first, ic.second())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(p.titulo, Estilo.filaLista, C.texto)
                    Texto(p.detalle, Estilo.footnote, C.texto2)
                }
                if (p.permiso == "leer") {
                    Box(Modifier.semantics { contentDescription = "Siempre" }) { Llave(true, {}, habilitado = false) }
                } else {
                    Box(Modifier.semantics { contentDescription = p.titulo }) {
                        Llave(p.permiso in elegidos, { v -> elegidos = if (v) elegidos + p.permiso else elegidos - p.permiso })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
        }
    }

    @Composable
    fun lista(s: SolicitudIA) {
        val habilitada = s.habilitada ?: false
        if (!habilitada) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.avisoFondo).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icono("lock", 13f, C.avisoTinta)
                Texto(Formato.sinViudas(if (s.rol == "duenio") "Primero habilitá Corredor en tu IA en Configuración › Integraciones." else "La dueña o el dueño tiene que habilitar Corredor en tu IA en Configuración."),
                    Estilo.footnote, C.avisoTinta, Modifier.weight(1f))
            }
        }
        Texto("QUÉ VA A PODER HACER", Estilo.etiqueta, C.texto2)
        Column(Modifier.fillMaxWidth().tarjeta()) {
            (s.permisos ?: emptyList()).forEach { p -> permiso(p) }
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconoFila("lock", C.texto2)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto("Mandar mensajes, cobrar, liquidar, borrar", Estilo.filaLista, C.texto)
                    Texto((s.nunca ?: "").replace(Regex("^[^:]+:\\s*"), ""), Estilo.footnote, C.texto2)
                }
            }
        }
        val negados = (s.pedidos ?: emptyList()).filter { it !in (s.permitidos ?: emptyList()) }
        if (negados.isNotEmpty()) {
            Texto(Formato.sinViudas("También pidió ${negados.joinToString(" y ") { AutorizarIA.nombres[it] ?: it }}: ${if (s.rol == "duenio") "lo habilitás" else "lo habilita la dueña o el dueño"} en Configuración."),
                Estilo.footnote, C.texto2)
        }
        val a = s.aviso?.takeIf { it.isNotBlank() }
        if (a != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.azulFondo).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icono("shield", 13f, C.azulTinta)
                Texto(Formato.sinViudas(a), Estilo.footnote, C.azulTinta, Modifier.weight(1f))
            }
        }
        val vuelta = s.cliente.vuelta ?: ""
        TextoRico(buildAnnotatedString {
            if (s.cliente.verificado != true) {
                append("Este asistente se registró solo: fijate que sea el que agregaste vos. Al autorizar volvés a ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(vuelta) }
                append(".")
            } else {
                append("Al autorizar volvés a ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(vuelta) }
                append(". Lo desconectás cuando quieras desde Configuración.")
            }
        }, Estilo.footnote, C.texto2)
        errorEnvio?.let { MensajeError(it) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPrincipal(
                if (enviando == "aprobar") "Conectando…" else "Autorizar", { alcance.launch { decidir("aprobar") } },
                habilitado = habilitada && enviando == null,
                contenido = if (enviando == "aprobar") ({
                    Rueda(C.sobreAccion)
                    Texto("Conectando…", Estilo.headline, C.sobreAccion)
                }) else null,
            )
            BotonTexto("Cancelar", { alcance.launch { decidir("rechazar") } }, habilitado = enviando == null)
        }
    }

    LaunchedEffect(Unit) { cargar() }

    PantallaApilada("Conectar tu IA") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            cabecera()
            when (val f = fase) {
                AutorizarIA.Fase.cargando -> EsqueletoFilas(5)
                is AutorizarIA.Fase.lista -> lista(f.s)
                AutorizarIA.Fase.ajena -> EstadoVacio(
                    icono = "person.crop.circle.badge.xmark", titulo = "Esta cuenta no es de esa inmobiliaria",
                    texto = "Entrá con la cuenta con la que usás Corredor en la inmobiliaria y volvé a conectar desde tu asistente.",
                    accion = "Entrar con otra cuenta" to { alcance.launch { estado.salir() }; Unit },
                )
                AutorizarIA.Fase.vencida -> EstadoVacio(
                    icono = "timer", titulo = "Esta solicitud venció o ya se usó",
                    texto = "Volvé a conectar desde tu asistente: te trae acá de nuevo.",
                    accion = "Ir a Corredor" to { estado.ir(Ruta(Pestana.inicio)) },
                )
                is AutorizarIA.Fase.error -> EstadoVacio(
                    icono = "icloud.slash", titulo = "No pudimos traer la solicitud", texto = f.m,
                    accion = "Reintentar" to { alcance.launch { cargar() }; Unit },
                )
            }
        }
    }
}
