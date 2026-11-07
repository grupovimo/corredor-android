package ar.com.corredor.app

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File

/** Cada pantalla de iOS (Pantallas/<Carpeta>/<Nombre>.swift) tiene su clon en Android (pantallas/<carpeta>/<Nombre>.kt).
 *  La lista la escribe herramientas/paridad.sh (paridad.txt); si falta una, el CI queda en rojo. Mientras estén las
 *  pantallas provisorias (src/provisorio, sólo para compilar el núcleo solo) no se exige. */
class ParidadTest {
    @Test fun cadaPantallaDeIosTieneSuKt() {
        assumeFalse("Todavía con las pantallas provisorias", File("src/provisorio").exists())
        val lista = javaClass.getResource("/paridad.txt")!!.readText().lines().map { it.trim() }.filter { it.endsWith(".swift") }
        assertTrue("paridad.txt vacío", lista.isNotEmpty())
        val base = File("src/main/java/ar/com/corredor/app/pantallas")
        val faltan = lista.filterNot { s ->
            val carpeta = s.substringBeforeLast('/').lowercase()
            File(base, "$carpeta/${s.substringAfterLast('/').removeSuffix(".swift")}.kt").isFile
        }
        assertTrue("Faltan en Android: $faltan", faltan.isEmpty())
    }
}
