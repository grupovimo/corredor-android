package ar.com.corredor.app.pantallas.cobranzas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

object RendicionesListas {
    /** `GET /v1/rendiciones/listas`: un cobro sin rendir (no es una rendición todavía: no tiene id). */
    @Serializable
    data class Lista(
        val contratoId: String,
        val periodo: String,
        val propiedadTitulo: String? = null,
        val propietarioNombre: String? = null,
        val cobrado: Double? = null,
        val moneda: String? = null,
    ) {
        val id: String get() = contratoId + periodo
        /** «AAAA-MM» (la API manda el primer día del mes). */
        val mes: String get() = periodo.take(7)
    }
}

/** Las rendiciones listas para hacer este mes: los alquileres con el mes cobrado que todavía no tienen
 *  rendición. Como la web, cada una lleva al contrato; «Armar» la crea y abre (el dueño la emite). */
@Composable
fun RendicionesListas() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<RendicionesListas.Lista>>(emptyList()) }
    var armando by remember { mutableStateOf<String?>(null) }

    suspend fun cargar() { lista = estado.api.opcional("/v1/rendiciones/listas") ?: emptyList() }

    /** Arma la rendición de ese mes y la abre. */
    suspend fun armar(r: RendicionesListas.Lista) {
        @Serializable data class Cuerpo(val periodo: String)
        @Serializable data class Nueva(val id: String)
        armando = r.id
        try {
            val n: Nueva = estado.api.enviar("POST", "/v1/contratos/${r.contratoId}/rendiciones", Cuerpo(r.mes))
            estado.abrir(ARendicion(contrato = r.contratoId, id = n.id))
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { armando = null }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Rendiciones") {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeccionCobro {
                    if (lista.isEmpty()) {
                        Texto("Se arman solas con lo cobrado. Acá aparecen cuando están listas.", Estilo.body, C.texto2, Modifier.padding(16.dp))
                    }
                    lista.forEachIndexed { i, r ->
                        if (i > 0) LineaFila(16.dp)
                        FilaDeslizable(
                            derecha = if (estado.escribe) listOf(AccionFila("Armar", color = C.accion) { alcance.launch { armar(r) } }) else emptyList(),
                        ) {
                            FilaToque(Modifier.background(C.superficie), habilitado = armando == null, alTocar = { estado.abrir(AFichaContrato(id = r.contratoId)) }) {
                                Column(Modifier.weight(1f)) {
                                    Texto(r.propiedadTitulo ?: "Contrato", Estilo.filaLista, C.texto)
                                    Texto(listOfNotNull(r.propietarioNombre, Formato.mayuscula(ReciboVista.mes(r.mes))).joinToString(" · "), Estilo.footnote, C.texto2)
                                }
                                Texto(Formato.plata(r.cobrado ?: 0.0, r.moneda ?: "ARS"), Estilo(17f, Peso.semibold), C.texto)
                            }
                        }
                    }
                }
            }
        }
    }
}
