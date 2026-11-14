package com.buzzsense.veritas.shared

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.buzzsense.veritas.shared.context.WeatherSource
import com.buzzsense.veritas.shared.detector.LogMelWindow
import com.buzzsense.veritas.shared.detector.WingbeatModel
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.Weather
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Executor that answers with scripted text, or throws. Records the prompts it saw. */
class ScriptedExecutor(private val answers: ArrayDeque<() -> String>) : PromptExecutor() {
    val prompts = mutableListOf<Prompt>()

    constructor(vararg answers: () -> String) : this(ArrayDeque(answers.toList()))

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        prompts += prompt
        val next = answers.removeFirstOrNull() ?: error("Status code: 500 no more scripted answers")
        return Message.Assistant(next(), ResponseMetaInfo.create(KoogClock.System))
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = emptyFlow()
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("not used")
    override fun close() = Unit
}

/** Constant scores per stage, ignoring the features. */
class FixedWingbeatModel(private val mosquito: Float, private val aedes: Float) : WingbeatModel {
    override suspend fun mosquitoScores(windows: List<LogMelWindow>) = FloatArray(windows.size) { mosquito }
    override suspend fun aedesScores(windows: List<LogMelWindow>) = FloatArray(windows.size) { aedes }
}

object FixedWeather : WeatherSource {
    override suspend fun today() = Weather("San Juan, PR", 31.0, 80.0, 2.0, "2026-11-14T10:00")
}

val CHUNKS = listOf(
    GuidanceChunk("cdc-en-1", "CDC", "https://www.cdc.gov/mosquitoes/", "Stop mosquitoes", Language.EN, "Empty and scrub containers that hold water once a week."),
    GuidanceChunk("cdc-en-2", "CDC", "https://www.cdc.gov/mosquitoes/", "Repellent", Language.EN, "Use EPA-registered insect repellent."),
    GuidanceChunk("who-en-1", "WHO", "https://www.who.int/", "Dengue", Language.EN, "Use window screens and mosquito nets."),
)

fun goodDraftJson(ids: List<String> = CHUNKS.map { it.id }) = """
    {"summary":"A mosquito that may be Aedes aegypti was heard indoors; act today.",
     "actions":[
       {"text":"Empty and scrub containers that hold water.","chunk_id":"${ids[0]}"},
       {"text":"Use an EPA-registered repellent.","chunk_id":"${ids[1]}"},
       {"text":"Keep window screens closed.","chunk_id":"${ids[2]}"}
     ],
     "urgency":"HIGH"}
""".trimIndent()

/** 3 s of a 600 Hz tone at 8 kHz: enough windows for the detector. */
fun toneSamples(seconds: Int = 3, rate: Int = 8_000, hz: Double = 600.0) =
    FloatArray(seconds * rate) { i -> (0.5 * kotlin.math.sin(2 * kotlin.math.PI * hz * i / rate)).toFloat() }
