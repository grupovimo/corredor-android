package ar.com.corredor.app.tema

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Cierra la hoja en la que está (@Environment(\.dismiss) en iOS). */
val LocalCerrarHoja = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/** Las alturas de una hoja (presentationDetents en iOS). */
enum class Altura { media, grande }

/** Una hoja que sube desde abajo (.sheet + hojaTLW en iOS): velo, radio 30 arriba, fondo `bg-elevated`, indicador para arrastrar, «atrás» la cierra.
 *  Va en su propia ventana (como en iOS), así tapa toda la pantalla aunque se abra desde adentro de una lista.
 *  Con `alturas = [media, grande]` arranca a media pantalla y se estira; arrastrar hacia abajo la cierra. */
@Composable
fun Hoja(
    visible: Boolean,
    alCerrar: () -> Unit,
    alturas: List<Altura> = listOf(Altura.grande),
    indicador: Boolean = false,
    /** .presentationDetents([.height(420)]) en iOS. */
    altoFijo: androidx.compose.ui.unit.Dp? = null,
    /** Con altoFijo: .presentationDetents([.height(n), .large]), se estira hacia arriba hasta casi toda la pantalla. */
    estirable: Boolean = false,
    radio: androidx.compose.ui.unit.Dp = 30.dp,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val estadoVisible = remember { MutableTransitionState(false) }
    estadoVisible.targetState = visible
    if (!estadoVisible.currentState && !estadoVisible.targetState && estadoVisible.isIdle) return
    val oscuro = LocalOscuro.current; val ancho = LocalAnchoPantalla.current
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        CompositionLocalProvider(LocalOscuro provides oscuro, LocalAnchoPantalla provides ancho, LocalCerrarHoja provides alCerrar) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(estadoVisible, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(remember { MutableInteractionSource() }, null, onClick = alCerrar))
                }
                AnimatedVisibility(estadoVisible, enter = slideInVertically(Mov.entrada<androidx.compose.ui.unit.IntOffset>()) { it }, exit = slideOutVertically(Mov.entrada<androidx.compose.ui.unit.IntOffset>()) { it }) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        val total = maxHeight
                        val densidad = LocalDensity.current
                        var altura by remember { mutableStateOf(if (altoFijo != null) Altura.media else alturas.first()) }
                        val grandeOk = if (altoFijo != null) estirable else Altura.grande in alturas
                        val medianaOk = if (altoFijo != null) true else Altura.media in alturas
                        val alto = when {
                            altura == Altura.grande && (altoFijo == null || estirable) -> total * 0.94f
                            altoFijo != null -> minOf(altoFijo + 34.dp, total * 0.94f)
                            else -> total * 0.5f
                        }
                        val arrastre = remember { Animatable(0f) }
                        val alcance = rememberCoroutineScope()
                        Column(
                            Modifier.fillMaxWidth().height(alto).offset { IntOffset(0, arrastre.value.roundToInt().coerceAtLeast(0)) }
                                .clip(RoundedCornerShape(topStart = radio, topEnd = radio)).background(C.elevado)
                                .clickable(remember { MutableInteractionSource() }, null) {}
                                .navigationBarsPadding().imePadding(),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(if (indicador) 20.dp else 8.dp).draggable(
                                    rememberDraggableState { d -> alcance.launch { arrastre.snapTo(arrastre.value + d) } },
                                    Orientation.Vertical,
                                    onDragStopped = { v ->
                                        val umbral = with(densidad) { 120.dp.toPx() }
                                        when {
                                            arrastre.value < -umbral / 2 && grandeOk -> { altura = Altura.grande; arrastre.animateTo(0f) }
                                            arrastre.value > umbral || v > 1500f -> if (altura == Altura.grande && medianaOk && (altoFijo != null || Altura.media in alturas)) {
                                                altura = Altura.media; arrastre.animateTo(0f)
                                            } else {
                                                alCerrar()
                                                // Si no se puede cerrar (alCerrar no hace nada), vuelve a su lugar.
                                                arrastre.animateTo(0f)
                                            }
                                            else -> arrastre.animateTo(0f)
                                        }
                                    },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (indicador) Box(Modifier.padding(top = 4.dp).size(36.dp, 5.dp).clip(CircleShape).background(C.linea))
                            }
                            contenido()
                        }
                    }
                }
            }
        }
    }
}
