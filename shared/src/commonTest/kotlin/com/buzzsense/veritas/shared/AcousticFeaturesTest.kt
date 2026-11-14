package com.buzzsense.veritas.shared

import com.buzzsense.veritas.shared.detector.AcousticFeatures
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class AcousticFeaturesTest {

    @Test
    fun harmonicSumFindsTheWingbeatFundamental() {
        val f = AcousticFeatures(TEST_SPEC)
        // A pure tone is ambiguous for a harmonic sum (150/200/300 Hz tie); use 4 harmonics like a wingbeat.
        val window = f.windows(harmonicSamples(f0 = 600.0), 8_000).first()
        val hz = f.f0Hz(window)
        assertTrue(abs(hz - 600f) < 3f, "got $hz")
    }
}
