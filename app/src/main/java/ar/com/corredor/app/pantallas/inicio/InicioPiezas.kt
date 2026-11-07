package ar.com.corredor.app.pantallas.inicio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ar.com.corredor.app.R
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.*
import ar.com.corredor.app.pantallas.cartera.*
import ar.com.corredor.app.pantallas.cobranzas.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
// Las piezas propias, explícitas: otras áreas tienen las suyas con el mismo nombre.
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.io.File
import java.time.Duration
import java.time.Instant

// Clon de Pantallas/Inicio/InicioPiezas.swift.
// Los bloques del Inicio que el panel web tiene (lib/inicio/*) armados con las piezas de la app: lo
// urgente, «Tu día», la agenda de hoy, las sugerencias, cómo viene el mes, las cobranzas, el equipo,
// los primeros pasos, el próximo paso, las acciones rápidas, «Tu web» y el número caído.

// MARK: - Ayudas de plataforma (lo que no está en tema/: la ruedita es `Rueda` y el margen negativo, `sangrar`)

/** La rayita de 0,5 entre filas (`.overlay(alignment: .top) { Rectangle().fill(C.linea).frame(height: 0.5) }`). */
@Composable
internal fun Modifier.lineaArriba(si: Boolean = true): Modifier {
    if (!si) return this
    val c = C.linea
    return this.drawBehind { drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx()) }
}

/** Text con `.strikethrough(si)`. */
@Composable
internal fun TextoTachado(texto: String, estilo: Estilo, color: Color, tachado: Boolean, modifier: Modifier = Modifier, lineas: Int = Int.MAX_VALUE) {
    BasicText(
        texto, modifier,
        style = estilo.ts().copy(color = color, textDecoration = if (tachado) TextDecoration.LineThrough else null),
        maxLines = lineas, overflow = TextOverflow.Ellipsis,
    )
}

/** `@Environment(\.dismiss)` de una pantalla apilada: vuelve atrás (saca la pantalla de la pila). */
@Composable
internal fun rememberVolver(): () -> Unit {
    val estado = LocalEstado.current
    return remember(estado) { { estado.volver() } }
}

/** Los ajustes de la app (UserDefaults / @AppStorage en iOS). */
internal fun ajustes(ctx: Context) = ctx.getSharedPreferences("corredor", Context.MODE_PRIVATE)

// MARK: - Lo urgente

/** «Lo urgente» (de noche, «Para mañana»): cobros, consultas, reservas, preguntas y WhatsApp, cada uno con
 *  su botón. Las consultas sin dueño se asignan («Asignar», la dueña) o se toman («Tomarla») ahí mismo. */
@Composable
fun UrgentesInicio(
    urgentes: List<Urgente>,
    titulo: String,
    nuevas: Set<String>,
    /** A quién se le puede dar una consulta (la dueña); null: es para quien mira («Tomarla»). */
    personas: List<Pair<String, String>>?,
    asignar: (Urgente, Pair<String, String>?) -> Unit,
    cobrar: (CobranzaInicio) -> Unit,
    recordar: (CobranzaInicio) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque(texto = titulo, cuenta = "${urgentes.size}")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            urgentes.forEachIndexed { i, u ->
                key(u.id) {
                    Box(Modifier.lineaArriba(i > 0)) {
                        FilaUrgente(u = u, nueva = u.id in nuevas, personas = personas, asignar = asignar, cobrar = cobrar, recordar = recordar)
                    }
                }
            }
        }
    }
}

@Composable
fun FilaUrgente(
    u: Urgente,
    nueva: Boolean,
    personas: List<Pair<String, String>>?,
    asignar: (Urgente, Pair<String, String>?) -> Unit,
    cobrar: (CobranzaInicio) -> Unit,
    recordar: (CobranzaInicio) -> Unit,
) {
    val estado = LocalEstado.current
    val fondo by animateColorAsState(if (nueva) C.avisoFondo.copy(alpha = C.avisoFondo.alpha * 0.6f) else Color.Transparent, tween(1200), label = "nueva")
    val menu = buildList {
        val c = u.cobranza
        if (c != null && estado.escribe) {
            add(OpcionDeMenu("Recordar", "bubble.left") { recordar(c) })
            add(OpcionDeMenu("Registrar cobro", "dollarsign.circle") { cobrar(c) })
        }
        u.ir?.let { ir -> add(OpcionDeMenu("Abrir", "arrow.up.right") { estado.abrir(ir) }) }
    }
    MenuContextual(opciones = menu, alTocar = { u.ir?.let { estado.abrir(it) } }) {
        Row(
            Modifier.fillMaxWidth().background(fondo).heightIn(min = 66.dp).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoTono(icono = u.icono, tono = u.tono)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto(u.titulo, Estilo.filaLista, C.texto, lineas = 2)
                if (u.detalle.isNotEmpty()) {
                    Texto(u.detalle, if (u.detalleTono == null) Estilo.footnote else Estilo.footnoteFuerte, u.detalleTono?.tinta ?: C.texto2, lineas = 2)
                }
            }
            FinUrgente(u, personas, asignar, cobrar, recordar)
        }
    }
}

@Composable
private fun FinUrgente(
    u: Urgente,
    personas: List<Pair<String, String>>?,
    asignar: (Urgente, Pair<String, String>?) -> Unit,
    cobrar: (CobranzaInicio) -> Unit,
    recordar: (CobranzaInicio) -> Unit,
) {
    val estado = LocalEstado.current
    val c = u.cobranza
    when {
        c != null -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (estado.escribe) {
                Tocable(alTocar = { recordar(c) }, descripcion = "Recordar") {
                    Box(Modifier.size(40.dp, 44.dp), contentAlignment = Alignment.Center) { Icono("bubble.left", 17f, C.accion, peso = Peso.semibold) }
                }
                PastillaUrgente("Registrar cobro") { cobrar(c) }
            } else {
                Chevron()
            }
        }
        u.consultaContacto != null && estado.escribe -> {
            if (personas != null && estado.esDuenio) {
                // Menu { Section("Asignar a") { … } }
                MenuBoton(
                    opciones = listOf(OpcionDeMenu("Asignar a", "", encabezado = true)) +
                        personas.map { p -> OpcionDeMenu(p.second, "person") { asignar(u, p) } },
                    descripcion = "Asignar",
                ) { EtiquetaPastilla("Asignar") }
            } else {
                PastillaUrgente("Tomarla") { asignar(u, null) }
            }
        }
        u.tipo == Urgente.Tipo.reserva -> PastillaUrgente("Ver la reserva") { u.ir?.let { estado.abrir(it) } }
        u.tipo == Urgente.Tipo.whatsapp || u.tipo == Urgente.Tipo.pregunta || u.tipo == Urgente.Tipo.consulta ->
            PastillaUrgente("Responder") { u.ir?.let { estado.abrir(it) } }
        else -> Chevron()
    }
}

@Composable
private fun PastillaUrgente(t: String, hacer: () -> Unit) {
    Tocable(alTocar = hacer) { EtiquetaPastilla(t) }
}

@Composable
private fun EtiquetaPastilla(t: String) {
    Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.height(34.dp).clip(CircleShape).background(C.hundida).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Texto(t, Estilo(14f, Peso.semibold), C.accion, lineas = 1)
        }
    }
}

/** El error de un bloque solo (7C «Error parcial»): el resto del Inicio se ve igual. */
@Composable
fun ErrorBloque(titulo: String, texto: String? = null, salida: Pair<String, () -> Unit>? = null, reintentar: () -> Unit) {
    Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        IconoTono(icono = "exclamationmark.triangle", tono = Tono.rojo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto(titulo, Estilo.filaLista, C.texto)
            if (texto != null) Texto(Formato.sinViudas(texto), Estilo.footnote, C.texto2)
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Texto("Reintentar", Estilo.footnoteFuerte, C.accion, Modifier.clickable(onClick = reintentar))
                if (salida != null) Texto(salida.first, Estilo.footnoteFuerte, C.accion, Modifier.clickable(onClick = salida.second))
            }
        }
    }
}

// MARK: - Número caído

private data class PiezaCaido(val icono: String, val titulo: String, val texto: String, val boton: String, val oro: Boolean)

/** «WhatsApp se desconectó a las 9:12», por qué (lo que dijo Meta) y la salida (el dueño reconecta). */
@Composable
fun NumeroCaidoInicio(numero: NumeroWhatsApp) {
    val estado = LocalEstado.current
    val cuando: String = numero.estadoEn?.let { f ->
        val d = CuentasInicio.diasEntre(f, Instant.now())
        if (d == 0) "a las ${Formato.horaDe(f)}" else if (d == 1) "ayer a las ${Formato.horaDe(f)}" else "el ${Formato.fechaLarga(f)}"
    } ?: ""
    val c = cuando
    val p = when (numero.estado) {
        "restringido" -> PiezaCaido(
            "nosign", "Meta restringió el WhatsApp${if (c.isEmpty()) "" else " $c"}",
            "Lo que mandes no sale hasta que Meta lo resuelva; lo que te escriban entra igual.", "Revisar", false,
        )
        "limitado" -> {
            val desde = if (c.isEmpty()) "" else " desde " + (if (c.startsWith("a las")) c.drop(2).trim() else c)
            PiezaCaido(
                "speedometer", "WhatsApp está limitado$desde",
                "Por ahora salen menos mensajes por día. Contestá a tiempo y sin repetir plantillas para que vuelva a la normalidad.", "Ver el número", true,
            )
        }
        else -> PiezaCaido(
            "link", "WhatsApp se desconectó${if (c.isEmpty()) "" else " $c"}",
            "Los mensajes que lleguen mientras tanto no se pierden: entran cuando reconectes.", "Reconectar", false,
        )
    }
    val tono = if (p.oro) Tono.oro else Tono.rojo
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(M.rTarjeta)).background(tono.fondo).semantics(mergeDescendants = true) {}.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        IconoTono(icono = p.icono, tono = tono)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Texto(p.titulo, Estilo.filaLista, C.texto)
            Texto(Formato.sinViudas("${numero.estadoMotivo?.let { "$it " } ?: ""}${p.texto}"), Estilo.footnote, C.texto2)
            if (estado.esDuenio) {
                BotonAccion(p.boton, { estado.abrir(ASeccion(clave = "integraciones")) }, Modifier.widthIn(max = 180.dp).padding(top = 4.dp))
            }
        }
    }
}

// MARK: - Tu día y la agenda

/** «Tu día» (de noche, «Quedó pendiente de hoy»): las citas de hoy con su casilla. */
@Composable
fun TuDiaInicio(titulo: String, citas: List<Cita>, hechas: Set<String>, tildar: (Cita, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque(texto = titulo)
        Column(Modifier.fillMaxWidth().tarjeta()) {
            citas.forEachIndexed { i, c ->
                key(c.id) {
                    val hecha = c.id in hechas
                    Tocable(alTocar = { tildar(c, !hecha) }, modifier = Modifier.lineaArriba(i > 0).semantics { selected = hecha }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icono(if (hecha) "checkmark.square.fill" else "square", 22f, if (hecha) C.accion else C.texto3)
                            if (!hecha) Texto(CuentasInicio.horaCorta(c.empiezaEn), Estilo.subheadFuerte, C.texto, numeros = true)
                            TextoTachado(c.titulo ?: c.donde, Estilo.filaLista, if (hecha) C.texto3 else C.texto, hecha, Modifier.weight(1f), lineas = 2)
                            if (!hecha) Icono(c.icono, 17f, C.texto3)
                        }
                    }
                }
            }
        }
    }
}

/** «Hoy en la agenda del equipo» / «Hoy en tu agenda» (de noche, mañana): hora, qué y quién; la que
 *  viene enseguida, resaltada con «en 25 min». */
@Composable
fun AgendaHoyInicio(titulo: String, citas: List<Cita>) {
    val estado = LocalEstado.current
    val ahora = Instant.now()
    val proxima: String? = citas.firstOrNull { it.estado == "pendiente" && it.empiezaEn > ahora }?.id

    fun que(c: Cita): String {
        val p = c.propiedadTitulo ?: return c.titulo ?: c.donde
        val extra = c.contactoNombre?.let { " con $it" } ?: (c.lugar?.let { l -> if (l.contains(p) || p.contains(l)) null else " · $l" } ?: "")
        return "${c.tipoTexto} $p$extra"
    }

    fun quien(c: Cita): String {
        val n = c.agenteNombre?.nonEmpty ?: return "Sin asignar"
        val nombre = if (c.agenteId == estado.usuario?.id) "Vos" else (n.split(" ").firstOrNull { it.isNotEmpty() } ?: n)
        val falta = Duration.between(Instant.now(), c.empiezaEn).seconds
        val cuando = if (c.id == proxima && falta > 0 && falta <= 3600) " · ${CuentasInicio.relativa(c.empiezaEn)}" else ""
        return nombre + cuando
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // HStack(alignment: .firstTextBaseline)
        Row {
            Box(Modifier.weight(1f).alignByBaseline()) { TituloBloque(texto = titulo) }
            Texto("Ver agenda", Estilo.footnoteFuerte, C.accion, Modifier.alignByBaseline().clickable { estado.abrir(AAgendaDia()) })
        }
        Column(Modifier.fillMaxWidth().tarjeta()) {
            citas.forEachIndexed { i, c ->
                key(c.id) {
                    val ya = c.id == proxima
                    Row(
                        Modifier.fillMaxWidth().lineaArriba(i > 0)
                            .background(if (ya) C.avisoFondo.copy(alpha = C.avisoFondo.alpha * 0.5f) else Color.Transparent)
                            .clickable { estado.abrir(ACita(id = c.id)) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                    ) {
                        Texto(Formato.horaDe(c.empiezaEn), Estilo.subheadFuerte, if (ya) C.avisoTinta else C.texto, Modifier.width(48.dp), numeros = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextoTachado(que(c), Estilo.filaLista, if (c.estado == "hecha") C.texto3 else C.texto, c.estado == "hecha", lineas = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                c.agenteNombre?.nonEmpty?.let { AvatarIniciales(nombre = it, tam = 22.dp) }
                                Texto(quien(c), Estilo.footnote, if (ya) C.avisoTinta else C.texto2)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Próxima visita

/** La tarjeta verde de la próxima visita: la foto (y cuántas tiene), quién, qué busca y el precio;
 *  «Cómo llegar», WhatsApp y llamar. De noche es la primera de mañana, con «Confirmar por WhatsApp». */
@Composable
fun TarjetaProximaVisita(p: ProximaVisita, noche: Boolean = false) {
    val estado = LocalEstado.current
    val oscuro = LocalOscuro.current
    val cita = p.cita
    val quien = cita.contactoNombre ?: ""
    val que = run {
        val op = if (p.operacion == "venta") "compra" else if (p.operacion == "alquiler") "quiere alquilar" else ""
        listOfNotNull(quien.nonEmpty, op.nonEmpty, p.precio?.let { Formato.plata(it, p.moneda ?: "ARS") }).joinToString(" · ")
    }
    val kicker = if (noche) {
        "Primera visita · mañana ${CuentasInicio.horaCorta(cita.empiezaEn)}"
    } else {
        val falta = CuentasInicio.relativa(cita.empiezaEn)
        "Próxima visita · ${if (falta.startsWith("en")) "$falta · " else ""}${CuentasInicio.horaCorta(cita.empiezaEn)}"
    }
    val forma = RoundedCornerShape(22.dp)
    val oro = C.oro
    Box(
        Modifier.fillMaxWidth().sombraE3().clip(forma)
            .clickable { estado.abrir(ACita(id = cita.id)) },
    ) {
        // De noche, grafito con el borde oro (propuesta A): el verde lleno teñía todo el Inicio.
        if (oscuro) {
            Box(Modifier.matchParentSize().background(C.superficie).border(1.dp, oro.copy(alpha = 0.4f), forma))
        } else {
            FondoTarjetaVerde(Modifier.matchParentSize())
        }
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val f = p.foto
            if (f != null && !noche) {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp))) {
                    Foto(url = urlDeFoto(f, 800), modifier = Modifier.matchParentSize())
                    if (p.fotos > 1) {
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icono("photo.on.rectangle", 12f, Color.White)
                            Texto("${p.fotos}", Estilo(12f, Peso.bold), Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            ConIcono(kicker, if (noche) "calendar" else "clock", Estilo(13f, Peso.bold), C.oro)
            Texto(cita.propiedadTitulo ?: cita.titulo ?: cita.donde, Estilo(24f, Peso.bold, -0.02f), Color.White, Modifier.padding(top = 4.dp))
            if (que.isNotEmpty()) Texto(que, Estilo.subhead, hex(0xCFE0D8))
            Box(Modifier.padding(top = 10.dp)) { BotonesProximaVisita(p, noche, quien) }
        }
    }
}

@Composable
private fun BotonesProximaVisita(p: ProximaVisita, noche: Boolean, quien: String) {
    val estado = LocalEstado.current
    val cita = p.cita
    fun llegando() {
        val t = p.telefono
        if (t != null) {
            val nombre = quien.split(" ").firstOrNull { it.isNotEmpty() } ?: ""
            Afuera.whatsapp(t, texto = "Hola $nombre, estoy llegando a ${cita.donde}.")
        } else {
            estado.avisar("La visita no tiene un contacto con teléfono.", ok = false)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (noche) {
            if (p.telefono != null) {
                BotonOro("Confirmar por WhatsApp", "bubble.left.fill", Modifier.weight(1f)) {
                    val nombre = quien.split(" ").firstOrNull { it.isNotEmpty() }?.let { " $it" } ?: ""
                    Afuera.whatsapp(p.telefono, texto = "Hola$nombre, te confirmo la visita de mañana a las ${CuentasInicio.horaCorta(cita.empiezaEn)} en ${cita.propiedadTitulo ?: cita.titulo ?: ""}.")
                }
            }
        } else {
            BotonOro("Cómo llegar", "location.fill", Modifier.weight(1f)) { Afuera.mapas(p.direccion ?: cita.lugar ?: cita.propiedadTitulo ?: "") }
            Tocable(alTocar = { llegando() }, modifier = Modifier.weight(1f), descripcion = "WhatsApp a $quien: estoy llegando") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(R.drawable.logo_whatsapp), null, Modifier.size(18.dp), contentScale = ContentScale.Fit)
                    Texto("Estoy llegando", Estilo(15f, Peso.semibold), Color.White, lineas = 1, escalaMinima = 0.8f)
                }
            }
        }
        if (p.telefono != null) {
            Tocable(alTocar = { Afuera.llamar(p.telefono) }, descripcion = "Llamar a $quien") {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icono("phone.fill", 18f, Color.White, peso = Peso.semibold) }
            }
        }
    }
}

@Composable
private fun BotonOro(t: String, icono: String, modifier: Modifier = Modifier, hacer: () -> Unit) {
    Tocable(alTocar = hacer, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.verticalGradient(0f to hex(0xF7CB73), 0.55f to hex(0xF2BB57), 1f to hex(0xE9AD44))),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icono(icono, 15f, hex(0x1A1405))
            Texto(t, Estilo(15f, Peso.bold), hex(0x1A1405), lineas = 1, escalaMinima = 0.8f)
        }
    }
}

// MARK: - Sugerencias

/** «Encontramos por vos» (o «3 sugerencias de hoy»): cada una con su botón, que la aplica y lleva a
 *  la cosa, y la cruz para descartarla (con «Deshacer»). */
@Composable
fun SugerenciasInicio(sugerencias: List<SugerenciaInicio>, aplicar: (SugerenciaInicio) -> Unit, descartar: (SugerenciaInicio) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.avisoFondo).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConIcono(if (sugerencias.size > 1) "${sugerencias.size} sugerencias de hoy" else "Encontramos por vos", "sparkles", Estilo(13f, Peso.bold), C.avisoTinta)
        sugerencias.forEachIndexed { i, s ->
            key(s.id) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        TextoRico(frase(s), Estilo(16f, Peso.regular), C.texto, Modifier.weight(1f))
                        Tocable(alTocar = { descartar(s) }, descripcion = "Descartar") {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icono("xmark", 14f, C.texto2, peso = Peso.bold) }
                        }
                    }
                    Tocable(alTocar = { aplicar(s) }) {
                        Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (i == 0) C.accion else C.hundida).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Texto(s.boton.first, Estilo(15f, Peso.semibold), if (i == 0) C.sobreAccion else C.accion) }
                    }
                }
                if (i < sugerencias.size - 1) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            }
        }
    }
}

/** «{accion} **{destacado}**. {motivo}.» */
private fun frase(s: SugerenciaInicio): AnnotatedString = buildAnnotatedString {
    append(s.accion?.let { "$it " } ?: "")
    s.destacado?.nonEmpty?.let { d ->
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(d) }
        append(".")
    }
    s.motivo?.nonEmpty?.let { m -> append(" " + m + (if (m.endsWith(".")) "" else ".")) }
}

// MARK: - Cómo viene el mes

/** «Cómo viene noviembre»: cada objetivo con su barra y la raya de dónde debería ir hoy, el ritmo y los
 *  días hábiles que quedan. Sin objetivos, los cierres del mes. */
@Composable
fun ComoVieneInicio(ritmos: List<RitmoInicio>, cerradas: Int) {
    val estado = LocalEstado.current
    fun cifra(r: RitmoInicio): String = r.moneda?.let { Formato.plata(r.avance, it) } ?: Formato.numero(r.avance.toInt())
    val habiles = CuentasInicio.diasHabilesRestantes()
    Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row {
            Texto("Cómo viene ${Formato.mesDe(Instant.now())}", Estilo(17f, Peso.bold), C.texto, Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Texto("quedan ${Formato.cantidad(habiles, "día hábil", "días hábiles")}", Estilo.footnote, C.texto2, Modifier.alignByBaseline())
        }
        if (ritmos.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto("Cierres", Estilo.subheadFuerte, C.texto, Modifier.alignByBaseline())
                Spacer(Modifier.weight(1f))
                Texto(Formato.numero(cerradas), Estilo(20f, Peso.bold), C.texto, Modifier.alignByBaseline())
                Texto("este mes", Estilo.footnote, C.texto2, Modifier.alignByBaseline())
            }
        }
        ritmos.forEachIndexed { i, r -> key(r.id) { FilaRitmo(r, primera = i == 0, cifra = cifra(r)) } }
        val boton = if (estado.esDuenio) (if (ritmos.isEmpty()) "Poner los objetivos del mes" else "Ver objetivos por agente") else "Ver tus objetivos"
        Texto(boton, Estilo.footnoteFuerte, C.accion, Modifier.clickable { estado.abrir(ASeccion(clave = "objetivos")) })
    }
}

@Composable
private fun FilaRitmo(r: RitmoInicio, primera: Boolean, cifra: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto(r.nombre, Estilo.subheadFuerte, C.texto, Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Texto(cifra, Estilo(17f, Peso.bold), C.texto, Modifier.alignByBaseline())
            Texto("de ${Formato.numero(r.meta.toInt())}", Estilo.footnote, C.texto2, Modifier.alignByBaseline())
        }
        val pista = C.hundida
        val relleno = if (!r.adelante && r.moneda == null) C.errorTinta else C.accion
        val raya = C.texto
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val alto = 10.dp.toPx()
            val y = (size.height - alto) / 2
            drawRoundRect(pista, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, alto), androidx.compose.ui.geometry.CornerRadius(alto / 2))
            val ancho = (size.width * r.ancho / 100).toFloat()
            if (ancho > 0) drawRoundRect(relleno, Offset(0f, y), androidx.compose.ui.geometry.Size(ancho, alto), androidx.compose.ui.geometry.CornerRadius(alto / 2))
            val x = (size.width * r.raya / 100).toFloat() - 1.dp.toPx()
            drawRect(raya, Offset(x, 0f), androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
        }
        if (r.moneda == null) {
            if (r.adelante) {
                ConIcono("Adelante del ritmo${if (primera) " · la raya marca dónde deberían ir hoy" else ""}", "chart.line.uptrend.xyaxis", Estilo.footnote, C.okTinta)
            } else {
                ConIcono("Atrás del ritmo: ${if (r.falta == 1) "falta 1" else "faltan ${Formato.numero(r.falta)}"} para ir al día", "chart.line.downtrend.xyaxis", Estilo.footnote, C.errorTinta)
            }
        }
    }
}

// MARK: - Cobranzas del mes

/** «Cobranzas de noviembre»: cuánto se cobró de lo que hay que cobrar (en la moneda de la mayoría de
 *  los alquileres), quién debe, qué vence, las rendiciones listas para mandar o «Todo cobrado». */
@Composable
fun CobranzasMesInicio(cobradas: List<CobranzaInicio>, pendientes: List<CobranzaInicio>, rendiciones: Pair<Int, PanelInicio.Rendicion?>?) {
    val estado = LocalEstado.current
    val moneda = run {
        val n = mutableMapOf<String, Int>()
        for (c in cobradas + pendientes) n[c.moneda] = (n[c.moneda] ?: 0) + 1
        n.maxByOrNull { it.value }?.key ?: "ARS"
    }
    fun suma(cs: List<CobranzaInicio>): Double = cs.filter { it.moneda == moneda }.sumOf { it.monto }
    val cobrado = suma(cobradas)
    val atrasadas = pendientes.filter { it.estado == "atrasado" }
    val deben = suma(atrasadas)
    val total = cobrado + suma(pendientes)
    val prox = pendientes.filter { it.estado == "pendiente" }.sortedBy { it.vence }
    val primero = prox.firstOrNull()?.vence
    val n = primero?.let { p -> prox.count { it.vence == p } } ?: 0
    Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tocable(alTocar = { estado.abrir(ACobranzas()) }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Texto("Cobranzas de ${Formato.mesDe(Instant.now())}", Estilo(17f, Peso.bold), C.texto)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Texto(Formato.plata(cobrado, moneda), Estilo(22f, Peso.extrabold), C.texto, Modifier.alignByBaseline())
                    Texto("cobrado de ${Formato.numero(total.toInt())}", Estilo.footnote, C.texto2, Modifier.alignByBaseline())
                }
                val verde = hex(0x2F9E6F)
                val rojo = C.errorTinta
                val pista = C.hundida
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    drawRoundRect(pista, cornerRadius = r)
                    val a = if (total > 0) (size.width * cobrado / total).toFloat() else 0f
                    if (a > 0) drawRoundRect(verde, size = androidx.compose.ui.geometry.Size(a, size.height), cornerRadius = r)
                    if (deben > 0) {
                        val b = if (total > 0) (size.width * deben / total).toFloat() else 0f
                        // HStack(spacing: 2): el espacio va aunque la parte cobrada mida cero.
                        val x = a + 2.dp.toPx()
                        if (b > 0) drawRoundRect(rojo, Offset(x, 0f), androidx.compose.ui.geometry.Size(b, size.height), r)
                    }
                }
            }
        }
        Column {
            if (atrasadas.isNotEmpty()) {
                FilaCobranzaMes("exclamationmark.circle", Tono.rojo, "${Formato.cantidad(atrasadas.size, "inquilino debe", "inquilinos deben")} ${Formato.plata(deben, moneda)}", "Ver quién") { estado.abrir(ACobranzas()) }
            }
            if (primero != null) {
                FilaCobranzaMes("clock", Tono.neutro, "${Formato.numero(n)} ${if (n == 1) "vence" else "vencen"} el ${CuentasInicio.elDia(primero)}", "Avisar") { estado.abrir(ACobranzas()) }
            }
            val rd = rendiciones
            if (rd != null && rd.first > 0) {
                FilaCobranzaMes("doc.text", Tono.neutro, "${Formato.cantidad(rd.first, "rendición lista", "rendiciones listas")} para mandar", "Revisar") {
                    val p = rd.second
                    if (p != null) estado.abrir(ARendicion(contrato = p.contratoId, id = p.id)) else estado.abrir(ASeccion(clave = "rendiciones"))
                }
            }
            if (atrasadas.isEmpty() && primero == null && (rendiciones?.first ?: 0) == 0) {
                FilaCobranzaMes("checkmark.circle", Tono.verde, "Todo cobrado", "Ver") { estado.abrir(ACobranzas()) }
            }
        }
    }
}

@Composable
private fun FilaCobranzaMes(icono: String, tono: Tono, texto: String, boton: String, hacer: () -> Unit) {
    Tocable(alTocar = hacer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono(icono, 17f, if (tono == Tono.neutro) C.texto2 else tono.tinta)
            Texto(texto, Estilo.subhead, C.texto, Modifier.weight(1f))
            Texto(boton, Estilo.footnoteFuerte, C.accion)
        }
    }
}

// MARK: - El equipo hoy

object EquipoHoyInicio {
    val nombres: Map<String, Pair<String, String>> = mapOf(
        "visita" to ("visita" to "visitas"), "llamado" to ("llamado" to "llamados"), "firma" to ("firma" to "firmas"),
        "tasacion" to ("tasación" to "tasaciones"), "otro" to ("cita" to "citas"),
    )
    val ausente: Map<String, String> = mapOf(
        "licencia" to "de licencia", "vacaciones" to "de vacaciones", "enfermedad" to "de licencia por enfermedad", "tramite" to "de trámite",
    )
}

/** «El equipo hoy»: qué tiene cada uno (citas por tipo, mensajes sin responder o la licencia) y desde
 *  cuándo; «al día» si no debe nada. */
@Composable
fun EquipoHoyInicio(personas: List<PersonaHoy>) {
    fun que(p: PersonaHoy): String {
        p.licencia?.let { l -> return EquipoHoyInicio.ausente[l.first] ?: "de licencia" }
        if (p.sinResponder > 0) return Formato.cantidad(p.sinResponder, "mensaje sin responder", "mensajes sin responder")
        val citas = p.citas.map { (t, n) ->
            val nom = EquipoHoyInicio.nombres[t] ?: ("cita" to "citas")
            Formato.cantidad(n, nom.first, nom.second)
        }
        return if (citas.isEmpty()) "sin citas hoy" else citas.joinToString(" y ")
    }

    fun vuelve(d: String): String {
        val p = d.split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return ""
        val mesHoy = Formato.hoy().monthValue
        return if (p[1] == mesHoy) "vuelve el ${p[2]}" else "vuelve el ${p[2]}/${p[1]}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque(texto = "El equipo hoy")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            personas.forEachIndexed { i, p ->
                key(p.id) {
                    Row(
                        Modifier.fillMaxWidth().lineaArriba(i > 0).heightIn(min = 56.dp).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarIniciales(
                            nombre = p.nombre, tam = 36.dp,
                            fondo = if (p.licencia == null) C.hundida else Tono.rojo.fondo,
                            tinta = if (p.licencia == null) C.texto else Tono.rojo.tinta,
                        )
                        TextoRico(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p.nombre) }
                                append(" · " + que(p))
                            },
                            Estilo.subhead, C.texto, Modifier.weight(1f), lineas = 2,
                        )
                        val l = p.licencia
                        val desde = p.desde
                        if (l != null) {
                            Texto(vuelve(l.second), Estilo.footnote, C.texto2)
                        } else if (p.sinResponder > 0 && desde != null) {
                            Texto(Formato.hace(desde), Estilo.footnoteFuerte, C.avisoTinta)
                        } else {
                            Texto("al día", Estilo.footnote, C.texto2)
                        }
                        if (p.telefono != null) {
                            Tocable(alTocar = { Afuera.whatsapp(p.telefono) }, descripcion = "WhatsApp con ${p.nombre}") {
                                Box(Modifier.size(40.dp, 44.dp), contentAlignment = Alignment.Center) {
                                    Image(painterResource(R.drawable.logo_whatsapp), null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Primeros pasos y el próximo paso

/** «Primeros pasos»: todos, con el avance («2/7»), tachados los hechos y el botón del que sigue.
 *  «Ocultar» lo guarda en la API; al completarlo se va solo, con «¡Listo! Corredor ya está andando». */
@Composable
fun PrimerosPasos(pasos: List<PasoInicio>, hacer: (PasoInicio) -> Unit, ocultar: (Boolean) -> Unit) {
    var ido by remember { mutableStateOf(false) }
    val hechos = pasos.count { it.hecho }
    val completo = pasos.isNotEmpty() && hechos == pasos.size
    val siguiente = pasos.indexOfFirst { !it.hecho }
    if (completo && !ido) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.crema).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoTono(icono = "party.popper", tono = Tono.oro, tam = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto("¡Listo! Corredor ya está andando", Estilo.filaLista, C.texto)
                Texto(Formato.sinViudas("Completaste los primeros pasos."), Estilo.footnote, C.texto2)
            }
        }
        LaunchedEffect(Unit) {
            delay(6000)
            ido = true
            ocultar(true)
        }
    } else if (!completo) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.crema).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val linea = C.linea
                val accion = C.accion
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        val g = 5.dp.toPx()
                        drawCircle(linea, radius = size.minDimension / 2 - g / 2, style = Stroke(g))
                        drawArc(
                            accion, -90f, 360f * hechos / maxOf(1, pasos.size), false,
                            topLeft = Offset(g / 2, g / 2), size = androidx.compose.ui.geometry.Size(size.width - g, size.height - g),
                            style = Stroke(g, cap = StrokeCap.Round),
                        )
                    }
                    Texto("$hechos/${pasos.size}", Estilo(12f, Peso.bold), C.texto)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("Primeros pasos", Estilo.filaLista, C.texto, Modifier.semantics { heading() })
                    Texto(
                        if (hechos == 0) "Arrancá por el primero" else "Te ${if (pasos.size - hechos == 1) "falta 1" else "faltan ${pasos.size - hechos}"}",
                        Estilo.footnote, C.texto2,
                    )
                }
                Box(Modifier.heightIn(min = 44.dp).clickable { ocultar(false) }, contentAlignment = Alignment.Center) {
                    Texto("Ocultar", Estilo.footnoteFuerte, C.accion)
                }
            }
            Column {
                pasos.forEachIndexed { i, p ->
                    key(p.id) { Box(Modifier.lineaArriba(i > 0)) { FilaPaso(p, ahora = i == siguiente, hacer = hacer) } }
                }
            }
        }
    }
}

@Composable
private fun FilaPaso(p: PasoInicio, ahora: Boolean, hacer: (PasoInicio) -> Unit) {
    if (p.hecho) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono("checkmark.circle.fill", 17f, C.accion)
            TextoTachado(p.texto, Estilo.subhead, C.texto3, true)
        }
    } else {
        Tocable(alTocar = { hacer(p) }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icono("circle", 17f, if (ahora) C.accion else C.texto3)
                Texto(p.texto, if (ahora) Estilo.subheadFuerte else Estilo.subhead, C.texto, Modifier.weight(1f))
                Texto(p.boton, Estilo.footnoteFuerte, C.accion)
            }
        }
    }
}

/** «Para hacer» cuando no hay nada urgente (de noche, «Para mañana»): el próximo paso útil. */
@Composable
fun ProximoPasoInicio(proximos: List<ProximoInicio>, noche: Boolean, hacer: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque(texto = if (noche) "Para mañana" else "Para hacer", cuenta = if (noche) "nada urgente" else "nada urgente hoy")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            proximos.forEachIndexed { i, p ->
                key(p.id) {
                    Tocable(alTocar = { hacer(p.clave) }, modifier = Modifier.lineaArriba(i > 0)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconoTono(icono = p.icono, tono = if (i == 0) Tono.oro else Tono.verde)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Texto(p.titulo, Estilo.filaLista, C.texto)
                                Texto(p.detalle, Estilo.footnote, C.texto2)
                            }
                            Texto(p.boton, Estilo.footnoteFuerte, C.accion)
                            Chevron()
                        }
                    }
                }
            }
            val s = proximos.firstOrNull()?.secundario
            if (s != null) {
                Box(Modifier.fillMaxWidth().lineaArriba().heightIn(min = 44.dp).clickable { hacer("importar") }, contentAlignment = Alignment.Center) {
                    Texto(s, Estilo.footnoteFuerte, C.accion)
                }
            }
        }
    }
}

/** Las acciones rápidas, siempre a la vista: en grilla de tres, ordenadas por lo que la persona más usa. */
@Composable
fun AccionesInicio(acciones: List<AccionInicio>, hacer: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        acciones.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { a ->
                    key(a.id) {
                        Tocable(
                            alTocar = { hacer(a.clave) }, modifier = Modifier.weight(1f),
                            descripcion = if (a.clave == "web") "Ver mi web (se abre aparte)" else a.texto,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 104.dp).tarjeta(16.dp).padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                            ) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (a.clave == "captar") C.oro else C.hundida),
                                    contentAlignment = Alignment.Center,
                                ) { Icono(a.icono, 21f, if (a.clave == "captar") hex(0x1A1405) else C.accion, peso = Peso.medium) }
                                Texto(a.texto, Estilo(12f, Peso.semibold), C.texto, Modifier.padding(horizontal = 4.dp), lineas = 2, alinear = TextAlign.Center, escalaMinima = 0.85f)
                            }
                        }
                    }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// MARK: - Tu web

/** El link de la web de la inmobiliaria, siempre a mano: copiar, WhatsApp, compartir y el código QR para
 *  la vidriera o la tarjeta. */
@Composable
fun CompartirWebInicio(web: String, nombre: String, alAbrir: () -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var verQR by remember { mutableStateOf(false) }
    val dominio = web.replace("https://", "").replace("http://", "")
    val mensaje = "Mirá las propiedades de ${nombre.nonEmpty ?: "la inmobiliaria"}: $web"
    Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoTono(icono = "globe", tono = Tono.verde, tam = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto("Tu web", Estilo.filaLista, C.texto)
                Texto(
                    dominio, Estilo(14f, Peso.medium), C.accion,
                    Modifier.clickable {
                        alAbrir()
                        abrirUrl(ctx, web)
                    },
                    lineas = 1, escalaMinima = 0.8f, monoespaciado = true,
                )
            }
            Tocable(alTocar = { verQR = !verQR }, descripcion = "Ver el código QR") {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("qrcode", 22f, C.accion, peso = Peso.medium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonAccion("Copiar link", {
                copiar(ctx, web)
                Haptico.exito()
                estado.avisar("Copiaste el link de tu web")
            }, Modifier.weight(1f), simbolo = "doc.on.doc")
            BotonHundido("WhatsApp", { Afuera.whatsapp(null, texto = mensaje) }, Modifier.weight(1f), simbolo = "bubble.left")
            Tocable(alTocar = { estado.compartirTexto("Mirá las propiedades de $nombre $web", asunto = nombre) }, descripcion = "Compartir") {
                Box(Modifier.size(48.dp, 44.dp).clip(RoundedCornerShape(12.dp)).background(C.hundida), contentAlignment = Alignment.Center) {
                    Icono("square.and.arrow.up", 17f, C.texto, peso = Peso.semibold)
                }
            }
        }
        AnimatedVisibility(verQR, enter = fadeIn(), exit = fadeOut()) {
            val qr = remember(web) { CodigoQR.imagen(web) }
            if (qr != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MenuContextual(
                        opciones = listOf(OpcionDeMenu("Compartir el QR", "square.and.arrow.up") { CodigoQR.compartir(ctx, qr, dominio) }),
                        alTocar = {},
                    ) {
                        Image(
                            qr.asImageBitmap(), "Código QR de $dominio",
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp).size(132.dp),
                            filterQuality = FilterQuality.None,
                        )
                    }
                    Texto(
                        Formato.sinViudas("Para la vidriera, las tarjetas o el cartel: lo escanean con la cámara y entran a tu web."),
                        Estilo.footnote, C.texto2, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** El código QR de un texto (zxing; CoreImage en iOS), nítido para imprimir. */
object CodigoQR {
    fun imagen(texto: String, escala: Int = 10): Bitmap? = runCatching {
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
        val ancho = m.width * escala
        val alto = m.height * escala
        val b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        for (y in 0 until alto) for (x in 0 until ancho) {
            b.setPixel(x, y, if (m.get(x / escala, y / escala)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        b
    }.getOrNull()

    /** ShareLink(item: Image(qr)) en iOS: el PNG por el selector del sistema. */
    fun compartir(ctx: Context, qr: Bitmap, dominio: String) {
        runCatching {
            val carpeta = File(ctx.cacheDir, "compartir").apply { mkdirs() }
            val f = File(carpeta, "qr.png")
            f.outputStream().use { qr.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", f)
            val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "QR de $dominio").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "QR de $dominio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
