package com.buzzsense.veritas.shared.detector

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.buzzsense.veritas.shared.rag.toLittleEndianBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One indexed clip close to the query. */
data class Neighbour(val clipId: String, val class3: String, val species: String, val distance: Float)

/**
 * The acoustic store `jumantik_rag.sqlite` built by `pipeline/build_rag.py`:
 *
 * ```sql
 * clips(id, clip_id, source, path, offset_s, species, class3, domain, split, device, country,
 *       f0_hz, centroid_hz, rolloff_hz, flatness, rms_db, description)
 * clips_vec USING vec0(id, embedding float[80] distance_metric=cosine, class3 TEXT, split TEXT)
 * clips_fts / profiles / profiles_fts  (text evidence)
 * ```
 *
 * kNN needs the sqlite-vec extension: without it the 365k-vector table cannot be read.
 */
class AcousticStore(
    private val connection: SQLiteConnection,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {

    private val lock = Mutex()

    init {
        val ok = runCatching { connection.prepare("SELECT vec_version()").let { st -> try { st.step() } finally { st.close() } } }
            .getOrDefault(false)
        check(ok) { "sqlite-vec is not loaded: the acoustic store needs it for kNN (see AcousticStore.open)" }
    }

    /** Cosine kNN restricted to the [split] the eval indexes (train), as in `eval_retrieval.py`. */
    suspend fun nearest(embedding: FloatArray, k: Int, split: String = "train"): List<Neighbour> =
        withContext(dispatcher) {
            lock.withLock {
                val st = connection.prepare(
                    """
                    SELECT c.clip_id, c.class3, c.species, v.distance
                    FROM clips_vec v JOIN clips c ON c.id = v.id
                    WHERE v.embedding MATCH ? AND k = ? AND v.split = ?
                    ORDER BY v.distance
                    """.trimIndent()
                )
                try {
                    st.bindBlob(1, embedding.toLittleEndianBytes())
                    st.bindLong(2, k.toLong())
                    st.bindText(3, split)
                    buildList {
                        while (st.step()) add(Neighbour(st.getText(0), st.getText(1), st.getText(2), st.getDouble(3).toFloat()))
                    }
                } finally {
                    st.close()
                }
            }
        }

    override fun close() = connection.close()

    companion object {
        /** @param sqliteVecLibrary path to vec0 (`libvec0.so` on Android, `vec0.dll/.so/.dylib` on the JVM). */
        fun open(path: String, sqliteVecLibrary: String): AcousticStore {
            val driver = BundledSQLiteDriver()
            driver.addExtension(sqliteVecLibrary, "sqlite3_vec_init")
            return AcousticStore(driver.open(path, SQLITE_OPEN_READONLY))
        }
    }
}
