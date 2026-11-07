package ar.com.corredor.app.tema

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.Aviso
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Formato
import java.time.Instant

// Clon de Tema/Estados.swift: los estados de cada pantalla (handoff: «Interactions & Behavior · Estados»): cargando
// con esqueletos de forma real (2c), vacío con la primera acción (E1), error con reintento (E3) y «Reintentar» (12f),
// sin conexión con el banner y la hora de lo guardado (5a).

/** Lo que puede pasar al traer algo de la API. */
sealed class Carga<out T> {
    data object cargando : Carga<Nothing>()
    data class listo<T>(val v: T) : Carga<T>()
    data class error(val e: ErrorApi) : Carga<Nothing>()

    val valor: T? get() = (this as? listo<T>)?.v
}

/** Un estado vacío o de error del handoff (E1–E3): ícono en un círculo hundido, título, texto y una o dos acciones. */
@Composable
fun EstadoVacio(
    icono: String,
    titulo: String,
    texto: String? = null,
    accion: Pair<String, () -> Unit>? = null,
    secundaria: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) {
            Icono(icono, 30f, C.accion)
        }
        Texto(titulo, Estilo.titulo3, C.texto, alinear = TextAlign.Center)
        if (texto != null) Texto(Formato.sinViudas(texto), Estilo.subhead, C.texto2, alinear = TextAlign.Center)
        if (accion != null) Box(Modifier.padding(top = 4.dp)) { BotonPrincipal(accion.first, accion.second) }
        if (secundaria != null) BotonTexto(secundaria.first, secundaria.second)
    }
}

/** El error de traer una lista (12f): «No pudimos traer los pedidos». */
@Composable
fun EstadoError(que: String, error: ErrorApi, reintentar: () -> Unit) {
    if (error.sinConexion) {
        EstadoVacio("wifi.slash", "Sin conexión", "Lo que cargues queda en cola y se sube solo cuando vuelva la señal.", accion = "Reintentar" to reintentar)
    } else {
        EstadoVacio("exclamationmark.triangle", "No pudimos traer $que", "Probá de nuevo en un ratito.", accion = "Reintentar" to reintentar)
    }
}

/** Banner `warn-bg` de sin conexión (5a). */
@Composable
fun BannerSinConexion(guardado: Instant) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.avisoFondo).padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icono("wifi.slash", 14f, C.bannerTinta)
        Texto("Sin conexión. Ves lo guardado ${Formato.cuando(guardado)}.", Estilo.texto14, C.bannerTinta, Modifier.weight(1f))
    }
}

/** Bloque de esqueleto con el brillo horizontal de 1,2 s (respeta Reducir movimiento). */
@Composable
fun Esqueleto(ancho: Dp? = null, alto: Dp, radio: Dp? = null, suave: Boolean = false, modifier: Modifier = Modifier) {
    val base = if (suave) C.esqueleto2 else C.esqueleto1
    val brillo = C.elevado.copy(alpha = 0.9f)
    val anima = animacionesActivas()
    val fase = if (anima) rememberInfiniteTransition(label = "esqueleto").animateFloat(-1f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "f").value else -1f
    Box(
        modifier.then(if (ancho != null) Modifier.width(ancho) else Modifier.fillMaxWidth()).height(alto)
            .clip(RoundedCornerShape(radio ?: (alto / 2))).background(base)
            .drawBehind {
                if (anima) drawRect(Brush.horizontalGradient(listOf(base, brillo, base), startX = size.width * fase, endX = size.width * (fase + 1)))
            },
    )
}

/** Filas de lista mientras cargan. */
@Composable
fun EsqueletoFilas(filas: Int = 4, miniatura: Dp = 48.dp, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().tarjeta().padding(horizontal = 16.dp, vertical = 14.dp).semantics { contentDescription = "Cargando" },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(filas) { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Esqueleto(miniatura, miniatura, 12.dp)
                BoxWithConstraints(Modifier.weight(1f).height(31.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Esqueleto(this@BoxWithConstraints.maxWidth * (if (i % 2 == 0) 0.7f else 0.8f), 13.dp)
                        Esqueleto(this@BoxWithConstraints.maxWidth * (if (i % 2 == 0) 0.45f else 0.35f), 11.dp, suave = true)
                    }
                }
            }
        }
    }
}

// MARK: - Toast

/** El aviso de abajo (verde oscuro, 52 de alto) con la acción sugerida y «Deshacer» cuando aplica. */
@Composable
fun VistaAviso(aviso: Aviso, cerrar: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().sombra(forma, C.verde800.copy(alpha = 0.4f), 12.dp, y = 10.dp).clip(forma).background(tema(0x0F3530, 0x2E3336).actual)
            .heightIn(min = 52.dp).padding(horizontal = 16.dp).semantics(mergeDescendants = false) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (aviso.ok) hex(0x1E5749) else C.error), contentAlignment = Alignment.Center) {
            Icono(if (aviso.ok) "checkmark" else "exclamationmark", 11f, Color.White)
        }
        Texto(aviso.texto, Estilo.subhead, hex(0xE8EFE9), Modifier.weight(1f).padding(vertical = 8.dp), lineas = 2)
        // Con las dos (p. ej. «Cobro registrado» con «Mandar recibo»), la acción y al lado «Deshacer».
        aviso.accion?.let { a ->
            Tocable({ a.second(); cerrar() }, hundir = false) {
                Texto(a.first, Estilo(15f, Peso.bold, relativo = Relativo.subheadline), C.oro, Modifier.padding(vertical = 12.dp))
            }
        }
        aviso.deshacer?.let { d ->
            Tocable({ d(); cerrar() }, hundir = false) {
                Texto("Deshacer", Estilo(15f, Peso.bold, relativo = Relativo.subheadline), C.oro, Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

/** Andamio mientras se arma un área: sólo para desarrollo, nunca llega a la tienda así. */
@Composable
fun Pendiente(titulo: String) {
    Box(Modifier.fillMaxSize().background(C.fondo), contentAlignment = Alignment.Center) { EstadoVacio("hammer", titulo) }
}

/** ProgressView(): la ruedita que gira. */
@Composable
fun Rueda(color: Color = C.texto3, tam: Dp = 20.dp) {
    val giro = rememberInfiniteTransition(label = "rueda").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "g").value
    Canvas(Modifier.size(tam).semantics { contentDescription = "Cargando" }) {
        val g = size.minDimension * 0.12f
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, style = Stroke(g))
        drawArc(color, giro, 100f, false, style = Stroke(g, cap = StrokeCap.Round))
    }
}

/** ProgressView(value:): la barra de avance (el valor de 0 a 1). */
@Composable
fun BarraAvance(valor: Double, modifier: Modifier = Modifier, color: Color = C.accion, alto: Dp = 4.dp) {
    BoxWithConstraints(modifier.fillMaxWidth().height(alto).clip(Capsula).background(C.pista)) {
        Box(Modifier.width(maxWidth * valor.coerceIn(0.0, 1.0).toFloat()).height(alto).clip(Capsula).background(color))
    }
}

/** Un punto de luz que late (los «en vivo»). */
@Composable
fun Pulso(color: Color, tam: Dp = 8.dp) {
    val anima = animacionesActivas()
    val a = if (anima) rememberInfiniteTransition(label = "pulso").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value else 1f
    Box(Modifier.size(tam).clip(CircleShape).background(color.copy(alpha = a)))
}
