package ar.com.corredor.app.pantallas.mensajes

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate

// Clon de Pantallas/Mensajes/ModeloMensajes.swift.
// Lo que la bandeja arma con los datos de la ficha y con lo que escribió el cliente, igual que la
// web (lib/mensajes/modelo.ts): las respuestas sugeridas, el día que propone para la visita, los
// turnos libres del agente, las plantillas con sus datos y las respuestas guardadas con «/».

object Charla {
    fun primerNombre(n: String?): String = (n ?: "").trim().split(" ").firstOrNull { it.isNotEmpty() } ?: ""

    private val dias = listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")

    /** «sáb 14 · 11:00» (en la hora de Buenos Aires). */
    fun diaHora(d: Instant): String {
        val z = Formato.enArgentina(d)
        val h = String.format(java.util.Locale.ROOT, "%02d:%02d", z.hour, z.minute)
        return "${dias[z.dayOfWeek.value % 7]} ${z.dayOfMonth} · $h"
    }

    /** Un instante a partir del día de `base` y la hora en Buenos Aires. */
    private fun a(base: LocalDate, hora: Int, minuto: Int): Instant? =
        runCatching { base.atTime(hora, minuto).atZone(Formato.zona).toInstant() }.getOrNull()

    private fun coincide(patron: String, t: String): Boolean = Regex("(?U)$patron", RegexOption.IGNORE_CASE).containsMatchIn(t)

    /** El día y la hora que propone el cliente («¿Puede ser el sábado a las 11?», «mañana 17:30», «el
     *  jueves 5 de la tarde»): siempre en el futuro, o null si no dice día y hora. */
    fun fechaPropuesta(texto: String, ahora: Instant = Instant.now()): Instant? {
        val t = texto.lowercase()
        val hoy = Formato.enArgentina(ahora).toLocalDate()
        val hoySem = hoy.dayOfWeek.value % 7
        var suma: Int? = null
        if (coincide("""\bpasado mañana\b""", t)) suma = 2
        else if (coincide("""(?<!de la )(?<!por la )\bmañana\b""", t)) suma = 1
        else if (coincide("""\bhoy\b""", t)) suma = 0
        else {
            val nombres = listOf(
                """\bdomingo\b""" to 0, """\blunes\b""" to 1, """\bmartes\b""" to 2, """\bmi[eé]rcoles\b""" to 3,
                """\bjueves\b""" to 4, """\bviernes\b""" to 5, """\bs[aá]bado\b|\bs[aá]b\b""" to 6,
            )
            for ((re, d) in nombres) {
                if (coincide(re, t)) {
                    val s = (d - hoySem + 7) % 7
                    suma = if (s == 0) 7 else s
                    break
                }
            }
        }
        val dias = suma ?: return null
        val patron = """(?:a las|tipo|las|a la)\s*(\d{1,2})(?:[:.](\d{2}))?|\b(\d{1,2})[:.](\d{2})\b|\b(\d{1,2})\s*(?:hs|h)\b|\b(\d{1,2})\s*de la (?:tarde|mañana|noche)"""
        val m = Regex("(?U)$patron").find(t) ?: return null
        fun grupo(i: Int): Int? = m.groups[i]?.value?.toIntOrNull()
        var hora = grupo(1) ?: grupo(3) ?: grupo(5) ?: grupo(6) ?: return null
        val minuto = grupo(2) ?: grupo(4) ?: 0
        if (hora > 23 || minuto > 59) return null
        if (coincide("de la tarde|de la noche", t) && hora < 12) hora += 12
        else if (hora in 1..7 && !coincide("de la mañana", t)) hora += 12 // «a las 5» es a la tarde
        val f = a(hoy.plusDays(dias.toLong()), hora, minuto) ?: return null
        return if (f > ahora) f else null
    }

    /** Los próximos turnos libres del agente para una visita («Martín tiene libre»): de lunes a viernes
     *  a las 11, 16 y 17; los sábados a las 10, 11 y 12; sin pisar su agenda y con 2 h de aviso. */
    fun turnosLibres(ocupado: List<Cita>, ahora: Instant = Instant.now(), cuantos: Int = 3): List<Instant> {
        val out = mutableListOf<Instant>()
        val hoy = Formato.enArgentina(ahora).toLocalDate()
        for (d in 0 until 10) {
            if (out.size >= cuantos) break
            val dia = hoy.plusDays(d.toLong())
            val sem = dia.dayOfWeek.value % 7
            if (sem == 0) continue
            for (h in if (sem == 6) listOf(10, 11, 12) else listOf(11, 16, 17)) {
                val ini = a(dia, h, 0) ?: continue
                if (ini < ahora.plusSeconds(2 * 3600)) continue
                val fin = ini.plusSeconds(3600)
                val pisa = ocupado.any { it.empiezaEn < fin && it.terminaEn > ini }
                if (!pisa) out.add(ini)
                if (out.size >= cuantos) break
            }
        }
        return out
    }

    /** «Proponer visita»: «Valeria, ¿te queda bien el vie 13 a las 16:00 o el sáb 14 a las 11:00 para verlo?». */
    fun textoPropuesta(nombre: String, turnos: List<Instant>): String? {
        if (turnos.isEmpty()) return null
        val t = turnos.take(2).map { d ->
            val p = diaHora(d).split(" · ")
            "el ${p[0]} a las ${if (p.size > 1) p[1] else ""}"
        }
        return "${if (nombre.isEmpty()) "¿" else "$nombre, ¿"}te queda bien ${t.joinToString(" o ")} para verlo?"
    }

    /** «USD 172.000 · 3 amb · 74 m²». */
    fun resumenFicha(p: Propiedad): String = listOfNotNull(
        p.precio?.let { if (p.precioOculto == true) null else Formato.plata(it, p.moneda ?: "USD") },
        p.ambientes?.let { if (it > 0) "$it amb" else null },
        p.superficie?.let { if (it > 0) "${Formato.numero(it.toInt())} m²" else null },
    ).joinToString(" · ")

    /** «Sigue disponible + precio»: sólo con lo cargado en la ficha y si está disponible. */
    fun sugerenciaDisponible(nombre: String, p: Propiedad): String? {
        if ((p.estado ?: "disponible") != "disponible") return null
        val datos = listOfNotNull(
            p.ambientes?.let { if (it > 0) "$it amb" else null },
            p.superficie?.let { if (it > 0) "${Formato.numero(it.toInt())} m²" else null },
        )
        var precio = p.precio?.let { if (p.precioOculto == true || it == 0.0) null else Formato.plata(it, p.moneda ?: "USD") } ?: ""
        val e = p.expensas
        if (precio.isNotEmpty() && e != null && e > 0) precio += " con expensas de ${Formato.plata(e, "ARS")}"
        val partes = (datos + listOf(precio)).filter { it.isNotEmpty() }.joinToString(", ")
        val hola = if (nombre.isEmpty()) "¡Hola! Sí, sigue" else "Hola $nombre, ¡sí! Sigue"
        return "$hola disponible${if (partes.isEmpty()) "" else ": $partes"}. ¿Querés verlo esta semana?"
    }

    /** «Mandar la ficha»: el link público de la propiedad en la web de la inmobiliaria. */
    fun textoFicha(nombre: String, p: Propiedad, web: String?): String? {
        val w = web?.nonEmpty ?: return null
        val link = "$w/propiedades/${p.slug?.nonEmpty ?: p.numero?.toString() ?: p.id}"
        return "${if (nombre.isEmpty()) "" else "Hola $nombre, "}te paso la ficha de ${p.nombre}: $link"
    }

    /** El texto de una plantilla con sus datos puestos ({{1}}, {{2}}…). */
    fun completar(cuerpo: String, ps: List<String>): String =
        Regex("""\{\{(\d+)\}\}""").replace(cuerpo) { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            val v = if (n >= 1 && n <= ps.size) ps[n - 1] else ""
            if (v.isNotEmpty()) v else m.value
        }

    private fun normal(x: String): String =
        Normalizer.normalize(x, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Las respuestas guardadas que coinciden con lo escrito después de la «/» (título o texto, sin acentos). */
    fun filtrar(rs: List<RespuestaGuardada>, q: String): List<RespuestaGuardada> {
        val b = normal(q.trim())
        if (b.isEmpty()) return rs
        return rs.filter { normal(it.titulo).contains(b) || normal(it.texto).contains(b) }
    }

    /** El texto de una respuesta guardada con {nombre} cambiado por el primer nombre del contacto. */
    fun completarRespuesta(texto: String, nombre: String): String =
        texto.replace(Regex("""\s*\{nombre\}"""), if (nombre.isEmpty()) "" else " $nombre").trimStart()

    /** «retomar_consulta» → «Retomar consulta». */
    fun nombrePlantilla(n: String): String {
        val t = n.replace("_", " ")
        return t.take(1).uppercase() + t.drop(1)
    }

    /** El diseño dice «Interesada»: el género sale del nombre y, si no se sabe, «Interesado». */
    fun esElla(n: String): Boolean {
        val ellas = setOf("rocío", "belén", "inés", "abril", "raquel", "isabel", "pilar", "luz", "sol", "mercedes", "dolores", "carmen", "ruth", "noemí", "ivonne")
        return n.lowercase().endsWith("a") || n.lowercase() in ellas
    }

    /** «Interesada · llegó por Zonaprop · sin dueño». */
    fun subtitulo(tipo: String?, origen: String?, primero: String, sinDueno: Boolean): String {
        var quien: String? = null
        if (tipo == "comprador" || tipo == "inquilino") quien = if (esElla(primero)) "Interesada" else "Interesado"
        else tipo?.nonEmpty?.let { t -> quien = t.take(1).uppercase() + t.drop(1) }
        val llego = origen?.nonEmpty?.let { "llegó por ${if (it == "web") "la web" else it.take(1).uppercase() + it.drop(1)}" }
        return listOfNotNull(quien, llego, if (sinDueno) "sin dueño" else null).joinToString(" · ")
    }
}

// En iOS son extensiones de Estado que viven acá (ModeloMensajes.swift) y las usan muchas pantallas.

/** El aviso de error con «Reintentar» (como el de la web): dura 6 s. */
fun Estado.avisarReintentar(texto: String, reintentar: () -> Unit) {
    val a = Aviso(texto = texto, ok = false, accion = "Reintentar" to reintentar)
    aviso = a
    MainScope().launch {
        delay(6000)
        if (aviso?.id == a.id) aviso = null
    }
}

/** El texto de un error de la API, para el aviso o la hoja. */
fun Estado.Companion.texto(e: Throwable, si: String = "Algo salió mal. Probá de nuevo."): String {
    if (e is ErrorApi) return if (e.detalle.isEmpty()) e.titulo else e.detalle
    return si
}

// MARK: - Archivos con la sesión

/** Lo bajado de /v1/archivos/{id}/contenido (necesita la sesión: Coil no la manda). */
object ArchivosSesion {
    private val cache = mutableMapOf<String, ByteArray>()

    suspend fun datos(id: String, api: Api): ByteArray {
        cache[id]?.let { return it }
        val d = api.pedir("GET", "/v1/archivos/$id/contenido")
        if (d.size < 8_000_000) cache[id] = d
        return d
    }

    /** Lo deja en un archivo temporal con su nombre, para la vista previa (compartir, imprimir). */
    suspend fun archivo(id: String, nombre: String, api: Api, ctx: Context): File {
        val d = datos(id, api)
        val carpeta = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val f = File(carpeta, nombre)
        withContext(Dispatchers.IO) { f.writeBytes(d) }
        return f
    }
}

/** Una foto de /v1/archivos con la sesión de la app. */
@Composable
fun ImagenArchivo(id: String, modifier: Modifier = Modifier) {
    val estado = LocalEstado.current
    var imagen by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id) {
        val d = runCatching { ArchivosSesion.datos(id, estado.api) }.getOrNull() ?: return@LaunchedEffect
        imagen = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(d, 0, d.size)?.asImageBitmap() }
    }
    Box(modifier.clipToBounds()) {
        val i = imagen
        if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(C.hundida))
    }
}
