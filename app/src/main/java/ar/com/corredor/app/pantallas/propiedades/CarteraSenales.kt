package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.Preferencias
import ar.com.corredor.app.tema.Tono
import ar.com.corredor.app.tema.dia
import ar.com.corredor.app.tema.plata
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// Clon de Pantallas/Propiedades/CarteraSenales.swift.

// Lo que dice cada fila de la cartera (web: lib/propiedades/cartera.ts): la nota del precio, la columna de
// interesados, la línea de atención y el orden «por lo que necesita atención».

/** GET /v1/propiedades/resumen: las cuentas de las vistas de la cartera (sin las unidades de los emprendimientos). */
@Serializable
data class ResumenCartera(
    val total: Int = 0,
    val venta: Int = 0,
    val alquiler: Int = 0,
    val temporario: Int = 0,
    val conError: Int = 0,
    val sinPublicar: Int = 0,
    val sinVistas: Int = 0,
    val sinAgente: Int = 0,
    val mias: Int = 0,
    val destacadas: Int = 0,
    val borradores: Int = 0,
    val barrios: List<Barrio> = emptyList(),
) {
    @Serializable data class Barrio(val barrio: String, val cantidad: Int)
}

/** Los cuatro lugares donde se publica, en el orden de la cartera (web: PORTALES). */
object PortalesCartera {
    data class Portal(val clave: String, val sigla: String, val nombre: String)

    val todos: List<Portal> = listOf(
        Portal("web", "W", "Tu web"), Portal("mercadolibre", "ML", "Mercado Libre"), Portal("zonaprop", "ZP", "Zonaprop"), Portal("argenprop", "AP", "Argenprop"),
    )

    /** ok · err · off de cada portal, para las siglas de la fila. */
    fun estado(p: Propiedad, s: Senal?, portal: String): String {
        if (portal == "web") return if (p.publicada == true) "ok" else "off"
        val e = s?.portales?.get(portal)
        return if (e == "publicada") "ok" else if (e == "error") "err" else "off"
    }
}

/** Las cuentas de días como la web (`diasEntre`): días de calendario de `a` a `b`, en la hora de Argentina. */
fun diasCalendario(a: Instant, b: Instant): Int =
    ChronoUnit.DAYS.between(a.atZone(Formato.zona).toLocalDate(), b.atZone(Formato.zona).toLocalDate()).toInt()

/** «hoy 17:30», «mañana 10:00», «13/11 18:00» (`Senal.cuando` en iOS). */
fun Senal.Companion.cuando(f: Instant, ahora: Instant = Instant.now()): String {
    val d = diasCalendario(ahora, f)
    if (d == 0) return "hoy ${Formato.horaDe(f)}"
    if (d == 1) return "mañana ${Formato.horaDe(f)}"
    val z = f.atZone(Formato.zona)
    return String.format("%02d/%02d ", z.dayOfMonth, z.monthValue) + Formato.horaDe(f)
}

/** La reserva vence a las 12 del día `venceEl`. */
val Senal.venceReserva: Instant? get() = reserva?.venceEl?.let { Fecha.dia(it) }

object Cartera {
    /** Lo que le falta a un borrador para publicarse. */
    fun faltantes(p: Propiedad): List<String> {
        val f = mutableListOf<String>()
        if ((p.fotos ?: emptyList()).isEmpty()) f.add("fotos")
        if (p.superficie == null && p.tipo != "lote") f.add("superficie")
        if ((p.descripcion ?: "").isEmpty()) f.add("descripción")
        if (p.precio == null) f.add("precio")
        return f
    }

    /** «fotos», «fotos y precio», «fotos, precio y descripción». */
    fun lista(xs: List<String>): String =
        if (xs.size < 2) (xs.firstOrNull() ?: "") else xs.dropLast(1).joinToString(", ") + " y " + (xs.lastOrNull() ?: "")

    fun nombrePortal(k: String?): String = PublicacionPortal.nombres[k ?: ""] ?: (k ?: "un portal")

    /** La nota debajo del precio: «Zonaprop la rechazó», «falta fotos y precio», «seña USD 5.000»… */
    fun notaPrecio(p: Propiedad, s: Senal?, ahora: Instant = Instant.now()): Pair<String, Tono> {
        s?.error?.let { e -> return "${nombrePortal(e.portal)} la rechazó" to Tono.rojo }
        if (p.esBorrador) {
            val f = faltantes(p)
            return (if (f.isEmpty()) "lista para publicar" else "falta ${lista(f.take(2))}") to Tono.oro
        }
        val reserva = s?.reserva
        reserva?.sena?.let { sena -> return "seña ${Formato.plata(sena, reserva.senaMoneda ?: "USD")}" to Tono.neutro }
        val antes = s?.precioAnterior; val ahoraP = p.precio; val cambio = s?.precioCambio
        if (antes != null && ahoraP != null && cambio != null && antes > ahoraP) {
            val pct = ((antes - ahoraP) / antes * 100).roundToInt()
            val d = diasCalendario(cambio, ahora)
            return "bajó $pct${Formato.fino}% ${if (d < 1) "hoy" else "hace $d d"}" to Tono.verde
        }
        if (p.operacion == "alquiler") {
            return listOfNotNull("por mes", p.expensas?.let { "exp. ${Formato.numero(it.toInt())}" }).joinToString(" · ") to Tono.neutro
        }
        return "" to Tono.neutro
    }

    /** La columna Interesados: «7 · visita hoy 17:30», «11 · 2 sin responder», «reserva vence mañana». */
    fun interesados(s: Senal?, ahora: Instant = Instant.now()): Pair<String, Boolean> {
        if (s == null) return "—" to false
        s.venceReserva?.let { v ->
            val d = diasCalendario(ahora, v)
            return (if (d <= 0) "reserva vence hoy" else if (d == 1) "reserva vence mañana" else "reserva vence en $d días") to (d <= 1)
        }
        val n = s.interesados ?: 0
        if (n == 0) return (s.proximaVisita?.let { "visita ${Senal.cuando(it, ahora)}" } ?: "—") to (s.proximaVisita != null)
        var cola = ""
        var urgente = false
        val v = s.proximaVisita
        val r = s.sinResponder
        val u = s.ultimaConsulta
        if (v != null) {
            cola = "visita ${Senal.cuando(v, ahora)}"
            urgente = diasCalendario(ahora, v) <= 1
        } else if (r != null && r > 0) {
            cola = "$r sin responder"
            urgente = true
        } else if (u != null) {
            val d = diasCalendario(u, ahora)
            cola = if (d == 0) "última hoy" else if (d == 1) "última ayer" else "última hace $d d"
        }
        return listOf(n.toString(), cola).filter { it.isNotEmpty() }.joinToString(" · ") to urgente
    }

    /** La línea de atención de la tarjeta (o null si no hay nada que hacer): texto, tono e ícono. */
    fun atencion(p: Propiedad, s: Senal?, ahora: Instant = Instant.now()): Triple<String, Tono, String?>? {
        s?.error?.let { e -> return Triple("${nombrePortal(e.portal)} la rechazó", Tono.rojo, "exclamationmark.circle.fill") }
        if (p.esBorrador) {
            val f = faltantes(p)
            return Triple(if (f.isEmpty()) "Borrador · lista para publicar" else "Borrador · faltan ${lista(f.take(2))}", Tono.oro, null)
        }
        val v = s?.proximaVisita
        if (v != null && diasCalendario(ahora, v) <= 1) return Triple("Visita ${Senal.cuando(v, ahora)}", Tono.oro, null)
        val r = s?.sinResponder
        if (r != null && r > 0) return Triple("$r ${if (r == 1) "consulta" else "consultas"} sin responder", Tono.oro, null)
        if (s?.venceReserva != null) {
            val t = interesados(s, ahora).first
            return Triple(t.take(1).uppercase(Formato.argentina) + t.drop(1), Tono.oro, null)
        }
        val n = s?.interesados
        if (n != null && n > 0) return Triple("$n ${if (n == 1) "interesado" else "interesados"}", Tono.neutro, null)
        return null
    }

    /** Mientras más alto, más arriba: error, borrador, visita o reserva cerca, consultas sin responder. */
    fun prioridad(p: Propiedad, s: Senal?, ahora: Instant = Instant.now()): Int {
        var n = 0
        if (s?.error != null) n += 100
        if (p.esBorrador) n += 60
        s?.proximaVisita?.let { if (diasCalendario(ahora, it) <= 1) n += 50 }
        s?.venceReserva?.let { if (diasCalendario(ahora, it) <= 2) n += 45 }
        s?.sinResponder?.let { if (it > 0) n += 40 }
        s?.interesados?.let { if (it > 0) n += minOf(20, it * 2) }
        if (p.estado == "vendida" || p.estado == "alquilada") n -= 30
        return n
    }

    /** El estado de la cartera (web: estadoDe): Disponible, Borrador, Reservada, Vendida, Alquilada, Suspendida. */
    fun estado(p: Propiedad): Pair<String, Tono> {
        if (p.esBorrador) return "Borrador" to Tono.neutro
        return when (p.estado ?: "disponible") {
            "reservada" -> "Reservada" to Tono.oro
            "vendida" -> "Vendida" to Tono.neutro
            "alquilada" -> "Alquilada" to Tono.neutro
            "suspendida" -> "Suspendida" to Tono.neutro
            else -> "Disponible" to Tono.verde
        }
    }
}

/** Los filtros de la cartera: lo que va tal cual a GET /v1/propiedades (como la URL de la web). */
@Serializable
data class FiltrosCartera(
    val q: String = "",
    val operacion: List<String> = emptyList(),
    val tipo: List<String> = emptyList(),
    val estado: List<String> = emptyList(),
    val barrio: List<String> = emptyList(),
    /** Un id del equipo, «yo» (Mías) o «libre» (Sin agente). */
    val agente: String? = null,
    /** «no»: borradores (disponible y sin publicar). */
    val publicada: String? = null,
    /** «error»: con error en un portal. */
    val portal: String? = null,
    /** «30»: sin visitas hace 30 días. */
    val sinVistas: String? = null,
    val precioDesde: String? = null,
    val precioHasta: String? = null,
    val destacada: Boolean = false,
) {
    val vacio: Boolean get() = this == FiltrosCartera()

    val query: List<Pair<String, String>>
        get() {
            val r = mutableListOf<Pair<String, String>>()
            fun y(k: String, v: String?) { if (!v.isNullOrEmpty()) r.add(k to v) }
            y("q", q.trim())
            y("operacion", operacion.joinToString(","))
            y("tipo", tipo.joinToString(","))
            y("estado", estado.joinToString(","))
            y("barrio", barrio.joinToString(","))
            y("agente", agente)
            y("publicada", publicada)
            y("portal", portal)
            y("sinVistas", sinVistas)
            y("precioDesde", precioDesde)
            y("precioHasta", precioHasta)
            if (destacada) y("destacada", "si")
            return r
        }

    companion object {
        /** Saca `v` si está, o lo suma (`alternar(&xs, v)` en iOS: acá devuelve la lista nueva). */
        fun alternar(xs: List<String>, v: String): List<String> = if (v in xs) xs - v else xs + v
    }
}

/** Una vista guardada de la cartera («Belgrano en venta»), en el aparato: la API no las guarda. */
@Serializable
data class VistaGuardada(val nombre: String, val filtros: FiltrosCartera) {
    val id: String get() = nombre

    companion object {
        const val clave = "corredor-cartera-vistas"

        fun todas(): List<VistaGuardada> {
            val d = Preferencias.texto(clave) ?: return emptyList()
            return try { Api.json.decodeFromString<List<VistaGuardada>>(d) } catch (e: Exception) { emptyList() }
        }

        fun guardar(vs: List<VistaGuardada>) {
            try { Preferencias.ponerTexto(clave, Api.json.encodeToString(vs)) } catch (e: Exception) {}
        }
    }
}
