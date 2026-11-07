package ar.com.corredor.app.pantallas.entrada

import ar.com.corredor.app.app.PantallaApilada

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.app.Sesion
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.Clerk
import ar.com.corredor.app.nucleo.ClerkMail
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.nonEmpty
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

// Clon de Pantallas/Entrada/Entrada.swift.

/** El error del ingreso dicho en palabras (los mismos mensajes que la web: nunca el código). `Entrada.mensaje` en iOS. */
object Entrada {
    fun mensaje(e: Throwable, proveedor: Boolean = false): String {
        val api = mapOf(
            "cancelado" to "Cancelaste el ingreso. Probá de nuevo cuando quieras.",
            "email_sin_verificar" to "Tu cuenta no tiene un mail verificado.",
            "demasiados_intentos" to "Hubo demasiados intentos. Probá de nuevo en unos minutos.",
            "too_many_requests" to "Hubo demasiados intentos. Probá de nuevo en unos minutos.",
            "sin_sesion" to "El ingreso venció o no es válido. Probá de nuevo.",
            "sin_clerk" to "El ingreso no está disponible en este momento.",
            "sin_clave" to "El ingreso no está disponible en este momento.",
        )
        if (e is Clerk.ErrorClerk) {
            api[e.codigo]?.let { return it }
            // Android: sólo Google (en iOS, «con Google o Apple»).
            return if (proveedor) "No pudimos conectar con Google. Probá de nuevo." else ClerkMail.mensaje(e.codigo)
        }
        if (e is ErrorApi) {
            if (e.sinConexion) return e.detalle
            return api[e.codigo] ?: "No pudimos completar el ingreso. Probá de nuevo."
        }
        return "No pudimos completar el ingreso. Probá de nuevo."
    }
}

/** 1A–1B Bienvenida: «De la consulta al contrato firmado.», Google y mail (en Android no va Apple). En modo «Creá tu
 *  cuenta» (el link de crear la web) dice que es gratis los primeros 30 días. */
@Composable
fun Entrada() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    var conMail by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun entrar(f: suspend () -> Unit) {
        error = null
        ocupado = true
        alcance.launch {
            try {
                f()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = Entrada.mensaje(e, proveedor = true)
            } finally { ocupado = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FondoVerde()
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.size(128.dp).clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                    Imagen(Imagenes.iconoRedondo, Modifier.size(112.dp).sombra(RoundedCornerShape(26.dp), Color.Black.copy(alpha = 0.6f), 30.dp, y = 30.dp).clip(RoundedCornerShape(26.dp)),
                        descripcion = "Corredor")
                }
                val oro = C.oro
                TextoRico(buildAnnotatedString {
                    append("De la consulta al contrato ")
                    withStyle(SpanStyle(color = oro)) { append("firmado.") }
                }, Estilo(36f, Peso.extrabold, -0.045f), Color.White, alinear = TextAlign.Center)
                if (estado.ingresoAlta) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Texto("Creá tu cuenta", Estilo(20f, Peso.bold), Color.White)
                        Texto("Gratis los primeros 30 días. Sin tarjeta.", Estilo.subhead, hex(0xCFE0D8))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // Los botones
            Box(contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().deshabilitado(ocupado, 1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    error?.let { Texto(it, Estilo.footnote, hex(0xF29B92), alinear = TextAlign.Center) }
                    // «Continuar con Apple» no va en Android: el ingreso social es sólo con Google.
                    Tocable({ entrar { estado.ingresarConGoogle() } }, Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.1f))
                                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(15.dp)),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Imagen(Imagenes.logoGoogle, Modifier.height(15.dp))
                            }
                            Texto("Continuar con Google", Estilo(17f, Peso.semibold), Color.White)
                        }
                    }
                    Box(Modifier.heightIn(min = 48.dp).clickable { conMail = true }, contentAlignment = Alignment.Center) {
                        Texto("Entrar con mail", Estilo(16f, Peso.semibold), hex(0xCFE0D8))
                    }
                    Texto(Formato.sinViudas("¿Te invitaron? Abrí el link del mail en este teléfono y entrás directo a tu inmobiliaria."), Estilo.footnote, hex(0xA9C0B7),
                        alinear = TextAlign.Center)
                    Box(Modifier.padding(top = 6.dp)) { PieIngreso(estado.ingresoAlta) { estado.ingresoAlta = false } }
                }
                if (ocupado) Rueda(Color.White)
            }
        }
    }
    Hoja(conMail, { conMail = false }, listOf(Altura.media, Altura.grande)) { IngresoMail() }
    Hoja(estado.recuperarAbierto, { estado.recuperarAbierto = false }) { RecuperarAcceso("") }
}

/** «Al seguir aceptás los Términos y la Política de privacidad.» y, en modo alta, «¿Ya tenés cuenta? Ingresá». */
@Composable
fun PieIngreso(alta: Boolean, claro: Boolean = true, ingresa: () -> Unit = {}) {
    val web = Api.web().trimEnd('/')
    val gris = if (claro) hex(0xA9C0B7) else C.texto3
    val enlace = if (claro) C.oro else C.accion
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val estiloLink = TextLinkStyles(SpanStyle(color = enlace))
        TextoRico(buildAnnotatedString {
            append("Al seguir aceptás los ")
            withLink(LinkAnnotation.Url("$web/terminos", estiloLink)) { append("Términos") }
            append(" y la ")
            withLink(LinkAnnotation.Url("$web/privacidad", estiloLink)) { append("Política de privacidad") }
            append(".")
        }, Estilo.footnote, gris, alinear = TextAlign.Center)
        if (alta) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto("¿Ya tenés cuenta?", Estilo.footnote, gris)
                Texto("Ingresá", Estilo.footnoteFuerte, enlace, Modifier.clickable(onClick = ingresa))
            }
        } else {
            Texto(Formato.sinViudas("Si es tu primera vez, se crea tu cuenta."), Estilo.footnote, gris, alinear = TextAlign.Center)
        }
    }
}

/** «Entrar con mail» (como la web): según la cuenta, la contraseña o un código de 6 números; si es la primera vez se
 *  crea la cuenta (con código, y la contraseña si la pide). «¿No podés entrar?» lleva a recuperar el acceso. */
@Composable
fun IngresoMail() {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var paso by remember { mutableStateOf<ClerkMail.Paso?>(null) }
    var flujo by remember { mutableStateOf(ClerkMail.configurado()) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recuperando by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }

    fun otroMail() {
        paso = null
        clave = ""; codigo = ""; error = null
        flujo = ClerkMail.configurado()
    }

    fun seguir() {
        val f = flujo ?: return
        if (ocupado) return
        if (paso is ClerkMail.Paso.claveNueva) {
            val (largo, comun) = ClerkMail.requisitos(clave)
            if (!largo || !comun) { error = "La contraseña todavía no cumple los requisitos."; return }
        }
        error = null
        ocupado = true
        val actual = paso
        val mail = email.trim()
        alcance.launch {
            try {
                val sig: ClerkMail.Paso = when (actual) {
                    null -> f.empezar(mail)
                    is ClerkMail.Paso.clave -> f.entrarConClave(clave)
                    is ClerkMail.Paso.codigo -> f.confirmarCodigo(codigo)
                    is ClerkMail.Paso.claveNueva -> f.guardarClave(clave)
                    is ClerkMail.Paso.listo -> return@launch
                }
                if (sig is ClerkMail.Paso.listo) {
                    estado.ingresarConJWT(sig.token)
                    cerrar()
                } else {
                    if (sig is ClerkMail.Paso.claveNueva) clave = ""
                    paso = sig
                    runCatching { foco.requestFocus() }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = Entrada.mensaje(e)
            } finally { ocupado = false }
        }
    }

    LaunchedEffect(Unit) { delay(300); runCatching { foco.requestFocus() } }

    Column(Modifier.fillMaxSize().background(C.fondo).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val p = paso
        if (p == null) {
            // El mail
            Texto(if (estado.ingresoAlta) "Creá tu cuenta" else "Entrar con mail", Estilo.titulo, C.texto)
            Texto(if (estado.ingresoAlta) "Gratis los primeros 30 días. Sin tarjeta." else "Si es tu primera vez, se crea tu cuenta.", Estilo.subhead, C.texto2)
            CampoCorredor(email, { email = it }, "tu@mail.com", teclado = KeyboardType.Email, mayusculas = KeyboardCapitalization.None, enfoque = foco,
                accion = ImeAction.Go, alAccion = { seguir() })
            BotonPrincipal(if (ocupado) "Un segundo…" else "Seguir", { seguir() }, habilitado = !(ocupado || !email.contains("@") || flujo == null))
            PieIngreso(estado.ingresoAlta, claro = false) { estado.ingresoAlta = false }
        } else {
            // Volver
            Row(Modifier.clickable { otroMail() }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icono("arrow.left", 15f, C.accion)
                Texto(email, Estilo.subheadFuerte, C.accion)
            }
            when (p) {
                is ClerkMail.Paso.clave -> {
                    Texto("Tu contraseña", Estilo.filaLista, C.texto)
                    CampoCorredor(clave, { clave = it }, secreto = true, enfoque = foco, accion = ImeAction.Go, alAccion = { seguir() })
                    BotonPrincipal(if (ocupado) "Entrando…" else "Entrar", { seguir() }, habilitado = !(ocupado || clave.isEmpty()))
                    Texto("¿No podés entrar?", Estilo.subheadFuerte, C.accion, Modifier.fillMaxWidth().clickable { recuperando = true }, alinear = TextAlign.Center)
                }
                is ClerkMail.Paso.codigo -> {
                    Texto("Te mandamos un código a $email. ${if (p.alta) "Con él creamos tu cuenta." else "Escribilo para entrar."}", Estilo.subhead, C.texto2)
                    Texto("Código", Estilo.filaLista, C.texto)
                    CampoCorredor(codigo, { n ->
                        codigo = n.filter { it.isDigit() }.take(6)
                        if (codigo.length == 6 && !ocupado) seguir()
                    }, "123456", estilo = Estilo(28f, Peso.bold, 8f / 28f), teclado = KeyboardType.NumberPassword, enfoque = foco)
                    BotonPrincipal(if (ocupado) "Revisando…" else if (p.alta) "Crear mi cuenta" else "Entrar", { seguir() }, habilitado = !(ocupado || codigo.length < 6))
                }
                is ClerkMail.Paso.claveNueva -> {
                    Texto("Elegí una contraseña para entrar con tu mail.", Estilo.subhead, C.texto2)
                    Texto("Contraseña", Estilo.filaLista, C.texto)
                    CampoCorredor(clave, { clave = it }, secreto = true, enfoque = foco)
                    RequisitosClave(clave)
                    BotonPrincipal("Guardar y entrar", { seguir() }, habilitado = !ocupado)
                }
                else -> {}
            }
        }
        error?.let { Texto(it, Estilo.footnote, C.error) }
        if (flujo == null) Texto("El ingreso no está disponible en este momento. Probá de nuevo en un rato.", Estilo.footnote, C.error)
    }
    Hoja(recuperando, { recuperando = false }) { RecuperarAcceso(email) }
}

/** Los requisitos de la contraseña nueva, que se tildan solos mientras se escribe (07 · 2c). */
@Composable
fun RequisitosClave(clave: String, distinta: Boolean = false) {
    val (largo, comun) = ClerkMail.requisitos(clave)
    @Composable
    fun fila(ok: Boolean, texto: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono(if (ok) "checkmark.circle.fill" else "circle", 14f, if (ok) C.ok else C.texto3)
            Texto(texto, Estilo.footnote, if (ok) C.ok else C.texto2)
        }
    }
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fila(largo, "10 caracteres o más")
        fila(comun, "No es una de las más usadas")
        if (distinta) fila(false, "Distinta a la anterior")
    }
}

/** Recuperar el acceso (07 · 2b) y la contraseña nueva (2c), con Clerk: un código por mail, con él la contraseña nueva,
 *  se cierran las otras sesiones y se entra. Si el mail no tiene cuenta, dice lo mismo (no se revela quién está
 *  registrado). */
@Composable
fun RecuperarAcceso(email: String) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var mail by remember { mutableStateOf(email) }
    var codigo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var ver by remember { mutableStateOf(false) }
    var paso by remember { mutableStateOf("mail") }
    var enviado by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val flujo = remember { ClerkMail.configurado() }

    fun enviar() {
        val f = flujo ?: return
        if (enviando) return
        if (paso == "nueva") {
            val (largo, comun) = ClerkMail.requisitos(clave)
            if (!largo || !comun) { error = "La contraseña todavía no cumple los requisitos."; return }
        }
        error = null
        enviando = true
        alcance.launch {
            try {
                when (paso) {
                    "mail" -> {
                        val hay = f.pedirRecuperar(mail.trim())
                        enviado = true
                        if (hay) paso = "codigo"
                    }
                    "codigo" -> {
                        val sig = f.confirmarCodigo(codigo, recuperar = true)
                        if (sig is ClerkMail.Paso.claveNueva) paso = "nueva"
                    }
                    else -> {
                        val sig = f.guardarClave(clave)
                        if (sig is ClerkMail.Paso.listo) {
                            estado.ingresarConJWT(sig.token)
                            cerrar()
                        }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = Entrada.mensaje(e)
            } finally { enviando = false }
        }
    }

    Column(Modifier.fillMaxSize().background(C.fondo).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (paso == "nueva") {
            Texto("Elegí una contraseña nueva", Estilo.titulo, C.texto)
            CampoCorredor(clave, { clave = it }, secreto = !ver, mayusculas = KeyboardCapitalization.None) {
                Box(Modifier.size(44.dp).clickable { ver = !ver }.semantics { contentDescription = if (ver) "Ocultar la contraseña" else "Ver la contraseña" },
                    contentAlignment = Alignment.Center) { Icono(if (ver) "eye.slash" else "eye", 17f, C.texto2) }
            }
            RequisitosClave(clave, distinta = true)
            error?.let { Texto(it, Estilo.footnote, C.error) }
            BotonPrincipal(if (enviando) "Guardando…" else "Guardar y entrar", { enviar() }, habilitado = !enviando)
            Texto("Cerramos las otras sesiones abiertas por seguridad.", Estilo.footnote, C.texto3, Modifier.fillMaxWidth(), alinear = TextAlign.Center)
        } else {
            IconoTono("key", Tono.oro, tam = 56.dp)
            Texto("¿No podés entrar?", Estilo.titulo, C.texto)
            // Android: sólo Google (en iOS, «Si entrás con Google o Apple»).
            Texto(Formato.sinViudas("Si entrás con Google, usá ese botón: no necesitás contraseña. Si usás mail, te mandamos un código para crear una nueva."),
                Estilo.subhead, C.texto2)
            if (paso == "mail") {
                CampoCorredor(mail, { mail = it }, "tu@mail.com", teclado = KeyboardType.Email, mayusculas = KeyboardCapitalization.None)
                BotonPrincipal(if (enviando) "Mandando…" else "Mandarme el código", { enviar() }, habilitado = !(enviando || flujo == null || !mail.contains("@")))
            } else {
                Texto("Código que te llegó a $mail", Estilo.filaLista, C.texto)
                CampoCorredor(codigo, { n -> codigo = n.filter { it.isDigit() }.take(6) }, "123456", estilo = Estilo(28f, Peso.bold, 8f / 28f), teclado = KeyboardType.NumberPassword)
                BotonPrincipal(if (enviando) "Revisando…" else "Seguir", { enviar() }, habilitado = !(enviando || codigo.length < 6))
            }
            error?.let { Texto(it, Estilo.footnote, C.error) }
            if (enviado) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.avisoFondo).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icono("envelope.open", 14f, C.texto)
                    Texto(Formato.sinViudas("Listo: si ese mail tiene cuenta, te llega un código en un minuto. Revisá también spam."), Estilo.footnote, C.texto)
                }
            }
            Texto("Volver a ingresar", Estilo.subheadFuerte, C.accion, Modifier.fillMaxWidth().clickable { cerrar() }, alinear = TextAlign.Center)
        }
    }
}

/** Los colores y el rol de `ElegirInmobiliaria` (sus `static` en iOS; `ElegirInmobiliaria.rol` lo usan el Estado y el
 *  perfil). */
object ElegirInmobiliaria {
    val colores: List<Pair<Int, Int>> = listOf(0x1E3A5F to 0xE9DCC3, 0xC9782E to 0xFFFFFF, 0x16443B to 0xF2BB57, 0x6B3FA0 to 0xFFFFFF)

    fun rol(r: String): String = when (r) {
        "duenio" -> "Dueño"
        "agente" -> "Agente"
        else -> "Lector"
    }
}

/** 1C «¿Dónde trabajás hoy?»: las inmobiliarias de la persona. Sin ninguna, crear la suya (con la dirección de la web) o
 *  aceptar una invitación pegando el link. */
@Composable
fun ElegirInmobiliaria() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf<String?>(null) }
    var pegando by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(C.fondo).verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Texto("Hola, ${estado.usuario?.nombre?.takeIf { it.isNotEmpty() } ?: ""}", Estilo.subhead, C.texto2)
            Texto(if (estado.sesion?.inmobiliarias?.isNotEmpty() == true) "¿Dónde trabajás hoy?" else "Creá tu inmobiliaria", Estilo(32f, Peso.extrabold, -0.035f), C.texto)
        }
        val lista = estado.sesion?.inmobiliarias
        if (!lista.isNullOrEmpty()) {
            Column(Modifier.fillMaxWidth().tarjeta()) {
                lista.forEachIndexed { i, m ->
                    key(m.id) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                        Tocable({
                            ocupado = m.id
                            alcance.launch {
                                try { estado.elegir(m) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                                ocupado = null
                            }
                        }) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val c = ElegirInmobiliaria.colores[i % ElegirInmobiliaria.colores.size]
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(hex(c.first)), contentAlignment = Alignment.Center) {
                                    Texto(m.nombre.take(1).uppercase(), Estilo(18f, Peso.bold), hex(c.second))
                                }
                                Column(Modifier.weight(1f)) {
                                    Texto(m.nombre, Estilo(17f, Peso.bold), C.texto)
                                    Texto(ElegirInmobiliaria.rol(m.rol), Estilo.footnote, C.texto2)
                                }
                                if (ocupado == m.id) Rueda() else Chevron()
                            }
                        }
                    }
                }
            }
        } else {
            FormCrearInmobiliaria()
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { pegando = true }, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            Icono("envelope.open", 15f, C.accion)
            Texto("Aceptar una invitación", Estilo.subheadFuerte, C.accion)
        }
        Texto("Cambiás de inmobiliaria cuando quieras desde tu perfil.", Estilo.footnote, C.texto3, Modifier.fillMaxWidth().padding(top = 8.dp), alinear = TextAlign.Center)
        BotonTexto("Salir", { alcance.launch { estado.salir() } }, color = C.texto2)
    }
    Hoja(pegando, { pegando = false }) { PegarInvitacion(enHoja = true, alCerrar = { pegando = false }) }
}

/** Lo de `FormCrearInmobiliaria` que no es la vista (sus `static` en iOS). */
object FormCrearInmobiliaria {
    /** La web de las inmobiliarias (corredor.com.ar, sin app./panel./www.). */
    val dominio: String
        get() {
            val h = runCatching { android.net.Uri.parse(Api.web()).host }.getOrNull() ?: "corredor.com.ar"
            for (p in listOf("app.", "panel.", "www.")) if (h.startsWith(p)) return h.drop(p.length)
            return h
        }

    /** Como `slugDe` de la web: minúsculas, sin acentos, sin «propiedades»/«inmobiliaria», sólo [a-z0-9]. */
    fun slugDe(nombre: String): String {
        val n = nombre.trim()
        var s = Normalizer.normalize(n.lowercase(Formato.argentina), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        s = s.replace("propiedades", "").replace("inmobiliaria", "")
        s = s.filter { letraDeSlug(it) && it != '-' }
        return if (n.isNotEmpty() && s.isNotEmpty()) s else "tuinmobiliaria"
    }

    /** a-z, 0-9 y el guion. */
    fun letraDeSlug(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '-'
}

@Serializable
private data class Disponible(val slug: String, val valido: Boolean, val libre: Boolean)

/** Crear la inmobiliaria (07 · 3C, como /empezar de la web): el nombre y la dirección de la web, que se arma mientras se
 *  escribe, se puede cambiar y dice si está libre. Abajo, la web de muestra. La persona queda como dueña y la sesión
 *  pasa a la nueva (sigue el alta: logo, colores, portada…). */
@Composable
fun FormCrearInmobiliaria() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(estado.nombreSugerido ?: "") }
    var slugPropio by remember { mutableStateOf("") }
    var editandoSlug by remember { mutableStateOf(false) }
    var disponible by remember { mutableStateOf("nada") }
    var enviando by remember { mutableStateOf(false) }
    var errorNombre by remember { mutableStateOf<String?>(null) }
    var errorSlugApi by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focoSlug = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (estado.nombreSugerido != null) estado.nombreSugerido = null }

    val slug = if (slugPropio.isEmpty()) (if (nombre.trim().isEmpty()) "" else FormCrearInmobiliaria.slugDe(nombre)) else slugPropio
    val inicial = nombre.trim().firstOrNull()?.uppercase() ?: "L"
    val errorSlug = errorSlugApi ?: (if (disponible == "tomado") "Esa dirección ya la usa otra inmobiliaria. Probá con otra."
        else if (disponible == "invalido") "Esa dirección no se puede usar. Probá con otra." else null)

    // «Libre»: se pregunta un ratito después de dejar de escribir.
    LaunchedEffect(slug) {
        if (slug.isEmpty()) { disponible = "nada"; return@LaunchedEffect }
        disponible = "mirando"
        try {
            delay(350)
            val d: Disponible = estado.api.get("/v1/inmobiliarias/disponible", listOf("slug" to slug.take(60)))
            disponible = if (!d.valido) "invalido" else if (d.libre) "libre" else "tomado"
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            // Sin señal o sin respuesta: no se dice nada (la API vuelve a mirar al crear).
            disponible = "nada"
        }
    }

    fun crear() {
        error = null; errorNombre = null; errorSlugApi = null
        val n = nombre.trim()
        if (n.isEmpty()) { errorNombre = "Poné el nombre de la inmobiliaria."; return }
        enviando = true
        alcance.launch {
            try {
                estado.crearInmobiliaria(n, slug)
            } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                // El error vuelve al campo, con lo escrito, para no tener que repetirlo.
                if (e.codigo == "slug_tomado") { errorSlugApi = e.detalle; disponible = "tomado" }
                else if (e.estado == 422 || e.estado == 400) error = e.detalle.ifEmpty { e.titulo }
                else estado.avisar(e)
            } catch (e: Exception) { estado.avisar(e) } finally { enviando = false }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Texto("¿Cómo se llama tu inmobiliaria?", Estilo.filaLista, C.texto)
        CampoCorredor(nombre, { n -> nombre = n.take(120); errorNombre = null }, "Luna Propiedades")
        errorNombre?.let { Texto(it, Estilo.footnote, C.error) }
        // Tu web
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto("Tu web", Estilo.filaLista, C.texto)
            val forma = RoundedCornerShape(M.rControl)
            val borde = if (errorSlug == null) Color.Transparent else C.error
            Row(Modifier.fillMaxWidth().campoCorredor().border(1.dp, borde, forma), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editandoSlug) {
                    LaunchedEffect(Unit) { runCatching { focoSlug.requestFocus() } }
                    BasicTextField(slug, { v -> slugPropio = v.lowercase().filter { FormCrearInmobiliaria.letraDeSlug(it) }.take(48); errorSlugApi = null },
                        Modifier.width(IntrinsicSize.Min).widthIn(min = 40.dp).focusRequester(focoSlug), textStyle = Estilo.subheadFuerte.ts().copy(color = C.texto),
                        singleLine = true, cursorBrush = SolidColor(C.accion),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { editandoSlug = false }))
                } else {
                    Box(Modifier.clickable { editandoSlug = true }.semantics { contentDescription = "Cambiar la dirección" }) {
                        androidx.compose.foundation.text.BasicText(slug.ifEmpty { "tuinmobiliaria" }, style = Estilo.subheadFuerte.ts().copy(color = C.texto, textDecoration = TextDecoration.Underline))
                    }
                }
                Texto(".${FormCrearInmobiliaria.dominio}", Estilo.subhead, C.texto2, Modifier.weight(1f, fill = false), lineas = 1)
                Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
                if (disponible == "libre") Icono("checkmark.circle.fill", 17f, C.ok, descripcion = "Libre")
                else if (disponible == "mirando") Rueda(tam = 16.dp)
            }
            if (errorSlug != null) Texto(errorSlug, Estilo.footnote, C.error)
            else if (disponible == "libre") Texto("Libre. Después podés usar tu dominio propio.", Estilo.footnote, C.texto2)
        }
        // Logo
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(C.verde700), contentAlignment = Alignment.Center) { Texto(inicial, Estilo(20f, Peso.bold), C.oro) }
            Column {
                Texto("Logo", Estilo.filaLista, C.texto)
                Texto("Lo subís en el paso que sigue", Estilo.footnote, C.texto2)
            }
        }
        // La web de muestra (en el celular, chica, debajo).
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, C.linea, RoundedCornerShape(14.dp)).clearAndSetSemantics { }) {
            Texto("${slug.ifEmpty { "tuinmobiliaria" }}.${FormCrearInmobiliaria.dominio}", Estilo(11f, Peso.medium), C.texto2,
                Modifier.fillMaxWidth().background(C.hundida).padding(horizontal = 10.dp, vertical = 6.dp), lineas = 1)
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(C.verde700), contentAlignment = Alignment.Center) { Texto(inicial, Estilo(12f, Peso.bold), C.oro) }
                Texto((nombre.nonEmpty ?: "Tu inmobiliaria").uppercase(), Estilo(12f, Peso.bold), C.texto, lineas = 1)
            }
            Box(Modifier.fillMaxWidth().height(70.dp).background(Brush.linearGradient(listOf(C.verde700, C.verde800))))
        }
        error?.let { Texto(it, Estilo.footnote, C.error) }
        BotonPrincipal(if (enviando) "Creando…" else "Seguir: tu logo y tu marca", { crear() },
            habilitado = !(enviando || nombre.trim().isEmpty() || disponible == "tomado" || disponible == "invalido")) {
            if (enviando) Texto("Creando…", Estilo.headline, C.sobreAccion) else TextoIcono("Seguir: tu logo y tu marca", "arrow.right", C.sobreAccion)
        }
    }
}

/** «Crear otra inmobiliaria» (desde «Tus inmobiliarias» del perfil), teniendo ya una. */
@Composable
fun CrearOtraInmobiliaria() {
    PantallaApilada("Creá tu inmobiliaria") {
        Box(Modifier.fillMaxSize()) {
            FondoApp(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FormCrearInmobiliaria()
            }
        }
    }
}
