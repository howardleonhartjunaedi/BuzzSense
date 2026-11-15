package com.buzzsense.veritas.app.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User settings kept on this phone (SharedPreferences). No secrets here; API keys live in the Keystore. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("buzzsense_settings", Context.MODE_PRIVATE)

    private val _useGuidance = MutableStateFlow(prefs.getBoolean(KEY_USE_GUIDANCE, true))

    /**
     * true: the agent retrieves guidance from guidance.db and every action cites it (RAG).
     * false: bare Gemini, no retrieval and no citations; the health-safety checks still run.
     */
    val useGuidance: StateFlow<Boolean> = _useGuidance.asStateFlow()

    fun setUseGuidance(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_GUIDANCE, enabled).apply()
        _useGuidance.value = enabled
    }

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))

    /** true once the walkthrough has been finished or skipped; it then no longer shows at launch. */
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    fun setOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        _onboarded.value = true
    }

    private companion object {
        const val KEY_USE_GUIDANCE = "use_guidance"
        const val KEY_ONBOARDED = "onboarded"
    }
}
