package ar.com.corredor.app.pantallas.informes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.StyleSpan
import androidx.core.content.res.ResourcesCompat
import ar.com.corredor.app.R
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mensajes.Charla
import ar.com.corredor.app.tema.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Clon de Pantallas/Informes/InformeMensual.swift. Las cuatro páginas A4 que en iOS se dibujan con SwiftUI
// (ImageRenderer → CGContext PDF) acá se dibujan con el Canvas de android.graphics.pdf.PdfDocument, con las mismas
// medidas (595 × 842 puntos), colores y textos.

/** GET /v1/informe/mensual (panel.Mensual): el mes, el anterior, las cuentas de cada uno, la serie de honorarios, el
 *  equipo, las caídas y las rendiciones. */
@Serializable
data class InformeMensualAPI(
    val mes: String,
    val anterior: String,
    val actual: Cuentas,
    val mesAnterior: Cuentas,
    val inmobiliaria: String? = null,
    val serie: List<Punto>? = null,
    val moneda: String? = null,
    val equipo: List<Agente>? = null,
    val caidas: List<Caida>? = null,
    val rendiciones: Rendiciones? = null,
) {
    @Serializable
    data class Cuentas(
        val consultas: Int? = null, val visitas: Int? = null, val reservas: Int? = null, val cierres: Int? = null, val captaciones: Int? = null, val caidas: Int? = null,
        val honorarios: Map<String, Double>? = null,
    )
    @Serializable data class Punto(val mes: String, val monto: Double)
    @Serializable
    data class Agente(
        val id: String, val nombre: String, val rol: String? = null, val captadas: Int? = null, val visitas: Int? = null, val cierres: Int? = null,
        val honorarios: Map<String, Double>? = null,
    )
    @Serializable data class Caida(val motivo: String, val cantidad: Int)
    @Serializable
    data class Rendiciones(val cantidad: Int? = null, val emitidas: Int? = null, val neto: Map<String, Double>? = null, val comision: Map<String, Double>? = null, val pendientes: Int? = null)
}

/** Lo de cobranzas que usa el informe (GET /v1/cobranzas?mes=). */
@Serializable
data class CobranzasInforme(
    val cobradas: List<Fila>? = null,
    val pendientes: List<Fila>? = null,
    val totalCobrado: Map<String, Double>? = null,
    val totalPendiente: Map<String, Double>? = null,
    val totalComision: Map<String, Double>? = null,
) {
    @Serializable data class Fila(val monto: Double? = null, val estado: String? = null, val comision: Double? = null)
}

/** Los datos del informe ya juntados (lo que arma la página de impresión de la web). */
data class DatosInforme(
    val m: InformeMensualAPI,
    val cobrado: Double,
    val pendiente: Double,
    val atrasados: Int,
    val comision: Double,
    val comisionAnterior: Double,
    val comisionPorcentaje: Double?,
    val ajustan: Int,
    val suba: Double?,
    val vencen: Int,
    val limite: String,
    val qr: Bitmap?,
)

/** Las frases del informe, como la web (lib/impresos/informe.ts): salen de los números. */
object TextosInforme {
    fun nombreMes(m: String): String = m.drop(5).take(2).toIntOrNull()?.let { Ritmo.meses[max(0, min(11, it - 1))] } ?: m

    fun siguiente(m: String): String {
        val p = m.split("-").mapNotNull { it.toIntOrNull() }
        if (p.size < 2) return m
        return if (p[1] == 12) String.format("%04d-01", p[0] + 1) else String.format("%04d-%02d", p[0], p[1] + 1)
    }

    fun anterior(m: String): String {
        val p = m.split("-").mapNotNull { it.toIntOrNull() }
        if (p.size < 2) return m
        return if (p[1] == 1) String.format("%04d-12", p[0] - 1) else String.format("%04d-%02d", p[0], p[1] - 1)
    }

    private fun dec(n: Double): String {
        val f = NumberFormat.getNumberInstance(Formato.argentina)
        f.maximumFractionDigits = 1
        return f.format(n)
    }

    /** 11200 → «11,2 k»; 34870000 → «34,9 M». */
    fun corto(n: Double): String {
        if (abs(n) >= 1e6) return "${dec(n / 1e6)} M"
        if (abs(n) >= 1e3) return "${dec(n / 1e3)} k"
        return dec(n)
    }

    fun kBarra(n: Double): String = if (n >= 1e3) "${dec(n / 1e3)}k" else dec(n)

    /** «+2», «−8 %», o nil si no hay con qué comparar. */
    fun diferencia(actual: Double, antes: Double, porcentaje: Boolean): Pair<String, Boolean>? {
        if (porcentaje) {
            if (antes == 0.0) return null
            val p = ((actual - antes) / antes * 100).roundToInt()
            return "${if (p > 0) "+" else if (p < 0) "−" else "±"}${abs(p)}${Formato.fino}%" to (p >= 0)
        }
        val d = (actual - antes).toInt()
        return "${if (d > 0) "+" else if (d < 0) "−" else "±"}${abs(d)}" to (d >= 0)
    }

    fun fraseDelMes(m: InformeMensualAPI): String {
        val a = m.actual; val b = m.mesAnterior
        val ac = a.cierres ?: 0; val bc = b.cierres ?: 0
        val serie = m.serie ?: emptyList()
        val mejor = serie.size > 1 && ac > 0 && (serie.lastOrNull()?.monto ?: 0.0) >= (serie.maxOfOrNull { it.monto } ?: 0.0)
        val cierres = when {
            mejor -> "El mejor mes del semestre en honorarios"
            ac > bc -> "Más cierres que en ${nombreMes(m.anterior)}"
            ac < bc -> "Menos cierres que en ${nombreMes(m.anterior)}"
            ac > 0 -> "Los mismos cierres que en ${nombreMes(m.anterior)}"
            else -> "Un mes sin cierres"
        }
        val aca = a.captaciones ?: 0; val bca = b.captaciones ?: 0
        val capta = when {
            aca < bca -> "con las captaciones atrás del ritmo"
            aca > bca -> "y con más captaciones"
            aca > 0 -> "y las captaciones al mismo ritmo"
            else -> "y sin captaciones nuevas"
        }
        return "$cierres, $capta."
    }

    fun tituloResumen(m: InformeMensualAPI): String {
        val ac = m.actual.cierres ?: 0; val bc = m.mesAnterior.cierres ?: 0
        if (ac > bc && (m.actual.consultas ?: 0) < (m.mesAnterior.consultas ?: 0)) return "Más cierres con menos consultas: convirtieron mejor."
        if (ac > bc) return "Más cierres que en ${nombreMes(m.anterior)}."
        if (ac < bc) return "Menos cierres que en ${nombreMes(m.anterior)}: $ac contra $bc."
        return if (ac > 0) "$ac cierres, como en ${nombreMes(m.anterior)}." else "Un mes sin cierres."
    }

    fun tituloEquipo(m: InformeMensualAPI): String {
        val total = m.actual.cierres ?: 0
        val eq = m.equipo ?: emptyList()
        val p = eq.firstOrNull()
        if (p == null || (p.cierres ?: 0) <= 0) {
            return if (eq.isEmpty()) "Sin movimiento del equipo este mes." else "Nadie cerró este mes: el equipo, en visitas y captaciones."
        }
        val nombre = Charla.primerNombre(p.nombre)
        val pc = p.cierres ?: 0
        val parte = if (pc * 2 >= total && total > 1) "cerró la mitad" else if (pc == total) "hizo todos los cierres" else "encabezó los cierres"
        if (eq.size > 1 && (eq[1].cierres ?: 0) > 0) return "$nombre $parte; ${Charla.primerNombre(eq[1].nombre)} lo siguió."
        return "$nombre $parte."
    }

    fun motivo(m: String): String {
        val t = m.trim()
        return if (t.lowercase() == "precio") "por precio" else t.take(1).lowercase() + t.drop(1)
    }

    /** El QR (CIQRCodeGenerator en iOS), con módulos de 8 px. */
    fun qr(texto: String): Bitmap? = runCatching {
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0)
        val lado = 8
        val b = Bitmap.createBitmap(m.width * lado, m.height * lado, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(android.graphics.Color.WHITE)
        val p = Paint().apply { color = android.graphics.Color.BLACK }
        for (x in 0 until m.width) for (y in 0 until m.height) if (m[x, y]) c.drawRect((x * lado).toFloat(), (y * lado).toFloat(), ((x + 1) * lado).toFloat(), ((y + 1) * lado).toFloat(), p)
        b
    }.getOrNull()
}

/** Arma el informe mensual de 4 páginas (A4) como la ruta de impresión de la web: portada, «Cómo nos fue», «Quién
 *  empujó» y «Cuánta plata entró», con el QR al informe en Corredor. */
object InformeMensualPDF {
    @Serializable private data class Evento(val contratoId: String, val tipo: String, val fecha: String, val monto: Double? = null)
    @Serializable private data class Ajuste(val vigente: Double? = null)

    suspend fun juntar(api: Api, mes: String?): DatosInforme {
        val m: InformeMensualAPI = api.get("/v1/informe/mensual", mes?.let { listOf("mes" to it) } ?: emptyList())
        val cob = api.opcional<CobranzasInforme>("/v1/cobranzas", listOf("mes" to m.mes))
        val cobAntes = api.opcional<CobranzasInforme>("/v1/cobranzas", listOf("mes" to m.anterior))
        val eventos = api.opcional<List<Evento>>("/v1/contratos/vencimientos", listOf("dias" to "120")) ?: emptyList()
        val prox = TextosInforme.siguiente(m.mes)
        val ajustes = eventos.filter { it.tipo == "ajuste" && it.fecha.startsWith(prox) && (it.monto ?: 0.0) > 0 }
        val subas = mutableListOf<Double>()
        for (e in ajustes.take(30)) {
            val a = api.opcional<Ajuste>("/v1/contratos/${e.contratoId}/ajuste")
            val v = a?.vigente
            val n = e.monto
            if (v != null && v > 0 && n != null) subas.add((n - v) / v)
        }
        val limite = TextosInforme.siguiente(TextosInforme.siguiente(TextosInforme.siguiente(prox)))
        val vencen = eventos.count { it.tipo == "vencimiento" && it.fecha.take(7) < limite }
        val conComision = cob?.cobradas?.firstOrNull { it.comision != null && (it.monto ?: 0.0) > 0 }
        return DatosInforme(
            m = m, cobrado = cob?.totalCobrado?.get("ARS") ?: 0.0, pendiente = cob?.totalPendiente?.get("ARS") ?: 0.0,
            atrasados = (cob?.pendientes ?: emptyList()).count { it.estado == "atrasado" },
            comision = cob?.totalComision?.get("ARS") ?: 0.0, comisionAnterior = cobAntes?.totalComision?.get("ARS") ?: 0.0,
            comisionPorcentaje = conComision?.let { Math.round((it.comision ?: 0.0) / (it.monto ?: 1.0) * 1000) / 10.0 },
            ajustan = ajustes.size, suba = if (subas.isEmpty()) null else subas.sum() / subas.size * 100,
            vencen = vencen, limite = limite, qr = TextosInforme.qr("${Api.web().toString().trimEnd('/')}/informe?mes=${m.mes}"),
        )
    }

    /** El PDF en la caché que comparte el FileProvider (cache/compartir). */
    suspend fun pdf(ctx: Context, d: DatosInforme): File? = withContext(Dispatchers.Default) {
        try {
            val carpeta = File(ctx.cacheDir, "compartir").apply { mkdirs() }
            val archivo = File(carpeta, "Informe de ${TextosInforme.nombreMes(d.m.mes)} ${d.m.mes.take(4)}.pdf")
            // Los íconos (Image(systemName:)) se dibujan con la misma letra de símbolos que usa `Icono`.
            letrasSimbolos = runCatching {
                (ResourcesCompat.getFont(ctx, R.font.simbolos_contorno) ?: Typeface.DEFAULT) to (ResourcesCompat.getFont(ctx, R.font.simbolos_relleno) ?: Typeface.DEFAULT)
            }.getOrNull()
            val doc = PdfDocument()
            val paginas: List<(Canvas) -> Unit> = listOf({ portadaInforme(it, d) }, { resumenInforme(it, d) }, { equipoInforme(it, d) }, { plataInforme(it, d) })
            paginas.forEachIndexed { i, dibujar ->
                val p = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, i + 1).create())
                p.canvas.drawColor(android.graphics.Color.WHITE)
                dibujar(p.canvas)
                doc.finishPage(p)
            }
            archivo.outputStream().use { doc.writeTo(it) }
            doc.close()
            archivo
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }
}

// MARK: - Las páginas

private const val ANCHO = 595
private const val ALTO = 842

private val verde = 0xFF16443B.toInt()
private val tinta = 0xFF18201D.toInt()
private val gris = 0xFF6B6457.toInt()
private val lineaA4 = 0xFFE3DDCF.toInt()
private val oroA4 = 0xFFF2BB57.toInt()
private val blanco = 0xFFFFFFFF.toInt()

private fun alfa(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a * 255).roundToInt() shl 24)
private fun hexA4(v: Long): Int = (0xFF000000 or v).toInt()

/** La letra del sistema (`.system(size:weight:)`): 400, 600, 700 o 900; `serif` es la de la frase en itálica. */
private fun pintura(tam: Float, peso: Int = 400, color: Int = tinta, tracking: Float = 0f, serif: Boolean = false): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = tam
    this.color = color
    typeface = when {
        serif -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        Build.VERSION.SDK_INT >= 28 -> Typeface.create(Typeface.SANS_SERIF, peso, false)
        else -> Typeface.create(Typeface.SANS_SERIF, if (peso >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    if (tracking != 0f) letterSpacing = tracking / tam
}

private fun armar(texto: CharSequence, p: TextPaint, ancho: Float, alinear: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, lineas: Int = Int.MAX_VALUE): StaticLayout =
    StaticLayout.Builder.obtain(texto, 0, texto.length, p, max(1, ancho.toInt()))
        .setAlignment(alinear).setMaxLines(lineas).setEllipsize(if (lineas == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
        .setIncludePad(false).build()

/** Dibuja el texto arriba a la izquierda en (x, y) y devuelve su alto. */
private fun Canvas.texto(l: StaticLayout, x: Float, y: Float): Float {
    save(); translate(x, y); l.draw(this); restore()
    return l.height.toFloat()
}

private fun Canvas.texto(s: CharSequence, p: TextPaint, x: Float, y: Float, ancho: Float, alinear: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, lineas: Int = Int.MAX_VALUE): Float =
    texto(armar(s, p, ancho, alinear, lineas), x, y)

/** Un renglón con el tamaño achicado hasta entrar (lineLimit(1).minimumScaleFactor). */
private fun Canvas.textoAjustado(s: String, tam: Float, peso: Int, color: Int, x: Float, y: Float, ancho: Float, escalaMinima: Float): Float {
    var t = tam
    while (t > tam * escalaMinima && pintura(t, peso).measureText(s) > ancho) t -= 0.5f
    return texto(s, pintura(t, peso, color), x, y, ancho, lineas = 1)
}

private fun Canvas.caja(x: Float, y: Float, w: Float, h: Float, radio: Float, color: Int) {
    drawRoundRect(RectF(x, y, x + w, y + h), radio, radio, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}

/** Las dos letras de símbolos (contorno, relleno); las carga `pdf` antes de dibujar. */
private var letrasSimbolos: Pair<Typeface, Typeface>? = null

/** Un ícono por su nombre de SF Symbol, como `Icono` (mismo mapa y misma escala): «.fill» elige la variante rellena.
 *  Lo dibuja arriba a la izquierda en (x, y) y devuelve su ancho. `tam` es el tamaño de iOS (sin `.font`, 17). */
private fun Canvas.simbolo(nombre: String, tam: Float, color: Int, x: Float, y: Float): Float {
    val letras = letrasSimbolos ?: return 0f
    val partes = nombre.split('.')
    val relleno = "fill" in partes
    val base = partes.filter { it != "fill" }.joinToString(".")
    val c = Simbolos.mapa[base] ?: Simbolos.mapa[nombre] ?: Simbolos.mapa[base.substringBeforeLast('.')] ?: return 0f
    val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam * 1.18f
        this.color = color
        typeface = if (relleno) letras.second else letras.first
    }
    val lado = tam * 1.18f
    // La letra de símbolos tiene el glifo en el cuadro de 1 em: se centra en un cuadrado de `lado`.
    val fm = p.fontMetrics
    drawText(c.toString(), x, y + (lado - (fm.descent + fm.ascent)) / 2, p)
    return p.measureText(c.toString())
}

/** Texto en negrita + texto normal (Text(…).bold() + Text(…)). */
private fun rico(fuerte: String, resto: String): SpannableStringBuilder = SpannableStringBuilder().apply {
    append(fuerte, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    append(resto)
}

private fun mesAnio(d: DatosInforme): String = "${TextosInforme.nombreMes(d.m.mes)} ${d.m.mes.take(4)}".uppercase(Formato.argentina)

/** La cabeza de las páginas 2 a 4: «Corredor», la inmobiliaria · mes · n/4, la línea, el kicker y el título. Devuelve
 *  dónde termina. */
private fun cabezaInforme(c: Canvas, d: DatosInforme, n: Int, kicker: String, titulo: String, x: Float, y0: Float, ancho: Float): Float {
    var y = y0
    val marca = armar("Corredor", pintura(14f, 900, verde), ancho)
    val derecha = armar("${d.m.inmobiliaria ?: ""} · ${mesAnio(d)} · $n/4".uppercase(Formato.argentina), pintura(9f, 600, gris, tracking = 0.5f), ancho * 0.7f, Layout.Alignment.ALIGN_OPPOSITE, 1)
    val altoFila = max(marca.height, derecha.height).toFloat()
    c.texto(marca, x, y + (altoFila - marca.height) / 2)
    c.texto(derecha, x + ancho * 0.3f, y + (altoFila - derecha.height) / 2)
    y += altoFila + 10
    c.drawRect(x, y, x + ancho, y + 1, Paint().apply { color = lineaA4 })
    y += 1 + 10 + 8
    y += c.texto(kicker, pintura(10f, 700, gris, tracking = 0.8f), x, y, ancho) + 10
    y += c.texto(titulo, pintura(22f, 700, tinta), x, y, ancho)
    return y
}

private fun porcentajeCobrado(d: DatosInforme): Int {
    val total = d.cobrado + d.pendiente
    return if (total > 0) (d.cobrado / total * 100).roundToInt() else 0
}

private fun portadaInforme(c: Canvas, d: DatosInforme) {
    val a = d.m.actual; val b = d.m.mesAnterior
    val moneda = d.m.moneda ?: "USD"
    val hon = a.honorarios?.get(moneda) ?: 0.0
    val dC = TextosInforme.diferencia((a.cierres ?: 0).toDouble(), (b.cierres ?: 0).toDouble(), porcentaje = false)
    val dH = TextosInforme.diferencia(hon, b.honorarios?.get(moneda) ?: 0.0, porcentaje = true)
    val total = d.cobrado + d.pendiente
    c.drawColor(verde)
    val m = 40f
    val ancho = ANCHO - 2 * m
    // Arriba: «Corredor» e «INFORME MENSUAL».
    val marca = armar("Corredor", pintura(16f, 900, blanco), ancho)
    val etiqueta = armar("INFORME MENSUAL", pintura(10f, 700, alfa(blanco, 0.8f), tracking = 1f), ancho, Layout.Alignment.ALIGN_OPPOSITE, 1)
    val altoArriba = max(marca.height, etiqueta.height).toFloat()
    c.texto(marca, m, m + (altoArriba - marca.height) / 2)
    c.texto(etiqueta, m, m + (altoArriba - etiqueta.height) / 2)
    // El medio: el mes, la inmobiliaria y la frase.
    val lMes = armar(mesAnio(d), pintura(12f, 700, oroA4, tracking = 1f), ancho)
    val lInmo = armar(d.m.inmobiliaria ?: "", pintura(34f, 700, blanco), ancho)
    val lFrase = armar(TextosInforme.fraseDelMes(d.m), pintura(22f, color = alfa(blanco, 0.92f), serif = true), ancho)
    val altoMedio = lMes.height + 6f + lInmo.height + 10f + lFrase.height
    // Abajo: los tres cuadros.
    data class Cuadro(val t: String, val v: String, val det: String?, val oro: Boolean)
    val cuadros = listOf(
        Cuadro("Cierres", "${a.cierres ?: 0}", dC?.let { "${it.first} vs ${TextosInforme.nombreMes(d.m.anterior)}" }, false),
        Cuadro("Honorarios", "$moneda ${TextosInforme.corto(hon)}", dH?.first, false),
        Cuadro("Cobrado", "${porcentajeCobrado(d)}${Formato.fino}%", "ARS ${TextosInforme.corto(d.cobrado)} de ${TextosInforme.corto(total)}", true),
    )
    val anchoCuadro = (ancho - 12f * 2) / 3
    val interior = anchoCuadro - 28f
    fun altoCuadro(q: Cuadro): Float {
        var h = 28f + armar(q.t, pintura(10f, 700), interior).height + 4f + pintura(24f, 700).let { it.fontMetrics.descent - it.fontMetrics.ascent }
        if (q.det != null) h += 4f + armar(q.det, pintura(9f), interior).height
        return h
    }
    val altoCuadros = cuadros.maxOf { altoCuadro(it) }
    val libre = (ALTO - 2 * m - altoArriba - altoMedio - altoCuadros).coerceAtLeast(0f)
    var y = m + altoArriba + libre / 2
    y += c.texto(lMes, m, y) + 6f
    y += c.texto(lInmo, m, y) + 10f
    c.texto(lFrase, m, y)
    val yc = ALTO - m - altoCuadros
    cuadros.forEachIndexed { i, q ->
        val x = m + i * (anchoCuadro + 12f)
        c.caja(x, yc, anchoCuadro, altoCuadros, 12f, if (q.oro) oroA4 else alfa(blanco, 0.1f))
        var yy = yc + 14f
        yy += c.texto(q.t, pintura(10f, 700, if (q.oro) alfa(tinta, 0.7f) else alfa(blanco, 0.7f)), x + 14f, yy, interior) + 4f
        yy += c.textoAjustado(q.v, 24f, 700, if (q.oro) tinta else blanco, x + 14f, yy, interior, 0.6f) + 4f
        q.det?.let { c.texto(it, pintura(9f, color = if (q.oro) alfa(tinta, 0.7f) else alfa(blanco, 0.75f)), x + 14f, yy, interior) }
    }
}

private fun resumenInforme(c: Canvas, d: DatosInforme) {
    val a = d.m.actual; val b = d.m.mesAnterior
    data class Cuadro(val t: String, val n: Int, val dif: Pair<String, Boolean>?, val fuerte: Boolean)
    val cuadros = listOf(
        Cuadro("Consultas", a.consultas ?: 0, TextosInforme.diferencia((a.consultas ?: 0).toDouble(), (b.consultas ?: 0).toDouble(), porcentaje = true), false),
        Cuadro("Visitas", a.visitas ?: 0, TextosInforme.diferencia((a.visitas ?: 0).toDouble(), (b.visitas ?: 0).toDouble(), porcentaje = true), false),
        Cuadro("Reservas", a.reservas ?: 0, TextosInforme.diferencia((a.reservas ?: 0).toDouble(), (b.reservas ?: 0).toDouble(), porcentaje = false), false),
        Cuadro("Cierres", a.cierres ?: 0, TextosInforme.diferencia((a.cierres ?: 0).toDouble(), (b.cierres ?: 0).toDouble(), porcentaje = false), true),
    )
    val embudo = listOf(Triple("Consultas", a.consultas ?: 0, 0xD6E2DCL), Triple("Visitas", a.visitas ?: 0, 0x8FB0A3L),
        Triple("Reservas", a.reservas ?: 0, 0x3F7A6AL), Triple("Cierres", a.cierres ?: 0, 0x16443BL))
    val tope = max(1, embudo.maxOf { it.second }).toDouble()
    val serie = d.m.serie ?: emptyList()
    val topeSerie = max(1.0, serie.maxOfOrNull { it.monto } ?: 1.0)
    val anterior = TextosInforme.nombreMes(d.m.anterior)
    val consejo: String? = run {
        val ac = a.captaciones ?: 0; val bc = b.captaciones ?: 0
        if (ac < bc && bc > 0) return@run "las captaciones vienen ${((1 - ac.toDouble() / bc) * 100).roundToInt()}${Formato.fino}% atrás de $anterior."
        val ca = d.m.caidas?.firstOrNull()
        if ((a.caidas ?: 0) > 0 && ca != null) return@run "${Formato.cantidad(ca.cantidad, "operación se cayó", "operaciones se cayeron")} ${TextosInforme.motivo(ca.motivo)}."
        null
    }
    val m = 36f
    val ancho = ANCHO - 2 * m
    var y = cabezaInforme(c, d, 2, "01 · CÓMO NOS FUE", TextosInforme.tituloResumen(d.m), m, m, ancho) + 18f
    // Los cuatro cuadros.
    val anchoCuadro = (ancho - 10f * 3) / 4
    val interior = anchoCuadro - 24f
    val altoCuadro = 24f + armar("X", pintura(10f, 600), interior).height + 3f + pintura(24f, 700).let { it.fontMetrics.descent - it.fontMetrics.ascent } +
        (if (cuadros.any { it.dif != null }) 3f + armar("X", pintura(10f, 700), interior).height else 0f)
    cuadros.forEachIndexed { i, q ->
        val x = m + i * (anchoCuadro + 10f)
        c.caja(x, y, anchoCuadro, altoCuadro, 10f, if (q.fuerte) verde else hexA4(0xF5F1E8))
        var yy = y + 12f
        yy += c.texto(q.t, pintura(10f, 600, if (q.fuerte) alfa(blanco, 0.8f) else gris), x + 12f, yy, interior) + 3f
        yy += c.texto(Formato.numero(q.n), pintura(24f, 700, if (q.fuerte) blanco else tinta), x + 12f, yy, interior, lineas = 1) + 3f
        q.dif?.let { c.texto(it.first, pintura(10f, 700, if (q.fuerte) oroA4 else (if (it.second) hexA4(0x2F7A4F) else hexA4(0xB33A3A))), x + 12f, yy, interior) }
    }
    y += altoCuadro + 18f
    // El embudo.
    y += c.texto("Embudo de ${TextosInforme.nombreMes(d.m.mes)}", pintura(12f, 700, tinta), m, y, ancho) + 8f
    embudo.forEachIndexed { i, e ->
        val anchoBarra = ancho - 64f - 10f - 10f - 40f
        c.texto(e.first, pintura(10f, color = gris), m, y + 1f, 64f, lineas = 1)
        val w = if (i == 0) anchoBarra else max(if (e.second > 0) anchoBarra * 0.02f else 0f, anchoBarra * (e.second / tope).toFloat())
        if (w > 0f) c.caja(m + 74f, y, w, 16f, 4f, hexA4(e.third))
        c.texto(Formato.numero(e.second), pintura(11f, 700, tinta), m + ancho - 40f, y, 40f, Layout.Alignment.ALIGN_OPPOSITE, 1)
        y += 16f + 8f
    }
    y += 18f - 8f
    // Los honorarios de los últimos 6 meses.
    y += c.texto("Honorarios de los últimos 6 meses · ${d.m.moneda ?: "USD"}", pintura(12f, 700, tinta), m, y, ancho) + 8f
    val altoGrafico = 150f
    if (serie.isNotEmpty()) {
        val anchoCol = (ancho - 12f * (serie.size - 1)) / serie.size
        serie.forEachIndexed { i, s ->
            val ultimo = i == serie.size - 1
            val x = m + i * (anchoCol + 12f)
            val etiqueta = armar(TextosInforme.kBarra(s.monto), pintura(9f, if (ultimo) 700 else 400, tinta), anchoCol, Layout.Alignment.ALIGN_CENTER, 1)
            val mes = armar(TextosInforme.nombreMes(s.mes).take(3), pintura(9f, if (ultimo) 700 else 400, gris), anchoCol, Layout.Alignment.ALIGN_CENTER, 1)
            val alto = max(2f, (s.monto / topeSerie).toFloat() * 110f)
            val abajo = y + altoGrafico
            c.texto(mes, x, abajo - mes.height)
            val yb = abajo - mes.height - 4f - alto
            c.caja(x, yb, anchoCol, alto, 4f, if (ultimo) verde else hexA4(0xC9D8D1))
            c.texto(etiqueta, x, yb - 4f - etiqueta.height)
        }
    }
    y += altoGrafico + 18f
    // El consejo para el mes que viene.
    if (consejo != null) {
        val texto = rico("Para ${TextosInforme.nombreMes(TextosInforme.siguiente(d.m.mes))}:", " $consejo")
        // HStack(alignment: .top, spacing: 8): el foco (17 de iOS, sin .font) y la frase de 11.
        val lado = 17f * 1.18f
        val l = armar(texto, pintura(11f, color = tinta), ancho - 24f - lado - 8f)
        val alto = max(l.height.toFloat(), lado) + 24f
        c.caja(m, y, ancho, alto, 10f, hexA4(0xFBEFD5))
        val w = c.simbolo("lightbulb.fill", 17f, hexA4(0x7A5200), m + 12f, y + 12f).takeIf { it > 0f } ?: lado
        c.texto(l, m + 12f + w + 8f, y + 12f)
    }
}

private fun equipoInforme(c: Canvas, d: DatosInforme) {
    val moneda = d.m.moneda ?: "USD"
    val eq = d.m.equipo ?: emptyList()
    val caidas = (d.m.caidas ?: emptyList()).take(3)
    val totalCaidas = (d.m.caidas ?: emptyList()).sumOf { it.cantidad }
    val m = 36f
    val ancho = ANCHO - 2 * m
    var y = cabezaInforme(c, d, 3, "02 · QUIÉN EMPUJÓ", TextosInforme.tituloEquipo(d.m), m, m, ancho) + 18f
    // La tabla.
    val filas = mutableListOf<Triple<List<String>, Boolean, Boolean>>()
    filas.add(Triple(listOf("Agente", "Captó", "Visitas", "Cierres", "Honorarios"), true, false))
    eq.forEachIndexed { i, x ->
        filas.add(Triple(listOf(x.nombre, "${x.captadas ?: 0}", "${x.visitas ?: 0}", "${x.cierres ?: 0}",
            x.honorarios?.get(moneda)?.let { if (it > 0) Formato.plata(it, moneda) else "—" } ?: "—"), false, i % 2 == 1))
    }
    filas.add(Triple(listOf("Equipo", "${eq.sumOf { it.captadas ?: 0 }}", "${eq.sumOf { it.visitas ?: 0 }}", "${eq.sumOf { it.cierres ?: 0 }}",
        Formato.plata(eq.sumOf { it.honorarios?.get(moneda) ?: 0.0 }, moneda)), true, false))
    val yTabla = y
    val anchos = listOf(50f, 50f, 50f, 110f)
    val anchoNombre = ancho - 24f - anchos.sum() - 8f * 4
    c.save()
    val forma = android.graphics.Path().apply { addRoundRect(RectF(m, yTabla, m + ancho, yTabla + 10000f), 10f, 10f, android.graphics.Path.Direction.CW) }
    c.clipPath(forma)
    filas.forEach { (celdas, encabezado, par) ->
        val color = if (encabezado) gris else tinta
        // Text(a).fontWeight(.semibold) pisa el peso del .font de la fila: el nombre va en 600 también en el encabezado.
        val nombre = armar(celdas[0], pintura(11f, 600, color), anchoNombre, lineas = 1)
        val alto = nombre.height + 18f
        c.drawRect(m, y, m + ancho, y + alto, Paint().apply { this.color = if (par) hexA4(0xF8F5EE) else (if (encabezado) hexA4(0xF5F1E8) else blanco) })
        c.texto(nombre, m + 12f, y + 9f)
        var x = m + 12f + anchoNombre
        celdas.drop(1).forEachIndexed { k, t ->
            x += 8f
            c.texto(t, pintura(11f, if (encabezado) 700 else 400, color), x, y + 9f, anchos[k], Layout.Alignment.ALIGN_OPPOSITE, 1)
            x += anchos[k]
        }
        y += alto
    }
    c.restore()
    c.drawRoundRect(RectF(m + 0.5f, yTabla + 0.5f, m + ancho - 0.5f, y - 0.5f), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = lineaA4 })
    y += 18f
    // Las caídas.
    y += c.texto("Operaciones que se cayeron · $totalCaidas", pintura(12f, 700, tinta), m, y, ancho) + 8f
    if (caidas.isEmpty()) {
        c.texto("Ninguna operación se cayó en ${TextosInforme.nombreMes(d.m.mes)}.", pintura(11f, color = gris), m, y, ancho)
    } else {
        val anchoCaja = (ancho - 10f * (caidas.size - 1)) / caidas.size
        val interior = anchoCaja - 24f
        val alto = 24f + pintura(24f, 700).let { it.fontMetrics.descent - it.fontMetrics.ascent } + 4f +
            caidas.maxOf { armar(TextosInforme.motivo(it.motivo), pintura(10f), interior).height }
        caidas.forEachIndexed { i, ca ->
            val x = m + i * (anchoCaja + 10f)
            c.caja(x, y, anchoCaja, alto, 10f, if (i == 0) hexA4(0xFBE3E3) else hexA4(0xF5F1E8))
            var yy = y + 12f
            yy += c.texto("${ca.cantidad}", pintura(24f, 700, if (i == 0) hexA4(0xB33A3A) else tinta), x + 12f, yy, interior, lineas = 1) + 4f
            c.texto(TextosInforme.motivo(ca.motivo), pintura(10f, color = gris), x + 12f, yy, interior)
        }
    }
}

private fun plataInforme(c: Canvas, d: DatosInforme) {
    val pct = porcentajeCobrado(d)
    val anterior = TextosInforme.nombreMes(d.m.anterior)
    val r = d.m.rendiciones
    val dCom = TextosInforme.diferencia(d.comision, d.comisionAnterior, porcentaje = true)
    val proximo = TextosInforme.nombreMes(TextosInforme.siguiente(d.m.mes))
    val titulo = "Se cobró el $pct${Formato.fino}%." + (if (d.atrasados > 0) " Quedan ${Formato.cantidad(d.atrasados, "inquilino atrasado", "inquilinos atrasados")}." else " Nadie quedó atrasado.")
    val m = 36f
    val ancho = ANCHO - 2 * m
    var y = cabezaInforme(c, d, 4, "03 · CUÁNTA PLATA ENTRÓ", titulo, m, m, ancho) + 18f
    // La barra de lo cobrado.
    c.caja(m, y, ancho, 22f, 8f, hexA4(0xF2D6D6))
    if (pct > 0) c.caja(m, y, ancho * pct / 100f, 22f, 8f, verde)
    y += 22f + 18f
    // Cobrado y pendiente.
    val cobrado = armar(rico(Formato.plata(d.cobrado, "ARS"), " cobrado"), pintura(11f, color = tinta), ancho / 2)
    c.texto(cobrado, m, y)
    if (d.pendiente > 0) {
        val anchoCobrado = (0 until cobrado.lineCount).maxOf { cobrado.getLineWidth(it) }
        c.texto(rico(Formato.plata(d.pendiente, "ARS"), " pendiente"), pintura(11f, color = hexA4(0xB33A3A)), m + anchoCobrado + 18f, y, ancho / 2)
    }
    y += cobrado.height + 18f
    // Rendido y administración.
    val anchoCaja = (ancho - 12f) / 2
    val interior = anchoCaja - 28f
    val rendidoDet = Formato.cantidad(r?.emitidas ?: 0, "rendición emitida", "rendiciones emitidas") + (if ((r?.pendientes ?: 0) > 0) " · ${r?.pendientes ?: 0} sin emitir" else "")
    val lineasCaja = listOf(
        listOf(
            armar("Rendido a propietarios", pintura(10f, 600, gris), interior),
            armar(Formato.plata(r?.neto?.get("ARS") ?: 0.0, "ARS"), pintura(18f, 700, tinta), interior),
            armar(rendidoDet, pintura(9f, color = gris), interior),
        ),
        listOfNotNull(
            armar("Administración${d.comisionPorcentaje?.let { " (${Formato.porcentaje(it)})" } ?: ""}", pintura(10f, 600, gris), interior),
            armar(Formato.plata(d.comision, "ARS"), pintura(18f, 700, tinta), interior),
            dCom?.let { armar("${it.first} vs $anterior", pintura(9f, 700, if (it.second) hexA4(0x2F7A4F) else hexA4(0xB33A3A)), interior) },
        ),
    )
    val altoCaja = lineasCaja.maxOf { l -> 28f + l.sumOf { it.height } + 4f * (l.size - 1) }
    lineasCaja.forEachIndexed { i, l ->
        val x = m + i * (anchoCaja + 12f)
        c.caja(x, y, anchoCaja, altoCaja, 10f, hexA4(0xF5F1E8))
        var yy = y + 14f
        l.forEach { yy += c.texto(it, x + 14f, yy) + 4f }
    }
    y += altoCaja + 18f
    // Los contratos del mes que viene.
    y += c.texto("Contratos en $proximo", pintura(12f, 700, tinta), m, y, ancho) + 8f
    val ajusta = rico(if (d.ajustan > 0) Formato.cantidad(d.ajustan, "ajusta", "ajustan") else "Ninguno ajusta",
        d.suba?.let { " · el alquiler promedio sube ${Formato.porcentaje(Math.round(it * 10) / 10.0)}" } ?: "")
    // HStack(spacing: 8), centrado: el ícono (17 de iOS, sin .font) y la frase de 11.
    val lado = 17f * 1.18f
    val lAjusta = armar(ajusta, pintura(11f, color = tinta), ancho - lado - 8f)
    val altoAjusta = max(lado, lAjusta.height.toFloat())
    val wA = c.simbolo("chart.line.uptrend.xyaxis", 17f, hexA4(0x7A5200), m, y + (altoAjusta - lado) / 2).takeIf { it > 0f } ?: lado
    c.texto(lAjusta, m + wA + 8f, y + (altoAjusta - lAjusta.height) / 2)
    y += altoAjusta + 8f
    val vence = rico(if (d.vencen > 0) Formato.cantidad(d.vencen, "vence", "vencen") else "Ninguno vence",
        " antes de ${TextosInforme.nombreMes(d.limite)}${if (d.vencen > 0) " · preguntar si renuevan" else ""}")
    val lVence = armar(vence, pintura(11f, color = tinta), ancho - lado - 8f)
    val altoVence = max(lado, lVence.height.toFloat())
    val wV = c.simbolo("calendar.badge.exclamationmark", 17f, gris, m, y + (altoVence - lado) / 2).takeIf { it > 0f } ?: lado
    c.texto(lVence, m + wV + 8f, y + (altoVence - lVence.height) / 2)
    // Abajo: el QR al informe interactivo.
    val pie = armar("Escaneá para abrir el informe interactivo en Corredor, con el detalle de cada número.", pintura(10f, color = gris), ancho - (if (d.qr != null) 68f else 0f))
    val altoPie = max(if (d.qr != null) 56f else 0f, pie.height.toFloat())
    val yPie = ALTO - m - altoPie
    var x = m
    d.qr?.let {
        c.drawBitmap(it, null, RectF(m, yPie, m + 56f, yPie + 56f), Paint().apply { isFilterBitmap = false })
        x += 56f + 12f
    }
    c.texto(pie, x, yPie + (altoPie - pie.height) / 2)
}
