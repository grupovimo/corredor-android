package ar.com.corredor.app.pantallas.cobranzas
// PROVISORIO: firmas de las pantallas que escriben los agentes de pantallas, sólo para que el núcleo compile solo.
// Se borra la carpeta src/provisorio entera (y su línea en app/build.gradle.kts) cuando entran las pantallas de verdad.

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable data class ARegistrarCobro(val contrato: String? = null)
@Serializable data class ARecordarMes(val contrato: String, val periodo: String)
@Serializable data class AContratosFiltro(val vista: String? = null, val indice: String? = null)
@Serializable data class AAltaContrato(val borrador: String? = null, val renovar: String? = null, val leer: Boolean = false)
@Serializable data class ARendicionVista(val contrato: String, val id: String)
data class ARecorrida(val id: String, val tipo: String)
data class ADevolucion(val id: String)

@Serializable data class Cobro(
    val id: String = "", val periodo: String, val monto: Double, val moneda: String, val pagadoEl: String? = null,
    val medio: String? = null, val numero: Int? = null, val conceptos: List<Concepto>? = null,
) { @Serializable data class Concepto(val concepto: String, val monto: Double) }

@Serializable data class ContratoAPI(
    val id: String, val tipo: String? = null, val inicio: String? = null, val fin: String? = null, val monto: Double? = null,
    val moneda: String? = null, val comision: Double? = null, val deposito: Double? = null, val diaVencimiento: Int? = null,
    val garanteNombre: String? = null, val garanteTelefono: String? = null, val garanteNotas: String? = null, val notas: String? = null,
    val honorarios: Double? = null, val punitorioDiario: Double? = null, val propiedadTitulo: String? = null,
    val contactoNombre: String? = null, val propietarioNombre: String? = null,
) { val ajusteTexto: String get() = "" }

@Serializable data class RendicionAPI(val id: String = "", val periodo: String = "", val items: List<Item>? = null) {
    @Serializable data class Item(val descripcion: String, val monto: Double)
    companion object { fun numeroTexto(n: Int?): String? = n?.let { String.format("%04d", it) } }
}

@Serializable data class ComprobanteRendicion(
    val inmobiliaria: Inmo? = null, val periodo: String, val moneda: String, val propiedadTitulo: String? = null,
    val propiedadDireccion: String? = null, val propietario: String? = null, val cobrado: Double, val comisionPorcentaje: Double? = null,
    val comision: Double? = null, val items: List<RendicionAPI.Item>? = null, val neto: Double, val estado: String, val notas: String? = null,
    val proximo: Proximo? = null, val numero: Int? = null,
) {
    @Serializable data class Inmo(val nombre: String)
    @Serializable data class Proximo(val indice: String, val fecha: String, val monto: Double? = null, val estimado: Boolean? = null)
}

object TextosCobro {
    val nombreIndice: Map<String, String> = emptyMap()
    fun nombreMes(periodo: String): String = periodo
    fun fechaCortita(f: String, anio: Boolean = false): String = f
    fun numero(n: Double): String = n.toString()
}

object ReciboVista {
    fun medioTexto(m: String): String = m
    fun mes(p: String): String = p
}

@Composable fun ReciboVista(contrato: String, cobro: String, enHoja: Boolean = false) {}
@Composable fun AltaContrato(leer: Boolean = false, renovar: String? = null, operacion: String? = null, borrador: String? = null, apilada: Boolean = false) {}
@Composable fun AltaContratoPantalla(destino: AAltaContrato) {}
@Composable fun Cobranzas(registrar: Boolean = false, contrato: String? = null) {}
@Composable fun Contratos(vista: String? = null, indice: String? = null) {}
@Composable fun FichaContrato(id: String, recordar: String? = null) {}
@Composable fun RendicionVista(contrato: String, id: String, verComoPropietario: Boolean = false) {}
@Composable fun Inventario(propiedad: String, tipoPedido: String? = null) {}
@Composable fun Firmas(clave: String) {}
@Composable fun Devolucion(propiedad: String) {}
