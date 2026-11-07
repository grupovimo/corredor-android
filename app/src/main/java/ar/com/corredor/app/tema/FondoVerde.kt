package ar.com.corredor.app.tema

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Lo que va sobre verde: `FondoVerde` de Entrada.swift (la entrada, el bloqueo, MIRA) y los bloques verdes con textura
// (`ZStack { C.verde800; Image("Patron").resizable(resizingMode: .tile).opacity(…); Image("GranoClaro")… }`).

/** Una imagen del bundle en mosaico (`.resizable(resizingMode: .tile)`), con su opacidad. El @3x de iOS: 660 px son
 *  220 pt. */
private fun mosaico(ctx: Context, id: Int, alfa: Double, densidad: Float): android.graphics.Paint? =
    runCatching { BitmapFactory.decodeResource(ctx.resources, id) }.getOrNull()?.let { b ->
        android.graphics.Paint().apply {
            shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(android.graphics.Matrix().apply { setScale(densidad / 3f, densidad / 3f) })
            }
            this.alpha = (alfa * 255).toInt().coerceIn(0, 255)
        }
    }

/** Un bloque verde con textura: el color, el patrón «rr» encima (`patron`: su opacidad; 0, sin patrón) y el grano
 *  claro en mosaico. */
@Composable
fun Modifier.texturaVerde(color: Color, patron: Double = 0.0): Modifier {
    val ctx = LocalContext.current
    val densidad = LocalDensity.current.density
    val pinturas = remember(patron, densidad) {
        listOfNotNull(if (patron > 0) mosaico(ctx, Imagenes.patron, patron, densidad) else null, mosaico(ctx, Imagenes.granoClaro, 1.0, densidad))
    }
    return this.background(color).drawBehind {
        drawIntoCanvas { c -> pinturas.forEach { c.nativeCanvas.drawRect(0f, 0f, size.width, size.height, it) } }
    }
}

/** Fondo de la entrada, el bloqueo y lo que va sobre verde: 0f3530, el patrón rr (al 9 %) y el grano claro con el
 *  resplandor verde arriba. */
@Composable
fun FondoVerde(patron: Double = 0.09, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val densidad = LocalDensity.current.density
    val dibujo = remember(patron, densidad) {
        (if (patron > 0) mosaico(ctx, Imagenes.patron, patron, densidad) else null) to mosaico(ctx, Imagenes.granoClaro, 1.0, densidad)
    }
    Box(modifier.fillMaxSize().background(C.verde800).drawBehind {
        drawIntoCanvas { c -> dibujo.first?.let { c.nativeCanvas.drawRect(0f, 0f, size.width, size.height, it) } }
        drawRect(Brush.radialGradient(listOf(hex(0x7FC4AD, 0.2f), hex(0x0F3530, 0f)), center = Offset(size.width * 0.5f, size.height * 0.3f), radius = 320.dp.toPx()))
        drawIntoCanvas { c -> dibujo.second?.let { c.nativeCanvas.drawRect(0f, 0f, size.width, size.height, it) } }
    })
}
