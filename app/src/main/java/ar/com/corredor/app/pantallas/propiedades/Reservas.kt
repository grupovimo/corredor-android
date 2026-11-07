package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cartera.Contacto
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// Clon de Pantallas/Propiedades/Reservas.swift.

// MARK: - Reservas de venta y alquiler

/** Una reserva (GET /v1/reservas, /v1/reservas/{id}; dominio/reservas.Reserva). */
@Serializable
data class ReservaAPI(
    val id: String,
    val propiedadId: String,
    val contactoId: String? = null,
    val interesadoNombre: String? = null,
    val interesadoTelefono: String? = null,
    val sena: Double? = null,
    val senaMoneda: String? = null,
    val reservadaEl: String? = null,
    val venceEl: String? = null,
    /** Hasta qué hora del día de vencimiento («18:00»). */
    val venceHora: String? = null,
    val notas: String? = null,
    val operacionId: String? = null,
    /** activa · concretada · caida */
    val estado: String,
    val cerradaEl: String? = null,
    val motivoCierre: String? = null,
    val contactoNombre: String? = null,
    val propiedadNumero: Int? = null,
    val propiedadTitulo: String? = null,
    val vencida: Boolean? = null,
    val reciboNumero: Int? = null,
    val creadaPor: String? = null,
    val creadoEn: FechaApi? = null,
) {
    val activa: Boolean get() = estado == "activa"
    val quien: String get() = contactoNombre ?: interesadoNombre ?: "Sin nombre"

    /** Días hasta el vencimiento (negativo si ya venció). */
    val faltan: Int? get() = venceEl?.let { Fecha.dia(it) }?.let { diasCalendario(Instant.now(), it) }

    /** «Vence en 3 días», «Venció ayer», «Aceptada», «Se cayó». */
    val titular: String
        get() {
            if (estado == "concretada") return "Aceptada"
            if (estado == "caida") return "Se cayó"
            val f = faltan ?: return "Sin vencimiento"
            if (f < -1) return "Venció hace ${-f} días"
            if (f == -1) return "Venció ayer"
            if (f == 0) return if (vencida == true) "Venció hoy" else "Vence hoy"
            if (f == 1) return "Vence mañana"
            return "Vence en $f días"
        }

    /** El cuerpo de PUT /v1/reservas/{id} (los datos editables, como datosDe de la web). Van todas las claves, también
     *  las vacías (null). */
    data class Datos(
        val contactoId: String?,
        val interesadoNombre: String?,
        val interesadoTelefono: String?,
        val sena: Double?,
        val senaMoneda: String,
        val reservadaEl: String?,
        val venceEl: String?,
        val venceHora: String?,
        val notas: String?,
        val operacionId: String?,
    ) {
        fun json(): JsonObject = buildJsonObject {
            put("contactoId", JsonPrimitive(contactoId))
            put("interesadoNombre", JsonPrimitive(interesadoNombre))
            put("interesadoTelefono", JsonPrimitive(interesadoTelefono))
            put("sena", JsonPrimitive(sena))
            put("senaMoneda", JsonPrimitive(senaMoneda))
            put("reservadaEl", JsonPrimitive(reservadaEl))
            put("venceEl", JsonPrimitive(venceEl))
            put("venceHora", JsonPrimitive(venceHora))
            put("notas", JsonPrimitive(notas))
            put("operacionId", JsonPrimitive(operacionId))
        }
    }

    val datos: Datos
        get() = Datos(
            contactoId, interesadoNombre, interesadoTelefono, sena, senaMoneda ?: "USD",
            reservadaEl, venceEl, if (venceEl != null) venceHora else null, notas, operacionId,
        )

    /** «Ofrece USD 140.000» escrito en las notas (la API no tiene el campo). */
    val oferta: Pair<String, Double>?
        get() {
            val n = notas ?: return null
            val m = Regex("""ofrece\s+(USD|ARS|U\${'$'}S|\${'$'})\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(n) ?: return null
            val t = m.value
            val moneda = if (t.contains("ARS", ignoreCase = true) || Regex("""\${'$'}\s*\d""").containsMatchIn(t) && !t.contains("U\$S")) "ARS" else "USD"
            val num = t.split(Regex("[^0-9.]")).filter { it.isNotEmpty() }.lastOrNull() ?: ""
            val v = num.replace(".", "").toDoubleOrNull() ?: return null
            return moneda to v
        }

    companion object {
        /** «0001-00471». */
        fun recibo(n: Int): String = String.format("0001-%05d", n)

        /** «12/10». */
        fun corta(dia: String?): String {
            val d = dia ?: return ""
            if (d.length < 10) return ""
            return "${d.drop(8).take(2)}/${d.drop(5).take(2)}"
        }
    }
}

/** Las reservas: las activas primero, cada una con su estado, y «Vencen esta semana» arriba. */
@Composable
fun Reservas(ruta: String = "/v1/reservas") {
    val estado = LocalEstado.current
    var lista by remember { mutableStateOf<List<ReservaAPI>?>(null) }
    var proximas by remember { mutableStateOf<List<ReservaAPI>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }

    suspend fun cargar() {
        try {
            lista = estado.api.todas(ruta)
            error = null
            if (ruta == "/v1/reservas") {
                proximas = estado.api.opcional<List<ReservaAPI>>("/v1/reservas", listOf("estado" to "activa", "vencen" to "7")) ?: emptyList()
            }
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    LaunchedEffect(estado.version) { cargar() }

    @Composable
    fun tarjeta(rs: List<ReservaAPI>) {
        Column(Modifier.fillMaxWidth().tarjeta()) {
            rs.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                Tocable({ estado.abrir(AReserva(id = r.id)) }) { FilaReserva(r) }
            }
        }
    }

    PantallaApilada("Reservas") {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val l = lista
                val e = error
                if (l != null) {
                    if (ruta == "/v1/reservas" && proximas.isNotEmpty()) {
                        TituloBloque(texto = "Vencen esta semana", cuenta = "${proximas.size}")
                        tarjeta(proximas)
                    }
                    if (l.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio(
                                icono = "hand.raised", titulo = "No hay reservas",
                                texto = "Se toman desde la ficha de una propiedad: quién, la seña y hasta cuándo.",
                                accion = "Ir a la cartera" to { estado.ir(Ruta(Pestana.propiedades)) },
                            )
                        }
                    } else {
                        TituloBloque(texto = "Todas", cuenta = "${l.size}")
                        tarjeta(l)
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError("las reservas", e) { estado.tarea { cargar() } } }
                } else EsqueletoFilas(filas = 5)
            }
        }
    }
}

/** Una fila de reserva: «Activa · vence 12/11 · seña USD 5.000», «Aceptada», «Se cayó». */
@Composable
fun FilaReserva(r: ReservaAPI) {
    val tono = if (r.estado == "concretada") Tono.verde else if (r.estado == "caida") Tono.neutro else if ((r.faltan ?: 9) <= 1) Tono.rojo else Tono.oro
    val detalle = when (r.estado) {
        "concretada" -> "${r.quien} · Aceptada"
        "caida" -> "${r.quien} · Se cayó" + (r.motivoCierre?.let { " · $it" } ?: "")
        else -> listOfNotNull(r.quien, "Activa", r.venceEl?.let { "vence ${ReservaAPI.corta(it)}" }, r.sena?.let { "seña ${Formato.plata(it, r.senaMoneda ?: "USD")}" })
            .joinToString(" · ")
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoTono(icono = if (r.estado == "caida") "bookmark.slash" else "bookmark", tono = tono, tam = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(r.propiedadTitulo ?: "Propiedad", Estilo.filaLista, C.texto, lineas = 1)
            Texto(detalle, Estilo.footnote, if (tono == Tono.rojo) C.errorTinta else C.texto2, lineas = 2)
        }
        Chevron()
    }
}

/** La reserva (handoff 02 · 13E): la cuenta regresiva, la seña, la oferta, el interesado, la línea de tiempo y las tres
 *  salidas (Aceptada, Extender 48 h, Se cayó). Aceptada y Se cayó esperan 6 s para deshacer antes de ir a la API (la API
 *  no reabre una reserva cerrada). */
@Composable
fun PantallaReserva(id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var r by remember { mutableStateOf<ReservaAPI?>(null) }
    var prop by remember { mutableStateOf<Propiedad?>(null) }
    var propietario by remember { mutableStateOf<Contacto?>(null) }
    var telInteresado by remember { mutableStateOf<String?>(null) }
    var proximas by remember { mutableStateOf<List<ReservaAPI>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var preguntarSena by remember { mutableStateOf(false) }

    val clase = if (prop?.operacion == "venta" || prop == null) "compra" else "alquiler"
    val direccion = prop?.direccion ?: r?.propiedadTitulo ?: "la propiedad"

    suspend fun cargar() {
        try {
            val x: ReservaAPI = estado.api.get("/v1/reservas/$id")
            r = x
            error = null
            prop = estado.api.opcional<Propiedad>("/v1/propiedades/${x.propiedadId}")
            x.contactoId?.let { c -> telInteresado = estado.api.opcional<Contacto>("/v1/contactos/$c")?.telefono }
            prop?.propietarioId?.let { pid -> propietario = estado.api.opcional<Contacto>("/v1/contactos/$pid") }
            proximas = estado.api.opcional<List<ReservaAPI>>("/v1/reservas", listOf("estado" to "activa", "vencen" to "7")) ?: emptyList()
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    @Serializable data class CuerpoCerrar(val resultado: String, val cerradaEl: String, val motivo: String? = null)

    fun cerrarCon(resultado: String, motivo: String?) {
        val antes = r ?: return
        r = antes.copy(estado = resultado, cerradaEl = Formato.diaISO(Instant.now()), motivoCierre = motivo)
        val hoy = Formato.diaISO(Instant.now())
        val tarea: Job = estado.tarea {
            delay(6000)
            try {
                val x: ReservaAPI = estado.api.enviar("POST", "/v1/reservas/${antes.id}/cerrar", CuerpoCerrar(resultado, hoy, motivo))
                r = x
                estado.version += 1
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                r = antes
                estado.avisar(e)
            }
        }
        val texto = if (resultado == "concretada") "Reserva aceptada · $direccion queda ${if (clase == "compra") "vendida" else "alquilada"}"
        else "Se cayó la reserva · $direccion vuelve a estar disponible"
        estado.avisar(texto) {
            tarea.cancel()
            r = antes
        }
    }

    suspend fun extender(antes: ReservaAPI) {
        val hoy = Instant.now()
        val base = antes.venceEl?.let { Fecha.dia(it) }?.let { if (it > hoy) it else hoy } ?: hoy
        val nuevo = Formato.diaISO(base.atZone(Formato.zona).plusDays(2).toInstant())
        val d = antes.datos.copy(venceEl = nuevo)
        ocupado = true
        try {
            val g: ReservaAPI = estado.api.enviar("PUT", "/v1/reservas/${antes.id}", d.json())
            r = g
            estado.avisar("Extendida 48 h · ahora vence el ${Fecha.dia(nuevo)?.let { Formato.fechaLarga(it) } ?: nuevo}") {
                estado.tarea {
                    try {
                        val v: ReservaAPI = estado.api.enviar("PUT", "/v1/reservas/${antes.id}", antes.datos.json())
                        r = v
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { ocupado = false }
    }

    LaunchedEffect(estado.version) { cargar() }

    @Composable
    fun cabecera(r: ReservaAPI) {
        Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icono(if (r.estado == "caida") "bookmark.slash.fill" else "bookmark.fill", 13f, C.texto2)
                Texto("Reserva de $clase · $direccion", Estilo.footnoteFuerte, C.texto2, lineas = 2)
            }
            // HStack(alignment: .firstTextBaseline): alineados por la primera línea de texto.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Texto(r.titular, Estilo.titulo, if (r.activa && (r.faltan ?: 0) < 0) C.errorTinta else C.texto, Modifier.alignBy(FirstBaseline))
                val v = r.venceEl?.let { Fecha.dia(it) }
                val c = r.cerradaEl
                if (r.activa && v != null) {
                    Texto(Formato.fechaLarga(v) + (r.venceHora?.let { " · $it" } ?: ""), Estilo.subhead, C.texto2, Modifier.alignBy(FirstBaseline))
                } else if (!r.activa && c != null) {
                    Texto("el ${ReservaAPI.corta(c)}", Estilo.subhead, C.texto2, Modifier.alignBy(FirstBaseline))
                }
            }
            val fv = r.venceEl?.let { Fecha.dia(it) }
            val fd = r.reservadaEl?.let { Fecha.dia(it) }
            val m = r.motivoCierre
            if (r.activa && fv != null && fd != null) {
                val total = maxOf(1, diasCalendario(fd, fv))
                val pasaron = minOf(total, maxOf(0, diasCalendario(fd, Instant.now())))
                BarraAvance(pasaron.toDouble() / total, color = C.oro)
                Texto("Pasaron $pasaron de ${Formato.cantidad(total, "día", "días")}", Estilo.footnote, C.texto2)
            } else if (!r.activa && m != null) {
                Texto(m, Estilo.footnote, C.texto2)
            }
        }
    }

    @Composable
    fun dato(clave: String, valor: String, nota: String, modifier: Modifier, verde: Boolean = false) {
        Column(modifier.tarjeta(radio = 14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(clave, Estilo.footnote, C.texto2)
            Texto(valor, Estilo.subheadFuerte, C.texto, lineas = 2, escalaMinima = 0.8f)
            Texto(nota, Estilo(12f, Peso.regular), if (verde) C.okTinta else C.texto3, lineas = 2)
        }
    }

    @Composable
    fun datos(r: ReservaAPI) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            val m = Modifier.weight(1f)
            dato(
                "Seña", r.sena?.let { Formato.plata(it, r.senaMoneda ?: "USD") } ?: "Sin seña",
                if (r.sena != null) (r.reservadaEl?.let { "recibida ${ReservaAPI.corta(it)}" } ?: "") else "de palabra", m, verde = r.sena != null,
            )
            val o = r.oferta
            if (o != null) {
                dato("Ofrece", Formato.plata(o.second, o.first), prop?.precio?.let { "pide ${Formato.plata(it, prop?.moneda ?: "USD")}" } ?: "", m)
            } else {
                dato("Pide", prop?.precio?.let { Formato.plata(it, prop?.moneda ?: "USD") } ?: "Consultar", "sin oferta anotada", m)
            }
            dato(if (clase == "compra") "Interesada" else "Inquilino", r.quien, r.interesadoTelefono ?: telInteresado ?: "sin teléfono", m)
        }
    }

    @Composable
    fun paso(hecho: Boolean, ahora: Boolean, t: String, sub: String?) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icono(if (hecho) "checkmark.circle.fill" else if (ahora) "hourglass.circle.fill" else "circle", 20f, if (hecho) C.okTinta else if (ahora) C.avisoTinta else C.texto3)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(t, Estilo.subheadFuerte, C.texto)
                if (sub != null) Texto(sub, Estilo.footnote, if (ahora) C.avisoTinta else C.texto2)
            }
        }
    }

    @Composable
    fun tiempo(r: ReservaAPI) {
        Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp)) {
            paso(
                hecho = r.sena != null || !r.activa, ahora = false, if (r.sena != null) "Seña recibida" else "Reserva tomada",
                if (r.sena != null && r.reciboNumero != null) "Recibo ${ReservaAPI.recibo(r.reciboNumero)}${r.reservadaEl?.let { " · el ${ReservaAPI.corta(it)}" } ?: ""}"
                else r.reservadaEl?.let { "el ${ReservaAPI.corta(it)}${r.notas?.let { n -> " · $n" } ?: ""}" },
            )
            paso(
                hecho = !r.activa, ahora = r.activa, "Respuesta del propietario",
                if (r.activa) (propietario?.let { "Esperando a ${it.nombre.split(" ").firstOrNull() ?: it.nombre}" } ?: "Esperando respuesta")
                else (if (r.estado == "concretada") "Aceptó la oferta" else "No se concretó"),
            )
            paso(hecho = r.estado == "concretada", ahora = false, if (clase == "compra") "Boleto" else "Contrato", null)
        }
    }

    @Composable
    fun salidas(r: ReservaAPI) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val t = propietario?.telefono
            val n = propietario?.nombre
            val ti = r.interesadoTelefono ?: telInteresado
            if (t != null && n != null) {
                BotonPrincipal("Llamar a $n, el propietario", { Afuera.llamar(t) }, simbolo = "phone.fill")
            } else if (ti != null) {
                BotonPrincipal("Llamar a ${r.quien}", { Afuera.llamar(ti) }, simbolo = "phone.fill")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonHundido("Aceptada", { cerrarCon("concretada", null) }, Modifier.weight(1f), habilitado = !ocupado)
                BotonHundido(if (ocupado) "Extendiendo…" else "Extender 48 h", { estado.tarea { extender(r) } }, Modifier.weight(1f), habilitado = !ocupado)
                BotonHundido("Se cayó", { if (r.sena != null) preguntarSena = true else cerrarCon("caida", null) }, Modifier.weight(1f), habilitado = !ocupado)
            }
            Texto(Formato.sinViudas("«Se cayó» te pregunta si devolvés la seña y deja la propiedad disponible otra vez, con deshacer."), Estilo.footnote, C.texto2)
        }
    }

    @Composable
    fun propiedad(p: Propiedad) {
        Tocable({ estado.abrir(AFichaPropiedad(id = p.id)) }, Modifier.fillMaxWidth().tarjeta()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Foto(p.miniatura, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(p.direccion ?: p.nombre, Estilo.filaLista, C.texto, lineas = 1)
                    Texto(listOfNotNull(p.titulo, p.precioTexto).joinToString(" · "), Estilo.footnote, C.texto2, lineas = 1)
                    Texto("La ficha de la propiedad", Estilo.footnoteFuerte, C.accion)
                }
                Chevron()
            }
        }
    }

    PantallaApilada("Reserva") {
        Box(Modifier.fillMaxSize()) {
            val x = r
            val e = error
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (x != null) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        cabecera(x)
                        datos(x)
                        tiempo(x)
                        if (x.activa && estado.escribe) salidas(x) else if (!x.activa) {
                            Texto(Formato.sinViudas("La reserva quedó en el historial de la propiedad. Si vuelve el interesado, cargá una nueva."), Estilo.footnote, C.texto2)
                        }
                        prop?.let { propiedad(it) }
                        val otras = proximas.filter { it.id != x.id }
                        if (otras.isNotEmpty()) {
                            TituloBloque(texto = "Vencen esta semana")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                otras.forEachIndexed { i, o ->
                                    if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                    Tocable({ estado.abrir(AReserva(id = o.id)) }) { FilaReserva(o) }
                                }
                            }
                        }
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 60.dp)) { EstadoError("la reserva", e) { estado.tarea { cargar() } } }
                } else EsqueletoFilas(filas = 4, modifier = Modifier.padding(16.dp))
            }
        }
    }

    Confirmacion(
        visible = preguntarSena, titulo = "¿Devolvés la seña?",
        mensaje = "Seña de ${r?.quien ?: ""}: ${r?.sena?.let { Formato.plata(it, r?.senaMoneda ?: "USD") } ?: ""}. La propiedad vuelve a estar disponible. Lo anotamos en la reserva.",
        opciones = listOf(
            OpcionConfirmacion("Sí, la devuelvo") { cerrarCon("caida", "Seña devuelta") },
            OpcionConfirmacion("No, queda retenida") { cerrarCon("caida", "Seña retenida") },
        ),
        alCerrar = { preguntarSena = false }, volver = "Cancelar",
    )
}

/** Reservar una propiedad, un lote o una unidad: quién (nombre libre, no hace falta que sea un contacto), el teléfono, la
 *  seña con su moneda y hasta qué día y hora. Queda reservada al toque. `alReservar`: al crearla (la ficha abre la
 *  reserva; el emprendimiento avisa con deshacer). */
@Composable
fun NuevaReservaVenta(propiedadId: String, titulo: String, moneda: String = "USD", alReservar: (ReservaAPI) -> Unit = {}) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var sena by remember { mutableStateOf("") }
    var senaMoneda by remember { mutableStateOf(moneda) }
    var vence by remember { mutableStateOf(LocalDate.now(Formato.zona).plusDays(7)) }
    var hora by remember { mutableStateOf(18 * 60) }
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mandando by remember { mutableStateOf(false) }
    val envio = remember { UUID.randomUUID().toString() }

    @Serializable data class CuerpoReserva(
        val interesadoNombre: String, val interesadoTelefono: String? = null, val sena: Double? = null,
        val senaMoneda: String, val venceEl: String, val venceHora: String,
    )

    suspend fun reservar() {
        errores = emptyMap()
        if (nombre.trim().length < 2) {
            errores = mapOf("interesadoNombre" to "Poné quién reserva. Sin nombre no sabés a quién devolverle la seña.")
            return
        }
        mandando = true
        try {
            val numero = sena.replace(".", "").replace(",", ".").toDoubleOrNull()
            val r: ReservaAPI = estado.api.conCamposComo(
                "POST", "/v1/propiedades/$propiedadId/reservas",
                CuerpoReserva(
                    interesadoNombre = nombre.trim(), interesadoTelefono = telefono.nonEmpty, sena = numero, senaMoneda = senaMoneda,
                    venceEl = vence.toString(), venceHora = String.format("%02d:%02d", hora / 60, hora % 60),
                ),
                envio = envio,
            )
            estado.version += 1
            cerrar()
            alReservar(r)
        } catch (e: CancellationException) { throw e } catch (e: ErrorConCampos) {
            errores = e.campos
        } catch (e: Exception) { estado.avisar(e) } finally { mandando = false }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(
            titulo = "Reservar $titulo",
            cancelar = "Cancelar", alCancelar = { cerrar() },
            confirmar = if (mandando) "Reservando…" else "Reservar", alConfirmar = { if (!mandando) estado.tarea { reservar() } },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SeccionLista(null, pie = "Queda reservada al toque y deja de ofrecerse.") {
                Entrada(nombre, { nombre = it }, "Quién reserva", Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp))
                (errores["interesadoNombre"] ?: errores["contactoId"])?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { MensajeError(it) } }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                Entrada(telefono, { telefono = it }, "Teléfono (opcional)", Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp), teclado = KeyboardType.Phone)
                errores["interesadoTelefono"]?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { MensajeError(it) } }
            }
            SeccionLista("Seña") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), senaMoneda, { senaMoneda = it }) }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                Entrada(sena, { sena = it }, "Seña", Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp), teclado = KeyboardType.Decimal)
                (errores["sena"] ?: errores["senaMoneda"])?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { MensajeError(it) } }
            }
            SeccionLista(null) {
                RenglonFecha("Vence el", vence, { vence = it })
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                RenglonHora("a las", hora, { hora = it })
                (errores["venceEl"] ?: errores["venceHora"])?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { MensajeError(it) } }
            }
        }
    }
}
