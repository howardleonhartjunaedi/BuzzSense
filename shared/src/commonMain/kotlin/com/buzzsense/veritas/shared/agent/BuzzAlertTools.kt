package com.buzzsense.veritas.shared.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.typeToken
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.context.ForecastRiskSource
import com.buzzsense.veritas.shared.context.WeatherSource
import com.buzzsense.veritas.shared.detector.WingbeatDetector
import com.buzzsense.veritas.shared.guardrails.ToolPermissions
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.RiskLevel
import com.buzzsense.veritas.shared.model.TraceEvent
import com.buzzsense.veritas.shared.model.Weather
import com.buzzsense.veritas.shared.rag.GuidanceRetriever
import kotlinx.serialization.Serializable

@Serializable
data object NoArgs

@Serializable
data class GuidanceQuery(val query: String)

@Serializable
data class GuidanceResults(val chunks: List<GuidanceChunk>)

@Serializable
data class RiskResult(val level: RiskLevel?)

/** Holds the in-memory clip for the current run only; the detector tool reads it once. */
class ClipHolder(var clip: AudioClip? = null)

/**
 * The agent's tools. All are read-only Koog [Tool]s; there is deliberately no
 * share or message tool. They are invoked by deterministic graph nodes through
 * [ToolGate], which enforces the per-node allowlist.
 */
class BuzzAlertTools(
    detector: WingbeatDetector,
    weather: WeatherSource,
    risk: ForecastRiskSource,
    retriever: GuidanceRetriever,
) {
    val clipHolder = ClipHolder()

    val detect = object : Tool<NoArgs, DetectionResult>(
        argsType = typeToken<NoArgs>(),
        resultType = typeToken<DetectionResult>(),
        name = ToolPermissions.TOOL_DETECTOR,
        description = "Runs the on-device two-stage wingbeat CNN on the current clip and returns labels and scores.",
    ) {
        override suspend fun execute(args: NoArgs): DetectionResult {
            val clip = checkNotNull(clipHolder.clip) { "No clip for this run" }
            return detector.detect(clip)
        }
    }

    val weatherToday = object : Tool<NoArgs, Weather>(
        argsType = typeToken<NoArgs>(),
        resultType = typeToken<Weather>(),
        name = ToolPermissions.TOOL_WEATHER,
        description = "Today's weather for San Juan, Puerto Rico (Open-Meteo).",
    ) {
        override suspend fun execute(args: NoArgs): Weather = weather.today()
    }

    val riskLevel = object : Tool<NoArgs, RiskResult>(
        argsType = typeToken<NoArgs>(),
        resultType = typeToken<RiskResult>(),
        name = ToolPermissions.TOOL_RISK,
        description = "This week's dengue outbreak risk level for San Juan (Low/Medium/High), if the forecast is built.",
    ) {
        override suspend fun execute(args: NoArgs): RiskResult = RiskResult(risk.currentLevel())
    }

    val searchGuidance = object : Tool<GuidanceQuery, GuidanceResults>(
        argsType = typeToken<GuidanceQuery>(),
        resultType = typeToken<GuidanceResults>(),
        name = ToolPermissions.TOOL_GUIDANCE,
        description = "Hybrid FTS5 + vector search over WHO/CDC/PAHO/PR Health guidance; returns the top 5 chunks.",
    ) {
        override suspend fun execute(args: GuidanceQuery): GuidanceResults = GuidanceResults(retriever.retrieve(args.query))
    }

    /** Registry for Koog tracing/inspection. The compose node clears tools, so the LLM never sees them. */
    val registry: ToolRegistry = ToolRegistry.builder()
        .tool(detect).tool(weatherToday).tool(riskLevel).tool(searchGuidance)
        .build()
}

/** Enforces [ToolPermissions] and logs every call for the "How it decided" panel. */
class ToolGate(private val permissions: ToolPermissions, private val events: MutableList<TraceEvent>) {
    suspend fun <A, R> call(node: String, tool: Tool<A, R>, args: A): R {
        try {
            permissions.check(node, tool.name)
        } catch (e: IllegalStateException) {
            events += TraceEvent("tool-permissions", "refused: ${e.message}")
            throw e
        }
        events += TraceEvent("tool", "$node -> ${tool.name}")
        return tool.execute(args)
    }
}
