package ar.com.corredor.app.pantallas.consorcios
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable

data class ALiquidarConsorcio(val consorcio: String, val periodo: String? = null)
data class ATrabajoConsorcio(val consorcio: String, val id: String)
data class AFichaConsorcioEn(val id: String, val pestana: String, val pedido: String? = null)

@Composable fun FichaTrabajoConsorcio(consorcio: String, id: String) {}
@Composable fun LiquidarConsorcio(consorcio: String, pedido: String? = null) {}
@Composable fun FichaConsorcio(id: String, pestanaInicial: String = "resumen", pedidoInicial: String? = null) {}
