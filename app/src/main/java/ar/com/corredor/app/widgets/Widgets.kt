package ar.com.corredor.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ar.com.corredor.app.MainActivity
import ar.com.corredor.app.compartido.Hoy
import ar.com.corredor.app.nucleo.Formato
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

// Clon de CorredorWidgets/Widgets.swift con Glance: «Hoy» (chico y mediano). En Android no hay widgets de pantalla
// bloqueada: los accesorios no van. La Live Activity de la visita es la notificación en curso (app/Atajos.kt).

object Widgets {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendiente: Job? = null
    /** Cambiaron los datos: la próxima vuelta pide a la API aunque lo guardado sea reciente. */
    @Volatile internal var forzar = false

    /** Vuelve a dibujar los widgets con lo último de la API (WidgetCenter.reloadAllTimelines en iOS). Junta los pedidos
     *  seguidos (un lote de cambios en vivo, varios avisos) en una sola vuelta. */
    fun actualizar(ctx: Context) {
        val app = ctx.applicationContext
        forzar = true
        pendiente?.cancel()
        pendiente = alcance.launch {
            delay(1500)
            runCatching { WidgetHoy().updateAll(app) }
        }
    }

    /** La próxima vuelta sola (el timeline de iOS: `.after(15 min)` y una entrada cuando termina la visita). Con la
     *  visita a menos de una hora, cada minuto, para la cuenta («en 25 min»: el `Text(style: .relative)` de iOS), y otra
     *  al empezar («Visita en curso»). Es una alarma que no despierta el teléfono. */
    internal fun programar(ctx: Context, h: Hoy) {
        val ahora = System.currentTimeMillis()
        var proxima = ahora + 15 * 60_000L
        h.visita?.let { v ->
            val empieza = v.empieza.toEpochMilli()
            val fin = empieza + 3_600_000L
            if (empieza > ahora) {
                proxima = minOf(proxima, empieza)
                if (empieza - ahora <= 3_600_000L) proxima = minOf(proxima, ahora + 60_000L)
            }
            if (fin > ahora) proxima = minOf(proxima, fin)
        }
        runCatching { ctx.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, proxima, vuelta(ctx)) }
    }

    /** Sin widgets en la pantalla, no se programa nada más. */
    internal fun olvidar(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(vuelta(ctx)) }
    }

    private fun vuelta(ctx: Context): PendingIntent {
        val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WidgetHoyReceiver::class.java))
        val i = Intent(ctx, WidgetHoyReceiver::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        return PendingIntent.getBroadcast(ctx, 7201, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

private val verde = Color(15 / 255f, 53 / 255f, 48 / 255f)
private val oro = Color(242 / 255f, 187 / 255f, 87 / 255f)
private val tinta2 = Color(207 / 255f, 224 / 255f, 216 / 255f)
private val tintaOro = Color(26 / 255f, 20 / 255f, 5 / 255f)

private fun cp(c: Color) = ColorProvider(c)

/** 14E: la próxima visita (chico y mediano) con «Cómo llegar», y lo sin responder al lado. */
class WidgetHoy : GlanceAppWidget() {
    companion object {
        private val CHICO = DpSize(110.dp, 110.dp)
        private val MEDIANO = DpSize(250.dp, 110.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(CHICO, MEDIANO))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Lo guardado si es de hace menos de un minuto y nada cambió (las vueltas de la cuenta regresiva); si no, la API.
        val guardado = Hoy.leer(context)
        val reciente = Duration.between(guardado.actualizado, Instant.now()).seconds in 0..59
        val h = if (reciente && !Widgets.forzar) guardado else runCatching { Hoy.traer(context) }.getOrElse { guardado }
        Widgets.forzar = false
        Widgets.programar(context, h)
        provideContent { Vista(context, h) }
    }

    private fun abrir(ctx: Context, ruta: String) =
        actionStartActivity(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("corredor://$ruta")))

    @Composable
    private fun Vista(ctx: Context, h: Hoy) {
        val mediano = LocalSize.current.width >= MEDIANO.width
        Box(GlanceModifier.fillMaxSize().background(verde).cornerRadius(22.dp).padding(16.dp)) {
            if (mediano) {
                Row(GlanceModifier.fillMaxSize().clickable(abrir(ctx, "mensajes"))) {
                    Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Visita(ctx, h) }
                    Spacer(GlanceModifier.width(14.dp))
                    Box(GlanceModifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.2f))) {}
                    Spacer(GlanceModifier.width(14.dp))
                    Column(GlanceModifier.width(110.dp).fillMaxHeight()) {
                        Text("SIN RESPONDER", style = TextStyle(cp(oro), 11.sp, FontWeight.Bold))
                        Spacer(GlanceModifier.height(4.dp))
                        Text("${h.sinResponder}", style = TextStyle(cp(Color.White), 30.sp, FontWeight.Bold))
                        h.mensajes.take(2).forEach { m ->
                            Text(m.quien, style = TextStyle(cp(tinta2), 12.sp, FontWeight.Medium), maxLines = 1)
                        }
                    }
                }
            } else {
                // Sin widgetURL, tocarlo abre la app (en Glance hace falta decirlo).
                Box(GlanceModifier.fillMaxSize().clickable(abrir(ctx, "inicio"))) { Visita(ctx, h) }
            }
        }
    }

    @Composable
    private fun Visita(ctx: Context, h: Hoy) {
        val v = h.visita
        if (v != null) {
            val ahora = Instant.now()
            val arriba = if (v.empieza.isAfter(ahora)) {
                val min = Duration.between(ahora, v.empieza).toMinutes()
                // Text(style: .relative) de iOS no va en mayúsculas: «25 min», «1 h 5 min».
                "PRÓXIMA VISITA · EN " + if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
            } else "VISITA EN CURSO"
            Column(GlanceModifier.fillMaxSize()) {
                Text(arriba, style = TextStyle(cp(oro), 11.sp, FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(3.dp))
                Text(v.donde, style = TextStyle(cp(Color.White), 17.sp, FontWeight.Bold), maxLines = 2)
                Text(Formato.horaDe(v.empieza) + (v.con?.let { " · con $it" } ?: ""), style = TextStyle(cp(tinta2), 12.sp), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.background(oro).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 6.dp).clickable(abrir(ctx, "agenda/${v.id}"))) {
                    Text("Cómo llegar", style = TextStyle(cp(tintaOro), 13.sp, FontWeight.Bold))
                }
            }
        } else {
            Column(GlanceModifier.fillMaxSize().clickable(abrir(ctx, "agenda"))) {
                Text("HOY", style = TextStyle(cp(oro), 11.sp, FontWeight.Bold))
                Spacer(GlanceModifier.height(4.dp))
                Text("Sin visitas", style = TextStyle(cp(Color.White), 17.sp, FontWeight.Bold))
                Text("Agendá una y le avisamos al cliente.", style = TextStyle(cp(tinta2), 12.sp))
                Spacer(GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.fillMaxWidth().height(1.dp))
            }
        }
    }
}

class WidgetHoyReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetHoy()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Widgets.olvidar(context)
    }
}
