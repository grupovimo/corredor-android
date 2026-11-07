package ar.com.corredor.app.pantallas.temporada

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.BotonBarraIcono
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.nonEmpty
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.pantallas.propiedades.ErrorConCampos
import ar.com.corredor.app.pantallas.propiedades.conCampos
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

// Clon de Pantallas/Temporada/Tarifas.swift.

// MARK: - Tarifas de temporada

@Serializable
data class TarifaAPI(
    val id: String,
    val nombre: String? = null,
    val desde: String,
    val hasta: String,
    val precio: Double,
    val moneda: String? = null,
    val periodo: String? = null,
    val nochesMinimas: Int? = null,
) {
    companion object {
        /** Los que acepta la API (sin período, la API pone «temporada»). */
        val periodos = listOf("noche" to "Noche", "semana" to "Semana", "quincena" to "Quincena", "mes" to "Mes", "temporada" to "Temporada")
    }
}

/** «yyyy-MM-dd» → la fecha (Fecha.dia en iOS), o hoy. */
internal fun diaTemporada(s: String): LocalDate = runCatching { LocalDate.parse(s.take(10)) }.getOrElse { Formato.hoy() }

@Serializable
private data class CuerpoTarifa(val nombre: String?, val desde: String, val hasta: String, val precio: Double, val moneda: String, val periodo: String, val nochesMinimas: Int?)

/** Tarifas de temporada de una propiedad: por rango de fechas, con noches mínimas. */
@Composable
fun TarifasTemporada(propiedad: String) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<TarifaAPI>>(emptyList()) }
    var editando by remember { mutableStateOf<TarifaAPI?>(null) }

    suspend fun cargar() { lista = estado.api.opcional<List<TarifaAPI>>("/v1/propiedades/$propiedad/tarifas") ?: emptyList() }

    suspend fun guardar(t: TarifaAPI) {
        val c = CuerpoTarifa(t.nombre?.nonEmpty, t.desde, t.hasta, t.precio, t.moneda ?: "USD", t.periodo ?: "noche", t.nochesMinimas)
        try {
            if (t.id.isEmpty()) estado.api.hacer("POST", "/v1/propiedades/$propiedad/tarifas", c) else estado.api.hacer("PUT", "/v1/propiedades/$propiedad/tarifas/${t.id}", c)
            cargar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Tarifas", acciones = if (estado.escribe) ({
        BotonBarraIcono("plus", "Sumar") {
            val hoy = Formato.hoy()
            editando = TarifaAPI(id = "", desde = hoy.toString(), hasta = hoy.plusDays(30).toString(), precio = 0.0, moneda = "USD", periodo = "noche")
        }
    }) else null) {
        // La List .insetGrouped de iOS: la tarjeta con 16 de margen a los costados (Bloque no lo trae).
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Bloque {
                if (lista.isEmpty()) Box(Modifier.padding(16.dp)) { Texto("Cargá la tarifa de cada temporada (alta, baja, fines de semana largos).", Estilo.body, C.texto2) }
                lista.forEachIndexed { i, t ->
                    key(t.id) {
                        if (i > 0) Separador()
                        FilaDeslizable(derecha = if (estado.escribe) listOf(AccionFila("Borrar", color = C.error) {
                            alcance.launch { borrarTarifa(t, propiedad, estado); cargar() }
                        }) else emptyList()) {
                            Tocable({ editando = t }) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Texto(t.nombre ?: "Tarifa", Estilo.filaLista, C.texto)
                                        // Las fechas como fechas («15/12/2026»), no como las manda la API («2026-12-15»).
                                        val f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")
                                        Texto("${diaTemporada(t.desde).format(f)} al ${diaTemporada(t.hasta).format(f)}", Estilo.footnote, C.texto2)
                                        t.nochesMinimas?.let { Texto("mínimo $it noches", Estilo.footnote, C.texto2) }
                                    }
                                    Texto("${Formato.plata(t.precio, t.moneda ?: "USD")}\u00A0/ ${TarifaAPI.periodos.firstOrNull { it.first == (t.periodo ?: "temporada") }?.second?.lowercase() ?: "temporada"}",
                                        Estilo.body.con(Peso.semibold), C.texto, Modifier.padding(start = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Hoja(editando != null, { editando = null }) {
        editando?.let { t -> key(t.id, t.desde) { FormTarifa(t) { x -> alcance.launch { guardar(x) } } } }
    }
}

@Composable
fun FormTarifa(t0: TarifaAPI, guardar: (TarifaAPI) -> Unit) {
    val cerrar = LocalCerrarHoja.current
    var t by remember { mutableStateOf(t0) }
    MarcoHoja("", confirmar = "Guardar", confirmarHabilitado = !(t.precio <= 0 || (t.nombre ?: "").nonEmpty == null), alConfirmar = { guardar(t); cerrar() }) {
        Bloque {
            RenglonCampo("", t.nombre ?: "", { t = t.copy(nombre = it) }, "Nombre (temporada alta…)")
            Separador()
            RenglonFecha("Desde", diaTemporada(t.desde), { t = t.copy(desde = it.toString()) })
            Separador()
            RenglonFecha("Hasta", diaTemporada(t.hasta), { t = t.copy(hasta = it.toString()) })
            Separador()
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), t.moneda ?: "USD", { t = t.copy(moneda = it) }, descripcion = "Moneda")
            }
            Separador()
            RenglonPesos("Precio", t.precio.takeIf { it != 0.0 }, { t = t.copy(precio = it ?: 0.0) }, moneda = "")
            Separador()
            RenglonSelector("Por", TarifaAPI.periodos, t.periodo ?: "noche", { t = t.copy(periodo = it) })
            Separador()
            Renglon("Mínimo ${t.nochesMinimas ?: 1} noches") { Pasador(t.nochesMinimas ?: 1, { t = t.copy(nochesMinimas = it) }, 1..120) }
        }
    }
}

/** Lo que va en POST …/tarifas (`HojaTarifasTemporada.Cuerpo` en iOS). */
@Serializable
data class CuerpoTemporada(val nombre: String, val desde: String, val hasta: String, val precio: Double, val moneda: String, val periodo: String, val nochesMinimas: Int?)

/** Borrar con deshacer (la vuelve a cargar igual) (`HojaTarifasTemporada.borrar` en iOS). */
suspend fun borrarTarifa(t: TarifaAPI, propiedad: String, estado: Estado) {
    try {
        estado.api.hacer("DELETE", "/v1/propiedades/$propiedad/tarifas/${t.id}")
        estado.avisar("Tarifa ${t.nombre ?: ""} borrada") {
            estado.tarea {
                try {
                    estado.api.hacer("POST", "/v1/propiedades/$propiedad/tarifas",
                        CuerpoTemporada(t.nombre ?: "Tarifa", t.desde, t.hasta, t.precio, t.moneda ?: "USD", t.periodo ?: "temporada", t.nochesMinimas))
                    estado.version += 1
                } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            }
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
}

/** Tarifas por temporada (15E): las de todas las casas, con borrar (y deshacer), y sumar una temporada a una casa o a
 *  todas a la vez. El precio de cada noche sale de la temporada en la que cae. */
@Composable
fun HojaTarifasTemporada(casas: List<CasaTemporada>) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var tarifas by remember { mutableStateOf<Map<String, List<TarifaAPI>>>(emptyMap()) }
    var casa by remember { mutableStateOf("todas") }
    var nombre by remember { mutableStateOf("Alta") }
    var desde by remember { mutableStateOf(Formato.hoy()) }
    var hasta by remember { mutableStateOf(Formato.hoy().plusDays(14)) }
    var precio by remember { mutableStateOf<Double?>(null) }
    var moneda by remember { mutableStateOf("USD") }
    var periodo by remember { mutableStateOf("noche") }
    var minimo by remember { mutableStateOf<Int?>(null) }
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var enviando by remember { mutableStateOf(false) }

    suspend fun recargar(id: String) {
        tarifas = tarifas + (id to (estado.api.opcional<List<TarifaAPI>>("/v1/propiedades/$id/tarifas") ?: emptyList()))
    }

    suspend fun sumar() {
        val p = precio ?: return
        val destino = if (casa == "todas") casas else casas.filter { it.id == casa }
        val c = CuerpoTemporada(nombre.trim(), desde.toString(), hasta.toString(), p, moneda, periodo, minimo)
        enviando = true
        errores = emptyMap()
        try {
            for (x in destino) {
                estado.api.conCampos("POST", "/v1/propiedades/${x.id}/tarifas", c, envio = UUID.randomUUID().toString())
                recargar(x.id)
            }
            estado.avisar("Temporada ${c.nombre} cargada en ${if (destino.size == 1) destino[0].direccion else "${destino.size} casas"}")
            precio = null
            minimo = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorConCampos) {
            errores = e.campos
        } catch (e: Exception) { estado.avisar(e) } finally { enviando = false }
    }

    MarcoHoja("Tarifas por temporada", cancelar = null, confirmar = "Listo", alConfirmar = { estado.version += 1; cerrar() }) {
        Bloque {
            Box(Modifier.padding(16.dp)) {
                Texto("El precio de cada noche sale de la temporada en la que cae. La última cargada manda si se pisan.", Estilo.footnote, C.texto2)
            }
        }
        casas.forEach { c ->
            key(c.id) {
                Bloque(c.direccion) {
                    val ts = tarifas[c.id] ?: c.tarifas
                    if (ts.isEmpty()) Box(Modifier.padding(16.dp)) { Texto("Sin tarifas: se cobra el precio por noche de la ficha.", Estilo.footnote, C.texto2) }
                    ts.forEachIndexed { i, t ->
                        key(t.id) {
                            if (i > 0) Separador()
                            FilaDeslizable(derecha = if (estado.escribe) listOf(AccionFila("Borrar", color = C.error) {
                                alcance.launch { borrarTarifa(t, c.id, estado); recargar(c.id) }
                            }) else emptyList()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Texto(t.nombre ?: "Tarifa", Estilo.filaLista, C.texto)
                                        Texto("${TempFechas.corto(t.desde)} al ${TempFechas.corto(t.hasta)}", Estilo.footnote, C.texto2)
                                    }
                                    Texto("${Formato.plata(t.precio, t.moneda ?: "USD")} / ${t.periodo ?: "temporada"}${t.nochesMinimas?.let { " · mín. $it" } ?: ""}", Estilo.footnote, C.texto)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (estado.escribe) {
            Bloque("Sumar una temporada") {
                RenglonSelector("Casa", listOf("todas" to "Todas las casas") + casas.map { it.id to it.direccion }, casa, { casa = it })
                Separador()
                RenglonCampo("", nombre, { nombre = it }, "Alta, Media, Baja")
                errores["nombre"]?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { MensajeError(it) } }
                Separador()
                RenglonFecha("Desde", desde, { desde = it; if (hasta.isBefore(it)) hasta = it })
                Separador()
                RenglonFecha("Hasta", hasta, { hasta = it }, minimo = desde)
                (errores["desde"] ?: errores["hasta"])?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { MensajeError(it) } }
                Separador()
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), moneda, { moneda = it }, descripcion = "Moneda") }
                Separador()
                RenglonPesos("Precio", precio, { precio = it }, moneda = "")
                errores["precio"]?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { MensajeError(it) } }
                Separador()
                RenglonSelector("Por", listOf("noche", "semana", "quincena", "mes").map { it to it }, periodo, { periodo = it })
                Separador()
                Renglon(minimo?.let { "Mín. $it noches" } ?: "Mín. noches (opcional)") { Pasador(minimo ?: 0, { minimo = if (it == 0) null else it }, 0..120) }
                Separador()
                RenglonBoton(if (enviando) "Guardando…" else "Sumar temporada", habilitado = !(enviando || precio == null || nombre.nonEmpty == null)) { alcance.launch { sumar() } }
            }
        }
    }
}
