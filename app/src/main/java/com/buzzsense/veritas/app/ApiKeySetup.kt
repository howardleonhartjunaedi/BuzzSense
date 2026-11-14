package com.buzzsense.veritas.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.shared.llm.CloudProviders
import com.buzzsense.veritas.shared.llm.FailureKind
import com.buzzsense.veritas.shared.llm.KeyCheck
import com.buzzsense.veritas.shared.llm.check
import com.buzzsense.veritas.shared.platform.KeystoreApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-launch setup for the Gemini tier: the user pastes their own Google AI Studio
 * key (https://aistudio.google.com/apikey). It is encrypted with a Keystore key and
 * never compiled into the APK. "Save and test" sends one short text prompt through Koog.
 */
@Composable
fun GeminiKeySetup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { KeystoreApiKeyStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var saved by remember { mutableStateOf(store.load(KeystoreApiKeyStore.Provider.GEMINI) != null) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (saved) "A Gemini key is saved on this phone." else "No Gemini key yet.") }
    var busy by remember { mutableStateOf(false) }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gemini (Google AI Studio)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste your own API key. It stays encrypted on this phone. Only labels, weather and " +
                "guidance text are sent to Gemini, never your recording.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.trim() },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && (input.isNotBlank() || saved),
                onClick = {
                    busy = true
                    status = "Checking…"
                    scope.launch {
                        val key = input.ifBlank { store.load(KeystoreApiKeyStore.Provider.GEMINI).orEmpty() }
                        val result = withContext(Dispatchers.IO) {
                            if (input.isNotBlank()) store.save(KeystoreApiKeyStore.Provider.GEMINI, key)
                            CloudProviders.gemini(key).check()
                        }
                        saved = true
                        input = ""
                        status = when (result) {
                            is KeyCheck.Ok -> "Key works with ${result.model}."
                            is KeyCheck.Failed -> when (result.kind) {
                                FailureKind.AUTH, FailureKind.BAD_REQUEST -> "Gemini rejected the key or model: ${result.message}"
                                FailureKind.NO_NETWORK -> "No connection. The key is saved; alerts will use the on-device model until you are online."
                                FailureKind.TRANSIENT -> "Gemini is busy or rate-limited. The key is saved; try again later."
                            }
                        }
                        busy = false
                    }
                },
            ) { Text(if (input.isNotBlank()) "Save and test" else "Test saved key") }
            if (saved) {
                OutlinedButton(enabled = !busy, onClick = {
                    store.remove(KeystoreApiKeyStore.Provider.GEMINI)
                    saved = false
                    status = "Key removed."
                }) { Text("Remove") }
            }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text("This project is a prototype and is not medical advice.", style = MaterialTheme.typography.labelSmall)
    }
}
