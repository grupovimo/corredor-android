package ar.com.corredor.app.pantallas.mas
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable

data class ACarteraAgente(val usuario: String, val nombre: String)

@Composable fun CarteraAgente(usuario: String, nombre: String) {}
@Composable fun MasRaiz() {}
@Composable fun MiPerfil() {}
