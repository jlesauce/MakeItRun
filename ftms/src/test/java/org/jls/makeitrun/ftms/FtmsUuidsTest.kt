package org.jls.makeitrun.ftms

import org.junit.Assert.assertEquals
import org.junit.Test

class FtmsUuidsTest {

    @Test
    fun `fitness machine service uses the bluetooth base uuid`() {
        assertEquals(
            "00001826-0000-1000-8000-00805f9b34fb",
            FtmsUuids.FITNESS_MACHINE_SERVICE.toString(),
        )
    }

    @Test
    fun `treadmill data characteristic matches the specification`() {
        assertEquals(
            "00002acd-0000-1000-8000-00805f9b34fb",
            FtmsUuids.TREADMILL_DATA.toString(),
        )
    }

    @Test
    fun `response code op code is decoded from its unsigned value`() {
        assertEquals(FtmsOpCode.RESPONSE_CODE, FtmsOpCode.fromValue(0x80.toByte()))
    }
}
