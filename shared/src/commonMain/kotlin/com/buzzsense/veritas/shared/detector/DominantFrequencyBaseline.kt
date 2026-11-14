package com.buzzsense.veritas.shared.detector

import com.buzzsense.veritas.shared.model.Verdict

/**
 * Baseline the CNN must beat on held-out sites: call Aedes aegypti when the
 * strongest wingbeat peak falls in a band fitted on the TRAINING sites (not
 * taken from the literature). Load the fitted band from the frozen eval config
 * so the app and the harness use the same numbers.
 */
class DominantFrequencyBaseline(val aedesBandHz: ClosedFloatingPointRange<Float>) {

    fun classify(dominantHz: Float?): Verdict =
        if (dominantHz != null && dominantHz in aedesBandHz) Verdict.YES else Verdict.NO

    companion object {
        /** Fits the band as the [lowerQ]..[upperQ] quantiles of training-site Aedes aegypti peaks. */
        fun fit(
            aedesTrainingFrequenciesHz: List<Float>,
            lowerQ: Double = 0.1,
            upperQ: Double = 0.9,
        ): DominantFrequencyBaseline {
            require(aedesTrainingFrequenciesHz.isNotEmpty()) { "Need Aedes aegypti training clips to fit the band" }
            val sorted = aedesTrainingFrequenciesHz.sorted()
            fun q(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
            return DominantFrequencyBaseline(q(lowerQ)..q(upperQ))
        }
    }
}
