package ar.com.corredor.app.tema

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Cuánto está tirado el [Refrescable] de adentro: el título grande de `PantallaApilada` no se esconde mientras tanto
 *  (primero se recoge lo tirado). */
class Tiron { var tirado by mutableFloatStateOf(0f) }

val LocalTiron = staticCompositionLocalOf<Tiron?> { null }

/** Tirar hacia abajo para volver a pedir (`.refreshable` en iOS), sin Material: envuelve una lista con scroll vertical
 *  (verticalScroll o LazyColumn). Mientras se tira baja el contenido y aparece la flecha; pasado el umbral, al soltar
 *  corre [alRefrescar] con el pulso arriba hasta que termina. */
@Composable
fun Refrescable(alRefrescar: suspend () -> Unit, modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    val alcance = rememberCoroutineScope()
    val accion by rememberUpdatedState(alRefrescar)
    var tirado by remember { mutableFloatStateOf(0f) }
    var refrescando by remember { mutableStateOf(false) }
    val umbral = with(LocalDensity.current) { 72.dp.toPx() }
    val tiron = LocalTiron.current
    LaunchedEffect(tiron) { if (tiron != null) snapshotFlow { tirado }.collect { tiron.tirado = it } }
    val conexion = remember(umbral) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Volviendo para arriba con el contenido corrido: primero se recoge lo tirado.
                if (available.y < 0 && tirado > 0 && !refrescando) {
                    val usado = maxOf(available.y, -tirado)
                    tirado += usado
                    return Offset(0f, usado)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput && !refrescando) {
                    tirado = minOf(umbral * 1.6f, tirado + available.y * 0.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (refrescando) return Velocity.Zero
                if (tirado >= umbral) {
                    refrescando = true
                    tirado = umbral
                    Haptico.impacto()
                    alcance.launch {
                        try { accion() } finally { refrescando = false; tirado = 0f }
                    }
                    return available
                }
                val habia = tirado > 0
                tirado = 0f
                return if (habia) available else Velocity.Zero
            }
        }
    }
    Box(modifier.nestedScroll(conexion)) {
        Box(Modifier.offset { IntOffset(0, tirado.roundToInt()) }) { contenido() }
        if (tirado > 0 || refrescando) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                if (refrescando) Pulso(C.acento, 12.dp)
                else Icono("arrow.down", 20f, C.texto3, Modifier.alpha((tirado / umbral).coerceIn(0f, 1f)))
            }
        }
    }
}
