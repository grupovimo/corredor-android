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
import kotlinx.serialization.Serializable

// Clon de App/Push.swift (el Delegado de APNs) con FCM sólo datos: registra el token (`POST /v1/push/dispositivos`),
// muestra los avisos con las mismas categorías y acciones (14B), abre la pantalla exacta al tocarlos (`ruta` del aviso)
// y resuelve la respuesta escrita desde el aviso (14C) sin abrir la app.

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

    /** Una acción que corre sin abrir la app (Responder, Marcar como hecha). */
    fun accion(ctx: Context, que: String, ruta: String, aviso: Int, cita: String? = null): PendingIntent {
        val i = Intent(ctx, AccionesPush::class.java).apply {
            action = "ar.com.corredor.app.$que"
            putExtra("ruta", ruta); putExtra("aviso", aviso); putExtra("citaId", cita)
        }
        val mutable = if (que == "responder") PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, (que + ruta).hashCode(), i, mutable or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Arma y muestra un aviso con las acciones de su categoría (las mismas que las categorías de iOS):
     *  mensaje (Responder, Abrir), consulta (Responder, Llamar, Abrir), cobro (Registrar cobro, Abrir) y visita (Abrir,
     *  Marcar como hecha). El aviso viene sólo con datos: {titulo, cuerpo, ruta, categoria, avisoId, telefono, citaId}. */
    fun mostrar(ctx: Context, d: Map<String, String>) {
        val titulo = d["titulo"] ?: return
        val cuerpo = d["cuerpo"].orEmpty()
        val ruta = d["ruta"].orEmpty()
        val aviso = d["avisoId"]
        val id = (d["colapso"] ?: ruta.ifEmpty { titulo + cuerpo }).hashCode()
        val b = NotificationCompat.Builder(ctx, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_aviso)
            .setColor(0xFF16443B.toInt())
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setAutoCancel(true)
            .setGroup(d["hilo"] ?: "corredor")
            .setContentIntent(abrir(ctx, ruta, id, aviso = aviso))
        val ver = NotificationCompat.Action(0, "Abrir", abrir(ctx, ruta, id + 1, "ver", aviso))
        // 14C: un WhatsApp o una pregunta de ML se responden sin abrir la app.
        val responder = NotificationCompat.Action.Builder(0, "Responder", accion(ctx, "responder", ruta, id))
            .addRemoteInput(RemoteInput.Builder("respuesta").setLabel("Escribí tu respuesta").build()).setAllowGeneratedReplies(false).build()
        when (d["categoria"]) {
            "mensaje" -> { b.addAction(responder); b.addAction(ver) }
            "consulta" -> {
                b.addAction(responder)
                d["telefono"]?.let { t ->
                    val marcar = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + t.filter { it.isDigit() || it == '+' }))
                    b.addAction(0, "Llamar", PendingIntent.getActivity(ctx, id + 2, marcar, PendingIntent.FLAG_IMMUTABLE))
                }
                b.addAction(ver)
            }
            // Un cobro se registra abriendo la app (en iOS pide desbloquear el teléfono).
            "cobro" -> { b.addAction(0, "Registrar cobro", abrir(ctx, ruta, id + 3, "cobrar", aviso)); b.addAction(ver) }
            "visita" -> { b.addAction(ver); b.addAction(0, "Marcar como hecha", accion(ctx, "hecha", ruta, id, d["citaId"])) }
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
                        val id = (ruta?.destino as? ACita)?.id ?: i.getStringExtra("citaId")
                        if (id != null) api.hacer("PATCH", "/v1/agenda/$id", Hecha())
                    }
                }
            }.isSuccess
            if (ok && aviso != 0) NotificationManagerCompat.from(ctx).cancel(aviso)
            // Si no se mandó, se abre el chat con lo escrito para mandarlo a mano.
            if (!ok && que == "responder") {
                runCatching { Push.abrir(ctx, rutaTexto, aviso + 9, accion = "fallo-respuesta").send() }
            }
            Widgets.actualizar(ctx)
            EnPrimerPlano.alAviso?.invoke()
            pendiente.finish()
        }
    }
}
