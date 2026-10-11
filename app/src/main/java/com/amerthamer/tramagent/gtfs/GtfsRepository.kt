package com.amerthamer.tramagent.gtfs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A GTFS-frissítés állapota alkalmazásszinten: a letöltés akkor is folytatódik,
 * ha a felhasználó közben elnavigál a képernyőről.
 */
object GtfsRepository {

    sealed interface State {
        data object Idle : State
        /** [progress]: 0..1, vagy null, ha nem ismert a teljes méret; [bytes]: eddig letöltve. */
        data class Running(val progress: Float?, val bytes: Long = 0) : State
        /** A letöltés után a villamos-index építése (DK3 gyorsításához). */
        data class Indexing(val progress: Float) : State
        data class Done(val result: GtfsUpdater.UpdateResult) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(context: Context) {
        if (_state.value is State.Running || _state.value is State.Indexing) return
        val app = context.applicationContext
        _state.value = State.Running(null)
        scope.launch {
            _state.value = try {
                val result = GtfsUpdater.update(app) { p, bytes -> _state.value = State.Running(p, bytes) }
                TramIndex.rebuild(GtfsUpdater.gtfsDir(app)) { p -> _state.value = State.Indexing(p) }
                State.Done(result)
            } catch (e: Exception) {
                Log.e("GTFS", "Hiba a frissítés közben", e)
                State.Failed(e.message ?: "Ismeretlen hiba")
            }
        }
    }
}
