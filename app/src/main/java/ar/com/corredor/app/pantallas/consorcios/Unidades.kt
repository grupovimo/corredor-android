package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.nucleo.copiar
import ar.com.corredor.app.nucleo.Fecha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

// Clon de Pantallas/Consorcios/Unidades.swift.

// MARK: - Unidades

/** Una unidad para editar (o null: la alta). */
data class EdicionUnidad(val u: UnidadAPI?) {
    val id: String get() = u?.id ?: "nueva"
}

/** Unidades del edificio: coeficiente, propietario (y si le llega por mail) y saldo. Tocar una abre su cuenta
 *  corriente: los movimientos, registrar un pago (con deshacer), el link público y editarla. */
@Composable
fun PestanaUnidadesConsorcio(c: ConsorcioAPI, pedido: String?, alCambiarPedido: (String?) -> Unit) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var unidades by remember { mutableStateOf<List<UnidadAPI>?>(null) }
    var deudores by remember { mutableStateOf<List<DeudorAPI>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var soloDeben by remember { mutableStateOf(false) }
    var cuenta by remember { mutableStateOf<UnidadAPI?>(null) }
    var editando by remember { mutableStateOf<EdicionUnidad?>(null) }
    var intimar by remember { mutableStateOf(false) }

    suspend fun cargar() {
        val b = "/v1/consorcios/${c.id}"
        try {
            val u: List<UnidadAPI> = estado.api.get("$b/unidades")
            deudores = estado.api.opcional<List<DeudorAPI>>("$b/deudores") ?: emptyList()
            unidades = u
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && unidades == null) error = e
        }
    }

    val morosos: List<MorosoConsorcio> = run {
        val u = unidades ?: emptyList()
        deudores.map { d -> MorosoConsorcio(d.unidadId, d.unidad, d.propietario, u.firstOrNull { it.id == d.unidadId }?.propietarioTelefono, d.total) }
    }

    /** Lo que pidió el Resumen o la lista: sólo las que deben, la alta, o la cuenta de una unidad. */
    fun atender(p: String?) {
        if (p == null) return
        if (p == "deben") { soloDeben = true; alCambiarPedido(null) }
        else if (p == "nueva") { if (estado.escribe) editando = EdicionUnidad(null); alCambiarPedido(null) }
        else if (p.startsWith("unidad:")) {
            val id = p.drop(7)
            val u = unidades?.firstOrNull { it.id == id } ?: return
            cuenta = u
            alCambiarPedido(null)
        }
    }

    LaunchedEffect(estado.version) { cargar() }
    LaunchedEffect(pedido, unidades) { atender(pedido) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val us = unidades
        val e = error
        if (us != null) {
            // Barra
            FilaPartible({
                ChipFiltro("Todas ${us.size}", !soloDeben) { soloDeben = false }
                ChipFiltro("Deben ${deudores.size}", soloDeben) { soloDeben = true }
            }, {
                if (deudores.isNotEmpty()) BotonBordeadoConsorcio("Intimar", "hammer") { intimar = true }
                if (estado.escribe) BotonBordeadoConsorcio("Nueva unidad", "plus") { editando = EdicionUnidad(null) }
            })
            if (us.isEmpty()) {
                EstadoVacio("door.left.hand.open", "Cargá las unidades del edificio",
                    "Con su propietario y el coeficiente del reglamento. Con eso, Corredor reparte los gastos de cada mes al centavo.",
                    accion = if (estado.escribe) ("Cargar la primera unidad" to { editando = EdicionUnidad(null) }) else null)
            } else {
                // Tabla
                val deuda = deudores.associateBy { it.unidadId }
                val l = us.sortedWith { a, b -> Cons.ordenUnidad.compare(a.etiqueta, b.etiqueta) }.filter { !soloDeben || deuda[it.id] != null }
                val general = c.general
                Column(Modifier.fillMaxWidth().tarjeta()) {
                    if (l.isEmpty()) VacioConsorcio("Nadie debe: todas las unidades están al día.")
                    l.forEachIndexed { i, u ->
                        key(u.id) {
                            Tocable({ cuenta = u }) {
                                RenglonConsorcio(primero = i == 0) {
                                    Texto(u.etiqueta, Estilo.filaLista, C.texto, Modifier.widthIn(min = 44.dp))
                                    DosLineasConsorcio(u.propietario ?: "Sin propietario",
                                        if (u.propietarioEmail != null) "le llega por mail" else if (u.propietario != null) "sin mail · va impresa" else "cargá quién es el dueño")
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        val d = deuda[u.id]
                                        if (d != null) {
                                            Texto(Cons.cifra(d.total), Estilo.footnoteFuerte, C.errorTinta)
                                        } else if ((u.saldo ?: 0.0) < 0) {
                                            Texto("a favor ${Cons.cifra(-(u.saldo ?: 0.0))}", Estilo.footnoteFuerte, C.okTinta)
                                        } else {
                                            Texto("al día", Estilo.footnoteFuerte, C.texto2)
                                        }
                                        Texto(general?.let { Cons.coeficiente(u.coeficientes?.get(it.id) ?: 0.0) } ?: "—", Estilo.footnote, C.texto3)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (e != null) {
            EstadoError("las unidades", e) { alcance.launch { cargar() } }
        } else EsqueletoFilas(filas = 5)
    }
    Hoja(cuenta != null, { cuenta = null }) {
        cuenta?.let { u -> HojaCuentaUnidad(c, u) { editando = EdicionUnidad(u) } }
    }
    Hoja(editando != null, { editando = null }) {
        editando?.let { e -> key(e.id) { HojaUnidadConsorcio(c, e.u) } }
    }
    Hoja(intimar, { intimar = false }) { HojaIntimarMorosos(morosos, c.id, c.nombre) }
}

// MARK: - Cuenta corriente

/** La cuenta de una unidad: «Saldo hoy» con el interés por mora y los movimientos (pago, expensas, interés, ajuste);
 *  registrar un pago, copiar el link público o editar la unidad. */
@Composable
fun HojaCuentaUnidad(c: ConsorcioAPI, u: UnidadAPI, editar: () -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var cuenta by remember { mutableStateOf<CuentaUnidadAPI?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var pagando by remember { mutableStateOf(false) }

    suspend fun cargar() {
        try {
            cuenta = estado.api.get("/v1/consorcios/${c.id}/unidades/${u.id}/cuenta")
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && cuenta == null) error = e
        }
    }

    suspend fun copiarLink() {
        try {
            val l: LinkConsorcio = estado.api.enviar("POST", "/v1/consorcios/${c.id}/unidades/${u.id}/link", vacioConsorcio)
            copiar(ctx, l.url)
            estado.avisar("Link de la cuenta de ${u.etiqueta} copiado. Pegalo donde quieras mandarlo.")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version) { cargar() }

    MarcoHoja(
        "Cuenta de ${u.etiqueta}", cancelar = "Cerrar",
        abajo = if (estado.escribe) ({
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { BotonHundido("Editar", { cerrar(); estado.tarea { kotlinx.coroutines.delay(450); editar() } }, simbolo = "pencil") }
                Box(Modifier.weight(1f)) { BotonHundido("Copiar link", { alcance.launch { copiarLink() } }, simbolo = "link") }
            }
            BotonAccion("Registrar pago", { pagando = true }, simbolo = "dollarsign.circle")
        }) else null,
    ) {
        Texto(u.propietario ?: "Sin propietario", Estilo.texto14, C.texto2)
        val k = cuenta
        val e = error
        if (k != null) {
            val interes = k.interesEstimado ?: 0.0
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (k.saldo > 0) C.errorFondo else C.hundida).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Texto("Saldo hoy", Estilo.footnote, C.texto2)
                Texto(Formato.plata(k.saldo + interes), Estilo(28f, Peso.bold, -0.02f, Relativo.title), if (k.saldo > 0) C.errorTinta else C.texto)
                if (interes > 0) Texto("Incluye ${Formato.plata(interes)} de interés por mora.", Estilo.footnote, C.texto2)
            }
            Column(Modifier.fillMaxWidth().tarjeta()) {
                val ms = (k.movimientos ?: emptyList()).take(12)
                if (ms.isEmpty()) VacioConsorcio("Sin movimientos todavía.")
                ms.forEachIndexed { i, m ->
                    key(m.id) {
                        val nombre = MovimientoUnidad.nombres[m.tipo] ?: m.tipo
                        RenglonConsorcio(primero = i == 0) {
                            DosLineasConsorcio(m.notas.noVacio() ?: nombre, "$nombre · ${Cons.fechaPapel(m.fecha)}")
                            Texto((if (m.signo < 0) "−" else "") + Cons.cifra(kotlin.math.abs(m.signo)), Estilo.footnoteFuerte, if (m.tipo == "pago") C.okTinta else C.texto)
                        }
                    }
                }
            }
        } else if (e != null) {
            EstadoError("la cuenta", e) { alcance.launch { cargar() } }
        } else EsqueletoFilas(filas = 4)
    }
    Hoja(pagando, { pagando = false }, listOf(Altura.media, Altura.grande)) { HojaPagoUnidad(c, u, cuenta?.saldo ?: 0.0) }
}

@Serializable
private data class CuerpoPago(val tipo: String = "pago", val monto: Double, val fecha: String, val notas: String?)

/** Registrar un pago en la cuenta de la unidad: lo que pagó, la fecha y una nota. Con deshacer. */
@Composable
fun HojaPagoUnidad(c: ConsorcioAPI, u: UnidadAPI, saldo: Double) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    val hoy = LocalDate.now(Formato.zona)
    var monto by remember { mutableStateOf<Double?>(if (saldo > 0) saldo else null) }
    var fecha by remember { mutableStateOf(hoy) }
    var notas by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    suspend fun pagar() {
        guardando = true
        val ruta = "/v1/consorcios/${c.id}/unidades/${u.id}/movimientos"
        try {
            val m: MovimientoUnidad = estado.api.enviar("POST", ruta, CuerpoPago(monto = Cons.redondo(monto ?: 0.0), fecha = fecha.toString(), notas = notas.take(200).noVacio()))
            Haptico.exito()
            estado.version += 1
            cerrar()
            val api = estado.api
            estado.avisar("Pago de ${u.etiqueta} · ${Formato.plata(m.monto)}") {
                estado.tarea {
                    try {
                        api.hacer("DELETE", "$ruta/${m.id}")
                        estado.version += 1
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja(
        "Pago de ${u.etiqueta}", confirmar = "Registrar pago", confirmarHabilitado = (monto ?: 0.0) > 0 && !guardando,
        alConfirmar = { alcance.launch { pagar() } },
    ) {
        SeccionConsorcio {
            CampoConsorcio("Pagó") { CampoNumeroConsorcio(monto, { monto = it }, "Monto") }
            monto?.let { m -> if (m <= 0) Texto("Poné el monto que pagó.", Estilo.footnote, C.error) }
            RenglonFechaConsorcio("Fecha", fecha, { fecha = it }, maximo = hoy)
            CampoConsorcio("Nota (opcional)") { CampoTextoConsorcio(notas, { notas = it }, "Transferencia, efectivo en la administración…") }
        }
    }
}

// MARK: - Alta y edición

@Serializable
private data class NuevoContactoUnidad(val nombre: String, val email: String?, val telefono: String?, val tipo: String = "propietario")

@Serializable
private data class DatosUnidad(
    val etiqueta: String, val piso: String?, val tipo: String, val propietarioId: String?, val inquilinoId: String?,
    val notas: String?, val expensaFija: Double, val coeficientes: Map<String, Double>,
)

/** Alta o edición de una unidad: etiqueta, tipo, propietario (elegido o uno nuevo con mail y celular) y el
 *  coeficiente de cada columna del reglamento. */
@Composable
fun HojaUnidadConsorcio(c: ConsorcioAPI, u: UnidadAPI?) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var etiqueta by remember { mutableStateOf(u?.etiqueta ?: "") }
    var tipo by remember { mutableStateOf(u?.tipo ?: "departamento") }
    var propietario by remember { mutableStateOf(u?.propietarioId ?: "") }
    var nuevoNombre by remember { mutableStateOf("") }
    var nuevoEmail by remember { mutableStateOf("") }
    var nuevoTelefono by remember { mutableStateOf("") }
    var coef by remember { mutableStateOf(u?.coeficientes ?: emptyMap()) }
    var propietarios by remember { mutableStateOf<List<Prop>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var l = runCatching { todasConsorcio<Prop>(estado.api, "/v1/contactos", listOf("tipo" to "propietario")) { it.id } }.getOrElse {
            if (it is CancellationException) throw it
            emptyList()
        }
        // El propietario de la unidad, aunque no esté cargado como tipo propietario.
        val pid = u?.propietarioId
        if (u != null && pid != null && l.none { it.id == pid }) l = listOf(Prop(pid, u.propietario ?: "Propietario")) + l
        propietarios = l
    }

    suspend fun guardar() {
        guardando = true
        try {
            var pid: String? = propietario.ifEmpty { null }
            if (propietario == "nuevo") {
                val n: Prop = estado.api.enviar("POST", "/v1/contactos", NuevoContactoUnidad(nuevoNombre.trim(), nuevoEmail.noVacio(), nuevoTelefono.noVacio()))
                pid = n.id
                propietario = n.id
                propietarios = propietarios + n
            }
            val cols = (c.columnas ?: emptyList()).map { it.id }.toSet()
            val d = DatosUnidad(etiqueta.trim(), u?.piso, tipo, pid, u?.inquilinoId, u?.notas, Cons.redondo(u?.expensaFija ?: 0.0), coef.filterKeys { it in cols })
            if (u != null) {
                estado.api.hacer("PUT", "/v1/consorcios/${c.id}/unidades/${u.id}", d)
                estado.avisar("Unidad guardada")
            } else {
                estado.api.hacer("POST", "/v1/consorcios/${c.id}/unidades", d)
                estado.avisar("Unidad cargada")
            }
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja(
        u?.let { "Editar ${it.etiqueta}" } ?: "Nueva unidad", confirmar = "Guardar",
        confirmarHabilitado = !(etiqueta.trim().isEmpty() || (propietario == "nuevo" && nuevoNombre.trim().isEmpty()) || guardando),
        alConfirmar = { alcance.launch { guardar() } },
    ) {
        SeccionConsorcio {
            CampoConsorcio("Unidad") { CampoTextoConsorcio(etiqueta, { etiqueta = it }, "3.º A") }
            RenglonSelectorConsorcio("Es", UnidadAPI.tipos, tipo) { tipo = it }
        }
        SeccionConsorcio {
            RenglonSelectorConsorcio("Propietario", listOf("" to "Sin cargar") + propietarios.map { it.id to it.nombre } + listOf("nuevo" to "Otro (cargarlo)…"), propietario) { propietario = it }
            if (propietario == "nuevo") {
                CampoConsorcio("Nombre del propietario") { CampoTextoConsorcio(nuevoNombre, { nuevoNombre = it }) }
                CampoConsorcio("Mail (opcional)", ayuda = "Para mandarle la liquidación.") {
                    CampoTextoConsorcio(nuevoEmail, { nuevoEmail = it }, teclado = KeyboardType.Email, sinCorrector = true)
                }
                CampoConsorcio("Celular (opcional)") { CampoTextoConsorcio(nuevoTelefono, { nuevoTelefono = it }, teclado = KeyboardType.Phone) }
            }
        }
        // Un `Section` sin filas no se ve en iOS: sin columnas, no va la tarjeta vacía.
        if (!c.columnas.isNullOrEmpty()) SeccionConsorcio {
            (c.columnas ?: emptyList()).forEach { col ->
                key(col.id) {
                    CampoConsorcio(if ((c.columnas?.size ?: 0) > 1) "Coeficiente · ${col.nombre} (%)" else "Coeficiente del reglamento (%)") {
                        CampoNumeroConsorcio(coef[col.id], { v -> coef = if (v == null) coef - col.id else coef + (col.id to v) }, "4,12")
                    }
                }
            }
        }
    }
}

/** Un propietario para elegir. */
@Serializable
data class Prop(val id: String, val nombre: String)
