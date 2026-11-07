package ar.com.corredor.app.pantallas.entrada

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.FechaApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.nonEmpty
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale

// Clon de Pantallas/Entrada/Invitacion.swift.

/** Lo que se ve del link de una invitación antes de aceptarla (GET /v1/invitaciones/vista?token=). */
@Serializable
data class VistaInvitacion(val inmobiliaria: String, val invita: String? = null, val rol: String, val email: String, val venceEn: FechaApi)

/** Los `static func` de `VerInvitacion` en iOS. */
object VerInvitacion {
    /** Qué ve cada rol (lo que dice la invitación antes de aceptar). */
    fun queVe(rol: String, invita: String): String = when (rol) {
        "duenio" -> "Vas a ver toda la inmobiliaria: la cartera, el equipo, las cobranzas y la configuración."
        "lector" -> "Vas a poder mirar la cartera y la agenda, sin cambiar nada."
        else -> "Vas a ver tu cartera y lo que no tiene dueño. Las cobranzas y la configuración las maneja $invita."
    }

    fun tokenValido(t: String): Boolean = Regex("^[A-Za-z0-9_-]{20,100}$").matches(t)

    /** «12/11» (día y mes, como la web). */
    fun diaMes(f: Instant): String {
        val d = Formato.diaDe(f)
        return String.format(Locale.US, "%02d/%02d", d.dayOfMonth, d.monthValue)
    }
}

/** La barra de una pantalla de la entrada: la nativa si está apilada; dentro de una hoja (`enHoja`), la propia con el
 *  título al medio y, si hay, «Cerrar» o «‹» a la izquierda. */
@Composable
private fun BarraInvitacion(titulo: String, enHoja: Boolean, izquierda: Pair<String, () -> Unit>?) {
    if (!enHoja) {
        BarraDe(titulo, izquierda = izquierda?.let { (t, f) -> { BotonBarraTexto(t, f) } })
        return
    }
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
        izquierda?.let { (t, f) ->
            Box(Modifier.align(Alignment.CenterStart).heightIn(min = 44.dp).clickable(onClick = f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Texto(t, Estilo.body, C.accion)
            }
        }
        Texto(titulo, Estilo.headline, C.texto, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun BotonBarraTexto(t: String, f: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clickable(onClick = f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Texto(t, Estilo.body, C.accion) }
}

/** El link de una invitación (07 · 4C, como /invitacion/<token> de la web): quién invita, a qué inmobiliaria, con qué rol
 *  y qué va a ver, a qué mail es y cuándo vence. Abrirla no la consume: se acepta con el botón. «No soy yo» sale y vuelve
 *  a entrar con la cuenta invitada, sin perderla. `enHoja`: dentro de una hoja (la del link o «Pegar el link»), con su
 *  propia barra; `alCerrar`: volver (apilada) o cerrar la hoja. */
@Composable
fun VerInvitacion(token: String, enHoja: Boolean = LocalEstado.current.invitacionAbierta != null, alCerrar: (() -> Unit)? = null) {
    val estado = LocalEstado.current
    val cerrarHoja = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var vista by remember { mutableStateOf<VistaInvitacion?>(null) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var pegando by remember { mutableStateOf(false) }

    /** En la hoja de un link, la cierra; apilada (desde «Pegar el link»), vuelve. */
    fun cerrarTodo() {
        if (estado.invitacionAbierta != null) estado.invitacionAbierta = null else (alCerrar ?: cerrarHoja)()
    }

    suspend fun cargar() {
        if (!VerInvitacion.tokenValido(token)) {
            errorCarga = "El link de la invitación no es válido. Pedile a quien te invitó que te mande otro."
            return
        }
        try {
            vista = estado.api.get("/v1/invitaciones/vista", listOf("token" to token))
            errorCarga = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            errorCarga = if (e.estado in 1..499) e.detalle.ifEmpty { e.titulo } else e.detalle
        } catch (e: Exception) {
            errorCarga = "Algo salió mal. Probá de nuevo."
        }
    }

    fun aceptar() {
        error = null
        enviando = true
        alcance.launch {
            try {
                estado.aceptarInvitacion(token)
                cerrarTodo()
                // Quien se suma a un equipo pasa por la bienvenida antes del Inicio (como la web), cuando ya se cerró
                // esta hoja.
                estado.tarea {
                    delay(700)
                    estado.bienvenidaAbierta = true
                }
            } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                error = e.detalle.ifEmpty { e.titulo }
            } catch (e: Exception) {
                error = "Algo salió mal. Probá de nuevo."
            }
            enviando = false
        }
    }

    LaunchedEffect(token) { cargar() }

    if (pegando) {
        BackHandler { pegando = false }
        PegarInvitacion(enHoja = enHoja, alCerrar = { pegando = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        BarraInvitacion("Invitación", enHoja, if (estado.invitacionAbierta != null) ("Cerrar" to { cerrarTodo() }) else if (enHoja && alCerrar != null) ("‹" to alCerrar) else null)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FondoApp(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val v = vista
                val ec = errorCarga
                if (v != null) {
                    val quien = v.invita?.nonEmpty ?: "Alguien del equipo"
                    val primero = quien.split(" ").firstOrNull { it.isNotEmpty() } ?: quien
                    val fondo = C.fondo
                    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(C.verde700).border(3.dp, fondo, CircleShape), contentAlignment = Alignment.Center) {
                            Texto(Formato.iniciales(quien), Estilo(20f, Peso.bold), C.oro)
                        }
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(C.hundida).border(3.dp, fondo, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Texto(v.inmobiliaria.take(1).uppercase(), Estilo(20f, Peso.bold), C.texto)
                        }
                    }
                    Texto(Formato.sinViudas("$quien te invitó a ${v.inmobiliaria}"), Estilo.titulo, C.texto)
                    Column(Modifier.fillMaxWidth().tarjeta()) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("person.text.rectangle", 17f, C.accion) }
                            TextoRico(buildAnnotatedString {
                                append("Rol: ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(ElegirInmobiliaria.rol(v.rol)) }
                            }, Estilo.body16, C.texto)
                        }
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("eye", 17f, C.accion) }
                            Texto(Formato.sinViudas(VerInvitacion.queVe(v.rol, primero)), Estilo.subhead, C.texto)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Texto("Entrás como ${estado.usuario?.email ?: ""} ·", Estilo.footnote, C.texto2)
                        Texto("no soy yo", Estilo.footnoteFuerte, C.accion, Modifier.clickable { estado.tarea { estado.otraCuenta(token) } })
                    }
                    if ((estado.usuario?.email ?: "").lowercase() != v.email.lowercase()) {
                        TextoRico(buildAnnotatedString {
                            append("La invitación es para ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(v.email) }
                            append(". Si es otra cuenta tuya, entrá con esa.")
                        }, Estilo.footnote, C.avisoTinta, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.avisoFondo).padding(12.dp))
                    }
                    error?.let { Texto(it, Estilo.footnote, C.error) }
                    BotonPrincipal(if (enviando) "Entrando…" else "Aceptar y entrar", { aceptar() }, habilitado = !enviando)
                    Texto("La invitación vence el ${VerInvitacion.diaMes(v.venceEn)}.", Estilo.footnote, C.texto3, Modifier.fillMaxWidth(), alinear = TextAlign.Center)
                } else if (ec != null) {
                    IconoTono("link.badge.plus", Tono.rojo, tam = 56.dp)
                    Texto("No pudimos abrir la invitación", Estilo.titulo, C.texto)
                    Texto(Formato.sinViudas(ec), Estilo.subhead, C.texto2)
                    BotonPrincipal("Pegar otro link", { pegando = true })
                    BotonSecundario("Ir a Corredor", { cerrarTodo() }, lleno = true)
                } else EsqueletoFilas(filas = 3)
            }
        }
    }
}

/** Los `static func` de `PegarInvitacion` en iOS. */
object PegarInvitacion {
    /** El token del link (`…/invitacion/<token>`) o el token solo. */
    fun token(link: String): String? {
        val l = link.trim()
        val m = Regex("(?:^|/invitacion/)([A-Za-z0-9_-]{20,100})/?(?:[?#].*)?$").find(l) ?: return null
        var t = m.value
        t.indexOf("/invitacion/").takeIf { it >= 0 }?.let { t = t.substring(it + "/invitacion/".length) }
        t.indexOfFirst { it == '?' || it == '#' }.takeIf { it >= 0 }?.let { t = t.substring(0, it) }
        if (t.endsWith("/")) t = t.dropLast(1)
        return if (VerInvitacion.tokenValido(t)) t else null
    }
}

/** «Aceptar una invitación» (como /invitaciones de la web): pegar el link que llegó por mail. Muestra de qué inmobiliaria
 *  es antes de entrar. Sirve con o sin inmobiliaria. */
@Composable
fun PegarInvitacion(enHoja: Boolean = false, alCerrar: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val cerrarHoja = LocalCerrarHoja.current
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    val cerrar = alCerrar ?: cerrarHoja

    fun ver() {
        val t = PegarInvitacion.token(link)
        if (t == null) {
            error = "Ese link no parece de una invitación. Copialo entero del mail."
            return
        }
        error = null
        token = t
    }

    val t = token
    if (t != null) {
        BackHandler { token = null }
        VerInvitacion(t, enHoja = enHoja, alCerrar = { token = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        BarraInvitacion("Invitación", enHoja, null)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FondoApp(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                IconoTono("envelope", Tono.verde, tam = 56.dp)
                Texto("Aceptar una invitación", Estilo.titulo, C.texto)
                Texto(Formato.sinViudas("Pegá el link que te llegó por mail. Te mostramos de qué inmobiliaria es antes de entrar."), Estilo.subhead, C.texto2)
                val forma = RoundedCornerShape(M.rControl)
                CampoCorredor(link, { link = it }, "corredor.com.ar/invitacion/…", teclado = KeyboardType.Uri, mayusculas = KeyboardCapitalization.None,
                    accion = ImeAction.Go, alAccion = { ver() },
                    modifier = Modifier.border(1.dp, if (error == null) Color.Transparent else C.error, forma)) {
                    // PasteButton de iOS: pega lo copiado y lo abre.
                    Box(Modifier.size(36.dp).clip(CircleShape).background(C.hundida).clickable {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val x = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                        if (!x.isNullOrEmpty()) { link = x; ver() }
                    }.semantics { contentDescription = "Pegar" }, contentAlignment = Alignment.Center) { Icono("doc.on.clipboard", 15f, C.accion) }
                }
                error?.let { Texto(it, Estilo.footnote, C.error) }
                BotonPrincipal("Ver la invitación", { ver() }, habilitado = link.trim().isNotEmpty())
                Texto("Volver a Corredor", Estilo.subheadFuerte, C.accion, Modifier.fillMaxWidth().clickable { cerrar() }, alinear = TextAlign.Center)
            }
        }
    }
}
