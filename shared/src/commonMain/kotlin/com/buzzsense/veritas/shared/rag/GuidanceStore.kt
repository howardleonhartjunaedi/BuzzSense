package com.buzzsense.veritas.shared.rag

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** multilingual-e5-small (ONNX) for corpus and query, so English and Spanish pages match. */
interface Embedder {
    val dimensions: Int

    /** e5 expects "query: " for queries and "passage: " for documents; implementations add the prefix. */
    suspend fun embed(text: String, isQuery: Boolean): FloatArray
}

/** A ranked hit from one retriever, best first. */
data class RankedHit(val chunkId: String, val rank: Int)

/**
 * Read-only guidance database shipped in the app's assets, built on a laptop by
 * the JVM corpus script. Schema:
 *
 * ```sql
 * CREATE TABLE chunks(rowid INTEGER PRIMARY KEY, id TEXT UNIQUE, source TEXT, url TEXT,
 *                     title TEXT, lang TEXT, text TEXT);
 * CREATE VIRTUAL TABLE chunks_fts USING fts5(title, text, content='chunks', content_rowid='rowid');
 * -- with the sqlite-vec extension:
 * CREATE VIRTUAL TABLE chunks_vec USING vec0(embedding float[384]);   -- rowid = chunks.rowid
 * -- always present, used when the extension cannot be loaded:
 * CREATE TABLE chunk_embeddings(rowid INTEGER PRIMARY KEY, embedding BLOB);  -- float32 LE
 * -- every templated query from GuidanceQueryBuilder, embedded with e5 "query: " prefix:
 * CREATE TABLE query_embeddings(query TEXT PRIMARY KEY, embedding BLOB);
 * ```
 */
class GuidanceStore(
    private val connection: SQLiteConnection,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {

    private val lock = Mutex()
    private var cachedEmbeddings: List<Pair<Long, FloatArray>>? = null

    /** True when sqlite-vec's `vec0` loaded; otherwise vector search is a brute-force cosine scan. */
    val hasSqliteVec: Boolean by lazy {
        runCatching { connection.prepare("SELECT vec_version()").use { it.step() } }.getOrDefault(false)
    }

    suspend fun keywordSearch(query: String, limit: Int): List<RankedHit> = io {
        val match = toFtsQuery(query) ?: return@io emptyList()
        connection.prepare(
            """
            SELECT c.id FROM chunks_fts
            JOIN chunks c ON c.rowid = chunks_fts.rowid
            WHERE chunks_fts MATCH ?
            ORDER BY bm25(chunks_fts) LIMIT ?
            """.trimIndent()
        ).use { st ->
            st.bindText(1, match)
            st.bindLong(2, limit.toLong())
            st.collect { RankedHit(it.getText(0), 0) }
        }.mapIndexed { i, h -> h.copy(rank = i + 1) }
    }

    suspend fun vectorSearch(queryVector: FloatArray, limit: Int): List<RankedHit> = io {
        val rowIds: List<Long> = if (hasSqliteVec) {
            connection.prepare(
                "SELECT rowid FROM chunks_vec WHERE embedding MATCH ? AND k = ? ORDER BY distance"
            ).use { st ->
                st.bindBlob(1, queryVector.toLittleEndianBytes())
                st.bindLong(2, limit.toLong())
                st.collect { it.getLong(0) }
            }
        } else {
            val all = cachedEmbeddings ?: loadEmbeddings().also { cachedEmbeddings = it }
            all.asSequence()
                .map { (rowId, v) -> rowId to cosine(queryVector, v) }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
                .toList()
        }
        rowIds.mapIndexedNotNull { i, rowId -> idForRow(rowId)?.let { RankedHit(it, i + 1) } }
    }

    suspend fun chunks(ids: List<String>): List<GuidanceChunk> = io {
        ids.mapNotNull { id ->
            connection.prepare("SELECT id, source, url, title, lang, text FROM chunks WHERE id = ?").use { st ->
                st.bindText(1, id)
                if (st.step()) st.toChunk() else null
            }
        }
    }

    /** Query vector precomputed by the corpus build script, or null. */
    suspend fun precomputedQueryEmbedding(query: String): FloatArray? = io {
        connection.prepare("SELECT embedding FROM query_embeddings WHERE query = ?").use { st ->
            st.bindText(1, query)
            if (st.step()) st.getBlob(0).toFloatArrayLittleEndian() else null
        }
    }

    override fun close() = connection.close()

    // -- internals ---------------------------------------------------------

    private suspend fun <T> io(block: () -> T): T = withContext(dispatcher) { lock.withLock { block() } }

    private fun idForRow(rowId: Long): String? =
        connection.prepare("SELECT id FROM chunks WHERE rowid = ?").use { st ->
            st.bindLong(1, rowId)
            if (st.step()) st.getText(0) else null
        }

    private fun loadEmbeddings(): List<Pair<Long, FloatArray>> =
        connection.prepare("SELECT rowid, embedding FROM chunk_embeddings").use { st ->
            st.collect { it.getLong(0) to it.getBlob(1).toFloatArrayLittleEndian() }
        }

    private fun SQLiteStatement.toChunk() = GuidanceChunk(
        id = getText(0),
        source = getText(1),
        url = getText(2),
        title = getText(3),
        language = if (getText(4) == Language.ES.code) Language.ES else Language.EN,
        text = getText(5),
    )

    private inline fun <T> SQLiteStatement.collect(row: (SQLiteStatement) -> T): List<T> {
        val out = mutableListOf<T>()
        while (step()) out += row(this)
        return out
    }

    private inline fun <R> SQLiteStatement.use(block: (SQLiteStatement) -> R): R =
        try { block(this) } finally { close() }

    companion object {
        /** Quote every word so user-looking text can never inject FTS5 operators. */
        fun toFtsQuery(query: String): String? {
            val terms = Regex("""[\p{L}\p{N}]+""").findAll(query).map { it.value.lowercase() }
                .filter { it.length > 1 }.distinct().toList()
            return if (terms.isEmpty()) null else terms.joinToString(" OR ") { "\"$it\"" }
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f; var na = 0f; var nb = 0f
            for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
        }
    }
}

internal fun FloatArray.toLittleEndianBytes(): ByteArray {
    val out = ByteArray(size * 4)
    forEachIndexed { i, f ->
        val bits = f.toRawBits()
        out[i * 4] = bits.toByte()
        out[i * 4 + 1] = (bits shr 8).toByte()
        out[i * 4 + 2] = (bits shr 16).toByte()
        out[i * 4 + 3] = (bits shr 24).toByte()
    }
    return out
}

internal fun ByteArray.toFloatArrayLittleEndian(): FloatArray = FloatArray(size / 4) { i ->
    val b = i * 4
    Float.fromBits(
        (this[b].toInt() and 0xFF) or
            ((this[b + 1].toInt() and 0xFF) shl 8) or
            ((this[b + 2].toInt() and 0xFF) shl 16) or
            ((this[b + 3].toInt() and 0xFF) shl 24)
    )
}
