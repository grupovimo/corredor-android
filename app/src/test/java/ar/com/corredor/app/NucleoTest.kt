package ar.com.corredor.app

import ar.com.corredor.app.app.posicionesAcciones
import ar.com.corredor.app.app.tamanoAccion
import ar.com.corredor.app.nucleo.AFichaContacto
import ar.com.corredor.app.nucleo.AFichaPropiedad
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.Clerk
import ar.com.corredor.app.nucleo.ClerkMail
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.Lugares
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.nucleo.Ruta
import ar.com.corredor.app.nucleo.Suscripcion
import ar.com.corredor.app.tema.Acento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/** Los casos del núcleo de CorredorTests y MarcaTests (iOS): rutas de los links, Clerk, la suscripción, el acento de la
 *  marca, las claves de la estructura y el abanico del botón del medio. */
class NucleoTest {
    @Test fun rutasDeEnlaces() {
        assertEquals(AFichaPropiedad("abc"), Ruta("corredor://propiedad/abc")?.destino)
        assertEquals(Pestana.mensajes, Ruta("chat/c1")?.pestana)
        assertEquals(AFichaContacto("x"), Ruta("https://corredor.com.ar/contactos/x")?.destino)
        assertEquals(Pestana.mira, Ruta("corredor://mira")?.pestana)
        assertEquals(ASeccion("consorcio", "e1"), Ruta("/consorcios/e1")?.destino)
    }

    @Test fun clerkFapiDeLaClavePublicable() {
        assertEquals("https://clerk.grupovimo.com.ar", Clerk.fapiDe("pk_live_Y2xlcmsuZ3J1cG92aW1vLmNvbS5hciQ"))
    }

    @Test fun suscripcionDeLaApi() {
        val json = """
        {"estado":"prueba","plan":null,"ciclo":"mes","medio":null,"pruebaHasta":"2026-11-19T12:00:00Z","pagadoHasta":null,
         "vigenteHasta":"2026-11-19T12:00:00Z","diasRestantes":5,"renueva":false,"proximoCobro":null,"importe":null,"moneda":null,
         "esperando":null,"soloLectura":false,"enPrueba":true,"funciones":["ia"],"agentes":{"usados":2,"cupo":null},
         "planes":[{"id":"inicial","nombre":"Inicial","resumen":"x","agentes":1,"precioMes":29900,"precioAnio":299000,"funciones":[],
           "incluye":["1 persona"],"destacado":false,"appleMes":"ar.com.corredor.app.inicial.mes","appleAnio":"ar.com.corredor.app.inicial.anio"}],
         "mercadoPago":false,"cobros":[],"graciaDias":3}
        """
        val s = Api.json.decodeFromString<Suscripcion>(json)
        assertEquals("Prueba gratis · 5 días", s.nombreEstado)
        assertTrue(s.pideAtencion)
        assertEquals("ar.com.corredor.app.inicial.anio", s.planes.first().producto("anio"))
        assertFalse(s.conMercadoPago)
    }

    private val casosAcento: List<Triple<String?, String?, Acento?>> = listOf(
        Triple("#16443b", "#f2bb57", Acento(0x16443B, 0x133C34, 0xF2BB57, 0xDFAC50)),
        Triple("#1e3a5f", null, Acento(0x1E3A5F, 0x1A3354, 0x77869E, 0x6D7B91)),
        Triple("#f5e6a8", null, Acento(0x7D7756, 0x6E694C, 0xF5E6A8, 0xE1D49B)),
        Triple("#e53935", "#ffd54f", Acento(0xD33431, 0xBA2E2B, 0xFFD54F, 0xEBC449)),
        Triple("#000000", null, Acento(0x000000, 0x000000, 0x868686, 0x7B7B7B)),
        Triple("#ffffff", "#ffffff", Acento(0x6F6F6F, 0x626262, 0xFFFFFF, 0xEBEBEB)),
        Triple("#888888", "#333333", Acento(0x737373, 0x656565, 0x8E8E8E, 0x838383)),
        Triple("#7c2d12", "#7c2d12", Acento(0x7C2D12, 0x6D2810, 0xB07F6F, 0xA27566)),
        Triple("azul", null, null),
        Triple(null, null, null),
    )

    @Test fun acentoIgualAlPanel() {
        for ((p, s, esperado) in casosAcento) assertEquals("marca ${p ?: "-"} / ${s ?: "-"}", esperado, Acento.de(p, s))
    }

    private val secciones = listOf("web", "configuracion", "equipo", "plan", "integraciones", "ia", "papelera", "agenda", "avisos", "buscar", "contactos", "operaciones", "reservas", "emprendimientos", "temporada", "cobranzas", "contratos", "rendiciones", "inventarios", "indices", "consorcios", "proveedores", "difusion", "respuestas", "importar", "objetivos", "informe", "revision", "actividad")
    private val rapidas = listOf("captar", "propiedad", "contacto", "visita", "contrato", "cobrar", "web", "buscar", "agenda", "avisos")

    @Test fun conoceTodasLasSecciones() {
        assertEquals("Secciones de la API que la app no sabe abrir", emptyList<String>(), secciones.filter { Lugares.secciones[it] == null })
        assertEquals("Acciones rápidas de la API sin ícono", emptyList<String>(), rapidas.filter { Lugares.rapidas[it] == null })
    }

    /** El abanico del botón amarillo: cada acción (círculo y nombre) entra en la pantalla, arriba de la barra, y no pisa a
     *  otra, de 1 a 8 acciones y en todos los anchos de teléfono. */
    @Test fun abanicoEntraSinPisarse() {
        val (w, h) = tamanoAccion
        for (ancho in listOf(375f, 390f, 393f, 402f, 414f, 430f, 440f)) for (alto in listOf(540f, 700f, 800f)) for (n in 1..8) {
            val p = posicionesAcciones(n, ancho, alto)
            assertEquals(n, p.size)
            for (x in p) assertTrue("$n acciones a ${ancho}x$alto: una queda afuera ($x)",
                x.x - w / 2 >= 0 && x.x + w / 2 <= ancho && x.y - h / 2 >= 0 && x.y + h / 2 <= alto)
            for (a in 0 until n) for (b in a + 1 until n) {
                if (abs(p[a].x - p[b].x) < w && abs(p[a].y - p[b].y) < h) fail("$n acciones a $ancho: la $a y la $b se pisan")
            }
        }
    }

    @Test fun formatoComoElHandoff() {
        assertEquals("\$${Formato.fino}184.350", Formato.pesos(184350))
        assertEquals("12,4${Formato.fino}%", Formato.porcentaje(12.4))
        assertEquals("AL", Formato.iniciales("Almacén Lumbre"))
        assertEquals("1 propiedad", Formato.cantidad(1, "propiedad", "propiedades"))
    }

    @Test fun claveDeOchoCaracteres() {
        assertEquals(true to true, ClerkMail.requisitos("luna2026"))
        assertFalse(ClerkMail.requisitos("luna202").first)
        assertFalse(ClerkMail.requisitos("12345678").second)
    }

    @Test fun planPagadoConOtroMedio() {
        val s = Suscripcion(estado = "activa", plan = "inicial", medio = "apple", renueva = true)
        assertTrue(s.pagaAfuera)
        assertEquals("Se administra donde lo contrataste.", s.comoSePaga)
        assertFalse(s.copy(medio = "google").pagaAfuera)
        assertTrue(s.copy(medio = "mercadopago").pagaAfuera)
        assertFalse(s.copy(renueva = false).pagaAfuera)
    }
}
