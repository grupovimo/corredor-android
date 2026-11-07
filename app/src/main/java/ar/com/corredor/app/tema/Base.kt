package ar.com.corredor.app.tema

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.corredor.app.R

// Lo que SwiftUI da hecho y Compose sin Material no: Text, Image(systemName:), Image de los assets, .shadow, los
// estilos de botón que se hunden (Presionable) y Reducir movimiento. Lo usan todas las piezas y pantallas.

/** El texto de la app (Text de SwiftUI). `escalaMinima`: .minimumScaleFactor (achica la letra hasta entrar). */
@Composable
fun Texto(
    texto: String,
    estilo: Estilo = Estilo.body,
    color: Color = C.texto,
    modifier: Modifier = Modifier,
    lineas: Int = Int.MAX_VALUE,
    alinear: TextAlign = TextAlign.Start,
    numeros: Boolean = false,
    escalaMinima: Float? = null,
    interlineado: Float = 0f,
    /** .monospaced() de iOS (códigos, números de cuenta). */
    monoespaciado: Boolean = false,
    /** .tracking(n) extra en puntos (la caption en versalitas). */
    tracking: Float? = null,
    /** .truncationMode(.middle) de iOS (links largos, en un renglón). */
    cortarAlMedio: Boolean = false,
) {
    var ts = estilo.ts(numeros)
    if (interlineado > 0f) ts = ts.copy(lineHeight = (estilo.tamano * 1.2f + interlineado).sp)
    if (monoespaciado) ts = ts.copy(fontFamily = FontFamily.Monospace)
    if (tracking != null) ts = ts.copy(letterSpacing = tracking.sp)
    BasicText(
        texto, modifier,
        style = ts.copy(color = color, textAlign = alinear),
        maxLines = lineas, overflow = if (cortarAlMedio) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
        autoSize = escalaMinima?.let { TextAutoSize.StepBased((estilo.tamano * it).sp, estilo.tamano.sp, 0.5.sp) },
    )
}

/** Texto con partes de distinto estilo (Text + Text en iOS). */
@Composable
fun TextoRico(
    texto: AnnotatedString,
    estilo: Estilo = Estilo.body,
    color: Color = C.texto,
    modifier: Modifier = Modifier,
    lineas: Int = Int.MAX_VALUE,
    alinear: TextAlign = TextAlign.Start,
    numeros: Boolean = false,
    escalaMinima: Float? = null,
) {
    BasicText(texto, modifier, style = estilo.ts(numeros).copy(color = color, textAlign = alinear), maxLines = lineas, overflow = TextOverflow.Ellipsis,
        autoSize = escalaMinima?.let { TextAutoSize.StepBased((estilo.tamano * it).sp, estilo.tamano.sp, 0.5.sp) })
}

private val simbolosContorno = FontFamily(Font(R.font.simbolos_contorno))
private val simbolosRelleno = FontFamily(Font(R.font.simbolos_relleno))

/** Un ícono por su nombre de SF Symbol (Image(systemName:) en iOS). «.fill» elige la variante rellena. `tam` es el
 *  .font(.system(size:)) de iOS. */
@Composable
fun Icono(simbolo: String, tam: Float = 17f, color: Color = C.texto, modifier: Modifier = Modifier, descripcion: String? = null) {
    val partes = simbolo.split('.')
    val relleno = "fill" in partes
    val nombre = partes.filter { it != "fill" }.joinToString(".")
    val c = Simbolos.mapa[nombre] ?: Simbolos.mapa[simbolo] ?: Simbolos.mapa[nombre.substringBeforeLast('.')] ?: ' '
    // Los símbolos de Material son un poco más chicos que los de SF a igual tamaño de letra.
    BasicText(
        c.toString(),
        modifier.then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier),
        style = TextStyle(fontFamily = if (relleno) simbolosRelleno else simbolosContorno, fontSize = (tam * 1.18f).sp, color = color, lineHeight = (tam * 1.18f).sp),
    )
}

/** Una imagen de la app (Image("Simbolo") en iOS): los drawables que vienen de Assets.xcassets. */
@Composable
fun Imagen(id: Int, modifier: Modifier = Modifier, recorte: ContentScale = ContentScale.Fit, descripcion: String? = null) {
    Image(painterResource(id), descripcion, modifier, contentScale = recorte)
}

/** Las imágenes de Assets.xcassets por su nombre de iOS (Image("LogoWhatsApp")). */
object Imagenes {
    val grano = R.drawable.grano
    val granoClaro = R.drawable.grano_claro
    val iconoRedondo = R.drawable.icono_redondo
    val lanzamiento = R.drawable.lanzamiento
    val logoGoogle = R.drawable.logo_google
    val logoMercadoLibre = R.drawable.logo_mercadolibre
    val logoWhatsApp = R.drawable.logo_whatsapp
    val patron = R.drawable.patron
    val simbolo = R.drawable.simbolo

    /** Image("Nombre") de iOS → el drawable (0 si no está). */
    fun de(nombre: String): Int = when (nombre) {
        "Grano" -> grano
        "GranoClaro" -> granoClaro
        "IconoRedondo" -> iconoRedondo
        "Lanzamiento" -> lanzamiento
        "LogoGoogle" -> logoGoogle
        "LogoMercadoLibre" -> logoMercadoLibre
        "LogoWhatsApp" -> logoWhatsApp
        "Patron" -> patron
        "Simbolo" -> simbolo
        else -> 0
    }
}

/** Sombra como la de SwiftUI (.shadow(color:radius:x:y:)), dibujada detrás de la forma. */
fun Modifier.sombra(forma: Shape, color: Color, radio: Dp, x: Dp = 0.dp, y: Dp = 0.dp): Modifier = drawBehind {
    if (color.alpha == 0f) return@drawBehind
    val contorno = forma.createOutline(size, layoutDirection, this)
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.isAntiAlias = true
        fp.color = android.graphics.Color.TRANSPARENT
        fp.setShadowLayer(radio.toPx().coerceAtLeast(0.5f), x.toPx(), y.toPx(), color.toArgb())
        canvas.drawOutline(contorno, paint)
    }
}

/** Un botón de SwiftUI con su estilo: `contenido` recibe si está apretado; se hunde (Presionable: 0,97 y 85 %) y se
 *  apaga si no está habilitado. */
@Composable
fun Tocable(
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    hundir: Boolean = true,
    descripcion: String? = null,
    contenido: @Composable (apretado: Boolean) -> Unit,
) {
    val fuente = remember { MutableInteractionSource() }
    val apretado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(if (apretado && hundir) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "hundir")
    androidx.compose.foundation.layout.Box(
        modifier.scale(escala).alpha(if (apretado && hundir) 0.85f else 1f)
            .clickable(fuente, null, enabled = habilitado, role = Role.Button, onClick = alTocar)
            .then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier),
    ) { contenido(apretado) }
}

/** Si el sistema tiene las animaciones prendidas (accessibilityReduceMotion en iOS, al revés). */
@Composable
fun animacionesActivas(): Boolean {
    val vista = LocalView.current
    return remember(vista) {
        android.provider.Settings.Global.getFloat(vista.context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** .disabled de SwiftUI sobre un bloque entero: se apaga y se traga los toques (sólo alpha no alcanza). */
fun Modifier.deshabilitado(si: Boolean, alfa: Float = 0.5f): Modifier = if (!si) this else this.alpha(alfa).pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
