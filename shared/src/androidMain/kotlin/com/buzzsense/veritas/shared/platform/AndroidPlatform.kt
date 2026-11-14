package com.buzzsense.veritas.shared.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ai.koog.prompt.executor.clients.litert.LiteRTClientConfig
import ai.koog.prompt.executor.clients.litert.LiteRTLLMClient
import ai.koog.prompt.executor.clients.litert.LiteRTLLModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import com.buzzsense.veritas.shared.llm.NetworkStatus
import com.buzzsense.veritas.shared.llm.ProviderTier
import com.buzzsense.veritas.shared.model.ProviderTierId
import java.io.File

/** Android connectivity check used by the failover executor ("No network → straight to on-device"). */
class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/**
 * Tier 3: an on-device model through Koog's LiteRT client (added in Koog 1.0).
 * Works in airplane mode. The `.litertlm` file is downloaded once into
 * [modelsDir] (app-private storage); returns null when it is not present yet,
 * in which case the chain goes straight from the APIs to the fixed checklist.
 */
object OnDeviceLlm {
    fun tier(
        context: Context,
        model: LLModel = LiteRTLLModels.Gemma4E2B,
        modelsDir: File = File(context.filesDir, "llm"),
    ): ProviderTier? {
        if (!File(modelsDir, model.id).exists()) return null
        val client = LiteRTLLMClient(
            LiteRTClientConfig(
                defaultModel = model,
                modelsPath = modelsDir.absolutePath,
                cacheDir = context.cacheDir.absolutePath,
            )
        )
        return ProviderTier(
            id = ProviderTierId.ON_DEVICE,
            executor = MultiLLMPromptExecutor(client),
            model = model,
            requiresNetwork = false,
        )
    }
}
