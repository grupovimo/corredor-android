package ar.com.corredor.app.pantallas.agenda

import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.Formato
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

// Clon de Pantallas/Agenda/Entender.swift.

/** El alta rápida de la agenda, como la web (lib/crm/entender.ts): «visita Gorriti 5530 con Inés Robledo el sábado a
 *  las 11» se entiende como lo dirías. Saca el tipo, la propiedad (de la cartera), la persona (de los contactos), el día
 *  y la hora, y marca qué entendió de cada cosa. Todo en la hora de Buenos Aires. */
object Entender {
    enum class Clase { prop, persona, cuando }
    data class Parte(val t: String, val clase: Clase?)
    data class Opcion(val id: String, val titulo: String, val agenteId: String?, val barrio: String?)

    data class Entendido(
        val tipo: String,
        val propiedad: Opcion?,
        val contacto: Opcion?,
        /** yyyy-MM-dd, o null si no dijo el día. */
        val dia: String?,
        /** HH:mm, o null si no dijo la hora. */
        val hora: String?,
        val duracionMin: Int,
        /** Lo que queda como título si no hay propiedad. */
        val titulo: String,
        val partes: List<Parte>,
    )

    private val dias = listOf("domingo", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado")
    private val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    val duracion: Map<String, Int> = mapOf("visita" to 45, "tasacion" to 60, "firma" to 60, "llamado" to 15, "otro" to 30)

    /** Minúsculas sin acentos, con la misma cantidad de letras que el original. */
    fun plano(s: String): String = String(s.map { c ->
        val f = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).lowercase(Locale.ROOT)
        f.firstOrNull() ?: c
    }.toCharArray())

    private data class Tramo(var desde: Int, var hasta: Int, val clase: Clase)
    private data class Hallazgo(val desde: Int, val hasta: Int, val grupos: List<String?>)

    /** Las coincidencias de `patron` en `p`, con sus posiciones. */
    private fun todas(patron: String, p: String, desde: Int = 0): List<Hallazgo> {
        val re = runCatching { Regex(patron) }.getOrNull() ?: return emptyList()
        return re.findAll(p, minOf(desde, p.length)).map { m ->
            Hallazgo(m.range.first, m.range.last + 1, (0 until m.groups.size).map { m.groups[it]?.value })
        }.toList()
    }

    private fun primera(patron: String, p: String, desde: Int = 0): Hallazgo? = todas(patron, p, desde).firstOrNull()
    private fun hay(patron: String, t: String): Boolean = Regex(patron).containsMatchIn(t)
    private fun escapar(s: String): String = Regex.escape(s)

    private fun hoyEnBA(ahora: Instant): Pair<String, Int> {
        val d = Formato.diaDe(ahora)
        return d.toString() to (d.dayOfWeek.value % 7)
    }

    fun sumar(dia: String, n: Int): String {
        val d = runCatching { LocalDate.parse(dia.take(10)) }.getOrNull() ?: return dia
        return d.plusDays(n.toLong()).toString()
    }

    private fun letras(s: String, a: Int, b: Int): String {
        val x = maxOf(0, minOf(a, s.length))
        val y = maxOf(x, minOf(b, s.length))
        return s.substring(x, y)
    }

    fun entender(texto: String, ahora: Instant = Instant.now(), propiedades: List<Opcion> = emptyList(), contactos: List<Opcion> = emptyList(), tipo: String? = null): Entendido {
        val forzado = tipo
        val p = plano(texto)
        val tramos = mutableListOf<Tramo>()
        fun libre(a: Int, b: Int): Boolean = tramos.none { a < it.hasta && b > it.desde }

        // El tipo, por la primera palabra.
        var tipoE = forzado ?: "otro"
        val inicio = p.dropWhile { it.isWhitespace() }
        for ((re, t) in listOf("^(visita|visitar|mostrar)\\b" to "visita", "^(tasacion|tasar)\\b" to "tasacion", "^(firma|firmar)\\b" to "firma", "^(llamar|llamado|llamada)\\b" to "llamado")) {
            if (hay(re, inicio)) tipoE = t
        }
        if (forzado == null && tipoE == "otro") primera("\\b(visita|tasacion|firma)\\b", p)?.grupos?.getOrNull(1)?.let { tipoE = it }

        // El cuándo: día y hora, juntos o por separado.
        val (hoy, dow) = hoyEnBA(ahora)
        var dia: String? = null
        var hora: String? = null
        val reDia = "\\b(?:(?:el|este|esta|el proximo|la proxima|proximo|proxima)\\s+)?(hoy|pasado manana|manana|domingo|lunes|martes|miercoles|jueves|viernes|sabado)\\b|\\b(?:el\\s+)?(\\d{1,2})(?:/(\\d{1,2})|\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre))\\b"
        primera(reDia, p)?.let { md ->
            val w = md.grupos[1]
            if (w != null) {
                if (w == "hoy") dia = hoy
                else if (w == "manana") dia = sumar(hoy, 1)
                else if (w == "pasado manana") dia = sumar(hoy, 2)
                else {
                    val objetivo = dias.indexOf(w)
                    if (objetivo >= 0) {
                        var n = (objetivo - dow + 7) % 7
                        if (n == 0 || hay("proxim", md.grupos[0] ?: "")) n = if (n == 0) 7 else n
                        dia = sumar(hoy, n)
                    }
                }
            } else {
                val d = md.grupos[2]?.toIntOrNull()
                if (d != null) {
                    val m = md.grupos[3]?.toIntOrNull() ?: ((md.grupos[4]?.let { meses.indexOf(it) } ?: -1) + 1)
                    var anio = hoy.take(4).toIntOrNull() ?: 2026
                    val cand = String.format(Locale.US, "%04d-%02d-%02d", anio, m, d)
                    if (cand < hoy) anio += 1
                    if (m in 1..12 && d in 1..31) dia = String.format(Locale.US, "%04d-%02d-%02d", anio, m, d)
                }
            }
            if (dia != null) tramos.add(Tramo(md.desde, md.hasta, Clase.cuando))
        }
        val reHora = "\\b(?:a\\s+las?\\s+|a\\s+la\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:hs?\\b|horas\\b)?(\\s+de\\s+la\\s+(?:manana|tarde|noche))?|\\bal\\s+mediodia\\b"
        for (mh in todas(reHora, p)) {
            val a = mh.desde
            val b = mh.hasta
            val todo = mh.grupos[0] ?: ""
            val sinLas = todo.replace(Regex("^a\\s+las?\\s+"), "")
            val conLas = hay("^a\\s+la", todo) || sinLas.contains("h") || mh.grupos[2] != null || mh.grupos[3] != null
            if (todo.contains("mediodia")) hora = "12:00"
            else if (!conLas || !libre(a, b) || hay("\\d/|\\d\\s+de\\s+[a-z]", letras(p, a, b + 4))) continue
            else {
                var h = mh.grupos[1]?.toIntOrNull() ?: continue
                val mi = mh.grupos[2]?.toIntOrNull() ?: 0
                val t = mh.grupos[3]
                if (t != null && hay("tarde|noche", t) && h < 12) h += 12
                // «a las 5» en una inmobiliaria es a la tarde: de 1 a 7 sin aclarar, se pasa a la tarde.
                if (mh.grupos[3] == null && h >= 1 && h <= 7) h += 12
                if (h > 23 || mi > 59) continue
                hora = String.format(Locale.US, "%02d:%02d", h, mi)
            }
            // El tramo del cuándo abarca día y hora si están pegados («sábado a las 11»).
            val recorte = todo.dropWhile { it.isWhitespace() }
            val a2 = a + (todo.length - recorte.length)
            val i = tramos.indexOfFirst { it.clase == Clase.cuando }
            if (i >= 0) {
                val entre = letras(p, tramos[i].hasta, a2)
                if (entre.all { it.isWhitespace() } || a2 - tramos[i].hasta <= 1) tramos[i].hasta = b
                else tramos.add(Tramo(a2, b, Clase.cuando))
            } else tramos.add(Tramo(a2, b, Clase.cuando))
            break
        }

        // La propiedad: el título hasta la coma («Gorriti 5530»), o la calle sola si es una sola.
        var propiedad: Opcion? = null
        val props = propiedades.map { it to plano(it.titulo.split(",").firstOrNull()?.trim() ?: "") }
        for ((x, clave) in props.sortedByDescending { it.second.length }) {
            if (clave.length < 4) continue
            val i = p.indexOf(clave)
            if (i >= 0 && libre(i, i + clave.length)) {
                propiedad = x
                tramos.add(Tramo(i, i + clave.length, Clase.prop))
                break
            }
        }
        if (propiedad == null) {
            val calles = mutableMapOf<String, MutableList<Opcion>>()
            val orden = mutableListOf<String>()
            for ((x, clave) in props) {
                val calle = clave.replace(Regex("\\s*\\d.*$"), "").trim()
                if (calle.length >= 4) {
                    if (calles[calle] == null) orden.add(calle)
                    calles.getOrPut(calle) { mutableListOf() }.add(x)
                }
            }
            for (calle in orden) {
                val xs = calles[calle] ?: continue
                if (xs.size != 1) continue
                val m = primera("\\b${escapar(calle)}\\b", p) ?: continue
                if (libre(m.desde, m.desde + calle.length)) {
                    propiedad = xs[0]
                    tramos.add(Tramo(m.desde, m.desde + calle.length, Clase.prop))
                    break
                }
            }
        }

        // La persona: «con Inés Robledo», «a Inés», o el nombre en cualquier lado.
        val cs = contactos.map { it to plano(it.titulo) }
        fun buscarNombre(desde: Int): Triple<Opcion, Int, Int>? {
            val resto = letras(p, desde, p.length)
            for ((x, n) in cs.sortedByDescending { it.second.length }) {
                if (n.isEmpty()) continue
                val r = resto.indexOf(n)
                if (r >= 0) {
                    val i = desde + r
                    if (libre(i, i + n.length)) return Triple(x, i, n.length)
                }
            }
            val primeros = mutableMapOf<String, MutableList<Opcion>>()
            val orden = mutableListOf<String>()
            for ((x, n) in cs) {
                val pn = n.split(" ").firstOrNull { it.isNotEmpty() } ?: ""
                if (pn.length >= 3) {
                    if (primeros[pn] == null) orden.add(pn)
                    primeros.getOrPut(pn) { mutableListOf() }.add(x)
                }
            }
            for (pn in orden) {
                val xs = primeros[pn] ?: continue
                if (xs.size != 1) continue
                val m = primera("\\b${escapar(pn)}\\b", resto) ?: continue
                val i = desde + m.desde
                if (libre(i, i + pn.length)) return Triple(xs[0], i, pn.length)
            }
            return null
        }
        var contacto: Opcion? = null
        val con = primera("\\b(con|a|al dueno de|para)\\s+", p)
        val h = con?.let { buscarNombre(it.hasta) } ?: buscarNombre(0)
        if (h != null) {
            contacto = h.first
            tramos.add(Tramo(h.second, h.second + h.third, Clase.persona))
        }

        // Las partes para pintar.
        tramos.sortBy { it.desde }
        val partes = mutableListOf<Parte>()
        var i = 0
        for (t in tramos) {
            if (t.desde > i) partes.add(Parte(letras(texto, i, t.desde), null))
            partes.add(Parte(letras(texto, t.desde, t.hasta), t.clase))
            i = t.hasta
        }
        if (i < texto.length) partes.add(Parte(letras(texto, i, texto.length), null))

        val limpio = texto.trim().replace(Regex("\\s+"), " ")
        return Entendido(tipoE, propiedad, contacto, dia, hora, duracion[tipoE] ?: 30, limpio.take(1).uppercase() + limpio.drop(1), partes)
    }

    /** «2026-11-14» + «11:00» → el instante en Buenos Aires (siempre −03:00). */
    fun instante(dia: String, hora: String): Instant? = Fecha.iso("${dia}T$hora:00-03:00")
}
