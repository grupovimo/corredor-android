package ar.com.corredor.app.pantallas.secciones
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonElement

object ListaGenerica {
    fun titulo(x: JsonElement): String = ""
    fun detalle(x: JsonElement): String? = null
}

@Composable fun Seccion(clave: String, id: String? = null) {}
