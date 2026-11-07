package ar.com.corredor.app.pantallas.inicio

import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cartera.*
import ar.com.corredor.app.pantallas.cobranzas.*
import ar.com.corredor.app.pantallas.mensajes.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Clon de Pantallas/Inicio/InicioDatos.swift.
// Lo que trae el Inicio y las cuentas puras que hace con eso, como el panel web
// (routes/panel/(app)/+page.server.ts, lib/inicio/modelo.ts y lib/inicio/guia.ts): lo urgente en su
// orden, el ritmo de cada objetivo, los primeros pasos, el próximo paso y las acciones rápidas.

// Las cuentas de calendario van en la hora de Buenos Aires: Formato.zona y Formato.argentina (nucleo/Formato.kt).
internal fun esHoyEnBA(f: Instant): Boolean = Formato.diaDe(f) == Formato.hoy()
internal fun esAyerEnBA(f: Instant): Boolean = Formato.diaDe(f) == Formato.hoy().minusDays(1)
/** «yyyy-MM-dd» de hoy (o de un instante) en Buenos Aires (Formato.diaISO en iOS). */
internal fun diaISOenBA(f: Instant = Instant.now()): String = Formato.diaDe(f).toString()
/** Un día a las 12 del mediodía en Buenos Aires, como Instant. */
internal fun mediodia(d: LocalDate): Instant = d.atTime(12, 0).atZone(Formato.zona).toInstant()

// MARK: - Modelos (con la forma de la API)

/** GET /v1/panel/guia completo (api/internal/dominio/panel/guia.go): los pasos, los usos y el alta. */
@Serializable
data class GuiaInicio(
    val tourVisto: Boolean? = null,
    val pasosOcultos: Boolean? = null,
    val pasos: Guia.Pasos? = null,
    val usos: Usos? = null,
    val contratos: Int? = null,
    val propiedades: Int? = null,
    val contactos: Int? = null,
    val alta: Alta? = null,
) {
    @Serializable
    data class Usos(
        val captar: Int? = null,
        val propiedad: Int? = null,
        val contacto: Int? = null,
        val visita: Int? = null,
        val cobro: Int? = null,
    )
}

/** Una sugerencia de GET /v1/panel (sugerencias.Sugerencia), con el consorcio y el agente. */
@Serializable
data class SugerenciaInicio(
    val id: String,
    val tipo: String? = null,
    val estado: String? = null,
    val accion: String? = null,
    val destacado: String? = null,
    val motivo: String? = null,
    val operacionId: String? = null,
    val contactoId: String? = null,
    val propiedadId: String? = null,
    val contratoId: String? = null,
    val consorcioId: String? = null,
    val agenteId: String? = null,
) {
    /** El botón según el tipo (accionSugerencia del panel): el texto y a dónde lleva. */
    val boton: Pair<String, Any?>
        get() = when (tipo) {
            "coincidencia" -> "Mandarle la ficha" to (contactoId?.let { AFichaContacto(id = it) } ?: AContactos())
            "contacto_frio" -> "Escribirle" to (contactoId?.let { AFichaContacto(id = it) } ?: AContactos())
            "operacion_estancada", "seguimiento_cierre" ->
                "Ver la operación" to (operacionId?.let { AFichaOperacion(id = it) } ?: AOperaciones())
            "ajuste_alquiler" -> "Avisar al inquilino" to (contratoId?.let { AFichaContrato(id = it) } ?: AContratos())
            "contrato_por_vencer" -> "Ver el contrato" to (contratoId?.let { AFichaContrato(id = it) } ?: AContratos())
            "unidad_morosa", "servicio_por_vencer" ->
                "Ver el consorcio" to (consorcioId?.let { ASeccion(clave = "consorcio", id = it) } ?: ASeccion(clave = "consorcios"))
            else -> "Ver" to null
        }

    /** «{accion} {destacado}. {motivo}.» */
    val frase: String
        get() {
            val primera = listOf(accion, destacado).mapNotNull { it?.nonEmpty }.joinToString(" ")
            val partes = listOf(primera, motivo?.nonEmpty).mapNotNull { it?.nonEmpty }
            return partes.joinToString(" ") { if (it.endsWith(".")) it else "$it." }
        }
}

/** Lo de GET /v1/panel que el modelo común no lee: la primera rendición lista y las sugerencias completas. */
@Serializable
data class PanelInicio(val numeros: Numeros? = null, val sugerencias: List<SugerenciaInicio>? = null) {
    @Serializable data class Rendicion(val id: String, val contratoId: String)
    @Serializable data class Numeros(val primeraRendicion: Rendicion? = null)
}

/** Un aviso de consulta web (GET /v1/avisos?vista=para_hacer&tipo=consulta; avisos.Aviso). */
@Serializable
data class AvisoConsulta(
    val id: String,
    val tipo: String? = null,
    val titulo: String? = null,
    val cuerpo: String? = null,
    val url: String? = null,
    val datos: kotlinx.serialization.json.JsonElement? = null,
    val responsableId: String? = null,
    val responsableNombre: String? = null,
    val creadoEn: FechaApi? = null,
) {
    val contactoId: String? get() = datos.texto("contactoId")?.nonEmpty
    val propiedad: String? get() = datos.texto("propiedad")?.nonEmpty
}

@Serializable
data class ListaAvisosConsulta(val avisos: List<AvisoConsulta> = emptyList())

/** Una reserva que vence (GET /v1/reservas?vencen=2; reservas.Reserva). */
@Serializable
data class ReservaInicio(
    val id: String,
    val propiedadId: String? = null,
    val propiedadTitulo: String? = null,
    val contactoNombre: String? = null,
    val interesadoNombre: String? = null,
    val sena: Double? = null,
    val senaMoneda: String? = null,
    val venceEl: String? = null,
    val notas: String? = null,
)

/** El número de WhatsApp (GET /v1/whatsapp/numero): si se cayó en Meta, el Inicio lo avisa. */
@Serializable
data class NumeroWhatsApp(
    val conectado: Boolean? = null,
    val estado: String? = null,
    val estadoMotivo: String? = null,
    val estadoEn: FechaApi? = null,
) {
    /** Conectado en Corredor y caído en Meta. */
    val caido: Boolean get() = conectado == true && (estado ?: "conectado") != "conectado"
}

/** GET /v1/clima (204 si la inmobiliaria no tiene dónde ubicarse). */
@Serializable
data class Clima(val zona: String, val temperatura: Int, val codigo: Int? = null)

/** Una deuda de GET /v1/contratos/deudas (contratos.Deuda), con el vencimiento de cada mes. */
@Serializable
data class DeudaInicio(
    val contratoId: String,
    val propiedadTitulo: String? = null,
    val contactoId: String? = null,
    val contactoNombre: String? = null,
    val agenteId: String? = null,
    val moneda: String? = null,
    val meses: List<Mes>? = null,
    val total: Double? = null,
) {
    @Serializable data class Mes(val periodo: String? = null, val vence: String? = null, val esperado: Double? = null)
    val id: String get() = contratoId
}

/** Un alquiler del mes (cobrado, pendiente o atrasado), con la deuda o con la fila de Cobranzas. */
data class CobranzaInicio(
    val contratoId: String,
    val propiedadTitulo: String?,
    val contactoNombre: String?,
    val agenteId: String?,
    val monto: Double,
    val moneda: String,
    val vence: String,
    val estado: String,
    /** Lo del mes más viejo que se debe (lo que propone «Registrar cobro»). */
    val montoMes: Double? = null,
)

/** Una persona en «El equipo hoy». */
data class PersonaHoy(
    val id: String,
    val nombre: String,
    val foto: String?,
    val telefono: String?,
    val citas: List<Pair<String, Int>>,
    val sinResponder: Int,
    val desde: Instant?,
    /** (tipo, vuelve). */
    val licencia: Pair<String, String>?,
) {
    override fun equals(other: Any?): Boolean = other is PersonaHoy && other.id == id && other.sinResponder == sinResponder
    override fun hashCode(): Int = id.hashCode()
}

/** La próxima visita con lo de la propiedad y el contacto. */
data class ProximaVisita(
    val cita: Cita,
    val foto: String?,
    val fotos: Int,
    val direccion: String?,
    val operacion: String?,
    val precio: Double?,
    val moneda: String?,
    val telefono: String?,
)

/** Una fila de lo urgente (lib/inicio/modelo.ts · Urgente), en la versión del celular. */
data class Urgente(
    val id: String,
    val tipo: Tipo,
    val icono: String,
    val tono: Tono,
    val titulo: String,
    val detalle: String,
    val detalleTono: Tono? = null,
    val ir: Any? = null,
    val agenteId: String? = null,
    /** Cobro: el contrato, el mes que se debe y el monto (para «Recordar» y «Registrar cobro»). */
    val cobranza: CobranzaInicio? = null,
    /** Consulta sin dueño: el contacto para «Asignar» o «Tomarla». */
    val consultaContacto: String? = null,
) {
    enum class Tipo { cobro, consulta, reserva, pregunta, whatsapp }

    override fun equals(other: Any?): Boolean =
        other is Urgente && other.id == id && other.titulo == titulo && other.detalle == detalle && other.consultaContacto == consultaContacto
    override fun hashCode(): Int = id.hashCode()
}

/** Un objetivo con su ritmo (modelo.ts · ritmo). */
data class RitmoInicio(
    val id: String,
    val nombre: String,
    val avance: Double,
    val meta: Double,
    val ancho: Double,
    val raya: Double,
    val adelante: Boolean,
    val falta: Int,
    val moneda: String?,
)

/** Un paso de «Primeros pasos» (guia.ts · Paso). */
data class PasoInicio(val clave: String, val texto: String, val boton: String, val hecho: Boolean) {
    val id: String get() = clave
}

/** El próximo paso útil cuando no hay nada urgente (guia.ts · Proximo). */
data class ProximoInicio(
    val clave: String,
    val icono: String,
    val titulo: String,
    val detalle: String,
    val boton: String,
    val secundario: String? = null,
) {
    val id: String get() = clave
}

/** Una acción rápida (guia.ts · AccionRapida). */
data class AccionInicio(val clave: String, val texto: String, val icono: String) {
    val id: String get() = clave
}

/** Lo que trae el Inicio (todo de una, para pintar una sola vez). */
data class DatosInicio(
    val panel: Panel? = null,
    val panelExtra: PanelInicio? = null,
    val guia: GuiaInicio? = null,
    val conversaciones: List<Conversacion> = emptyList(),
    val preguntas: List<PreguntaML> = emptyList(),
    val consultas: List<AvisoConsulta> = emptyList(),
    val reservas: List<ReservaInicio> = emptyList(),
    val deudas: List<DeudaInicio>? = null,
    val cobranzas: MesCobranzas? = null,
    val agenda: List<Cita> = emptyList(),
    val equipo: List<Miembro> = emptyList(),
    val licencias: List<Licencia> = emptyList(),
    val numero: NumeroWhatsApp? = null,
    val clima: Clima? = null,
    val avisos: Int = 0,
    /** La inmobiliaria con su marca (logo, portada, web): el Inicio es de ella, no genérico. */
    val inmo: Inmobiliaria? = null,
    /** Las propiedades recién movidas, para «Tu cartera». */
    val props: List<Propiedad> = emptyList(),
    val proxima: ProximaVisita? = null,
    /** Los bloques que no llegaron (7C «Error parcial»): «mensajes», «agenda», «cobranzas». */
    val fallo: Set<String> = emptySet(),
    /** Cuándo se trajo (sin señal: «Actualizado a las…»). */
    val actualizado: Instant = Instant.now(),
    val sinRed: Boolean = false,
)

// MARK: - Las cuentas del Inicio

object CuentasInicio {
    /** De noche (desde las 20) el Inicio mira a mañana. */
    fun deNoche(ahora: Instant = Instant.now()): Boolean {
        val h = Formato.enArgentina(ahora).hour
        return h >= 20 || h < 5
    }

    /** «Buen día» hasta las 13, «Buenas tardes» hasta las 20, después «Buenas noches». */
    fun saludo(ahora: Instant = Instant.now()): String {
        val h = Formato.enArgentina(ahora).hour
        if (h in 5 until 13) return "Buen día"
        if (h in 13 until 20) return "Buenas tardes"
        return "Buenas noches"
    }

    val palabras = listOf("Ninguna", "Una", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho", "Nueve", "Diez")

    /** «Cuatro cosas no esperan.» */
    fun cosasQueNoEsperan(n: Int): String {
        if (n == 1) return "Una cosa no espera."
        return "${if (n < palabras.size) palabras[n] else n.toString()} cosas no esperan."
    }

    /** «Quedaron tres cosas para mañana.» */
    fun cosasParaManana(n: Int): String {
        if (n == 1) return "Quedó una cosa para mañana."
        return "Quedaron ${(if (n < palabras.size) palabras[n] else n.toString()).lowercase(Formato.argentina)} cosas para mañana."
    }

    /** Los días de lunes a viernes que quedan en el mes, contando hoy. */
    fun diasHabilesRestantes(ahora: Instant = Instant.now()): Int {
        val hoy = Formato.diaDe(ahora)
        var n = 0
        for (d in hoy.dayOfMonth..hoy.lengthOfMonth()) {
            val f = hoy.withDayOfMonth(d)
            if (f.dayOfWeek != DayOfWeek.SATURDAY && f.dayOfWeek != DayOfWeek.SUNDAY) n += 1
        }
        return n
    }

    private val formatoDiaCorto: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d", Formato.argentina)

    /** «Jueves 12». */
    fun diaCorto(ahora: Instant = Instant.now()): String = Formato.mayuscula(formatoDiaCorto.format(Formato.enArgentina(ahora)))

    /** «9:30» (sin el cero de adelante). */
    fun horaCorta(f: Instant): String {
        val h = Formato.horaDe(f)
        return if (h.startsWith("0")) h.drop(1) else h
    }

    /** Días entre dos fechas (por día calendario). */
    fun diasEntre(a: Instant, b: Instant): Int = ChronoUnit.DAYS.between(Formato.diaDe(a), Formato.diaDe(b)).toInt()

    fun diasEntre(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()

    /** «en 25 min», «hace 2 h», «recién» (la `relativa` del panel). */
    fun relativa(f: Instant, ahora: Instant = Instant.now()): String {
        val s = Duration.between(ahora, f).toMillis() / 1000.0
        if (s > 0) {
            val m = ceil(s / 60).toInt()
            if (m < 60) return "en $m min"
            val h = m / 60
            if (h < 24) return "en $h h"
            return "en ${Formato.cantidad(h / 24, "día", "días")}"
        }
        return Formato.hace(f, ahora)
    }

    val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** «venció hace 2 días» / «venció ayer» / «vence hoy». */
    fun vencio(vence: String, ahora: Instant = Instant.now()): String {
        val f = Fecha.fecha(vence) ?: return ""
        val d = diasEntre(f, Formato.diaDe(ahora))
        if (d <= 0) return "vence hoy"
        if (d == 1) return "venció ayer"
        return "venció hace $d días"
    }

    /** «vence mañana», «vence hoy», «vence el jueves 12». */
    fun vence(fecha: String, ahora: Instant = Instant.now()): String {
        val f = Fecha.fecha(fecha) ?: return ""
        val d = diasEntre(Formato.diaDe(ahora), f)
        if (d <= 0) return "vence hoy"
        if (d == 1) return "vence mañana"
        return "vence el " + formatoDiaCorto.format(f)
    }

    /** «el jueves 12» (sin el mes). */
    fun elDia(fecha: String): String {
        val f = Fecha.fecha(fecha) ?: return fecha
        return formatoDiaCorto.format(f)
    }

    /** El día que vuelve quien está de licencia: el siguiente al último, salteando el fin de semana. */
    fun vuelveDe(hasta: String): String {
        var d = Fecha.fecha(hasta) ?: return hasta
        do {
            d = d.plusDays(1)
        } while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY)
        return d.toString()
    }

    // MARK: Lo urgente

    fun cobroUrgente(c: CobranzaInicio, ahora: Instant = Instant.now()): Urgente {
        val quien = c.contactoNombre ?: "El inquilino"
        val cuando = vencio(c.vence, ahora)
        return Urgente(
            id = "cobro:" + c.contratoId, tipo = Urgente.Tipo.cobro, icono = "dollarsign.circle", tono = Tono.rojo,
            titulo = "$quien no pagó", detalle = "${Formato.mayuscula(cuando)} · ${Formato.plata(c.monto, c.moneda)}", detalleTono = Tono.rojo,
            ir = AFichaContrato(id = c.contratoId), agenteId = c.agenteId, cobranza = c,
        )
    }

    fun consultaUrgente(a: AvisoConsulta, ahora: Instant = Instant.now()): Urgente {
        val libre = a.responsableId == null
        val cuando = a.creadoEn?.let { relativa(it, ahora) } ?: ""
        val detalle = listOfNotNull(a.propiedad, cuando.nonEmpty).joinToString(" · ")
        val ir: Any? = a.url?.let { Ruta.de(it)?.destino } ?: a.contactoId?.let { AFichaContacto(id = it) }
        return Urgente(
            id = "consulta:" + a.id, tipo = Urgente.Tipo.consulta, icono = "globe", tono = Tono.azul,
            titulo = if (libre) "Consulta sin dueño" else (a.titulo ?: "Consulta"),
            detalle = if (libre) detalle else listOfNotNull(detalle.nonEmpty, a.responsableNombre).joinToString(" · "),
            ir = ir, agenteId = a.responsableId, consultaContacto = if (libre) a.contactoId else null,
        )
    }

    fun reservaUrgente(r: ReservaInicio, ahora: Instant = Instant.now()): Urgente {
        val cuando = r.venceEl?.let { vence(it, ahora) } ?: ""
        val sena = r.sena?.let { if (it > 0) Formato.plata(it, r.senaMoneda ?: "ARS") else null }
        return Urgente(
            id = "reserva:" + r.id, tipo = Urgente.Tipo.reserva, icono = "bookmark", tono = Tono.oro,
            titulo = "Reserva ${r.propiedadTitulo ?: ""}",
            detalle = listOfNotNull(Formato.mayuscula(cuando).nonEmpty, sena).joinToString(" · "), detalleTono = Tono.oro,
            ir = AReserva(id = r.id),
        )
    }

    fun preguntaUrgente(q: PreguntaML, ahora: Instant = Instant.now()): Urgente {
        val cuando = q.creadaEn?.let { relativa(it, ahora) } ?: ""
        return Urgente(
            id = "pregunta:" + q.id, tipo = Urgente.Tipo.pregunta, icono = "storefront", tono = Tono.oro,
            titulo = "Pregunta en Mercado Libre",
            detalle = listOfNotNull(q.propiedad?.nonEmpty, cuando.nonEmpty).joinToString(" · "),
            ir = APreguntaML(id = q.id),
        )
    }

    fun whatsappUrgente(c: Conversacion, ahora: Instant = Instant.now()): Urgente {
        val cuando = (c.ultimoEntranteEn ?: c.ultimoMensajeEn)?.let { relativa(it, ahora) } ?: ""
        val texto = c.ultimoTexto?.nonEmpty?.let { "«${it.trim()}»" }
        return Urgente(
            id = "whatsapp:" + c.id, tipo = Urgente.Tipo.whatsapp, icono = "bubble.left", tono = Tono.verde,
            titulo = c.quien, detalle = listOfNotNull(texto, cuando.nonEmpty).joinToString(" · "),
            ir = AChat(id = c.id), agenteId = c.agenteId,
        )
    }

    /** Cobros, consultas, reservas, preguntas, WhatsApp; adentro, en el orden de la API (lo más viejo primero). */
    fun ordenar(us: List<Urgente>): List<Urgente> =
        us.withIndex().sortedWith(compareBy({ it.value.tipo.ordinal }, { it.index })).map { it.value }

    // MARK: Objetivos

    val nombresMetricas = mapOf(
        "operaciones_cerradas" to "Cierres", "propiedades_captadas" to "Captaciones", "contratos_firmados" to "Contratos",
        "visitas_hechas" to "Visitas", "contactos_nuevos" to "Contactos nuevos", "honorarios_usd" to "Honorarios", "honorarios_ars" to "Honorarios",
    )
    val ordenMetricas = listOf("operaciones_cerradas", "propiedades_captadas", "visitas_hechas", "contactos_nuevos", "contratos_firmados", "honorarios_usd", "honorarios_ars")

    fun ordenMetrica(m: String?): Int = ordenMetricas.indexOf(m ?: "").let { if (it < 0) ordenMetricas.size else it }

    fun ritmo(o: Objetivo, ahora: Instant = Instant.now()): RitmoInicio {
        val meta = o.meta ?: 0.0
        val avance = o.avance ?: 0.0
        val hoy = Formato.diaDe(ahora)
        val desde = Fecha.fecha(o.desde) ?: hoy
        val hasta = Fecha.fecha(o.hasta) ?: hoy
        val total = diasEntre(desde, hasta) + 1
        val pasados = min(total, max(0, diasEntre(desde, hoy) + 1))
        val esperado = if (total > 0) meta * pasados.toDouble() / total.toDouble() else meta
        val adelante = avance >= esperado
        val moneda: String? = if (o.metrica == "honorarios_usd") "USD" else if (o.metrica == "honorarios_ars") "ARS" else null
        return RitmoInicio(
            id = o.id, nombre = nombresMetricas[o.metrica ?: ""] ?: (o.metrica ?: ""), avance = avance, meta = meta,
            ancho = min(100.0, if (meta > 0) avance / meta * 100 else 0.0), raya = min(100.0, pasados.toDouble() / max(1, total).toDouble() * 100),
            adelante = adelante, falta = if (adelante) 0 else max(1, ceil(esperado - avance).toInt()), moneda = moneda,
        )
    }

    // MARK: Guía

    val pasosAltaEnInicio = listOf("logo", "marca", "portada", "contacto", "legales", "rubros")

    /** «Primeros pasos»: la dueña o el dueño ve todos; un agente, los suyos (la propiedad, su web y los avisos). */
    fun primerosPasos(g: GuiaInicio, duenio: Boolean): List<PasoInicio> {
        val p = g.pasos
        val todos = mutableListOf(
            PasoInicio("propiedad", "Cargá tu primera propiedad", "Cargar", p?.propiedad ?: false) to false,
            PasoInicio("web", "Mirá tu web", "Abrir", p?.web ?: false) to false,
        )
        val alta = g.alta
        if (alta != null) {
            for (x in alta.pasos) if (x.clave in pasosAltaEnInicio) {
                todos.add(PasoInicio(x.clave, x.titulo, "Completar", x.hecho) to true)
            }
        } else {
            todos.add(PasoInicio("datos", "Subí el logo de la inmobiliaria", "Subir", p?.datos ?: false) to true)
        }
        todos.add(PasoInicio("whatsapp", "Conectá WhatsApp", "Conectar", p?.whatsapp ?: false) to true)
        todos.add(PasoInicio("equipo", "Invitá a alguien del equipo", "Invitar", p?.equipo ?: false) to true)
        todos.add(PasoInicio("avisos", "Activá los avisos", "Activar", p?.avisos ?: false) to false)
        todos.add(PasoInicio("portales", "Publicá en los portales", "Conectar", p?.portales ?: false) to true)
        return todos.filter { duenio || !it.second }.map { it.first }
    }

    /** El próximo paso útil cuando «Para hacer» no tiene nada. Nunca vuelve vacío (hasta tres). */
    fun proximosPasos(g: GuiaInicio?, duenio: Boolean, pasosVisibles: Boolean, libres: Int): List<ProximoInicio> {
        if (g == null) return listOf(ProximoInicio("nueva", "house", "Cargá una propiedad", "Con foto, precio y dirección", "Cargar"))
        val out = mutableListOf<ProximoInicio>()
        val props = g.propiedades ?: 0
        val contactos = g.contactos ?: 0
        if (libres > 0) {
            out.add(ProximoInicio("libres", "tray", "Tomá una consulta sin dueño", if (libres == 1) "Hay 1 esperando" else "Hay $libres esperando", "Ver"))
        }
        if (props == 0) {
            out.add(ProximoInicio("propiedad", "house", "Cargá tu primera propiedad", "Con una foto del cartel alcanza", "Captar",
                secundario = if (duenio) "Importar de Excel" else null))
        } else if (props < 10) {
            out.add(ProximoInicio("mas", "house", "Sumá más propiedades", if (props == 1) "Tenés 1 en la cartera" else "Tenés $props en la cartera", "Cargar"))
        }
        if (contactos == 0) {
            out.add(ProximoInicio("contacto", "person.badge.plus", "Cargá tu primer contacto", "Un interesado o un propietario", "Cargar",
                secundario = if (duenio) "Importar de Excel" else null))
        }
        if (duenio && !pasosVisibles) {
            val a = g.alta
            if (a != null && a.completo < 100) {
                out.add(ProximoInicio("alta", "paintpalette", "Tu inmobiliaria al ${a.completo}%", "Logo, colores, portada y contacto", "Completar"))
            }
            if (g.pasos?.whatsapp == false) out.add(ProximoInicio("whatsapp", "bubble.left.and.bubble.right", "Conectá WhatsApp", "Las consultas entran ordenadas", "Conectar"))
            if (g.pasos?.equipo == false) out.add(ProximoInicio("equipo", "person.2", "Invitá a tu equipo", "Cada agente, desde su celular", "Invitar"))
            if (g.pasos?.portales == false) out.add(ProximoInicio("portales", "storefront", "Publicá en portales", "Mercado Libre, Zonaprop y Argenprop", "Conectar"))
            if (g.alta == null && g.pasos?.datos == false) out.add(ProximoInicio("datos", "paintpalette", "Completá tu web", "Logo, colores y portada", "Completar"))
        }
        if (props > 0 && contactos > 0) {
            out.add(ProximoInicio("visita", "calendar", "Agendá una visita", "Con recordatorio por WhatsApp", "Agendar"))
        }
        if (out.isEmpty()) out.add(ProximoInicio("cartera", "building.2", "Repasá tu cartera", "Precios, fotos y estados", "Ver"))
        return out.take(3)
    }

    /** Las acciones rápidas: arriba lo que falta (sin cartera: captar y cargar), después lo que más usa.
     *  «Registrar cobro» sólo con contratos; la web, al final. */
    fun accionesRapidas(g: GuiaInicio?, web: Boolean): List<AccionInicio> {
        val base = listOf(
            Triple("captar", "Captar con MIRA", "viewfinder"), Triple("propiedad", "Cargar propiedad", "house"),
            Triple("contacto", "Nuevo contacto", "person.badge.plus"), Triple("visita", "Agendar visita", "calendar.badge.plus"),
            Triple("cobro", "Registrar cobro", "dollarsign.circle"),
        )
        val u = g?.usos
        fun usado(c: String): Int = when (c) {
            "captar" -> u?.captar ?: 0
            "propiedad" -> u?.propiedad ?: 0
            "contacto" -> u?.contacto ?: 0
            "visita" -> u?.visita ?: 0
            "cobro" -> u?.cobro ?: 0
            else -> 0
        }
        fun peso(c: String): Int {
            var p = usado(c)
            val props = g?.propiedades ?: -1
            val contactos = g?.contactos ?: -1
            if (props == 0 && c == "captar") p += 2000
            if (props == 0 && c == "propiedad") p += 1999
            if (props > 0 && contactos == 0 && c == "contacto") p += 1000
            return p
        }
        data class Elegida(val i: Int, val x: Triple<String, String, String>, val p: Int)
        val elegidas = base.withIndex()
            .filter { it.value.first != "cobro" || (g?.contratos ?: 0) > 0 }
            .map { Elegida(it.index, it.value, peso(it.value.first)) }
            .sortedWith { a, b -> if (a.p != b.p) b.p.compareTo(a.p) else a.i.compareTo(b.i) }
        val out = elegidas.map { AccionInicio(it.x.first, it.x.second, it.x.third) }.toMutableList()
        if (web) out.add(AccionInicio("web", "Ver mi web", "globe"))
        return out
    }

    // MARK: - Traer el Inicio

    /** Todo en paralelo (un grupo de tareas: nada de `async let`); cada bloque puede fallar solo. Sólo el
     *  panel es obligatorio: si no llega, sale el error (o lo guardado). */
    suspend fun traer(estado: Estado): DatosInicio {
        val api = estado.api
        val duenio = estado.esDuenio
        val ahora = Instant.now()
        val hoy = diaISOenBA(ahora)
        val manana = diaISOenBA(ahora.plusSeconds(86400))
        val pedidos = mutableListOf<Triple<String, String, List<Pair<String, String>>>>(
            Triple("panel", "/v1/panel", emptyList()),
            Triple("guia", "/v1/panel/guia", emptyList()),
            Triple("agenda", "/v1/agenda", listOf("desde" to hoy, "hasta" to manana, "limite" to "200")),
            Triple("cobranzas", "/v1/cobranzas", emptyList()),
            Triple("reservas", "/v1/reservas", listOf("vencen" to "2", "limite" to "10")),
            Triple("conversaciones", "/v1/whatsapp/conversaciones", listOf("noLeidas" to "si", "limite" to "20")),
            Triple("preguntas", "/v1/mercadolibre/preguntas", listOf("estado" to "sin_responder", "limite" to "10")),
            Triple("consultas", "/v1/avisos", listOf("vista" to "para_hacer", "tipo" to "consulta", "limite" to "20")),
            Triple("numero", "/v1/whatsapp/numero", emptyList()),
            Triple("clima", "/v1/clima", emptyList()),
            Triple("deudas", "/v1/contratos/deudas", emptyList()),
            Triple("inmo", "/v1/inmobiliaria", emptyList()),
            Triple("props", "/v1/propiedades", listOf("orden" to "actualizada", "limite" to "10")),
            Triple("contador", "/v1/avisos/contador", emptyList()),
        )
        if (duenio) {
            pedidos.add(Triple("equipo", "/v1/equipo", emptyList()))
            pedidos.add(Triple("licencias", "/v1/licencias", listOf("desde" to hoy, "hasta" to hoy)))
        }
        val r: Map<String, Result<String>> = coroutineScope {
            pedidos.map { (clave, ruta, query) ->
                async {
                    clave to try {
                        Result.success(String(api.pedir("GET", ruta, query), Charsets.UTF_8))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }.awaitAll().toMap()
        }
        fun crudo(clave: String): String? = r[clave]?.getOrNull()?.takeIf { it.isNotEmpty() }
        fun fallo(clave: String): Boolean = r[clave]?.isFailure == true

        val panelR = r["panel"]
        panelR?.exceptionOrNull()?.let { throw it }
        val p = panelR?.getOrNull()
        val panel = p?.let { runCatching { Api.json.decodeFromString<Panel>(it) }.getOrNull() }
        val panelExtra = p?.let { runCatching { Api.json.decodeFromString<PanelInicio>(it) }.getOrNull() }

        @Serializable data class Contador(val noLeidos: Int? = null, val paraHacer: Int? = null)

        val fallos = mutableSetOf<String>()
        if (fallo("conversaciones")) fallos.add("mensajes")
        if (fallo("agenda")) fallos.add("agenda")
        if (fallo("cobranzas")) fallos.add("cobranzas")

        var d = DatosInicio(
            panel = panel,
            panelExtra = panelExtra,
            guia = crudo("guia")?.let { runCatching { Api.json.decodeFromString<GuiaInicio>(it) }.getOrNull() },
            agenda = crudo("agenda")?.let { runCatching { Api.json.decodeFromString<List<Cita>>(it) }.getOrNull() } ?: emptyList(),
            cobranzas = crudo("cobranzas")?.let { runCatching { Api.json.decodeFromString<MesCobranzas>(it) }.getOrNull() },
            reservas = crudo("reservas")?.let { runCatching { Api.json.decodeFromString<List<ReservaInicio>>(it) }.getOrNull() } ?: emptyList(),
            conversaciones = crudo("conversaciones")?.let { runCatching { Api.json.decodeFromString<List<Conversacion>>(it) }.getOrNull() } ?: emptyList(),
            preguntas = crudo("preguntas")?.let { runCatching { Api.json.decodeFromString<List<PreguntaML>>(it) }.getOrNull() } ?: emptyList(),
            consultas = crudo("consultas")?.let { runCatching { Api.json.decodeFromString<ListaAvisosConsulta>(it) }.getOrNull() }?.avisos ?: emptyList(),
            numero = crudo("numero")?.let { runCatching { Api.json.decodeFromString<NumeroWhatsApp>(it) }.getOrNull() },
            clima = crudo("clima")?.let { runCatching { Api.json.decodeFromString<Clima>(it) }.getOrNull() },
            deudas = crudo("deudas")?.let { runCatching { Api.json.decodeFromString<List<DeudaInicio>>(it) }.getOrNull() },
            inmo = crudo("inmo")?.let { runCatching { Api.json.decodeFromString<Inmobiliaria>(it) }.getOrNull() },
            props = crudo("props")?.let { runCatching { Api.json.decodeFromString<List<Propiedad>>(it) }.getOrNull() } ?: emptyList(),
            equipo = crudo("equipo")?.let { runCatching { Api.json.decodeFromString<List<Miembro>>(it) }.getOrNull() } ?: emptyList(),
            licencias = crudo("licencias")?.let { runCatching { Api.json.decodeFromString<List<Licencia>>(it) }.getOrNull() } ?: emptyList(),
            avisos = crudo("contador")?.let { runCatching { Api.json.decodeFromString<Contador>(it) }.getOrNull() }?.noLeidos ?: 0,
            fallo = fallos,
            actualizado = ahora,
        )
        d = d.copy(proxima = proximaVisita(d, estado, ahora))
        return d
    }

    /** La próxima visita (la que sigue hoy; de noche, la primera de mañana), con la foto, la operación,
     *  el precio y el teléfono del contacto. La dueña ve la suya. */
    suspend fun proximaVisita(d: DatosInicio, estado: Estado, ahora: Instant): ProximaVisita? {
        val yo = estado.usuario?.id
        val mias = d.agenda.filter { it.estado == "pendiente" && it.tipo == "visita" && (!estado.esDuenio || it.agenteId == yo) }
            .sortedBy { it.empiezaEn }
        val cita: Cita? = if (deNoche(ahora)) {
            mias.firstOrNull { !Formato.mismoDia(it.empiezaEn, ahora) && it.empiezaEn > ahora }
        } else {
            mias.firstOrNull { Formato.mismoDia(it.empiezaEn, ahora) && it.empiezaEn.plusSeconds(15 * 60) > ahora }
        }
        if (cita == null) return null

        @Serializable data class P(
            val fotosUrl: List<String>? = null, val direccion: String? = null, val barrio: String? = null, val ciudad: String? = null,
            val operacion: String? = null, val precio: Double? = null, val moneda: String? = null,
        )
        @Serializable data class Ct(val telefono: String? = null)

        val p: P? = cita.propiedadId?.let { estado.api.opcional<P>("/v1/propiedades/$it") }
        val t: Ct? = cita.contactoId?.let { estado.api.opcional<Ct>("/v1/contactos/$it") }
        val partes = listOf(p?.direccion, p?.barrio, p?.ciudad).mapNotNull { it?.nonEmpty }
        val dir: String? = if (partes.isEmpty()) cita.lugar else partes.joinToString(", ")
        return ProximaVisita(
            cita = cita, foto = p?.fotosUrl?.firstOrNull(), fotos = p?.fotosUrl?.size ?: 0, direccion = dir,
            operacion = p?.operacion, precio = p?.precio, moneda = p?.moneda, telefono = t?.telefono,
        )
    }
}
