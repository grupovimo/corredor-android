package ar.com.corredor.app.app

import android.app.Application
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.compartido.Llavero
import ar.com.corredor.app.nucleo.Guardado
import ar.com.corredor.app.nucleo.Impresor
import ar.com.corredor.app.nucleo.Preferencias
import ar.com.corredor.app.nucleo.Ruta
import ar.com.corredor.app.nucleo.Tienda
import ar.com.corredor.app.nucleo.nonEmpty
import ar.com.corredor.app.pantallas.entrada.ElegirInmobiliaria
import ar.com.corredor.app.pantallas.entrada.Entrada
import ar.com.corredor.app.pantallas.entrada.VerInvitacion
import ar.com.corredor.app.tema.Afuera
import ar.com.corredor.app.tema.C
import ar.com.corredor.app.tema.Hoja
import ar.com.corredor.app.tema.Imagen
import ar.com.corredor.app.tema.Imagenes
import ar.com.corredor.app.widgets.Widgets

// Clon de App/CorredorApp.swift: la aplicación (la actividad está en MainActivity), la raíz que elige qué se ve y los
// links que abren la app.

/** La aplicación: deja listos el Llavero, las preferencias, lo guardado y el push antes que cualquier pantalla, servicio
 *  de avisos o widget (en iOS es el proceso entero). */
class CorredorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Llavero.contexto = applicationContext
        Preferencias.contexto = applicationContext
        Guardado.contexto = applicationContext
        Impresor.contexto = applicationContext
        Afuera.contexto = applicationContext
        ActividadVisita.contexto = applicationContext
        Tienda.compartida.contexto = applicationContext
        Consejos.configurar()
        Push.iniciar(applicationContext)
    }

    companion object {
        /** El Estado vive en el proceso (como en iOS), no en la actividad: si Android la rehace (modo oscuro, tamaño de
         *  letra, multiventana) no se pierden la sesión, las pilas de navegación ni el ingreso con Google a medias. */
        val estado: Estado by lazy { Estado() }
    }
}

/** Un link que abre la app (App Link de corredor.com.ar o `corredor://`).
 *  - `invitacion/<token>`: se muestra la invitación antes de aceptarla (sin sesión, después de entrar).
 *  - `ingresar?alta=1` o `empezar`: el ingreso en modo «Creá tu cuenta»; `ingresar/recuperar`: «¿No podés entrar?».
 *  - Lo demás: su pantalla. Sin sesión queda pendiente y se abre al entrar (como el `?volver=` de la web). */
fun abrirLink(estado: Estado, url: Uri) {
    val web = url.host?.endsWith("corredor.com.ar") == true
    val partes = if (url.scheme == "corredor") listOf(url.host ?: "") + url.pathSegments else url.pathSegments
    if ((web || url.scheme == "corredor") && partes.size > 1 && partes[0] == "invitacion") {
        estado.abrirInvitacion(partes[1])
        return
    }
    if (estado.fase != Estado.Fase.adentro) {
        if (partes.firstOrNull() == "ingresar" || partes.firstOrNull() == "empezar") {
            if (partes.size > 1 && partes[1] == "recuperar") estado.recuperarAbierto = true
            else estado.ingresoAlta = partes.firstOrNull() == "empezar" || url.getQueryParameter("alta") != null
            url.getQueryParameter("nombre")?.nonEmpty?.let { estado.nombreSugerido = it.take(120) }
            return
        }
    }
    if (partes.firstOrNull() == "ingresar") return
    val r = Ruta(url.toString()) ?: return
    if (estado.fase == Estado.Fase.adentro) estado.ir(r) else Abrir.pendiente = r
}

/** Qué se ve según la sesión: lanzamiento, entrada (1A–1B), elegir inmobiliaria (1C) o la app. */
@Composable
fun Raiz(estado: Estado) {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(estado.fase, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) }, label = "fase") { f ->
            when (f) {
                Estado.Fase.arrancando -> Lanzamiento()
                Estado.Fase.afuera -> Entrada()
                Estado.Fase.eligiendo -> ElegirInmobiliaria()
                // Los colores de la acción leen el acento de la inmobiliaria al pintarse: si cambia (se publicó la web con
                // otro color), la app se pinta de nuevo. La navegación vive en `Estado`, así que no se pierde.
                Estado.Fase.adentro -> key(estado.marcaVersion) { Principal() }
            }
        }
        AnimatedVisibility(estado.bloqueada, Modifier.zIndex(2f), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) { Bloqueo() }
        // La invitación de un link, para verla antes de aceptarla (sobre la app o sobre «¿Dónde trabajás hoy?»).
        val i = estado.invitacionAbierta
        Hoja(i != null, { estado.invitacionAbierta = null }) { i?.let { VerInvitacion(token = it.token) } }
    }
    // Una sola vez por proceso: el Estado sobrevive a la actividad, y rehacerla no es volver a arrancar.
    LaunchedEffect(Unit) { if (estado.fase == Estado.Fase.arrancando) estado.arrancar() }
    LaunchedEffect(estado.fase) {
        val f = estado.fase
        if (f == Estado.Fase.adentro) Abrir.pendiente?.let { Abrir.pendiente = null; estado.ir(it) }
        if (f == Estado.Fase.afuera) Spotlight.olvidar()
        estado.contexto?.let { Widgets.actualizar(it) }
    }
}

/** La pantalla de lanzamiento: verde 0f3530 con la rr en oro, sin texto (igual que la de iOS). */
@Composable
fun Lanzamiento() {
    Box(Modifier.fillMaxSize().background(C.verde800), contentAlignment = Alignment.Center) {
        Imagen(Imagenes.lanzamiento, Modifier.width(120.dp), descripcion = "Corredor")
    }
}
