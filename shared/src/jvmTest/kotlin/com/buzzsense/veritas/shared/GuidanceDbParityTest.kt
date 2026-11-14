package com.buzzsense.veritas.shared

import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.RiskLevel
import com.buzzsense.veritas.shared.model.Verdict
import com.buzzsense.veritas.shared.model.Weather
import com.buzzsense.veritas.shared.rag.GuidanceDb
import com.buzzsense.veritas.shared.rag.GuidanceQueryBuilder
import com.buzzsense.veritas.shared.rag.HybridRetriever
import com.buzzsense.veritas.shared.rag.PrecomputedQueryEmbedder
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the guidance.db built by tools/guidance/build_guidance_db.py against the app's
 * own GuidanceStore and GuidanceQueryBuilder. Skipped until the DB exists in the app's assets.
 */
class GuidanceDbParityTest {

    private val dbFile = File("../app/src/main/assets/guidance.db")

    /** Every query the agent can send: Aedes yes/no × rain × heat × high risk × language. */
    private fun allQueries(): Set<String> {
        val out = mutableSetOf<String>()
        for (aedes in listOf(Verdict.YES, Verdict.NO)) for (rain in listOf(0.0, 2.0)) for (temp in listOf(25.0, 31.0))
            for (risk in listOf(null, RiskLevel.HIGH)) for (lang in Language.entries) {
                val detection = DetectionResult(
                    mosquito = Verdict.YES, aedesAegypti = aedes, mosquitoScore = 0.9f, aedesScore = 0.8f,
                    dominantFrequencyHz = 600f, baselineAedes = Verdict.NO, windowCount = 4, mosquitoWindowCount = 4,
                )
                out += GuidanceQueryBuilder.build(detection, Weather("San Juan, PR", temp, 80.0, rain, "t"), risk, lang)
            }
        return out
    }

    @Test
    fun everyAgentQueryHasAPrecomputedEmbedding() = runTest {
        if (!dbFile.exists()) return@runTest
        GuidanceDb.open(dbFile.absolutePath, System.getenv("SQLITE_VEC_LIB")).use { store ->
            val queries = allQueries()
            assertEquals(32, queries.size)
            val missing = queries.filter { store.precomputedQueryEmbedding(it) == null }
            assertTrue(missing.isEmpty(), "build_guidance_db.py and GuidanceQueryBuilder drifted; missing: $missing")
            assertEquals(384, assertNotNull(store.precomputedQueryEmbedding(queries.first())).size)
        }
    }

    @Test
    fun hybridRetrievalReturnsFiveCitableChunks() = runTest {
        if (!dbFile.exists()) return@runTest
        GuidanceDb.open(dbFile.absolutePath, System.getenv("SQLITE_VEC_LIB")).use { store ->
            val retriever = HybridRetriever(store, PrecomputedQueryEmbedder(store))
            for (query in allQueries()) {
                val hits = retriever.retrieve(query)
                assertEquals(5, hits.size, "query: $query")
                assertTrue(hits.all { it.url.startsWith("http") && it.text.isNotBlank() && it.source.isNotBlank() })
            }
            println("guidance.db: ${allQueries().size} queries retrieved 5 chunks each (sqlite-vec loaded: ${store.hasSqliteVec})")
        }
    }
}
