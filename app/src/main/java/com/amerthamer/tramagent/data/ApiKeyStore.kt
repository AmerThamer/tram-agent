package com.amerthamer.tramagent.data

import android.content.Context
import android.content.SharedPreferences
import com.amerthamer.tramagent.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A BKK API-kulcs forrása: elsősorban az appban (Beállítások) megadott kulcs,
 * ha az nincs, akkor a buildbe a local.properties-ből került kulcs.
 * Az appban megadott kulcs csak a készüléken, az app privát beállításai között tárolódik.
 */
object ApiKeyStore {

    private const val PREFS = "settings"
    private const val KEY_BKK = "bkk_api_key"

    private var prefs: SharedPreferences? = null
    private val _userKey = MutableStateFlow<String?>(null)
    /** Az appban megadott kulcs (null, ha nincs). */
    val userKey: StateFlow<String?> = _userKey.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _userKey.value = p.getString(KEY_BKK, null)?.takeIf { it.isNotBlank() }
    }

    fun setUserKey(key: String?) {
        val clean = key?.trim()?.takeIf { it.isNotEmpty() }
        prefs?.edit()?.apply { if (clean == null) remove(KEY_BKK) else putString(KEY_BKK, clean) }?.apply()
        _userKey.value = clean
    }

    /** A buildbe fordított kulcs, ha valódi (nem helykitöltő). */
    val buildKey: String? get() = BuildConfig.BKK_API_KEY.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }

    /** Az éppen érvényes kulcs. */
    val current: String? get() = _userKey.value ?: buildKey
}
