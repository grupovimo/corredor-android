package ar.com.corredor.app.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ar.com.corredor.app.nucleo.ErrorApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.time.Instant

/** Los números de la barra y de «Más» (lib/ui/shell/insignias.svelte.ts): mensajes y preguntas sin responder, cobros
 *  atrasados, avisos y quiénes del equipo están en la calle, en un solo pedido liviano (GET /v1/panel/insignias). Se
 *  piden al entrar, con cada cambio en vivo, al volver a la app y cada minuto (las visitas empiezan y terminan solas). */
class Insignias private constructor() {
    companion object {
        val compartidas = Insignias()
    }

    @Serializable
    data class Persona(val id: String, val nombre: String, val foto: String? = null)

    @Serializable
    data class Respuesta(
        val mensajes: Int? = null,
        val preguntas: Int? = null,
        val bandeja: Int? = null,
        val cobranzas: Int? = null,
        val avisos: Int? = null,
        val paraHacer: Int? = null,
        val enLaCalle: List<Persona>? = null,
    )

    var mensajes by mutableIntStateOf(0)
    var preguntas by mutableIntStateOf(0)
    var bandeja by mutableIntStateOf(0)
    var cobranzas by mutableIntStateOf(0)
    var avisos by mutableIntStateOf(0)
    var paraHacer by mutableIntStateOf(0)
    var enLaCalle by mutableStateOf<List<Persona>>(emptyList())
    /** La última vez que la API respondió (para «Todo guardado · hace X»). */
    var guardadoEn by mutableStateOf<Instant?>(null)
    /** Sin señal la última vez que se pidió. */
    var sinRed by mutableStateOf(false)

    suspend fun refrescar(estado: Estado) {
        if (estado.fase != Estado.Fase.adentro) return
        try {
            val r: Respuesta = estado.api.get("/v1/panel/insignias")
            mensajes = r.mensajes ?: 0
            preguntas = r.preguntas ?: 0
            bandeja = r.bandeja ?: (mensajes + preguntas)
            cobranzas = r.cobranzas ?: 0
            avisos = r.avisos ?: 0
            paraHacer = r.paraHacer ?: 0
            enLaCalle = r.enLaCalle ?: emptyList()
            guardadoEn = Instant.now()
            sinRed = false
            estado.sinLeer = bandeja
            estado.avisosPendientes = avisos
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            sinRed = e.sinConexion
        } catch (e: Exception) {
        }
    }

    fun olvidar() {
        mensajes = 0; preguntas = 0; bandeja = 0; cobranzas = 0; avisos = 0; paraHacer = 0
        enLaCalle = emptyList()
        guardadoEn = null
    }
}
