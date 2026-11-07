package ar.com.corredor.app.tema

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

// Swift Charts en Compose (Canvas), con los colores del handoff: barras (simples o apiladas, verticales u horizontales),
// la dona (SectorMark) y el anillo de avance. Las animaciones de entrada respetan «Reducir movimiento».

// MARK: - Movimiento

object Mov {
    private val suave = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    /** Entrada de tarjetas y hojas: suave y corta. */
    fun <T> entrada(): FiniteAnimationSpec<T> = tween(500, easing = suave)
    /** Gráficos y barras que crecen. */
    fun <T> crecer(): FiniteAnimationSpec<T> = tween(900, easing = suave)
    /** Cambios por un toque (elegir, filtrar): el .snappy de iOS. */
    fun <T> toque(): FiniteAnimationSpec<T> = tween(250, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
}

/** Entra subiendo apenas y apareciendo, con un retraso para escalonar una grilla (una sola vez). */
@Composable
fun Modifier.aparecer(retraso: Double = 0.0): Modifier {
    if (!animacionesActivas()) return this
    var visto by rememberSaveable { mutableStateOf(false) }
    val t = remember { Animatable(if (visto) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!visto) { delay((retraso * 1000).toLong()); t.animateTo(1f, Mov.entrada()); visto = true }
    }
    return this.graphicsLayer { alpha = t.value; translationY = (1 - t.value) * 14.dp.toPx() }
}

/** 0 → 1 la primera vez que aparece (1 de entrada con «Reducir movimiento»): el `listo` de los gráficos de iOS. */
@Composable
fun crecer(retraso: Double = 0.1): Float {
    val anima = animacionesActivas()
    val a = remember { Animatable(if (anima) 0f else 1f) }
    LaunchedEffect(Unit) { if (a.value < 1f) { delay((retraso * 1000).toLong()); a.animateTo(1f, Mov.crecer()) } }
    return a.value
}

/** Un número que cambia contando (contentTransition(.numericText()) en iOS). */
@Composable
fun NumeroAnimado(texto: String, estilo: Estilo, color: Color, modifier: Modifier = Modifier, escalaMinima: Float? = null) {
    AnimatedContent(texto, modifier, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "numero") {
        Texto(it, estilo, color, lineas = 1, numeros = true, escalaMinima = escalaMinima)
    }
}

/** Una serie de un gráfico de barras: su nombre (la leyenda), el color y un valor por categoría. */
data class SerieBarras(val nombre: String, val color: Color, val valores: List<Double>)

/** BarMark de Swift Charts: una barra por categoría (`etiquetas`); con varias series, apiladas (como dos BarMark con el
 *  mismo x). `formato` escribe el valor del eje y del globo. Tocar una barra muestra su valor. */
@Composable
fun GraficoBarras(
    etiquetas: List<String>,
    series: List<SerieBarras>,
    modifier: Modifier = Modifier,
    alto: Dp = 160.dp,
    formato: (Double) -> String = { ar.com.corredor.app.nucleo.Formato.numero(it.roundToInt()) },
    radio: Dp = 4.dp,
) {
    val listo = crecer()
    var elegida by remember(etiquetas) { mutableStateOf<Int?>(null) }
    val n = etiquetas.size
    val totales = (0 until n).map { i -> series.sumOf { it.valores.getOrElse(i) { 0.0 } } }
    val maximo = max(totales.maxOrNull() ?: 0.0, 1.0)
    val linea = C.linea
    Column(modifier.fillMaxWidth().semantics { contentDescription = etiquetas.indices.joinToString(", ") { "${etiquetas[it]}: ${formato(totales[it])}" } },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        elegida?.let { i ->
            GloboGrafico(etiquetas.getOrElse(i) { "" }, series.map { LineaGlobo(it.nombre, formato(it.valores.getOrElse(i) { 0.0 }), it.color) })
        }
        Canvas(Modifier.fillMaxWidth().height(alto).pointerTocar(n) { elegida = if (elegida == it) null else it }) {
            val paso = size.width / max(n, 1)
            val ancho = paso * 0.62f
            for (k in 0..3) {
                val y = size.height * k / 3f
                drawLine(linea, Offset(0f, y), Offset(size.width, y), 1f)
            }
            for (i in 0 until n) {
                var base = size.height
                series.forEach { s ->
                    val v = s.valores.getOrElse(i) { 0.0 } * listo
                    val h = (v / maximo * size.height).toFloat()
                    if (h > 0f) {
                        drawRoundRect(if (elegida == null || elegida == i) s.color else s.color.copy(alpha = 0.35f),
                            Offset(paso * i + (paso - ancho) / 2, base - h), Size(ancho, h), CornerRadius(radio.toPx(), radio.toPx()))
                        base -= h
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            etiquetas.forEach { Texto(it, Estilo.chip, C.texto3, Modifier.weight(1f), lineas = 1, alinear = TextAlign.Center) }
        }
        if (series.size > 1) Leyenda(series.map { it.nombre to it.color })
    }
}

/** BarMark horizontal (x: cantidad, y: nombre): un renglón por fila con su barra y el número. */
@Composable
fun GraficoBarrasHorizontales(filas: List<Pair<String, Double>>, color: Color = C.accion, modifier: Modifier = Modifier, formato: (Double) -> String = { ar.com.corredor.app.nucleo.Formato.numero(it.roundToInt()) }) {
    val listo = crecer()
    val maximo = max(filas.maxOfOrNull { it.second } ?: 0.0, 1.0)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filas.forEach { (nombre, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Texto(nombre, Estilo.footnote, C.texto2, Modifier.widthIn(max = 120.dp).width(110.dp), lineas = 1)
                BoxWithConstraints(Modifier.weight(1f).height(14.dp)) {
                    Box(Modifier.width(this@BoxWithConstraints.maxWidth * (v / maximo * listo).toFloat()).height(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
                Texto(formato(v), Estilo.footnoteFuerte, C.texto, numeros = true)
            }
        }
    }
}

/** SectorMark (la dona, innerRadius 0,62, angularInset 2): las partes con su color y lo de adentro en el centro. */
@Composable
fun GraficoDona(partes: List<Triple<String, Double, Color>>, modifier: Modifier = Modifier, tam: Dp = 140.dp, centro: @Composable () -> Unit = {}) {
    val listo = crecer()
    val total = max(partes.sumOf { it.second }, 0.0001)
    val vacio = C.pista
    Box(modifier.size(tam).semantics { contentDescription = partes.joinToString(", ") { "${it.first}: ${it.second.roundToInt()}" } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(tam)) {
            val grosor = size.minDimension * 0.19f
            val r = Size(size.width - grosor, size.height - grosor)
            val o = Offset(grosor / 2, grosor / 2)
            if (partes.all { it.second <= 0 }) drawArc(vacio, 0f, 360f, false, o, r, style = Stroke(grosor))
            var inicio = -90f
            partes.forEach { (_, v, c) ->
                val barrido = (v / total * 360 * listo).toFloat()
                if (barrido > 0f) drawArc(c, inicio + 1f, max(0f, barrido - 2f), false, o, r, style = Stroke(grosor))
                inicio += barrido
            }
        }
        centro()
    }
}

/** El anillo de avance (Gauge / ProgressView circular): de 0 a 1. */
@Composable
fun AnilloAvance(valor: Double, tam: Dp = 76.dp, grosor: Dp = 9.dp, color: Color = C.accion, centro: @Composable () -> Unit = {}) {
    val listo = crecer()
    val pista = C.pista
    Box(Modifier.size(tam).semantics { stateDescription = "${(valor * 100).roundToInt()} %" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(tam)) {
            val g = grosor.toPx()
            val r = Size(size.width - g, size.height - g)
            val o = Offset(g / 2, g / 2)
            drawArc(pista, 0f, 360f, false, o, r, style = Stroke(g))
            drawArc(color, -90f, (valor.coerceIn(0.0, 1.0) * 360 * listo).toFloat(), false, o, r, style = Stroke(g, cap = StrokeCap.Round))
        }
        centro()
    }
}

/** Barra de progreso del handoff (pista y lo lleno en Acción). */
@Composable
fun BarraProgreso(valor: Double, alto: Dp = 10.dp, fondo: Color = C.pistaMeta, color: Color = C.accion) {
    val listo = crecer(0.15)
    BoxWithConstraints(Modifier.fillMaxWidth().height(alto).semantics { stateDescription = "${(valor * 100).roundToInt()} %" }) {
        Box(Modifier.fillMaxWidth().height(alto).clip(Capsula).background(fondo))
        Box(Modifier.width(maxOf(alto, maxWidth * valor.coerceIn(0.0, 1.0).toFloat() * listo)).height(alto).clip(Capsula).background(color))
    }
}

/** La leyenda de un gráfico (chartForegroundStyleScale): un punto de color y el nombre. */
@Composable
fun Leyenda(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (n, c) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Texto(n, Estilo.chip, C.texto2)
            }
        }
    }
}

/** El globo con el valor del punto elegido. */
data class LineaGlobo(val etiqueta: String, val valor: String, val color: Color)

@Composable
fun GloboGrafico(titulo: String, lineas: List<LineaGlobo>, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(10.dp)
    Column(
        modifier.sombra(forma, C.verde800.copy(alpha = 0.12f), 8.dp, y = 3.dp).clip(forma).background(C.superficie).border(1.dp, C.anillo, forma)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (titulo.isNotEmpty()) Texto(titulo, Estilo.chip, C.texto2)
        lineas.forEach { l ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(l.color))
                Texto(l.etiqueta, Estilo.footnote, C.texto2)
                Texto(l.valor, Estilo.footnoteFuerte, C.texto, numeros = true)
            }
        }
    }
}

/** Tocar el gráfico elige la categoría bajo el dedo. */
private fun Modifier.pointerTocar(n: Int, alElegir: (Int) -> Unit): Modifier = this.pointerInput(n) {
    detectTapGestures { o -> if (n > 0) alElegir((o.x / size.width * n).toInt().coerceIn(0, n - 1)) }
}
