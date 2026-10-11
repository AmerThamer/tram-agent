package com.amerthamer.tramagent.data

import com.amerthamer.tramagent.domain.Dk2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A villamos-zavarok közös, memóriában tartott állapota.
 * A főképernyő zavar-sávja és a DK2 képernyő is innen olvas, így nincs dupla lekérés.
 */
object AlertsRepository {

    data class State(
        /** null = még nem volt sikeres lekérés. */
        val alerts: List<Dk2.TramAlert>? = null,
        val loading: Boolean = false,
        val error: String? = null,
        /** Utolsó sikeres lekérés ideje (epoch ms). */
        val fetchedAt: Long? = null,
    )

    private const val MAX_AGE_MS = 2 * 60 * 1000L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val mutex = Mutex()

    /** Frissítés; [force] nélkül a 2 percnél frissebb adatot nem kéri le újra. */
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val current = _state.value
        val fresh = current.fetchedAt?.let { System.currentTimeMillis() - it < MAX_AGE_MS } == true
        if (!force && fresh) return@withLock

        if (!BkkApi.hasApiKey) {
            _state.update { it.copy(loading = false, error = "Nincs beállítva BKK API-kulcs (local.properties → bkk.apiKey).") }
            return@withLock
        }

        _state.update { it.copy(loading = true, error = null) }
        when (val r = Dk2.fetchTramAlerts()) {
            is Dk2.Result.Success -> _state.value =
                State(alerts = r.alerts, fetchedAt = System.currentTimeMillis())
            is Dk2.Result.Failure -> _state.update { it.copy(loading = false, error = r.message) }
        }
    }
}
