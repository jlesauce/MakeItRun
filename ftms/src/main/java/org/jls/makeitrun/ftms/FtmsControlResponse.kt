package org.jls.makeitrun.ftms

data class FtmsControlResponse(
    val requestOpCode: FtmsOpCode?,
    val result: FtmsResultCode?,
) {

    val isSuccess: Boolean
        get() = result == FtmsResultCode.SUCCESS

    companion object {

        fun parse(payload: ByteArray): FtmsControlResponse? {
            if (payload.size < 3) return null
            if (payload[0] != FtmsOpCode.RESPONSE_CODE.value) return null
            return FtmsControlResponse(
                requestOpCode = FtmsOpCode.fromValue(payload[1]),
                result = FtmsResultCode.fromValue(payload[2]),
            )
        }
    }
}
