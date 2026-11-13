package ar.com.corredor.app.pantallas.cartera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.inicio.*
import ar.com.corredor.app.pantallas.mensajes.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Duration
import java.time.Instant

// Clon de Pantallas/Cartera/Cartera.swift.

/** Los destinos de Contactos y Operaciones (los `navigationDestination` de iOS). app/Registro.kt lo llama con cada
 *  destino apilado: pinta la pantalla y devuelve true si es suyo. */
@Composable
fun destinosCartera(d: Any): Boolean {
    when (d) {
        is AContactos -> Contactos(libres = d.agente == "libre")
        is AFichaContacto -> FichaContacto(id = d.id)
        is AOperaciones -> Operaciones()
        is AFichaOperacion -> FichaOperacion(id = d.id)
        else -> return false
    }
    return true
}

/** Un dato de un JSON libre como texto (`datos?["k"]?.texto` en iOS). */
internal fun JsonElement?.texto(k: String): String? = ((this as? JsonObject)?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/** Un contacto (GET /v1/contactos, contactos.Contacto; la lista suma contactos.EnLista). */
@Serializable
data class Contacto(
    val id: String,
    val nombre: String,
    val telefono: String? = null,
    val email: String? = null,
    val tipo: String? = null,
    val origen: String? = null,
    val notas: String? = null,
    val agenteId: String? = null,
    val buscaOperacion: String? = null,
    val buscaTipo: String? = null,
    val buscaMoneda: String? = null,
    val buscaDesde: Double? = null,
    val buscaHasta: Double? = null,
    val buscaAmbientes: Int? = null,
    val buscaBarrios: List<String>? = null,
    /** Lo que pide además («Con balcón», «Apto crédito»): una lista, como los chips de la web. */
    val buscaDetalles: List<String>? = null,
    val etiquetas: List<String>? = null,
    val creadoEn: FechaApi? = null,
    val actualizadoEn: FechaApi? = null,
    // De la lista (contactos.EnLista): lo último que pasó y lo pendiente.
    val actividadEn: FechaApi? = null,
    val esperaDesde: FechaApi? = null,
    val proximaCitaEn: FechaApi? = null,
    val proximaCitaTitulo: String? = null,
    val propiedad: String? = null,
    /** Meses de alquiler vencidos sin cobrar (AAAA-MM), del más viejo al más nuevo. */
    val debe: List<String>? = null,
) {
    val tipoTexto: String get() = tipos.firstOrNull { it.first == tipo }?.second ?: "Contacto"

    /** Lo que busca, en chips: «3 ambientes», «Palermo o Belgrano», «USD 150 a 180 mil». */
    val busca: List<String>
        get() {
            val b = mutableListOf<String>()
            buscaOperacion?.let { o -> b.add(if (o == "alquiler") "Alquilar" else if (o == "venta") "Comprar" else o.replaceFirstChar { it.uppercase() }) }
            buscaTipo?.let { t -> b.add(Propiedad.tipos.firstOrNull { it.first == t }?.second ?: t) }
            buscaAmbientes?.let { b.add("$it ambientes") }
            val l = buscaBarrios
            if (l != null && l.isNotEmpty()) b.add(if (l.size == 1) l[0] else l.dropLast(1).joinToString(", ") + " o " + l.last())
            val m = buscaMoneda ?: "USD"
            val d = buscaDesde
            val h = buscaHasta
            when {
                d != null && h != null -> b.add("$m ${Formato.numero(d.toInt())} a ${Formato.numero(h.toInt())}")
                d == null && h != null -> b.add("Hasta ${Formato.plata(h, m)}")
                d != null && h == null -> b.add("Desde ${Formato.plata(d, m)}")
                else -> {}
            }
            b += (buscaDetalles ?: emptyList()).mapNotNull { it.nonEmpty }
            return b
        }

    companion object {
        val tipos = listOf("comprador" to "Comprador", "vendedor" to "Vendedor", "inquilino" to "Inquilino", "propietario" to "Propietario", "garante" to "Garante")
    }
}

/** Cómo se nombra a cada persona y cómo se resume lo que busca (web: lib/crm/contactos.ts y filas.ts). */
object TextosContacto {
    /** El rol, con formas que no marcan el género: «Persona interesada», «Parte inquilina». */
    fun rol(tipo: String?): String = mapOf(
        "comprador" to "Persona interesada", "vendedor" to "Parte vendedora", "inquilino" to "Parte inquilina",
        "propietario" to "Parte propietaria", "garante" to "Garante",
    )[tipo ?: ""] ?: "Contacto"

    data class Lista(val clave: String, val texto: String, val tipos: List<String>)

    /** Las listas del filtro, con sus tipos de la API. */
    val listas: List<Lista> = listOf(
        Lista("", "Todos", emptyList()), Lista("interesados", "Interesados", listOf("comprador")), Lista("propietarios", "Propietarios", listOf("propietario", "vendedor")),
        Lista("inquilinos", "Inquilinos", listOf("inquilino")), Lista("garantes", "Garantes", listOf("garante")),
    )

    /** Los tipos para elegir (alta y edición), como la web. */
    val tiposElegir: List<Pair<String, String>> = listOf(
        "comprador" to "Interesado", "propietario" to "Propietario", "vendedor" to "Vendedor", "inquilino" to "Inquilino", "garante" to "Garante",
    )

    private val tipoProp = mapOf(
        "departamento" to "depto", "casa" to "casa", "ph" to "PH", "duplex" to "dúplex", "local" to "local",
        "oficina" to "oficina", "lote" to "lote", "galpon" to "galpón", "cochera" to "cochera",
    )
    private val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    private fun unir(xs: List<String>): String = if (xs.size <= 1) xs.joinToString("") else xs.dropLast(1).joinToString(", ") + " o " + xs.last()

    private fun presupuesto(c: Contacto): String {
        val m = c.buscaMoneda ?: "USD"
        val d = c.buscaDesde
        val h = c.buscaHasta
        return when {
            d != null && h != null -> "de ${Formato.plata(d, m)} a ${Formato.numero(h.toInt())}"
            d == null && h != null -> "hasta ${Formato.plata(h, m)}"
            d != null && h == null -> "desde ${Formato.plata(d, m)}"
            else -> ""
        }
    }

    /** «Comprar 3 ambientes en Belgrano o Núñez, hasta USD 180.000». Vacío si no cargaron nada. */
    fun buscaLarga(c: Contacto): String {
        val verbo = when (c.buscaOperacion) { "venta" -> "Comprar"; "alquiler" -> "Alquilar"; "temporario" -> "Alquilar por temporada"; else -> "" }
        val que = c.buscaAmbientes?.let { "$it ambientes" } ?: c.buscaTipo?.let { tipoProp[it] ?: it } ?: ""
        val barrios = c.buscaBarrios ?: emptyList()
        val donde = if (barrios.isEmpty()) "" else "en ${unir(barrios)}"
        val plata = presupuesto(c)
        val cuerpo = listOf(verbo, que, donde).filter { it.isNotEmpty() }.joinToString(" ")
        if (cuerpo.isEmpty() && plata.isEmpty()) return ""
        val t = if (cuerpo.isEmpty()) plata else (if (plata.isEmpty()) cuerpo else "$cuerpo, $plata")
        return Formato.mayuscula(t)
    }

    /** Corta, para una fila: «3 amb Belgrano hasta USD 180.000». */
    fun buscaCorta(c: Contacto): String {
        val p = mutableListOf<String>()
        if (c.buscaOperacion == "alquiler") p.add("Alquiler")
        if (c.buscaOperacion == "temporario") p.add("Temporario")
        val a = c.buscaAmbientes
        val t = c.buscaTipo
        if (a != null) p.add("$a amb") else if (t != null) p.add(tipoProp[t] ?: t)
        val b = c.buscaBarrios
        if (b != null && b.isNotEmpty()) p.add(b.take(2).joinToString(" o "))
        val plata = presupuesto(c)
        if (plata.isNotEmpty()) p.add(plata)
        return p.joinToString(" ")
    }

    /** «debe noviembre», «debe octubre y noviembre», «debe 3 meses»; null si está al día. */
    fun deuda(c: Contacto): String? {
        val xs = c.debe ?: emptyList()
        if (xs.isEmpty()) return null
        val nombre = { m: String -> m.drop(5).take(2).toIntOrNull()?.let { if (it in 1..12) meses[it - 1] else null } ?: m }
        if (xs.size == 1) return "debe ${nombre(xs[0])}"
        if (xs.size == 2) return "debe ${nombre(xs[0])} y ${nombre(xs[1])}"
        return "debe ${xs.size} meses"
    }

    /** «Mendoza 2350 · escribió por WhatsApp», «3 amb Belgrano hasta USD 180.000», «Llegó por Zonaprop». */
    fun detalle(c: Contacto): String {
        val busca = buscaCorta(c)
        deuda(c)?.let { d -> return c.propiedad?.let { "$it · $d" } ?: Formato.mayuscula(d) }
        if (c.esperaDesde != null) return c.propiedad?.let { "$it · escribió por WhatsApp" } ?: "Escribió por WhatsApp"
        val p = c.propiedad
        if ((c.tipo ?: "") in listOf("propietario", "vendedor", "inquilino") && p != null) return p
        if (busca.isNotEmpty()) return busca
        if (p != null) return p
        return c.origen?.let { "Llegó por $it" } ?: ""
    }

    /** El «cuándo»: si espera respuesta, hace cuánto (oro); si tiene algo hoy o mañana, eso (verde
     *  «visita 17:30»); si no, la última vez que pasó algo. */
    fun cuando(c: Contacto, ahora: Instant = Instant.now()): Pair<String, Tono?> {
        c.esperaDesde?.let { e -> return Formato.hace(e, ahora).replace("hace ", "") to Tono.oro }
        val p = c.proximaCitaEn
        if (p != null) {
            val visita = (c.proximaCitaTitulo ?: "").lowercase().startsWith("visita")
            if (Formato.mismoDia(p, ahora)) return "${if (visita) "visita" else "hoy"} ${Formato.horaDe(p)}" to Tono.verde
            if (Formato.diaDe(p) == Formato.diaDe(ahora).plusDays(1)) return "mañana ${Formato.horaDe(p)}" to null
        }
        val a = c.actividadEn ?: c.actualizadoEn ?: c.creadoEn ?: return "" to null
        if (Formato.mismoDia(a, ahora)) return "hoy" to null
        val dias = CuentasInicio.diasEntre(a, ahora)
        if (dias == 1) return "ayer" to null
        if (dias in 2..6) return "hace $dias d" to null
        return Formato.hace(a, ahora) to null
    }
}

@Serializable
data class NotaContacto(val id: String, val tipo: String? = null, val texto: String, val ocurrioEn: FechaApi, val autorId: String? = null)

/** Una propiedad que le puede interesar a un contacto (GET /v1/contactos/{id}/propiedades): las que
 *  ya mira (con operación o visita) y las de la cartera que coinciden con lo que busca. */
@Serializable
data class PropiedadDeContacto(
    val propiedadId: String,
    val titulo: String? = null,
    val direccion: String? = null,
    val barrio: String? = null,
    val precio: Double? = null,
    val moneda: String? = null,
    val fotoId: String? = null,
    /** La etapa de su operación (nuevo… cierre), «visita» si sólo tiene una visita, «nueva» si es una coincidencia. */
    val estado: String? = null,
    val visitaEn: FechaApi? = null,
    val operacionId: String? = null,
    val puntaje: Double? = null,
    val enviadaEn: FechaApi? = null,
    val reservaVenceEl: String? = null,
) {
    val id: String get() = propiedadId
    val nombre: String get() = titulo?.nonEmpty ?: direccion ?: "Propiedad"
    val precioTexto: String get() = precio?.let { Formato.plata(it, moneda ?: "USD") } ?: "Consultar precio"

    /** Lo que pasa con esa propiedad para la persona, como la web: «Visita hoy 17:30», «Reservó ·
     *  vence en 2 días», «Cerrando», «Nueva · no se la mandaste», «Ya la visitó», «En charla». */
    val estadoTexto: Pair<String, Tono?>
        get() {
            val v = visitaEn
            if (v != null) {
                val cuando = if (esHoyEnBA(v)) "hoy" else if (Formato.diaDe(v) == Formato.hoy().plusDays(1)) "mañana" else Formato.dia(v)
                return "Visita $cuando ${Formato.horaDe(v)}" to Tono.verde
            }
            return when (estado) {
                "reserva" -> (Operacion.venceSena(reservaVenceEl)?.let { "Reservó · ${it.first}" } ?: "Reservó") to Tono.oro
                "cierre" -> "Cerrando" to Tono.verde
                "nueva", null -> (enviadaEn?.let { "Se la mandaste ${Formato.hace(it)}" } ?: "Nueva · no se la mandaste") to null
                "visita" -> "Ya la visitó" to null
                else -> "En charla" to null
            }
        }

    /** La miniatura: las fotos de las propiedades son públicas (/publico/archivos/{inmobiliaria}/{id}). */
    fun miniatura(api: String, inmobiliaria: String?): String? = urlFotoPropiedad(fotoId, api, inmobiliaria)
}

/** Un contacto de la agenda del teléfono (CNContact en iOS). */
data class ContactoAgenda(val nombre: String, val telefono: String?, val email: String?)

/** La agenda del teléfono para elegir un contacto (SelectorAgenda en iOS): el selector del sistema, sin pedir permiso.
 *  Devuelve nombre, teléfono y mail (el mail sólo si ya se dio permiso a los contactos). */
@Composable
fun rememberContactos(alElegir: (String, String, String) -> Unit): () -> Unit {
    val contexto = LocalContext.current
    val l = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        var nombre = ""
        var telefono = ""
        var idContacto: String? = null
        contexto.contentResolver.query(
            uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                nombre = c.getString(0).orEmpty()
                telefono = c.getString(1).orEmpty()
                idContacto = c.getString(2)
            }
        }
        var email = ""
        val idc = idContacto
        if (idc != null && ContextCompat.checkSelfPermission(contexto, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                contexto.contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?", arrayOf(idc), null,
                )?.use { c -> if (c.moveToFirst()) email = c.getString(0).orEmpty() }
            }
        }
        alElegir(nombre, telefono, email)
    }
    return { l.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
}

/** La agenda del teléfono para elegir varios contactos de una vez («Importar del celular»). En iOS es el selector del
 *  sistema; Android no trae uno para varios, así que se lista la agenda (con permiso) y se tildan. Va dentro de una Hoja. */
@Composable
fun SelectorAgendaVarios(alElegir: (List<ContactoAgenda>) -> Unit) {
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    var permiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    var todos by remember { mutableStateOf<List<ContactoAgenda>?>(null) }
    var elegidos by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var q by remember { mutableStateOf("") }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> permiso = ok }
    // El permiso se pide recién después de explicar qué se sube (divulgación destacada de Play), no al abrir.
    LaunchedEffect(permiso) {
        if (!permiso) return@LaunchedEffect
        todos = withContext(Dispatchers.IO) {
            val porId = linkedMapOf<String, Triple<String, String?, String?>>()
            runCatching {
                ctx.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        if (id !in porId) porId[id] = Triple(c.getString(1).orEmpty(), c.getString(2), null)
                    }
                }
                ctx.contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val x = porId[id] ?: continue
                        if (x.third == null) porId[id] = x.copy(third = c.getString(1))
                    }
                }
            }
            porId.values.map { ContactoAgenda(it.first, it.second, it.third) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        BarraHoja(titulo = "Contactos", izquierda = { BotonHoja("Cancelar", alTocar = { cerrar() }) }) {
            Texto(
                if (elegidos.isEmpty()) "Listo" else "Listo (${elegidos.size})", Estilo.headline, C.accion,
                Modifier.clickable(enabled = elegidos.isNotEmpty()) {
                    val l = todos ?: emptyList()
                    alElegir(elegidos.sorted().mapNotNull { l.getOrNull(it) })
                    cerrar()
                },
            )
        }
        if (!permiso) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(
                    icono = "person.crop.circle", titulo = "Traé contactos de tu agenda",
                    texto = "Vas a ver tu agenda para elegir a quiénes traer. Sólo los que tildes se suben a tu cartera de Corredor; el resto no sale del teléfono.",
                    accion = "Seguir" to { pedir.launch(Manifest.permission.READ_CONTACTS) },
                )
            }
            return@Column
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) { Buscador(placeholder = "Buscar", texto = q, alCambiar = { q = it }) }
        val l = todos
        if (l == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Rueda() }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val f = MasRaizNormal(q)
                l.forEachIndexed { i, x ->
                    if (f.isEmpty() || MasRaizNormal(x.nombre).contains(f) || (x.telefono ?: "").contains(f)) {
                        key(i) {
                            val on = i in elegidos
                            Tocable(alTocar = { elegidos = if (on) elegidos - i else elegidos + i }) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icono(if (on) "checkmark.circle.fill" else "circle", 22f, if (on) C.accion else C.texto3)
                                    Column(Modifier.weight(1f)) {
                                        Texto(x.nombre, Estilo.filaLista, C.texto, lineas = 1)
                                        x.telefono?.let { Texto(it, Estilo.footnote, C.texto2, lineas = 1) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sin acentos ni mayúsculas (MasRaiz.normal en iOS). */
internal fun MasRaizNormal(t: String): String =
    java.text.Normalizer.normalize(t.trim(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

// MARK: - 9 Operaciones

/** Una operación del tablero (operaciones.Operacion). */
@Serializable
data class Operacion(
    val id: String,
    val titulo: String? = null,
    val etapa: String,
    val valor: Double? = null,
    val moneda: String? = null,
    val contactoId: String? = null,
    val propiedadId: String? = null,
    val agenteId: String? = null,
    val contactoNombre: String? = null,
    val propiedadTitulo: String? = null,
    val agenteNombre: String? = null,
    val creadaEn: FechaApi? = null,
    val actualizadoEn: FechaApi? = null,
    val orden: Double? = null,
    val sena: Double? = null,
    val senaMoneda: String? = null,
    val senaVenceEl: String? = null,
    val caidaEn: FechaApi? = null,
    val caidaMotivo: String? = null,
    val honorariosComprador: Double? = null,
    val honorariosVendedor: Double? = null,
    val comisionAgente: Double? = null,
    /** Lo tildado para el cierre: clave del paso (dominio, credito, aceptacion, boleto, garantia,
     *  documentacion, firma) → cuándo y quién. */
    val checklist: Map<String, Tildado>? = null,
    /** Los ids de los archivos adjuntos (GET /v1/archivos/{id} da el nombre). */
    val adjuntos: List<String>? = null,
    val propiedadOperacion: String? = null,
    val propiedadBarrio: String? = null,
    val propiedadPrecio: Double? = null,
    val propiedadMoneda: String? = null,
    val propiedadFotoId: String? = null,
    val contactoTelefono: String? = null,
    val senaRecibo: String? = null,
    val propietarioId: String? = null,
    val propietarioNombre: String? = null,
    val propietarioTelefono: String? = null,
    val proximaCita: CitaOp? = null,
    val ultimaCita: CitaOp? = null,
    val reserva: Reserva? = null,
) {
    /** Un paso tildado de la lista del cierre: cuándo y quién. */
    @Serializable data class Tildado(val en: FechaApi? = null, val por: String? = null)
    /** Una cita de la operación (la próxima o la última). */
    @Serializable data class CitaOp(val tipo: String? = null, val titulo: String? = null, val empiezaEn: FechaApi? = null, val estado: String? = null)
    /** La reserva formal de la propiedad hecha desde la operación. */
    @Serializable data class Reserva(val id: String? = null, val venceEl: String? = null, val recibo: String? = null)

    /** Un paso de «Para pasar a Cierre», con el texto que le pone el panel. */
    data class Paso(
        val clave: String, val texto: String, val hecho: Boolean, val auto: Boolean = false, val detalle: String? = null,
        /** «Llamar a Jorge» en la aceptación del propietario: (texto, teléfono). */
        val llamar: Pair<String, String>? = null,
    ) {
        override fun equals(other: Any?): Boolean = other is Paso && other.clave == clave && other.hecho == hecho && other.texto == texto && other.detalle == detalle
        override fun hashCode(): Int = clave.hashCode() * 31 + hecho.hashCode()
    }

    data class Columna(val clave: String, val texto: String, val etapas: List<String>, val tono: Tono)

    val siguiente: String?
        get() {
            val i = etapas.indexOfFirst { it.first == etapa }
            return if (i >= 0 && i + 1 < etapas.size) etapas[i + 1].first else null
        }

    val nombre: String get() = propiedadTitulo ?: titulo ?: "Operación"

    /** «Venta · Belgrano». */
    val tipoTexto: String
        get() = listOfNotNull(mapOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario")[propiedadOperacion ?: ""], propiedadBarrio).joinToString(" · ")

    /** La nota de la tarjeta: lo próximo agendado, la seña, lo último que pasó o hace cuánto que entró.
     *  El Boolean es la alerta (va en oro). */
    fun nota(ahora: Instant = Instant.now()): Pair<String, Boolean> {
        val citas = mapOf("visita" to "visita", "firma" to "firma", "tasacion" to "tasación", "llamado" to "llamado", "otro" to "")
        val p = proximaCita
        val pen = p?.empiezaEn
        if (p != null && pen != null) {
            val que = (citas[p.tipo ?: ""] ?: "").nonEmpty ?: "agenda"
            if (Formato.mismoDia(pen, ahora)) return "$que hoy ${Formato.horaDe(pen)}" to true
            if (Formato.diaDe(pen) == Formato.diaDe(ahora).plusDays(1)) return "$que mañana ${Formato.horaDe(pen)}" to false
        }
        if (etapa == "reserva") {
            val vence = venceSena(reserva?.venceEl ?: senaVenceEl, ahora)
            val s = sena
            if (s != null) return "seña ${Formato.plata(s, senaMoneda ?: moneda ?: "USD")}${vence?.let { " · ${it.first}" } ?: ""}" to (vence?.second ?: false)
            return "seña pendiente" to true
        }
        val u = ultimaCita
        val uen = u?.empiezaEn
        if (u != null && u.tipo == "visita" && uen != null && Formato.mismoDia(uen, ahora)) return "visitó hoy" to false
        if (p != null && pen != null) return "${(citas[p.tipo ?: ""] ?: "").nonEmpty ?: "agenda"} ${Formato.hace(pen)}" to false
        val c = creadaEn
        if ((etapa == "nuevo" || etapa == "contactado") && c != null) {
            if (Duration.between(c, ahora).seconds < 86_400) return "entró ${Formato.hace(c)}" to (etapa == "nuevo")
            if (etapa == "contactado") return "contactado" to false
        }
        return "sin movimiento ${actualizadoEn?.let { Formato.hace(it) } ?: ""}" to false
    }

    /** «Para pasar a Cierre» según sea venta o alquiler (los mismos pasos y textos que el panel,
     *  lib/crm/etapas.ts). La seña se tilda sola al cargarla; lo demás, a mano. */
    fun pasosCierre(etiquetas: List<String>): List<Paso> {
        val hecho = { k: String -> checklist?.get(k) != null }
        val banco = etiquetas.firstOrNull { it.contains("banco", ignoreCase = true) }
            ?.replace(Regex("^pre-?aprobad[oa]\\s+", RegexOption.IGNORE_CASE), "")
        val recibo = reserva?.recibo ?: senaRecibo
        val s = this.sena
        val pasoSena = Paso(
            clave = "sena", texto = s?.let { "Seña ${Formato.plata(it, senaMoneda ?: moneda ?: "USD")} recibida" } ?: "Seña recibida",
            hecho = s != null, auto = true, detalle = if (s != null) recibo?.let { "recibo $it" } else null,
        )
        val tel = propietarioTelefono?.filter { it.isDigit() || it == '+' }
        val dueno = propietarioNombre?.let { Charla.primerNombre(it) }
        var aceptacion = Paso(clave = "aceptacion", texto = "Aceptación del propietario", hecho = hecho("aceptacion"))
        if (tel != null && tel.isNotEmpty()) aceptacion = aceptacion.copy(llamar = (dueno?.let { "Llamar a $it" } ?: "Llamar al dueño") to tel)
        val l: MutableList<Paso> = if (propiedadOperacion == "alquiler" || propiedadOperacion == "temporario") mutableListOf(
            pasoSena, Paso("garantia", "Garantía aprobada", hecho("garantia")),
            Paso("documentacion", "Documentación del inquilino", hecho("documentacion")), aceptacion,
            Paso("firma", "Fecha de firma del contrato", hecho("firma")),
        ) else mutableListOf(
            pasoSena, Paso("dominio", "Informe de dominio", hecho("dominio")),
            Paso("credito", banco?.let { "Pre-aprobación del crédito ($it)" } ?: "Pre-aprobación del crédito", hecho("credito")), aceptacion,
            Paso("boleto", "Fecha de boleto y escribana", hecho("boleto")),
        )
        if (!l[l.size - 1].hecho && !aceptacion.hecho) l[l.size - 1] = l[l.size - 1].copy(detalle = "después de la aceptación")
        return l
    }

    /** Para el código que ya usaba `pasos` sin las etiquetas del contacto. */
    val pasos: List<Paso> get() = pasosCierre(etiquetas = emptyList())

    companion object {
        val etapas = listOf("nuevo" to "Nuevo", "contactado" to "Contactado", "visita" to "Visita", "reserva" to "Reserva", "cierre" to "Cierre")
        fun etapaTexto(e: String): String = etapas.firstOrNull { it.first == e }?.second ?: e.replaceFirstChar { it.uppercase() }

        /** Las cuatro columnas del tablero de la web: «Nueva» junta nuevo y contactado. */
        val columnas: List<Columna> = listOf(
            Columna("nuevo", "Nueva", listOf("nuevo", "contactado"), Tono.azul), Columna("visita", "Visita", listOf("visita"), Tono.verde),
            Columna("reserva", "Reserva", listOf("reserva"), Tono.oro), Columna("cierre", "Cierre", listOf("cierre"), Tono.neutro),
        )
        fun columna(etapa: String): Int = maxOf(0, columnas.indexOfFirst { etapa in it.etapas }.let { if (it < 0) 0 else it })
        fun textoColumna(etapa: String): String = columnas[columna(etapa)].texto
        /** Los motivos de «Se cayó». */
        val motivos = listOf("Precio", "Eligió otra", "No le dieron el crédito", "Dejó de responder", "El dueño la bajó")

        /** Cuándo vence la reserva: «vence en 7 días», «vence mañana», «vence hoy», «venció» (con alerta desde el día anterior). */
        fun venceSena(f: String?, ahora: Instant = Instant.now()): Pair<String, Boolean>? {
            val d = Fecha.fecha(f) ?: return null
            val n = CuentasInicio.diasEntre(Formato.diaDe(ahora), d)
            if (n < 0) return "venció" to true
            if (n == 0) return "vence hoy" to true
            if (n == 1) return "vence mañana" to true
            return "vence en $n días" to false
        }

        /** Lo que suma en dólares (los alquileres en pesos no entran en el «en juego»). */
        fun enJuego(ops: List<Operacion>): Double = ops.filter { (it.moneda ?: "USD") == "USD" }.sumOf { it.valor ?: 0.0 }
    }
}

/** Un evento del historial de una operación (GET /v1/operaciones/{id}/historial). */
@Serializable
data class EventoOperacion(
    val id: String,
    val tipo: String,
    val texto: String? = null,
    val datos: JsonElement? = null,
    val autorId: String? = null,
    val autorNombre: String? = null,
    val creadoEn: FechaApi,
) {
    private fun dato(k: String): String? {
        val p = (datos as? JsonObject)?.get(k) as? JsonPrimitive ?: return null
        if (p.isString) return p.content
        return p.doubleOrNull?.toString()
    }

    /** Lo que se lee, como en el panel: «Pasó a Visita · Sofía», «La lleva Sofía», «Se cayó · Precio». */
    fun frase(nombres: Map<String, String>): String {
        val quien = autorNombre?.let { " · ${Charla.primerNombre(it)}" } ?: ""
        return when (tipo) {
            "alta" -> "Entró en ${Operacion.textoColumna(dato("etapa") ?: "nuevo")}"
            "etapa" -> "Pasó a ${Operacion.textoColumna(dato("hasta") ?: "")}$quien"
            "nota" -> (texto ?: "") + quien
            "asignacion" -> {
                val h = dato("hasta") ?: return "Quedó sin agente"
                "La lleva ${nombres[h]?.let { Charla.primerNombre(it) } ?: "otra persona"}"
            }
            "caida" -> "Se cayó · ${dato("motivo") ?: ""}"
            "reabierta" -> "Volvió al tablero"
            "sena" -> {
                val n = ((datos as? JsonObject)?.get("monto") as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
                if (n != null) "Dejó seña de ${Formato.plata(n, dato("moneda") ?: "USD")}" else "Dejó seña"
            }
            else -> texto ?: tipo
        }
    }

    val descripcion: String get() = frase(nombres = emptyMap())
}

/** El texto antes del ícono («Pasar a boleto →»): el `LabelStyle` TextoDespues de iOS. */
@Composable
fun TextoDespues(texto: String, simbolo: String, estilo: Estilo, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Texto(texto, estilo, color)
        Icono(simbolo, estilo.tamano, color)
    }
}

/** La foto de una propiedad por su id (las fotos son públicas: /publico/archivos/{inmobiliaria}/{id}). */
fun urlFotoPropiedad(fotoId: String?, api: String, inmobiliaria: String?): String? {
    if (fotoId == null || inmobiliaria == null) return null
    return api.trimEnd('/') + "/publico/archivos/$inmobiliaria/$fotoId" + "?tam=chica"
}
