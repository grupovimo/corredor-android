package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

// Clon de Pantallas/Consorcios/Reglamento.swift.

// MARK: - Reglamento del consorcio

/** GET /v1/consorcios/{id}/reglamento (`ReglamentoConsorcio.R` en iOS). */
@Serializable
data class ReglamentoConsorcioR(
    /** RFC 3339 (Date en iOS). */
    val guardadoEn: String? = null,
    val sinCambios: Boolean? = null,
    val cambios: List<Cambio>? = null,
) {
    @Serializable
    data class Cambio(val unidad: String? = null, val columna: String? = null, val reglamento: Double? = null, val hoy: Double? = null)
}

/** `ReglamentoConsorcio.num` en iOS: 4 decimales con coma, o «—». */
fun numReglamento(n: Double?): String = n?.let { String.format(Locale.US, "%.4f", it).replace(".", ",") } ?: "—"

/** Los coeficientes del reglamento: si los de hoy cambiaron desde que se guardaron, cuáles; y «Guardar los de hoy como
 *  reglamento». */
@Composable
fun ReglamentoConsorcio(consorcio: String) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var r by remember { mutableStateOf<ReglamentoConsorcioR?>(null) }

    suspend fun cargar() { r = estado.api.opcional<ReglamentoConsorcioR>("/v1/consorcios/$consorcio/reglamento") }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Reglamento") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val x = r
            if (x != null) {
                SeccionConsorcio {
                    if (x.guardadoEn == null) {
                        Texto("Todavía no guardaste los coeficientes del reglamento.", Estilo.body, C.texto2)
                    } else if (x.sinCambios == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("checkmark.seal", 17f, C.okTinta)
                            Texto("Coeficientes del reglamento: sin cambios", Estilo.body, C.okTinta)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("exclamationmark.triangle", 17f, C.avisoTinta)
                            Texto("Hay coeficientes distintos a los del reglamento", Estilo.body, C.avisoTinta)
                        }
                    }
                    x.guardadoEn?.let { Fecha.iso(it) }?.let { g -> Texto("Guardado ${Formato.cuando(g)}", Estilo.footnote, C.texto3) }
                }
                val c = x.cambios
                if (!c.isNullOrEmpty()) {
                    SeccionConsorcio("Cambios") {
                        c.forEach { y ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Texto("${y.unidad ?: ""} · ${y.columna ?: ""}", Estilo.body, C.texto, Modifier.weight(1f))
                                Texto("${numReglamento(y.reglamento)} → ${numReglamento(y.hoy)}", Estilo.body, C.texto2, alinear = TextAlign.End)
                            }
                        }
                    }
                }
                if (estado.escribe) {
                    SeccionConsorcio {
                        Texto("Guardar los de hoy como reglamento", Estilo.body, C.accion, Modifier.fillMaxWidth().clickable {
                            alcance.launch {
                                try {
                                    estado.api.hacer("PUT", "/v1/consorcios/$consorcio/reglamento")
                                    cargar()
                                    estado.avisar("Reglamento guardado")
                                } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                            }
                        }.padding(vertical = 4.dp))
                    }
                }
            } else Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) { Rueda() }
        }
    }
}
