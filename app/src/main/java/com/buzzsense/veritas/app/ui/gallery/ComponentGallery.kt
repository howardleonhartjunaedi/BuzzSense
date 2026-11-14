package com.buzzsense.veritas.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.components.BuzzActionCard
import com.buzzsense.veritas.app.ui.components.BuzzAlertDialog
import com.buzzsense.veritas.app.ui.components.BuzzBackButton
import com.buzzsense.veritas.app.ui.components.BuzzBottomNavBar
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzButtonVariant
import com.buzzsense.veritas.app.ui.components.BuzzCard
import com.buzzsense.veritas.app.ui.components.BuzzCheckbox
import com.buzzsense.veritas.app.ui.components.BuzzChecklistItem
import com.buzzsense.veritas.app.ui.components.BuzzChevron
import com.buzzsense.veritas.app.ui.components.BuzzConfidenceBar
import com.buzzsense.veritas.app.ui.components.BuzzDisclaimer
import com.buzzsense.veritas.app.ui.components.BuzzDragHandle
import com.buzzsense.veritas.app.ui.components.BuzzEyebrow
import com.buzzsense.veritas.app.ui.components.BuzzGroupedList
import com.buzzsense.veritas.app.ui.components.BuzzHighlightedText
import com.buzzsense.veritas.app.ui.components.BuzzIconChip
import com.buzzsense.veritas.app.ui.components.BuzzLinkButton
import com.buzzsense.veritas.app.ui.components.BuzzListRow
import com.buzzsense.veritas.app.ui.components.BuzzMessageState
import com.buzzsense.veritas.app.ui.components.BuzzMetricChip
import com.buzzsense.veritas.app.ui.components.BuzzMetricTile
import com.buzzsense.veritas.app.ui.components.BuzzMicButton
import com.buzzsense.veritas.app.ui.components.BuzzModalBottomSheet
import com.buzzsense.veritas.app.ui.components.BuzzPageIndicator
import com.buzzsense.veritas.app.ui.components.BuzzPreviewContainer
import com.buzzsense.veritas.app.ui.components.BuzzProgressIndicator
import com.buzzsense.veritas.app.ui.components.BuzzResultCard
import com.buzzsense.veritas.app.ui.components.BuzzResultHero
import com.buzzsense.veritas.app.ui.components.BuzzScreenHeader
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSegmentedControl
import com.buzzsense.veritas.app.ui.components.BuzzSheetContent
import com.buzzsense.veritas.app.ui.components.BuzzSoundLevelBars
import com.buzzsense.veritas.app.ui.components.BuzzSpectrumCard
import com.buzzsense.veritas.app.ui.components.BuzzStatusTag
import com.buzzsense.veritas.app.ui.components.BuzzSwitch
import com.buzzsense.veritas.app.ui.components.BuzzTextButton
import com.buzzsense.veritas.app.ui.components.BuzzTextField
import com.buzzsense.veritas.app.ui.components.BuzzTile
import com.buzzsense.veritas.app.ui.components.BuzzTimerReadout
import com.buzzsense.veritas.app.ui.components.BuzzTopAppBar
import com.buzzsense.veritas.app.ui.components.BuzzTopBarAction
import com.buzzsense.veritas.app.ui.components.BuzzTwoToneText
import com.buzzsense.veritas.app.ui.components.BuzzValueChip
import com.buzzsense.veritas.app.ui.components.IconChipTone
import com.buzzsense.veritas.app.ui.components.MetricTone
import com.buzzsense.veritas.app.ui.components.MicButtonState
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.components.SampleSpectrum
import com.buzzsense.veritas.app.ui.components.StatusTone
import com.buzzsense.veritas.app.ui.components.rememberBuzzNavItems
import com.buzzsense.veritas.app.ui.screens.SampleChecklist
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Living style guide: every foundation token and component in one
 * scrolling list, grouped like the Stitch style guide.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xl),
    ) {
        item { GallerySection("01 · Foundation", "Colour roles") { ColorSwatches() } }
        item { GallerySection("02 · Foundation", "Type scale") { TypeScale() } }
        item { GallerySection("03 · Actions", "Buttons") { ButtonsDemo() } }
        item { GallerySection("04 · Navigation", "App bars & bottom nav") { NavigationDemo() } }
        item { GallerySection("05 · Microphone", "Hero mic & sound level") { MicDemo() } }
        item { GallerySection("06 · Data", "Readouts & metrics") { ReadoutsDemo() } }
        item { GallerySection("07 · Results", "Result hero & cards") { ResultsDemo() } }
        item { GallerySection("08 · Surfaces", "Cards, tiles & chips") { SurfacesDemo() } }
        item { GallerySection("09 · Lists", "Grouped list & rows") { ListsDemo() } }
        item { GallerySection("10 · Selection", "Checklist & controls") { SelectionDemo() } }
        item { GallerySection("11 · Text", "Headers, tags & highlight") { TextDemo() } }
        item { GallerySection("12 · Input", "Text field") { InputDemo() } }
        item { GallerySection("13 · Overlays", "Sheet & dialog") { OverlaysDemo() } }
        item { GallerySection("14 · Visualizer", "Spectrum") { BuzzSpectrumCard("Spectral analysis", "480 Hz", SampleSpectrum) } }
        item { GallerySection("15 · States", "Messages & progress") { StatesDemo() } }
        item { BuzzDisclaimer() }
    }
}

@Composable
private fun GallerySection(eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzScreenHeader(eyebrow = eyebrow, title = title, titleStyle = BuzzTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun ColorSwatches() {
    val scheme = BuzzTheme.colorScheme
    val buzz = BuzzTheme.colors
    val swatches = listOf(
        "primary" to scheme.primary,
        "secondary" to scheme.secondary,
        "tertiary" to scheme.tertiary,
        "surface" to scheme.surface,
        "containerLow" to scheme.surfaceContainerLow,
        "containerHigh" to scheme.surfaceContainerHigh,
        "outline" to scheme.outline,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "resultSafe" to buzz.resultSafe,
        "resultWatch" to buzz.resultWatch,
        "resultHazard" to buzz.resultHazard,
        "accent" to buzz.accent,
    )
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
        swatches.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
                row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier = Modifier) {
    BuzzCard(modifier = modifier, contentPadding = PaddingValues(BuzzTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(color, BuzzTheme.shapes.small),
        )
        Text(name, style = BuzzTheme.typography.labelMedium, color = BuzzTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TypeScale() {
    val t = BuzzTheme.typography
    val styles: List<Pair<String, TextStyle>> = listOf(
        "displayLarge · 48" to t.displayLarge,
        "displayMedium · 36" to t.displayMedium,
        "headlineLarge · 32" to t.headlineLarge,
        "headlineMedium · 26" to t.headlineMedium,
        "headlineSmall · 22" to t.headlineSmall,
        "titleLarge · 18" to t.titleLarge,
        "bodyLarge · 16" to t.bodyLarge,
        "bodyMedium · 14" to t.bodyMedium,
        "labelMedium · 12" to t.labelMedium,
        "labelSmall · 11" to t.labelSmall,
        "data.tabularLarge · 28" to BuzzTheme.data.tabularLarge,
        "data.tabularSmall · 16" to BuzzTheme.data.tabularSmall,
    )
    BuzzCard(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        styles.forEach { (name, style) ->
            Column {
                BuzzEyebrow(name)
                Text("Wingbeat 620 Hz", style = style, color = BuzzTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ButtonsDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
        BuzzButtonVariant.entries.forEach { variant ->
            BuzzButton(text = variant.name, onClick = {}, variant = variant, modifier = Modifier.fillMaxWidth())
        }
        BuzzButton(text = "Disabled", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
        BuzzButton(text = "With icon", onClick = {}, leadingIcon = BuzzIcons.Share, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuzzTextButton(text = "Record again", onClick = {})
            BuzzLinkButton(text = "Official info", onClick = {})
        }
    }
}

@Composable
private fun NavigationDemo() {
    val items = rememberBuzzNavItems()
    var selected by remember { mutableStateOf(items.first().key) }
    BuzzCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        BuzzTopAppBar(actions = { BuzzTopBarAction(BuzzIcons.History, "History", onClick = {}) })
        BuzzTopAppBar(
            navigation = { BuzzBackButton(onClick = {}) },
            title = { Text("Result details", style = BuzzTheme.typography.titleLarge) },
            actions = { BuzzTextButton(text = "Skip", onClick = {}) },
        )
        BuzzBottomNavBar(items = items, selectedKey = selected, onSelect = { selected = it.key })
    }
}

@Composable
private fun MicDemo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            BuzzMicButton(MicButtonState.Idle, onClick = {}, contentDescription = "Idle", modifier = Modifier.weight(1f))
            BuzzMicButton(MicButtonState.Recording, onClick = {}, contentDescription = "Recording", progress = { 0.6f }, modifier = Modifier.weight(1f))
        }
        BuzzMicButton(MicButtonState.Unavailable, onClick = {}, contentDescription = "Unavailable")
        BuzzSoundLevelBars(modifier = Modifier.padding(top = BuzzTheme.spacing.md))
    }
}

@Composable
private fun ReadoutsDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzTimerReadout(elapsedSeconds = 3, totalSeconds = 5)
        BuzzTwoToneText(value = "620", unit = " Hz")
        BuzzConfidenceBar(0.82f, BuzzTheme.colors.resultHazard)
        BuzzConfidenceBar(0.46f, BuzzTheme.colors.resultWatch)
        Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzMetricTile("Duration", "4.2", unit = " s", modifier = Modifier.weight(1f))
            BuzzMetricTile("Peak", "480", unit = " Hz", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzMetricChip("Peak", "480 Hz")
            BuzzMetricChip("Windows", "5/5", tone = MetricTone.Safe)
            BuzzMetricChip("Level", "48 dB", tone = MetricTone.Neutral)
        }
    }
}

@Composable
private fun ResultsDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzResultHero(kind = ResultKind.LikelyAedes, confidence = 0.82f)
        BuzzResultCard(ResultKind.LikelyAedes, 0.88f, "Wingbeat near 580 Hz matches the Aedes profile.")
        BuzzResultCard(ResultKind.NotDengue, 0.92f, "Sounds like Culex, the common house mosquito.")
        BuzzResultCard(ResultKind.NotSure, 0.46f, "Signal faint or noisy. Record again, closer.")
        BuzzResultCard(ResultKind.NoMosquito, 0.90f, "No wingbeat in this clip.")
    }
}

@Composable
private fun SurfacesDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzCard {
            BuzzEyebrow("Card")
            Text("Flat mist plane, no border, no shadow.", style = BuzzTheme.typography.bodyMedium)
        }
        BuzzActionCard(title = "Tips for a clear recording", icon = BuzzIcons.Lightbulb, onClick = {})
        Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzTile("How it works", BuzzIcons.GraphicEq, subtitle = "On-device model", iconTone = IconChipTone.Brand, modifier = Modifier.weight(1f))
            BuzzTile("Meet Aedes", BuzzIcons.MenuBook, subtitle = "Know the vector", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            IconChipTone.entries.forEach { BuzzIconChip(BuzzIcons.Shield, tone = it) }
        }
    }
}

@Composable
private fun ListsDemo() {
    var filter by remember { mutableStateOf(true) }
    BuzzGroupedList {
        BuzzListRow("Sample duration", supporting = "Standard acoustic window", trailing = { BuzzValueChip("5 sec") })
        BuzzListRow(
            "Filter ambient noise",
            supporting = "Fans and road traffic",
            trailing = { BuzzSwitch(checked = filter, onCheckedChange = { filter = it }) },
        )
        BuzzListRow(
            "Today, 19:42",
            supporting = "Likely Aedes aegypti · 82%",
            selected = true,
            onClick = {},
            leading = { BuzzIconChip(BuzzIcons.Warning, tone = IconChipTone.Hazard) },
            trailing = { BuzzChevron() },
        )
    }
}

@Composable
private fun SelectionDemo() {
    var checked by remember { mutableStateOf(setOf("drain")) }
    var segment by remember { mutableIntStateOf(0) }
    var box by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzCard(contentPadding = PaddingValues(BuzzTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SampleChecklist.forEach { entry ->
                BuzzChecklistItem(
                    text = entry.text,
                    icon = entry.icon,
                    supporting = entry.source,
                    checked = entry.id in checked,
                    onCheckedChange = { on -> checked = if (on) checked + entry.id else checked - entry.id },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuzzCheckbox(checked = box, onCheckedChange = { box = it })
            BuzzCheckbox(checked = true, onCheckedChange = null, enabled = false)
            BuzzSwitch(checked = box, onCheckedChange = { box = it })
        }
        BuzzSegmentedControl(listOf("Acoustic 5s", "Spectrum", "Harmonics"), segment, { segment = it })
        BuzzPageIndicator(pageCount = 4, currentPage = segment.coerceAtMost(3))
    }
}

@Composable
private fun TextDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzScreenHeader(eyebrow = "Detect", title = "Is it a dengue mosquito?", subtitle = "Centered screen header.", centered = true)
        BuzzHighlightedText("Targeted monitoring for dengue transmission zones.", highlight = "dengue")
        Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzStatusTag("Safe", tone = StatusTone.Safe)
            BuzzStatusTag("Retest", tone = StatusTone.Watch)
            BuzzStatusTag("Aedes", tone = StatusTone.Hazard)
            BuzzStatusTag("Draft")
        }
        BuzzDisclaimer()
    }
}

@Composable
private fun InputDemo() {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzTextField(value = text, onValueChange = { text = it }, label = "House number", placeholder = "e.g. RT 04 / 12")
        BuzzTextField(value = "abc", onValueChange = {}, label = "Containers checked", supportingText = "Enter a number", isError = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet's SheetState
@Composable
private fun OverlaysDemo() {
    var showSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
        BuzzCard(containerColor = BuzzTheme.colorScheme.surfaceContainerLowest, contentPadding = PaddingValues(0.dp)) {
            BuzzDragHandle()
            BuzzSheetContent(title = "Inline sheet content", subtitle = "Same layout the modal sheet uses.") {
                Text("Drag handle, title, subtitle, content, actions.", style = BuzzTheme.typography.bodyMedium)
            }
        }
        BuzzButton(text = "Open bottom sheet", onClick = { showSheet = true }, variant = BuzzButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        BuzzButton(text = "Open delete dialog", onClick = { showDialog = true }, variant = BuzzButtonVariant.Destructive, modifier = Modifier.fillMaxWidth())
    }
    if (showSheet) {
        BuzzModalBottomSheet(onDismissRequest = { showSheet = false }) {
            BuzzSheetContent(
                title = "Recording tips",
                subtitle = "Hold the phone within 30 cm of the mosquito.",
                actions = {
                    BuzzButton(text = "Got it", onClick = { showSheet = false }, variant = BuzzButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
                },
            ) {
                BuzzHighlightedText("Turn off fans: they mask the buzz.", highlight = "fans")
            }
        }
    }
    if (showDialog) {
        BuzzAlertDialog(
            title = "Delete recording?",
            text = "This removes the result from History. It cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { showDialog = false },
            dismissLabel = "Cancel",
            onDismiss = { showDialog = false },
            destructive = true,
        )
    }
}

@Composable
private fun StatesDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        BuzzMessageState(
            icon = BuzzIcons.MicOff,
            title = "Microphone is off",
            body = "BuzzSense needs the microphone to hear the buzz.",
            actions = { BuzzButton(text = "Allow microphone", onClick = {}, modifier = Modifier.fillMaxWidth()) },
        )
        BuzzProgressIndicator()
    }
}

@BuzzScreenPreviews
@Composable
private fun ComponentGalleryPreview() {
    BuzzPreviewContainer { ComponentGallery() }
}
