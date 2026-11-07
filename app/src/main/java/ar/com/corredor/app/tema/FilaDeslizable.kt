package ar.com.corredor.app.tema

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Clon de Tema/FilaDeslizable.swift.

/** Una acción de swipe (84 de ancho, ícono 20 y texto 13/600 en blanco). */
data class AccionFila(val texto: String, val simbolo: String, val color: Color, val accion: () -> Unit)

/** Fila con swipe dentro de una tarjeta (3a): a la izquierda muestra `derecha` (la última es la principal y se ejecuta
 *  al deslizar hasta el final); a la derecha, `izquierda`. Las mismas acciones quedan como acciones de TalkBack. */
@Composable
fun FilaDeslizable(
    izquierda: List<AccionFila> = emptyList(),
    derecha: List<AccionFila> = emptyList(),
    anchoAccion: Dp = 84.dp,
    contenido: @Composable () -> Unit,
) {
    val densidad = LocalDensity.current
    val anchoPx = with(densidad) { anchoAccion.toPx() }
    val desplazamiento = remember { Animatable(0f) }
    var ancho by remember { mutableIntStateOf(0) }
    val alcance = rememberCoroutineScope()
    val izq by rememberUpdatedState(izquierda)
    val der by rememberUpdatedState(derecha)
    val tocarMinimo = LocalViewConfiguration.current.touchSlop
    val superficie = C.superficie

    fun cerrar(animar: Boolean = true) {
        alcance.launch { if (animar) desplazamiento.animateTo(0f, spring(stiffness = 700f)) else desplazamiento.snapTo(0f) }
    }

    val x = desplazamiento.value
    Box(
        Modifier.fillMaxWidth().clipToBounds().onSizeChanged { ancho = it.width }
            .semantics {
                customActions = (izquierda + derecha).map { a -> CustomAccessibilityAction(a.texto) { a.accion(); true } }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val abajo = awaitFirstDown(requireUnconsumed = false)
                    var horizontal: Boolean? = null
                    var acumulado = androidx.compose.ui.geometry.Offset.Zero
                    val base = desplazamiento.value
                    var actual = base
                    // Decide la dirección como iOS (minimumDistance 18 y 1,3 veces más horizontal que vertical).
                    while (horizontal == null) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == abajo.id } ?: return@awaitEachGesture
                        if (!c.pressed) return@awaitEachGesture
                        acumulado += c.positionChange()
                        if (acumulado.getDistance() > maxOf(tocarMinimo, 18.dp.toPx())) {
                            horizontal = abs(acumulado.x) > abs(acumulado.y) * 1.3f
                        }
                    }
                    if (horizontal != true) return@awaitEachGesture
                    actual = base + acumulado.x
                    horizontalDrag(abajo.id) { c ->
                        c.consume()
                        actual += c.positionChange().x
                        var nx = actual
                        if (izq.isEmpty()) nx = minOf(0f, nx)
                        if (der.isEmpty()) nx = maxOf(0f, nx)
                        // Resistencia más allá del largo total.
                        val tope = ancho * 0.92f
                        if (abs(nx) > tope) nx = if (nx > 0) tope + (nx - tope) * 0.2f else -tope + (nx + tope) * 0.2f
                        alcance.launch { desplazamiento.snapTo(nx) }
                    }
                    val completo = ancho * 0.6f
                    val abiertoDerecha = -anchoPx * der.size
                    val abiertoIzquierda = anchoPx * izq.size
                    alcance.launch {
                        when {
                            actual < -completo && der.isNotEmpty() -> {
                                Haptico.impacto()
                                desplazamiento.animateTo(-ancho.toFloat(), spring(stiffness = 700f))
                                delay(180); der.last().accion(); desplazamiento.snapTo(0f)
                            }
                            actual > completo && izq.isNotEmpty() -> {
                                Haptico.impacto()
                                desplazamiento.animateTo(ancho.toFloat(), spring(stiffness = 700f))
                                delay(180); izq.first().accion(); desplazamiento.snapTo(0f)
                            }
                            actual < abiertoDerecha / 2 && der.isNotEmpty() -> desplazamiento.animateTo(abiertoDerecha, spring(stiffness = 700f))
                            actual > abiertoIzquierda / 2 && izq.isNotEmpty() -> desplazamiento.animateTo(abiertoIzquierda, spring(stiffness = 700f))
                            else -> desplazamiento.animateTo(0f, spring(stiffness = 700f))
                        }
                    }
                }
            },
    ) {
        // Fondo: el color de la acción que se ve.
        if (x > 0 && izquierda.isNotEmpty()) {
            Row(Modifier.matchParentSize().background(izquierda.first().color)) {
                val w = with(densidad) { maxOf(anchoPx, x / izquierda.size).toDp() }
                izquierda.forEach { a -> BotonAccionFila(a, Modifier.width(w).fillMaxHeight()) { cerrar(); a.accion() } }
            }
        }
        if (x < 0 && derecha.isNotEmpty()) {
            Row(Modifier.matchParentSize().background(derecha.last().color), horizontalArrangement = Arrangement.End) {
                derecha.forEachIndexed { i, a ->
                    val w = if (i == derecha.size - 1) with(densidad) { maxOf(anchoPx, -x - anchoPx * (derecha.size - 1)).toDp() } else anchoAccion
                    BotonAccionFila(a, Modifier.width(w).fillMaxHeight()) { cerrar(); a.accion() }
                }
            }
        }
        Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }.background(superficie)) { contenido() }
    }
}

@Composable
private fun BotonAccionFila(a: AccionFila, modifier: Modifier, alTocar: () -> Unit) {
    Column(modifier.background(a.color).clickable(onClick = alTocar), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Icono(a.simbolo, 18f, Color.White)
        Texto(a.texto, Estilo.footnoteFuerte, Color.White, lineas = 1)
    }
}
