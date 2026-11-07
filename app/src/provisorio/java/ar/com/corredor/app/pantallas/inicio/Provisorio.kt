package ar.com.corredor.app.pantallas.inicio
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable
import ar.com.corredor.app.app.Estado

object Avisos {
    var foco: Pair<String, String?>? = null
    fun abrir(estado: Estado, aviso: String, responder: String?) { foco = aviso to responder }
}

@Composable fun Avisos() {}
@Composable fun BuscarTodo(texto: String) {}
@Composable fun ConfigAvisos() {}
@Composable fun InicioRaiz() {}
