package com.buzzsense.veritas.shared.rag

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY

/**
 * Opens the guidance DB with the bundled SQLite build (FTS5 included, same
 * engine on Android and the JVM).
 *
 * @param sqliteVecLibrary path to the sqlite-vec loadable extension
 *   (Android: `libvec0.so` shipped in jniLibs → `applicationInfo.nativeLibraryDir`;
 *   JVM: `vec0.dll` / `vec0.so` / `vec0.dylib`). When null or not loadable,
 *   [GuidanceStore] falls back to a brute-force cosine scan, which is fine for a
 *   corpus of a few hundred chunks.
 */
object GuidanceDb {
    fun open(path: String, sqliteVecLibrary: String? = null): GuidanceStore {
        val driver = BundledSQLiteDriver()
        if (sqliteVecLibrary != null) {
            runCatching { driver.addExtension(sqliteVecLibrary, "sqlite3_vec_init") }
        }
        return GuidanceStore(driver.open(path, SQLITE_OPEN_READONLY))
    }
}
