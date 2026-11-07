package ar.com.corredor.app.pantallas.entrada

import ar.com.corredor.app.pantallas.web.achicada

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.Archivo
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.Miembro
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.nucleo.nonEmpty
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.nucleo.subir
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

// Clon de Pantallas/Entrada/Bienvenida.swift.

/** Cómo aparece en la web de la inmobiliaria (GET/PUT /v1/perfil/web) (`Bienvenida.EnLaWeb` en iOS). */
@Serializable
data class EnLaWeb(val fotoId: String? = null, val enWeb: Boolean = true, val presentacion: String? = null)

@Serializable
private data class PerfilWhatsApp(val whatsapp: String? = null)

@Serializable
private data class PropId(val id: String)

@Serializable
private data class CuerpoWhatsApp(val whatsapp: String)

/** La bienvenida de quien se suma a un equipo (su alta corta, la misma que el panel web en /bienvenida): la foto y el
 *  WhatsApp con los que aparece en la web y en las fichas, los avisos y la apariencia. Todo se puede dejar para después;
 *  al cerrarla sigue el recorrido. */
@Composable
fun Bienvenida() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var enLaWeb by remember { mutableStateOf(EnLaWeb()) }
    var subiendo by remember { mutableStateOf(false) }
    var whatsapp by remember { mutableStateOf("") }
    var guardado by remember { mutableStateOf("") }
    /** Los avisos del teléfono prendidos (el UNAuthorizationStatus de iOS). */
    var permiso by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var pedido by remember { mutableStateOf(false) }
    /** Cuántas propiedades le asignaron y quién (el dueño o la dueña del equipo). */
    var propias by remember { mutableIntStateOf(0) }
    var quienAsigna by remember { mutableStateOf("") }
    /** La foto subida (GET /v1/archivos/{id} da su dirección pública). */
    var urlFoto by remember { mutableStateOf<String?>(null) }

    /** «Hola, Martín. Esto es lo tuyo hoy.» */
    val saludo = run {
        val n = (estado.usuario?.nombre?.nonEmpty ?: estado.usuario?.email ?: "").split(" ").firstOrNull { it.isNotEmpty() } ?: ""
        "Hola, $n. Esto es lo tuyo hoy."
    }

    suspend fun guardarWhatsApp() {
        val w = whatsapp.trim()
        try {
            estado.api.hacer("PATCH", "/v1/perfil", CuerpoWhatsApp(w))
            guardado = whatsapp
            estado.avisar("Guardamos tu WhatsApp")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Guarda el WhatsApp si quedó escrito sin guardar, y sigue (al recorrido). */
    suspend fun empezar() {
        if (whatsapp.isNotEmpty() && guardado != whatsapp) guardarWhatsApp()
        cerrar()
    }

    suspend fun avisos() {
        if (pedido && !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            // Ya dijo que no: sólo se cambia en los ajustes del teléfono.
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(i) }
            return
        }
        pedido = true
        estado.pedirPermisoPush()
        permiso = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Sus propiedades (Propiedades, «Mías»). */
    fun verCartera() {
        cerrar()
        estado.carteraDe = "yo"
        estado.pestana = Pestana.propiedades
    }

    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            subiendo = true
            try {
                val jpg = withContext(Dispatchers.IO) {
                    val d = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                    val img = BitmapFactory.decodeByteArray(d, 0, d.size) ?: return@withContext null
                    ByteArrayOutputStream().also { achicada(img, 1200).compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                }
                if (jpg != null) {
                    val a = estado.api.subir(jpg, "foto.jpg", "image/jpeg", "foto")
                    val w = enLaWeb.copy(fotoId = a.id)
                    estado.api.hacer("PUT", "/v1/perfil/web", w)
                    enLaWeb = w
                    urlFoto = a.url
                    Haptico.exito()
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            subiendo = false
        }
    }

    LaunchedEffect(Unit) {
        estado.api.opcional<PerfilWhatsApp>("/v1/perfil")?.let { whatsapp = it.whatsapp ?: ""; guardado = whatsapp }
        estado.api.opcional<EnLaWeb>("/v1/perfil/web")?.let { w ->
            enLaWeb = w
            w.fotoId?.let { id -> estado.api.opcional<Archivo>("/v1/archivos/$id")?.let { urlFoto = it.url } }
        }
        permiso = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        estado.api.opcional<List<PropId>>("/v1/propiedades", listOf("agente" to "yo", "limite" to "200"))?.let { propias = it.size }
        estado.api.opcional<List<Miembro>>("/v1/equipo")?.let { eq ->
            quienAsigna = eq.firstOrNull { it.rol == "duenio" && it.usuarioId != estado.usuario?.id }?.nombre ?: ""
        }
    }

    /** Una tarea de la bienvenida: ícono en su tono, qué es y para qué. */
    @Composable
    fun Tarea(icono: String, tono: Tono, titulo: String, detalle: String, hacer: () -> Unit) {
        Tocable(hacer) {
            Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoTono(icono, tono, tam = 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto(titulo, Estilo.filaLista, C.texto)
                    Texto(detalle, Estilo.footnote, C.texto2)
                }
                Icono("chevron.right", 13f, C.texto3)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FondoApp(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // «Saltear» (ToolbarItem .cancellationAction).
            Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp)) {
                Box(Modifier.align(Alignment.CenterStart).heightIn(min = 44.dp).clickable(onClick = cerrar).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Texto("Saltear", Estilo.body, C.accion)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Texto((estado.inmobiliaria?.nombre ?: "").uppercase(), Estilo(13f, Peso.bold, 0.6f / 13f), C.oroTinta)
                    Texto(saludo, Estilo.tituloPaso, C.texto)
                }
                Tocable({ elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, habilitado = !subiendo) {
                    Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                            val u = urlFoto ?: estado.usuario?.foto
                            if (u != null) {
                                // La subida o, si no subió otra, la de la cuenta.
                                Rueda()
                                coil3.compose.AsyncImage(model = u, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            } else {
                                Box(Modifier.matchParentSize().background(C.verde700), contentAlignment = Alignment.Center) {
                                    Texto(Formato.iniciales(estado.usuario?.completo ?: "C"), Estilo(22f, Peso.bold), C.oro)
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(if (subiendo) "Subiendo…" else "Tu foto", Estilo.filaLista, C.texto)
                            Texto("Sale en la web y en las fichas que mandás", Estilo.footnote, C.texto2)
                        }
                        Icono("chevron.right", 13f, C.texto3)
                    }
                }
                Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Texto("Tu WhatsApp", Estilo.filaLista, C.texto)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CampoCorredor(whatsapp, { whatsapp = it }, "11 5555 0000", Modifier.weight(1f), teclado = KeyboardType.Phone)
                        BotonSecundario(if (guardado == whatsapp && whatsapp.isNotEmpty()) "Listo" else "Guardar", { alcance.launch { guardarWhatsApp() } },
                            habilitado = !(whatsapp.trim().isEmpty() || guardado == whatsapp))
                    }
                    Texto("Ahí te escriben por las propiedades que tenés.", Estilo.footnote, C.texto2)
                }
                if (!permiso) {
                    Tarea("bell.badge", Tono.oro, "Activá los avisos", "Para que no se te escape una consulta") { alcance.launch { avisos() } }
                }
                if (propias > 0) {
                    Tarea("house", Tono.verde, "${quienAsigna.nonEmpty ?: "Tu equipo"} te asignó ${Formato.cantidad(propias, "propiedad", "propiedades")}", "Miralas en 1 minuto") { verCartera() }
                } else {
                    Tarea("house", Tono.verde, "Mirá la cartera", "Lo que no tiene dueño también es tuyo") { verCartera() }
                }
                Tarea("camera", Tono.neutro, "Probá MIRA con un cartel", "Tres toques, sin escribir") {
                    cerrar()
                    estado.miraAbierta = true
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Texto("Apariencia", Estilo.filaLista, C.texto)
                    Segmentado(listOf("sistema" to "Automático", "claro" to "Claro", "oscuro" to "Oscuro"), estado.tema, { estado.tema = it })
                }
            }
            // .safeAreaInset(edge: .bottom)
            Column(Modifier.fillMaxWidth().background(C.fondo.copy(alpha = 0.92f)).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BotonPrincipal("Empezar", { alcance.launch { empezar() } })
                Texto("Podés saltearlo: queda en tu Inicio.", Estilo.footnote, C.texto2)
            }
        }
    }
}
