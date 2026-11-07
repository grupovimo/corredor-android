package ar.com.corredor.app.pantallas.agenda
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable
import java.time.Instant

@Composable fun AgendaRaiz(diaInicial: Instant? = null, apilada: Boolean = false) {}
@Composable fun FichaCita(id: String) {}
@Composable fun NuevaCita(dia: Instant = Instant.now()) {}
