package ar.com.corredor.app

import ar.com.corredor.app.nucleo.Ruta
import ar.com.corredor.app.nucleo.clave
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Cada link de `enlaces.json` (copia de la de la web) que abre la app lleva a su pantalla (clon de EnlacesTests.swift), y el
 *  manifest declara exactamente los patrones del JSON (lo mismo que sirve la web en el assetlinks). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnlacesTest {
    private val lista = Json.parseToJsonElement(javaClass.getResource("/pruebas/enlaces.json")!!.readText()).jsonObject
    private val dominio = lista["dominio"]!!.jsonPrimitive.content

    @Test fun cadaLinkAbreSuPantalla() {
        for ((camino, destino) in lista["ejemplos"]!!.jsonObject) {
            val d = destino.jsonPrimitive.content
            if (d == "web") continue
            assertEquals(camino, d, Ruta.de("https://$dominio$camino")?.clave)
        }
    }

    @Test fun otroDominioNoEsDeLaApp() {
        assertNull(Ruta.de("https://otro.com.ar/propiedades/1"))
    }

    @Test fun elManifestDeclaraLoMismoQueElJson() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val android = "http://schemas.android.com/apk/res/android"
        val filtro = (0 until doc.getElementsByTagName("intent-filter").length).map { doc.getElementsByTagName("intent-filter").item(it) as Element }
            .single { it.getAttributeNS(android, "autoVerify") == "true" }
        fun patron(d: Element): String? = d.getAttributeNS(android, "path").ifEmpty { null }
            ?: d.getAttributeNS(android, "pathPrefix").ifEmpty { null }?.let { "$it*" }
            ?: d.getAttributeNS(android, "pathPattern").ifEmpty { null }?.replace(".*", "*")
        val datos = filtro.getElementsByTagName("data")
        val app = mutableSetOf<String>(); val web = mutableSetOf<String>()
        for (i in 0 until datos.length) {
            val d = datos.item(i) as Element
            val p = patron(d) ?: continue
            if ((d.parentNode as Element).tagName == "uri-relative-filter-group") web += p else app += p
        }
        assertEquals(lista["app"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), app)
        assertEquals(lista["web"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), web)
    }
}
