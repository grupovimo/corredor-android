package ar.com.corredor.app.pantallas.propiedades
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable data class AArreglarPropiedad(val id: String)
@Serializable data class AMandarPropiedad(val id: String, val contacto: String? = null)
@Serializable data class AEditarEnPaso(val id: String, val paso: String)
@Serializable data class AReserva(val id: String)
@Serializable data class AReservaTemporada(val id: String)
@Serializable data class AMercadoLibre(val nada: Int = 0)
@Serializable data class ATarjetaPropiedad(val id: String)
@Serializable data class ACartera(val barrio: String)

@Serializable data class FiltrosCartera(val barrio: List<String> = emptyList())

@Serializable data class Inmobiliaria(val slug: String? = null, val nombre: String? = null, val web: String? = null, val cbu: String? = null)

@Serializable data class Propiedad(
    val id: String, val numero: Int? = null, val titulo: String? = null, val slug: String? = null, val descripcion: String? = null,
    val tipo: String? = null, val operacion: String? = null, val precio: Double? = null, val moneda: String? = null, val expensas: Double? = null,
    val direccion: String? = null, val barrio: String? = null, val ambientes: Int? = null, val dormitorios: Int? = null, val banos: Int? = null,
    val cocheras: Int? = null, val superficie: Double? = null, val cubierta: Double? = null, val aptoCredito: Boolean? = null,
    val comodidades: List<String>? = null, val caracteristicas: List<String>? = null, val servicios: List<String>? = null,
    val fotosUrl: List<String>? = null,
) {
    val nombre: String get() = titulo ?: direccion ?: "Propiedad"
    val portada: String? get() = fotosUrl?.firstOrNull()
    val operacionTexto: String get() = operacion ?: ""
    val tipoTexto: String get() = tipo ?: ""
    val precioTexto: String get() = ""
}

@Composable fun AltaPropiedad(editar: String? = null, borrador: String? = null, enPaso: String? = null) {}
@Composable fun FichaPropiedad(id: String, arreglarAlAbrir: Boolean = false, compartirAlAbrir: Boolean = false, compartirCon: String? = null) {}
@Composable fun Galeria(id: String, indice: Int = 0) {}
@Composable fun PantallaMercadoLibre() {}
@Composable fun PantallaReserva(id: String) {}
@Composable fun PropiedadesRaiz(inicial: FiltrosCartera? = null, titulo: String? = null) {}
@Composable fun TarjetaVisita(id: String) {}
