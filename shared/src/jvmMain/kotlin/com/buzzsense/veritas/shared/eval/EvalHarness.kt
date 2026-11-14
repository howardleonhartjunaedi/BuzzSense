package com.buzzsense.veritas.shared.eval

import com.buzzsense.veritas.shared.agent.BuzzAlertEngine
import com.buzzsense.veritas.shared.context.OpenMeteoWeather
import com.buzzsense.veritas.shared.detector.DominantFrequencyBaseline
import com.buzzsense.veritas.shared.detector.WingbeatDetector
import com.buzzsense.veritas.shared.llm.NetworkStatus
import com.buzzsense.veritas.shared.llm.ProviderKeys
import com.buzzsense.veritas.shared.llm.buildProviderChain
import com.buzzsense.veritas.shared.onnx.OnnxWingbeatModel
import com.buzzsense.veritas.shared.rag.GuidanceDb
import com.buzzsense.veritas.shared.rag.GuidanceStore
import com.buzzsense.veritas.shared.rag.HybridRetriever
import com.buzzsense.veritas.shared.rag.PrecomputedQueryEmbedder
import com.buzzsense.veritas.shared.rag.RetrievalMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JVM side of the eval harness: the same shared code as the phone, run on a
 * laptop. Every command appends rows to one results table (`results.jsonl`) so a
 * single run regenerates every number in the write-up.
 *
 * Keys come from environment variables here (GEMINI_API_KEY, ANTHROPIC_API_KEY,
 * OPENAI_API_KEY), never from files in the repo.
 */
object JvmBuzzAlert {
    fun create(artifacts: File, baselineBandHz: ClosedFloatingPointRange<Float>): Pair<BuzzAlertEngine, GuidanceStore> {
        val store = GuidanceDb.open(File(artifacts, "guidance.db").absolutePath, System.getenv("SQLITE_VEC_LIB"))
        val http = HttpClient(OkHttp) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val providers = buildProviderChain(
            keys = ProviderKeys(
                gemini = System.getenv("GEMINI_API_KEY"),
                anthropic = System.getenv("ANTHROPIC_API_KEY"),
                openAI = System.getenv("OPENAI_API_KEY"),
            ),
            onDevice = null, // LiteRT is Android-only; eval 9 measures that tier on the phone
            network = NetworkStatus { true },
        )
        val engine = BuzzAlertEngine(
            detector = WingbeatDetector(
                OnnxWingbeatModel(File(artifacts, "wingbeat_stage1.onnx").readBytes(), File(artifacts, "wingbeat_stage2.onnx").readBytes()),
                DominantFrequencyBaseline(baselineBandHz),
            ),
            weather = OpenMeteoWeather(http),
            retriever = HybridRetriever(store, PrecomputedQueryEmbedder(store)),
            chunkLookup = store::chunks,
            providers = providers,
        )
        return engine to store
    }
}

/** Eval 7 input: one query with hand-labelled relevant chunk ids. */
@Serializable
data class LabeledQuery(val query: String, val relevant: List<String>)

@Serializable
data class RetrievalRow(val eval: String = "retrieval", val mode: String, val recallAt5: Double, val mrr: Double, val n: Int)

object RetrievalMetrics {
    fun recallAtK(retrieved: List<String>, relevant: Set<String>, k: Int = 5): Double =
        if (relevant.isEmpty()) 0.0 else retrieved.take(k).count { it in relevant }.toDouble() / relevant.size

    fun reciprocalRank(retrieved: List<String>, relevant: Set<String>): Double {
        val idx = retrieved.indexOfFirst { it in relevant }
        return if (idx < 0) 0.0 else 1.0 / (idx + 1)
    }
}

/**
 * Eval 7: hybrid vs FTS5-only vs vector-only.
 * The corpus build script must also embed the labelled eval queries into
 * `query_embeddings` (or pass an `OnnxE5Embedder` as fallback).
 * Usage: `EvalHarnessKt <artifactsDir> <labeled_queries.json> <results.jsonl>`
 */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 3) { "usage: <artifactsDir> <labeled_queries.json> <results.jsonl>" }
    val artifacts = File(args[0])
    val json = Json { ignoreUnknownKeys = true }
    val queries = json.decodeFromString<List<LabeledQuery>>(File(args[1]).readText())
    val store = GuidanceDb.open(File(artifacts, "guidance.db").absolutePath, System.getenv("SQLITE_VEC_LIB"))
    val retriever = HybridRetriever(store, PrecomputedQueryEmbedder(store))
    val out = File(args[2])

    store.use {
        RetrievalMode.entries.forEach { mode ->
            var recall = 0.0
            var mrr = 0.0
            queries.forEach { q ->
                val ids = retriever.retrieve(q.query, mode).map { it.id }
                recall += RetrievalMetrics.recallAtK(ids, q.relevant.toSet())
                mrr += RetrievalMetrics.reciprocalRank(ids, q.relevant.toSet())
            }
            val row = RetrievalRow(mode = mode.name, recallAt5 = recall / queries.size, mrr = mrr / queries.size, n = queries.size)
            out.appendText(json.encodeToString(RetrievalRow.serializer(), row) + "\n")
            println(row)
        }
    }
}
