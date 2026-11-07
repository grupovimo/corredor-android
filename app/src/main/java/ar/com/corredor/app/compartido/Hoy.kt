package ar.com.corredor.app.compartido

import android.content.Context
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.FechaApi
import ar.com.corredor.app.nucleo.opcional
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

/** Lo que muestran los widgets (14A, 14E): la próxima visita, lo sin responder y los cobros de hoy. El widget lo pide a
 *  la API con la sesión compartida y guarda lo último para mostrarlo sin señal. */
@Serializable
data class Hoy(
    val visita: Visita? = null,
    val sinResponder: Int = 0,
    val mensajes: List<Mensaje> = emptyList(),
    val cobrosHoy: Int = 0,
    val actualizado: FechaApi = Instant.EPOCH,
) {
    @Serializable data class Visita(val id: String, val donde: String, val con: String? = null, val empieza: FechaApi)
    @Serializable data class Mensaje(val id: String, val quien: String, val canal: String, val cuando: FechaApi? = null)

    fun guardar(ctx: Context) {
        runCatching { archivo(ctx).writeText(Api.json.encodeToString(this)) }
    }

    companion object {
        val vacio = Hoy()

        private fun archivo(ctx: Context) = File(ctx.cacheDir, "hoy.json")

        fun leer(ctx: Context): Hoy = runCatching { Api.json.decodeFromString<Hoy>(archivo(ctx).readText()) }.getOrDefault(vacio)

        @Serializable private data class Cita(
            val id: String, val empiezaEn: FechaApi, val propiedadTitulo: String? = null, val lugar: String? = null,
            val titulo: String? = null, val contactoNombre: String? = null, val estado: String? = null,
        )
        @Serializable private data class Panel(val agendaHoy: List<Cita>? = null)
        @Serializable private data class Conv(val id: String, val contactoNombre: String? = null, val nombre: String? = null, val noLeidos: Int? = null, val ultimoMensajeEn: FechaApi? = null)

        /** Lo pide a la API (la próxima visita de hoy y lo sin responder) con la sesión compartida. */
        suspend fun traer(ctx: Context): Hoy {
            val api = SesionCompartida.api(ctx) ?: return leer(ctx)
            val p = api.opcional<Panel>("/v1/panel") ?: return leer(ctx)
            val convs = api.opcional<List<Conv>>("/v1/whatsapp/conversaciones?noLeidas=si&limite=20") ?: emptyList()
            val ahora = Instant.now()
            val v = (p.agendaHoy ?: emptyList()).filter { it.empiezaEn.plusSeconds(3600).isAfter(ahora) && it.estado != "cancelada" }.minByOrNull { it.empiezaEn }
            val h = Hoy(
                visita = v?.let { Visita(it.id, it.propiedadTitulo ?: it.lugar ?: it.titulo ?: "Visita", it.contactoNombre, it.empiezaEn) },
                sinResponder = convs.sumOf { it.noLeidos ?: 0 },
                mensajes = convs.take(3).map { Mensaje(it.id, it.contactoNombre ?: it.nombre ?: "Sin nombre", "wa", it.ultimoMensajeEn) },
                cobrosHoy = 0, actualizado = ahora,
            )
            h.guardar(ctx)
            return h
        }
    }
}
