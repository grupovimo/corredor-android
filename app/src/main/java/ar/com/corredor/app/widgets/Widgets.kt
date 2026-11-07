package ar.com.corredor.app.widgets

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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

// Clon de CorredorWidgets/Widgets.swift con Glance: «Hoy» (chico y mediano). En Android no hay widgets de pantalla
// bloqueada: los accesorios no van. La Live Activity de la visita es la notificación en curso (app/Atajos.kt).

object Widgets {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Vuelve a dibujar los widgets (WidgetCenter.reloadAllTimelines en iOS). */
    fun actualizar(ctx: Context) { alcance.launch { runCatching { WidgetHoy().updateAll(ctx.applicationContext) } } }
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
        val h = runCatching { Hoy.traer(context) }.getOrElse { Hoy.leer(context) }
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
                Visita(ctx, h)
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
}
