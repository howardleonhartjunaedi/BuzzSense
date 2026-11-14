package com.buzzsense.veritas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSheetContent
import com.buzzsense.veritas.app.ui.components.BuzzTextButton
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Stitch "10 Result Aedes aegypti", "12 Not a dengue mosquito",
 * "22 Not Sure", "23 No Mosquito Heard": one screen, four [ResultKind]s.
 * "What to do now" is offered only for a likely Aedes result.
 */
@Composable
fun ResultScreen(
    kind: ResultKind,
    confidence: Float?,
    onWhatToDo: () -> Unit,
    onRecordAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.md),
    ) {
        BuzzEyebrow(stringResource(R.string.result_eyebrow))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BuzzResultHero(kind = kind, confidence = confidence)
        }
        Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            if (kind == ResultKind.LikelyAedes) {
                BuzzButton(text = stringResource(R.string.result_what_to_do), onClick = onWhatToDo, modifier = Modifier.fillMaxWidth())
                BuzzTextButton(text = stringResource(R.string.result_record_again), onClick = onRecordAgain, modifier = Modifier.fillMaxWidth())
            } else {
                BuzzButton(text = stringResource(R.string.result_record_again), onClick = onRecordAgain, modifier = Modifier.fillMaxWidth())
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
    checkedIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onOpenOfficialInfo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuzzSheetContent(
        title = stringResource(R.string.todo_title),
        subtitle = stringResource(R.string.todo_subtitle),
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
                BuzzChecklistItem(
                    text = entry.text,
                    icon = entry.icon,
                    supporting = entry.source,
                    checked = entry.id in checkedIds,
                    onCheckedChange = { onToggle(entry.id, it) },
                )
            }
        }
        BuzzLinkButton(text = stringResource(R.string.todo_official_info), onClick = onOpenOfficialInfo)
    }
}

/** Modal sheet wrapper; owns only the checked-state of the rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatToDoNowSheet(
    entries: List<ChecklistEntry>,
    onDismiss: () -> Unit,
    onOpenOfficialInfo: () -> Unit,
) {
    var checked by remember { mutableStateOf(emptySet<String>()) }
    BuzzModalBottomSheet(onDismissRequest = onDismiss) {
        WhatToDoNowContent(
            entries = entries,
            checkedIds = checked,
            onToggle = { id, on -> checked = if (on) checked + id else checked - id },
            onOpenOfficialInfo = onOpenOfficialInfo,
            onDone = onDismiss,
        )
    }
}

/** Placeholder actions until the agent's AlertDraft is wired in. */
internal val SampleChecklist = listOf(
    ChecklistEntry("drain", "Drain water containers", "WHO · Dengue and severe dengue", BuzzIcons.WaterDrop),
    ChecklistEntry("cover", "Cover water storage", "CDC · Mosquito control at home", BuzzIcons.Shield),
    ChecklistEntry("recycle", "Recycle items that hold water", "PAHO · 3M Plus", BuzzIcons.Recycling),
)

@BuzzScreenPreviews
@Composable
private fun ResultAedesPreview() {
    BuzzPreviewContainer { ResultScreen(ResultKind.LikelyAedes, 0.82f, onWhatToDo = {}, onRecordAgain = {}) }
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
            checkedIds = setOf("drain"),
            onToggle = { _, _ -> },
            onOpenOfficialInfo = {},
            onDone = {},
        )
    }
}
