package ar.com.corredor.app.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import ar.com.corredor.app.BuildConfig
import ar.com.corredor.app.MainActivity
import ar.com.corredor.app.R
import ar.com.corredor.app.compartido.SesionCompartida
import ar.com.corredor.app.nucleo.AChat
import ar.com.corredor.app.nucleo.ACita
import ar.com.corredor.app.nucleo.APreguntaML
import ar.com.corredor.app.nucleo.Ruta
import ar.com.corredor.app.widgets.Widgets
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import ar.com.corredor.app.nucleo.Api
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Clon de App/Push.swift (el Delegado de APNs) con FCM sólo datos: registra el token (`POST /v1/push/dispositivos` con
// plataforma android), muestra los avisos con las mismas categorías y acciones (14B), abre la pantalla exacta al tocarlos
// (`ruta` del aviso) y resuelve la respuesta escrita desde el aviso (14C) sin abrir la app. Las claves son las que manda la
// API (integraciones/push/nativo.go › datosFCM y actividades.go › datosActividad):
//   aviso: titulo, cuerpo, ruta, tipo, categoria, avisoId, acciones (JSON: [{accion, titulo, metodo, ruta, cuerpo, entrada}]);
//   visita en curso: tipo «actividad», cita, empieza (RFC 3339), estado, terminar («1» o «0»).

/** FCM sin google-services.json (como Tengo Libre y Caigo): FirebaseApp se arma a mano con cr.firebase.* de
 *  local.properties. Sin esos datos el push queda apagado y la app anda igual. */
object Push {
    /** Los canales: los avisos de la inmobiliaria (alta) y la próxima visita en curso (sin sonido). */
    const val CANAL_AVISOS = "avisos"
    const val CANAL_VISITA = "visita"

    val disponible: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROYECTO.isNotBlank() && BuildConfig.FIREBASE_REMITENTE.isNotBlank()

    fun iniciar(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_AVISOS, "Avisos de la inmobiliaria", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Consultas nuevas, mensajes de WhatsApp, preguntas de Mercado Libre, cobros y visitas."
        })
        nm.createNotificationChannel(NotificationChannel(CANAL_VISITA, "Próxima visita", NotificationManager.IMPORTANCE_LOW).apply {
            description = "La cuenta regresiva de la visita que viene, con «Cómo llegar»."
            setShowBadge(false)
        })
        if (!disponible || FirebaseApp.getApps(ctx).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(ctx, FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROYECTO).setGcmSenderId(BuildConfig.FIREBASE_REMITENTE).build())
        }
    }

    /** El token de FCM de este teléfono (null sin push). */
    suspend fun token(ctx: Context): String? {
        if (!disponible || FirebaseApp.getApps(ctx).isEmpty()) return null
        return runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
    }

    /** Android 13+ pide permiso para mostrar avisos; antes viene dado. */
    fun necesitaPermiso(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hayPermiso(ctx: Context): Boolean = !necesitaPermiso(ctx) && NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Lo que abre un aviso al tocarlo: la app con la ruta (corredor://…). `accion` y `aviso`: el botón tocado y el aviso
     *  del centro de avisos (las acciones que pide la API abren el aviso con esa acción). */
    fun abrir(ctx: Context, ruta: String, codigo: Int, accion: String? = null, aviso: String? = null): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("corredor://" + ruta.removePrefix("/").ifEmpty { "inicio" })
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("accion", accion)
            putExtra("avisoId", aviso)
        }
        return PendingIntent.getActivity(ctx, codigo, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Un botón del aviso como lo manda la API en `acciones` (avisos.Accion): con `ruta`, llama a la API con `metodo` y
     *  `cuerpo`; con `entrada: "texto"`, primero pide el texto y manda {"texto": …}; sin `ruta`, abre el aviso en la app
     *  con esa acción (la `url` del panel queda de vuelta). */
    @Serializable
    data class AccionAviso(
        val accion: String,
        val titulo: String,
        val metodo: String? = null,
        val ruta: String? = null,
        val cuerpo: JsonElement? = null,
        val entrada: String? = null,
        val url: String? = null,
    )

    /** `acciones` del aviso (un JSON con la lista). */
    fun acciones(d: Map<String, String>): List<AccionAviso> =
        d["acciones"]?.let { runCatching { Api.json.decodeFromString<List<AccionAviso>>(it) }.getOrNull() } ?: emptyList()

    /** «Llamar» de una consulta: abre el marcador con el teléfono del aviso (sólo dígitos y «+», como iOS). */
    fun llamar(ctx: Context, telefono: String?, ruta: String, codigo: Int, aviso: String?): PendingIntent {
        val t = telefono?.filter { it.isDigit() || it == '+' }.orEmpty()
        if (t.isEmpty()) return abrir(ctx, ruta, codigo, "llamar", aviso)
        val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$t")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, codigo, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Una acción que corre sin abrir la app: Responder (14C), Marcar como hecha, o una de la API. */
    fun accion(ctx: Context, que: String, ruta: String, aviso: Int, avisoId: String? = null, api: AccionAviso? = null, citaId: String? = null): PendingIntent {
        val i = Intent(ctx, AccionesPush::class.java).apply {
            action = "ar.com.corredor.app.$que"
            putExtra("ruta", ruta); putExtra("aviso", aviso); putExtra("avisoId", avisoId); putExtra("citaId", citaId)
            api?.let { putExtra("api", Api.json.encodeToString(it)) }
        }
        val conTexto = que == "responder" || api?.entrada == "texto"
        val mutable = if (conTexto) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, (que + ruta + (api?.accion ?: "")).hashCode(), i, mutable or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Arma y muestra un aviso. Llega sólo con datos: {titulo, cuerpo, ruta, tipo, categoria, avisoId, acciones}.
     *  Los botones son los de su categoría, como las categorías de iOS: mensaje (Responder, Abrir), consulta (Responder,
     *  Llamar, Abrir), cobro (Registrar cobro, Abrir) y visita (Abrir, Marcar como hecha). Un aviso sin categoría con
     *  `acciones` muestra las que pide la API (hasta tres, lo que deja Android). */
    fun mostrar(ctx: Context, d: Map<String, String>) {
        val titulo = d["titulo"] ?: return
        val cuerpo = d["cuerpo"].orEmpty()
        val ruta = d["ruta"].orEmpty()
        val tipo = d["tipo"].orEmpty()
        val aviso = d["avisoId"]?.ifEmpty { null }
        val id = (aviso ?: ruta.ifEmpty { titulo + cuerpo }).hashCode()
        val b = NotificationCompat.Builder(ctx, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_aviso)
            .setColor(0xFF16443B.toInt())
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setAutoCancel(true)
            // El thread-id de iOS es el tipo del aviso.
            .setGroup(tipo.ifEmpty { "corredor" })
            .setContentIntent(abrir(ctx, ruta, id, aviso = aviso))
        val ver = NotificationCompat.Action(0, "Abrir", abrir(ctx, ruta, id + 1, "ver", aviso))
        // 14C: un WhatsApp o una pregunta de ML se responden sin abrir la app.
        val responder = NotificationCompat.Action.Builder(0, "Responder", accion(ctx, "responder", ruta, id, aviso))
            .addRemoteInput(RemoteInput.Builder("respuesta").setLabel("Escribí tu respuesta").build()).setAllowGeneratedReplies(false).build()
        when (d["categoria"]) {
            "mensaje" -> { b.addAction(responder); b.addAction(ver) }
            // Llamar: el marcador con el `telefono` de la consulta (tel:// en iOS); sin él, abre la app.
            "consulta" -> { b.addAction(responder); b.addAction(0, "Llamar", llamar(ctx, d["telefono"], ruta, id + 2, aviso)); b.addAction(ver) }
            // Un cobro se registra abriendo la app (en iOS pide desbloquear el teléfono).
            "cobro" -> { b.addAction(0, "Registrar cobro", abrir(ctx, ruta, id + 3, "cobrar", aviso)); b.addAction(ver) }
            "visita" -> { b.addAction(ver); b.addAction(0, "Marcar como hecha", accion(ctx, "hecha", ruta, id, aviso, citaId = d["citaId"])) }
            else -> acciones(d).take(3).forEachIndexed { n, a ->
                when {
                    a.ruta.isNullOrEmpty() -> b.addAction(0, a.titulo, abrir(ctx, ruta, id + 10 + n, a.accion, aviso))
                    a.entrada == "texto" -> b.addAction(NotificationCompat.Action.Builder(0, a.titulo, accion(ctx, "api", ruta, id, aviso, a))
                        .addRemoteInput(RemoteInput.Builder("respuesta").setLabel("Escribí tu respuesta").build()).setAllowGeneratedReplies(false).build())
                    else -> b.addAction(0, a.titulo, accion(ctx, "api", ruta, id, aviso, a))
                }
            }
        }
        if (!hayPermiso(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(id, b.build()) }
    }
}

/** Recibe los avisos de FCM (vienen sólo con datos: la app arma la notificación con sus acciones) y la próxima visita
 *  en curso que mueve el servidor. */
class ServicioPush : FirebaseMessagingService() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val api = SesionCompartida.api(this) ?: return
        if (ar.com.corredor.app.nucleo.Preferencias.bool("pushApagado")) return
        alcance.launch { runCatching { api.hacer("POST", "/v1/push/dispositivos", Estado.Dispositivo(token)) } }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val d = m.data.toMutableMap()
        m.notification?.let { n -> d.putIfAbsent("titulo", n.title.orEmpty()); d.putIfAbsent("cuerpo", n.body.orEmpty()) }
        if (d["tipo"] == "actividad") { ActividadVisita.desdePush(applicationContext, d); return }
        Push.mostrar(applicationContext, d)
        // Con la app abierta, el aviso se ve igual y las listas se refrescan (willPresent en iOS).
        EnPrimerPlano.alAviso?.invoke()
        // Lo sin responder del widget cambió (una consulta, un WhatsApp).
        Widgets.actualizar(applicationContext)
    }
}

/** Lo que hace la app abierta cuando llega un aviso (refrescar, como willPresent en iOS). */
object EnPrimerPlano { @Volatile var alAviso: (() -> Unit)? = null }

/** Las acciones de los avisos que no abren la app: Responder (14C) y Marcar como hecha. */
class AccionesPush : BroadcastReceiver() {
    @Serializable private data class Mensaje(val texto: String)
    @Serializable private data class Hecha(val estado: String = "hecha")

    override fun onReceive(ctx: Context, i: Intent) {
        val que = i.action?.substringAfterLast('.') ?: return
        val rutaTexto = i.getStringExtra("ruta").orEmpty()
        val ruta = Ruta.de(rutaTexto)
        val aviso = i.getIntExtra("aviso", 0)
        val api = SesionCompartida.api(ctx) ?: return
        val texto = RemoteInput.getResultsFromIntent(i)?.getCharSequence("respuesta")?.toString()?.trim()
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching {
                when (que) {
                    "responder" -> {
                        if (texto.isNullOrEmpty()) return@runCatching
                        when (val d = ruta?.destino) {
                            is AChat -> api.hacer("POST", "/v1/whatsapp/conversaciones/${d.id}/mensajes", Mensaje(texto))
                            is APreguntaML -> api.hacer("POST", "/v1/mercadolibre/preguntas/${d.id}/respuesta", Mensaje(texto))
                            else -> {}
                        }
                    }
                    "hecha" -> {
                        // La cita sale de la ruta del aviso (/agenda/<id>) o de `citaId`.
                        val id = (ruta?.destino as? ACita)?.id ?: i.getStringExtra("citaId")?.ifEmpty { null }
                        if (id != null) api.hacer("PATCH", "/v1/agenda/$id", Hecha())
                    }
                    "api" -> {
                        val a = i.getStringExtra("api")?.let { Api.json.decodeFromString<Push.AccionAviso>(it) } ?: return@runCatching
                        val r = a.ruta ?: return@runCatching
                        val cuerpo = if (a.entrada == "texto") {
                            if (texto.isNullOrEmpty()) throw IllegalStateException("sin texto")
                            Api.json.encodeToString(Mensaje(texto)).toByteArray()
                        } else a.cuerpo?.toString()?.toByteArray()
                        api.pedir(a.metodo ?: "POST", r, cuerpo = cuerpo)
                    }
                }
            }.isSuccess
            if (ok && aviso != 0) NotificationManagerCompat.from(ctx).cancel(aviso)
            // Si no se mandó, se abre el chat con lo escrito para mandarlo a mano.
            if (!ok && (que == "responder" || (que == "api" && texto != null))) {
                runCatching { Push.abrir(ctx, rutaTexto, aviso + 9, accion = "fallo-respuesta").send() }
            }
            Widgets.actualizar(ctx)
            EnPrimerPlano.alAviso?.invoke()
            pendiente.finish()
        }
    }
}
