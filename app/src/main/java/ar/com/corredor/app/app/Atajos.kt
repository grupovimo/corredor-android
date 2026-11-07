package ar.com.corredor.app.app

import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ar.com.corredor.app.BuildConfig
import ar.com.corredor.app.R
import ar.com.corredor.app.nucleo.AAgendaDia
import ar.com.corredor.app.nucleo.ABuscar
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.Cita
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.Preferencias
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.nucleo.Ruta
import ar.com.corredor.app.nucleo.intentar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.time.Instant

// Clon de App/Atajos.swift. 14F–14H: el menú del ícono (res/xml/shortcuts.xml, que abre links corredor://), Spotlight
// (en Android no hay), los atajos de Siri (son los mismos del menú del ícono) y la Live Activity de la próxima visita
// (una notificación en curso con la cuenta regresiva, pedida como Live Update en Android 16).

/** Abre la app en una ruta (lo usan los atajos, los avisos y los widgets). */
object Abrir {
    var estado: WeakReference<Estado>? = null
    var pendiente: Ruta? = null

    fun ir(r: Ruta) {
        val e = estado?.get()
        if (e != null && e.fase == Estado.Fase.adentro) e.ir(r) else pendiente = r
    }
}

/** 14F El menú del ícono (mantener apretado): las cuatro acciones rápidas. En Android están en res/xml/shortcuts.xml
 *  como links corredor://mira, //agenda, //buscar y //mensajes; esto es lo que hace cada una. */
object MenuIcono {
    fun abrir(tipo: String) {
        when (tipo) {
            "mira" -> Abrir.ir(Ruta(Pestana.mira))
            "agenda" -> Abrir.ir(Ruta(Pestana.inicio, AAgendaDia()))
            "buscar" -> Abrir.ir(Ruta(Pestana.inicio, ABuscar()))
            else -> Abrir.ir(Ruta(Pestana.mensajes))
        }
    }
}

/** 14G Spotlight: en Android no hay un índice del sistema para las apps; queda la misma interfaz, sin hacer nada. */
object Spotlight {
    fun indexar(propiedades: List<*>) {}
    fun indexar(contactos: Collection<*>) {}
    fun indexarSecciones(duenio: Boolean) {}
    fun olvidar() {}
}

/** 14A, 14D La próxima visita en curso (la Live Activity de iOS): arranca una hora antes y se termina al pasar. Es una
 *  notificación en curso con la cuenta regresiva hasta la visita, «Cómo llegar» y «Estoy llegando». Le pasa el token
 *  de FCM a la API (`POST /v1/push/actividades` con plataforma android) para que el servidor la mueva o la termine
 *  (cambio de hora, hecha, cancelada) aunque la app esté cerrada. */
object ActividadVisita {
    private const val AVISO = 7101
    /** La API manda las actualizaciones a este token. */
    var api: Api? = null
    internal var contexto: Context? = null
    private var actual: String? = null
    private val registradas = mutableSetOf<String>()
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable private data class Registro(
        val token: String,
        val citaId: String,
        val plataforma: String = "android",
        val entorno: String = if (BuildConfig.DEBUG) "sandbox" else "produccion",
    )

    fun actualizar(cita: Cita?) {
        val ctx = contexto ?: return
        val c = cita
        val ahora = Instant.now()
        if (c == null || c.empiezaEn.epochSecond - ahora.epochSecond >= 3600 || !c.terminaEn.isAfter(ahora) || c.estado == "cancelada") {
            actual?.let { terminar(ctx, it) }
            return
        }
        mostrar(ctx, c.id, c.donde, c.contactoNombre, c.empiezaEn, c.terminaEn)
        if (c.id !in registradas) alcance.launch {
            val token = Push.token(ctx) ?: return@launch
            if (intentar { api?.hacer("POST", "/v1/push/actividades", Registro(token, c.id)) }) registradas.add(c.id)
        }
    }

    /** Lo que manda el servidor por FCM (actividades.go › datosActividad): {tipo: «actividad», cita, empieza (RFC 3339),
     *  estado, terminar: «1»|«0»}. El lugar, con quién y la duración no vienen: son los de la última vez que se mostró esa
     *  cita (los guarda `mostrar`, también con la app cerrada). */
    fun desdePush(ctx: Context, d: Map<String, String>) {
        val cita = d["cita"] ?: return
        val estado = d["estado"].orEmpty()
        if (d["terminar"] == "1" || estado == "cancelada" || estado == "hecha") { terminar(ctx, cita); return }
        val empieza = Fecha.iso(d["empieza"]) ?: return
        Preferencias.contexto = Preferencias.contexto ?: ctx.applicationContext
        val donde = Preferencias.texto("visita.$cita.donde") ?: "Visita"
        val con = Preferencias.texto("visita.$cita.con")?.ifEmpty { null }
        val dura = Preferencias.entero("visita.$cita.minutos").takeIf { it > 0 } ?: 60
        mostrar(ctx, cita, donde, con, empieza, empieza.plusSeconds(dura * 60L))
    }

    private fun mostrar(ctx: Context, cita: String, donde: String, con: String?, empieza: Instant, termina: Instant) {
        Preferencias.ponerTexto("visita.$cita.donde", donde)
        Preferencias.ponerTexto("visita.$cita.con", con ?: "")
        Preferencias.ponerEntero("visita.$cita.minutos", maxOf(1L, (termina.epochSecond - empieza.epochSecond) / 60).toInt())
        if (!Push.hayPermiso(ctx)) return
        actual = cita
        val ruta = "agenda/$cita"
        val b = NotificationCompat.Builder(ctx, Push.CANAL_VISITA)
            .setSmallIcon(R.drawable.ic_aviso)
            .setColor(0xFF0F3530.toInt())
            .setColorized(true)
            .setContentTitle(con?.let { "Visita con $it" } ?: "Visita")
            .setContentText(donde)
            .setSubText(if (empieza.isAfter(Instant.now())) "Próxima visita" else "Visita en curso")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(empieza.toEpochMilli())
            .setShowWhen(true)
            .setTimeoutAfter(maxOf(60_000L, termina.toEpochMilli() - System.currentTimeMillis()))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(Push.abrir(ctx, ruta, AVISO))
            .addAction(0, "Cómo llegar", Push.abrir(ctx, ruta, AVISO + 1))
            .addAction(0, "Estoy llegando", Push.abrir(ctx, "$ruta?llegando=1", AVISO + 2))
            // Android 16: pedirla como Live Update (EXTRA_REQUEST_PROMOTED_ONGOING); antes se ignora.
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        runCatching { NotificationManagerCompat.from(ctx).notify(AVISO, b.build()) }
    }

    fun terminar(ctx: Context, cita: String) {
        if (actual != null && actual != cita) return
        actual = null
        NotificationManagerCompat.from(ctx).cancel(AVISO)
    }
}
