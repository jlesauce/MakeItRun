package org.jls.makeitrun.heartrate

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateUuidsTest {

    @Test
    fun `heart rate service uses the bluetooth base uuid`() {
        assertEquals(
            "0000180d-0000-1000-8000-00805f9b34fb",
            HeartRateUuids.HEART_RATE_SERVICE.toString(),
        )
    }

    @Test
    fun `heart rate measurement characteristic matches the specification`() {
        assertEquals(
            "00002a37-0000-1000-8000-00805f9b34fb",
            HeartRateUuids.HEART_RATE_MEASUREMENT.toString(),
        )
    }
}
