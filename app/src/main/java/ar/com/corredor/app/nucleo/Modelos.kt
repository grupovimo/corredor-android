package ar.com.corredor.app.nucleo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Duration
import java.time.Instant

// Clon de Nucleo/Modelos.swift: los modelos de la API de Corredor (api/internal/dominio/*). Casi todo es opcional: un
// campo nuevo o que falta no rompe la pantalla. Los nombres siguen el JSON de la API.

/** GET /v1/panel: los números, la agenda de hoy, las sugerencias y los objetivos. */
@Serializable
data class Panel(
    val numeros: Numeros? = null,
    val agendaHoy: List<Cita>? = null,
    val sugerencias: List<Sugerencia>? = null,
    val objetivos: List<Objetivo>? = null,
) {
    @Serializable
    data class Numeros(
        val propiedadesDisponibles: Int? = null,
        val propiedadesReservadas: Int? = null,
        val propiedadesNuevasMes: Int? = null,
        val contactos: Int? = null,
        val contactosNuevosMes: Int? = null,
        val operacionesAbiertas: Int? = null,
        val cerradasMes: Int? = null,
        val cerradasMesAnterior: Int? = null,
        val citasHoy: Int? = null,
        val alquileresVigentes: Int? = null,
        val rendicionesListas: Int? = null,
    )
}

/** Una cita de la agenda (visita, tasación, firma, llamada…). */
@Serializable
data class Cita(
    val id: String,
    val tipo: String? = null,
    val estado: String? = null,
    val titulo: String? = null,
    val empiezaEn: FechaApi,
    val duracionMin: Int? = null,
    val propiedadId: String? = null,
    val contactoId: String? = null,
    val operacionId: String? = null,
    val lugar: String? = null,
    val notas: String? = null,
    val recordar: Boolean? = null,
    val agenteId: String? = null,
    val contactoNombre: String? = null,
    val propiedadTitulo: String? = null,
    val agenteNombre: String? = null,
    /** Cuándo se le mandó el recordatorio por WhatsApp («confirmada»). */
    val recordadoEn: FechaApi? = null,
    val creadaEn: FechaApi? = null,
) {
    /** Lo que se ve: la propiedad, el lugar o el título. */
    val donde: String get() = propiedadTitulo ?: lugar ?: titulo ?: "Cita"
    val terminaEn: Instant get() = empiezaEn.plusSeconds(((duracionMin ?: 60) * 60).toLong())
    val icono: String get() = tipos.firstOrNull { it.first == tipo }?.third ?: "calendar"
    val tipoTexto: String get() = tipos.firstOrNull { it.first == tipo }?.second ?: "Cita"

    companion object {
        val tipos: List<Triple<String, String, String>> = listOf(
            Triple("visita", "Visita", "house"), Triple("tasacion", "Tasación", "ruler"), Triple("firma", "Firma", "signature"),
            Triple("llamado", "Llamado", "phone"), Triple("otro", "Tarea", "checklist"),
        )
    }
}

/** «Encontramos por vos»: lo que el sistema propone hacer. */
@Serializable
data class Sugerencia(
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
    val agenteId: String? = null,
)

/** Un objetivo del mes (operaciones, captaciones…) con su avance. */
@Serializable
data class Objetivo(
    val id: String,
    val agenteId: String? = null,
    val metrica: String? = null,
    val meta: Double? = null,
    val desde: String? = null,
    val hasta: String? = null,
    val agenteNombre: String? = null,
    val avance: Double? = null,
    val porcentaje: Double? = null,
) {
    companion object {
        /** Las métricas que registra la API (objetivos.Registrar). */
        val metricas: Map<String, Pair<String, String>> = mapOf(
            "operaciones_cerradas" to ("operación cerrada" to "operaciones cerradas"), "propiedades_captadas" to ("captación" to "captaciones"),
            "visitas_hechas" to ("visita" to "visitas"), "contactos_nuevos" to ("contacto nuevo" to "contactos nuevos"),
            "contratos_firmados" to ("contrato firmado" to "contratos firmados"), "honorarios_usd" to ("dólar de honorarios" to "dólares de honorarios"),
            "honorarios_ars" to ("peso de honorarios" to "pesos de honorarios"),
        )
    }
}

/** GET /v1/contratos/deudas: quién debe y cuánto (la misma regla en toda la app). */
@Serializable
data class Deuda(
    val contratoId: String,
    val propiedadTitulo: String? = null,
    val contactoId: String? = null,
    val contactoNombre: String? = null,
    val agenteId: String? = null,
    val moneda: String? = null,
    val punitorioDiario: Double? = null,
    val meses: List<Mes>? = null,
    val total: Double? = null,
    val estimado: Boolean? = null,
) {
    @Serializable
    data class Mes(val periodo: String? = null, val monto: Double? = null, val esperado: Double? = null, val vence: String? = null)

    val id: String get() = contratoId
}

/** Una conversación de WhatsApp. */
@Serializable
data class Conversacion(
    val id: String,
    val waId: String? = null,
    val nombre: String? = null,
    val contactoId: String? = null,
    val contactoNombre: String? = null,
    val operacionId: String? = null,
    val propiedadId: String? = null,
    val propiedadTitulo: String? = null,
    val agenteId: String? = null,
    val agenteNombre: String? = null,
    val noLeidos: Int? = null,
    val ultimoTexto: String? = null,
    val ultimoMensajeEn: FechaApi? = null,
    val ultimoEntranteEn: FechaApi? = null,
    val enVentana: Boolean? = null,
    /** Lo último es del otro lado: nadie le contestó todavía. */
    val esperando: Boolean? = null,
    val archivada: Boolean? = null,
    /** Se vinculó sola por el código del aviso que trajo un mensaje. */
    val propiedadPorCodigo: Boolean? = null,
    val creadaEn: FechaApi? = null,
) {
    /** Como la web (lib/mensajes/modelo.ts `nombreDe`): el contacto, el nombre de WhatsApp o el número. */
    val quien: String get() = contactoNombre ?: nombre?.nonEmpty ?: telefono(waId ?: "")
    /** Sin contacto ni nombre: «número nuevo · crear contacto». */
    val sinNombre: Boolean get() = contactoNombre == null && nombre?.nonEmpty == null
    /** Fuera de la ventana de 24 h (y ya escribió alguna vez). */
    val fuera: Boolean get() = enVentana != true && ultimoEntranteEn != null

    /** Lo que queda de la ventana de 24 h: «23 h 48 min» (null si está cerrada). */
    fun ventanaRestante(ahora: Instant = Instant.now()): String? {
        val u = ultimoEntranteEn ?: return null
        val resta = Duration.between(ahora, u.plusSeconds(86_400)).seconds
        if (resta <= 0) return null
        val h = resta / 3600; val m = (resta % 3600) / 60
        return if (h > 0) "$h h $m min" else "$m min"
    }

    companion object {
        /** «11 3392-0071» para un número de Buenos Aires sin nombre; si no, «+<número>». */
        fun telefono(wa: String): String {
            for (patron in listOf("^549(11)(\\d{4})(\\d{4})$", "^54(11)(\\d{4})(\\d{4})$")) {
                Regex(patron).find(wa)?.let { m -> return "${m.groupValues[1]} ${m.groupValues[2]}-${m.groupValues[3]}" }
            }
            return if (wa.isEmpty()) "Sin nombre" else "+$wa"
        }
    }
}

/** GET /v1/whatsapp/conversaciones/cuentas: cada vista de la bandeja con su número. */
@Serializable
data class CuentasBandeja(
    val esperando: Int? = null,
    val mias: Int? = null,
    val sinDueno: Int? = null,
    val fueraDeVentana: Int? = null,
    val noLeidas: Int? = null,
    val todas: Int? = null,
)

/** GET /v1/whatsapp/numero: el número conectado y su estado en Meta (conectado, desconectado, restringido, limitado),
 *  por qué y desde cuándo. */
@Serializable
data class NumeroWA(
    val conectado: Boolean,
    val telefono: String? = null,
    val estado: String? = null,
    val estadoMotivo: String? = null,
    val estadoEn: FechaApi? = null,
) {
    /** Conectado en Corredor y caído en Meta: hay que avisar. */
    val caido: Boolean get() = conectado && (estado?.nonEmpty?.let { it != "conectado" } ?: false)
}

/** Una pregunta de Mercado Libre. */
@Serializable
data class PreguntaML(
    val id: String,
    val preguntaId: Long? = null,
    val itemId: String? = null,
    val propiedadId: String? = null,
    val propiedad: String? = null,
    val texto: String? = null,
    val respuesta: String? = null,
    val estado: String? = null,
    val creadaEn: FechaApi? = null,
    val respondidaEn: FechaApi? = null,
    val compradorApodo: String? = null,
)

/** GET /v1/panel/guia: los primeros pasos y lo que la persona ya hizo. */
@Serializable
data class Guia(
    val tourVisto: Boolean? = null,
    val pasosOcultos: Boolean? = null,
    val pasos: Pasos? = null,
    val contratos: Int? = null,
    val propiedades: Int? = null,
    val contactos: Int? = null,
) {
    @Serializable
    data class Pasos(
        val datos: Boolean? = null,
        val propiedad: Boolean? = null,
        val web: Boolean? = null,
        val whatsapp: Boolean? = null,
        val equipo: Boolean? = null,
        val avisos: Boolean? = null,
        val portales: Boolean? = null,
    )
}

/** Una persona del equipo (GET /v1/equipo). */
@Serializable
data class Miembro(
    val usuarioId: String,
    val email: String? = null,
    val nombre: String? = null,
    val apellido: String? = null,
    val foto: String? = null,
    val rol: String? = null,
    val desde: String? = null,
    val propiedades: Int? = null,
    val barrios: List<String>? = null,
    val avisosPush: Boolean? = null,
    val telefono: String? = null,
) {
    val id: String get() = usuarioId
    val completo: String get() = listOfNotNull(nombre, apellido).filter { it.isNotEmpty() }.joinToString(" ").nonEmpty ?: email ?: "Sin nombre"
}

/** Una licencia o ausencia del equipo. */
@Serializable
data class Licencia(
    val id: String,
    val usuarioId: String,
    val usuarioNombre: String? = null,
    val tipo: String? = null,
    val desde: String,
    val hasta: String,
    val nota: String? = null,
)

/** Un aviso del centro de avisos (GET /v1/avisos). */
@Serializable
data class AvisoCentro(
    val id: String,
    val tipo: String? = null,
    val titulo: String? = null,
    val cuerpo: String? = null,
    val url: String? = null,
    /** Lo que el aviso sabe de lo que pasó (la sugerencia de respuesta, la propiedad, el monto…). */
    val datos: JSONLibre? = null,
    /** Quién lo provocó (el que movió la operación) y quién lo tiene que resolver. */
    val actorId: String? = null,
    val actorNombre: String? = null,
    val responsableId: String? = null,
    val responsableNombre: String? = null,
    /** Pide algo (responder, cobrar): al hacerlo queda hecho, no sólo leído. */
    val accionable: Boolean? = null,
    val hecho: Boolean? = null,
    val leido: Boolean? = null,
    val leidoEn: FechaApi? = null,
    val hechoEn: FechaApi? = null,
    val creadoEn: FechaApi? = null,
    val acciones: List<Accion>? = null,
) {
    /** Un botón del aviso (avisos.Accion). Con `ruta`, llama a la API con `metodo` (POST si falta) y `cuerpo`; con
     *  `entrada` "texto", primero pide el texto y manda {"texto": …}. Sin ruta, abre `url`. */
    @Serializable
    data class Accion(
        val accion: String? = null,
        val titulo: String? = null,
        val metodo: String? = null,
        val ruta: String? = null,
        val cuerpo: JSONLibre? = null,
        val url: String? = null,
        val entrada: String? = null,
    )

    val estaHecho: Boolean get() = hecho == true || hechoEn != null
    val estaLeido: Boolean get() = leido == true || leidoEn != null
}

/** GET /v1/avisos: una página, el cursor de la siguiente y los contadores. */
@Serializable
data class PaginaAvisos(val avisos: List<AvisoCentro> = emptyList(), val siguiente: String? = null, val noLeidos: Int? = null, val paraHacer: Int? = null)

/** El resultado de la búsqueda global (GET /v1/buscar). */
@Serializable
data class Resultado(val id: String, val tipo: String, val titulo: String, val subtitulo: String? = null)

/** null si está vacío (o sólo espacios). */
val String.nonEmpty: String? get() = if (isBlank()) null else this

/** Un JSON cualquiera, para los campos libres (marca, datos de un evento). */
@Serializable(with = JSONLibre.Serializador::class)
sealed class JSONLibre {
    data class texto(val v: String) : JSONLibre()
    data class numero(val v: Double) : JSONLibre()
    data class bool(val v: Boolean) : JSONLibre()
    data class objeto(val v: Map<String, JSONLibre>) : JSONLibre()
    data class lista(val v: List<JSONLibre>) : JSONLibre()
    data object nulo : JSONLibre()

    /** El texto, si es un texto (`.texto` en iOS). */
    val comoTexto: String? get() = (this as? JSONLibre.texto)?.v
    /** `.texto` de iOS (el mismo nombre que el caso: uno es la propiedad y el otro la clase). */
    val texto: String? get() = comoTexto
    /** subscript(k) de iOS: el campo `k` de un objeto. */
    operator fun get(k: String): JSONLibre? = (this as? objeto)?.v?.get(k)

    val elemento: JsonElement get() = when (this) {
        is JSONLibre.texto -> JsonPrimitive(v)
        is numero -> JsonPrimitive(v)
        is bool -> JsonPrimitive(v)
        is objeto -> JsonObject(v.mapValues { it.value.elemento })
        is lista -> JsonArray(v.map { it.elemento })
        nulo -> JsonNull
    }

    companion object {
        fun de(e: JsonElement): JSONLibre = when (e) {
            is JsonNull -> JSONLibre.nulo
            is JsonObject -> JSONLibre.objeto(e.mapValues { de(it.value) })
            is JsonArray -> JSONLibre.lista(e.map { de(it) })
            is JsonPrimitive -> when {
                e.isString -> JSONLibre.texto(e.content)
                e.booleanOrNull != null -> JSONLibre.bool(e.booleanOrNull!!)
                e.doubleOrNull != null -> JSONLibre.numero(e.doubleOrNull!!)
                else -> JSONLibre.texto(e.content)
            }
        }
    }

    object Serializador : KSerializer<JSONLibre> {
        override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
        override fun deserialize(decoder: Decoder): JSONLibre = de((decoder as JsonDecoder).decodeJsonElement())
        override fun serialize(encoder: Encoder, value: JSONLibre) = (encoder as JsonEncoder).encodeJsonElement(value.elemento)
    }
}
