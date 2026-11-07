package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.app.PantallaApilada

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.inicio.Cifra
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ar.com.corredor.app.pantallas.secciones.ListaGenerica
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// Clon de Pantallas/Propiedades/Importar.swift.

// MARK: - Importar la cartera

/** Una importación (POST /v1/importaciones, GET /v1/importaciones/{id}). `datos` de cada fila es un JSON libre
 *  (JSONLibre en iOS): acá un JsonElement. */
@Serializable
data class ImportacionAPI(
    val id: String,
    val tipo: String? = null,
    val archivo: String? = null,
    val estado: String? = null,
    val columnas: List<Columna>? = null,
    val total: Int? = null,
    val validas: Int? = null,
    val conErrores: Int? = null,
    val conAvisos: Int? = null,
    val filas: List<Fila>? = null,
    val resultado: Resultado? = null,
) {
    @Serializable data class Nota(val campo: String? = null, val mensaje: String = "")
    @Serializable data class Fila(val fila: Int, val datos: Map<String, JsonElement>? = null, val errores: List<Nota>? = null, val avisos: List<Nota>? = null)
    @Serializable data class Resultado(val creadas: Int? = null, val yaEstaban: Int? = null, val conErrores: Int? = null)
    /** Cada columna del archivo y el campo de Corredor que se reconoció (null: no se usa). */
    @Serializable data class Columna(val columna: String, val campo: String? = null, val ejemplo: String? = null)
}

/** El texto de un campo libre (`JSONLibre.texto`). */
private fun JsonElement?.textoLibre(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** `ListaGenerica.titulo(.objeto(datos))`, igual que el iPhone. */
private fun tituloFila(d: Map<String, JsonElement>): String = ListaGenerica.titulo(JsonObject(d))

/** Traer la cartera de otro sistema: un Excel o CSV de propiedades o contactos. Primero la vista previa (cuántas entran,
 *  avisos y errores por fila); después se traen, sin publicar. `retomar`: una vista previa para retomar (la web:
 *  `/importar?id=`). */
@Composable
fun Importar(retomar: String? = null) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    /** @Environment(\.dismiss): Importar se apila (ASeccion "importar"), así que cerrar es volver. */
    val cerrar: () -> Unit = { estado.volver() }
    var tipo by remember { mutableStateOf("propiedades") }
    var subiendo by remember { mutableStateOf(false) }
    var imp by remember { mutableStateOf<ImportacionAPI?>(null) }

    suspend fun subir(u: Uri) {
        val d: ByteArray = try {
            withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: run { estado.avisar("Algo salió mal. Probá de nuevo.", ok = false); return }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e); return }
        if (d.size > 5 shl 20) { estado.avisar("El archivo pesa más de 5 MB.", ok = false); return }
        subiendo = true
        try {
            val nombre = nombreDe(ctx, u)
            val r = estado.api.multipart(
                "/v1/importaciones", listOf("tipo" to tipo), d, nombre,
                if (nombre.substringAfterLast('.', "").lowercase() == "csv") "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
            imp = Api.json.decodeFromString<ImportacionAPI>(String(r))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { subiendo = false }
    }

    suspend fun confirmar(i: ImportacionAPI) {
        subiendo = true
        try {
            imp = estado.api.enviar("POST", "/v1/importaciones/${i.id}/confirmar", emptyMap<String, String>())
            estado.version += 1
            Haptico.exito()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { subiendo = false }
    }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) estado.tarea { subir(u) } }
    fun abrirSelector() {
        elegir.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*"))
    }

    LaunchedEffect(Unit) {
        if (retomar == null || imp != null) return@LaunchedEffect
        try {
            imp = estado.api.get("/v1/importaciones/$retomar")
            tipo = imp?.tipo ?: tipo
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** «Revisá antes de traerla · 38 / 40», con la barra (traídas o válidas sobre el total). */
    @Composable
    fun barra(i: ImportacionAPI, importada: Boolean) {
        val total = maxOf(1, i.total ?: 0)
        val n = if (importada) (i.resultado?.creadas ?: 0) else (i.validas ?: 0)
        val lleno = if (importada) (i.resultado?.creadas ?: 0) + (i.resultado?.yaEstaban ?: 0) else (i.validas ?: 0)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Texto(if (importada) "Trajimos tu cartera" else "Revisá antes de traerla", Estilo.subheadFuerte, C.texto, Modifier.weight(1f))
                Texto("${Formato.numero(n)} / ${Formato.numero(i.total ?: 0)}", Estilo.subheadFuerte, C.texto, numeros = true)
            }
            val e = i.conErrores
            if (!importada && e != null && e > 0) Texto(Formato.cantidad(e, "fila con errores", "filas con errores"), Estilo.footnote, C.errorTinta)
            BarraAvance(lleno.toDouble() / total, color = C.oro)
        }
    }

    /** Las primeras 6 que entran: el precio y la dirección (o su aviso). */
    @Composable
    fun muestra(i: ImportacionAPI) {
        val fichas = (i.filas ?: emptyList()).filter { (it.errores ?: emptyList()).isEmpty() }.take(6)
        if (fichas.isEmpty()) return
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fichas.chunked(2).forEach { par ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    par.forEach { f ->
                        val d = f.datos ?: emptyMap()
                        val numero = (d["precio"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
                        val precio = if (numero != null) Formato.plata(numero, d["moneda"].textoLibre() ?: "USD") else "Sin precio"
                        Column(Modifier.weight(1f).tarjeta(radio = 14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(precio, Estilo.subheadFuerte, C.texto)
                            val a = f.avisos?.firstOrNull()?.mensaje
                            if (a != null) Texto(a, Estilo.footnote, C.avisoTinta, lineas = 2)
                            else Texto(d["direccion"].textoLibre() ?: d["titulo"].textoLibre() ?: "", Estilo.footnote, C.texto2, lineas = 2)
                        }
                    }
                    if (par.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    PantallaApilada("Importar") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val i = imp
            val r = i?.resultado
            if (i != null && r != null) {
                Box(Modifier.padding(top = 30.dp)) {
                    EstadoVacio(
                        icono = "checkmark.circle",
                        titulo = "Listo: ${Formato.cantidad(r.creadas ?: 0, if (tipo == "contactos") "contacto nuevo" else "propiedad nueva", if (tipo == "contactos") "contactos nuevos" else "propiedades nuevas")}",
                        texto = listOfNotNull(
                            if ((r.yaEstaban ?: 0) > 0) "${r.yaEstaban} ya estaban" else null,
                            if ((r.conErrores ?: 0) > 0) "${r.conErrores} con errores quedaron afuera" else null,
                        ).joinToString(" · ").nonEmpty,
                        accion = "Ver la cartera" to {
                            estado.pestana = if (tipo == "contactos") Pestana.inicio else Pestana.propiedades
                            if (tipo == "contactos") estado.abrir(AContactos())
                        },
                    )
                }
                barra(i, importada = true)
                Texto(
                    Formato.sinViudas("${if ((r.yaEstaban ?: 0) > 0) "${Formato.cantidad(r.yaEstaban ?: 0, "ya estaba", "ya estaban")} en tu cartera. " else ""}Quedaron sin publicar: revisalas en Propiedades."),
                    Estilo.footnote, C.texto2,
                )
                if (estado.esDuenio && estado.estructura?.alta?.terminada == false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BotonPrincipal("Seguir: tu equipo", { estado.abrir(ASeccion(clave = "equipo")) }, Modifier.weight(1f))
                        BotonTexto("Lo hago después", { cerrar() }, Modifier.weight(1f))
                    }
                }
            } else if (i != null) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Cifra(etiqueta = "Entran", valor = "${i.validas ?: 0}") }
                    Box(Modifier.weight(1f)) { Cifra(etiqueta = "Con avisos", valor = "${i.conAvisos ?: 0}") }
                    Box(Modifier.weight(1f)) { Cifra(etiqueta = "Con errores", valor = "${i.conErrores ?: 0}") }
                }
                barra(i, importada = false)
                muestra(i)
                val c = i.columnas?.filter { it.campo != null }?.map { it.columna }
                if (!c.isNullOrEmpty()) Texto("Reconocimos: ${c.joinToString(", ")}", Estilo.footnote, C.texto2)
                Column(Modifier.fillMaxWidth().tarjeta()) {
                    (i.filas ?: emptyList()).filter { !(it.errores ?: emptyList()).isEmpty() || !(it.avisos ?: emptyList()).isEmpty() }.take(60).forEachIndexed { n, f ->
                        if (n > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Texto("Fila ${f.fila} · ${tituloFila(f.datos ?: emptyMap())}", Estilo.filaLista, C.texto)
                            (f.errores ?: emptyList()).forEach { e ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icono("xmark.circle", 13f, C.errorTinta); Texto(e.mensaje, Estilo.footnote, C.errorTinta) }
                            }
                            (f.avisos ?: emptyList()).forEach { e ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icono("exclamationmark.triangle", 13f, C.avisoTinta); Texto(e.mensaje, Estilo.footnote, C.avisoTinta) }
                            }
                        }
                    }
                }
                BotonPrincipal(
                    "Traer ${Formato.cantidad(i.validas ?: 0, if (tipo == "contactos") "contacto" else "propiedad", if (tipo == "contactos") "contactos" else "propiedades")}",
                    { estado.tarea { confirmar(i) } }, habilitado = (i.validas ?: 0) != 0 && !subiendo,
                )
                BotonTexto("Elegir otro archivo", { imp = null; abrirSelector() })
            } else {
                Segmentado(listOf("propiedades" to "Propiedades", "contactos" to "Contactos"), tipo, { tipo = it })
                Box(Modifier.padding(top = 20.dp)) {
                    EstadoVacio(
                        icono = "tablecells", titulo = "Traé tu cartera",
                        texto = "Un Excel o un CSV de tu sistema anterior. Primero ves cómo queda y después se trae, sin publicar.",
                        accion = (if (subiendo) "Leyendo…" else "Elegir el archivo") to { abrirSelector() },
                        secundaria = "O captá en la calle con MIRA" to { estado.miraAbierta = true },
                    )
                }
            }
        }
    }
}

/** El nombre del archivo elegido (lastPathComponent en iOS). */
private fun nombreDe(ctx: Context, u: Uri): String =
    runCatching {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: u.lastPathSegment ?: "archivo.xlsx"
