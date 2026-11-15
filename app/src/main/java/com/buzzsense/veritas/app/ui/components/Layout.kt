package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Standard screen body: phone margins, fills the viewport so `Modifier.weight` spacers can
 * push actions to the bottom, and scrolls instead of clipping on small screens or large fonts.
 */
@Composable
fun BuzzScreenColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.md),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@BuzzScreenPreviews
@Composable
private fun BuzzScreenColumnPreview() {
    BuzzPreviewContainer {
        BuzzScreenColumn {
            BuzzScreenHeader(eyebrow = "Detect", title = "Screen body")
            Spacer(Modifier.weight(1f))
            BuzzButton(text = "Pinned to the bottom", onClick = {}, modifier = Modifier.fillMaxWidth())
            Text("Scrolls when the content is taller than the screen.", style = BuzzTheme.typography.bodySmall)
        }
    }
}
