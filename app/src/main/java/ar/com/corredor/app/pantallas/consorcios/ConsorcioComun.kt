package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.nucleo.Fotos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// Clon de Pantallas/Consorcios/ConsorcioComun.swift.
// Consorcios: los tipos de la API (/v1/consorcios/*, con los tags json del Go de
// api/internal/dominio/consorcios) y las cuentas chicas que comparten las pantallas, como
// web/src/lib/consorcios/comun.ts. Los montos llegan en pesos con dos decimales.

// MARK: - Destinos del área

/** Liquidar expensas (el flujo Gastos → Prorrateo → Emitir). `periodo`: «AAAA-MM»; null es el que toca. */
@Serializable
data class ALiquidarConsorcio(val consorcio: String, val periodo: String? = null)

/** La ficha de un trabajo o reclamo. */
@Serializable
data class ATrabajoConsorcio(val consorcio: String, val id: String)

/** La ficha de un consorcio abierta en una pestaña (las alertas llevan a Unidades que deben, Trabajos…).
 *  `pedido`: lo que se abre adentro («deben», «nueva», «unidad:<id>», «liquidar», «recibo:<id>»). */
@Serializable
data class AFichaConsorcioEn(val id: String, val pestana: String, val pedido: String? = null)

// Los destinos de Consorcios (`destinosConsorcios()` en iOS) los registra app/Registro.kt:
//   ALiquidarConsorcio → LiquidarConsorcio(consorcio = l.consorcio, pedido = l.periodo)
//   ATrabajoConsorcio → FichaTrabajoConsorcio(consorcio = t.consorcio, id = t.id)
//   AFichaConsorcioEn → FichaConsorcio(id = f.id, pestanaInicial = f.pestana, pedidoInicial = f.pedido)

// MARK: - Modelos

@Serializable
data class ConsorcioAPI(
    val id: String,
    val nombre: String,
    val direccion: String? = null,
    val cuit: String? = null,
    val cbu: String? = null,
    val diaVencimiento: Int? = null,
    val diaSegundoVencimiento: Int? = null,
    val interesMensual: Double? = null,
    // Lo que la app no edita pero el PUT pisa: viaja tal como vino (como datos.ts de la web).
    val modoExpensas: String? = null,
    val fondoTipo: String? = null,
    val fondoValor: Double? = null,
    val notas: String? = null,
    val agenteId: String? = null,
    val fotoId: String? = null,
    val fotoUrl: String? = null,
    val reglamentoEn: String? = null,
    val metricas: Metricas? = null,
    /** Sólo en la ficha (GET /v1/consorcios/{id}). */
    val columnas: List<ColumnaAPI>? = null,
) {
    @Serializable
    data class Metricas(
        val unidades: Int? = null,
        val deudores: Int? = null,
        val saldoAdeudado: Double? = null,
        val novedadesAbiertas: Int? = null,
        val trabajosPendientes: Int? = null,
        val trabajosVencidos: Int? = null,
        val servicesPorVencer: Int? = null,
    )

    /** «Consorcio Cabildo 2620» → «Cabildo 2620». */
    val nombreCorto: String get() = nombre.replace(Regex("^consorcio\\s+", RegexOption.IGNORE_CASE), "")

    /** El barrio: lo que va después de la primera coma de la dirección. */
    val barrio: String?
        get() {
            val p = (direccion ?: "").split(",", limit = 2)
            return if (p.size > 1) p[1].trim().noVacio() else null
        }

    /** La columna General (la de por defecto) o la primera. */
    val general: ColumnaAPI? get() = columnas?.firstOrNull { it.porDefecto == true } ?: columnas?.firstOrNull()
}

@Serializable
data class ColumnaAPI(val id: String, val nombre: String, val metodo: String? = null, val porDefecto: Boolean? = null, val orden: Int? = null)

/** GET /v1/consorcios/alertas. */
@Serializable
data class AlertaConsorcio(
    val consorcioId: String,
    val consorcio: String,
    val tipo: String,
    val texto: String,
    val monto: Double? = null,
    val fecha: String? = null,
    val dias: Int? = null,
)

@Serializable
data class UnidadAPI(
    val id: String,
    val etiqueta: String,
    val piso: String? = null,
    val tipo: String? = null,
    val propietarioId: String? = null,
    val inquilinoId: String? = null,
    val notas: String? = null,
    val expensaFija: Double? = null,
    val coeficientes: Map<String, Double>? = null,
    val propietario: String? = null,
    val inquilino: String? = null,
    val propietarioEmail: String? = null,
    val propietarioTelefono: String? = null,
    val saldo: Double? = null,
) {
    val nombre: String get() = etiqueta

    companion object {
        val tipos = listOf("departamento" to "Departamento", "cochera" to "Cochera", "baulera" to "Baulera", "local" to "Local", "otro" to "Otro")
    }
}

@Serializable
data class DeudorAPI(
    val unidadId: String,
    val unidad: String,
    val propietario: String? = null,
    val saldo: Double,
    val impagoDesde: String? = null,
    val dias: Int? = null,
    val interesEstimado: Double? = null,
) {
    val id: String get() = unidadId
    val total: Double get() = saldo + (interesEstimado ?: 0.0)
}

@Serializable
data class MovimientoUnidad(
    val id: String,
    val fecha: String,
    val tipo: String,
    val monto: Double,
    val notas: String? = null,
    val periodoId: String? = null,
) {
    val signo: Double get() = if (tipo == "pago") -monto else monto

    companion object {
        val nombres = mapOf("pago" to "Pago", "cargo" to "Expensas", "interes" to "Interés", "ajuste" to "Ajuste")
    }
}

/** GET /v1/consorcios/{id}/unidades/{u}/cuenta. */
@Serializable
data class CuentaUnidadAPI(
    val unidad: String? = null,
    val saldo: Double,
    val impagoDesde: String? = null,
    val interesEstimado: Double? = null,
    val movimientos: List<MovimientoUnidad>? = null,
)

@Serializable
data class PeriodoAPI(
    val id: String,
    val periodo: String,
    val estado: String,
    val gastosTotal: Double? = null,
    val fondoTotal: Double? = null,
    val total: Double? = null,
    val notas: String? = null,
    val cargado: Double? = null,
    /** Sólo en GET …/periodos/{p}. */
    val gastos: List<GastoExpensas>? = null,
    val liquidaciones: List<LiquidacionExpensas>? = null,
)

@Serializable
data class GastoExpensas(
    val id: String,
    val descripcion: String,
    val tipo: String? = null,
    val monto: Double,
    val columnaId: String? = null,
    val proveedorId: String? = null,
    val documentoId: String? = null,
    val fecha: String? = null,
    val columna: String? = null,
    val proveedor: String? = null,
    val proveedorRubro: String? = null,
)

@Serializable
data class LineaExpensas(val columnaId: String? = null, val columna: String, val base: Double? = null, val coeficiente: Double, val monto: Double)

@Serializable
data class LiquidacionExpensas(
    val id: String,
    val periodoId: String? = null,
    val periodo: String,
    val unidadId: String,
    val unidad: String,
    val propietario: String? = null,
    val email: String? = null,
    val propietarioId: String? = null,
    val telefono: String? = null,
    val desglose: List<LineaExpensas>? = null,
    val subtotal: Double? = null,
    val fondo: Double? = null,
    val total: Double,
)

/** GET …/periodos/{p}/prorrateo. */
@Serializable
data class ProrrateoExpensas(
    val unidades: List<Unidad>,
    val gastosTotal: Double? = null,
    val fondoTotal: Double? = null,
    val total: Double? = null,
) {
    @Serializable
    data class Unidad(val unidadId: String, val subtotal: Double? = null, val fondo: Double? = null, val total: Double)
}

@Serializable
data class AdjuntoTrabajo(val archivoId: String, val nombre: String, val tipo: String, val bytes: Int? = null, val url: String? = null) {
    val id: String get() = archivoId
}

@Serializable
data class TrabajoAPI(
    val id: String,
    val unidadId: String? = null,
    val estado: String,
    val prioridad: String? = null,
    val titulo: String,
    val descripcion: String? = null,
    val presupuesto: Double? = null,
    val costoReal: Double? = null,
    val responsable: String? = null,
    val proveedorId: String? = null,
    val proveedor: String? = null,
    val proveedorRubro: String? = null,
    val proveedorTelefono: String? = null,
    val unidad: String? = null,
    val venceEl: String? = null,
    val hechoEl: String? = null,
    val adjuntos: List<AdjuntoTrabajo>? = null,
    val creadoEn: String? = null,
)

@Serializable
data class NovedadAPI(
    val id: String,
    val unidadId: String? = null,
    val bienId: String? = null,
    val tipo: String? = null,
    val estado: String? = null,
    val titulo: String,
    val descripcion: String? = null,
    val costo: Double? = null,
    val responsable: String? = null,
    val ocurrioEl: String? = null,
    val resueltoEl: String? = null,
) {
    companion object {
        val tipos = listOf("rotura" to "Rotura", "arreglo" to "Arreglo", "reclamo" to "Reclamo", "aviso" to "Aviso", "mantenimiento" to "Mantenimiento", "gasto" to "Gasto", "otro" to "Nota")
    }
}

@Serializable
data class ProveedorConsorcio(
    val id: String,
    val nombre: String,
    val cuit: String? = null,
    val rubro: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val notas: String? = null,
) {
    val conRubro: String get() = rubro.noVacio()?.let { "$nombre · $it" } ?: nombre
}

@Serializable
data class EmpleadoAPI(
    val id: String,
    val nombre: String,
    val documento: String? = null,
    val categoria: String? = null,
    val ingreso: String? = null,
    val basico: Double? = null,
    val horario: String? = null,
    val notas: String? = null,
)

@Serializable
data class DeduccionAPI(val nombre: String, val tasa: Double, val monto: Double)

@Serializable
data class ReciboSueldoAPI(
    val id: String,
    val empleadoId: String,
    val empleado: String,
    val periodo: String,
    val notas: String? = null,
    val basico: Double? = null,
    val antiguedad: Double? = null,
    val otrosRemunerativos: Double? = null,
    val bruto: Double? = null,
    val deducciones: List<DeduccionAPI>? = null,
    val deduccionesTotal: Double? = null,
    val neto: Double? = null,
    val sac: Double? = null,
)

@Serializable
data class EscalaAPI(val id: String, val categoria: String, val basico: Double, val vigenteDesde: String? = null, val notas: String? = null)

/** Un link público (cuenta, recibo de expensas, recibo de sueldo). */
@Serializable
data class LinkConsorcio(val id: String? = null, val url: String)

/** POST …/periodos/{p}/enviar. */
@Serializable
data class EnvioMailExpensas(val encolados: Int, val sinEmail: List<String>? = null)

/** Un cuerpo vacío (`[String: String]()` en iOS: `{}`). */
internal val vacioConsorcio: Map<String, String> = emptyMap()

/** `.nonEmpty` de iOS: el texto, o null si está vacío o en blanco. */
internal fun String?.noVacio(): String? = this?.takeIf { it.isNotBlank() }

// MARK: - Cuentas chicas (comun.ts)

object Cons {
    private val argentina = Formato.argentina
    private val zona = Formato.zona

    /** Hoy, «AAAA-MM-DD». */
    val hoy: String get() = LocalDate.now(zona).toString()

    fun fechaDeMes(p: String): LocalDate? = runCatching { YearMonth.parse(p.take(7)).atDay(1) }.getOrNull()

    /** «2026-11» → «noviembre». */
    fun nombreMes(p: String): String = fechaDeMes(p)?.month?.getDisplayName(TextStyle.FULL, argentina)?.lowercase(argentina) ?: p

    /** «2026-11» → «Noviembre». */
    fun NombreMes(p: String): String = Formato.mayuscula(nombreMes(p))

    /** «Noviembre» o «Noviembre 2025» si no es de este año. */
    fun mesConAnio(p: String): String = NombreMes(p) + (if (p.take(4) == hoy.take(4)) "" else " ${p.take(4)}")

    /** «2026-11» → «nov.». */
    fun mesCorto(p: String): String = nombreMes(p).take(3) + "."

    /** El mes siguiente: «2026-11» → «2026-12». */
    fun mesSiguiente(p: String, n: Int = 1): String {
        val x = p.take(7).split("-").mapNotNull { it.toIntOrNull() }
        if (x.size != 2) return p
        val t = x[0] * 12 + (x[1] - 1) + n
        return String.format(Locale.US, "%04d-%02d", Math.floorDiv(t, 12), Math.floorMod(t, 12) + 1)
    }

    /** El vencimiento: el día `dia` del mes siguiente al período. */
    fun vencimiento(p: String, dia: Int?): String? {
        if (dia == null || dia <= 0) return null
        return "${mesSiguiente(p)}-${String.format(Locale.US, "%02d", dia)}"
    }

    /** «2026-12-10» → «10/12». */
    fun diaBarraMes(f: String): String {
        if (f.length < 10) return f
        val a = f.split("-")
        return if (a.size >= 3) "${a[2].take(2)}/${a[1]}" else f
    }

    /** «2026-11-10» → «10/11/2026». */
    fun fechaPapel(f: String): String {
        val a = f.take(10).split("-")
        return if (a.size == 3) "${a[2]}/${a[1]}/${a[0]}" else f
    }

    /** El período que toca liquidar: el borrador más nuevo; si no, el mes siguiente al último; si no hay ninguno,
     *  el mes de hoy. */
    fun aLiquidar(periodos: List<PeriodoAPI>): Pair<String, PeriodoAPI?> {
        val ord = periodos.sortedByDescending { it.periodo }
        ord.firstOrNull { it.estado == "borrador" }?.let { return it.periodo to it }
        ord.firstOrNull()?.let { return mesSiguiente(it.periodo) to null }
        return hoy.take(7) to null
    }

    /** En la lista: el mes que toca si hay borrador, o si el mes que sigue ya empezó. */
    fun liquidarEnLista(periodos: List<PeriodoAPI>): Pair<String, Boolean>? {
        val a = aLiquidar(periodos)
        if (a.second != null) return a.first to true
        if (periodos.isNotEmpty() && a.first <= hoy.take(7)) return a.first to false
        return null
    }

    /** El período liquidado justo antes de `periodo`. */
    fun anterior(periodos: List<PeriodoAPI>, periodo: String): PeriodoAPI? =
        periodos.filter { it.estado != "borrador" && it.periodo < periodo }.sortedByDescending { it.periodo }.firstOrNull()

    /** 4.2 → «4,20 %». */
    fun coeficiente(v: Double): String = String.format(Locale.US, "%.2f", v).replace(".", ",") + " %"

    /** Un número con coma: 3.5 → «3,5». */
    fun coma(v: Double): String {
        val s = if (Math.rint(v) == v) v.toLong().toString() else formatoG(v)
        return s.replace(".", ",")
    }

    /** `String(format: "%g")` de iOS: hasta 6 cifras significativas, sin ceros de más. */
    private fun formatoG(v: Double): String = java.math.BigDecimal(v).round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()

    fun sumaCoeficientes(unidades: List<UnidadAPI>, columna: String): Double =
        Math.rint(unidades.sumOf { it.coeficientes?.get(columna) ?: 0.0 } * 100) / 100

    /** El primero del personal cuya categoría dice encargado (o el primero). */
    fun encargado(e: List<EmpleadoAPI>): EmpleadoAPI? =
        e.firstOrNull { (it.categoria ?: "").contains("encargad", ignoreCase = true) } ?: e.firstOrNull()

    /** Los años completos desde el ingreso. */
    fun antiguedad(ingreso: String?, al: String = hoy): Int? {
        if (ingreso == null || ingreso.length < 10 || al.length < 10) return null
        var n = (al.take(4).toIntOrNull() ?: 0) - (ingreso.take(4).toIntOrNull() ?: 0)
        if (al.drop(5) < ingreso.drop(5)) n -= 1
        return maxOf(0, n)
    }

    /** Los pasos del reclamo y el estado de la API de cada uno. */
    val pasosTrabajo: List<Pair<String, String>> = listOf("pendiente" to "Abierto", "aprobado" to "Presupuesto", "en_curso" to "En obra", "hecho" to "Cerrado")

    /** El chip de un trabajo: Esperando · Agendado · En obra · Cerrado. */
    fun chipTrabajo(t: TrabajoAPI): Pair<String, Tono> = when (t.estado) {
        "pendiente" -> "Esperando" to Tono.oro
        "aprobado" -> (if (t.venceEl != null) "Agendado" else "Aprobado") to Tono.azul
        "en_curso" -> "En obra" to Tono.azul
        "hecho" -> "Cerrado" to Tono.verde
        else -> "Cancelado" to Tono.neutro
    }

    private fun hay(s: String, patron: String): Boolean = Regex(patron, RegexOption.IGNORE_CASE).containsMatchIn(s)

    fun iconoTrabajo(t: TrabajoAPI): String {
        val s = "${t.titulo} ${t.proveedorRubro ?: ""}".lowercase()
        if (hay(s, "filtr|humed|gote|agua|plom|ca[ñn]o|p[ée]rdida")) return "drop.triangle"
        if (hay(s, "luz|el[ée]ctr|l[aá]mpara|lumin")) return "lightbulb"
        if (hay(s, "ascensor")) return "arrow.up.arrow.down.square"
        if (hay(s, "port[oó]n|puerta|cerradura|herrer")) return "door.garage.closed"
        if (hay(s, "pint")) return "paintbrush"
        if (hay(s, "gas")) return "flame"
        return "wrench.and.screwdriver"
    }

    fun iconoRubro(rubro: String?): String {
        val s = (rubro ?: "").lowercase()
        if (hay(s, "plom|agua")) return "drop"
        if (hay(s, "el[ée]ctr")) return "bolt"
        if (hay(s, "ascensor|manten")) return "arrow.up.arrow.down.square"
        if (hay(s, "herrer|repar")) return "hammer"
        if (hay(s, "limp")) return "sparkles"
        if (hay(s, "segur")) return "shield"
        return "wrench.and.screwdriver"
    }

    fun iconoGasto(g: GastoExpensas): String {
        val s = "${g.descripcion} ${g.proveedorRubro ?: ""}".lowercase()
        if (hay(s, "f931|cargas|afip")) return "building.columns"
        if (hay(s, "sueldo|encargad|personal")) return "person.text.rectangle"
        if (hay(s, "edenor|edesur|luz|electric")) return "bolt"
        if (hay(s, "aysa|agua")) return "drop"
        if (hay(s, "ascensor")) return "arrow.up.arrow.down.square"
        if (hay(s, "limpi")) return "sparkles"
        if (hay(s, "port[oó]n|herrer")) return "door.garage.closed"
        if (hay(s, "segur")) return "shield"
        if (hay(s, "honorario|administr")) return "building.2"
        if (hay(s, "plom|ca[ñn]er")) return "drop"
        return "doc.text"
    }

    /** El rubro de la fila del gasto: el del proveedor o, si no, la columna o el tipo. */
    fun rubroGasto(g: GastoExpensas): String =
        g.proveedorRubro ?: (if (g.tipo == "extraordinaria") "Extraordinaria" else (g.columna ?: "Ordinaria"))

    /** «+10,4 %» de un total contra el anterior. */
    fun variacion(ahora: Double, antes: Double): String {
        if (antes == 0.0) return ""
        val v = (ahora - antes) / antes * 100
        val s = if (v > 0) "+" else if (v < 0) "−" else ""
        return s + String.format(Locale.US, "%.1f", abs(v)).replace(".", ",") + " %"
    }

    /** Montos sin moneda, como en las tablas: «818.000». */
    fun cifra(n: Double): String = Formato.numero(redondeo(n).toInt())

    /** `.rounded()` de iOS (la mitad se aleja del cero). */
    private fun redondeo(n: Double): Long = if (n < 0) -Math.round(-n) else Math.round(n)

    /** Los días enteros desde una fecha ISO hasta hoy. */
    fun diasDesde(f: String?): Int {
        if (f == null) return 0
        val a = runCatching { LocalDate.parse(f.take(10)) }.getOrNull() ?: return 0
        val b = LocalDate.parse(hoy)
        return maxOf(0, ChronoUnit.DAYS.between(a, b).toInt())
    }

    /** «hoy», «ayer», «hace 6 días». */
    fun haceDias(n: Int): String = if (n == 0) "hoy" else if (n == 1) "ayer" else "hace $n días"

    /** Los chips de un edificio en la lista: morosos, liquidar el mes, reclamos; si nada, «Al día». */
    fun chipsEdificio(c: ConsorcioAPI, liquidar: String?): List<Pair<String, Tono>> {
        val m = c.metricas
        val out = mutableListOf<Pair<String, Tono>>()
        m?.deudores?.let { d -> if (d > 0) out.add((if (d == 1) "1 moroso" else "$d morosos") to Tono.rojo) }
        if (liquidar != null) out.add("Liquidar ${mesCorto(liquidar)}" to Tono.oro)
        m?.trabajosPendientes?.let { t -> if (t > 0) out.add((if (t == 1) "1 reclamo" else "$t reclamos") to Tono.azul) }
        if (out.isEmpty()) out.add("Al día" to Tono.verde)
        return out
    }

    private fun claveUnidad(e: String): String =
        e.replace(Regex("^\\s*(PB|planta baja)", RegexOption.IGNORE_CASE), "0")
            .replace(Regex("^\\s*(SS|subsuelo)", RegexOption.IGNORE_CASE), "-1")

    private val cotejo: Collator = Collator.getInstance(argentina).apply { strength = Collator.PRIMARY }

    /** `compare(options: [.numeric, .caseInsensitive, .diacriticInsensitive])`: los números por su valor. */
    private fun comparar(a: String, b: String): Int {
        val partes = Regex("\\d+|\\D+")
        val x = partes.findAll(a).map { it.value }.toList()
        val y = partes.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val r = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 } ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else cotejo.compare(p, q)
            if (r != 0) return r
        }
        return x.size.compareTo(y.size)
    }

    /** Las unidades como se leen en el edificio: PB, 1.º A, 1.º B… 10.º A. */
    fun porUnidad(a: String, b: String): Boolean = comparar(claveUnidad(a), claveUnidad(b)) < 0

    /** El orden de `porUnidad` para `sortedWith`. */
    val ordenUnidad: Comparator<String> = Comparator { a, b -> comparar(claveUnidad(a), claveUnidad(b)) }

    /** Un monto con dos decimales a lo sumo (la API rechaza más). */
    fun redondo(n: Double): Double = redondeo(n * 100) / 100.0

    fun plural(n: Int, uno: String, varios: String): String = Formato.cantidad(n, uno, varios)

    /** «3.º B y 4.º A», «1.º A, 2.º B y 3.º C». */
    fun lista(l: List<String>): String {
        if (l.size <= 1) return l.firstOrNull() ?: ""
        return l.dropLast(1).joinToString(", ") + " y " + l.last()
    }

    /** UIPasteboard.general.string en iOS. */
    fun copiar(ctx: Context, s: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Corredor", s))
    }
}

// MARK: - Afuera (WhatsApp, mail, compartir)

// MARK: - Archivos de la API

/** Los bytes de un archivo de la API (una foto, un PDF, el CSV del F931): `api.pedir("GET", …)`. */
internal suspend fun bajarConsorcio(api: Api, ruta: String): ByteArray = api.pedir("GET", ruta)

/** `api.todas` de iOS: una lista entera, de a 200 con `desde`. */
internal suspend inline fun <reified T> todasConsorcio(api: Api, ruta: String, query: List<Pair<String, String>> = emptyList(), crossinline id: (T) -> String): List<T> =
    api.todas(ruta, query) { id(it) }


// MARK: - Piezas comunes del área

/** Un chip de estado (cápsula en el tono). */
@Composable
fun ChipConsorcio(texto: String, tono: Tono) {
    Box(Modifier.height(24.dp).clip(CircleShape).background(tono.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Texto(texto, Estilo(12f, Peso.bold, relativo = Relativo.caption), tono.tinta, lineas = 1)
    }
}

/** Un bloque con título (y una acción a la derecha) sobre una tarjeta de renglones. */
@Composable
fun BloqueConsorcio(
    titulo: String?,
    tono: Tono? = null,
    accion: Pair<String, () -> Unit>? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (titulo != null || accion != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (titulo != null) Texto(titulo, Estilo.subheadFuerte, tono?.tinta ?: C.texto, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (accion != null) {
                    Texto(accion.first, Estilo.subheadFuerte, C.accion, Modifier.clickable(onClick = accion.second).padding(vertical = 4.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().tarjeta(), content = contenido)
    }
}

/** Un renglón de una tarjeta (con la raya de arriba si no es el primero). */
@Composable
fun RenglonConsorcio(primero: Boolean = false, modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    val linea = C.linea
    Row(
        modifier.fillMaxWidth()
            .drawBehind { if (!primero) drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = contenido,
    )
}

/** Dos líneas: título fuerte y detalle. */
@Composable
fun RowScope.DosLineasConsorcio(titulo: String, detalle: String?, tonoDetalle: Color = C.texto2) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Texto(titulo, Estilo.filaLista, C.texto, lineas = 2)
        detalle.noVacio()?.let { Texto(it, Estilo.footnote, tonoDetalle, lineas = 3) }
    }
}

/** El texto gris de una tarjeta vacía. */
@Composable
fun VacioConsorcio(texto: String) {
    Texto(texto, Estilo.texto14, C.texto3, Modifier.fillMaxWidth().padding(14.dp))
}

/** Un archivo de la API (con la sesión): la foto de un trabajo, un comprobante. */
@Composable
fun ImagenConsorcio(id: String, modifier: Modifier = Modifier) {
    val estado = LocalEstado.current
    var imagen by remember(id) { mutableStateOf<Bitmap?>(null) }
    var fallo by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        try {
            val d = bajarConsorcio(estado.api, "/v1/archivos/$id/contenido")
            imagen = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(d, 0, d.size) }
            fallo = imagen == null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { fallo = true }
    }
    Box(modifier.clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        val img = imagen
        if (img != null) {
            Image(img.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.matchParentSize().background(C.hundida))
            if (fallo) Icono("photo", 17f, C.texto3) else Rueda()
        }
    }
}

/** Un campo con etiqueta para las hojas (Form): etiqueta arriba en gris y el control. */
@Composable
fun CampoConsorcio(etiqueta: String, ayuda: String? = null, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Texto(etiqueta, Estilo.footnote, C.texto2)
        contenido()
        if (ayuda != null) Texto(ayuda, Estilo.footnote, C.texto3)
    }
}

/** El TextField de una fila de Form (sin borde, como en iOS). */
@Composable
internal fun CampoTextoConsorcio(
    texto: String,
    alCambiar: (String) -> Unit,
    placeholder: String = "",
    teclado: KeyboardType = KeyboardType.Text,
    lineas: Int = 1,
    lineasMaximas: Int = lineas,
    sinCorrector: Boolean = false,
) {
    Entrada(texto, alCambiar, placeholder, Modifier.fillMaxWidth().padding(vertical = 4.dp), estilo = Estilo.body, teclado = teclado,
        lineas = lineas, lineasMaximas = lineasMaximas, sinCorrector = sinCorrector)
}

/** `TextField(value:format: .number)` de iOS: el número en es-AR (coma decimal); vacío es null. */
@Composable
internal fun CampoNumeroConsorcio(valor: Double?, alCambiar: (Double?) -> Unit, placeholder: String = "", decimal: Boolean = true) {
    var texto by remember { mutableStateOf(valor?.let { Cons.coma(it) } ?: "") }
    LaunchedEffect(valor) {
        val actual = leerNumeroConsorcio(texto)
        if (actual != valor) texto = valor?.let { Cons.coma(it) } ?: ""
    }
    Entrada(texto, { t -> texto = t; alCambiar(leerNumeroConsorcio(t)) }, placeholder, Modifier.fillMaxWidth().padding(vertical = 4.dp),
        estilo = Estilo.body, teclado = if (decimal) KeyboardType.Decimal else KeyboardType.Number, numeros = true)
}

/** «1.234,5» → 1234.5 (lo que entiende el formato de número de iOS en es-AR). */
internal fun leerNumeroConsorcio(t: String): Double? {
    val s = t.trim().replace(" ", "").replace("$", "")
    if (s.isEmpty()) return null
    val limpio = if (s.contains(",")) s.replace(".", "").replace(",", ".") else if (s.count { it == '.' } > 1) s.replace(".", "") else s
    return limpio.toDoubleOrNull()
}

/** Un Picker de una fila de Form: el título a la izquierda y la opción elegida con su menú. */
@Composable
internal fun <T> RenglonSelectorConsorcio(titulo: String, opciones: List<Pair<T, String>>, elegido: T, alElegir: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Texto(titulo, Estilo.body, C.texto, Modifier.weight(1f).padding(end = 8.dp))
        SelectorMenu(opciones, elegido, alElegir, titulo)
    }
}

/** Un DatePicker compacto de una fila de Form, con tope arriba (`in: ...Date()`) si hace falta. */
@Composable
internal fun RenglonFechaConsorcio(titulo: String, fecha: LocalDate, alCambiar: (LocalDate) -> Unit, maximo: LocalDate? = null) {
    var abierto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(titulo, Estilo.body, C.texto, Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.segmentado).clickable { abierto = !abierto }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Texto(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Formato.argentina).format(fecha), Estilo.body16, if (abierto) C.accion else C.texto)
            }
        }
        if (abierto) Box(Modifier.padding(bottom = 8.dp)) { CalendarioMes(fecha, { alCambiar(it); abierto = false }, maximo = maximo) }
    }
}

/** Un `Section` de un Form de iOS: el título chico arriba y la tarjeta con las filas. */
@Composable
internal fun SeccionConsorcio(titulo: String? = null, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (titulo != null) Texto(titulo.uppercase(), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
        Column(Modifier.fillMaxWidth().tarjeta().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
    }
}

/** Un botón con ícono y texto en Acción (los `Button { Label(…) }` .borderless de iOS). */
@Composable
internal fun BotonLabelConsorcio(texto: String, simbolo: String?, color: Color = C.accion, habilitado: Boolean = true, estilo: Estilo = Estilo.footnoteFuerte, alTocar: () -> Unit) {
    Row(
        Modifier.heightIn(min = 34.dp).clickable(enabled = habilitado, onClick = alTocar).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (simbolo != null) Icono(simbolo, estilo.tamano, if (habilitado) color else C.texto3)
        Texto(texto, estilo, if (habilitado) color else C.texto3, lineas = 1)
    }
}

/** `.buttonStyle(.bordered)` de iOS: cápsula hundida con el texto en Acción. */
@Composable
internal fun BotonBordeadoConsorcio(texto: String, simbolo: String? = null, habilitado: Boolean = true, lleno: Color? = null, alTocar: () -> Unit) {
    val fondo = lleno ?: C.accion.copy(alpha = 0.14f)
    val tinta = if (lleno != null) Color.White else C.accion
    Row(
        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(50)).background(fondo).clickable(enabled = habilitado, onClick = alTocar)
            .padding(horizontal = 12.dp).let { if (habilitado) it else it.then(Modifier) },
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (simbolo != null) Icono(simbolo, 13f, if (habilitado) tinta else C.texto3)
        Texto(texto, Estilo.footnoteFuerte, if (habilitado) tinta else C.texto3, lineas = 1)
    }
}

/** Un archivo elegido para subir: datos, nombre y tipo MIME. */
data class ArchivoElegidoConsorcio(val datos: ByteArray, val nombre: String, val tipo: String)

/** Elegir un archivo para subir: cámara, fotos o Archivos (el «soltá o tocá para elegir» de la web).
 *  `.elegirArchivoConsorcio($abierto, tipos:, multiple:)` en iOS. `tipos`: los MIME de Archivos. */
@Composable
fun ElegirArchivoConsorcio(
    abierto: Boolean,
    alCerrar: () -> Unit,
    tiposArchivo: List<String> = listOf("application/pdf", "image/*"),
    multiple: Boolean = false,
    alElegir: (List<ArchivoElegidoConsorcio>) -> Unit,
) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val elegir by rememberUpdatedState(alElegir)

    fun leer(uris: List<Uri>, fotos: Boolean) {
        if (uris.isEmpty()) return
        alcance.launch {
            val out = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, u ->
                    runCatching {
                        if (fotos) {
                            // `jpegData(compressionQuality: 0.85)` de iOS: la foto entera, derecha, en JPEG.
                            val jpg = Fotos.achicar(ctx, u, lado = LADO_FOTO_CONSORCIO, calidad = 85)
                                ?: ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@runCatching null
                            ArchivoElegidoConsorcio(jpg, "foto-${i + 1}.jpg", "image/jpeg")
                        } else {
                            val d = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@runCatching null
                            var nombre = "archivo"
                            ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                                if (c.moveToFirst()) nombre = c.getString(0) ?: nombre
                            }
                            ArchivoElegidoConsorcio(d, nombre, ctx.contentResolver.getType(u) ?: "application/octet-stream")
                        }
                    }.getOrNull()
                }
            }
            if (out.isNotEmpty()) elegir(out)
        }
    }

    // La cámara del sistema con la foto en tamaño real (el `Camara` de iOS devuelve la imagen original).
    var fotoCamara by remember { mutableStateOf<Uri?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = fotoCamara
        if (ok && u != null) alcance.launch {
            val jpg = withContext(Dispatchers.IO) { Fotos.achicar(ctx, u, lado = LADO_FOTO_CONSORCIO, calidad = 85) }
            if (jpg != null) elegir(listOf(ArchivoElegidoConsorcio(jpg, "foto-${System.currentTimeMillis() / 1000}.jpg", "image/jpeg")))
        }
    }
    val unaFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) leer(listOf(u), true) }
    val variasFotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { l -> leer(l, true) }
    val unArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) leer(listOf(u), false) }
    val variosArchivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { l -> leer(l, false) }

    Confirmacion(
        visible = abierto, titulo = "Sumar", verTitulo = false, volver = "Cancelar", alCerrar = alCerrar,
        opciones = listOf(
            OpcionConfirmacion("Sacar una foto") {
                runCatching { val (_, u) = Fotos.archivoCamara(ctx); fotoCamara = u; camara.launch(u) }
            },
            OpcionConfirmacion("Elegir de Fotos") {
                val pedido = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (multiple) variasFotos.launch(pedido) else unaFoto.launch(pedido)
            },
            OpcionConfirmacion("Elegir de Archivos") {
                val tipos = tiposArchivo.toTypedArray()
                if (multiple) variosArchivos.launch(tipos) else unArchivo.launch(tipos)
            },
        ),
    )
}

/** Los proveedores de la inmobiliaria (todos, de a 200). */
object ProveedoresConsorcio {
    suspend fun todos(api: Api): List<ProveedorConsorcio> = todasConsorcio<ProveedorConsorcio>(api, "/v1/proveedores") { it.id }

    @Serializable
    data class Nuevo(val nombre: String, val rubro: String?, val cuit: String? = null, val telefono: String? = null, val email: String? = null, val notas: String? = null)

    /** Da de alta un proveedor (queda para todos los edificios). */
    suspend fun crear(api: Api, n: Nuevo): ProveedorConsorcio = api.enviar("POST", "/v1/proveedores", n)
}

/** Para mostrar un monto entero sin decimales en los recibos de la tabla (`Int(n.rounded())`). */
internal fun redondoEntero(n: Double): Long = if (n < 0) -(-n).roundToLong() else n.roundToLong()

/** El lado mayor de las fotos que se suben (iOS no las achica: sólo un tope para no quedarse sin memoria). */
private const val LADO_FOTO_CONSORCIO = 4096

/** El ancho del ícono de las cifras en las tarjetas. */
internal val anchoUnidad: Dp = 44.dp
