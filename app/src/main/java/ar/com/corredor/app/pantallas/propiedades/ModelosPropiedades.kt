package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.nucleo.capitalizado

import androidx.compose.ui.graphics.Color
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.FechaApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.JSONLibre
import ar.com.corredor.app.nucleo.nonEmpty
import ar.com.corredor.app.tema.hex
import ar.com.corredor.app.tema.plata
import kotlinx.serialization.Serializable

// Clon de Pantallas/Propiedades/ModelosPropiedades.swift.

/** Una propiedad (GET /v1/propiedades, /v1/propiedades/{id}). */
@Serializable
data class Propiedad(
    val id: String,
    val numero: Int? = null,
    val titulo: String? = null,
    val slug: String? = null,
    val descripcion: String? = null,
    val tipo: String? = null,
    val operacion: String? = null,
    val estado: String? = null,
    val precio: Double? = null,
    val moneda: String? = null,
    val periodo: String? = null,
    val expensas: Double? = null,
    val precioOculto: Boolean? = null,
    val direccion: String? = null,
    val barrio: String? = null,
    val ciudad: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val ambientes: Int? = null,
    val dormitorios: Int? = null,
    val banos: Int? = null,
    val cocheras: Int? = null,
    val superficie: Double? = null,
    val cubierta: Double? = null,
    val antiguedad: Int? = null,
    val aptoCredito: Boolean? = null,
    val tambienTemporario: Boolean? = null,
    val comodidades: List<String>? = null,
    val caracteristicas: List<String>? = null,
    val servicios: List<String>? = null,
    val fotos: List<String>? = null,
    val video: String? = null,
    val destacada: Boolean? = null,
    val publicada: Boolean? = null,
    val agenteId: String? = null,
    val propietarioId: String? = null,
    val emprendimientoId: String? = null,
    val fotosUrl: List<String>? = null,
    /** La ficha libre (notas internas, datos de MIRA). */
    val ficha: JSONLibre? = null,
    val creadaEn: FechaApi? = null,
    val actualizadoEn: FechaApi? = null,
) {
    val nombre: String get() = titulo?.nonEmpty ?: direccion ?: "Propiedad"
    /** Captada con MIRA (lo que la web muestra en «Captadas»). */
    val deMira: Boolean get() = ficha?.get("notasInternas")?.texto?.startsWith("Captada con MIRA") == true
    val portada: String? get() = fotosUrl?.firstOrNull()
    val miniatura: String? get() = fotosUrl?.firstOrNull()?.let { it + (if (it.contains("?")) "&" else "?") + "tam=chica" }

    /** Borrador, como en la web (lib/propiedades/propiedad.ts): disponible y fuera de la web. */
    val esBorrador: Boolean get() = (estado ?: "disponible") == "disponible" && publicada == false

    val operacionTexto: String get() = operaciones.firstOrNull { it.first == operacion }?.second?.lowercase(Formato.argentina) ?: (operacion ?: "")
    val tipoTexto: String get() = tipos.firstOrNull { it.first == tipo }?.second ?: (tipo?.capitalizado() ?: "")

    /** «USD 172.000», «ARS 611.800 / mes», «USD 180 / noche». */
    val precioTexto: String
        get() {
            val precio = precio
            if (precio == null || precioOculto == true) return "Consultar precio"
            val p = Formato.plata(precio, moneda ?: "USD")
            return when (operacion) {
                "alquiler" -> "$p / mes"
                "temporario" -> p + (if (periodo == "semana") " / semana" else if (periodo == "mes") " / mes" else " / noche")
                else -> p
            }
        }

    /** «Belgrano · venta · 3 amb · 74 m²». */
    val resumen: String
        get() = listOf(
            barrio ?: ciudad, operacionTexto.nonEmpty, if (tipo == "ph") "PH" else null, ambientes?.let { "$it amb" },
            superficie?.let { "${Formato.numero(it.toInt())} m²" },
        ).filterNotNull().joinToString(" · ")

    companion object {
        val operaciones = listOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporada")
        /** Los que acepta la API (propiedades.Tipos). */
        val tipos = listOf(
            "departamento" to "Departamento", "casa" to "Casa", "ph" to "PH", "duplex" to "Dúplex", "lote" to "Lote", "local" to "Local",
            "oficina" to "Oficina", "cochera" to "Cochera", "galpon" to "Galpón",
        )
        /** Los que acepta la API (propiedades.Estados). «Borrador» no es un estado: es disponible y sin publicar. */
        val estados = listOf(
            "disponible" to "Disponible", "reservada" to "Reservada", "vendida" to "Vendida", "alquilada" to "Alquilada",
            "suspendida" to "Suspendida",
        )
    }
}

/** Lo que pasa con una propiedad (GET /v1/propiedades/senales): portales, error, interesados, visita. */
@Serializable
data class Senal(
    val propiedadId: String,
    val codigo: String? = null,
    val portales: Map<String, String>? = null,
    val error: ErrorPortal? = null,
    val interesados: Int? = null,
    val ultimaConsulta: FechaApi? = null,
    val sinResponder: Int? = null,
    val proximaVisita: FechaApi? = null,
    val proximaVisitaCon: String? = null,
    val reserva: Reservada? = null,
    val vistasSemana: Int? = null,
    val precioAnterior: Double? = null,
    val precioCambio: FechaApi? = null,
    val cargadaPor: String? = null,
) {
    @Serializable data class ErrorPortal(val portal: String? = null, val error: String? = null, val en: FechaApi? = null)
    @Serializable data class Reservada(val id: String? = null, val venceEl: String? = null, val sena: Double? = null, val senaMoneda: String? = null, val interesado: String? = null)

    val publicadaEn: Int get() = (portales ?: emptyMap()).values.count { it == "publicada" || it == "activa" }

    companion object {
        /** Las señales de esas propiedades. La API pide `?ids=` (hasta 200 por pedido; sin ids contesta vacío): van de
         *  a 100, como en la web. */
        suspend fun de(ids: List<String>, api: Api): Map<String, Senal> {
            val todas = mutableListOf<Senal>()
            for (tanda in ids.chunked(100)) {
                val s: List<Senal> = api.get("/v1/propiedades/senales", listOf("ids" to tanda.joinToString(",")))
                todas += s
            }
            val r = LinkedHashMap<String, Senal>()
            todas.forEach { if (!r.containsKey(it.propiedadId)) r[it.propiedadId] = it }
            return r
        }
    }
}

/** Un interesado en una propiedad (GET /v1/propiedades/{id}/interesados). */
@Serializable
data class Interesado(
    val contactoId: String,
    val nombre: String,
    val telefono: String? = null,
    val email: String? = null,
    val agenteId: String? = null,
    val puntaje: Double? = null,
    val visito: Boolean? = null,
    val enviadaEn: FechaApi? = null,
) {
    val id: String get() = contactoId
}

/** Una publicación en un portal (GET /v1/propiedades/{id}/publicaciones). */
@Serializable
data class PublicacionPortal(
    val id: String,
    val portal: String,
    val estado: String,
    val url: String? = null,
    val error: String? = null,
    val visitas: Int? = null,
    val visitasSemana: Int? = null,
    val consultas: Int? = null,
    val propiedadId: String? = null,
    /** El título de la propiedad (GET /v1/publicaciones). */
    val propiedad: String? = null,
    val externoId: String? = null,
    val publicadaEn: FechaApi? = null,
    val sincronizadaEn: FechaApi? = null,
    val actualizadoEn: FechaApi? = null,
) {
    val nombre: String get() = nombres[portal] ?: portal.capitalizado()

    companion object {
        val nombres = mapOf(
            "web" to "Tu web", "mercadolibre" to "Mercado Libre", "zonaprop" to "Zonaprop", "argenprop" to "Argenprop",
            "properati" to "Properati", "inmuebles24" to "Inmuebles24",
        )
    }
}

/** La inmobiliaria (GET /v1/inmobiliaria): la web pública para armar los links de las fichas. */
@Serializable
data class Inmobiliaria(
    val id: String,
    val slug: String? = null,
    val nombre: String? = null,
    val web: String? = null,
    val dominio: String? = null,
    val cbu: String? = null,
    val zonaHoraria: String? = null,
    /** La marca de su web (Mi web): logo, portada, colores y frase. La app la usa para sentirse propia. */
    val marca: JSONLibre? = null,
) {
    private fun texto(k: String): String? = marca?.get(k)?.texto?.takeIf { it.isNotEmpty() }
    val logoUrl: String? get() = texto("logoUrl")
    val portadaUrl: String? get() = texto("portadaUrl")
    val frase: String? get() = texto("frase")
    /** El color principal de su marca («#1E5749»), si lo eligió. */
    val colorPrimario: Color?
        get() {
            var h = texto("colorPrimario") ?: return null
            if (h.startsWith("#")) h = h.drop(1)
            if (h.length != 6) return null
            val n = h.toLongOrNull(16) ?: return null
            return hex(n)
        }
}

