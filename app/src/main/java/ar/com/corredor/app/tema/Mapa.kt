package ar.com.corredor.app.tema

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

// Map de MapKit: las teselas de CARTO (OpenStreetMap, apagadas: sin lugares de interés) con Coil, que se arrastran y
// se pellizcan, con los pines encima y el punto azul de «vos». Lo usa el mapa de la cartera (Propiedades).

/** Dónde mira el mapa: centro y zoom (fraccionario, como el de las teselas web). */
class CamaraMapa {
    var lat by mutableDoubleStateOf(-34.6037)
    var lng by mutableDoubleStateOf(-58.3816)
    var zoom by mutableDoubleStateOf(12.0)

    /** Encuadra la región (los grados de alto y ancho) en `ancho` × `alto` dp. */
    fun encuadrar(cLat: Double, cLng: Double, dLat: Double, dLng: Double, ancho: Float, alto: Float) {
        lat = cLat; lng = cLng
        if (ancho <= 0f || alto <= 0f) return
        val zx = log2(ancho / (dLng / 360.0 * TESELA))
        val y0 = mundoY(cLat + dLat / 2, 0.0); val y1 = mundoY(cLat - dLat / 2, 0.0)
        val zy = log2(alto / max(1e-9, y1 - y0))
        zoom = min(zx, zy).coerceIn(2.0, 18.0)
    }
}

class PinMapa(val id: String, val lat: Double, val lng: Double, val contenido: @Composable () -> Unit)

private const val TESELA = 256.0
private fun mundoX(lng: Double, z: Double) = (lng + 180.0) / 360.0 * TESELA * 2.0.pow(z)
private fun mundoY(lat: Double, z: Double): Double {
    val s = sin(lat * PI / 180.0).coerceIn(-0.9999, 0.9999)
    return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * TESELA * 2.0.pow(z)
}
private fun lngDe(x: Double, z: Double) = x / (TESELA * 2.0.pow(z)) * 360.0 - 180.0
private fun latDe(y: Double, z: Double): Double {
    val n = PI - 2.0 * PI * y / (TESELA * 2.0.pow(z))
    return atan(sinh(n)) * 180.0 / PI
}

/** Un mapa de teselas (apagado: sin lugares de interés) que se arrastra y se pellizca, con los pines encima y el punto
 *  azul de la ubicación. */
@Composable
fun MapaTeselas(camara: CamaraMapa, ancho: Float, alto: Float, pines: List<PinMapa>, yo: Pair<Double, Double>?, alTocarFondo: () -> Unit) {
    val oscuro = LocalOscuro.current
    val estiloTeselas = if (oscuro) "dark_nolabels" else "light_all"
    val densidad = LocalDensity.current.density
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomCambio, _ ->
                    val z = camara.zoom
                    val cx = mundoX(camara.lng, z) - pan.x / densidad
                    val cy = mundoY(camara.lat, z) - pan.y / densidad
                    camara.lng = lngDe(cx, z); camara.lat = latDe(cy, z).coerceIn(-85.0, 85.0)
                    if (zoomCambio != 1f) camara.zoom = (z + log2(zoomCambio.toDouble())).coerceIn(2.0, 19.0)
                }
            }
            .pointerInput(Unit) { detectTapGestures { alTocarFondo() } },
    ) {
        val zoom = camara.zoom
        val z = floor(zoom).toInt().coerceIn(0, 19)
        val escala = 2.0.pow(zoom - z)
        val tam = TESELA * escala
        val cx = mundoX(camara.lng, zoom); val cy = mundoY(camara.lat, zoom)
        val izq = cx - ancho / 2; val arriba = cy - alto / 2
        val n = 1 shl z
        val x0 = floor(izq / tam).toInt(); val x1 = floor((izq + ancho) / tam).toInt()
        val y0 = floor(arriba / tam).toInt().coerceAtLeast(0); val y1 = floor((arriba + alto) / tam).toInt().coerceAtMost(n - 1)
        for (ty in y0..y1) for (tx in x0..x1) {
            val xx = ((tx % n) + n) % n
            AsyncImage(
                "https://${"abcd"[(xx + ty) % 4]}.basemaps.cartocdn.com/$estiloTeselas/$z/$xx/$ty@2x.png", null,
                Modifier.offset((tx * tam - izq).toFloat().dp, (ty * tam - arriba).toFloat().dp).size((tam + 0.5).toFloat().dp),
                contentScale = ContentScale.FillBounds,
            )
        }
        if (yo != null) {
            val px = mundoX(yo.second, zoom) - izq; val py = mundoY(yo.first, zoom) - arriba
            Box(Modifier.centradoEn(px.toFloat().dp, py.toFloat().dp).size(18.dp).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(hex(0x007AFF)))
        }
        pines.forEach { p ->
            val px = mundoX(p.lng, zoom) - izq; val py = mundoY(p.lat, zoom) - arriba
            if (px > -80 && px < ancho + 80 && py > -40 && py < alto + 40) Box(Modifier.centradoEn(px.toFloat().dp, py.toFloat().dp)) { p.contenido() }
        }
        Texto("© OpenStreetMap · © CARTO", Estilo(9f, Peso.regular), C.texto3, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

/** Pone el centro de la vista en (x, y). */
private fun Modifier.centradoEn(x: Dp, y: Dp): Modifier = this.layout { m, r ->
    val p = m.measure(r.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2) }
}
