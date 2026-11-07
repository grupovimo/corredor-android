package ar.com.corredor.app.tema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

// `.campoCorredor()` de Entrada.swift (lo usan el ingreso, el alta, el perfil, Inicio, la cartera y el chat).

/** `.campoCorredor()` de iOS: un campo de texto del handoff: superficie, radio 12, borde línea 2, 50 de alto. */
@Composable
fun Modifier.campoCorredor(): Modifier {
    val forma = RoundedCornerShape(M.rControl)
    return this.heightIn(min = 50.dp).clip(forma).background(C.superficie).border(1.dp, C.linea2, forma).padding(horizontal = 14.dp)
}

/** Un TextField con `.campoCorredor()`: el texto (17), el placeholder en tinta 3 y lo que vaya a la derecha. */
@Composable
fun CampoCorredor(
    valor: String,
    alCambiar: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    estilo: Estilo = Estilo.body,
    lineasMin: Int = 1,
    lineasMax: Int = 1,
    teclado: KeyboardType = KeyboardType.Text,
    mayusculas: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    secreto: Boolean = false,
    habilitado: Boolean = true,
    enfoque: FocusRequester? = null,
    accion: ImeAction = ImeAction.Default,
    alAccion: (() -> Unit)? = null,
    /** `.autocorrectionDisabled()` de iOS: sin autocorrector, pero con la mayúscula de `mayusculas`. */
    sinCorrector: Boolean = false,
    // Último, para que la lambda final de las llamadas siga siendo `derecha`.
    derecha: (@Composable RowScope.() -> Unit)? = null,
) {
    val color = C.texto
    val gris = C.texto3
    BasicTextField(
        value = valor, onValueChange = alCambiar, enabled = habilitado,
        textStyle = estilo.ts().copy(color = color),
        cursorBrush = SolidColor(C.accion),
        singleLine = lineasMax == 1, minLines = lineasMin, maxLines = lineasMax,
        visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = if (secreto || teclado == KeyboardType.Email || teclado == KeyboardType.Uri) KeyboardCapitalization.None else mayusculas,
            autoCorrectEnabled = !(sinCorrector || secreto || teclado == KeyboardType.Email || teclado == KeyboardType.Uri || mayusculas == KeyboardCapitalization.None),
            keyboardType = teclado, imeAction = accion,
        ),
        keyboardActions = KeyboardActions(onAny = { alAccion?.invoke() }),
        modifier = modifier.fillMaxWidth().then(if (enfoque != null) Modifier.focusRequester(enfoque) else Modifier),
        decorationBox = { campo ->
            Row(Modifier.fillMaxWidth().campoCorredor().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (valor.isEmpty() && placeholder.isNotEmpty()) Texto(placeholder, estilo, gris, lineas = lineasMax)
                    campo()
                }
                derecha?.invoke(this)
            }
        },
    )
}
