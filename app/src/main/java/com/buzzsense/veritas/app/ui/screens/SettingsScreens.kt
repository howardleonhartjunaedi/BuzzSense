package com.buzzsense.veritas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzButtonVariant
import com.buzzsense.veritas.app.ui.components.BuzzDisclaimer
import com.buzzsense.veritas.app.ui.components.BuzzEyebrow
import com.buzzsense.veritas.app.ui.components.BuzzGroupedList
import com.buzzsense.veritas.app.ui.components.BuzzListRow
import com.buzzsense.veritas.app.ui.components.BuzzPreviewContainer
import com.buzzsense.veritas.app.ui.components.BuzzScreenHeader
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSwitch
import com.buzzsense.veritas.app.ui.components.BuzzTextField
import com.buzzsense.veritas.app.ui.settings.KeyStatus
import com.buzzsense.veritas.app.ui.settings.SettingsActions
import com.buzzsense.veritas.app.ui.settings.SettingsUiState
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Settings (About tab): the RAG switch and the user's own Gemini API key. Stateless; state and
 * behaviour come from SettingsViewModel. The typed key lives only in plain `remember` (never in
 * saved state) and is cleared once saved.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    var keyInput by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.lg),
    ) {
        BuzzScreenHeader(
            title = stringResource(R.string.settings_title),
            eyebrow = stringResource(R.string.settings_eyebrow),
        )
        BuzzGroupedList {
            BuzzListRow(
                headline = stringResource(R.string.settings_rag_title),
                supporting = stringResource(if (state.useGuidance) R.string.settings_rag_on else R.string.settings_rag_off),
                onClick = { actions.onUseGuidanceChange(!state.useGuidance) },
                trailing = { BuzzSwitch(checked = state.useGuidance, onCheckedChange = actions::onUseGuidanceChange) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzEyebrow(stringResource(R.string.settings_key_section))
            Text(
                stringResource(if (state.keySaved) R.string.settings_key_saved else R.string.settings_key_missing),
                style = BuzzTheme.typography.bodyMedium,
                color = BuzzTheme.colorScheme.onSurfaceVariant,
            )
            BuzzTextField(
                value = keyInput,
                onValueChange = { keyInput = it.trim() },
                placeholder = stringResource(R.string.settings_key_placeholder),
                supportingText = stringResource(R.string.settings_key_privacy),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                enabled = !state.checking,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm), modifier = Modifier.fillMaxWidth()) {
                BuzzButton(
                    text = stringResource(if (keyInput.isNotBlank() || !state.keySaved) R.string.settings_key_save else R.string.settings_key_test),
                    onClick = {
                        if (keyInput.isNotBlank()) { actions.onSaveKey(keyInput); keyInput = "" } else actions.onTestKey()
                    },
                    enabled = !state.checking && (keyInput.isNotBlank() || state.keySaved),
                    modifier = Modifier.weight(1f),
                )
                if (state.keySaved) {
                    BuzzButton(
                        text = stringResource(R.string.settings_key_remove),
                        onClick = actions::onRemoveKey,
                        variant = BuzzButtonVariant.Secondary,
                        enabled = !state.checking,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val status = when (val s = state.keyStatus) {
                null -> if (state.checking) stringResource(R.string.settings_key_checking) else null
                is KeyStatus.Works -> stringResource(R.string.settings_key_works, s.model)
                is KeyStatus.Rejected -> stringResource(R.string.settings_key_rejected, s.message)
                KeyStatus.Offline -> stringResource(R.string.settings_key_offline)
                KeyStatus.Busy -> stringResource(R.string.settings_key_busy)
                KeyStatus.Removed -> stringResource(R.string.settings_key_removed)
            }
            if (status != null) {
                Text(status, style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.onSurface)
            }
        }
        BuzzDisclaimer()
    }
}

private object PreviewSettingsActions : SettingsActions {
    override fun onUseGuidanceChange(enabled: Boolean) = Unit
    override fun onSaveKey(key: String) = Unit
    override fun onTestKey() = Unit
    override fun onRemoveKey() = Unit
}

@BuzzScreenPreviews
@Composable
private fun SettingsNoKeyPreview() {
    BuzzPreviewContainer { SettingsScreen(SettingsUiState(useGuidance = true), PreviewSettingsActions) }
}

@BuzzScreenPreviews
@Composable
private fun SettingsBareGeminiPreview() {
    BuzzPreviewContainer {
        SettingsScreen(
            SettingsUiState(useGuidance = false, keySaved = true, keyStatus = KeyStatus.Works("gemini-3.5-flash-lite")),
            PreviewSettingsActions,
        )
    }
}
