package ar.com.corredor.app.nucleo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Montos, porcentajes y fechas como los escribe el handoff (clon de Nucleo/Formato.swift): `$ 184.350` (espacio fino
 *  que no corta el renglón, punto de miles, coma decimal), `12,4 %`, «hoy, 14:32», «jueves 30 de octubre». Las fechas
 *  son `Instant` (el Date de iOS) y se muestran en la hora de Buenos Aires. */
object Formato {
    /** Espacio fino sin corte (U+202F): entre `$` y el número, y antes de `%`. */
    const val fino = " "

    val argentina: Locale = Locale.forLanguageTag("es-AR")
    val zona: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")

    private val simbolos get() = DecimalFormatSymbols(argentina).apply { groupingSeparator = '.'; decimalSeparator = ',' }
    private val miles get() = DecimalFormat("#,##0", simbolos)
    private val decimal get() = DecimalFormat("#,##0.#", simbolos)

    /** Un número entero con punto de miles: 1.234.567. */
    fun numero(n: Int): String = miles.format(n)
    fun numero(n: Long): String = miles.format(n)

    /** Pesos enteros (la API manda pesos sin centavos): `$ 24.020`, `−$ 2.400`. */
    fun pesos(n: Int): String = if (n < 0) "−$$fino${numero(-n)}" else "$$fino${numero(n)}"
    fun pesos(n: Double): String = pesos(Math.round(n).toInt())

    /** Un porcentaje con un decimal si hace falta: `12,4 %`, `73 %`. */
    fun porcentaje(p: Double): String = "${decimal.format(p)}$fino%"

    private fun formateador(patron: String) = DateTimeFormatter.ofPattern(patron, argentina).withZone(zona)
    private val hora = formateador("H:mm")
    private val diaLargo = formateador("EEEE d 'de' MMMM")
    private val diaCorto = formateador("d 'de' MMM")
    private val mes = formateador("MMMM")
    private val diaMesAnio = formateador("d 'de' MMMM 'de' yyyy")

    /** Calendar(identifier: .gregorian) en la hora y el idioma de Argentina (uno nuevo cada vez, como en iOS). */
    val calendario: Calendar get() = Calendar.getInstance(TimeZone.getTimeZone(zona), argentina)

    /** La fecha y hora en Buenos Aires. */
    fun enArgentina(f: Instant): ZonedDateTime = f.atZone(zona)
    /** El día de Buenos Aires de un instante. */
    fun diaDe(f: Instant): LocalDate = enArgentina(f).toLocalDate()
    /** Calendar.isDate(_:inSameDayAs:) en Buenos Aires. */
    fun mismoDia(a: Instant, b: Instant) = diaDe(a) == diaDe(b)
    /** Hoy en Buenos Aires. */
    fun hoy(): LocalDate = LocalDate.now(zona)
    /** El comienzo de un día de Buenos Aires (startOfDay). */
    fun inicioDe(d: LocalDate): Instant = d.atStartOfDay(zona).toInstant()
    /** Un día y una hora de Buenos Aires como instante. */
    fun instante(d: LocalDate, hora: Int, minuto: Int = 0): Instant = LocalDateTime.of(d, java.time.LocalTime.of(hora, minuto)).atZone(zona).toInstant()

    /** «14:32». */
    fun horaDe(f: Instant): String = hora.format(f)

    /** «jueves 30 de octubre». */
    fun fechaLarga(f: Instant): String = diaLargo.format(f)

    /** «octubre». */
    fun mesDe(f: Instant): String = mes.format(f)
    fun mesDe(d: LocalDate): String = DateTimeFormatter.ofPattern("MMMM", argentina).format(d)

    /** «jueves 12 de noviembre» de un día. */
    fun diaCompleto(d: LocalDate): String = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", argentina).format(d)

    /** «2026-11-12» (Formato.diaISO de iOS, para un día). */
    fun diaISO(d: LocalDate): String = d.toString()

    /** «14 de noviembre de 2026». */
    fun diaMesAnio(f: Instant): String = diaMesAnio.format(f)

    /** «hoy», «ayer», o «28 de oct.». */
    fun dia(f: Instant, ahora: Instant = Instant.now()): String = when {
        mismoDia(f, ahora) -> "hoy"
        mismoDia(f, ahora.minus(1, ChronoUnit.DAYS)) -> "ayer"
        else -> diaCorto.format(f)
    }

    /** Título de sección por día: «Hoy», «Ayer», «Martes 28 de octubre». */
    fun seccion(f: Instant, ahora: Instant = Instant.now()): String = when {
        mismoDia(f, ahora) -> "Hoy"
        mismoDia(f, ahora.minus(1, ChronoUnit.DAYS)) -> "Ayer"
        else -> mayuscula(diaLargo.format(f))
    }

    /** «hoy, 14:32». */
    fun cuando(f: Instant, ahora: Instant = Instant.now()): String = "${dia(f, ahora)}, ${horaDe(f)}"

    /** «hace 3 h», «hace 12 min», «hace 2 días». */
    fun hace(f: Instant, ahora: Instant = Instant.now()): String {
        val s = maxOf(0L, Duration.between(f, ahora).seconds)
        if (s < 60) return "recién"
        if (s < 3600) return "hace ${s / 60}${fino}min"
        if (s < 86400) return "hace ${s / 3600}${fino}h"
        val d = s / 86400
        return if (d == 1L) "hace 1 día" else "hace $d días"
    }

    fun mayuscula(s: String): String = s.take(1).uppercase(argentina) + s.drop(1)

    /** Iniciales para los avatares: «Almacén Lumbre» → «AL». */
    fun iniciales(nombre: String): String {
        val p = nombre.split(' ', '-').filter { it.firstOrNull()?.isLetter() == true }
        val s = p.take(2).map { it.first().toString() }.joinToString("")
        return if (s.isEmpty()) "TL" else s.uppercase(argentina)
    }

    /** «1 propiedad», «5 propiedades». */
    fun cantidad(n: Int, singular: String, plural: String): String = "${numero(n)} ${if (n == 1) singular else plural}"

    private val cortas = setOf("a", "al", "y", "o", "u", "e", "de", "del", "el", "la", "los", "las", "en", "un", "una",
        "con", "por", "que", "se", "tu", "te", "su", "lo", "mi", "es", "no", "ya", "si", "ni")

    /** Une palabras cortas a la siguiente con un espacio duro, para que no queden solas al final de un renglón («a»,
     *  «de», «y», «el»…): sin renglones viudos ni cortes raros. */
    fun sinViudas(s: String): String {
        val palabras = s.split(" ")
        if (palabras.size <= 2) return s
        val r = StringBuilder()
        palabras.forEachIndexed { i, w ->
            r.append(w)
            if (i == palabras.size - 1) return@forEachIndexed
            // La última palabra va pegada a la anteúltima: nunca queda sola en el renglón final.
            val pegar = w.lowercase() in cortas || i == palabras.size - 2
            r.append(if (pegar) " " else " ")
        }
        return r.toString()
    }
}

object Fecha {
    private val fraccion = Regex("""\.\d+""")

    /** RFC 3339 con o sin fracción (Go manda nanosegundos), o una fecha sola. */
    fun iso(s: String?): Instant? {
        if (s == null) return null
        runCatching { return OffsetDateTime.parse(s).toInstant() }
        runCatching { return Instant.parse(s) }
        fraccion.find(s)?.let { m -> runCatching { return OffsetDateTime.parse(s.replaceRange(m.range, m.value.take(10))).toInstant() } }
        return runCatching { LocalDate.parse(s.take(10)).atStartOfDay(Formato.zona).toInstant() }.getOrNull()
    }

    /** «2026-11-12» (o un RFC 3339) → el día (null si no tiene año, mes y día). */
    fun fecha(s: String?): LocalDate? {
        val p = (s ?: return null).take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return null
        return runCatching { LocalDate.of(p[0], p[1], p[2]) }.getOrNull()
    }

    /** El instante en RFC 3339 (lo que espera la API). */
    fun texto(f: Instant): String = DateTimeFormatter.ISO_INSTANT.format(f)
}

/** Las fechas de la API (Codable con Fecha.iso en iOS). */
object FechaSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Fecha", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant = Fecha.iso(decoder.decodeString()) ?: Instant.EPOCH
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(Fecha.texto(value))
}

/** Un Date de un modelo: `val creadaEn: FechaApi? = null`. */
typealias FechaApi = @Serializable(with = FechaSerializer::class) Instant
