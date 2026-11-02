package pl.edu.slowscore.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pl.edu.slowscore.data.RepositoryFactory
import pl.edu.slowscore.domain.ScoreController
import pl.edu.slowscore.domain.ScoreRepository

class ScoreViewModel @JvmOverloads constructor(application: Application,
    repository: ScoreRepository = RepositoryFactory.create(application),
    clock: () -> Long = System::currentTimeMillis) : AndroidViewModel(application) {
    val controller = ScoreController(repository, clock)
    init { run { refresh() } }
    fun run(block: suspend ScoreController.() -> Unit) { viewModelScope.launch { controller.block() } }
    fun submit(block: suspend ScoreController.() -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (controller.state.value.busy) return@launch
            controller.block()
            if (controller.state.value.error == null) onSuccess()
        }
    }
    fun watch() = viewModelScope.launch {
        while (isActive) { delay(15_000); controller.refresh() }
    }
}
