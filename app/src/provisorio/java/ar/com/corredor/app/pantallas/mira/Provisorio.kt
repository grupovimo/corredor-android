package ar.com.corredor.app.pantallas.mira
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable

data class ARevisionIds(val ids: String)

@Composable fun Captadas() {}
@Composable fun RevisionIA(ids: String? = null) {}
@Composable fun MiraRaiz() {}
