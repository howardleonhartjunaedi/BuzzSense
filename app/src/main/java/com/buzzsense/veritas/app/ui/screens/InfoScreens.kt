package com.buzzsense.veritas.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.components.BuzzArticleCard
import com.buzzsense.veritas.app.ui.components.BuzzChevron
import com.buzzsense.veritas.app.ui.components.BuzzDisclaimer
import com.buzzsense.veritas.app.ui.components.BuzzEyebrow
import com.buzzsense.veritas.app.ui.components.BuzzFilterChips
import com.buzzsense.veritas.app.ui.components.BuzzInfoCard
import com.buzzsense.veritas.app.ui.components.BuzzMetricChip
import com.buzzsense.veritas.app.ui.components.BuzzPreviewContainer
import com.buzzsense.veritas.app.ui.components.BuzzScreenColumn
import com.buzzsense.veritas.app.ui.components.BuzzScreenHeader
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzStatusDot
import com.buzzsense.veritas.app.ui.components.BuzzTextChip
import com.buzzsense.veritas.app.ui.components.MetricTone
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** Learn topics, matching the filter chips (index 0 is "All"). */
enum class LearnTopic { Dengue, Malaria, Acoustic, Prevention }

/** One public article on the Learn tab. Links open in the browser; nothing is fetched in-app. */
@Immutable
data class LearnArticle(
    val topic: LearnTopic,
    @StringRes val tag: Int,
    @StringRes val meta: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val link: Int,
    val url: String,
    val icon: ImageVector,
    /** Stitch illustration for the card header. */
    @DrawableRes val image: Int,
)

/** The four Stitch "07 Learn Hub" articles; public WHO, HumBug and Kemenkes pages. */
val LearnArticles = listOf(
    LearnArticle(
        LearnTopic.Dengue, R.string.learn_a1_tag, R.string.learn_a1_meta, R.string.learn_a1_title, R.string.learn_a1_body,
        R.string.learn_a1_link, "https://www.who.int/news-room/fact-sheets/detail/dengue-and-severe-dengue", BuzzIcons.Warning,
        R.drawable.learn_aedes,
    ),
    LearnArticle(
        LearnTopic.Malaria, R.string.learn_a2_tag, R.string.learn_a2_meta, R.string.learn_a2_title, R.string.learn_a2_body,
        R.string.learn_a2_link, "https://www.who.int/news-room/fact-sheets/detail/malaria", BuzzIcons.Shield,
        R.drawable.learn_malaria,
    ),
    LearnArticle(
        LearnTopic.Acoustic, R.string.learn_a3_tag, R.string.learn_a3_meta, R.string.learn_a3_title, R.string.learn_a3_body,
        R.string.learn_a3_link, "https://humbug.ox.ac.uk/", BuzzIcons.GraphicEq,
        R.drawable.learn_acoustic,
    ),
    LearnArticle(
        LearnTopic.Prevention, R.string.learn_a4_tag, R.string.learn_a4_meta, R.string.learn_a4_title, R.string.learn_a4_body,
        R.string.learn_a4_link, "https://ayosehat.kemkes.go.id/", BuzzIcons.WaterDrop,
        R.drawable.learn_field,
    ),
)

/** Stitch "07 Learn Hub": topic filter and article cards. */
@Composable
fun LearnScreen(onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = listOf(
        stringResource(R.string.learn_filter_all),
        stringResource(R.string.learn_filter_dengue),
        stringResource(R.string.learn_filter_malaria),
        stringResource(R.string.learn_filter_acoustic),
    )
    val shown = when (filter) {
        1 -> LearnArticles.filter { it.topic == LearnTopic.Dengue || it.topic == LearnTopic.Prevention }
        2 -> LearnArticles.filter { it.topic == LearnTopic.Malaria }
        3 -> LearnArticles.filter { it.topic == LearnTopic.Acoustic }
        else -> LearnArticles
    }
    BuzzScreenColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        BuzzScreenHeader(
            title = stringResource(R.string.learn_title),
            subtitle = stringResource(R.string.learn_subtitle),
        )
        BuzzFilterChips(filters, filter, { filter = it }, modifier = Modifier.padding(vertical = BuzzTheme.spacing.xs))
        shown.forEach { article ->
            BuzzArticleCard(
                tag = stringResource(article.tag),
                meta = stringResource(article.meta),
                title = stringResource(article.title),
                summary = stringResource(article.body),
                linkLabel = stringResource(article.link),
                icon = article.icon,
                onClick = { onOpenUrl(article.url) },
                image = painterResource(article.image),
            )
        }
        BuzzDisclaimer(modifier = Modifier.padding(top = BuzzTheme.spacing.md))
    }
}

/**
 * Stitch "51 Credits": data, guidance, tools and team, plus two cards the design does not
 * show: Settings (RAG switch and the Gemini key, which must stay reachable) and the Design
 * system gallery of every token and Buzz* component.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CreditsScreen(
    versionName: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDesignSystem: () -> Unit = {},
) {
    BuzzScreenColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            BuzzScreenHeader(
                title = stringResource(R.string.credits_title),
                subtitle = stringResource(R.string.credits_subtitle),
                modifier = Modifier.weight(1f),
            )
            BuzzStatusDot(stringResource(R.string.credits_version, versionName), modifier = Modifier.padding(start = BuzzTheme.spacing.sm, top = BuzzTheme.spacing.sm))
        }
        BuzzInfoCard(
            title = stringResource(R.string.credits_data_title),
            icon = BuzzIcons.GraphicEq,
            body = stringResource(R.string.credits_data_body),
        ) {
            BuzzMetricChip(stringResource(R.string.credits_data_chip_label), stringResource(R.string.credits_data_chip_value), tone = MetricTone.Safe)
        }
        BuzzInfoCard(
            title = stringResource(R.string.credits_guidance_title),
            icon = BuzzIcons.MenuBook,
            body = stringResource(R.string.credits_guidance_body),
        )
        BuzzInfoCard(
            title = stringResource(R.string.credits_built_title),
            icon = BuzzIcons.Code,
            body = stringResource(R.string.credits_built_body),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs)) {
                BuzzTextChip(stringResource(R.string.credits_built_chip1))
                BuzzTextChip(stringResource(R.string.credits_built_chip2))
                BuzzTextChip(stringResource(R.string.credits_built_chip3))
            }
        }
        BuzzInfoCard(
            title = stringResource(R.string.credits_ai_title),
            icon = BuzzIcons.AutoAwesome,
            body = stringResource(R.string.credits_ai_body),
        ) {
            BuzzStatusDot(stringResource(R.string.credits_ai_note))
        }
        BuzzInfoCard(
            title = stringResource(R.string.credits_fonts_title),
            icon = BuzzIcons.Info,
            body = stringResource(R.string.credits_fonts_body),
        )
        BuzzInfoCard(
            title = stringResource(R.string.credits_team_title),
            icon = BuzzIcons.Group,
            body = stringResource(R.string.credits_team_body),
        )
        BuzzInfoCard(
            title = stringResource(R.string.credits_settings_title),
            icon = BuzzIcons.Settings,
            body = stringResource(R.string.credits_settings_body),
            onClick = onOpenSettings,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { BuzzChevron() }
        }
        BuzzInfoCard(
            title = stringResource(R.string.credits_design_title),
            icon = BuzzIcons.AutoAwesome,
            body = stringResource(R.string.credits_design_body),
            onClick = onOpenDesignSystem,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { BuzzChevron() }
        }
        Column(Modifier.fillMaxWidth().padding(top = BuzzTheme.spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            BuzzEyebrow(stringResource(R.string.credits_footer))
            Text(
                stringResource(R.string.disclaimer),
                style = BuzzTheme.typography.labelMedium,
                color = BuzzTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = BuzzTheme.spacing.sm),
            )
        }
    }
}

@BuzzScreenPreviews
@Composable
private fun LearnScreenPreview() {
    BuzzPreviewContainer { LearnScreen(onOpenUrl = {}) }
}

@BuzzScreenPreviews
@Composable
private fun CreditsScreenPreview() {
    BuzzPreviewContainer { CreditsScreen(versionName = "1.0", onOpenSettings = {}) }
}
