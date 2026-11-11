package ar.com.corredor.app.nucleo

/** Los links de corredor.com.ar (el panel) que abren la app (clon de Nucleo/Vinculos.swift). Qué paths los declara
 *  `enlaces.json` de la web (crm-inmobiliaria, web/src/lib; copia en test/resources/pruebas) y el manifest los repite;
 *  la pantalla de cada uno la elige `Ruta.de`. Lo que la app no tiene, o es sólo de la web (impresos, exportes,
 *  legales), se abre en la web dentro de la app: nunca «No encontramos esta sección». */
object Vinculos {
    const val base = "https://corredor.com.ar"

    /** Las secciones que dibuja `Seccion` (Secciones.kt), además de las que Ruta abre con su propia pantalla. */
    val secciones = setOf(
        "mas", "plan", "suscripcion", "configuracion", "inmobiliaria", "equipo", "integraciones", "whatsapp",
        "mercadolibre", "respuestas", "proveedores", "objetivos", "inventarios", "actas", "rendiciones", "papelera",
        "actividad", "revision", "informe", "indices", "vistas", "tarifas", "web", "plantillas", "editor", "difusion",
        "emprendimientos", "emprendimiento", "temporada", "reservas", "consorcios", "consorcio", "importar", "ia",
        "firma", "invitacion", "invitaciones", "empezar",
    )

    /** Lo que vive sólo en la web aunque esté en el panel: impresos, planillas, archivos, endpoints, legales, la
     *  rendición del propietario y las vueltas de OAuth y de Mercado Pago (los «web» de enlaces.json y más). */
    fun soloWeb(camino: String): Boolean {
        val p = camino.split('/').filter { it.isNotEmpty() }
        val a = p.firstOrNull() ?: return true
        if (a in listOf("privacidad", "terminos", "og", "rendicion", "plan", "integraciones", "archivos", "exportar", "guia")) return true
        if (p.size > 1 && p.last() in listOf("ficha", "tarjeta", "imprimir", "excel", "hacer", "accion", "marcar", "lista", "publicar", "sugerencia")) return true
        return "api" in p || "imprimir" in p || "excel" in p || (a == "contratos" && "documentos" in p)
    }

    /** La web dentro de la app, como una sección de Más. */
    fun web(url: String): Ruta = Ruta(Pestana.mas, ASeccion("en-la-web", url))
}

/** Cómo se escribe la ruta en `enlaces.json` («inicio:ACita», «mas:ASeccion/consorcios», «web-en-app»). */
val Ruta.clave: String get() {
    val d = destino ?: return pestana.name
    if (d is ASeccion) return if (d.clave == "en-la-web") "web-en-app" else "${pestana.name}:ASeccion/${d.clave}"
    return "${pestana.name}:${d::class.simpleName}"
}
