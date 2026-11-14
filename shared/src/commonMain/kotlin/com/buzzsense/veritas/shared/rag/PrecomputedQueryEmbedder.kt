package com.buzzsense.veritas.shared.rag

/**
 * Every retrieval query comes from [GuidanceQueryBuilder] (detection × weather ×
 * risk × language), so the set of possible queries is small and fixed. The
 * laptop build script embeds all of them with multilingual-e5-small and stores
 * them in the guidance DB (`query_embeddings` table, see [GuidanceStore]).
 *
 * This avoids shipping a SentencePiece tokenizer on the phone while keeping the
 * exact same model and vectors as the corpus. Unknown queries fall back to
 * [fallback] (e.g. `OnnxE5Embedder`) when provided.
 */
class PrecomputedQueryEmbedder(
    private val store: GuidanceStore,
    override val dimensions: Int = 384,
    private val fallback: Embedder? = null,
) : Embedder {

    override suspend fun embed(text: String, isQuery: Boolean): FloatArray =
        (if (isQuery) store.precomputedQueryEmbedding(text) else null)
            ?: fallback?.embed(text, isQuery)
            ?: error("No precomputed embedding for query and no fallback embedder: \"$text\"")
}
