package com.buzzsense.veritas.app.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.buzzsense.veritas.app.agent.AppContainer
import com.buzzsense.veritas.shared.llm.CloudProviders
import com.buzzsense.veritas.shared.llm.FailureKind
import com.buzzsense.veritas.shared.llm.KeyCheck
import com.buzzsense.veritas.shared.llm.check
import com.buzzsense.veritas.shared.platform.KeystoreApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of the last key action, mapped to strings by the screen. */
sealed interface KeyStatus {
    data class Works(val model: String) : KeyStatus
    data class Rejected(val message: String) : KeyStatus
    data object Offline : KeyStatus
    data object Busy : KeyStatus
    data object Removed : KeyStatus
}

@Immutable
data class SettingsUiState(
    val useGuidance: Boolean = true,
    val keySaved: Boolean = false,
    val checking: Boolean = false,
    val keyStatus: KeyStatus? = null,
)

/** Behaviour the settings screen needs; separate so the stateless screen keeps simple previews. */
interface SettingsActions {
    fun onUseGuidanceChange(enabled: Boolean)
    fun onSaveKey(key: String)
    fun onTestKey()
    fun onRemoveKey()
}

/**
 * Settings: the RAG switch (AppSettings) and the user's own Gemini key. The key is encrypted
 * with the Android Keystore and never shown again; "test" sends one short text prompt through
 * Koog. Every key change rebuilds the engine so the next detection uses it.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel(), SettingsActions {

    private val store: KeystoreApiKeyStore get() = container.keyStore
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load(KeystoreApiKeyStore.Provider.GEMINI) != null }
            _state.update { it.copy(keySaved = saved) }
        }
        viewModelScope.launch {
            container.settings.useGuidance.collect { on -> _state.update { it.copy(useGuidance = on) } }
        }
    }

    override fun onUseGuidanceChange(enabled: Boolean) = container.settings.setUseGuidance(enabled)

    override fun onSaveKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || _state.value.checking) return
        runCheck {
            withContext(Dispatchers.IO) { store.save(KeystoreApiKeyStore.Provider.GEMINI, trimmed) }
            container.onKeysChanged()
            _state.update { it.copy(keySaved = true) }
            trimmed
        }
    }

    override fun onTestKey() {
        if (_state.value.checking) return
        runCheck { withContext(Dispatchers.IO) { store.load(KeystoreApiKeyStore.Provider.GEMINI) } }
    }

    override fun onRemoveKey() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.remove(KeystoreApiKeyStore.Provider.GEMINI) }
            container.onKeysChanged()
            _state.update { it.copy(keySaved = false, keyStatus = KeyStatus.Removed) }
        }
    }

    private fun runCheck(key: suspend () -> String?) {
        _state.update { it.copy(checking = true, keyStatus = null) }
        viewModelScope.launch {
            val status = try {
                val k = key() ?: return@launch _state.update { it.copy(checking = false) }
                when (val r = withContext(Dispatchers.IO) { CloudProviders.gemini(k).check() }) {
                    is KeyCheck.Ok -> KeyStatus.Works(r.model)
                    is KeyCheck.Failed -> when (r.kind) {
                        FailureKind.AUTH, FailureKind.BAD_REQUEST -> KeyStatus.Rejected(r.message)
                        FailureKind.NO_NETWORK -> KeyStatus.Offline
                        FailureKind.TRANSIENT -> KeyStatus.Busy
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KeyStatus.Rejected(e.message ?: e::class.simpleName.orEmpty())
            }
            _state.update { it.copy(checking = false, keyStatus = status) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
