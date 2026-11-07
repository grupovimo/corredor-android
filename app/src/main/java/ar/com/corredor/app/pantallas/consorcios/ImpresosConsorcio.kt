package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.nucleo.archivoParaCompartir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.ArchivoPrevio
import ar.com.corredor.app.nucleo.Documentos
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.tema.plata
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

// Clon de Pantallas/Consorcios/ImpresosConsorcio.swift.
// Los impresos de Consorcios, como los A4 del panel ((impresos)/consorcios/[id]/imprimir): la liquidación de expensas
// (una hoja por unidad, con el QR al recibo público) y el recibo de sueldo (con las dos firmas). Se arman en el teléfono
// como PDF (PdfDocument de Android) y se ven con el visor del sistema.

// `extension Documentos` en iOS: las funciones del área sobre el `Documentos` del núcleo.

/** A4 en puntos (Impresor.tam en iOS). */
internal const val ANCHO_A4 = 595
internal const val ALTO_A4 = 842

private object PapelConsorcio {

    @Serializable
    data class ArchivoInfo(val nombre: String? = null, val tipo: String? = null)
}

    /** La liquidación de expensas del período: una hoja por unidad (`unidad`: sólo esa). */
    suspend fun Documentos.expensas(ctx: Context, consorcio: String, periodo: String, unidad: String?, api: Api): File {
        val pid = periodo
        val base = "/v1/consorcios/$consorcio"
        val c: ConsorcioAPI = api.get(base)
        val p: PeriodoAPI = api.get("$base/periodos/$pid")
        val inmo = Documentos.inmobiliaria(api)
        val proveedores: List<ProveedorConsorcio> = runCatching { ProveedoresConsorcio.todos(api) }.getOrElse { if (it is CancellationException) throw it; emptyList() }
        val liqs = (p.liquidaciones ?: emptyList()).filter { unidad == null || it.unidadId == unidad }.sortedWith { a, b -> Cons.ordenUnidad.compare(a.unidad, b.unidad) }
        if (liqs.isEmpty()) {
            throw ErrorApi(404, "sin_liquidacion", "Sin liquidación",
                if (p.estado == "borrador") "Este período todavía no se liquidó." else "Esa unidad no tiene liquidación en este período.")
        }

        // Cómo se reparte el gasto del período, por rubro del proveedor.
        val rubroDe = proveedores.associate { it.id to it.rubro?.trim().noVacio() }
        val porRubro = mutableListOf<Pair<String, Double>>()
        var total = 0.0
        for (g in p.gastos ?: emptyList()) {
            val r = g.proveedorId?.let { rubroDe[it] } ?: (if (g.tipo == "extraordinaria") "Extraordinarias" else "Otros")
            val i = porRubro.indexOfFirst { it.first == r }
            if (i >= 0) porRubro[i] = r to (porRubro[i].second + g.monto) else porRubro.add(r to g.monto)
            total += g.monto
        }
        val rubros: List<Pair<String, Int>> = if (total > 0) porRubro.map { it.first to (it.second / total * 100).roundToInt() }.sortedByDescending { it.second } else emptyList()

        // Los vencimientos: los días del consorcio, en el mes siguiente al período.
        val sig = Cons.mesSiguiente(p.periodo)
        val dm: (Int) -> String = { String.format(Locale.US, "%02d/%s", it, sig.takeLast(2)) }
        var vence = ""
        c.diaVencimiento?.let { d1 ->
            vence = "Vence el ${dm(d1)}"
            c.diaSegundoVencimiento?.let { d2 ->
                vence += "; 2.º vto. ${dm(d2)}"
                c.interesMensual?.let { i -> if (i > 0) vence += " con ${Cons.coma(i)} % de recargo" }
            }
            vence += "."
        }

        val hojas = mutableListOf<HojaExpensasA4.Datos>()
        for (l in liqs) {
            val cuenta = api.opcional<CuentaUnidadAPI>("$base/unidades/${l.unidadId}/cuenta")
            val historial = api.opcional<List<LiquidacionExpensas>>("$base/unidades/${l.unidadId}/liquidaciones") ?: emptyList()
            val link: LinkConsorcio? = runCatching { api.enviar<LinkConsorcio, Map<String, String>>("POST", "$base/liquidaciones/${l.id}/link", vacioConsorcio) }
                .getOrElse { if (it is CancellationException) throw it; null }
            // El saldo anterior sólo tiene sentido en la última liquidación: lo que se debe además de esta.
            var anterior = 0.0
            if (historial.firstOrNull()?.id == l.id && cuenta != null) anterior = maxOf(0.0, Cons.redondo(cuenta.saldo - l.total)) + (cuenta.interesEstimado ?: 0.0)
            hojas.add(HojaExpensasA4.Datos(l, anterior, link?.let { qrConsorcio(it.url) }))
        }
        val mm = "${p.periodo.takeLast(2)}/${p.periodo.take(4)}"
        val nombre = "Expensas ${p.periodo.takeLast(2)}-${p.periodo.take(4)}${if (liqs.size == 1) " · ${liqs[0].unidad}" else ""}"
        val paginas: List<(Canvas) -> Unit> = hojas.map { h ->
            { cv: Canvas -> HojaExpensasA4.dibujar(cv, c.nombre, c.cuit, inmo?.nombre ?: "Corredor", mm, Cons.nombreMes(p.periodo), rubros, vence, h) }
        }
        return pdfDePaginas(ctx, paginas, nombre) ?: throw ErrorApi.desconocido
    }

    /** El recibo de sueldo A4: membrete con CUIT, CUIL, categoría, antigüedad, haberes y descuentos, el neto y las dos
     *  firmas. */
    suspend fun Documentos.sueldo(ctx: Context, consorcio: String, recibo: String, api: Api): File {
        val base = "/v1/consorcios/$consorcio"
        val c: ConsorcioAPI = api.get(base)
        val r: ReciboSueldoAPI = api.get("$base/sueldos/$recibo")
        val empleados = api.opcional<List<EmpleadoAPI>>("$base/empleados") ?: emptyList()
        val e = empleados.firstOrNull { it.id == r.empleadoId }
        // Los años de antigüedad al mes del recibo.
        var anios: Int? = null
        e?.ingreso?.let { i ->
            val ai = i.take(4).toIntOrNull() ?: 0; val mi = i.drop(5).take(2).toIntOrNull() ?: 0
            val ap = r.periodo.take(4).toIntOrNull() ?: 0; val mp = r.periodo.drop(5).take(2).toIntOrNull() ?: 0
            anios = maxOf(0, ap - ai - (if (mp < mi) 1 else 0))
        }
        val a = anios
        return pdfDePaginas(ctx, listOf<(Canvas) -> Unit>({ cv -> HojaSueldoA4.dibujar(cv, c, r, e, a) }), "Recibo de sueldo ${r.empleado} ${r.periodo}") ?: throw ErrorApi.desconocido
    }

    /** Un archivo de la API (un comprobante, un adjunto) guardado con su nombre, para la vista previa. */
    suspend fun Documentos.archivoConsorcio(ctx: Context, id: String, api: Api): ArchivoPrevio {
        val a = api.opcional<PapelConsorcio.ArchivoInfo>("/v1/archivos/$id")
        val nombre = (a?.nombre.noVacio() ?: "archivo-${id.take(8)}").replace("/", "-")
        val f = archivo("/v1/archivos/$id/contenido", nombre, api)
        val tipo = a?.tipo.noVacio() ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(nombre.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        return ArchivoPrevio(f, tipo)
    }

    /** Un QR (para el link del recibo público). */
    fun Documentos.qrConsorcio(texto: String): Bitmap? = runCatching {
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
        val escala = 8
        val b = Bitmap.createBitmap(m.width * escala, m.height * escala, Bitmap.Config.ARGB_8888)
        for (x in 0 until m.width) for (y in 0 until m.height) {
            val color = if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            for (dx in 0 until escala) for (dy in 0 until escala) b.setPixel(x * escala + dx, y * escala + dy, color)
        }
        b
    }.getOrNull()

    /** Un PDF A4 con una página por dibujo. */
    suspend fun Documentos.pdfDePaginas(ctx: Context, paginas: List<(Canvas) -> Unit>, nombre: String): File? = withContext(Dispatchers.Default) {
        runCatching {
            val doc = PdfDocument()
            paginas.forEachIndexed { i, dibujar ->
                val pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO_A4, ALTO_A4, i + 1).create())
                pagina.canvas.drawColor(android.graphics.Color.WHITE)
                dibujar(pagina.canvas)
                doc.finishPage(pagina)
            }
            val f = archivoParaCompartir(ctx, "$nombre.pdf")
            f.outputStream().use { doc.writeTo(it) }
            doc.close()
            f
        }.getOrNull()
    }

    /** «12.345,50»: el número con miles y hasta dos decimales. */
    fun Documentos.numeroPapel(n: Double): String {
        val f = NumberFormat.getNumberInstance(Formato.argentina)
        f.minimumFractionDigits = if (Math.rint(n) == n) 0 else 2
        f.maximumFractionDigits = 2
        return f.format(n)
    }

// MARK: - Dibujo del papel

/** Lo que tienen en común las hojas: un pincel de texto y un renglón que se parte si no entra. */
internal object Papel {
    val tinta = 0xFF18201D.toInt()
    val gris = 0xFF6B6457.toInt()
    val verde = 0xFF16443B.toInt()
    val raya = 0xFFE3E0D8.toInt()
    val rayaFina = 0xFFEFEDE7.toInt()
    val rojo = 0xFFA3392F.toInt()

    fun pincel(tam: Float, negrita: Boolean = false, color: Int = tinta, semi: Boolean = false): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam
        this.color = color
        typeface = if (negrita) Typeface.DEFAULT_BOLD else if (semi) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
    }

    /** Un texto en varias líneas hasta `ancho`; devuelve el alto. */
    fun parrafo(cv: Canvas, texto: CharSequence, x: Float, y: Float, ancho: Float, p: TextPaint, alinear: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
        val l = StaticLayout.Builder.obtain(texto, 0, texto.length, p, maxOf(1, ancho.toInt())).setAlignment(alinear).build()
        cv.save()
        cv.translate(x, y)
        l.draw(cv)
        cv.restore()
        return l.height.toFloat()
    }

    /** Un texto de una línea con la línea base calculada desde arriba. */
    fun linea(cv: Canvas, texto: String, x: Float, arriba: Float, p: TextPaint, derecha: Boolean = false) {
        val base = arriba - p.fontMetrics.ascent
        val ancho = p.measureText(texto)
        cv.drawText(texto, if (derecha) x - ancho else x, base, p)
    }

    fun alto(p: TextPaint): Float = p.fontMetrics.descent - p.fontMetrics.ascent

    /** El membrete de la familia de papel (MembreteConsorcio en iOS): la inicial en el cuadrado verde, el nombre, la línea de
     *  abajo y el número arriba a la derecha, con la raya verde. Devuelve dónde termina. */
    fun membrete(cv: Canvas, x: Float, y: Float, ancho: Float, nombre: String, detalle: String?, numero: String?, letra: String?): Float {
        var izq = x
        val fondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = verde }
        val pn = pincel(13f, negrita = true)
        val pd = pincel(10f, color = gris)
        val altoTexto = alto(pn) + (if (detalle != null) 1f + alto(pd) else 0f)
        val altoFila = maxOf(if (letra != null) 28f else 0f, altoTexto)
        if (letra != null) {
            val top = y + (altoFila - 28f) / 2
            cv.drawRoundRect(RectF(x, top, x + 28f, top + 28f), 6f, 6f, fondo)
            val pl = pincel(14f, negrita = true, color = android.graphics.Color.WHITE)
            val l = letra.trim().take(1).uppercase()
            val w = pl.measureText(l)
            linea(cv, l, x + (28f - w) / 2, top + (28f - alto(pl)) / 2, pl)
            izq = x + 28f + 8f
        }
        var ty = y + (altoFila - altoTexto) / 2
        linea(cv, nombre, izq, ty, pn)
        ty += alto(pn) + 1f
        if (detalle != null) linea(cv, detalle, izq, ty, pd)
        if (numero != null) {
            val pnum = pincel(10f, color = gris, semi = true)
            linea(cv, numero, x + ancho, y + (altoFila - alto(pnum)) / 2, pnum, derecha = true)
        }
        val abajo = y + altoFila + 10f
        cv.drawRect(x, abajo, x + ancho, abajo + 2f, fondo)
        return abajo + 2f
    }
}

/** Una hoja de la liquidación de expensas (una unidad). */
object HojaExpensasA4 {
    data class Datos(val liquidacion: LiquidacionExpensas, val anterior: Double, val qr: Bitmap?)

    fun dibujar(cv: Canvas, consorcio: String, cuit: String?, inmobiliaria: String, mm: String, mes: String, rubros: List<Pair<String, Int>>, vence: String, d: Datos) {
        val l = d.liquidacion
        val m = 32f
        val ancho = ANCHO_A4 - m * 2
        var y = m
        y = Papel.membrete(cv, m, y, ancho, consorcio, "Adm. $inmobiliaria${cuit?.let { " · CUIT $it" } ?: ""}", "LIQ. $mm", inmobiliaria) + 12f
        val pt = Papel.pincel(16f, negrita = true)
        Papel.linea(cv, "Expensas de $mes · ${l.unidad}", m, y, pt)
        y += Papel.alto(pt) + 12f

        // La tabla.
        val inicioTabla = y
        val p11 = Papel.pincel(11f)
        val pRojo = Papel.pincel(11f, color = Papel.rojo)
        val fina = Paint().apply { color = Papel.rayaFina }
        val raya = Paint().apply { color = Papel.raya }
        var primero = true
        fun renglon(a: String, b: String, rojo: Boolean = false) {
            val p = if (rojo) pRojo else p11
            if (!primero) cv.drawRect(m, y, m + ancho, y + 1f, fina)
            primero = false
            Papel.linea(cv, a, m + 8f, y + 5f, p)
            Papel.linea(cv, b, m + ancho - 8f, y + 5f, p, derecha = true)
            y += Papel.alto(p) + 10f
        }
        (l.desglose ?: emptyList()).forEach { x ->
            renglon("${x.columna} (${Documentos.numeroPapel(Cons.redondo(x.coeficiente * 100))} %)", Documentos.numeroPapel(x.monto))
        }
        l.fondo?.let { f -> if (f > 0) renglon("Fondo de reserva", Documentos.numeroPapel(f)) }
        if (d.anterior > 0) renglon("Saldo anterior + interés", Documentos.numeroPapel(d.anterior), rojo = true)
        cv.drawRect(m, y, m + ancho, y + 1f, raya)
        val pTotalE = Papel.pincel(12f, negrita = true)
        val pTotal = Papel.pincel(15f, negrita = true)
        val altoTotal = Papel.alto(pTotal) + 14f
        Papel.linea(cv, "Total a pagar", m + 8f, y + (altoTotal - Papel.alto(pTotalE)) / 2, pTotalE)
        Papel.linea(cv, Formato.plata(l.total + d.anterior), m + ancho - 8f, y + 7f, pTotal, derecha = true)
        y += altoTotal
        val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Papel.raya; style = Paint.Style.STROKE; strokeWidth = 1f }
        cv.drawRoundRect(RectF(m, inicioTabla, m + ancho, y), 4f, 4f, borde)
        y += 12f

        if (rubros.isNotEmpty()) {
            y += Papel.parrafo(cv, rubros.joinToString("   ") { "${it.first} ${it.second} %" }, m, y, ancho, Papel.pincel(10f, color = Papel.gris)) + 12f
        }
        if (d.qr != null || vence.isNotEmpty()) {
            var x = m
            var altoQr = 0f
            d.qr?.let { q ->
                cv.drawBitmap(q, null, RectF(m, y, m + 84f, y + 84f), Paint().apply { isFilterBitmap = false })
                x = m + 84f + 12f
                altoQr = 84f
            }
            val t = "${if (d.qr != null) "Escaneá para ver el detalle, los comprobantes y pagar. " else ""}$vence"
            val h = Papel.parrafo(cv, t, x, y, m + ancho - x, Papel.pincel(10.5f))
            y += maxOf(altoQr, h)
        }
        val pPie = Papel.pincel(9f, color = Papel.gris)
        Papel.linea(cv, "Hecha con Corredor", m, ALTO_A4 - m - Papel.alto(pPie), pPie)
    }
}

/** El recibo de sueldo A4. */
object HojaSueldoA4 {
    fun dibujar(cv: Canvas, c: ConsorcioAPI, r: ReciboSueldoAPI, e: EmpleadoAPI?, anios: Int?) {
        val haberes = listOf(
            "Sueldo básico" to (r.basico ?: 0.0),
            (anios?.let { "Antigüedad ${Cons.plural(it, "año", "años")}" } ?: "Antigüedad") to (r.antiguedad ?: 0.0),
            "Otros remunerativos" to (r.otrosRemunerativos ?: 0.0),
        ).filter { it.second > 0 }
        val m = 32f
        val ancho = ANCHO_A4 - m * 2
        var y = m
        y = Papel.membrete(cv, m, y, ancho, c.nombre + (c.cuit?.let { " · CUIT $it" } ?: ""), null, "RECIBO ${r.periodo.takeLast(2)}/${r.periodo.take(4)}", null) + 12f

        // El empleado (negrita) con el CUIL, y la categoría a la derecha.
        val p11 = Papel.pincel(11f)
        val nombre = SpannableStringBuilder(r.empleado).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(e?.documento?.let { " · CUIL $it" } ?: "")
        }
        val categoria = e?.categoria
        val anchoCat = categoria?.let { Papel.pincel(11f, color = Papel.gris).measureText(it) + 12f } ?: 0f
        val h = Papel.parrafo(cv, nombre, m, y, ancho - anchoCat, p11)
        categoria?.let { Papel.linea(cv, it, m + ancho, y, Papel.pincel(11f, color = Papel.gris), derecha = true) }
        y += h + 12f

        // La tabla.
        val inicio = y
        val fina = Paint().apply { color = Papel.rayaFina }
        fun fila(a: String, b: String, d: String, cabeza: Boolean = false) {
            val p = if (cabeza) Papel.pincel(11f, color = Papel.gris, semi = true) else p11
            if (!cabeza) cv.drawRect(m, y, m + ancho, y + 1f, fina)
            Papel.linea(cv, a, m + 8f, y + 5f, p)
            Papel.linea(cv, b, m + ancho - 8f - 110f, y + 5f, p, derecha = true)
            Papel.linea(cv, d, m + ancho - 8f, y + 5f, p, derecha = true)
            y += Papel.alto(p) + 10f
        }
        fila("Concepto", "Haberes", "Descuentos", cabeza = true)
        haberes.forEach { fila(it.first, Documentos.numeroPapel(it.second), "") }
        (r.deducciones ?: emptyList()).forEach { x -> fila("${x.nombre} ${Cons.coma(x.tasa)} %", "", Documentos.numeroPapel(x.monto)) }
        val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Papel.raya; style = Paint.Style.STROKE; strokeWidth = 1f }
        cv.drawRoundRect(RectF(m, inicio, m + ancho, y), 4f, 4f, borde)
        y += 12f

        val pN = Papel.pincel(12f, negrita = true)
        val pM = Papel.pincel(15f, negrita = true)
        Papel.linea(cv, "Neto a cobrar", m, y + (Papel.alto(pM) - Papel.alto(pN)) / 2, pN)
        Papel.linea(cv, Formato.plata(r.neto ?: 0.0), m + ancho, y, pM, derecha = true)
        y += Papel.alto(pM) + 12f + 60f

        // Las dos firmas.
        val gris = Papel.pincel(10f, color = Papel.gris)
        val trazo = Paint().apply { color = Papel.tinta }
        val anchoFirma = (ancho - 40f) / 2
        listOf("Firma del empleador", "Firma del empleado").forEachIndexed { i, f ->
            val x = m + i * (anchoFirma + 40f)
            cv.drawRect(x, y, x + anchoFirma, y + 0.7f, trazo)
            val w = gris.measureText(f)
            Papel.linea(cv, f, x + (anchoFirma - w) / 2, y + 4f, gris)
        }
        val pPie = Papel.pincel(9f, color = Papel.gris)
        Papel.linea(cv, "Hecha con Corredor", m, ALTO_A4 - m - Papel.alto(pPie), pPie)
    }
}
