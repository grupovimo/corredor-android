package ar.com.corredor.app.tema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

// Map de MapKit: los mosaicos de CARTO (OpenStreetMap) con Coil, encuadrados en los puntos (como el .region que arma
// iOS con un 30 % de aire), que se arrastran con el dedo, con un marcador por punto y el punto azul de «vos».

/** Un punto del mapa (Annotation): su id, dónde y su nombre. */
data class PuntoMapa(val id: String, val lat: Double, val lng: Double, val nombre: String = "")

private const val TESELA = 256.0

private fun xDe(lng: Double, z: Int) = (lng + 180) / 360 * TESELA * 2.0.pow(z)
private fun yDe(lat: Double, z: Int): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * TESELA * 2.0.pow(z)
}

/** El mapa. `marcador` dibuja cada punto (elegido o no); tocar uno lo elige, tocar el mapa suelta la elección. */
@Composable
fun Mapa(
    puntos: List<PuntoMapa>,
    modifier: Modifier = Modifier,
    elegido: String? = null,
    alElegir: (String?) -> Unit = {},
    yo: Pair<Double, Double>? = null,
    marcador: @Composable (PuntoMapa, Boolean) -> Unit = { _, e -> MarcadorMapa(e) },
) {
    val oscuro = LocalOscuro.current
    BoxWithConstraints(modifier.clipToBounds().background(C.hundida)) {
        val anchoDp = maxWidth.value.toDouble()
        val altoDp = maxHeight.value.toDouble().takeIf { it.isFinite() && it > 0 } ?: 300.0
        val todos = puntos.map { it.lat to it.lng } + listOfNotNull(yo)
        val (z, centro) = remember(todos, anchoDp, altoDp) { encuadre(todos, anchoDp, altoDp) }
        var corrido by remember(todos) { mutableStateOf(Offset.Zero) }
        val densidad = LocalDensity.current.density
        val cx = xDe(centro.second, z) - corrido.x / densidad
        val cy = yDe(centro.first, z) - corrido.y / densidad
        val izq = cx - anchoDp / 2
        val arriba = cy - altoDp / 2
        Box(Modifier.matchParentSize().pointerInput(todos) {
            detectDragGestures { c, d -> c.consume(); corrido += d }
        }.pointerInput(todos) { detectTapGestures { alElegir(null) } })
        val n = 2.0.pow(z).toInt()
        val tx0 = floor(izq / TESELA).toInt(); val tx1 = floor((izq + anchoDp) / TESELA).toInt()
        val ty0 = floor(arriba / TESELA).toInt().coerceAtLeast(0); val ty1 = floor((arriba + altoDp) / TESELA).toInt().coerceAtMost(n - 1)
        val estilo = if (oscuro) "dark_all" else "rastertiles/voyager"
        for (tx in tx0..tx1) for (ty in ty0..ty1) {
            val x = ((tx % n) + n) % n
            AsyncImage("https://a.basemaps.cartocdn.com/$estilo/$z/$x/$ty@2x.png", null,
                Modifier.offset { IntOffset(((tx * TESELA - izq) * densidad).roundToInt(), ((ty * TESELA - arriba) * densidad).roundToInt()) }.size(TESELA.dp),
                contentScale = ContentScale.FillBounds)
        }
        yo?.let { (la, lo) ->
            val px = xDe(lo, z) - izq; val py = yDe(la, z) - arriba
            Box(Modifier.offset { IntOffset(((px - 9) * densidad).roundToInt(), ((py - 9) * densidad).roundToInt()) }.size(18.dp).clip(CircleShape)
                .background(Color.White).padding(3.dp).clip(CircleShape).background(hex(0x2F80ED)))
        }
        puntos.forEach { p ->
            val px = xDe(p.lng, z) - izq; val py = yDe(p.lat, z) - arriba
            Box(Modifier.offset { IntOffset((px * densidad).roundToInt(), (py * densidad).roundToInt()) }) {
                Box(Modifier.offset(x = (-20).dp, y = (-40).dp).size(40.dp).pointerInput(p.id) { detectTapGestures { alElegir(p.id) } },
                    contentAlignment = Alignment.BottomCenter) { marcador(p, p.id == elegido) }
            }
        }
        Texto("© OpenStreetMap · CARTO", Estilo(9f, Peso.regular), C.texto3, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

/** El marcador de siempre: un círculo en Acción (más grande si está elegido). */
@Composable
fun MarcadorMapa(elegido: Boolean) {
    val t = if (elegido) 26.dp else 18.dp
    Box(Modifier.size(t).clip(CircleShape).background(C.accion).border(3.dp, Color.White, CircleShape))
}

/** El zoom y el centro para que entren todos los puntos con un 30 % de aire (0,012° como mínimo, como iOS). */
private fun encuadre(puntos: List<Pair<Double, Double>>, ancho: Double, alto: Double): Pair<Int, Pair<Double, Double>> {
    if (puntos.isEmpty()) return 12 to (-34.6037 to -58.3816)
    val minLat = puntos.minOf { it.first }; val maxLat = puntos.maxOf { it.first }
    val minLng = puntos.minOf { it.second }; val maxLng = puntos.maxOf { it.second }
    val centro = (minLat + maxLat) / 2 to (minLng + maxLng) / 2
    val dLat = max((maxLat - minLat) * 1.3, 0.012); val dLng = max((maxLng - minLng) * 1.3, 0.012)
    var z = 18
    while (z > 2) {
        val w = xDe(centro.second + dLng / 2, z) - xDe(centro.second - dLng / 2, z)
        val h = yDe(centro.first - dLat / 2, z) - yDe(centro.first + dLat / 2, z)
        if (w <= ancho && h <= alto) break
        z -= 1
    }
    return min(z, 18) to centro
}
