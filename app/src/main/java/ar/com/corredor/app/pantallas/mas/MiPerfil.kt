package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.pantallas.web.achicada

import ar.com.corredor.app.app.PantallaApilada

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import ar.com.corredor.app.BuildConfig
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.entrada.ElegirInmobiliaria
import ar.com.corredor.app.pantallas.inicio.BorrarCuenta
import ar.com.corredor.app.tema.*
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import kotlin.math.max

// Clon de Pantallas/Mas/MiPerfil.swift.

private fun String?.lleno(): String? = this?.takeIf { it.isNotBlank() }

// MARK: - Modelos (formas de la API)

/** GET /v1/perfil (equipo.Miembro): lo de la persona que no viene de Clerk. */
@Serializable
data class DatosPerfil(
    val usuarioId: String? = null,
    val email: String? = null,
    val nombre: String? = null,
    val apellido: String? = null,
    val foto: String? = null,
    val telefono: String? = null,
    val whatsapp: String? = null,
    val rol: String? = null,
)

/** GET/PUT /v1/perfil/web (equipo.EnLaWeb): cómo aparece en la web de la inmobiliaria. */
@Serializable
data class EnLaWebPerfil(
    val fotoId: String? = null,
    val enWeb: Boolean = false,
    val presentacion: String? = null,
)

/** GET/PUT /v1/perfil/firma (equipo.Firma): la firma en mensajes, por inmobiliaria. */
@Serializable
data class FirmaPerfil(
    val texto: String? = null,
    val activa: Boolean = false,
    val sugerida: String = "",
)

/** GET /v1/auth/sesiones (cuentas.SesionAbierta). */
@Serializable
data class SesionAbiertaPerfil(
    val id: String,
    val actual: Boolean,
    val agente: String? = null,
    val ip: String? = null,
    val creadaEn: FechaApi,
    val vistoEn: FechaApi? = null,
) {
    /** «Este iPhone», «Chrome en Mac»… desde el navegador (o la app) que anotó la sesión. */
    val dispositivo: Pair<String, String>
        get() {
            val ua = agente ?: ""
            fun tiene(p: String) = Regex(p).containsMatchIn(ua)
            // La app de iPhone manda «Corredor/… CFNetwork/… Darwin/…»; la de Android, la de OkHttp.
            val appAndroid = tiene("okhttp")
            val app = tiene("CFNetwork|Darwin|Corredor") || appAndroid
            val cel = app || tiene("iPhone|Android.*Mobile|Mobile")
            val equipo = when {
                appAndroid -> "Android"
                tiene("iPhone") || (app && !tiene("iPad")) -> "iPhone"
                tiene("iPad") -> "iPad"
                tiene("Android") -> "Android"
                tiene("Mac OS X") -> "Mac"
                tiene("Windows") -> "Windows"
                tiene("Linux") -> "Linux"
                else -> ""
            }
            val nav = when { tiene("Edg/") -> "Edge"; tiene("Chrome/") -> "Chrome"; tiene("Firefox/") -> "Firefox"; tiene("Safari/") -> "Safari"; else -> "" }
            val titulo = when {
                actual -> if (cel && equipo.isNotEmpty()) "Este $equipo" else "Este navegador"
                nav.isNotEmpty() && equipo.isNotEmpty() -> "$nav en $equipo"
                app -> if (appAndroid) "Corredor en Android" else "Corredor en iPhone"
                else -> equipo.lleno() ?: nav.lleno() ?: "Otro dispositivo"
            }
            return titulo to (if (cel) "iphone" else "laptopcomputer")
        }
}

/** Cómo están los avisos en este teléfono. */
enum class EstadoPushDispositivo { activo, inactivo, bloqueado }

// MARK: - Mi perfil

@Composable
private fun Linea() { Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea)) }

/** Mi perfil (07 · 6C, como /perfil de la web): la foto, el rol, los datos de contacto que van en la
 *  web y las fichas, la firma, «En la web», los avisos (los de este teléfono, con la prueba), el
 *  recorrido, las sesiones abiertas, las inmobiliarias (cambiar, crear otra, aceptar una invitación),
 *  la huella o la cara, la apariencia, salir y borrar la cuenta. */
@Composable
fun MiPerfil() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var faceID by remember { mutableStateOf(estado.conFaceID) }
    var perfil by remember { mutableStateOf<DatosPerfil?>(null) }
    var web by remember { mutableStateOf(EnLaWebPerfil()) }
    var firma by remember { mutableStateOf<FirmaPerfil?>(null) }
    var avisosActivos by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var cargado by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf(false) }
    var editandoWeb by remember { mutableStateOf(false) }
    var editandoFirma by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf(false) }
    var push by remember { mutableStateOf<EstadoPushDispositivo?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    var urlFoto by remember { mutableStateOf<String?>(null) }

    val nombre = listOfNotNull(perfil?.nombre, perfil?.apellido).filter { it.isNotEmpty() }.joinToString(" ").lleno()
        ?: perfil?.email ?: estado.usuario?.completo ?: ""
    val esLector = estado.rol == "lector"

    fun mirarPush() {
        val habilitados = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        push = when {
            habilitados -> if (estado.pushApagado) EstadoPushDispositivo.inactivo else EstadoPushDispositivo.activo
            // Antes de Android 13 no hay permiso que pedir: si están apagados, es desde los ajustes.
            Build.VERSION.SDK_INT < 33 -> EstadoPushDispositivo.bloqueado
            else -> EstadoPushDispositivo.inactivo
        }
    }

    @Serializable data class Tipo(val push: Boolean? = null, val mail: Boolean? = null)
    @Serializable data class Prefs(val tipos: List<Tipo>? = null)

    suspend fun cargar() {
        try {
            val p: DatosPerfil = estado.api.get("/v1/perfil")
            perfil = p
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            error = e
            if (perfil == null) return
        } catch (_: Exception) { return }
        // Lo demás, cada cosa por su lado: si falla, la fila sale con lo que hay (como la web).
        estado.api.opcional<Prefs>("/v1/avisos/preferencias")?.let { pr ->
            avisosActivos = (pr.tipos ?: emptyList()).count { it.push == true || it.mail == true }
        }
        estado.api.opcional<EnLaWebPerfil>("/v1/perfil/web")?.let { w ->
            web = w
            val id = w.fotoId
            if (id != null) estado.api.opcional<Archivo>("/v1/archivos/$id")?.let { urlFoto = it.url }
        }
        firma = estado.api.opcional<FirmaPerfil>("/v1/perfil/firma") ?: FirmaPerfil()
        mirarPush()
        cargado = true
    }

    suspend fun activarPush() {
        if (push == EstadoPushDispositivo.bloqueado) {
            mirarPush()
            return
        }
        estado.prenderPush()
        mirarPush()
        // Pidió el permiso y se lo negaron: quedan bloqueados (se prenden desde los ajustes).
        if (push != EstadoPushDispositivo.activo && !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) push = EstadoPushDispositivo.bloqueado
    }

    suspend fun apagarPush() {
        try {
            estado.apagarPush()
            push = EstadoPushDispositivo.inactivo
            estado.avisar("Este dispositivo ya no recibe avisos") { estado.tareaDeshacer { activarPush() } }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            estado.avisar("No se guardó", ok = false)
        }
    }

    suspend fun probarPush() {
        try {
            estado.api.hacer("POST", "/v1/push/prueba")
            estado.avisar("Mandamos uno de prueba")
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            estado.avisar("No salió la prueba", ok = false)
        }
    }

    /** La foto nueva: se sube (uso foto: pública y con sus tamaños) y queda al toque, con «Deshacer». */
    fun subirFoto(u: Uri) {
        val antes = web
        val urlAntes = urlFoto
        alcance.launch {
            subiendo = true
            try {
                val datos = withContext(Dispatchers.IO) {
                    val b = ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
                    ByteArrayOutputStream().also { achicada(b, 1200).compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                }
                if (datos == null) {
                    estado.avisar("Elegí una foto (JPG, PNG o WebP).", ok = false)
                    return@launch
                }
                val a = estado.api.subir(datos, "foto.jpg", "image/jpeg", "foto")
                val w = web.copy(fotoId = a.id)
                estado.api.hacer("PUT", "/v1/perfil/web", w)
                web = w
                urlFoto = a.url
                Haptico.exito()
                estado.avisar("Listo: tu foto nueva") {
                    estado.tareaDeshacer {
                        try {
                            estado.api.hacer("PUT", "/v1/perfil/web", antes)
                            web = antes
                            urlFoto = urlAntes
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { estado.avisar("No se guardó", ok = false) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                estado.avisar(e)
            } finally { subiendo = false }
        }
    }

    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) subirFoto(u) }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Mi perfil") {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (!cargado && error != null) {
                    EstadoError("tu perfil", error!!) { alcance.launch { cargar() } }
                } else if (!cargado) {
                    EsqueletoFilas(6)
                } else {
                    // Cabeza
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val circulo: @Composable () -> Unit = {
                            Box(Modifier.size(72.dp).clip(CircleShape)) {
                                val u = urlFoto ?: (perfil?.foto ?: estado.usuario?.foto)
                                if (u != null) {
                                    SubcomposeAsyncImage(u, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Rueda() } })
                                } else {
                                    Box(Modifier.fillMaxSize().background(C.verde700), contentAlignment = Alignment.Center) {
                                        Texto(Formato.iniciales(nombre), Estilo(24f, Peso.bold), C.oro)
                                    }
                                }
                            }
                        }
                        if (esLector) circulo() else {
                            Box(Modifier.clickable(enabled = !subiendo) {
                                elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }.semantics { contentDescription = if (web.fotoId == null) "Subir tu foto" else "Cambiar tu foto" }) {
                                circulo()
                                val fondo = C.fondo
                                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(fondo).padding(2.dp).clip(CircleShape).background(C.accion),
                                    contentAlignment = Alignment.Center) {
                                    Icono(if (subiendo) "arrow.triangle.2.circlepath" else "camera.fill", 12f, C.sobreAccion, peso = Peso.bold)
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(nombre, Estilo(22f, Peso.bold), C.texto)
                            Texto("${ElegirInmobiliaria.rol(estado.rol)} en ${estado.inmobiliaria?.nombre ?: ""}", Estilo.texto14, C.texto2)
                        }
                    }
                    // Tus datos
                    Column(Modifier.fillMaxWidth().tarjeta()) {
                        FilaPerfil("Celular", perfil?.telefono.lleno() ?: "Agregalo") { editando = true }
                        Linea()
                        FilaPerfil("WhatsApp", perfil?.whatsapp.lleno() ?: perfil?.telefono.lleno() ?: "Agregalo") { editando = true }
                        Linea()
                        FilaPerfil("Mail", perfil?.email ?: estado.usuario?.email ?: "", hacer = null)
                        firma?.let { f ->
                            Linea()
                            FilaPerfil("Firma en mensajes", if (f.activa) (f.texto.lleno() ?: "Sin firma") else "Sin firma", apagado = !f.activa) { editandoFirma = true }
                        }
                        if (!esLector) {
                            Linea()
                            FilaPerfil("En la web", if (web.enWeb) (web.presentacion.lleno() ?: "Aparecés en Nosotros") else "No aparecés", verde = web.enWeb) { editandoWeb = true }
                        }
                        Linea()
                        FilaPerfil("Avisos", avisosActivos?.let { if (it == 1) "1 activo" else "$it activos" } ?: "Configurar", verde = true) { estado.abrir(AConfigAvisos) }
                    }
                    // Avisos en este dispositivo
                    push?.let { p ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).tarjeta().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                                Icono(if (p == EstadoPushDispositivo.activo) "bell.badge.fill" else "bell.slash", 20f, C.accion)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Texto("Avisos en este dispositivo", Estilo.filaLista, C.texto)
                                Texto(when (p) {
                                    EstadoPushDispositivo.activo -> "Te llegan aunque Corredor esté cerrado."
                                    EstadoPushDispositivo.bloqueado -> "Están bloqueados en este dispositivo."
                                    else -> "Para que no se te escape una consulta."
                                }, Estilo.footnote, C.texto2)
                            }
                            when (p) {
                                EstadoPushDispositivo.bloqueado -> BotonSecundario("Ver cómo", {
                                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                })
                                EstadoPushDispositivo.inactivo -> BotonSecundario("Activar", { alcance.launch { activarPush() } })
                                EstadoPushDispositivo.activo -> MenuBoton(listOf(
                                    OpcionDeMenu("Probar", "") { alcance.launch { probarPush() } },
                                    OpcionDeMenu("Apagar", "", destructiva = true) { alcance.launch { apagarPush() } },
                                ), "Probar o apagar los avisos") {
                                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 22f, C.accion) }
                                }
                            }
                        }
                    }
                    // Recorrido
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).tarjeta().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { Icono("map", 20f, C.accion) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto("Recorrido del panel", Estilo.filaLista, C.texto)
                            Texto(Formato.sinViudas("Dónde está cada cosa, en 5 pasos."), Estilo.footnote, C.texto2)
                        }
                        BotonSecundario("Verlo", { estado.mostrarRecorrido = true })
                    }
                    ListaSesiones()
                    // Inmobiliarias
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Texto("INMOBILIARIAS", Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.padding(horizontal = 4.dp))
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            (estado.sesion?.inmobiliarias ?: emptyList()).forEach { m ->
                                val esta = m.id == estado.sesion?.inmobiliariaId
                                Tocable({
                                    if (!esta) alcance.launch {
                                        try {
                                            estado.elegir(m)
                                            estado.avisar("Estás en ${m.nombre} como ${ElegirInmobiliaria.rol(m.rol).lowercase()}")
                                        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                                    }
                                }) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.hundida), contentAlignment = Alignment.Center) {
                                            Texto(m.nombre.take(1), Estilo.headline, C.texto)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Texto(m.nombre, Estilo(16f, Peso.semibold), C.texto)
                                            Texto("${ElegirInmobiliaria.rol(m.rol)} · ${m.slug}.${dominioWeb()}", Estilo.footnote, C.texto2, lineas = 1)
                                        }
                                        if (esta) Icono("checkmark", 17f, C.accion)
                                    }
                                }
                                Linea()
                            }
                            FilaIconoPerfil("plus", "Crear otra inmobiliaria") { estado.abrir(ASeccion("empezar")) }
                            Linea()
                            FilaIconoPerfil("envelope.open", "Aceptar una invitación") { estado.abrir(ASeccion("invitaciones")) }
                        }
                    }
                    // Ajustes
                    // En iOS van arriba el consejo de Siri y el botón de Atajos: el consejo va igual (habla de los
                    // atajos del ícono y el widget); el ShortcutsLink de Apple no tiene par en Android.
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ar.com.corredor.app.app.VistaConsejo(ar.com.corredor.app.app.ConsejoSiri())
                    Column(Modifier.fillMaxWidth().tarjeta()) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { faceID = !faceID; estado.ponerFaceID(faceID) }.padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("faceid", 17f, C.accion)
                            Texto("Desbloquear con tu huella o tu cara", Estilo.body16, C.texto, Modifier.weight(1f))
                            Llave(faceID, { faceID = it; estado.ponerFaceID(it) }, color = hex(0x2F9E6F), descripcion = "Desbloquear con tu huella o tu cara")
                        }
                        Linea()
                        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("circle.lefthalf.filled", 20f, C.accion) }
                            Texto("Apariencia", Estilo.body16, C.texto, Modifier.weight(1f))
                            SelectorMenu(listOf("sistema" to "Como el teléfono", "claro" to "Claro", "oscuro" to "Oscuro"), estado.tema, { estado.ponerTema(it) }, "Apariencia")
                        }
                        accesosPerfil.forEach { (icono, texto, destino) ->
                            Linea()
                            FilaIconoPerfil(icono, texto) { estado.abrir(destino) }
                        }
                    }
                    }
                    // Salir
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { alcance.launch { estado.salir() } }, contentAlignment = Alignment.Center) {
                                Texto("Salir", Estilo(16f, Peso.semibold), C.accion)
                            }
                            Linea()
                            Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { borrando = true }, contentAlignment = Alignment.Center) {
                                Texto("Borrar mi cuenta", Estilo(16f, Peso.semibold), C.error)
                            }
                        }
                        Texto(Formato.sinViudas("Al borrar tu cuenta se eliminan tus datos personales. Las operaciones y contratos quedan en la inmobiliaria."),
                            Estilo.footnote, C.texto3, Modifier.fillMaxWidth(), alinear = TextAlign.Center)
                        Texto("Corredor ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Estilo.footnote, C.texto3, Modifier.fillMaxWidth(), alinear = TextAlign.Center)
                    }
                }
            }
        }
    }

    Hoja(editando, { editando = false }, alturas = listOf(Altura.media, Altura.grande)) {
        HojaDatosContacto(perfil) { p -> perfil = p }
    }
    Hoja(editandoWeb, { editandoWeb = false }, alturas = listOf(Altura.media, Altura.grande)) {
        HojaEnLaWeb(web) { web = it }
    }
    Hoja(editandoFirma, { editandoFirma = false }, alturas = listOf(Altura.media, Altura.grande)) {
        firma?.let { f -> HojaFirmaMensajes(f) { firma = it } }
    }
    Hoja(borrando, { borrando = false }, alturas = listOf(Altura.media)) { BorrarCuenta() }
}

/** La web de las inmobiliarias (corredor.com.ar, sin app./panel./www.), `FormCrearInmobiliaria.dominio`. */
private fun dominioWeb(): String {
    val h = Uri.parse(Api.web().toString()).host ?: "corredor.com.ar"
    for (p in listOf("app.", "panel.", "www.")) if (h.startsWith(p)) return h.removePrefix(p)
    return h
}

private val accesosPerfil: List<Triple<String, String, Any>> = listOf(
    Triple("person.2", "Contactos", AContactos()), Triple("rectangle.split.3x1", "Operaciones", AOperaciones),
    Triple("doc.text", "Contratos y cobranzas", ACobranzas), Triple("checklist", "Inventarios", ASeccion("inventarios")),
    Triple("flag", "Objetivo del mes", ASeccion("objetivos")), Triple("bell", "Avisos", AAvisos()),
    Triple("gearshape", "Configuración, consorcios e informes", ASeccion("mas")),
)

/** Una fila «clave · valor ›» de la lista del perfil (6C). */
@Composable
private fun FilaPerfil(clave: String, valor: String, verde: Boolean = false, apagado: Boolean = false, hacer: (() -> Unit)?) {
    Tocable({ hacer?.invoke() }, habilitado = hacer != null) {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(clave, Estilo.body16, C.texto)
            Texto(valor, Estilo.texto14, if (verde) C.ok else if (apagado) C.texto3 else C.texto2, Modifier.weight(1f).padding(start = 8.dp), lineas = 1, alinear = TextAlign.End)
            if (hacer != null) Chevron()
        }
    }
}

@Composable
private fun FilaIconoPerfil(icono: String, texto: String, hacer: () -> Unit) {
    Tocable(hacer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono(icono, 20f, C.accion) }
            Texto(texto, Estilo.body16, C.texto, Modifier.weight(1f))
            Chevron()
        }
    }
}

/** Los botones de abajo de las hojas del perfil: «Cancelar» y «Guardar». */
@Composable
private fun BotonesHoja(guardando: Boolean, cancelar: () -> Unit, guardar: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BotonSecundario("Cancelar", cancelar, alto = 52.dp)
        BotonPrincipal(if (guardando) "Guardando…" else "Guardar", guardar, Modifier.weight(1f), habilitado = !guardando)
    }
}

// MARK: - Hojas

/** «Tus datos de contacto»: el celular y el WhatsApp (van en tu ficha de la web y en lo que mandás). */
@Composable
fun HojaDatosContacto(perfil: DatosPerfil?, alGuardar: (DatosPerfil) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var telefono by remember { mutableStateOf(perfil?.telefono ?: "") }
    var whatsapp by remember { mutableStateOf(perfil?.whatsapp ?: "") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    @Serializable data class Cuerpo(val telefono: String, val whatsapp: String)

    fun guardar() {
        val t = telefono.trim(); val w = whatsapp.trim()
        if (t.length > 40 || w.length > 40) { error = "El teléfono es demasiado largo."; return }
        guardando = true
        error = null
        alcance.launch {
            try {
                estado.api.hacer("PATCH", "/v1/perfil", Cuerpo(t, w))
                alGuardar((perfil ?: DatosPerfil()).copy(telefono = t.lleno(), whatsapp = w.lleno()))
                cerrar()
            } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                error = e.detalle.ifEmpty { e.titulo }
            } catch (_: Exception) {
                error = "No se guardó"
            } finally { guardando = false }
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Texto("Tus datos de contacto", Estilo.titulo, C.texto)
        Texto("Van en tu ficha de la web y en lo que mandás a los clientes.", Estilo.subhead, C.texto2)
        Etiquetado("Celular") { CampoCorredor(telefono, { telefono = it }, teclado = KeyboardType.Phone) }
        Etiquetado("WhatsApp (opcional)") {
            CampoCorredor(whatsapp, { whatsapp = it }, teclado = KeyboardType.Phone)
            Texto("Si es otro número que el celular.", Estilo.footnote, C.texto3)
        }
        error?.let { Texto(it, Estilo.footnote, C.error) }
        BotonesHoja(guardando, { cerrar() }) { guardar() }
    }
}

/** «Tu lugar en la web»: aparecer en «Nosotros» de la web de la inmobiliaria y cómo se presenta. */
@Composable
fun HojaEnLaWeb(web: EnLaWebPerfil, alCambiar: (EnLaWebPerfil) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var enWeb by remember { mutableStateOf(web.enWeb) }
    var presentacion by remember { mutableStateOf(web.presentacion ?: "") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun guardar() {
        val antes = web
        val nuevo = web.copy(enWeb = enWeb, presentacion = presentacion.trim().lleno())
        guardando = true
        error = null
        alcance.launch {
            try {
                estado.api.hacer("PUT", "/v1/perfil/web", nuevo)
                alCambiar(nuevo)
                cerrar()
                estado.avisar(if (nuevo.enWeb) "Listo: aparecés en Nosotros" else "Listo: ya no aparecés en la web") {
                    estado.tareaDeshacer {
                        try {
                            estado.api.hacer("PUT", "/v1/perfil/web", antes)
                            alCambiar(antes)
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { estado.avisar("No se guardó", ok = false) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                error = e.detalle.ifEmpty { e.titulo }
            } catch (_: Exception) {
                error = "No se guardó"
            } finally { guardando = false }
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Texto("Tu lugar en la web", Estilo.titulo, C.texto)
        Texto(Formato.sinViudas("Tu nombre, tu foto y cómo te presentás, en «Nosotros» de la web de la inmobiliaria."), Estilo.subhead, C.texto2)
        InterruptorConDetalle("Aparecer en Nosotros", Formato.sinViudas("Ni tu mail ni tu teléfono: sólo nombre, foto y rol."), enWeb) { enWeb = it }
        Etiquetado("Cómo te presentás (opcional)") {
            CampoCorredor(presentacion, { presentacion = it.take(60) })
            Texto("Por ejemplo: «Palermo y Belgrano» o «Corredora · Mat. 7215».", Estilo.footnote, C.texto3)
        }
        error?.let { Texto(it, Estilo.footnote, C.error) }
        BotonesHoja(guardando, { cerrar() }) { guardar() }
    }
}

/** Un Toggle con título y detalle (en `ok`). */
@Composable
private fun InterruptorConDetalle(titulo: String, detalle: String, prendido: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { alCambiar(!prendido) }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(titulo, Estilo.filaLista, C.texto)
            Texto(detalle, Estilo.footnote, C.texto2)
        }
        Llave(prendido, alCambiar, color = C.ok, descripcion = titulo)
    }
}

/** El «Deshacer» corre después de cerrada la hoja: lo lanza el alcance de la app, no el de la hoja. */
private fun ar.com.corredor.app.app.Estado.tareaDeshacer(bloque: suspend () -> Unit) {
    tarea { bloque() }
}

/** «Firma en mensajes»: el renglón que va al pie de los WhatsApp y los mails que se mandan desde
 *  Corredor. Si no hay, propone la sugerida y la activa; sin texto no queda activa. */
@Composable
fun HojaFirmaMensajes(firma: FirmaPerfil, alGuardar: (FirmaPerfil) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var activa by remember { mutableStateOf(if (firma.texto.lleno() != null) firma.activa else true) }
    var texto by remember { mutableStateOf(firma.texto ?: firma.sugerida) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    @Serializable data class Cuerpo(val texto: String?, val activa: Boolean)

    fun guardar() {
        val antes = firma
        val t = texto.trim().lleno()
        val nuevo = FirmaPerfil(texto = t, activa = activa && t != null, sugerida = firma.sugerida)
        guardando = true
        error = null
        alcance.launch {
            try {
                estado.api.hacer("PUT", "/v1/perfil/firma", Cuerpo(nuevo.texto, nuevo.activa))
                alGuardar(nuevo)
                cerrar()
                val volver = alGuardar
                estado.avisar(if (nuevo.activa) "Listo: tus mensajes van firmados" else "Listo: tus mensajes van sin firma") {
                    estado.tareaDeshacer {
                        try {
                            estado.api.hacer("PUT", "/v1/perfil/firma", Cuerpo(antes.texto, antes.activa))
                            volver(antes)
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { estado.avisar("No se guardó", ok = false) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                error = e.detalle.ifEmpty { e.titulo }
            } catch (_: Exception) {
                error = "No se guardó"
            } finally { guardando = false }
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Texto("Firma en mensajes", Estilo.titulo, C.texto)
        Texto(Formato.sinViudas("Va al pie de los WhatsApp y los mails que mandás desde Corredor."), Estilo.subhead, C.texto2)
        InterruptorConDetalle("Firmar mis mensajes", Formato.sinViudas("Dos renglones abajo de lo que escribís, sólo en esta inmobiliaria."), activa) { activa = it }
        Etiquetado("Firma") {
            CampoCorredor(texto, { texto = it.take(120) })
            if (firma.sugerida.isNotEmpty()) Texto("Por ejemplo: «${firma.sugerida}».", Estilo.footnote, C.texto3)
        }
        error?.let { Texto(it, Estilo.footnote, C.error) }
        BotonesHoja(guardando, { cerrar() }) { guardar() }
    }
}

// MARK: - Sesiones abiertas

/** «Sesiones abiertas» con nombre legible («Este Android», «Chrome en Mac»), «ahora / hace…» y
 *  «Cerrar» al toque (vuelve si falla). */
@Composable
fun ListaSesiones() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var sesiones by remember { mutableStateOf<List<SesionAbiertaPerfil>>(emptyList()) }
    var cerradas by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(estado.version) {
        estado.api.opcional<List<SesionAbiertaPerfil>>("/v1/auth/sesiones")?.let { sesiones = it }
    }

    fun cerrar(s: SesionAbiertaPerfil, titulo: String) {
        cerradas = cerradas + s.id
        alcance.launch {
            try {
                estado.api.hacer("DELETE", "/v1/auth/sesiones/${s.id}")
                estado.avisar("Cerramos la sesión de $titulo")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (e is ErrorApi && e.estado == 404) estado.avisar("Cerramos la sesión de $titulo")
                else {
                    cerradas = cerradas - s.id
                    estado.avisar("No se guardó", ok = false)
                }
            }
        }
    }

    val lista = sesiones.filter { it.id !in cerradas }
    if (lista.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto("Sesiones abiertas", Estilo(17f, Peso.bold), C.texto, Modifier.padding(horizontal = 4.dp))
            Column(Modifier.fillMaxWidth().tarjeta()) {
                lista.forEachIndexed { i, s ->
                    if (i > 0) Linea()
                    val (titulo, icono) = s.dispositivo
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (s.actual) C.okFondo else C.hundida), contentAlignment = Alignment.Center) {
                            Icono(icono, 20f, if (s.actual) C.accion else C.texto2)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(titulo, Estilo.filaLista, C.texto)
                            Texto(if (s.actual) "ahora" else Formato.hace(s.vistoEn ?: s.creadaEn), Estilo.footnote, C.texto2)
                        }
                        if (!s.actual) {
                            Box(Modifier.widthIn(min = 44.dp).heightIn(min = 44.dp).clickable { cerrar(s, titulo) }, contentAlignment = Alignment.Center) {
                                Texto("Cerrar", Estilo.subheadFuerte, C.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
