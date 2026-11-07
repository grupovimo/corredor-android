package ar.com.corredor.app.pantallas.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import coil3.compose.SubcomposeAsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

// Clon de Pantallas/Mas/ConfigPiezas.swift: piezas que comparten las pantallas de configuración (Inmobiliaria,
// Equipo, Integraciones, Proveedores, Respuestas, Corredor en tu IA): los errores por campo de la API, la fecha
// relativa de la web, el avatar con foto y el aviso de rol.
//
// Al final, las piezas de Android que reemplazan a lo que SwiftUI da hecho en estas pantallas (List/Form agrupado,
// la barra de una hoja con NavigationStack, Label, .alert). Las usan sólo las pantallas de Más, Web, Secciones e
// Informes.

/** Un error de la API con sus errores por campo (`issues: [{path, message}]` del problem+json). */
class ErrorCampos(val api: ErrorApi, val campos: Map<String, String>) : Exception(api.detalle)

/** Un pedido que, si la API lo rechaza, trae también el error de cada campo (para mostrarlo debajo de él, como la
 *  web). Devuelve la respuesta cruda. */
suspend inline fun <reified C> Api.pedirConCampos(metodo: String, ruta: String, cuerpo: C?, envio: String? = null): String {
    try {
        return String(pedir(metodo, ruta, cuerpo = cuerpo?.let { Api.json.encodeToString(it).toByteArray() }, envio = envio))
    } catch (e: ErrorApi) {
        throw ErrorCampos(e, e.campos)
    }
}

/** El error de un pedido con campos: el general va al aviso de abajo; devuelve los de cada campo. */
fun Estado.camposDe(e: Throwable): Map<String, String> {
    if (e is ErrorCampos) {
        if (e.campos.isEmpty()) avisar(e.api) else avisar(e.api.detalle.ifEmpty { e.api.titulo }, ok = false)
        return e.campos
    }
    avisar(e)
    return emptyMap()
}

/** La fecha relativa de la web (`relativa` de lib/ui/formato.ts): «recién», «hace 2 h», «en 5 días», «mañana»,
 *  «ayer», o «12 nov» si está lejos. */
object Relativa {
    fun de(f: Instant, ahora: Instant = Instant.now()): String {
        val seg = ((f.toEpochMilli() - ahora.toEpochMilli()) / 1000.0).roundToLong()
        val futuro = seg > 0
        val a = abs(seg)
        fun envolver(t: String) = if (futuro) "en $t" else "hace $t"
        if (a < 3) return "recién"
        if (a < 60) return envolver("$a s")
        if (a < 3600) return envolver("${a / 60} min")
        val zona = ZoneId.systemDefault()
        val dias = ChronoUnit.DAYS.between(ahora.atZone(zona).toLocalDate(), f.atZone(zona).toLocalDate()).toInt()
        if (a < 6 * 3600 || dias == 0) return envolver("${a / 3600} h")
        if (dias == 1) return "mañana"
        if (dias == -1) return "ayer"
        if (abs(dias) < 7) return envolver("${abs(dias)} días")
        return diaMes(f.atZone(zona))
    }

    /** «12 nov» (setLocalizedDateFormatFromTemplate("d MMM") en es_AR, sin el punto de la abreviatura). */
    private val diaMesBase: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Formato.argentina)
    fun diaMes(z: java.time.temporal.TemporalAccessor): String = diaMesBase.format(z).replace(".", "")

    /** «18 nov» (la fecha corta de las licencias, `fechaCorta` de la web). */
    fun corta(dia: String): String {
        val p = dia.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return dia
        return runCatching { diaMes(LocalDate.of(p[0], p[1], p[2])) }.getOrDefault(dia)
    }
}

/** La foto de la persona o sus iniciales. */
@Composable
fun AvatarPersona(nombre: String, foto: String?, tam: Dp = 40.dp) {
    if (foto != null && foto.startsWith("http")) {
        SubcomposeAsyncImage(
            foto, null, Modifier.size(tam).clip(CircleShape), contentScale = ContentScale.Crop,
            loading = { AvatarIniciales(nombre, tam) }, error = { AvatarIniciales(nombre, tam) },
        )
    } else {
        val (fondo, tinta) = coloresAvatar(nombre)
        AvatarIniciales(nombre, tam, fondo = fondo, tinta = tinta)
    }
}

/** El error de un campo, debajo de él (la API o la validación de acá). */
@Composable
fun ErrorDeCampo(texto: String?) {
    val t = texto?.trim()
    if (!t.isNullOrEmpty()) Box(Modifier.padding(top = 2.dp)) { MensajeError(t) }
}

/** «Tu rol es Agente: podés ver estos datos…»: el candado arriba de lo que sólo cambia el dueño. */
@Composable
fun AvisoRol(texto: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.hundida).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Icono("lock", 17f, C.texto2)
        Texto(Formato.sinViudas(texto), Estilo.footnote, C.texto2, Modifier.weight(1f))
    }
}

/** Un ícono de color dentro de una fila de Form, para los rubros y los permisos. */
@Composable
fun IconoFila(icono: String, color: Color) {
    Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { Icono(icono, 17f, color) }
}

/** Una confirmación de las que la web pide en una hoja roja («¿Sacás a Lara del equipo?»). */
data class Confirmacion(
    val titulo: String,
    val texto: String,
    val boton: String,
    val no: String = "No",
    val hacer: () -> Unit,
    val id: String = UUID.randomUUID().toString(),
)

/** La confirmación roja de la web, como diálogo del sistema (`.confirmacion($c)` en iOS). */
@Composable
fun VistaConfirmacion(c: Confirmacion?, alCerrar: () -> Unit) {
    Alerta(
        visible = c != null, titulo = c?.titulo ?: "", mensaje = c?.texto ?: "", alCerrar = alCerrar,
        botones = listOf(
            BotonAlerta(c?.no ?: "No", RolBoton.cancelar),
            BotonAlerta(c?.boton ?: "", RolBoton.destructivo) { c?.hacer?.invoke() },
        ),
    )
}

// MARK: - Lo que SwiftUI da hecho (sólo Android)
