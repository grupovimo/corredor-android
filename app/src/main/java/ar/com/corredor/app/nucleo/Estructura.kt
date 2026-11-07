package ar.com.corredor.app.nucleo

import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.Spotlight
import ar.com.corredor.app.tema.Acento
import kotlinx.serialization.Serializable

/** GET /v1/panel/estructura: «Mi inmobiliaria» y «Herramientas», las acciones rápidas del botón del medio, el alta
 *  (pasos y porcentaje) y la marca publicada. La arma la API, igual para la app y para el panel web; acá sólo se sabe el
 *  ícono y a dónde lleva cada clave (las claves, en docs/estructura-claves.json de crm-inmobiliaria). */
@Serializable
data class Estructura(
    val partes: List<Parte> = emptyList(),
    val rapidas: List<Rapida> = emptyList(),
    val alta: Alta = Alta(),
    val rubros: List<String> = emptyList(),
    val escondido: Boolean = false,
    val marca: Marca? = null,
) {
    @Serializable data class Item(val clave: String, val nombre: String, val palabras: List<String> = emptyList()) { val id: String get() = clave }
    @Serializable data class Grupo(val titulo: String = "", val items: List<Item> = emptyList())
    @Serializable data class Parte(val clave: String, val titulo: String, val grupos: List<Grupo> = emptyList()) { val id: String get() = clave }
    @Serializable data class Rapida(val clave: String, val nombre: String) { val id: String get() = clave }
    @Serializable data class Marca(val logoUrl: String? = null, val colorPrimario: String? = null, val colorSecundario: String? = null)

    val logo: String? get() = marca?.logoUrl?.nonEmpty
}

/** El alta de la inmobiliaria: los pasos del asistente (los mismos que el panel), el porcentaje, lo que falta y si ya
 *  tiene lo mínimo (logo y WhatsApp) para entrar. */
@Serializable
data class Alta(
    val pasos: List<Paso> = emptyList(),
    val completo: Int = 0,
    val faltan: List<String> = emptyList(),
    val minimoListo: Boolean = true,
    val terminada: Boolean = true,
) {
    @Serializable
    data class Paso(val clave: String, val titulo: String, val texto: String = "", val obligatorio: Boolean = false, val peso: Int = 0, val hecho: Boolean = false) {
        val id: String get() = clave
    }
}

/** Ícono y destino de cada clave de la estructura (lo único que la app sabe de cada sección). */
object Lugares {
    val secciones: Map<String, Pair<String, Any>> = mapOf(
        "web" to ("globe" to ASeccion("web")),
        "configuracion" to ("storefront" to ASeccion("configuracion")),
        "equipo" to ("person.3" to ASeccion("equipo")),
        "plan" to ("crown" to ASeccion("plan")),
        "integraciones" to ("puzzlepiece.extension" to ASeccion("integraciones")),
        "ia" to ("sparkles" to ASeccion("ia")),
        "papelera" to ("trash" to ASeccion("papelera")),
        "agenda" to ("calendar" to AAgendaDia()),
        "avisos" to ("bell" to AAvisos()),
        "buscar" to ("magnifyingglass" to ABuscar()),
        "contactos" to ("person.2" to AContactos()),
        "operaciones" to ("rectangle.split.3x1" to AOperaciones()),
        "reservas" to ("hand.raised" to ASeccion("reservas")),
        "emprendimientos" to ("building" to ASeccion("emprendimientos")),
        "temporada" to ("beach.umbrella" to ASeccion("temporada")),
        "cobranzas" to ("dollarsign.circle" to ACobranzas()),
        "contratos" to ("doc.text" to AContratos()),
        "rendiciones" to ("doc.plaintext" to ASeccion("rendiciones")),
        "inventarios" to ("checklist" to ASeccion("inventarios")),
        "indices" to ("chart.line.uptrend.xyaxis" to ASeccion("indices")),
        "consorcios" to ("building.columns" to ASeccion("consorcios")),
        "proveedores" to ("wrench.and.screwdriver" to ASeccion("proveedores")),
        "difusion" to ("megaphone" to ASeccion("difusion")),
        "respuestas" to ("quote.bubble" to ASeccion("respuestas")),
        "importar" to ("square.and.arrow.down" to ASeccion("importar")),
        "objetivos" to ("flag" to ASeccion("objetivos")),
        "informe" to ("chart.bar" to ASeccion("informe")),
        "revision" to ("checkmark.shield" to ASeccion("revision")),
        "actividad" to ("clock.arrow.circlepath" to ASeccion("actividad")),
    )

    /** Las acciones del botón del medio: el ícono (las abre `AccionesRapidas`). */
    val rapidas: Map<String, String> = mapOf(
        "captar" to "viewfinder", "propiedad" to "house", "contacto" to "person.badge.plus", "visita" to "calendar.badge.plus",
        "contrato" to "doc.badge.plus", "cobrar" to "dollarsign.circle", "web" to "paintbrush",
        "buscar" to "magnifyingglass", "agenda" to "calendar", "avisos" to "bell",
    )
}

/** Abre el alta (el asistente) en un paso: desde el Inicio, «Más» o la primera vez. */
data class PasoAlta(val clave: String?) { val id: String get() = clave ?: "-" }

/** Pide la estructura (con «Mostrar todas las secciones», sin esconder por rubros) y pone el acento. */
suspend fun Estado.cargarEstructura() {
    if (sesion?.inmobiliariaId == null) return
    val todas = Preferencias.bool("todasLasSecciones")
    val e = api.opcional<Estructura>("/v1/panel/estructura", if (todas) listOf("todas" to "1") else emptyList()) ?: return
    estructura = e
    Guardado.guardar(e, "estructura")
    ponerAcento(Acento.de(e.marca?.colorPrimario, e.marca?.colorSecundario))
    Spotlight.indexarSecciones(duenio = esDuenio)
    // Una inmobiliaria nueva sin lo mínimo (logo y WhatsApp): el dueño sigue el alta antes que nada.
    if (esDuenio && !e.alta.terminada && !e.alta.minimoListo && altaAbierta == null) altaAbierta = PasoAlta(null)
}

/** Cambia el acento; si cambió, repinta la app entera (los colores dinámicos lo leen al pintarse). */
fun Estado.ponerAcento(a: Acento?) {
    if (a == Acento.actual) return
    Acento.actual = a
    marcaVersion += 1
}
