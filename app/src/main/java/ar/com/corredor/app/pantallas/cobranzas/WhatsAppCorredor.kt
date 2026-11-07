package ar.com.corredor.app.pantallas.cobranzas

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// Mandar un WhatsApp con el texto ya escrito (recordatorio, recibo, aviso de ajuste, rendición), como
// la web (lib/contratos/MensajeWhatsApp.svelte y la acción `mensaje` de lib/server/contratos.ts):
// - con el WhatsApp de la inmobiliaria conectado, sale desde Corredor (queda en la bandeja y en la
//   ficha); fuera de la ventana de 24 h, con la plantilla aprobada que sirva para eso;
// - sin WhatsApp conectado, o sin plantilla para fuera de 24 h, se abre wa.me con el texto en el
//   WhatsApp del teléfono.

/** Los textos de los mensajes y cómo se nombran los meses y los índices (lib/contratos/comun.ts). */
object TextosCobro {
    val nombreIndice: Map<String, String> = mapOf("icl" to "ICL", "ipc" to "IPC", "uva" to "UVA", "fijo" to "Fijo")

    /** El primer nombre («Lucía Méndez» → «Lucía»). Para un comercio («Panadería Soler»), entero. */
    fun primerNombre(n: String?): String {
        val t = (n ?: "").trim()
        val p = t.split(" ").firstOrNull { it.isNotEmpty() } ?: return ""
        val comercio = listOf("panader", "caf", "kiosco", "almac", "estudio", "local", "farmac", "librer", "ferret").any { p.lowercase().startsWith(it) }
        return if (comercio) t else p
    }

    /** «2026-11-01» o «2026-11» → «noviembre». */
    fun nombreMes(periodo: String): String {
        val d = Fecha.fecha(periodo.take(7) + "-01") ?: return periodo
        return Formato.mesDe(d)
    }

    /** «noviembre» si es de este año; si no, «noviembre 2025». */
    fun mesConAnio(periodo: String, hoy: String = Formato.hoy().toString()): String {
        val m = nombreMes(periodo)
        return if (periodo.take(4) == hoy.take(4)) m else "$m ${periodo.take(4)}"
    }

    /** «2026-11-12» → «12/11» (con el año: «12/11/26»). */
    fun fechaCortita(f: String, anio: Boolean = false): String {
        val p = f.take(10).split("-").filter { it.isNotEmpty() }
        if (p.size != 3) return f
        return if (anio) "${p[2]}/${p[1]}/${p[0].takeLast(2)}" else "${p[2]}/${p[1]}"
    }

    fun hola(nombre: String?): String {
        val n = primerNombre(nombre)
        return "¡Hola${if (n.isEmpty()) "" else " $n"}!"
    }

    fun recordatorio(nombre: String?, periodo: String, importe: Double, moneda: String, vence: String, hoy: String = Formato.hoy().toString()): String {
        val mes = nombreMes(periodo)
        if (vence < hoy) {
            return "${hola(nombre)} Te recordamos que el alquiler de $mes (${Formato.plata(importe, moneda)}) venció el ${fechaCortita(vence)}. Si ya lo pagaste, avisanos y te mandamos el recibo. ¡Gracias!"
        }
        return "${hola(nombre)} Te recordamos que el alquiler de $mes (${Formato.plata(importe, moneda)}) vence el ${fechaCortita(vence)}. ¡Gracias!"
    }

    /** Uno solo por persona aunque deba varios meses («Recordar a todos»). */
    fun recordatorioVarios(nombre: String?, meses: List<String>, total: Double, moneda: String): String {
        val nombres = meses.map { nombreMes(it) }
        val lista = if (nombres.size > 1) nombres.dropLast(1).joinToString(", ") + " y " + (nombres.lastOrNull() ?: "") else (nombres.firstOrNull() ?: "")
        return "${hola(nombre)} Te recordamos que tenés pendiente el alquiler de $lista (${Formato.plata(total, moneda)} en total). Si ya lo pagaste, avisanos y te mandamos el recibo. ¡Gracias!"
    }

    fun sinEsquema(url: String): String = url.replace("https://", "").replace("http://", "")

    fun recibo(nombre: String?, periodo: String, url: String): String =
        "${hola(nombre)} Te mandamos el recibo de ${nombreMes(periodo)}: ${sinEsquema(url)}. ¡Gracias!"

    fun ajuste(nombre: String?, indice: String, fecha: String, desde: Double, hasta: Double, moneda: String): String {
        val i = nombreIndice[indice] ?: "el índice"
        return "${hola(nombre)} Te avisamos que el ${fechaCortita(fecha)} el alquiler se ajusta por $i: pasa de ${Formato.plata(desde, moneda)} a ${Formato.plata(hasta, moneda)}. Cualquier duda, escribinos."
    }

    fun rendicion(nombre: String?, periodo: String, neto: Double, moneda: String, url: String): String =
        "${hola(nombre)} Te mandamos la rendición de ${nombreMes(periodo)}: ${Formato.plata(neto, moneda)}. La ves acá: ${sinEsquema(url)}"

    /** «ICL cada 6 meses», «12 % fijo cada 4 meses», «sin ajuste». */
    fun indiceCada(indice: String?, meses: Int?, porcentaje: Double?): String {
        val m = meses ?: return "sin ajuste"
        if (m <= 0) return "sin ajuste"
        val cada = if (m == 1) "cada mes" else "cada $m meses"
        if (indice == "fijo") return "${numero(porcentaje ?: 0.0)} % fijo $cada"
        return "${indice?.let { nombreIndice[it] } ?: "Ajuste"} $cada"
    }

    /** Un número con decimales sólo si los tiene («12», «1,5»). */
    fun numero(n: Double): String = DecimalFormat("0.###", DecimalFormatSymbols(Formato.argentina)).format(n)
}

/** El envío de un WhatsApp por el número de la inmobiliaria, o el wa.me de respaldo. */
object WhatsAppCorredor {
    @Serializable data class Numero(val conectado: Boolean = false)
    @Serializable data class Conversacion(val id: String, val enVentana: Boolean = false)
    @Serializable data class Plantilla(val nombre: String, val idioma: String, val categoria: String? = null, val parametros: Int = 0)
    @Serializable data class Contacto(val nombre: String? = null, val telefono: String? = null, val email: String? = null)

    /** Para qué es el mensaje: elige la plantilla aprobada cuando la conversación está fuera de 24 h. */
    val plantillas: Map<String, String> = mapOf(
        "recordatorio" to "recordatorio_(pago|cobro|alquiler)|cobr|pago|vencim|alquiler",
        "recibo" to "recib|pago|cobr",
        "ajuste" to "ajuste|aumento|actualizaci|alquiler",
        "rendicion" to "rendici|liquidaci",
        "expensas" to "expensa|liquidaci|consorci|recib",
    )

    /** ¿Está conectado el WhatsApp de la inmobiliaria? (GET /v1/whatsapp/numero; si falla, no). */
    suspend fun conectado(api: Api): Boolean = try {
        val n: Numero = api.get("/v1/whatsapp/numero")
        n.conectado
    } catch (e: CancellationException) { throw e } catch (e: Exception) { false }

    fun waMe(telefono: String?, texto: String): String? {
        val n = (telefono ?: "").filter { it.isDigit() }
        return Uri.parse("https://wa.me/$n").buildUpon().appendQueryParameter("text", texto).build().toString()
    }

    @Serializable private data class Abrir(val contactoId: String)
    @Serializable private data class TextoMensaje(val texto: String)
    @Serializable private data class Datos(val nombre: String, val idioma: String, val parametros: List<String>)
    @Serializable private data class Enviar(val contactoId: String, val plantilla: Datos)

    /** Manda el texto. Devuelve el wa.me si hay que abrirlo en el teléfono (sin WhatsApp conectado o
     *  sin plantilla para fuera de 24 h); `null` si salió desde Corredor. */
    suspend fun mandar(api: Api, contactoId: String, texto: String, para: String, parametros: List<String>): String? {
        val contacto: Contacto = api.get("/v1/contactos/$contactoId")
        val tel = contacto.telefono?.takeIf { it.isNotBlank() }
            ?: throw ErrorApi(estado = 422, codigo = "sin_telefono", titulo = "Sin teléfono", detalle = "${contacto.nombre ?: "Esta persona"} no tiene teléfono cargado.")
        if (!conectado(api)) return waMe(tel, texto)
        val conv: Conversacion = api.enviar("POST", "/v1/whatsapp/conversaciones", Abrir(contactoId))
        if (conv.enVentana) {
            api.hacer("POST", "/v1/whatsapp/conversaciones/${conv.id}/mensajes", TextoMensaje(texto))
            return null
        }
        val todas: List<Plantilla> = try { api.get("/v1/whatsapp/plantillas") } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        val re = Regex(plantillas[para] ?: plantillas.getValue("recordatorio"))
        val sirve: (Plantilla) -> Boolean = { re.containsMatchIn(it.nombre) }
        val p = todas.firstOrNull { it.categoria?.uppercase() == "UTILITY" && sirve(it) } ?: todas.firstOrNull(sirve) ?: return waMe(tel, texto)
        val ps = (0 until maxOf(0, p.parametros)).map { if (it < parametros.size) parametros[it] else "" }
        api.hacer("POST", "/v1/whatsapp/plantillas/enviar", Enviar(contactoId, Datos(p.nombre, p.idioma, ps)))
        return null
    }
}

/** «Mensaje para Lucía» con el texto (se puede retocar) y «Mandar por WhatsApp». Al mandarlo queda la
 *  burbuja con la hora y el tilde, y «Listo». `texto`/`enviado` son los @Binding de iOS (valor y setter). */
@Composable
fun MensajeWhatsApp(
    contactoId: String?,
    telefono: String?,
    nombre: String?,
    texto: String,
    alCambiarTexto: (String) -> Unit,
    /** recordatorio · recibo · ajuste · rendicion (elige la plantilla fuera de 24 h). */
    para: String,
    parametros: List<String> = emptyList(),
    /** Lo que se lee después de mandarlo. */
    listo: String = "Quedó en la ficha y en la bandeja de WhatsApp.",
    enviado: Boolean,
    alEnviado: (Boolean) -> Unit,
    alListo: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var mandando by remember { mutableStateOf(false) }
    var porWaMe by remember { mutableStateOf<String?>(null) }
    var enviadoEn by remember { mutableStateOf<Instant?>(null) }
    var tel by remember { mutableStateOf<String?>(null) }
    var conectado by remember { mutableStateOf(false) }
    val n = TextosCobro.primerNombre(nombre)

    LaunchedEffect(Unit) {
        conectado = WhatsAppCorredor.conectado(estado.api)
        if (telefono == null && contactoId != null) {
            val c: WhatsAppCorredor.Contacto? = estado.api.opcional("/v1/contactos/$contactoId")
            tel = c?.telefono
        }
    }

    suspend fun mandar() {
        val t = texto.trim()
        if (t.isEmpty()) return
        val telefonoActual = telefono ?: tel
        // Sin WhatsApp conectado: wa.me con el teléfono de la persona.
        if (!conectado || contactoId == null) {
            val u = telefonoActual?.takeIf { it.isNotBlank() }?.let { WhatsAppCorredor.waMe(it, t) }
            if (u == null) {
                estado.avisar("${if (n.isEmpty()) "Esta persona" else n} no tiene teléfono cargado.", ok = false)
                return
            }
            abrirUrl(ctx, u)
            porWaMe = u
            enviadoEn = Instant.now()
            alEnviado(true)
            return
        }
        mandando = true
        try {
            val u = WhatsAppCorredor.mandar(estado.api, contactoId, t, para, parametros)
            if (u != null) abrirUrl(ctx, u)
            porWaMe = u
            enviadoEn = Instant.now()
            alEnviado(true)
            Haptico.exito()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detalle = (e as? ErrorApi)?.detalle ?: "Probá de nuevo."
            estado.sugerir("No se mandó · $detalle", "Reintentar") { alcance.launch { mandar() } }
        } finally {
            mandando = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!enviado) {
            Texto("Mensaje para ${if (n.isEmpty()) "el contacto" else n}", Estilo.footnoteFuerte, C.texto2)
            CampoCobro(texto, { t -> alCambiarTexto(if (t.length > 1000) t.take(1000) else t) }, lineasMin = 3, lineasMax = 8,
                modifier = Modifier.fillMaxWidth())
            BotonPrincipal("Mandar por WhatsApp", { alcance.launch { mandar() } },
                habilitado = !(mandando || texto.trim().isEmpty())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mandando) CargandoCobro(C.sobreAccion) else Icono("bubble.left.fill", 17f, C.sobreAccion)
                    Texto(if (mandando) "Mandando…" else "Mandar por WhatsApp", Estilo.headline, C.sobreAccion, lineas = 1)
                }
            }
            extra()
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.okFondo).padding(12.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Texto(texto, Estilo.subhead, C.texto, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto(enviadoEn?.let { Formato.horaDe(it) } ?: "", Estilo.caption, C.texto3)
                    if (porWaMe == null) Icono("checkmark.circle.fill", 13f, C.azulTinta, descripcion = "Enviado")
                }
            }
            Etiqueta(if (porWaMe == null) "Enviado. $listo" else "Lo abrimos en WhatsApp con el mensaje escrito.", "checkmark.circle.fill",
                Estilo.footnote, C.okTinta)
            porWaMe?.let { u ->
                BotonHundido("Abrir WhatsApp de nuevo", { abrirUrl(ctx, u) }, simbolo = "arrow.up.right.square")
            }
            BotonPrincipal("Listo", alListo)
        }
    }
}

/** Lo que hace falta para armar una hoja de WhatsApp (se usa con `.sheet(item:)`). */
data class PedidoMensaje(
    val titulo: String,
    val descripcion: String? = null,
    val contactoId: String?,
    val telefono: String? = null,
    val nombre: String?,
    val texto: String,
    val para: String,
    val parametros: List<String> = emptyList(),
    val listo: String = "Quedó en la ficha y en la bandeja de WhatsApp.",
    /** Salió el mensaje (por Corredor o abriendo wa.me): p. ej., anotar el aviso del ajuste. */
    val alEnviar: (() -> Unit)? = null,
    val id: String = UUID.randomUUID().toString(),
)

/** Un WhatsApp con el texto armado, en una hoja: «Recordar», «Avisarle a Lucía» del ajuste, la
 *  rendición al propietario. Mandarlo es el único toque: la hoja es la vista previa. */
@Composable
fun HojaMensajeWhatsApp(pedido: PedidoMensaje) {
    val cerrar = LocalCerrarHoja.current
    var texto by remember(pedido.id) { mutableStateOf(pedido.texto) }
    var enviado by remember(pedido.id) { mutableStateOf(false) }
    var avisado by remember(pedido.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(C.superficie).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(pedido.titulo, Estilo.tituloHojaChico, C.texto)
            pedido.descripcion?.let { Texto(it, Estilo.texto14, C.texto2) }
        }
        MensajeWhatsApp(pedido.contactoId, pedido.telefono, pedido.nombre, texto, { texto = it }, pedido.para,
            pedido.parametros, pedido.listo, enviado, { e ->
                enviado = e
                if (e && !avisado) {
                    avisado = true
                    pedido.alEnviar?.invoke()
                }
            }, alListo = { cerrar() })
    }
}

/** La hoja de WhatsApp para un pedido (`.hojaMensaje($mensaje)` en iOS). */
@Composable
fun hojaMensaje(pedido: PedidoMensaje?, alCerrar: () -> Unit) {
    HojaDe(pedido, alCerrar, alturas = listOf(Altura.media, Altura.grande)) { p -> HojaMensajeWhatsApp(p) }
}

// MARK: - Piezas de Cobranzas
// Lo que SwiftUI da hecho y estas pantallas comparten (fechas, abrir links, hojas con item, filas de lista,
// campos). Viven acá, en el paquete de Cobranzas, para no depender de piezas de otras áreas.

// Las fechas, la zona y el idioma son los de nucleo/Formato.kt (Formato.zona, Formato.argentina, Fecha.fecha…).

/** `.sheet(item:)`: la hoja se ve mientras `item` no sea null (y mantiene el último mientras se cierra). */
@Composable
internal fun <T : Any> HojaDe(
    item: T?,
    alCerrar: () -> Unit,
    alturas: List<Altura> = listOf(Altura.grande),
    contenido: @Composable ColumnScope.(T) -> Unit,
) {
    val ultimo = remember { arrayOfNulls<Any>(1) }
    if (item != null) ultimo[0] = item
    @Suppress("UNCHECKED_CAST")
    val mostrado = (item ?: ultimo[0]) as T?
    Hoja(visible = item != null, alCerrar = alCerrar, alturas = alturas) {
        if (mostrado != null) contenido(mostrado)
    }
}

/** Una sección de List/Form (.insetGrouped): el encabezado en versalitas, la tarjeta con las filas y el pie. */
@Composable
internal fun SeccionCobro(
    encabezado: String? = null,
    pie: String? = null,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        encabezado?.let { Texto(it.uppercase(Formato.argentina), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp)) }
        Column(Modifier.fillMaxWidth().tarjeta(radio = 12.dp)) { contenido() }
        pie?.let { Texto(it, Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp)) }
    }
}

/** El separador entre filas de una sección (0,5 de alto, con sangría). */
@Composable
internal fun LineaFila(inicio: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inicio).height(0.5.dp).background(C.linea))
}

/** Label(texto, systemImage:). */
@Composable
internal fun Etiqueta(texto: String, simbolo: String, estilo: Estilo = Estilo.body, color: Color = C.texto, modifier: Modifier = Modifier, colorIcono: Color = color) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icono(simbolo, estilo.tamano, colorIcono)
        Texto(texto, estilo, color)
    }
}

/** Un Button de texto suelto (.font(...).foregroundStyle(C.accion)). */
@Composable
internal fun BotonLink(texto: String, estilo: Estilo = Estilo.subheadFuerte, color: Color = C.accion, modifier: Modifier = Modifier, habilitado: Boolean = true, alTocar: () -> Unit) {
    Box(modifier.heightIn(min = 32.dp).alpha(if (habilitado) 1f else 0.4f).clickable(enabled = habilitado, onClick = alTocar), contentAlignment = Alignment.CenterStart) {
        Texto(texto, estilo, color)
    }
}

/** ProgressView() (la ruedita). */
@Composable
internal fun CargandoCobro(color: Color = C.texto3, tam: Dp = 18.dp) {
    val giro = rememberInfiniteTransition(label = "rueda").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "g").value
    Canvas(Modifier.size(tam)) {
        val g = size.minDimension * 0.12f
        drawArc(color, giro, 280f, false, Offset(g / 2, g / 2), Size(size.width - g, size.height - g), style = Stroke(g, cap = StrokeCap.Round))
    }
}

/** Un TextField: con `marco`, el `.campoCorredor()` (superficie, radio 12, borde línea 2, 50 de alto); sin él, el
 *  campo pelado de un Form. `lineasMax` > 1: el TextField(axis: .vertical). */
@Composable
internal fun CampoCobro(
    valor: String,
    alCambiar: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    lineasMin: Int = 1,
    lineasMax: Int = 1,
    estilo: Estilo = Estilo.body,
    marco: Boolean = true,
    habilitado: Boolean = true,
    alinear: TextAlign = TextAlign.Start,
    /** `.submitLabel(.done)` + `.onSubmit`: «Listo» en el teclado corre esto. */
    alListo: (() -> Unit)? = null,
) {
    val forma = RoundedCornerShape(12.dp)
    BasicTextField(
        valor, alCambiar,
        modifier.then(if (marco) Modifier.heightIn(min = 50.dp).clip(forma).background(C.superficie).border(BorderStroke(1.dp, C.linea2), forma)
            .padding(horizontal = 14.dp, vertical = if (lineasMax > 1) 12.dp else 0.dp) else Modifier.heightIn(min = 44.dp)),
        enabled = habilitado,
        textStyle = estilo.ts().copy(color = C.texto, textAlign = alinear),
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = if (alListo != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = if (alListo != null) KeyboardActions(onDone = { alListo() }) else KeyboardActions.Default,
        singleLine = lineasMax == 1,
        minLines = lineasMin, maxLines = lineasMax,
        cursorBrush = SolidColor(C.accion),
        decorationBox = { campo ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (lineasMax > 1) Alignment.TopStart else Alignment.CenterStart) {
                if (valor.isEmpty() && placeholder.isNotEmpty()) Texto(placeholder, estilo, C.texto3, alinear = alinear, modifier = Modifier.fillMaxWidth())
                campo()
            }
        },
    )
}

/** Un renglón tocable de una lista (Button { HStack … } en un List). */
@Composable
internal fun FilaToque(modifier: Modifier = Modifier, habilitado: Boolean = true, alTocar: () -> Unit, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = habilitado, onClick = alTocar).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = contenido,
    )
}

/** Spacer() dentro de un Row. */
@Composable
internal fun RowScope.Relleno() = Spacer(Modifier.weight(1f))

/** Un espacio fijo. */
@Composable
internal fun Hueco(alto: Dp) = Spacer(Modifier.height(alto).width(alto))
