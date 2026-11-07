package ar.com.corredor.app.pantallas.entrada
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable

object ElegirInmobiliaria { fun rol(r: String): String = r }

@Composable fun ElegirInmobiliaria() {}
@Composable fun AltaInmobiliaria(inicial: String?) {}
@Composable fun Bienvenida() {}
@Composable fun Entrada() {}
@Composable fun FondoVerde(patron: Double = 0.09) {}
@Composable fun VerInvitacion(token: String) {}
