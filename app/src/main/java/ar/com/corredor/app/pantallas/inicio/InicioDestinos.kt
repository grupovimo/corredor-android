package ar.com.corredor.app.pantallas.inicio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mas.*

// Clon de Pantallas/Inicio/InicioDestinos.swift.

/** Los destinos del Inicio: búsqueda, avisos, su configuración y el perfil (los `navigationDestination` de iOS).
 *  app/Registro.kt lo llama con cada destino apilado: pinta la pantalla y devuelve true si es suyo. */
@Composable
fun destinosInicio(d: Any): Boolean {
    when (d) {
        is ABuscar -> BuscarTodo(texto = d.texto)
        is AAvisos -> {
            // Un aviso puntual (`/avisos?aviso=<id>&responder=<acción>`): arriba y con su hoja.
            remember(d) { d.aviso?.let { Avisos.foco = it to d.responder } }
            Avisos()
        }
        is AConfigAvisos -> ConfigAvisos()
        is APerfil -> MiPerfil()
        else -> return false
    }
    return true
}
