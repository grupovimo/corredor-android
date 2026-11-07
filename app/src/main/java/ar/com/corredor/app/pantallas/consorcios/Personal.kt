package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.nucleo.copiar
import ar.com.corredor.app.nucleo.Fecha

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.ArchivoPrevio
import ar.com.corredor.app.nucleo.Documentos
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.VistaPrevia
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

// Clon de Pantallas/Consorcios/Personal.swift.

// MARK: - Personal

data class LiquidarPara(val empleado: String) { val id: String get() = empleado }
data class EdicionEmpleado(val e: EmpleadoAPI?) { val id: String get() = e?.id ?: "nuevo" }

/** Personal del edificio: cada empleado con su categoría, antigüedad y recibos; liquidar el sueldo del mes (con
 *  deshacer), el recibo (A4 y link público), la escala vigente y el F931. */
@Composable
fun PestanaPersonalConsorcio(c: ConsorcioAPI, empleados: List<EmpleadoAPI>, pedido: String?, alCambiarPedido: (String?) -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var recibos by remember { mutableStateOf<List<ReciboSueldoAPI>?>(null) }
    var escalas by remember { mutableStateOf<List<EscalaAPI>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var recibo by remember { mutableStateOf<ReciboSueldoAPI?>(null) }
    var liquidar by remember { mutableStateOf<LiquidarPara?>(null) }
    var empleado by remember { mutableStateOf<EdicionEmpleado?>(null) }
    var escala by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    val ultimoMes: String? = recibos?.firstOrNull()?.periodo
    val mesALiquidar: String = ultimoMes?.let { Cons.mesSiguiente(it) } ?: Cons.hoy.take(7)

    suspend fun cargar() {
        try {
            val r: List<ReciboSueldoAPI> = estado.api.get("/v1/consorcios/${c.id}/sueldos", listOf("limite" to "60"))
            escalas = estado.api.opcional<List<EscalaAPI>>("/v1/sueldos/escalas") ?: emptyList()
            recibos = r
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && recibos == null) error = e
        }
    }

    fun atender(p: String?) {
        if (p == null) return
        if (p == "liquidar") {
            val e = empleados.firstOrNull()
            if (estado.escribe && e != null) liquidar = LiquidarPara(e.id)
            alCambiarPedido(null)
        } else if (p.startsWith("recibo:")) {
            val r = recibos?.firstOrNull { it.id == p.drop(7) } ?: return
            recibo = r
            alCambiarPedido(null)
        }
    }

    /** El F931 del mes del último recibo, como CSV para Excel. */
    suspend fun f931() {
        try {
            val ruta = "/v1/consorcios/${c.id}/sueldos/f931" + (ultimoMes?.let { "?periodo=$it" } ?: "")
            val f = Documentos.archivo(ruta, ultimoMes?.let { "sueldos-$it.csv" } ?: "sueldos-todos.csv", estado.api)
            previo = ArchivoPrevio(f, "text/csv")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version) { cargar() }
    LaunchedEffect(pedido, recibos) { atender(pedido) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val rs = recibos
        val e = error
        if (rs != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(ultimoMes?.let { "Último liquidado: ${Cons.nombreMes(it)}" } ?: "Todavía sin recibos", Estilo.footnote, C.texto2, Modifier.weight(1f))
                BotonBordeadoConsorcio("Exportar F931", "square.and.arrow.down") { alcance.launch { f931() } }
            }
            if (estado.escribe) BotonHundido("Sumar empleado", { empleado = EdicionEmpleado(null) }, simbolo = "person.badge.plus")
            if (empleados.isEmpty()) {
                BloqueConsorcio(null) {
                    VacioConsorcio("Sin personal cargado. Sumá al encargado con su categoría y fecha de ingreso: Corredor calcula la antigüedad, las retenciones y arma el recibo y el F931.")
                }
            }
            empleados.forEach { emp ->
                key(emp.id) {
                    val suyos = rs.filter { it.empleadoId == emp.id }
                    BloqueConsorcio(null) {
                        RenglonConsorcio(primero = true) {
                            AvatarIniciales(emp.nombre, tam = 40.dp)
                            DosLineasConsorcio(emp.nombre + (emp.categoria?.let { " · " + it.lowercase() } ?: ""),
                                listOfNotNull(emp.documento?.let { "CUIL $it" }, emp.notas, Cons.antiguedad(emp.ingreso)?.let { "$it años" },
                                    emp.basico?.let { if (it > 0) "básico ${Formato.plata(it)}" else null }).joinToString(" · "))
                            if (estado.escribe) {
                                Box(Modifier.size(36.dp).clickable { empleado = EdicionEmpleado(emp) }.semantics { contentDescription = "Editar a ${emp.nombre}" },
                                    contentAlignment = Alignment.Center) { Icono("pencil", 17f, C.accion) }
                            }
                        }
                        suyos.take(6).forEach { r ->
                            key(r.id) {
                                Tocable({ recibo = r }) {
                                    RenglonConsorcio {
                                        Icono("doc.text", 17f, C.okTinta)
                                        DosLineasConsorcio("Recibo de ${Cons.nombreMes(r.periodo)}${if (r.periodo.take(4) != Cons.hoy.take(4)) " ${r.periodo.take(4)}" else ""}",
                                            "bruto ${Cons.cifra(r.bruto ?: 0.0)} · retenciones ${Cons.cifra(r.deduccionesTotal ?: 0.0)}")
                                        Texto(Cons.cifra(r.neto ?: 0.0), Estilo.footnoteFuerte, C.texto)
                                    }
                                }
                            }
                        }
                        if (estado.escribe) {
                            RenglonConsorcio {
                                BotonLabelConsorcio("Liquidar ${Cons.nombreMes(suyos.firstOrNull()?.let { Cons.mesSiguiente(it.periodo) } ?: mesALiquidar)}", "plus") {
                                    liquidar = LiquidarPara(emp.id)
                                }
                            }
                        }
                    }
                }
            }
            // Escala
            BloqueConsorcio("Escala de sueldos", accion = if (estado.esDuenio) ("Sumar categoría" to { escala = true }) else null) {
                if (escalas.isEmpty()) VacioConsorcio("Cargá los básicos de cada categoría (SUTERH): al sumar un empleado, toma el de su categoría.")
                escalas.forEachIndexed { i, s ->
                    key(s.id) {
                        RenglonConsorcio(primero = i == 0) {
                            DosLineasConsorcio(s.categoria, listOfNotNull(s.notas, s.vigenteDesde?.let { "desde el ${Cons.fechaPapel(it)}" }).joinToString(" · "))
                            Texto(Cons.cifra(s.basico), Estilo.footnoteFuerte, C.texto)
                        }
                    }
                }
            }
        } else if (e != null) {
            EstadoError("el personal", e) { alcance.launch { cargar() } }
        } else EsqueletoFilas(filas = 4)
    }
    Hoja(recibo != null, { recibo = null }) { recibo?.let { r -> key(r.id) { HojaReciboSueldo(c, r) } } }
    Hoja(liquidar != null, { liquidar = null }) {
        liquidar?.let { l -> key(l.id) { HojaLiquidarSueldo(c, empleados, recibos ?: emptyList(), l.empleado, mesALiquidar) } }
    }
    Hoja(empleado != null, { empleado = null }) { empleado?.let { x -> key(x.id) { HojaEmpleadoConsorcio(c, x.e, escalas) } } }
    Hoja(escala, { escala = false }) { HojaEscalaSueldos() }
    VistaPrevia(previo) { previo = null }
}

// MARK: - Recibo

/** El recibo en pantalla: haberes (básico, antigüedad, otros), descuentos con su tasa, el neto y la provisión de
 *  aguinaldo; el A4, y el link para mandárselo al empleado. */
@Composable
fun HojaReciboSueldo(c: ConsorcioAPI, r: ReciboSueldoAPI) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var link by remember { mutableStateOf<String?>(null) }
    var armando by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    suspend fun armar() {
        armando = true
        try {
            val l: LinkConsorcio = estado.api.enviar("POST", "/v1/consorcios/${c.id}/sueldos/${r.id}/link", vacioConsorcio)
            link = l.url
            copiar(ctx, l.url)
            estado.avisar("Link del recibo de ${r.empleado} copiado.")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        armando = false
    }

    suspend fun imprimir() {
        try {
            val u = Documentos.sueldo(ctx, consorcio = c.id, recibo = r.id, api = estado.api)
            previo = ArchivoPrevio(u, "application/pdf")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    @Composable
    fun linea(a: String, b: String, d: String, cabeza: Boolean = false) {
        val estilo = if (cabeza) Estilo.footnoteFuerte else Estilo.texto14
        val color = if (cabeza) C.texto2 else C.texto
        RenglonConsorcio(primero = cabeza) {
            Texto(a, estilo, color, Modifier.weight(1f))
            Texto(b, estilo, color, Modifier.width(84.dp), alinear = TextAlign.End)
            Texto(d, estilo, color, Modifier.width(84.dp), alinear = TextAlign.End)
        }
    }

    MarcoHoja(
        "Recibo de ${Cons.nombreMes(r.periodo)}", cancelar = "Cerrar",
        abajo = {
            BotonHundido("Imprimir", { alcance.launch { imprimir() } }, simbolo = "printer")
            val l = link
            if (l != null) {
                val nombre = r.empleado.split(" ").firstOrNull { it.isNotEmpty() } ?: ""
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        BotonHundido("Copiar link", { copiar(ctx, l); estado.avisar("Link del recibo de ${r.empleado} copiado.") }, simbolo = "link")
                    }
                    Box(Modifier.weight(1f)) {
                        BotonAccion("Mandar por WhatsApp", { Afuera.whatsapp(null, "Hola $nombre, te paso tu recibo de sueldo de ${Cons.nombreMes(r.periodo)}: $l") }, simbolo = "bubble.left")
                    }
                }
            } else if (estado.escribe) {
                BotonAccion(if (armando) "Armando…" else "Armar el link", { alcance.launch { armar() } }, habilitado = !armando, simbolo = "link")
            }
        },
    ) {
        Texto(r.empleado, Estilo.texto14, C.texto2)
        Column(Modifier.fillMaxWidth().tarjeta()) {
            linea("Concepto", "Haberes", "Descuentos", cabeza = true)
            linea("Sueldo básico", Cons.cifra(r.basico ?: 0.0), "")
            if ((r.antiguedad ?: 0.0) > 0) linea("Antigüedad", Cons.cifra(r.antiguedad ?: 0.0), "")
            if ((r.otrosRemunerativos ?: 0.0) > 0) linea("Otros remunerativos", Cons.cifra(r.otrosRemunerativos ?: 0.0), "")
            (r.deducciones ?: emptyList()).forEach { d -> key(d.nombre) { linea("${d.nombre} ${Cons.coma(d.tasa)} %", "", Cons.cifra(d.monto)) } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Texto("Neto a cobrar", Estilo.subheadFuerte, C.texto, Modifier.weight(1f))
            Texto(Formato.plata(r.neto ?: 0.0), Estilo(20f, Peso.bold), C.texto)
        }
        Texto("Provisión de aguinaldo del mes: ${Formato.plata(r.sac ?: 0.0)}.", Estilo.footnote, C.texto2)
    }
    VistaPrevia(previo) { previo = null }
}

// MARK: - Liquidar el sueldo

@Serializable
private data class CuerpoSueldo(val empleadoId: String, val periodo: String, val otrosRemunerativos: Double, val notas: String?)

/** Liquidar el sueldo de un mes: el empleado, el mes (el siguiente al último recibo), otros remunerativos y una nota.
 *  Con deshacer (borra el recibo). */
@Composable
fun HojaLiquidarSueldo(c: ConsorcioAPI, empleados: List<EmpleadoAPI>, recibos: List<ReciboSueldoAPI>, inicial: String, porDefecto: String) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()

    fun mesPara(e: String): String = recibos.firstOrNull { it.empleadoId == e }?.let { Cons.mesSiguiente(it.periodo) } ?: porDefecto

    var empleado by remember { mutableStateOf(inicial) }
    var periodo by remember { mutableStateOf(mesPara(inicial)) }
    var otros by remember { mutableStateOf<Double?>(null) }
    var notas by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    /** Los meses para elegir: un año para atrás y dos para adelante del de hoy (y el que toca). */
    val meses: List<String> = run {
        val hoy = Cons.hoy.take(7)
        val l = (-12..2).map { Cons.mesSiguiente(hoy, it) }.toMutableList()
        if (periodo.isNotEmpty() && periodo !in l) l.add(periodo)
        l.sortedDescending()
    }

    suspend fun liquidar() {
        guardando = true
        val base = "/v1/consorcios/${c.id}/sueldos"
        try {
            val r: ReciboSueldoAPI = estado.api.enviar("POST", base, CuerpoSueldo(empleado, periodo, Cons.redondo(otros ?: 0.0), notas.take(200).noVacio()))
            estado.version += 1
            cerrar()
            val api = estado.api
            estado.avisar("Recibo de ${Cons.nombreMes(r.periodo)} de ${r.empleado} · neto ${Formato.plata(r.neto ?: 0.0)}") {
                estado.tarea {
                    try { api.hacer("DELETE", "$base/${r.id}") } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    estado.version += 1
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja("Liquidar sueldo", confirmar = "Liquidar", confirmarHabilitado = !(empleado.isEmpty() || periodo.isEmpty() || guardando),
        alConfirmar = { alcance.launch { liquidar() } }) {
        SeccionConsorcio {
            RenglonSelectorConsorcio("Empleado", empleados.map { it.id to it.nombre }, empleado) { empleado = it; periodo = mesPara(it) }
            RenglonSelectorConsorcio("Mes", meses.map { it to Cons.mesConAnio(it) }, periodo) { periodo = it }
            CampoConsorcio("Otros remunerativos (opcional)", ayuda = "Horas extra, plus por tareas. El básico y la antigüedad salen de la ficha.") {
                CampoNumeroConsorcio(otros, { otros = it }, "Monto")
            }
            CampoConsorcio("Nota (opcional)") { CampoTextoConsorcio(notas, { notas = it }) }
        }
    }
}

// MARK: - Empleado y escala

@Serializable
private data class CuerpoEmpleado(val nombre: String, val documento: String?, val categoria: String?, val ingreso: String?, val basico: Double?, val horario: String?, val notas: String?)

/** Alta o edición de un empleado: nombre, CUIL, ingreso, categoría (de la escala), básico (vacío: el de la escala),
 *  horario y nota. */
@Composable
fun HojaEmpleadoConsorcio(c: ConsorcioAPI, e: EmpleadoAPI?, escalas: List<EscalaAPI>) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    val hoy = LocalDate.now(Formato.zona)
    val ingresoE = e?.ingreso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    var nombre by remember { mutableStateOf(e?.nombre ?: "") }
    var documento by remember { mutableStateOf(e?.documento ?: "") }
    var conIngreso by remember { mutableStateOf(ingresoE != null) }
    var ingreso by remember { mutableStateOf(ingresoE ?: hoy) }
    var categoria by remember { mutableStateOf(if (e != null) e.categoria ?: "" else escalas.firstOrNull()?.categoria ?: "") }
    var basico by remember { mutableStateOf(e?.basico) }
    var horario by remember { mutableStateOf(e?.horario ?: "") }
    var notas by remember { mutableStateOf(e?.notas ?: "") }
    var guardando by remember { mutableStateOf(false) }

    val categorias: List<String> = run {
        val l = mutableListOf<String>()
        for (s in escalas) if (s.categoria !in l) l.add(s.categoria)
        e?.categoria?.let { if (it !in l) l.add(it) }
        l
    }

    suspend fun guardar() {
        guardando = true
        val d = CuerpoEmpleado(nombre.trim(), documento.noVacio(), categoria.noVacio(), if (conIngreso) ingreso.toString() else null,
            basico?.let { Cons.redondo(it) }, horario.noVacio(), notas.noVacio())
        val base = "/v1/consorcios/${c.id}/empleados"
        try {
            if (e != null) {
                estado.api.hacer("PUT", "$base/${e.id}", d)
                estado.avisar("Empleado guardado")
            } else {
                estado.api.hacer("POST", base, d)
                estado.avisar("Empleado cargado")
            }
            estado.version += 1
            cerrar()
        } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) }
        guardando = false
    }

    MarcoHoja(e?.let { "Editar a ${it.nombre}" } ?: "Sumar empleado", confirmar = "Guardar", confirmarHabilitado = !(nombre.trim().isEmpty() || guardando),
        alConfirmar = { alcance.launch { guardar() } }) {
        SeccionConsorcio {
            CampoConsorcio("Nombre") { CampoTextoConsorcio(nombre, { nombre = it }) }
            CampoConsorcio("CUIL (opcional)") { CampoTextoConsorcio(documento, { documento = it }, teclado = KeyboardType.Phone) }
            Interruptor("Ingresó el", conIngreso, { conIngreso = it })
            if (conIngreso) RenglonFechaConsorcio("Fecha de ingreso", ingreso, { ingreso = it }, maximo = hoy)
            RenglonSelectorConsorcio("Categoría", listOf("" to "Sin categoría") + categorias.map { it to it }, categoria) { categoria = it }
            CampoConsorcio("Básico (opcional)", ayuda = "Vacío: el de la escala de su categoría.") { CampoNumeroConsorcio(basico, { basico = it }, "Monto") }
            CampoConsorcio("Horario (opcional)") { CampoTextoConsorcio(horario, { horario = it }) }
            CampoConsorcio("Nota (opcional)") { CampoTextoConsorcio(notas, { notas = it }, "2.ª categoría") }
        }
    }
}

@Serializable
private data class CuerpoEscala(val categoria: String, val basico: Double, val vigenteDesde: String?, val notas: String?)

/** Una categoría nueva en la escala de sueldos (SUTERH), con su básico y desde cuándo vale. Sólo el dueño. */
@Composable
fun HojaEscalaSueldos() {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var categoria by remember { mutableStateOf("") }
    var basico by remember { mutableStateOf<Double?>(null) }
    var desde by remember { mutableStateOf(LocalDate.parse(Cons.hoy).withDayOfMonth(1)) }
    var notas by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    suspend fun guardar() {
        guardando = true
        try {
            estado.api.hacer("POST", "/v1/sueldos/escalas", CuerpoEscala(categoria.trim(), Cons.redondo(basico ?: 0.0), desde.toString(), notas.noVacio()))
            estado.avisar("Escala guardada")
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja("Sumar categoría", confirmar = "Guardar", confirmarHabilitado = !(categoria.trim().isEmpty() || (basico ?: -1.0) < 0 || guardando),
        alConfirmar = { alcance.launch { guardar() } }) {
        SeccionConsorcio {
            CampoConsorcio("Categoría") { CampoTextoConsorcio(categoria, { categoria = it }, "Encargado permanente con vivienda") }
            CampoConsorcio("Básico") { CampoNumeroConsorcio(basico, { basico = it }, "Monto") }
            RenglonFechaConsorcio("Vigente desde", desde, { desde = it })
            CampoConsorcio("Nota (opcional)") { CampoTextoConsorcio(notas, { notas = it }, "Escala SUTERH noviembre") }
        }
    }
}
