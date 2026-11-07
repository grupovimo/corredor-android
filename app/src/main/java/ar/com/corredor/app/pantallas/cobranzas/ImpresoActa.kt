package ar.com.corredor.app.pantallas.cobranzas

import ar.com.corredor.app.nucleo.*
import java.io.File

/** El acta de entrega o devolución como la imprime la web (14): el membrete con la matrícula, el
 *  título, la dirección y la fecha, las partes por nombre, las observaciones, la tabla de los ítems
 *  congelados al labrarla (`acta.items`: ambiente, ítem, cantidad, nota, estado —en mayúsculas si es
 *  regular o malo— y el código de la foto F-01…) y las tres firmas. Sale como PDF (PdfDocument). */
suspend fun Documentos.actaFirmada(
    a: Acta,
    direccion: String,
    barrio: String?,
    inmobiliaria: InmobiliariaActa?,
    inquilino: String?,
    propietario: String?,
    firmaInmobiliaria: String,
    api: Api,
): File? {
    val inmo = inmobiliaria
    var nombreInmo = inmo?.nombre
    if (nombreInmo == null) nombreInmo = Documentos.inmobiliaria(api)?.nombre
    val entrada = a.tipo != "salida"
    var lugar = direccion
    barrio?.takeIf { it.isNotBlank() }?.let { lugar += " · $it" }
    Fecha.fecha(a.fecha)?.let { lugar += " · ${Formato.diaCompleto(it)}" }
    val intro = mutableListOf<String>()
    inmo?.matricula?.let { intro.add(it) }
    val partes = mutableListOf<String>()
    inquilino?.takeIf { it.isNotBlank() }?.let { partes.add("Inquilina/o: $it.") }
    propietario?.takeIf { it.isNotBlank() }?.let { partes.add("Propietario: $it.") }
    if (partes.isNotEmpty()) intro.add(partes.joinToString(" "))
    val bloques = mutableListOf<Documento.Bloque>()
    if (intro.isNotEmpty()) bloques.add(Documento.Bloque(titulo = null, texto = intro.joinToString("\n")))
    a.observaciones?.takeIf { it.isNotBlank() }?.let { bloques.add(Documento.Bloque(titulo = "Observaciones", texto = it)) }
    val filas: List<Pair<String, String>> = Firmas.filas(a.items ?: emptyList()).map { f ->
        val ojo = f.item.estado == "regular" || f.item.estado == "malo"
        val est = if (ojo) Inv.nombre(f.item.estado).uppercase(Formato.argentina) else Inv.nombre(f.item.estado)
        "${f.item.ambiente} · ${Firmas.textoItem(f.item)}" to "$est · ${f.codigo}"
    }
    if (filas.isEmpty()) {
        bloques.add(Documento.Bloque(titulo = "Ítems", texto = "El inventario estaba vacío cuando se labró el acta."))
    } else {
        bloques.add(Documento.Bloque(titulo = "Ambiente · ítem — estado · foto", filas = filas))
    }
    val firmas = listOf("inquilino" to "Inquilina/o", "propietario" to "Propietario", "inmobiliaria" to firmaInmobiliaria).map { (k, n) ->
        Documento.Firma(parte = n, nombre = a.firmas?.get(k)?.nombre, trazo = a.firmas?.get(k)?.trazo)
    }
    val d = Documento(
        titulo = if (entrada) "Acta de entrega e inventario" else "Acta de devolución e inventario",
        subtitulo = lugar, numero = "ACTA · ${Inv.numerica(a.fecha)}", bloques = bloques, firmas = firmas,
        pie = "Al firmar, las partes aceptan el estado de cada ambiente tal como quedó registrado, con sus fotos. Hecha con Corredor",
    )
    return Impresor.pdf(d, inmobiliaria = nombreInmo ?: "Corredor", nombre = "Acta $direccion")
}
