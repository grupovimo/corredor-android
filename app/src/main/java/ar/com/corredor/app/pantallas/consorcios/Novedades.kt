package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.nucleo.abrirMail
import ar.com.corredor.app.nucleo.Fecha

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// Clon de Pantallas/Consorcios/Novedades.swift.

// MARK: - Libro de novedades

private val tipoNovedad: Map<String, String> = NovedadAPI.tipos.toMap()

@Serializable
private data class EstadoNovedad(val estado: String)

/** Libro de novedades: lo que pasa en el edificio (asambleas, arreglos, avisos), con fecha y quién lo anotó; lo
 *  pendiente se marca resuelto al toque (o vuelve a pendiente), con deshacer. */
@Composable
fun PestanaNovedadesConsorcio(c: ConsorcioAPI, pedido: String?, alCambiarPedido: (String?) -> Unit) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var novedades by remember { mutableStateOf<List<NovedadAPI>?>(null) }
    var local by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var escribir by remember { mutableStateOf(false) }

    suspend fun cargar() {
        try {
            novedades = estado.api.get("/v1/consorcios/${c.id}/novedades", listOf("limite" to "200"))
            local = emptyMap()
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && novedades == null) error = e
        }
    }

    fun estadoDe(n: NovedadAPI): String = local[n.id] ?: n.estado ?: "abierto"

    suspend fun marcar(n: NovedadAPI) {
        val antes = estadoDe(n)
        val despues = if (antes == "resuelto") "abierto" else "resuelto"
        local = local + (n.id to despues)
        val ruta = "/v1/consorcios/${c.id}/novedades/${n.id}"
        try {
            estado.api.enviar<NovedadAPI, EstadoNovedad>("PATCH", ruta, EstadoNovedad(despues))
            val api = estado.api
            estado.avisar(if (despues == "resuelto") "Marcada como resuelta" else "Volvió a pendiente") {
                local = local + (n.id to antes)
                estado.tarea {
                    try { api.enviar<NovedadAPI, EstadoNovedad>("PATCH", ruta, EstadoNovedad(antes)) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    estado.version += 1
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            local = local + (n.id to antes)
            estado.avisar(e)
        }
    }

    LaunchedEffect(estado.version) { cargar() }
    LaunchedEffect(pedido) {
        if (pedido == "nueva") { if (estado.escribe) escribir = true; alCambiarPedido(null) }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val l = novedades
        val e = error
        if (l != null) {
            val pendientes = l.count { estadoDe(it) != "resuelto" }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Texto(if (pendientes == 0) "Nada pendiente" else "$pendientes pendiente${if (pendientes == 1) "" else "s"}", Estilo.footnote, C.texto2, Modifier.weight(1f))
                if (estado.escribe) BotonBordeadoConsorcio("Escribir", "pencil", lleno = C.accion) { escribir = true }
            }
            Column(Modifier.fillMaxWidth().tarjeta()) {
                if (l.isEmpty()) VacioConsorcio("El libro está vacío. Anotá lo que pasa en el edificio: asambleas, arreglos, avisos a los vecinos.")
                l.forEachIndexed { i, n ->
                    key(n.id) {
                        val es = estadoDe(n)
                        RenglonConsorcio(primero = i == 0) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Texto(listOfNotNull(n.ocurrioEl?.let { Cons.diaBarraMes(it) }, n.responsable, tipoNovedad[n.tipo ?: "otro"]).joinToString(" · "), Estilo.footnote, C.texto2)
                                Texto(n.titulo, Estilo.texto14, C.texto)
                                n.descripcion.noVacio()?.let { Texto(it, Estilo.footnote, C.texto2) }
                            }
                            Tocable({ alcance.launch { marcar(n) } }, Modifier.semantics { selected = es == "resuelto" }, habilitado = estado.escribe) {
                                ChipConsorcio(if (es == "resuelto") "Resuelta" else "Pendiente", if (es == "resuelto") Tono.verde else Tono.oro)
                            }
                        }
                    }
                }
            }
        } else if (e != null) {
            EstadoError("el libro", e) { alcance.launch { cargar() } }
        } else EsqueletoFilas(filas = 4)
    }
    Hoja(escribir, { escribir = false }) { HojaNovedadConsorcio(c) }
}

@Serializable
private data class NuevaNovedad(val titulo: String, val descripcion: String?, val tipo: String, val estado: String, val responsable: String?, val ocurrioEl: String)

/** Escribir en el libro: qué pasó, el tipo, la fecha, la firma (quien escribe) y si queda pendiente. */
@Composable
fun HojaNovedadConsorcio(c: ConsorcioAPI) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var titulo by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("aviso") }
    var fecha by remember { mutableStateOf(LocalDate.now(Formato.zona)) }
    var firma by remember { mutableStateOf(estado.usuario?.nombre?.split(" ")?.firstOrNull { it.isNotEmpty() } ?: "") }
    var pendiente by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    suspend fun anotar() {
        guardando = true
        try {
            estado.api.hacer("POST", "/v1/consorcios/${c.id}/novedades",
                NuevaNovedad(titulo.trim(), null, tipo, if (pendiente) "abierto" else "resuelto", firma.take(80).noVacio(), fecha.toString()))
            estado.avisar("Anotado en el libro")
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja(
        "Escribir en el libro", confirmar = "Anotar", confirmarHabilitado = !(titulo.trim().isEmpty() || titulo.length > 200 || guardando),
        alConfirmar = { alcance.launch { anotar() } },
    ) {
        SeccionConsorcio {
            CampoConsorcio("Qué pasó") { CampoTextoConsorcio(titulo, { titulo = it }, "Se aprobó en asamblea cambiar el portón.", lineas = 3, lineasMaximas = 6) }
            if (titulo.length > 200) Texto("Hasta 200 letras.", Estilo.footnote, C.error)
            RenglonSelectorConsorcio("Es", NovedadAPI.tipos, tipo) { tipo = it }
            RenglonFechaConsorcio("Fecha", fecha, { fecha = it })
            CampoConsorcio("Firma") { CampoTextoConsorcio(firma, { firma = it }) }
            Interruptor("Queda pendiente (aparece en las alertas hasta resolverla)", pendiente, { pendiente = it })
        }
    }
}

// MARK: - Proveedores del consorcio

/** Proveedores del edificio: primero los que trabajan acá (con lo gastado en el último mes y los trabajos abiertos),
 *  después el resto de la inmobiliaria. WhatsApp y mail a un toque. */
@Composable
fun PestanaProveedoresConsorcio(c: ConsorcioAPI, periodos: List<PeriodoAPI>) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var proveedores by remember { mutableStateOf<List<ProveedorConsorcio>?>(null) }
    var uso by remember { mutableStateOf<Map<String, Pair<Double, Int>>>(emptyMap()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nuevo by remember { mutableStateOf(false) }

    val periodoUso: String? = periodos.firstOrNull()?.periodo

    suspend fun cargar() {
        val b = "/v1/consorcios/${c.id}"
        try {
            val l = ProveedoresConsorcio.todos(estado.api)
            val trabajos: List<TrabajoAPI> = estado.api.opcional<List<TrabajoAPI>>("$b/trabajos", listOf("limite" to "200")) ?: emptyList()
            val u = mutableMapOf<String, Pair<Double, Int>>()
            val ultimo = periodos.firstOrNull()
            if (ultimo != null) {
                estado.api.opcional<PeriodoAPI>("$b/periodos/${ultimo.id}")?.let { f ->
                    for (g in f.gastos ?: emptyList()) g.proveedorId?.let { p -> val x = u[p] ?: (0.0 to 0); u[p] = (x.first + g.monto) to x.second }
                }
            }
            for (t in trabajos) if (t.estado != "hecho" && t.estado != "cancelado") t.proveedorId?.let { p -> val x = u[p] ?: (0.0 to 0); u[p] = x.first to (x.second + 1) }
            uso = u
            proveedores = l
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && proveedores == null) error = e
        }
    }

    fun detalle(id: String): String? {
        val u = uso[id] ?: return null
        return listOfNotNull(
            if (u.first > 0) periodoUso?.let { "${Formato.plata(u.first)} en ${Cons.nombreMes(it)}" } else null,
            if (u.second > 0) Cons.plural(u.second, "trabajo abierto", "trabajos abiertos") else null,
        ).joinToString(" · ")
    }

    @Composable
    fun fila(p: ProveedorConsorcio, primera: Boolean) {
        RenglonConsorcio(primero = primera) {
            IconoTono(Cons.iconoRubro(p.rubro), Tono.neutro, tam = 36.dp)
            DosLineasConsorcio(p.nombre, listOfNotNull(p.rubro, detalle(p.id).noVacio() ?: p.telefono ?: p.email).joinToString(" · ").noVacio() ?: "Sin datos de contacto")
            p.email.noVacio()?.let { m ->
                Box(Modifier.size(36.dp).clickable { abrirMail(ctx, m) }.semantics { contentDescription = "Mandarle un mail a ${p.nombre}" }, contentAlignment = Alignment.Center) {
                    Icono("envelope", 17f, C.accion)
                }
            }
            p.telefono.noVacio()?.let { t ->
                Box(Modifier.size(36.dp).clickable { Afuera.whatsapp(t, "Hola, te escribo de la administración del ${c.nombre}.") }
                    .semantics { contentDescription = "Escribirle a ${p.nombre} por WhatsApp" }, contentAlignment = Alignment.Center) {
                    Imagen(Imagenes.logoWhatsApp, Modifier.size(22.dp))
                }
            }
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val l = proveedores
        val e = error
        if (l != null) {
            val cotejo = Collator.getInstance(Formato.argentina)
            val ord = l.sortedWith { a, b ->
                val ua = uso[a.id] != null; val ub = uso[b.id] != null
                if (ua != ub) (if (ua) -1 else 1) else cotejo.compare(a.nombre, b.nombre)
            }
            val deAca = ord.filter { uso[it.id] != null }
            val otros = ord.filter { uso[it.id] == null }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Texto(Cons.plural(l.size, "proveedor", "proveedores"), Estilo.footnote, C.texto2, Modifier.weight(1f))
                if (estado.escribe) BotonBordeadoConsorcio("Nuevo proveedor", "plus") { nuevo = true }
            }
            if (deAca.isNotEmpty()) {
                BloqueConsorcio("Trabajan en este edificio") {
                    deAca.forEachIndexed { i, p -> key(p.id) { fila(p, i == 0) } }
                }
            }
            BloqueConsorcio(if (deAca.isEmpty()) "Proveedores" else "Los demás") {
                if (otros.isEmpty()) {
                    VacioConsorcio(if (l.isEmpty()) "Cargá a los que trabajan en tus edificios: plomeros, electricistas, el abono del ascensor." else "Todos trabajan en este edificio.")
                }
                otros.forEachIndexed { i, p -> key(p.id) { fila(p, i == 0) } }
            }
        } else if (e != null) {
            EstadoError("los proveedores", e) { alcance.launch { cargar() } }
        } else EsqueletoFilas(filas = 4)
    }
    Hoja(nuevo, { nuevo = false }) { HojaProveedorConsorcio() }
}

/** Un proveedor nuevo (queda para todos los edificios): nombre, rubro, CUIT, celular y mail. */
@Composable
fun HojaProveedorConsorcio() {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    var rubro by remember { mutableStateOf("") }
    var cuit by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    suspend fun guardar() {
        guardando = true
        try {
            ProveedoresConsorcio.crear(estado.api, ProveedoresConsorcio.Nuevo(nombre = nombre.trim(), rubro = rubro.noVacio(), cuit = cuit.noVacio(),
                telefono = telefono.noVacio(), email = email.noVacio()))
            estado.avisar("Proveedor cargado")
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja("Nuevo proveedor", confirmar = "Guardar", confirmarHabilitado = !(nombre.trim().isEmpty() || guardando), alConfirmar = { alcance.launch { guardar() } }) {
        SeccionConsorcio {
            CampoConsorcio("Nombre") { CampoTextoConsorcio(nombre, { nombre = it }, "Plomería Rossi") }
            CampoConsorcio("Rubro (opcional)") { CampoTextoConsorcio(rubro, { rubro = it }, "Plomería") }
            CampoConsorcio("CUIT (opcional)") { CampoTextoConsorcio(cuit, { cuit = it }, teclado = KeyboardType.Phone) }
            CampoConsorcio("Celular (opcional)") { CampoTextoConsorcio(telefono, { telefono = it }, teclado = KeyboardType.Phone) }
            CampoConsorcio("Mail (opcional)") { CampoTextoConsorcio(email, { email = it }, teclado = KeyboardType.Email, sinCorrector = true) }
        }
    }
}
