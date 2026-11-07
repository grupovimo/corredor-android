package ar.com.corredor.app.tema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import ar.com.corredor.app.nucleo.Formato
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// Las piezas del sistema que iOS da hechas (confirmationDialog, DatePicker, Menu) con el estilo de Corredor.

data class OpcionConfirmacion(val titulo: String, val destructiva: Boolean = false, val accion: () -> Unit)

/** El diálogo de confirmación (confirmationDialog en iOS): título, mensaje, las opciones y «Volver». */
@Composable
/** verTitulo: el titleVisibility de iOS; sin pedirlo, el iPhone esconde el título (queda el mensaje, si hay). */
fun Confirmacion(visible: Boolean, titulo: String, mensaje: String = "", opciones: List<OpcionConfirmacion>, alCerrar: () -> Unit, volver: String = "Volver", verTitulo: Boolean = true) {
    if (!visible) return
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.superficie)) {
                val encabezado = verTitulo || mensaje.isNotEmpty()
                if (encabezado) Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (verTitulo) Texto(titulo, Estilo(14f, Peso.semibold), C.texto2, alinear = TextAlign.Center)
                    if (mensaje.isNotEmpty()) Texto(mensaje, Estilo(13f, Peso.regular), C.texto2, alinear = TextAlign.Center)
                }
                opciones.forEachIndexed { i, o ->
                    if (encabezado || i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.linea))
                    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { o.accion(); alCerrar() }, contentAlignment = Alignment.Center) {
                        Texto(o.titulo, Estilo(18f, Peso.regular), if (o.destructiva) C.error else C.primarioTexto)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(C.superficie).clickable(onClick = alCerrar), contentAlignment = Alignment.Center) {
                Texto(volver, Estilo(18f, Peso.semibold), C.primarioTexto)
            }
        }
    }
}

/** Una opción de un menú desplegable (Menu en iOS). */
data class OpcionDeMenu(val titulo: String, val simbolo: String, val destructiva: Boolean = false, val accion: () -> Unit)

/** Un botón que abre un menú flotante con opciones (Menu { … } label: { … } en iOS). */
@Composable
fun MenuBoton(opciones: List<OpcionDeMenu>, descripcion: String, boton: @Composable () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.clickable { abierto = true }.semantics { contentDescription = descripcion }) { boton() }
        if (abierto) Popup(alignment = Alignment.TopEnd, onDismissRequest = { abierto = false }) {
            Column(Modifier.padding(top = 48.dp).widthIn(min = 250.dp).tarjeta(14.dp)) {
                opciones.forEachIndexed { i, o ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.linea))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { abierto = false; o.accion() }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Texto(o.titulo, Estilo(16f, Peso.regular), if (o.destructiva) C.error else C.texto)
                        if (o.simbolo.isNotEmpty()) Icono(o.simbolo, 17f, if (o.destructiva) C.error else C.texto)
                    }
                }
            }
        }
    }
}

/** El calendario de un mes (DatePicker .graphical en iOS): flechas para cambiar de mes, la semana arranca el lunes,
 *  el día elegido en Acción y los días anteriores a `minimo` apagados. */
@Composable
fun CalendarioMes(fecha: LocalDate, alElegir: (LocalDate) -> Unit, minimo: LocalDate? = null, maximo: LocalDate? = null) {
    var mes by remember { mutableStateOf(YearMonth.from(fecha)) }
    val titulo = DateTimeFormatter.ofPattern("MMMM yyyy", Formato.argentina).format(mes)
    val hoy = Formato.enArgentina(java.time.Instant.now()).toLocalDate()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Texto(Formato.mayuscula(titulo), Estilo(17f, Peso.semibold), C.texto, Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { mes = mes.minusMonths(1) }.semantics { contentDescription = "Mes anterior" }, contentAlignment = Alignment.Center) { Icono("chevron.left", 18f, C.primarioTexto) }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { mes = mes.plusMonths(1) }.semantics { contentDescription = "Mes siguiente" }, contentAlignment = Alignment.Center) { Icono("chevron.right", 18f, C.primarioTexto) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM").forEach { Texto(it, Estilo(11f, Peso.semibold), C.texto2, Modifier.weight(1f), alinear = TextAlign.Center) }
        }
        val primero = mes.atDay(1)
        val vacios = (primero.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val celdas = List(vacios) { null } + (1..mes.lengthOfMonth()).map { mes.atDay(it) }
        celdas.chunked(7).forEach { fila ->
            Row(Modifier.fillMaxWidth()) {
                fila.forEach { d ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val habil = (minimo == null || !d.isBefore(minimo)) && (maximo == null || !d.isAfter(maximo))
                            val on = d == fecha
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(if (on) C.acento else Color.Transparent)
                                    .clickable(enabled = habil) { alElegir(d); Haptico.seleccion() }.alpha(if (habil) 1f else 0.3f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Texto("${d.dayOfMonth}", Estilo(18f, if (on || d == hoy) Peso.semibold else Peso.regular),
                                    if (on) C.sobreAccion else if (d == hoy) C.primarioTexto else C.texto)
                            }
                        }
                    }
                }
                repeat(7 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** La hora (DatePicker .hourAndMinute en iOS): horas y minutos con − y + (de a 5 minutos). */
@Composable
fun SelectorHora(minutos: Int, alCambiar: (Int) -> Unit, paso: Int = 5) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable
        fun boton(s: String, d: String, f: () -> Unit) = Box(Modifier.size(36.dp).clip(CircleShape).background(C.segmentado).clickable { f(); Haptico.seleccion() }.semantics { contentDescription = d }, contentAlignment = Alignment.Center) { Icono(s, 15f) }
        boton("minus", "Una hora menos") { alCambiar(((minutos - 60) + 1440) % 1440) }
        Texto("%d".format(minutos / 60), Estilo(22f, Peso.bold), C.texto, Modifier.width(32.dp), alinear = TextAlign.Center, numeros = true)
        boton("plus", "Una hora más") { alCambiar((minutos + 60) % 1440) }
        Texto(":", Estilo(22f, Peso.bold), C.texto)
        boton("minus", "Menos minutos") { alCambiar(((minutos - paso) + 1440) % 1440) }
        Texto("%02d".format(minutos % 60), Estilo(22f, Peso.bold), C.texto, Modifier.width(32.dp), alinear = TextAlign.Center, numeros = true)
        boton("plus", "Más minutos") { alCambiar((minutos + paso) % 1440) }
    }
}

/** Un Picker de iOS en un renglón: la opción elegida en Acción con ⌃⌄ y un menú para cambiarla. */
@Composable
fun <T> SelectorMenu(opciones: List<Pair<T, String>>, elegido: T, alElegir: (T) -> Unit, descripcion: String = "") {
    val actual = opciones.firstOrNull { it.first == elegido }?.second ?: ""
    MenuBoton(opciones.map { (v, t) -> OpcionDeMenu(t, if (v == elegido) "checkmark" else "circle") { alElegir(v) } }, descripcion.ifEmpty { actual }) {
        Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Texto(actual, Estilo(16f, Peso.regular), C.primarioTexto, lineas = 1)
            Icono("chevron.up.chevron.down", 12f, C.primarioTexto)
        }
    }
}

/** Un DatePicker compacto de iOS en un renglón: el título, la fecha en una pastilla y, al tocarla, el calendario. */
@Composable
fun RenglonFecha(titulo: String, fecha: LocalDate, alCambiar: (LocalDate) -> Unit, minimo: LocalDate? = null) {
    var abierto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(titulo, Estilo(16f, Peso.regular), C.texto, Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.segmentado).clickable { abierto = !abierto }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Texto(DateTimeFormatter.ofPattern("d MMM yyyy", Formato.argentina).format(fecha), Estilo(16f, Peso.regular), if (abierto) C.primarioTexto else C.texto)
            }
        }
        if (abierto) Box(Modifier.padding(bottom = 8.dp)) { CalendarioMes(fecha, { alCambiar(it); abierto = false }, minimo = minimo) }
    }
}

/** Un DatePicker de hora en un renglón (minutos desde la medianoche). */
@Composable
fun RenglonHora(titulo: String, minutos: Int, alCambiar: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Texto(titulo, Estilo(16f, Peso.regular), C.texto, Modifier.weight(1f))
        SelectorHora(minutos, alCambiar)
    }
}

/** El Stepper de iOS (.labelsHidden()): una pastilla de 94 × 32 con − y + separados por una línea. */
@Composable
fun Pasador(valor: Int, alCambiar: (Int) -> Unit, rango: IntRange = 0..999, paso: Int = 1) {
    Row(Modifier.size(94.dp, 32.dp).clip(RoundedCornerShape(8.dp)).background(C.segmentado), verticalAlignment = Alignment.CenterVertically) {
        val menos = valor - paso >= rango.first
        val mas = valor + paso <= rango.last
        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = menos) { alCambiar(valor - paso); Haptico.seleccion() }.semantics { contentDescription = "Menos" },
            contentAlignment = Alignment.Center) { Icono("minus", 15f, if (menos) C.texto else C.texto3) }
        Box(Modifier.size(1.dp, 18.dp).background(C.linea2))
        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = mas) { alCambiar(valor + paso); Haptico.seleccion() }.semantics { contentDescription = "Más" },
            contentAlignment = Alignment.Center) { Icono("plus", 15f, if (mas) C.texto else C.texto3) }
    }
}

/** .contextMenu de iOS: tocar hace lo de siempre y mantener apretado abre el menú con las opciones. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MenuContextual(opciones: List<OpcionDeMenu>, alTocar: () -> Unit, contenido: @Composable () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.combinedClickable(onClick = alTocar, onLongClick = { if (opciones.isNotEmpty()) { Haptico.impacto(); abierto = true } })) { contenido() }
        if (abierto) Popup(alignment = Alignment.BottomEnd, onDismissRequest = { abierto = false }) {
            Column(Modifier.padding(horizontal = 16.dp).widthIn(min = 250.dp).tarjeta(14.dp)) {
                opciones.forEachIndexed { i, o ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.linea))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { abierto = false; o.accion() }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Texto(o.titulo, Estilo(16f, Peso.regular), if (o.destructiva) C.error else C.texto)
                        if (o.simbolo.isNotEmpty()) Icono(o.simbolo, 17f, if (o.destructiva) C.error else C.texto)
                    }
                }
            }
        }
    }
}

/** El Slider de iOS (.tint(C.acento)): la pista, lo lleno en acento y la perilla blanca de 28; se toca o se arrastra. */
@Composable
fun Deslizador(valor: Float, alCambiar: (Float) -> Unit, rango: ClosedFloatingPointRange<Float>, paso: Float = 0f) {
    val acento = C.acento; val pista = C.linea2
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp)) {
        val ancho = constraints.maxWidth.toFloat()
        val perilla = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
        fun desde(x: Float): Float {
            var v = rango.start + ((x - perilla / 2) / (ancho - perilla)).coerceIn(0f, 1f) * (rango.endInclusive - rango.start)
            if (paso > 0f) v = rango.start + Math.round((v - rango.start) / paso) * paso
            return v.coerceIn(rango.start, rango.endInclusive)
        }
        val f = ((valor - rango.start) / (rango.endInclusive - rango.start)).coerceIn(0f, 1f)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(32.dp)
            .pointerInput(rango) { detectTapGestures { o -> alCambiar(desde(o.x)); Haptico.seleccion() } }
            .pointerInput(rango) { detectHorizontalDragGestures { c, _ -> alCambiar(desde(c.position.x)) } }) {
            val y = size.height / 2
            val x = perilla / 2 + f * (size.width - perilla)
            drawLine(pista, androidx.compose.ui.geometry.Offset(perilla / 2, y), androidx.compose.ui.geometry.Offset(size.width - perilla / 2, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(acento, androidx.compose.ui.geometry.Offset(perilla / 2, y), androidx.compose.ui.geometry.Offset(x, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.Black.copy(alpha = 0.12f), perilla / 2 + 1.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y + 1.dp.toPx()))
            drawCircle(Color.White, perilla / 2, androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

/** El color de un texto «#16443b» (null si no es un color). */
fun colorDeHex(texto: String?): Color? = Acento.rgb(texto)?.let { hex(Acento.hex(it)) }

/** El elegidor de color (ColorPicker de iOS): la muestra redonda y, al tocarla, una hoja con la grilla de colores y el
 *  código. `hexActual` y lo que devuelve van como «#rrggbb». */
@Composable
fun SelectorDeColor(titulo: String, hexActual: String, soloMuestra: Boolean = false, alCambiar: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    if (soloMuestra) {
        // ColorPicker con .labelsHidden(): sólo la muestra (50).
        Box(Modifier.size(50.dp).clip(CircleShape).background(colorDeHex(hexActual) ?: Color.White)
            .border(androidx.compose.foundation.BorderStroke(1.dp, C.linea2), CircleShape).clickable { abierto = true }.semantics { contentDescription = "Elegir $titulo" })
    } else Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { abierto = true }.semantics { contentDescription = titulo },
        verticalAlignment = Alignment.CenterVertically) {
        Texto(titulo, Estilo.body16, C.texto, Modifier.weight(1f))
        Box(Modifier.size(28.dp).clip(CircleShape).background(colorDeHex(hexActual) ?: C.accion)
            .border(androidx.compose.foundation.BorderStroke(2.dp, C.linea), CircleShape))
    }
    val grilla = listOf(
        "#0f3530", "#16443b", "#1e5749", "#2f7d5b", "#1f4f8a", "#2f80ed", "#5b3a8c", "#7a5cfa",
        "#b42318", "#d9534f", "#c48a1c", "#f2bb57", "#e9ad44", "#7a5200", "#8b5e34", "#4d5853",
        "#18201d", "#374151", "#9ca3af", "#f3efe6", "#ffffff", "#0ea5e9", "#14b8a6", "#ec4899",
    )
    Hoja(abierto, { abierto = false }, alturas = listOf(Altura.media)) {
        var texto by remember(hexActual) { mutableStateOf(hexActual) }
        val valido = colorDeHex(texto) != null
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto("Cancelar", Estilo.body, C.primarioTexto, Modifier.clickable { abierto = false }.padding(vertical = 8.dp))
            Texto(titulo, Estilo.headline, C.texto, Modifier.weight(1f), alinear = TextAlign.Center, lineas = 1)
            Texto("Listo", Estilo.headline, if (valido) C.primarioTexto else C.texto3,
                Modifier.clickable(enabled = valido) { alCambiar(texto.trim().lowercase().let { if (it.startsWith("#")) it else "#$it" }); abierto = false }.padding(vertical = 8.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            grilla.chunked(8).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    fila.forEach { c ->
                        val elegido = texto.equals(c, ignoreCase = true)
                        Box(Modifier.size(34.dp).clip(CircleShape).background(colorDeHex(c) ?: Color.Gray)
                            .border(androidx.compose.foundation.BorderStroke(if (elegido) 3.dp else 1.dp, if (elegido) C.texto else C.linea), CircleShape)
                            .clickable { texto = c; Haptico.seleccion() }.semantics { contentDescription = c })
                    }
                }
            }
            Campo("Código") { Entrada(texto, { texto = it }, "#16443b", Modifier.weight(1f), sinCorrector = true, mayusculas = androidx.compose.ui.text.input.KeyboardCapitalization.None) }
        }
    }
}
