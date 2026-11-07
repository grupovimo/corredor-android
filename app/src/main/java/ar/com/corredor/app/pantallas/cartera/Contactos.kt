package ar.com.corredor.app.pantallas.cartera

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.R
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.inicio.*
import ar.com.corredor.app.pantallas.mensajes.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.Instant

// Clon de Pantallas/Cartera/Contactos.swift.

/** GET /v1/contactos/resumen: los números de los filtros de la lista. */
@Serializable
data class ResumenContactos(
    val total: Int,
    val tipos: Map<String, Int>? = null,
    val esperando: Int? = null,
    val etiquetas: List<Etiqueta>? = null,
) {
    @Serializable data class Etiqueta(val texto: String, val cuenta: Int)
}

// MARK: - 8A Contactos

object Contactos {
    const val porPagina = 60

    fun tono(t: String?): Tono = when (t) {
        "comprador" -> Tono.oro
        "inquilino" -> Tono.azul
        "propietario", "vendedor" -> Tono.verde
        "garante" -> Tono.violeta
        else -> Tono.neutro
    }

    /** Abre (o crea) la conversación de WhatsApp con el contacto dentro de Corredor. */
    suspend fun abrirChat(contacto: String, estado: Estado) {
        @Serializable data class Cuerpo(val contactoId: String)
        @Serializable data class Conv(val id: String)
        try {
            val x: Conv = estado.api.enviar("POST", "/v1/whatsapp/conversaciones", Cuerpo(contactoId = contacto))
            estado.abrir(AChat(id = x.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }
}

/** 8A Contactos, como la lista del panel: lo más reciente arriba, la búsqueda en el servidor
 *  (nombre, teléfono o lo que busca), las listas con su número, las etiquetas, los que esperan
 *  respuesta arriba con WhatsApp a un toque, y en cada fila la deuda, el «escribió por WhatsApp» o lo
 *  que busca, con el «cuándo» de la derecha. */
@Composable
fun Contactos(libres: Boolean = false) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<Contacto>?>(null) }
    var esperan by remember { mutableStateOf<List<Contacto>>(emptyList()) }
    var resumen by remember { mutableStateOf<ResumenContactos?>(null) }
    var q by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("") }
    var etiqueta by remember { mutableStateOf<String?>(null) }
    /** Sólo las consultas sin dueño (desde «Tomá una consulta sin dueño» del Inicio). */
    val libresInicial = libres
    var libres by remember { mutableStateOf(libresInicial) }
    var nuevo by remember { mutableStateOf(false) }
    var importar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var guardadoEn by remember { mutableStateOf<Instant?>(null) }

    val filtrado = q.isNotEmpty() || etiqueta != null || libres || filtro.isNotEmpty()

    suspend fun cargar() {
        val query = mutableListOf("limite" to Contactos.porPagina.toString())
        q.nonEmpty?.let { query.add("q" to it.take(120)) }
        etiqueta?.let { query.add("etiqueta" to it) }
        if (libres) query.add("agente" to "libre")
        if (filtro == "sin_respuesta") query.add("esperando" to "si")
        else TextosContacto.listas.firstOrNull { it.clave == filtro && it.clave.isNotEmpty() }?.let { query.add("tipo" to it.tipos.joinToString(",")) }
        val clave = if (filtrado) null else "contactos"
        try {
            val l: List<Contacto> = estado.api.get("/v1/contactos", query)
            lista = l
            error = null
            guardadoEn = null
            if (clave != null) {
                Guardado.guardar(l, clave = clave)
                // Spotlight.indexar(contactos:) en iOS: Android no tiene un buscador del sistema para esto.
            }
        } catch (e: ErrorApi) {
            val g = if (e.sinConexion && clave != null) Guardado.leer<List<Contacto>>(clave = clave) else null
            if (g != null) { lista = g.first; guardadoEn = g.second } else if (lista == null) error = e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        resumen = estado.api.opcional<ResumenContactos>("/v1/contactos/resumen")
        esperan = if (filtrado) emptyList() else (estado.api.opcional<List<Contacto>>("/v1/contactos", listOf("esperando" to "si", "limite" to "5")) ?: emptyList())
    }

    LaunchedEffect(estado.version, q, filtro, etiqueta, libres) {
        if (q.isNotEmpty()) delay(250)
        cargar()
    }

    @Composable
    fun Avatar(c: Contacto) {
        val t = Contactos.tono(c.tipo)
        Box(Modifier.size(42.dp).clip(CircleShape).background(t.fondo), contentAlignment = Alignment.Center) {
            Texto(Formato.iniciales(c.nombre), Estilo(14f, Peso.bold), t.tinta)
        }
    }

    @Composable
    fun BotonWhatsApp(c: Contacto, lleno: Boolean) {
        Tocable(alTocar = { alcance.launch { Contactos.abrirChat(c.id, estado = estado) } }, descripcion = "WhatsApp a ${c.nombre}") {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (lleno) hex(0x25D366) else C.hundida), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.logo_whatsapp), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (lleno) Color.White else C.accion))
            }
        }
    }

    @Composable
    fun Fila(c: Contacto, primera: Boolean) {
        val (cuando, tono) = TextosContacto.cuando(c)
        val detalle = TextosContacto.detalle(c).nonEmpty ?: c.telefono ?: " "
        val debe = TextosContacto.deuda(c) != null
        val linea = C.linea
        Row(
            Modifier.fillMaxWidth().then(if (!primera) Modifier.lineaDesde(54.dp, linea) else Modifier).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Tocable(alTocar = { estado.abrir(AFichaContacto(id = c.id)) }, modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                            Texto(c.nombre, Estilo(16f, Peso.semibold), C.texto, Modifier.weight(1f, fill = false), lineas = 1)
                            Texto(TextosContacto.rol(c.tipo), Estilo.footnote, C.texto3, lineas = 1)
                        }
                        Texto(detalle, Estilo.footnote, if (debe) C.errorTinta else C.texto2, lineas = 1)
                    }
                    Texto(cuando, Estilo(12f, Peso.semibold), if (tono == Tono.oro) C.avisoTinta else if (tono == Tono.verde) C.okTinta else C.texto3)
                }
            }
            if (c.telefono != null) {
                Tocable(alTocar = { Afuera.llamar(c.telefono) }, descripcion = "Llamar a ${c.nombre}") {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) { Icono("phone.fill", 16f, C.accion) }
                }
                BotonWhatsApp(c, lleno = false)
            }
        }
    }

    /** Los que esperan respuesta, arriba y con WhatsApp a un toque. */
    @Composable
    fun BloqueEsperan() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto("Esperan respuesta", Estilo.label, C.avisoTinta, Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().tarjeta()) {
                esperan.forEachIndexed { i, c ->
                    key(c.id) {
                        Row(Modifier.fillMaxWidth().lineaArriba(i > 0).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tocable(alTocar = { estado.abrir(AFichaContacto(id = c.id)) }, modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(c)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Texto(c.nombre, Estilo.filaLista, C.texto)
                                        Texto("${c.propiedad ?: TextosContacto.detalle(c)} · ${TextosContacto.cuando(c).first}", Estilo.footnote, C.texto2, lineas = 1)
                                    }
                                }
                            }
                            BotonWhatsApp(c, lleno = true)
                        }
                    }
                }
            }
            Texto("Recientes", Estilo.label, C.texto2, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
        }
    }

    PantallaApilada(
        titulo = "Contactos",
        grande = true,
        acciones = {
            if (estado.escribe) {
                // Menu { … } primaryAction: { nuevo = true }: tocar abre el alta; mantener apretado, el menú.
                MenuContextual(
                    opciones = listOf(
                        OpcionDeMenu("Nuevo contacto", "person.badge.plus") { nuevo = true },
                        OpcionDeMenu("Importar del celular o un Excel", "square.and.arrow.down") { importar = true },
                    ),
                    alTocar = { nuevo = true },
                ) {
                    Box(Modifier.size(44.dp).semantics { contentDescription = "Contacto nuevo" }, contentAlignment = Alignment.Center) { Icono("plus", 20f, C.accion) }
                }
            }
        },
    ) {
        Refrescable(alRefrescar = { cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(horizontal = 16.dp)) { Buscador(placeholder = "Nombre, teléfono o lo que busca", texto = q, alCambiar = { q = it }) }
                guardadoEn?.let { g -> Box(Modifier.padding(horizontal = 16.dp)) { BannerSinConexion(guardado = g) } }
                if (resumen?.total != 0) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextosContacto.listas.forEach { l ->
                            key(l.clave) {
                                val n: Int? = if (l.clave.isEmpty()) resumen?.total else l.tipos.sumOf { resumen?.tipos?.get(it) ?: 0 }
                                ChipFiltro(texto = l.texto + (n?.let { " ${Formato.numero(it)}" } ?: ""), activo = filtro == l.clave && etiqueta == null && !libres, hacer = {
                                    filtro = l.clave
                                    etiqueta = null
                                    libres = false
                                })
                            }
                        }
                        val e = resumen?.esperando
                        if (e != null && e > 0) {
                            ChipFiltro(texto = "Sin respuesta ${Formato.numero(e)}", activo = filtro == "sin_respuesta" && etiqueta == null && !libres, hacer = {
                                filtro = "sin_respuesta"; etiqueta = null; libres = false
                            })
                        }
                        if (libres) {
                            Tocable(alTocar = { Haptico.seleccion(); libres = false }, descripcion = "Sin dueño. Quitar el filtro", modifier = Modifier.semantics { selected = true }) {
                                Box(Modifier.height(36.dp).clip(CircleShape).background(C.accion).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                    TextoDespues("Sin dueño", "xmark", Estilo(14f, Peso.semibold), C.sobreAccion)
                                }
                            }
                        }
                        (resumen?.etiquetas ?: emptyList()).take(8).forEach { et ->
                            key(et.texto) {
                                val on = etiqueta == et.texto
                                Tocable(alTocar = { Haptico.seleccion(); etiqueta = if (etiqueta == et.texto) null else et.texto }, modifier = Modifier.semantics { selected = on }) {
                                    Box(Modifier.height(36.dp).clip(CircleShape).background(if (on) C.accion else C.avisoFondo).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                        ConIcono("${et.texto} ${Formato.numero(et.cuenta)}", "tag", Estilo(14f, Peso.semibold), if (on) C.sobreAccion else C.avisoTinta)
                                    }
                                }
                            }
                        }
                    }
                }
                // Contenido
                val l = lista
                val e = error
                if (l != null) {
                    if (resumen?.total == 0 && !filtrado) {
                        Box(Modifier.padding(top = 40.dp)) {
                            EstadoVacio(
                                icono = "person.2", titulo = "Los contactos llegan solos", texto = "Cada consulta crea el contacto con lo que busca.",
                                accion = if (estado.escribe) ("Nuevo contacto" to { nuevo = true }) else null,
                                secundaria = if (estado.escribe) ("Importar del celular o un Excel" to { importar = true }) else null,
                            )
                        }
                    } else if (l.isEmpty() && libres && q.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio(
                                icono = "tray", titulo = "No quedan consultas sin dueño", texto = "Ya las tomó alguien.",
                                accion = "Ver todos" to { filtro = ""; etiqueta = null; libres = false },
                            )
                        }
                    } else if (l.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio(
                                icono = "magnifyingglass", titulo = "No hay nadie con esa búsqueda", texto = "Probá con el teléfono o un barrio.",
                                accion = "Ver todos" to { q = ""; filtro = ""; etiqueta = null; libres = false },
                            )
                        }
                    } else {
                        if (esperan.isNotEmpty() && !filtrado) BloqueEsperan()
                        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().tarjeta()) {
                            l.forEachIndexed { i, c -> key(c.id) { Fila(c, primera = i == 0) } }
                        }
                        if (l.size >= Contactos.porPagina) {
                            Texto(
                                Formato.sinViudas("Se ven los primeros 60. Buscá para ver al resto."), Estilo.footnote, C.texto3,
                                Modifier.fillMaxWidth().padding(vertical = 8.dp), alinear = TextAlign.Center,
                            )
                        }
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError(que = "los contactos", error = e) { alcance.launch { cargar() } } }
                } else {
                    Box(Modifier.padding(horizontal = 16.dp)) { EsqueletoFilas(filas = 8) }
                }
            }
        }
    }

    Hoja(visible = nuevo, alCerrar = { nuevo = false }, alturas = listOf(Altura.grande)) { EditarContacto() }
    Hoja(visible = importar, alCerrar = { importar = false }, alturas = listOf(Altura.media, Altura.grande)) { ImportarContactos() }
}

/** La rayita de arriba con sangría (`Rectangle().frame(height: 0.5).padding(.leading, n)`). */
internal fun Modifier.lineaDesde(desde: androidx.compose.ui.unit.Dp, color: Color): Modifier = this.drawBehind {
    drawLine(color, androidx.compose.ui.geometry.Offset(desde.toPx(), 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 0.5.dp.toPx())
}

object ImportarContactos {
    @Serializable
    data class Vista(val id: String, val total: Int? = null, val validas: Int? = null, val conErrores: Int? = null)
}

/** «Importar del celular» (varios de la agenda del teléfono, con origen «Agenda del celular») y «Subir
 *  Excel» (pasa por la importación de la API: primero se ve cuántos entran y después se confirma). */
@Composable
fun ImportarContactos() {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var agenda by remember { mutableStateOf(false) }
    var subiendo by remember { mutableStateOf(false) }
    var confirmando by remember { mutableStateOf(false) }
    var importando by remember { mutableStateOf(false) }
    var vista by remember { mutableStateOf<ImportarContactos.Vista?>(null) }

    fun resumen(v: ImportarContactos.Vista): String {
        val total = v.total ?: 0
        val validas = v.validas ?: 0
        val errores = v.conErrores ?: 0
        var t = "Leímos ${Formato.cantidad(total, "fila", "filas")}: entran ${Formato.cantidad(validas, "contacto", "contactos")}"
        if (errores > 0) t += " y ${if (errores == 1) "1 fila tiene" else "${Formato.numero(errores)} filas tienen"} errores (esas no se importan)"
        return "$t."
    }

    suspend fun delCelular(cs: List<ContactoAgenda>) {
        @Serializable data class Cuerpo(val nombre: String, val telefono: String?, val email: String?, val origen: String = "Agenda del celular")
        importando = true
        var creados = 0
        try {
            for (c in cs.take(500)) {
                val nombre = c.nombre.trim()
                if (nombre.isEmpty()) continue
                val mail = c.email?.takeIf { it.contains("@") }
                try {
                    estado.api.hacer("POST", "/v1/contactos", Cuerpo(nombre = nombre.take(120), telefono = c.telefono, email = mail))
                    creados += 1
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // El que falla (un mail raro) no frena a los demás.
                }
            }
            estado.avisar(Formato.cantidad(creados, "contacto importado", "contactos importados"))
            estado.version += 1
            if (creados > 0) cerrar()
        } finally {
            importando = false
        }
    }

    suspend fun subir(u: Uri) {
        subiendo = true
        try {
            val d = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: throw IllegalStateException()
            if (d.size > 5 shl 20) { estado.avisar("El archivo pasa de 5 MB.", ok = false); return }
            val nombre = ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "contactos.xlsx"
            val tipo = if (nombre.substringAfterLast('.', "").lowercase() == "csv") "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            val r = estado.api.multipart("/v1/importaciones", query = listOf("tipo" to "contactos"), datos = d, nombre = nombre, tipo = tipo)
            vista = Api.json.decodeFromString<ImportarContactos.Vista>(String(r, Charsets.UTF_8))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(Estado.texto(e, si = "No pudimos leer el archivo."), ok = false)
        } finally {
            subiendo = false
        }
    }

    suspend fun confirmar(v: ImportarContactos.Vista) {
        @Serializable data class R(val creadas: Int? = null)
        @Serializable data class Resultado(val resultado: R? = null)
        confirmando = true
        try {
            val r: Resultado = estado.api.enviar("POST", "/v1/importaciones/${v.id}/confirmar", JsonObject(emptyMap()))
            estado.avisar(Formato.cantidad(r.resultado?.creadas ?: 0, "contacto importado", "contactos importados"))
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            confirmando = false
        }
    }

    val archivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) alcance.launch { subir(u) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        ConIcono("Traer tus contactos", "square.and.arrow.down", Estilo.tituloHojaChico, C.texto)
        val v = vista
        if (v != null) {
            Texto(Formato.sinViudas(resumen(v)), Estilo.body16, C.texto)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonSecundario("Cancelar", { vista = null }, Modifier.weight(1f))
                BotonPrincipal(if (confirmando) "Importando…" else "Importar", { alcance.launch { confirmar(v) } }, Modifier.weight(1f), habilitado = (v.validas ?: 0) != 0 && !confirmando)
            }
        } else {
            Texto(Formato.sinViudas("Elegí varios de la agenda del teléfono o subí un Excel o CSV con nombre, teléfono y mail."), Estilo.subhead, C.texto2)
            BotonPrincipal(if (importando) "Importando…" else "Importar del celular", { agenda = true }, habilitado = !importando)
            BotonSecundario(if (subiendo) "Leyendo…" else "Subir Excel", {
                archivo.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            }, Modifier.fillMaxWidth(), habilitado = !subiendo)
            Spacer(Modifier.weight(1f))
        }
    }

    Hoja(visible = agenda, alCerrar = { agenda = false }, alturas = listOf(Altura.grande)) {
        SelectorAgendaVarios { cs -> alcance.launch { delCelular(cs) } }
    }
}
