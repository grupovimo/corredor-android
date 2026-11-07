package ar.com.corredor.app.pantallas.cartera
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable

data class Operacion(val id: String) { companion object { fun etapaTexto(e: String): String = e } }
data class Contacto(val id: String)

@Composable fun Contactos(libres: Boolean = false) {}
@Composable fun FichaContacto(id: String) {}
@Composable fun FichaOperacion(id: String) {}
@Composable fun Operaciones() {}
@Composable fun EditarContacto(contacto: Contacto? = null, soloBusca: Boolean = false) {}
