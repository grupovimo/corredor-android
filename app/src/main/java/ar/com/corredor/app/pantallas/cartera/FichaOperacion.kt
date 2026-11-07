package ar.com.corredor.app.pantallas.cartera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.*
import ar.com.corredor.app.pantallas.cobranzas.AltaContrato
import ar.com.corredor.app.pantallas.inicio.*
import ar.com.corredor.app.pantallas.mensajes.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

// Clon de Pantallas/Cartera/FichaOperacion.swift.

/** 9C Ficha de la operación, como la del panel (18b): «¿qué falta para cerrar?». La etapa como una
 *  línea que se toca para pasar a cualquiera (con Deshacer); «Para pasar a Cierre» según sea venta o
 *  alquiler; las dos partes; la seña y los honorarios (editables); los adjuntos; el historial con
 *  comentarios; «Se cayó» y «Reabrir». */
@Composable
fun FichaOperacion(id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var o by remember { mutableStateOf<Operacion?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var historial by remember { mutableStateOf<List<EventoOperacion>>(emptyList()) }
    var adjuntos by remember { mutableStateOf<List<Archivo>>(emptyList()) }
    var visitas by remember { mutableIntStateOf(0) }
    var proxima by remember { mutableStateOf<Cita?>(null) }
    var contacto by remember { mutableStateOf<Contacto?>(null) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var comentario by remember { mutableStateOf("") }
    var haciendoContrato by remember { mutableStateOf(false) }
    var editarPlata by remember { mutableStateOf(false) }
    var cayendo by remember { mutableStateOf(false) }
    var agendar by remember { mutableStateOf(false) }
    var subiendo by remember { mutableStateOf(false) }

    val puedeMover = estado.escribe
    val nombres: Map<String, String> = equipo.associate { it.usuarioId to it.completo }

    suspend fun cargar() {
        try {
            o = estado.api.get<Operacion>("/v1/operaciones/$id")
            error = null
        } catch (e: ErrorApi) {
            if (o == null) error = e
            return
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        historial = (estado.api.opcional<List<EventoOperacion>>("/v1/operaciones/$id/historial") ?: emptyList()).sortedByDescending { it.creadoEn }
        val l = mutableListOf<Archivo>()
        for (a in o?.adjuntos ?: emptyList()) estado.api.opcional<Archivo>("/v1/archivos/$a")?.let { l.add(it) }
        adjuntos = l
        val citas: List<Cita> = estado.api.opcional<List<Cita>>("/v1/agenda", listOf("operacion" to id, "rango" to "todas", "limite" to "50")) ?: emptyList()
        visitas = citas.count { it.tipo == "visita" && it.estado != "cancelada" }
        val ahora = Instant.now()
        proxima = citas.filter { it.estado == "pendiente" && it.empiezaEn > ahora }.minByOrNull { it.empiezaEn }
        o?.contactoId?.let { c -> contacto = estado.api.opcional<Contacto>("/v1/contactos/$c") }
        if (equipo.isEmpty()) equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
    }

    suspend fun aEtapa(i: Int) {
        val op = o ?: return
        if (!puedeMover || op.caidaEn != null || i == Operacion.columna(op.etapa)) return
        val destino = Operacion.columnas[i]
        val antes = op.etapa
        @Serializable data class Cuerpo(val etapa: String, val desde: String?)
        try {
            estado.api.hacer("POST", "/v1/operaciones/$id/mover", Cuerpo(etapa = destino.clave, desde = antes))
            Haptico.impacto()
            if (destino.clave == "cierre" && op.propiedadOperacion == "alquiler") {
                estado.sugerir("Llegó al cierre: armá el contrato", "Hacer contrato") { haciendoContrato = true }
            } else {
                estado.avisar("${op.nombre} pasó a ${destino.texto}") {
                    alcance.launch {
                        try {
                            estado.api.hacer("POST", "/v1/operaciones/$id/mover", Cuerpo(etapa = antes, desde = null))
                            estado.avisar("Listo, volvió a su etapa")
                            estado.version += 1
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            estado.avisar(e)
                        }
                    }
                }
            }
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
            cargar()
        }
    }

    suspend fun marcar(p: Operacion.Paso) {
        if (p.auto) return
        @Serializable data class Cuerpo(val paso: String, val hecho: Boolean)
        try {
            estado.api.hacer("POST", "/v1/operaciones/$id/checklist", Cuerpo(paso = p.clave, hecho = !p.hecho))
            Haptico.impacto()
            cargar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    suspend fun comentar() {
        @Serializable data class Cuerpo(val texto: String)
        val t = comentario.trim()
        comentario = ""
        try {
            estado.cola.enviar("POST", "/v1/operaciones/$id/notas", Cuerpo(texto = t.take(2000)), etiqueta = "Comentario en la operación")
            cargar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            comentario = t
            estado.avisar(e)
        }
    }

    suspend fun reabrir() {
        try {
            estado.api.hacer("POST", "/v1/operaciones/$id/reabrir")
            estado.avisar("Volvió al tablero")
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    suspend fun caer(motivo: String) {
        @Serializable data class Cuerpo(val motivo: String)
        val n = o?.nombre ?: "La operación"
        try {
            estado.api.hacer("POST", "/v1/operaciones/$id/caer", Cuerpo(motivo = motivo))
            estado.avisar("$n se cayó · ${motivo.lowercase()}") { alcance.launch { reabrir() } }
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    suspend fun ponerAdjuntos(ids: List<String>) {
        @Serializable data class Cuerpo(val adjuntos: List<String>)
        estado.api.hacer("PUT", "/v1/operaciones/$id/adjuntos", Cuerpo(adjuntos = ids))
    }

    suspend fun subir(d: ByteArray, nombre: String, tipo: String) {
        subiendo = true
        try {
            val a = estado.api.subir(d, nombre = nombre, tipo = tipo, uso = "adjunto")
            ponerAdjuntos((o?.adjuntos ?: emptyList()) + a.id)
            estado.avisar("Adjunto subido")
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            subiendo = false
        }
    }

    suspend fun subirArchivo(u: Uri) {
        try {
            val d = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: throw IllegalStateException()
            val nombre = ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "archivo"
            subir(d, nombre, ctx.contentResolver.getType(u) ?: "application/octet-stream")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar("No se pudo subir", ok = false)
        }
    }

    suspend fun quitar(a: Archivo) {
        val antes = o?.adjuntos ?: emptyList()
        try {
            ponerAdjuntos(antes.filter { it != a.id })
            estado.avisar("Adjunto quitado") {
                alcance.launch {
                    try {
                        ponerAdjuntos(antes)
                        estado.version += 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        estado.avisar(e)
                    }
                }
            }
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    /** Lo baja con la sesión de la app y lo muestra con la vista previa. */
    suspend fun abrir(a: Archivo) {
        try {
            val f = ArchivosSesion.archivo(a.id, nombre = a.nombre?.nonEmpty ?: "documento-${a.id}", api = estado.api, ctx = ctx)
            verArchivo(ctx, f)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    val fotos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        alcance.launch {
            try {
                val d = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: return@launch
                val jpg = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(d, 0, d.size)?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
                } ?: d
                subir(jpg, "foto.jpg", "image/jpeg")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estado.avisar(e)
            }
        }
    }
    val archivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) alcance.launch { subirArchivo(u) } }

    LaunchedEffect(estado.version) { cargar() }

    // MARK: Partes de la ficha

    @Composable
    fun Cabecera(o: Operacion) {
        val dias = o.creadaEn?.let { CuentasInicio.diasEntre(it, Instant.now()) } ?: 0
        val tipo = mapOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario")[o.propiedadOperacion ?: ""]
        val sub = listOfNotNull(tipo, o.agenteNombre, if (dias <= 0) "abierta hoy" else "abierta hace ${Formato.cantidad(dias, "día", "días")}").joinToString(" · ")
        val hc = o.honorariosComprador
        val hv = o.honorariosVendedor
        val extra = listOfNotNull(
            o.propiedadPrecio?.let { if (it != o.valor) "pedía ${Formato.numero(it.toInt())}" else null },
            if (hc != null || hv != null) "honorarios ${hc?.let { Formato.porcentaje(it) } ?: "—"} + ${hv?.let { Formato.porcentaje(it) } ?: "—"}" else null,
        ).joinToString(" · ")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
                Foto(url = urlFotoPropiedad(o.propiedadFotoId, estado.api.base.toString(), estado.sesion?.inmobiliariaId), modifier = Modifier.matchParentSize())
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto(sub, Estilo.footnote, C.texto2)
                Texto("${o.nombre.split(",").firstOrNull() ?: ""}${o.contactoNombre?.let { " → $it" } ?: ""}", Estilo(22f, Peso.bold, -0.02f), C.texto)
                Texto(if ((o.valor ?: 0.0) > 0) Formato.plata(o.valor ?: 0.0, o.moneda ?: "USD") else "Sin oferta", Estilo.headline, C.texto)
                if (extra.isNotEmpty()) Texto(extra, Estilo.footnote, C.texto2)
            }
        }
    }

    @Composable
    fun Caida(o: Operacion) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.errorFondo).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono("heart.slash.fill", 17f, C.errorTinta)
            Texto("Se cayó el ${o.caidaEn?.let { FichaContacto.diaMes(it) } ?: ""} · ${o.caidaMotivo ?: ""}", Estilo.subheadFuerte, C.errorTinta, Modifier.weight(1f))
            if (puedeMover) BotonTonal("Reabrir", { alcance.launch { reabrir() } })
        }
    }

    /** «Visita · 2», «Reserva · seña USD 3.000». */
    fun extraEtapa(i: Int, o: Operacion): String {
        if (i != Operacion.columna(o.etapa)) return ""
        if (Operacion.columnas[i].clave == "visita" && visitas > 0) return " · $visitas"
        if (Operacion.columnas[i].clave == "reserva") return " · ${o.nota().first}"
        return ""
    }

    /** La etapa como una línea: tocar otra etapa la pasa ahí (con Deshacer). */
    @Composable
    fun Linea(o: Operacion) {
        val actual = Operacion.columna(o.etapa)
        val iconos = listOf("sparkle", "calendar", "bookmark", "hands.sparkles")
        Row(Modifier.fillMaxWidth().tarjeta().padding(vertical = 14.dp, horizontal = 8.dp), verticalAlignment = Alignment.Top) {
            Operacion.columnas.forEachIndexed { i, c ->
                Tocable(
                    alTocar = { alcance.launch { aEtapa(i) } }, modifier = Modifier.weight(1f),
                    habilitado = !(!puedeMover || o.caidaEn != null || i == actual),
                    descripcion = if (i == actual) "${c.texto}, etapa actual" else "Pasar a ${c.texto}",
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(if (i < actual) C.accion else (if (i == actual) C.oro else C.hundida)),
                            contentAlignment = Alignment.Center,
                        ) { Icono(if (i < actual) "checkmark" else iconos[i], 14f, if (i < actual) C.sobreAccion else (if (i == actual) hex(0x1A1405) else C.texto3)) }
                        Texto(c.texto + extraEtapa(i, o), Estilo(11f, if (i == actual) Peso.bold else Peso.medium), if (i <= actual) C.texto else C.texto3, lineas = 2, alinear = TextAlign.Center)
                    }
                }
            }
        }
    }

    @Composable
    fun TarjetaProxima(p: Cita) {
        Tocable(alTocar = { estado.abrir(ACita(id = p.id)) }) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.avisoFondo).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icono("calendar", 24f, C.avisoTinta)
                Column {
                    Texto("Próximo paso", Estilo(13f, Peso.bold), C.avisoTinta)
                    Texto("${p.tipoTexto} · ${Formato.diaCompleto(p.empiezaEn)}, ${Formato.horaDe(p.empiezaEn)}", Estilo(16f, Peso.bold), C.texto)
                }
            }
        }
    }

    @Composable
    fun Paso(p: Operacion.Paso, ahora: Boolean, primera: Boolean) {
        Row(
            Modifier.fillMaxWidth().lineaArriba(!primera).background(if (ahora) C.avisoFondo.copy(alpha = C.avisoFondo.alpha * 0.6f) else Color.Transparent)
                .heightIn(min = 50.dp).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Tocable(alTocar = { alcance.launch { marcar(p) } }, habilitado = !(!puedeMover || p.auto), descripcion = p.texto, modifier = Modifier.semantics { selected = p.hecho }) {
                Icono(if (p.hecho) "checkmark.square.fill" else "square", 24f, if (p.hecho) hex(0x2F9E6F) else C.texto3)
            }
            TextoTachado(p.texto, if (ahora) Estilo.body16.copy(peso = Peso.semibold) else Estilo.body16, if (p.hecho) C.texto3 else C.texto, p.hecho, Modifier.weight(1f))
            val l = p.llamar
            val d = p.detalle
            if (p.clave == "sena" && !p.hecho && puedeMover) {
                BotonTonal("Cargar seña", { editarPlata = true })
            } else if (ahora && l != null) {
                BotonTonal(l.first, { Afuera.llamar(l.second) }, simbolo = "phone.fill")
            } else if (d != null) {
                Texto(d, Estilo.footnote, C.texto2, alinear = TextAlign.End)
            }
        }
    }

    @Composable
    fun ParaCerrar(o: Operacion) {
        val pasos = o.pasosCierre(etiquetas = contacto?.etiquetas ?: emptyList())
        val actual = pasos.indexOfFirst { !it.hecho }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = if (Operacion.columna(o.etapa) == 3) "Para cerrar" else "Para pasar a Cierre", cuenta = "${pasos.count { it.hecho }} de ${pasos.size}")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                pasos.forEachIndexed { i, p -> key(p.clave) { Paso(p, ahora = i == actual, primera = i == 0) } }
            }
        }
    }

    @Composable
    fun Parte(n: String, d: String, primera: Boolean, abrir: String?, fin: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().lineaArriba(!primera).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Tocable(alTocar = { abrir?.let { estado.abrir(AFichaContacto(id = it)) } }, modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarIniciales(nombre = n, tam = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Texto(n, Estilo.filaLista, C.texto)
                        if (d.isNotEmpty()) Texto(d, Estilo.footnote, C.texto2)
                    }
                }
            }
            fin()
        }
    }

    @Composable
    fun Partes(o: Operacion) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Las partes")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                val n = o.contactoNombre
                if (n != null) {
                    val rol = if (o.propiedadOperacion == "venta") "parte compradora" else contacto?.let { TextosContacto.rol(it.tipo).lowercase() }
                    Parte(n, listOfNotNull(rol, contacto?.etiquetas?.firstOrNull()?.lowercase()).joinToString(" · "), primera = true, abrir = o.contactoId) {
                        val c = o.contactoId
                        if (c != null) {
                            Tocable(alTocar = { alcance.launch { Contactos.abrirChat(c, estado = estado) } }, descripcion = "WhatsApp a $n") {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) { Icono("bubble.left.fill", 17f, C.accion) }
                            }
                        }
                    }
                }
                val pn = o.propietarioNombre
                if (pn != null) {
                    Parte(pn, TextosContacto.rol("propietario").lowercase(), primera = o.contactoNombre == null, abrir = o.propietarioId) {
                        val t = o.propietarioTelefono
                        if (t != null) {
                            Tocable(alTocar = { Afuera.llamar(t) }, descripcion = "Llamar a $pn") {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) { Icono("phone.fill", 17f, C.accion) }
                            }
                        }
                    }
                }
                if (o.contactoNombre == null && o.propietarioNombre == null) {
                    Texto("Sin partes cargadas todavía.", Estilo.subhead, C.texto3, Modifier.padding(16.dp))
                }
            }
        }
    }

    @Composable
    fun FilaPlata(l: String, v: String, fuerte: Boolean = false) {
        Row(Modifier.fillMaxWidth()) {
            Texto(l, Estilo.body16.copy(peso = if (fuerte) Peso.bold else Peso.regular), C.texto, Modifier.weight(1f))
            Texto(v, Estilo.body16.copy(peso = Peso.bold), C.texto)
        }
    }

    @Composable
    fun Honorarios(o: Operacion) {
        val base = o.valor ?: 0.0
        val deC = o.honorariosComprador?.let { base * it / 100 }
        val deV = o.honorariosVendedor?.let { base * it / 100 }
        val paraAgente = o.comisionAgente?.let { ((deC ?: 0.0) + (deV ?: 0.0)) * it / 100 }
        val m = o.moneda ?: "USD"
        Tocable(alTocar = { editarPlata = true }, habilitado = puedeMover) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.okFondo).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Texto("HONORARIOS", Estilo(12f, Peso.bold, 0.5f / 12f), C.okTinta)
                if (deC != null || deV != null) {
                    FilaPlata("Comprador ${o.honorariosComprador?.let { Formato.porcentaje(it) } ?: "—"}", deC?.let { Formato.plata(it, m) } ?: "—")
                    FilaPlata("Vendedor ${o.honorariosVendedor?.let { Formato.porcentaje(it) } ?: "—"}", deV?.let { Formato.plata(it, m) } ?: "—")
                    if (paraAgente != null) {
                        FilaPlata("Para ${o.agenteNombre?.let { Charla.primerNombre(it) } ?: "el agente"} (${Formato.porcentaje(o.comisionAgente ?: 0.0)})", Formato.plata(paraAgente, m), fuerte = true)
                    }
                } else {
                    Texto("Cargá los porcentajes para ver cuánto queda", Estilo.subhead, C.texto2)
                }
                o.sena?.let { s -> FilaPlata("Seña", Formato.plata(s, o.senaMoneda ?: m)) }
            }
        }
    }

    @Composable
    fun AdjuntosVista(o: Operacion) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Adjuntos · ${adjuntos.size}")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                adjuntos.forEachIndexed { i, a ->
                    key(a.id) {
                        Row(Modifier.fillMaxWidth().lineaArriba(i > 0).heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tocable(alTocar = { alcance.launch { abrir(a) } }, modifier = Modifier.weight(1f)) {
                                ConIcono(a.nombre ?: "Documento", "doc.text", Estilo.body16, C.texto, Modifier.fillMaxWidth())
                            }
                            if (puedeMover) {
                                Tocable(alTocar = { alcance.launch { quitar(a) } }, descripcion = "Quitar ${a.nombre ?: "el adjunto"}") { Icono("trash", 17f, C.error) }
                            }
                        }
                    }
                }
                if (puedeMover) {
                    Box(Modifier.lineaArriba(adjuntos.isNotEmpty())) {
                        if (subiendo) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) { ConIcono("Subiendo…", "paperclip", Estilo.subheadFuerte, C.accion) }
                        } else {
                            MenuBoton(
                                opciones = listOf(
                                    OpcionDeMenu("Una foto", "photo") { fotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    OpcionDeMenu("Un archivo (PDF)", "doc") { archivos.launch(arrayOf("application/pdf", "image/*", "*/*")) },
                                ),
                                descripcion = "Subir: reserva firmada, DNI, informes",
                            ) {
                                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                    ConIcono("Subir: reserva firmada, DNI, informes", "paperclip", Estilo.subheadFuerte, C.accion)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HistorialVista() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Historial")
            Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (puedeMover) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val estilo = Estilo.body16
                        BasicTextField(
                            value = comentario, onValueChange = { comentario = it }, minLines = 1, maxLines = 6,
                            textStyle = estilo.ts().copy(color = C.texto), cursorBrush = SolidColor(C.accion), modifier = Modifier.weight(1f),
                            decorationBox = { campo -> Box { if (comentario.isEmpty()) Texto("Comentá algo para el equipo…", estilo, C.texto3); campo() } },
                        )
                        if (comentario.trim().isNotEmpty()) BotonTonal("Comentar", { alcance.launch { comentar() } })
                    }
                }
                historial.forEach { n ->
                    key(n.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Texto(FichaContacto.diaMes(n.creadoEn), Estilo(12f, Peso.regular), C.texto3, Modifier.width(40.dp))
                            Texto(n.frase(nombres = nombres), Estilo.subhead, if (n.tipo == "nota") C.texto else C.texto2, Modifier.weight(1f))
                        }
                    }
                }
                if (historial.isEmpty()) Texto("Todavía no pasó nada.", Estilo.texto14, C.texto3)
            }
        }
    }

    // MARK: Pantalla

    val op = o
    PantallaApilada(
        titulo = "Operación",
        acciones = {
            if (op != null) {
                val opciones = buildList {
                    op.contactoId?.let { c -> add(OpcionDeMenu("Ver el contacto", "person") { estado.abrir(AFichaContacto(id = c)) }) }
                    op.propiedadId?.let { p -> add(OpcionDeMenu("Ver la propiedad", "house") { estado.abrir(AFichaPropiedad(id = p)) }) }
                    if (puedeMover) add(OpcionDeMenu("Agendar visita", "calendar") { agendar = true })
                    if (puedeMover && op.caidaEn == null) add(OpcionDeMenu("Se cayó", "heart.slash", destructiva = true) { cayendo = true })
                }
                MenuBoton(opciones = opciones, descripcion = "Más") { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 20f, C.accion) } }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val e = error
            if (op != null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Cabecera(op)
                    if (op.caidaEn != null) Caida(op)
                    Linea(op)
                    proxima?.let { TarjetaProxima(it) }
                    if (op.caidaEn == null) ParaCerrar(op)
                    Partes(op)
                    Honorarios(op)
                    AdjuntosVista(op)
                    HistorialVista()
                    if (op.etapa == "cierre" && op.propiedadOperacion == "alquiler") {
                        BotonHundido("Hacer el contrato", { haciendoContrato = true })
                    }
                }
            } else if (e != null) {
                Box(Modifier.padding(top = 40.dp)) { EstadoError(que = "la operación", error = e) { alcance.launch { cargar() } } }
            } else {
                Box(Modifier.padding(16.dp)) { EsqueletoFilas(filas = 5) }
            }
        }
    }

    Hoja(visible = haciendoContrato, alCerrar = { haciendoContrato = false }, alturas = listOf(Altura.grande)) { AltaContrato(operacion = id) }
    Hoja(visible = editarPlata && op != null, alCerrar = { editarPlata = false }, alturas = listOf(Altura.grande)) {
        if (op != null) PlataOperacion(o = op, agente = op.agenteNombre?.let { Charla.primerNombre(it) })
    }
    Hoja(visible = cayendo && op != null, alCerrar = { cayendo = false }, alturas = listOf(Altura.media, Altura.grande)) {
        if (op != null) SeCayo(propiedad = op.nombre.split(",").firstOrNull() ?: "", contacto = op.contactoNombre) { m -> alcance.launch { caer(m) } }
    }
    Hoja(visible = agendar && op != null, alCerrar = { agendar = false }, alturas = listOf(Altura.grande)) {
        if (op != null) NuevaCita(contacto = op.contactoId?.let { it to (op.contactoNombre ?: "") }, operacion = op.id)
    }
}

/** «Seña y honorarios»: la seña, hasta cuándo vale la reserva (si no, 7 días) y los porcentajes. */
@Composable
fun PlataOperacion(o: Operacion, agente: String?) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    val sinFino = { p: Double -> Formato.porcentaje(p).replace("${Formato.fino}%", "") }
    var sena by remember { mutableStateOf(o.sena?.let { Formato.numero(it.toInt()) } ?: "") }
    val venceInicial = Fecha.fecha(o.senaVenceEl)
    var conVence by remember { mutableStateOf(venceInicial != null) }
    var vence by remember { mutableStateOf(venceInicial ?: Formato.hoy()) }
    var hc by remember { mutableStateOf(o.honorariosComprador?.let(sinFino) ?: "") }
    var hv by remember { mutableStateOf(o.honorariosVendedor?.let(sinFino) ?: "") }
    var ca by remember { mutableStateOf(o.comisionAgente?.let(sinFino) ?: "") }
    var guardando by remember { mutableStateOf(false) }

    fun numero(s: String): JsonElement {
        val t = s.trim()
        if (t.isEmpty()) return JsonNull
        val n = t.replace(".", "").replace(",", ".").filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return JsonNull
        return JsonPrimitive(n)
    }

    fun porcentaje(s: String): JsonElement {
        val t = s.trim()
        if (t.isEmpty()) return JsonNull
        val n = t.replace(",", ".").filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return JsonNull
        return JsonPrimitive(n)
    }

    suspend fun guardar() {
        val cuerpo = mutableMapOf<String, JsonElement>("honorariosComprador" to porcentaje(hc), "honorariosVendedor" to porcentaje(hv), "comisionAgente" to porcentaje(ca))
        val s = numero(sena)
        cuerpo["sena"] = s
        if (s != JsonNull) cuerpo["senaMoneda"] = JsonPrimitive(o.senaMoneda ?: o.moneda ?: "USD")
        // La fecha va sólo si la tocaron (o si sacan la seña): si no, al cargarla la API pone 7 días.
        val antes = o.senaVenceEl
        val ahora = if (conVence) vence.toString() else null
        if (s == JsonNull || ahora != antes) cuerpo["senaVenceEl"] = ahora?.let { JsonPrimitive(it) } ?: JsonNull
        guardando = true
        try {
            estado.api.hacer("PATCH", "/v1/operaciones/${o.id}", JsonObject(cuerpo))
            estado.avisar("Guardado")
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            guardando = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(titulo = "Seña y honorarios", cancelar = { cerrar() }) {
            Texto("Guardar", Estilo.headline, if (!guardando) C.accion else C.texto3, Modifier.clickable(enabled = !guardando) { alcance.launch { guardar() } })
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.fillMaxWidth().tarjeta(12.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoCorredor(sena, { sena = it }, placeholder = "Seña (${o.moneda ?: "USD"})", teclado = KeyboardType.Number)
                    Interruptor(texto = "La reserva vence", prendido = conVence, alCambiar = { conVence = it })
                    if (conVence) RenglonFecha(titulo = "Vence el", fecha = vence, alCambiar = { vence = it })
                }
                if (!conVence) Texto("Si no la ponés, en 7 días", Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto("HONORARIOS", Estilo(13f, Peso.regular), C.texto2, Modifier.padding(horizontal = 16.dp))
                Column(Modifier.fillMaxWidth().tarjeta(12.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoCorredor(hc, { hc = it }, placeholder = "Comprador (%)", teclado = KeyboardType.Decimal)
                    CampoCorredor(hv, { hv = it }, placeholder = "Vendedor (%)", teclado = KeyboardType.Decimal)
                    CampoCorredor(ca, { ca = it }, placeholder = "Para ${agente ?: "el agente"} (% de los honorarios)", teclado = KeyboardType.Decimal)
                }
            }
        }
    }
}
