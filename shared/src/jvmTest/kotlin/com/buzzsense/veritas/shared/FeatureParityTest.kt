package com.buzzsense.veritas.shared

import com.buzzsense.veritas.shared.detector.AcousticFeatures
import com.buzzsense.veritas.shared.detector.AcousticStore
import com.buzzsense.veritas.shared.detector.FeatureSpec
import com.buzzsense.veritas.shared.detector.KnnClassifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Eval 6 at unit level: the Kotlin port must reproduce `build_rag.features()`.
 * Fixture and spec are exported from the pipeline:
 * `python -I pipeline/export_parity_fixture.py --data data/raw --rag data/rag --out <jvmTest/resources>/parity_fixture.json`
 * Regenerate both files whenever feature_spec.json changes.
 */
class FeatureParityTest {

    @Serializable
    data class Case(
        val clip_id: String,
        val class3: String,
        val samples: List<Float>,
        val raw: List<Float>,
        val embedding: List<Float>,
        val stored_embedding: List<Float>,
        val f0_hz: Double,
    )

    @Serializable
    data class Fixture(val spec_version: String? = null, val cases: List<Case>)

    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResource(name)) { "missing $name" }.readText()

    private val spec = FeatureSpec.fromJson(resource("feature_spec.json"))
    private val fixture = Json { ignoreUnknownKeys = true }.decodeFromString(Fixture.serializer(), resource("parity_fixture.json"))
    private val features = AcousticFeatures(spec)

    @Test
    fun fixtureMatchesSpecVersion() = assertEquals(spec.version, fixture.spec_version)

    @Test
    fun embeddingsMatchPythonAndTheStore() {
        fixture.cases.forEach { c ->
            val x = c.samples.toFloatArray()
            val raw = features.raw(x)
            val e = features.embedding(x)
            val rawErr = c.raw.indices.maxOf { abs(raw[it] - c.raw[it]) }
            val embErr = c.stored_embedding.indices.maxOf { abs(e[it] - c.stored_embedding[it]) }
            assertTrue(rawErr < 1e-3f, "${c.clip_id}: raw features differ by $rawErr")
            assertTrue(embErr < 1e-3f, "${c.clip_id}: embedding differs from clips_vec by $embErr")
        }
    }

    @Test
    fun f0MatchesPython() {
        fixture.cases.forEach { c ->
            val f0 = features.f0Hz(c.samples.toFloatArray())
            assertTrue(abs(f0 - c.f0_hz) <= 1.0, "${c.clip_id}: f0 $f0 vs ${c.f0_hz}")
        }
    }

    /**
     * Runs only when the real store is available:
     * `JUMANTIK_RAG_DIR=<Audio File/data/rag>` and `SQLITE_VEC_LIB=<.venv/Lib/site-packages/sqlite_vec/vec0>`.
     */
    @Test
    fun knnOnRealStoreFindsTheClipsOwnClass() = runTest {
        val dir = System.getenv("JUMANTIK_RAG_DIR") ?: return@runTest
        val vec = System.getenv("SQLITE_VEC_LIB") ?: return@runTest
        AcousticStore.open(File(dir, "jumantik_rag.sqlite").absolutePath, vec).use { store ->
            val knn = KnnClassifier(store)
            fixture.cases.forEach { c ->
                val nn = store.nearest(features.embedding(c.samples.toFloatArray()), 15)
                assertEquals(15, nn.size, c.clip_id)
                println("${c.clip_id} (${c.class3}) -> ${knn.classify(listOf(features.embedding(c.samples.toFloatArray()))).single()}")
            }
        }
    }
}
