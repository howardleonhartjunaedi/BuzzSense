package com.buzzsense.veritas.app.agent

import android.content.Context
import com.buzzsense.veritas.app.audio.ClipRecorder
import com.buzzsense.veritas.shared.agent.BuzzAlertEngine
import com.buzzsense.veritas.shared.platform.AndroidBuzzAlert
import com.buzzsense.veritas.shared.platform.KeystoreApiKeyStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The engine cannot start in this build; [missing] lists what is not bundled. */
class EngineUnavailableException(val missing: List<String>, cause: Throwable? = null) :
    IllegalStateException("BuzzAlert engine unavailable: ${missing.joinToString()}", cause)

/**
 * Process-wide objects: one [BuzzAlertEngine] (detector, stores, provider chain) shared by
 * every detection run, built lazily off the main thread because opening the stores copies
 * the databases out of the APK on first use.
 *
 * The provider chain is built from the keys saved at creation time, so saving or removing
 * an API key must call [onKeysChanged]; the next run then rebuilds the engine.
 */
class AppContainer(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val app = context.applicationContext
    private val lock = Mutex()
    private var engine: BuzzAlertEngine? = null

    val keyStore: KeystoreApiKeyStore by lazy { KeystoreApiKeyStore(app) }

    val settings: AppSettings by lazy { AppSettings(app) }

    fun newRecorder(): ClipRecorder = ClipRecorder(app)

    /** @throws EngineUnavailableException when required assets or libvec0.so are missing. */
    suspend fun engine(): BuzzAlertEngine = lock.withLock {
        engine ?: withContext(io) {
            val missing = AndroidBuzzAlert.missingFiles(app)
            if (missing.isNotEmpty()) throw EngineUnavailableException(missing)
            try {
                AndroidBuzzAlert.create(app)
            } catch (e: Exception) {
                throw EngineUnavailableException(listOf(e.message ?: e::class.simpleName.orEmpty()), e)
            }
        }.also { engine = it }
    }

    /** Drop the cached engine so the next run picks up new or removed API keys. */
    suspend fun onKeysChanged() = lock.withLock { engine = null }
}
