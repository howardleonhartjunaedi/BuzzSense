package com.buzzsense.veritas.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

/** Light + dark preview pair used by every component in this package. */
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 380)
@Preview(
    name = "Dark",
    showBackground = true,
    backgroundColor = 0xFF0A0A0B,
    widthDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class BuzzPreviews

/** Full-screen preview pair (phone size). */
@Preview(name = "Phone Light", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(
    name = "Phone Dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class BuzzScreenPreviews

/** Theme + canvas + padding wrapper for component previews. */
@Composable
internal fun BuzzPreviewContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BuzzsenseTheme {
        Surface(modifier = modifier, color = BuzzTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}
