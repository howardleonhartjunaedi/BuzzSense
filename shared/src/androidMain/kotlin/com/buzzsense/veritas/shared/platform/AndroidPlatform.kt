package com.buzzsense.veritas.shared.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
 * Tier 3: an on-device model through Koog's LiteRT client (added in Koog 1.0). Works in
 * airplane mode. The model is Gemma 4 E2B, `gemma-4-E2B-it.litertlm` (about 2.6 GB) from
 * Hugging Face `litert-community/gemma-4-E2B-it-litert-lm`, under Google's Gemma terms.
 * The app never downloads it by itself: the user (or a developer) supplies the file once and
 * it is kept in app-private storage. Until then [tier] returns null and the chain goes from
 * the APIs straight to the fixed checklist.
 *
 * Developer route (debug build): see `shared/README.md`, "On-device model".
 */
object OnDeviceLlm {
    val DEFAULT_MODEL: LLModel = LiteRTLLModels.Gemma4E2B

    /** Below this size a `.litertlm` file is certainly truncated or not a model. */
    private const val MIN_MODEL_BYTES = 100L * 1024 * 1024

    fun modelsDir(context: Context): File = File(context.filesDir, "llm")

    fun isInstalled(context: Context, model: LLModel = DEFAULT_MODEL): Boolean =
        File(modelsDir(context), model.id).let { it.isFile && it.length() >= MIN_MODEL_BYTES }

    /** App-relative path for `adb shell run-as <package> …` on a debug build. */
    fun installPathForAdb(model: LLModel = DEFAULT_MODEL): String = "files/llm/${model.id}"

    /**
     * Copies a model file the user picked (Storage Access Framework `Uri`, e.g. from
     * `ActivityResultContracts.OpenDocument`) into app-private storage. Writes to a temp
     * file first, so a cancelled or failed copy never leaves a half model behind.
     * Call off the main thread; the file is about 2.6 GB.
     */
    fun install(context: Context, source: Uri, model: LLModel = DEFAULT_MODEL): Result<File> = runCatching {
        val dir = modelsDir(context).apply { mkdirs() }
        val target = File(dir, model.id)
        val tmp = File(dir, model.id + ".part")
        try {
            val input = context.contentResolver.openInputStream(source) ?: error("cannot open the selected file")
            input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out, bufferSize = 1 shl 20) } }
            require(tmp.length() >= MIN_MODEL_BYTES) { "file is too small to be ${model.id} (${tmp.length()} bytes)" }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "could not move the model into place" }
            target
        } finally {
            tmp.delete()
        }
    }

    fun remove(context: Context, model: LLModel = DEFAULT_MODEL): Boolean = File(modelsDir(context), model.id).delete()

    fun tier(
        context: Context,
        model: LLModel = DEFAULT_MODEL,
        modelsDir: File = modelsDir(context),
    ): ProviderTier? {
        val file = File(modelsDir, model.id)
        if (!file.isFile || file.length() < MIN_MODEL_BYTES) return null
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
