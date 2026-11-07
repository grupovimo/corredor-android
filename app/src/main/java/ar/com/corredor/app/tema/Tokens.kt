package ar.com.corredor.app.tema

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ar.com.corredor.app.R

// Clon de Tema/Tokens.swift: los tokens del handoff (docs/handoff/design_handoff_corredor_iphone · 09 «Tokens SwiftUI»),
// claro y oscuro. Un punto de iOS es un dp acá (y un sp en el texto, que escala con el tamaño de letra del sistema).

/** Color(hex:alfa:) de iOS. */
fun hex(v: Long, alfa: Float = 1f): Color = Color(0xFF000000 or (v and 0xFFFFFF)).copy(alpha = alfa)
fun hex(v: Int, alfa: Float = 1f): Color = hex(v.toLong(), alfa)

/** Si se ve en oscuro (lo fija TemaCorredor: el sistema o lo que se eligió en el perfil). */
val LocalOscuro = staticCompositionLocalOf { false }

/** Un color que cambia con el modo oscuro (Color.tema en iOS). `.actual` lo resuelve en la pantalla. */
class ColorTema(val claro: Color, val oscuro: Color) {
    val actual: Color @Composable @ReadOnlyComposable get() = if (LocalOscuro.current) oscuro else claro
    fun en(oscuro: Boolean): Color = if (oscuro) this.oscuro else claro
}

/** Color.tema(claro, oscuro, alfa:, alfaOscuro:) de iOS. */
fun tema(claro: Long, oscuro: Long, alfa: Float = 1f, alfaOscuro: Float? = null) = ColorTema(hex(claro, alfa), hex(oscuro, alfaOscuro ?: alfa))
fun tema(claro: Int, oscuro: Int, alfa: Float = 1f, alfaOscuro: Float? = null) = tema(claro.toLong(), oscuro.toLong(), alfa, alfaOscuro)

/** El tema: «sistema» sigue al teléfono (como @AppStorage("tema") en iOS), «claro» y «oscuro» lo fijan. Da también el
 *  ancho de la pantalla para los márgenes del handoff (anchoPantalla). */
@Composable
fun TemaCorredor(tema: String = "sistema", contenido: @Composable () -> Unit) {
    val oscuro = when (tema) { "oscuro" -> true; "claro" -> false; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalOscuro provides oscuro) {
        BoxWithConstraints {
            CompositionLocalProvider(LocalAnchoPantalla provides maxWidth.value, content = contenido)
        }
    }
}

/** Los tonos del handoff por nombre, sin resolver (los de C). */
object T {
    val fondo = tema(0xF3EFE6, 0x111314)
    val elevado = tema(0xFFFDF8, 0x1B1E20)
    val superficie = tema(0xFFFDF8, 0x1B1E20)
    val hundida = tema(0xE9E5DB, 0x25292C)
    val texto = tema(0x18201D, 0xEEF0F1)
    val texto2 = tema(0x4D5853, 0xA8AEB2)
    val texto3 = tema(0x68716C, 0x8B9195)
    val chevron = tema(0xA9B1AC, 0x50585D)
    val sobreAccion = tema(0xFFFFFF, 0x1A1405)
    val oroTinta = tema(0x7A5200, 0xF2BB57)
    val primarioTinta = tema(0x16443B, 0xF7CB73)
    val crema = tema(0xFBEDCC, 0xF2BB57, alfaOscuro = 0.18f)
    val crema2 = tema(0xF7F3EA, 0x25292C)
    val pista = tema(0xE9E5DB, 0x25292C)
    val pistaMeta = tema(0xE9E5DB, 0x25292C)
    val ok = tema(0x1E5749, 0x9FD6C2)
    val okFondo = tema(0xE4EEE9, 0x9FD6C2, alfaOscuro = 0.16f)
    val okTinta = tema(0x16443B, 0x9FD6C2)
    val aviso = tema(0xC48A1C, 0xF2BB57)
    val avisoFondo = tema(0xFBEDCC, 0xF2BB57, alfaOscuro = 0.16f)
    val avisoTinta = tema(0x7A5200, 0xF2BB57)
    val bannerTinta = tema(0x7A5200, 0xF2BB57)
    val azulFondo = tema(0xE7EFF9, 0x9CC2EF, alfaOscuro = 0.16f)
    val azulTinta = tema(0x1F4F8A, 0x9CC2EF)
    val violetaFondo = tema(0xF1EAF9, 0xC9A8EF, alfaOscuro = 0.16f)
    val violetaTinta = tema(0x5B3A8C, 0xC9A8EF)
    val error = tema(0xB42318, 0xF29B92)
    val errorFondo = tema(0xFBE4E1, 0xF29B92, alfaOscuro = 0.16f)
    val errorTinta = tema(0xB42318, 0xF29B92)
    val neutroFondo = tema(0xE9E5DB, 0xEEF0F1, alfaOscuro = 0.08f)
    val neutroTinta = tema(0x4D5853, 0xA8AEB2)
    val apagadoFondo = tema(0xEFEBE2, 0xEEF0F1, alfaOscuro = 0.06f)
    val linea = tema(0xE3DDCF, 0x2E3336)
    val linea2 = tema(0xD5CEBE, 0x32413B)
    val anillo = tema(0x0F3530, 0xE8EFE9, alfa = 0.06f, alfaOscuro = 0.08f)
    val segmentado = tema(0x0F3530, 0xE8EFE9, alfa = 0.07f, alfaOscuro = 0.07f)
    val segmentoElegido = tema(0xFFFDF8, 0x26332E)
    val rayado1 = tema(0xEDE7DA, 0x1F2C27)
    val rayado2 = tema(0xF5F0E6, 0x16211D)
    val esqueleto1 = tema(0xE9E5DB, 0x1F2C27)
    val esqueleto2 = tema(0xF1EDE4, 0x16211D)
    val canalWeb = tema(0x16443B, 0x9FD6C2)
    val canalLocal = tema(0x4D5853, 0xA9B8B1)
}

/** Colores del handoff de Corredor (09 · «Tokens SwiftUI»): Fondo, Superficie, Tinta, Línea, Acción (verde; oro en
 *  oscuro) y los estados. Los nombres de Tu Local Web se mantienen para las piezas compartidas. */
object C {
    val fondo: Color @Composable @ReadOnlyComposable get() = T.fondo.actual
    val elevado: Color @Composable @ReadOnlyComposable get() = T.elevado.actual
    val superficie: Color @Composable @ReadOnlyComposable get() = T.superficie.actual
    val hundida: Color @Composable @ReadOnlyComposable get() = T.hundida.actual
    val texto: Color @Composable @ReadOnlyComposable get() = T.texto.actual
    val texto2: Color @Composable @ReadOnlyComposable get() = T.texto2.actual
    val texto3: Color @Composable @ReadOnlyComposable get() = T.texto3.actual
    val chevron: Color @Composable @ReadOnlyComposable get() = T.chevron.actual
    /** Acción: el acento de la inmobiliaria (Tema/Acento.swift); sin marca, verde en claro y oro en oscuro. */
    val accion: Color @Composable get() = Acento.accion(LocalOscuro.current)
    val sobreAccion: Color @Composable @ReadOnlyComposable get() = T.sobreAccion.actual
    val verde800 = hex(0x0F3530)
    val verde700 = hex(0x16443B)
    val oro = hex(0xF2BB57)
    val oroTinta: Color @Composable @ReadOnlyComposable get() = T.oroTinta.actual
    val acento: Color @Composable get() = accion
    val primarioTexto: Color @Composable get() = accion
    val primarioTinta: Color @Composable @ReadOnlyComposable get() = T.primarioTinta.actual
    val crema: Color @Composable @ReadOnlyComposable get() = T.crema.actual
    val crema2: Color @Composable @ReadOnlyComposable get() = T.crema2.actual
    val pista: Color @Composable @ReadOnlyComposable get() = T.pista.actual
    val pistaMeta: Color @Composable @ReadOnlyComposable get() = T.pistaMeta.actual
    val ok: Color @Composable @ReadOnlyComposable get() = T.ok.actual
    val okFondo: Color @Composable @ReadOnlyComposable get() = T.okFondo.actual
    val okTinta: Color @Composable @ReadOnlyComposable get() = T.okTinta.actual
    val aviso: Color @Composable @ReadOnlyComposable get() = T.aviso.actual
    val avisoFondo: Color @Composable @ReadOnlyComposable get() = T.avisoFondo.actual
    val avisoTinta: Color @Composable @ReadOnlyComposable get() = T.avisoTinta.actual
    val bannerTinta: Color @Composable @ReadOnlyComposable get() = T.bannerTinta.actual
    val azulFondo: Color @Composable @ReadOnlyComposable get() = T.azulFondo.actual
    val azulTinta: Color @Composable @ReadOnlyComposable get() = T.azulTinta.actual
    val violetaFondo: Color @Composable @ReadOnlyComposable get() = T.violetaFondo.actual
    val violetaTinta: Color @Composable @ReadOnlyComposable get() = T.violetaTinta.actual
    val error: Color @Composable @ReadOnlyComposable get() = T.error.actual
    val errorFondo: Color @Composable @ReadOnlyComposable get() = T.errorFondo.actual
    val errorTinta: Color @Composable @ReadOnlyComposable get() = T.errorTinta.actual
    val neutroFondo: Color @Composable @ReadOnlyComposable get() = T.neutroFondo.actual
    val neutroTinta: Color @Composable @ReadOnlyComposable get() = T.neutroTinta.actual
    val apagadoFondo: Color @Composable @ReadOnlyComposable get() = T.apagadoFondo.actual
    val linea: Color @Composable @ReadOnlyComposable get() = T.linea.actual
    val linea2: Color @Composable @ReadOnlyComposable get() = T.linea2.actual
    val anillo: Color @Composable @ReadOnlyComposable get() = T.anillo.actual
    val segmentado: Color @Composable @ReadOnlyComposable get() = T.segmentado.actual
    val segmentoElegido: Color @Composable @ReadOnlyComposable get() = T.segmentoElegido.actual
    val velo = hex(0x0B2622, 0.45f)
    val rayado1: Color @Composable @ReadOnlyComposable get() = T.rayado1.actual
    val rayado2: Color @Composable @ReadOnlyComposable get() = T.rayado2.actual
    val esqueleto1: Color @Composable @ReadOnlyComposable get() = T.esqueleto1.actual
    val esqueleto2: Color @Composable @ReadOnlyComposable get() = T.esqueleto2.actual
    val canalWeb: Color @Composable @ReadOnlyComposable get() = T.canalWeb.actual
    val canalML = hex(0xE9AD44)
    val canalWhatsApp = hex(0x25D366)
    val canalLocal: Color @Composable @ReadOnlyComposable get() = T.canalLocal.actual
    /** El botón principal: acción lisa (el handoff no usa gradiente). */
    val primario: Brush @Composable get() = Brush.verticalGradient(listOf(accion, accion))
    val sombraPrimario = hex(0x16443B, 0.5f)
    /** Sombras teñidas de verde (e1, e2, e3). */
    val sombra = hex(0x16443B)
}

/** Pesos de Geist incluidos en la app (los mismos .ttf que UIAppFonts en iOS: 400, 600, 700 y 800). */
enum class Peso(val peso: FontWeight) {
    /** `.light` de iOS (`.font(.system(size:, weight: .light))`). Geist no trae 300: el texto cae en la 400. */
    light(FontWeight.Light),
    regular(FontWeight.Normal),
    /** Como en iOS, medium usa Geist-SemiBold. */
    medium(FontWeight.SemiBold),
    semibold(FontWeight.SemiBold),
    bold(FontWeight.Bold),
    extrabold(FontWeight.ExtraBold),
}

/** La letra: Geist 400–800. */
val Geist = FontFamily(
    Font(R.font.geist_400, FontWeight.Normal),
    Font(R.font.geist_600, FontWeight.SemiBold),
    Font(R.font.geist_700, FontWeight.Bold),
    Font(R.font.geist_800, FontWeight.ExtraBold),
)

/** El estilo de Dynamic Type al que escala un Estilo (en Android todo escala con el tamaño de letra del sistema). */
enum class Relativo { largeTitle, title, title2, title3, headline, body, callout, subheadline, footnote, caption, caption2 }

/** Un estilo de texto del handoff: tamaño, peso y tracking (en em). Escala con el tamaño de letra del sistema. */
data class Estilo(val tamano: Float, val peso: Peso, val tracking: Float = 0f, val relativo: Relativo = Relativo.body) {
    /** El TextStyle de Compose. `numeros`: dígitos tabulares (.monospacedDigit()). */
    fun ts(numeros: Boolean = false): TextStyle = TextStyle(
        fontFamily = Geist,
        fontSize = tamano.sp,
        fontWeight = peso.peso,
        letterSpacing = tracking.em,
        fontFeatureSettings = if (numeros) "tnum" else null,
        lineBreak = LineBreak.Paragraph,
    )

    fun con(peso: Peso) = copy(peso = peso)

    companion object {
        val montoHeroe = Estilo(46f, Peso.bold, -0.035f, Relativo.largeTitle)
        val montoCobro = Estilo(52f, Peso.bold, -0.035f, Relativo.largeTitle)
        val montoPantalla = Estilo(40f, Peso.bold, -0.03f, Relativo.largeTitle)
        val tituloGrande = Estilo(34f, Peso.extrabold, -0.035f, Relativo.largeTitle)
        val titulo = Estilo(28f, Peso.bold, -0.03f, Relativo.title)
        val titulo3 = Estilo(20f, Peso.bold, -0.02f, Relativo.title3)
        val caption = Estilo(12f, Peso.bold, 0.02f, Relativo.caption)
        val saludo = Estilo(32f, Peso.bold, -0.025f, Relativo.title)
        val tituloPaso = Estilo(30f, Peso.bold, -0.025f, Relativo.title)
        val tituloBloqueo = Estilo(28f, Peso.bold, -0.02f, Relativo.title)
        val montoMeta = Estilo(28f, Peso.bold, -0.025f, Relativo.title)
        val tituloHoja = Estilo(24f, Peso.bold, -0.02f, Relativo.title2)
        val tituloHojaChico = Estilo(22f, Peso.bold, -0.02f, Relativo.title2)
        val tituloSeccion = Estilo(21f, Peso.bold, -0.02f, Relativo.title3)
        val indicador = Estilo(22f, Peso.bold, -0.02f, Relativo.title3)
        val nombreTienda = Estilo(18f, Peso.bold, relativo = Relativo.headline)
        val total = Estilo(18f, Peso.bold, relativo = Relativo.headline)
        val headline = Estilo(17f, Peso.semibold, relativo = Relativo.headline)
        val body = Estilo(17f, Peso.regular, relativo = Relativo.body)
        val fila = Estilo(16f, Peso.semibold, relativo = Relativo.body)
        val body16 = Estilo(16f, Peso.regular, relativo = Relativo.body)
        val tarjetaTincho = Estilo(16f, Peso.medium, relativo = Relativo.body)
        val subhead = Estilo(15f, Peso.regular, relativo = Relativo.subheadline)
        val subheadFuerte = Estilo(15f, Peso.semibold, relativo = Relativo.subheadline)
        val filaLista = Estilo(15f, Peso.semibold, relativo = Relativo.subheadline)
        val label = Estilo(14f, Peso.semibold, relativo = Relativo.footnote)
        val texto14 = Estilo(14f, Peso.regular, relativo = Relativo.footnote)
        val footnote = Estilo(13f, Peso.regular, relativo = Relativo.footnote)
        val footnoteFuerte = Estilo(13f, Peso.semibold, relativo = Relativo.footnote)
        val chip = Estilo(12f, Peso.semibold, relativo = Relativo.caption)
        val etiqueta = Estilo(11f, Peso.bold, relativo = Relativo.caption2)
        val tab = Estilo(10f, Peso.semibold, relativo = Relativo.caption2)
    }
}

/** Medidas del handoff. */
object M {
    /** Margen horizontal: 20 (16 en SE, 24 en Pro Max). */
    fun margen(ancho: Float): Dp = if (ancho <= 375f) 16.dp else if (ancho >= 428f) 24.dp else 20.dp
    val tabBar = 49.dp
    /** Radios continuos: control, tarjeta, destacada y hoja. */
    val rControl = 12.dp
    val rTarjeta = 18.dp
    val rDestacada = 22.dp
    val rHoja = 30.dp
}

/** El ancho de la pantalla en dp para los márgenes del handoff (anchoPantalla en iOS). */
val LocalAnchoPantalla = staticCompositionLocalOf { 393f }

/** El margen de la pantalla (\.margen en iOS: 20, o 16 en SE). */
val margen: Dp @Composable @ReadOnlyComposable get() = M.margen(LocalAnchoPantalla.current)

/** e1: radio 2, y 1, 7 %. */
fun Modifier.sombraE1(forma: androidx.compose.ui.graphics.Shape = RoundedCornerShape(M.rTarjeta)): Modifier =
    this.sombra(forma, C.sombra.copy(alpha = 0.07f), 1.dp, y = 1.dp)

/** e2: la de las tarjetas. */
fun Modifier.sombraE2(forma: androidx.compose.ui.graphics.Shape = RoundedCornerShape(M.rTarjeta)): Modifier =
    this.sombra(forma, C.sombra.copy(alpha = 0.32f), 11.dp, y = 10.dp).sombra(forma, C.sombra.copy(alpha = 0.07f), 1.dp, y = 1.dp)

/** e3: la de lo que flota (hojas, el botón de MIRA). */
fun Modifier.sombraE3(forma: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)): Modifier =
    this.sombra(forma, C.verde800.copy(alpha = 0.8f), 20.dp, y = 22.dp)

/** Una tarjeta del handoff (.tarjeta() en iOS): superficie, radio 18 continuo y e2. */
@Composable
fun Modifier.tarjeta(radio: Dp = M.rTarjeta): Modifier {
    val forma = RoundedCornerShape(radio)
    return this.sombraE2(forma).clip(forma).background(C.superficie)
}
