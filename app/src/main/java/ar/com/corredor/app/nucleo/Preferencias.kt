package ar.com.corredor.app.nucleo

import android.content.Context
import android.content.SharedPreferences

/** UserDefaults de iOS: lo que la app recuerda en este teléfono (no secreto). */
object Preferencias {
    internal var contexto: Context? = null
    private val p: SharedPreferences? get() = contexto?.getSharedPreferences("preferencias", Context.MODE_PRIVATE)
    private val memoria = HashMap<String, Any?>() // en los tests, sin contexto

    fun texto(k: String): String? = p?.getString(k, null) ?: memoria[k] as? String
    fun ponerTexto(k: String, v: String?) { p?.edit()?.putString(k, v)?.apply() ?: memoria.put(k, v) }
    fun bool(k: String): Boolean = p?.getBoolean(k, false) ?: (memoria[k] as? Boolean ?: false)
    fun ponerBool(k: String, v: Boolean) { p?.edit()?.putBoolean(k, v)?.apply() ?: memoria.put(k, v) }
    fun entero(k: String): Int = p?.getInt(k, 0) ?: (memoria[k] as? Int ?: 0)
    fun ponerEntero(k: String, v: Int) { p?.edit()?.putInt(k, v)?.apply() ?: memoria.put(k, v) }
    fun lista(k: String): List<String> = p?.getStringSet(k, null)?.toList() ?: (memoria[k] as? List<String> ?: emptyList())
    fun ponerLista(k: String, v: List<String>) { p?.edit()?.putStringSet(k, v.toSet())?.apply() ?: memoria.put(k, v) }
    fun borrarTodo() { p?.edit()?.clear()?.apply(); memoria.clear() }
}
