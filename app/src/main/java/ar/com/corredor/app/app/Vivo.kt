package ar.com.corredor.app.app

import ar.com.corredor.app.nucleo.AFichaOperacion
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.FechaApi
import ar.com.corredor.app.nucleo.cargarEstructura
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.pantallas.cartera.Operacion
import ar.com.corredor.app.widgets.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.min

// En vivo: el mismo stream que usa el panel web (GET /v1/eventos, Server-Sent Events). Cada cambio de la inmobiliaria
// (una consulta que entra, un cobro, una visita que se mueve, lo que carga otro del equipo) sube `version` y las
// pantallas abiertas se vuelven a pedir; el evento «avisos» trae el número de la campana. Corre mientras la app está
// adelante y adentro: Principal lo arranca con la app en primer plano y la tarea se cancela sola al ir al fondo.

/** Abre el stream de eventos y entrega cada línea del cuerpo tal como llega (hasta que se corta o se cancela). */
suspend fun Api.eventos(alLlegar: (String) -> Unit) {
    val r = Request.Builder().url(url("/v1/eventos")).header("Accept", "text/event-stream").header("Cache-Control", "no-store")
    token?.let { r.header("Authorization", "Bearer $it") }
    val largo = http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()
    val llamada = largo.newCall(r.build())
    withContext(Dispatchers.IO) {
        val corte = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { runCatching { llamada.cancel() } }
        try {
            llamada.execute().use { res ->
                if (res.code != 200) throw ErrorApi.desconocido
                val fuente = res.body?.source() ?: throw ErrorApi.desconocido
                while (isActive) {
                    val linea = fuente.readUtf8Line() ?: break
                    withContext(Dispatchers.Main) { alLlegar(linea) }
                }
            }
        } finally {
            corte?.dispose()
        }
    }
}

/** Escucha hasta que se cancela la tarea; si se corta, reconecta (3 s, y más espaciado si falla seguido). */
suspend fun Estado.escucharEnVivo() {
    var espera = 3L
    while (coroutineContext.isActive) {
        try {
            // Cada evento de la API es «event: x» + una sola línea «data: {…}»: se despacha al llegar el data. Los
            // latidos («: latido») se ignoran.
            var evento = "message"
            api.eventos { linea ->
                if (linea.startsWith("event:")) {
                    evento = linea.drop(6).trim()
                } else if (linea.startsWith("data:")) {
                    recibir(evento, linea.drop(5).trim())
                    evento = "message"
                }
            }
            espera = 3
        } catch (e: CancellationException) {
            enVivo = false
            throw e
        } catch (e: Exception) {
            espera = min(espera * 2, 60)
        }
        enVivo = false
        delay(espera * 1000)
    }
    enVivo = false
}

@Serializable private data class EventoAvisos(val noLeidos: Int? = null, val paraHacer: Int? = null)
@Serializable private data class EventoCambio(val tabla: String? = null, val op: String? = null, val id: String? = null)

private fun Estado.recibir(evento: String, datos: String) {
    when (evento) {
        // Recién conectado (o reconectado): lo que haya cambiado mientras tanto se vuelve a pedir.
        "listo" -> if (!enVivo) { enVivo = true; cambio() }
        "avisos" -> {
            runCatching { Api.json.decodeFromString<EventoAvisos>(datos) }.getOrNull()?.noLeidos?.let { avisosPendientes = it }
            cambio()
        }
        "cambio", "resync" -> {
            // La marca (logo, colores) o el alta cambiaron (se publicó la web, se completó un paso): la estructura se
            // vuelve a pedir enseguida, así el acento y el porcentaje quedan al día.
            val c = runCatching { Api.json.decodeFromString<EventoCambio>(datos) }.getOrNull()
            if (evento == "resync" || c?.tabla == "inmobiliarias") tarea { cargarEstructura() }
            // Otra persona movió una operación: «Sofía pasó Mendoza 2350 a Visita · recién», con «Ver».
            val id = c?.id
            if (c?.tabla == "operaciones" && c.op == "update" && id != null) tarea { avisarMovida(id) }
            cambio()
        }
        else -> {}
    }
}

@Serializable private data class EventoHistorial(
    val tipo: String? = null,
    val datos: Datos? = null,
    val autorId: String? = null,
    val autorNombre: String? = null,
    val creadoEn: FechaApi? = null,
) {
    @Serializable data class Datos(val hasta: String? = null)
}

@Serializable private data class OpMovida(val titulo: String? = null, val propiedadTitulo: String? = null)

/** La última movida de la operación (operaciones/[id]/movida del panel): quién la pasó y a qué etapa, si fue un cambio
 *  de etapa del último minuto y no lo hizo quien mira. */
private suspend fun Estado.avisarMovida(id: String) {
    val historial = api.opcional<List<EventoHistorial>>("/v1/operaciones/$id/historial") ?: return
    val e = historial.firstOrNull() ?: return
    val hasta = e.datos?.hasta ?: return
    val autor = e.autorId ?: return
    val en = e.creadoEn ?: return
    if (e.tipo != "etapa" || autor == usuario?.id || Duration.between(en, Instant.now()).seconds > 60) return
    val op = api.opcional<OpMovida>("/v1/operaciones/$id") ?: return
    val quien = (e.autorNombre ?: "").split(" ").firstOrNull() ?: ""
    val que = (op.propiedadTitulo ?: op.titulo ?: "").split(",").firstOrNull() ?: ""
    sugerir("$quien pasó $que a ${Operacion.etapaTexto(hasta)} · recién", "Ver", AFichaOperacion(id))
}

/** Junta los cambios que llegan seguidos (una importación, un lote de cobros) en una sola recarga. */
private fun Estado.cambio() {
    if (recargaVivo != null) return
    recargaVivo = tarea {
        delay(400)
        version += 1
        contexto?.let { Widgets.actualizar(it) }
        recargaVivo = null
    }
}
