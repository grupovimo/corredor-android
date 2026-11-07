package ar.com.corredor.app.tema

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** El acento de la marca (la misma regla que el panel: web/src/lib/ui/acento.ts y docs/marca-acento.md en
 *  crm-inmobiliaria; los casos de prueba, en AcentoTests). La inmobiliaria ve su color en los botones, los links y lo
 *  elegido; fondos y textos siguen siendo los de Corredor.
 *
 *  - Claro: el color principal, oscurecido (mezcla con negro, de a 8 %) hasta que el blanco encima pase AA (4.5:1).
 *  - Oscuro: el de acento (o el principal), aclarado (mezcla con blanco, de a 8 %) hasta pasar AA contra la
 *    superficie oscura (#1b1e20) y con la tinta oscura encima (#1a1405).
 *  - Sin color o sin llegar en 24 pasos: null (quedan el verde y el oro de Corredor). */
data class Acento(val claro: Int, val claroHondo: Int, val oscuro: Int, val oscuroHondo: Int) {
    /** Un color en RGB de 0 a 255 (el `Rgb` de iOS). */
    data class Rgb(val r: Double, val g: Double, val b: Double)

    companion object {
        fun rgb(texto: String?): Rgb? {
            var h = texto?.trim()?.lowercase() ?: return null
            if (h.startsWith("#")) h = h.drop(1)
            if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
            if (h.length != 6 || !h.all { it.isDigit() || it in 'a'..'f' }) return null
            val n = h.toIntOrNull(16) ?: return null
            return Rgb(((n shr 16) and 0xFF).toDouble(), ((n shr 8) and 0xFF).toDouble(), (n and 0xFF).toDouble())
        }

        /** Redondea cada canal (como `hexDe` del panel: Math.round, que para positivos es lo mismo). */
        fun hex(c: Rgb): Int {
            fun r(v: Double): Int = max(0.0, min(255.0, v)).let { x -> kotlin.math.floor(x + 0.5).toInt() }
            return (r(c.r) shl 16) or (r(c.g) shl 8) or r(c.b)
        }

        fun rgb(n: Int): Rgb = Rgb(((n shr 16) and 0xFF).toDouble(), ((n shr 8) and 0xFF).toDouble(), (n and 0xFF).toDouble())

        fun luminancia(c: Rgb): Double {
            fun l(v: Double): Double { val s = v / 255; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * l(c.r) + 0.7152 * l(c.g) + 0.0722 * l(c.b)
        }

        fun contraste(a: Int, b: Int): Double {
            val x = luminancia(rgb(a)); val y = luminancia(rgb(b))
            return (max(x, y) + 0.05) / (min(x, y) + 0.05)
        }

        /** Mezcla `a` con `b` (t: cuánto de `b`) y redondea, como el panel. */
        fun mezclar(a: Int, b: Int, t: Double): Int {
            val x = rgb(a); val y = rgb(b)
            return hex(Rgb(x.r + (y.r - x.r) * t, x.g + (y.g - x.g) * t, x.b + (y.b - x.b) * t))
        }

        private const val blanco = 0xFFFFFF
        private const val negro = 0x000000
        private const val superficieOscura = 0x1B1E20
        private const val tintaOscura = 0x1A1405
        private const val paso = 0.08
        private const val pasos = 24

        fun paraClaro(c: Int): Int? {
            var x = c
            var i = 0
            while (i < pasos && contraste(x, blanco) < 4.5) { x = mezclar(x, negro, paso); i += 1 }
            return if (contraste(x, blanco) >= 4.5) x else null
        }

        fun paraOscuro(c: Int): Int? {
            fun pasa(y: Int) = contraste(y, superficieOscura) >= 4.5 && contraste(y, tintaOscura) >= 4.5
            var x = c
            var i = 0
            while (i < pasos && !pasa(x)) { x = mezclar(x, blanco, paso); i += 1 }
            return if (pasa(x)) x else null
        }

        /** El acento de una marca, o null para dejar el de Corredor. */
        fun de(primario: String?, secundario: String?): Acento? {
            val p = rgb(primario)?.let { hex(it) } ?: return null
            val claro = paraClaro(p) ?: return null
            val oscuro = paraOscuro(rgb(secundario)?.let { hex(it) } ?: p) ?: return null
            return Acento(claro, mezclar(claro, negro, 0.12), oscuro, mezclar(oscuro, negro, 0.08))
        }

        /** El de la inmobiliaria abierta (lo pone `Estado` con la marca publicada; null: el de Corredor). Los colores
         *  de `C` lo leen al pintarse (es estado de Compose: si cambia, se repinta). */
        var actual by mutableStateOf<Acento?>(null)

        /** La acción de la app: el acento de la inmobiliaria, o el verde (claro) y el oro (oscuro) de Corredor. */
        fun accion(oscuro: Boolean): Color {
            val a = actual
            return hex(if (a != null) (if (oscuro) a.oscuro else a.claro) else (if (oscuro) 0xF2BB57 else 0x16443B))
        }

        private fun hex(v: Int): Color = ar.com.corredor.app.tema.hex(v)

        @Suppress("unused")
        private fun redondo(v: Double) = v.roundToInt()
    }
}
