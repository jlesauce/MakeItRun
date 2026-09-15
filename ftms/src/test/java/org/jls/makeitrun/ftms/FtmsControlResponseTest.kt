package org.jls.makeitrun.ftms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtmsControlResponseTest {

    @Test
    fun `a successful response identifies the command it answers`() {
        val response = FtmsControlResponse.parse(byteArrayOf(0x80.toByte(), 0x00, 0x01))!!

        assertEquals(FtmsOpCode.REQUEST_CONTROL, response.requestOpCode)
        assertEquals(FtmsResultCode.SUCCESS, response.result)
        assertTrue(response.isSuccess)
    }

    @Test
    fun `control not permitted is reported as a failure`() {
        val response = FtmsControlResponse.parse(byteArrayOf(0x80.toByte(), 0x02, 0x05))!!

        assertEquals(FtmsOpCode.SET_TARGET_SPEED, response.requestOpCode)
        assertEquals(FtmsResultCode.CONTROL_NOT_PERMITTED, response.result)
        assertFalse(response.isSuccess)
    }

    @Test
    fun `a frame that is not a response code is ignored`() {
        assertNull(FtmsControlResponse.parse(byteArrayOf(0x00, 0x00, 0x01)))
    }

    @Test
    fun `a truncated frame is ignored`() {
        assertNull(FtmsControlResponse.parse(byteArrayOf(0x80.toByte(), 0x00)))
    }
}
