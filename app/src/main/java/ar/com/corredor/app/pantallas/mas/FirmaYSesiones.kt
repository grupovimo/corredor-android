package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Clon de Pantallas/Mas/FirmaYSesiones.swift.

// MARK: - Perfil: firma y sesiones

/** La firma en mensajes y las sesiones abiertas (lo mismo que en Mi perfil, para el acceso directo de «Más»): la
 *  firma con la sugerida, su hoja con «Deshacer», y cerrar las sesiones de otros aparatos. */
@Composable
fun FirmaYSesiones() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var firma by remember { mutableStateOf<FirmaPerfil?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var editando by remember { mutableStateOf(false) }

    suspend fun cargar() {
        try {
            firma = estado.api.get<FirmaPerfil>("/v1/perfil/firma")
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Firma y sesiones") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val f = firma
            val err = error
            if (f != null) {
                Box(Modifier.fillMaxWidth().tarjeta()) {
                    Tocable({ editando = true }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Texto("Firma en mensajes", Estilo.body16, C.texto)
                            Spacer(Modifier.weight(1f).width(8.dp))
                            Texto(if (f.activa) (f.texto?.takeIf { it.isNotBlank() } ?: "Sin firma") else "Sin firma", Estilo.texto14,
                                if (f.activa) C.texto2 else C.texto3, Modifier.weight(1f, fill = false), lineas = 1)
                            Chevron()
                        }
                    }
                }
            } else if (err != null) {
                EstadoError("tu firma", err) { alcance.launch { cargar() } }
            } else {
                EsqueletoFilas(2)
            }
            ListaSesiones()
        }
    }

    val f = firma
    Hoja(editando && f != null, { editando = false }, alturas = listOf(Altura.media, Altura.grande)) {
        if (f != null) HojaFirmaMensajes(f) { firma = it }
    }
}
