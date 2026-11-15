package com.buzzsense.veritas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.components.BuzzIconChip
import com.buzzsense.veritas.app.ui.components.BuzzListRow
import com.buzzsense.veritas.app.ui.components.IconChipTone
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzCard
import com.buzzsense.veritas.app.ui.components.BuzzChecklistItem
import com.buzzsense.veritas.app.ui.components.BuzzDisclaimer
import com.buzzsense.veritas.app.ui.components.BuzzEyebrow
import com.buzzsense.veritas.app.ui.components.BuzzLinkButton
import com.buzzsense.veritas.app.ui.components.BuzzModalBottomSheet
import com.buzzsense.veritas.app.ui.components.BuzzPreviewContainer
import com.buzzsense.veritas.app.ui.components.BuzzPreviews
import com.buzzsense.veritas.app.ui.components.BuzzResultHero
import com.buzzsense.veritas.app.ui.components.BuzzScreenColumn
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSheetContent
import com.buzzsense.veritas.app.ui.components.BuzzTextButton
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Stitch "10 Result Aedes aegypti", "12 Not a dengue mosquito",
 * "22 Not Sure", "23 No Mosquito Heard": one screen for every [ResultKind].
 * "What to do now" is shown when [showWhatToDo]: by default only for a likely Aedes
 * result; the app passes true whenever the agent produced a checklist.
 */
@Composable
fun ResultScreen(
    kind: ResultKind,
    confidence: Float?,
    onWhatToDo: () -> Unit,
    onRecordAgain: () -> Unit,
    modifier: Modifier = Modifier,
    showWhatToDo: Boolean = kind == ResultKind.LikelyAedes,
    peakHz: Float? = null,
) {
    BuzzScreenColumn(
        modifier = modifier,
    ) {
        BuzzEyebrow(stringResource(R.string.result_eyebrow))
        Spacer(Modifier.height(48.dp))
        BuzzResultHero(kind = kind, confidence = confidence, peakHz = peakHz)
        Spacer(Modifier.weight(1f).heightIn(min = BuzzTheme.spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            if (showWhatToDo) {
                BuzzButton(text = stringResource(R.string.result_what_to_do), onClick = onWhatToDo, modifier = Modifier.fillMaxWidth())
                BuzzTextButton(text = stringResource(R.string.result_record_again), onClick = onRecordAgain, modifier = Modifier.fillMaxWidth())
            } else {
                BuzzButton(
                    text = stringResource(R.string.result_record_again),
                    onClick = onRecordAgain,
                    leadingIcon = BuzzIcons.GraphicEq,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BuzzDisclaimer()
        }
    }
}

/** One prevention action shown in the checklist, with its cited source. */
@Immutable
data class ChecklistEntry(
    val id: String,
    val text: String,
    val source: String?,
    val icon: ImageVector,
)

/** Body of Stitch "06 What To Do Now"; reused by the sheet and previews. */
@Composable
fun WhatToDoNowContent(
    entries: List<ChecklistEntry>,
    onOpenOfficialInfo: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = stringResource(R.string.todo_subtitle),
) {
    BuzzSheetContent(
        title = stringResource(R.string.todo_title),
        subtitle = subtitle,
        modifier = modifier,
        actions = {
            BuzzButton(text = stringResource(R.string.todo_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
            BuzzDisclaimer()
        },
    ) {
        BuzzCard(
            contentPadding = PaddingValues(BuzzTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm),
        ) {
            entries.forEach { entry ->
                BuzzListRow(
                    headline = entry.text,
                    supporting = entry.source,
                    leading = { BuzzIconChip(entry.icon, tone = IconChipTone.Neutral, size = 28.dp) },
                )
            }
        }
        if (onOpenOfficialInfo != null) {
            BuzzLinkButton(text = stringResource(R.string.todo_official_info), onClick = onOpenOfficialInfo)
        }
    }
}

/** Modal sheet wrapper (Stitch 11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatToDoNowSheet(
    entries: List<ChecklistEntry>,
    onDismiss: () -> Unit,
    onOpenOfficialInfo: (() -> Unit)?,
    subtitle: String = stringResource(R.string.todo_subtitle),
) {
    BuzzModalBottomSheet(onDismissRequest = onDismiss) {
        WhatToDoNowContent(
            entries = entries,
            onOpenOfficialInfo = onOpenOfficialInfo,
            onDone = onDismiss,
            subtitle = subtitle,
        )
    }
}

/** Sample actions for previews and the component gallery; the app shows the agent's AlertDraft. */
internal val SampleChecklist = listOf(
    ChecklistEntry("drain", "Drain water containers", "WHO · Dengue and severe dengue", BuzzIcons.WaterDrop),
    ChecklistEntry("cover", "Cover water storage", "CDC · Mosquito control at home", BuzzIcons.Shield),
    ChecklistEntry("recycle", "Recycle items that hold water", "PAHO · 3M Plus", BuzzIcons.Recycling),
)

@BuzzScreenPreviews
@Composable
private fun ResultAedesPreview() {
    BuzzPreviewContainer { ResultScreen(ResultKind.LikelyAedes, 0.82f, onWhatToDo = {}, onRecordAgain = {}, peakHz = 584f) }
}

@BuzzScreenPreviews
@Composable
private fun ResultNotDenguePreview() {
    BuzzPreviewContainer { ResultScreen(ResultKind.NotDengue, null, onWhatToDo = {}, onRecordAgain = {}, peakHz = 448f) }
}

@BuzzScreenPreviews
@Composable
private fun ResultNotSurePreview() {
    BuzzPreviewContainer { ResultScreen(ResultKind.NotSure, 0.46f, onWhatToDo = {}, onRecordAgain = {}) }
}

@BuzzScreenPreviews
@Composable
private fun ResultNoMosquitoPreview() {
    BuzzPreviewContainer { ResultScreen(ResultKind.NoMosquito, null, onWhatToDo = {}, onRecordAgain = {}) }
}

@BuzzPreviews
@Composable
private fun WhatToDoNowContentPreview() {
    BuzzPreviewContainer {
        WhatToDoNowContent(
            entries = SampleChecklist,
            onOpenOfficialInfo = {},
            onDone = {},
        )
    }
}
