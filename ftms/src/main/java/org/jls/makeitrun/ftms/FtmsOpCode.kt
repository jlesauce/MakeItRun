package org.jls.makeitrun.ftms

enum class FtmsOpCode(val value: Byte) {
    REQUEST_CONTROL(0x00),
    RESET(0x01),
    SET_TARGET_SPEED(0x02),
    SET_TARGET_INCLINATION(0x03),
    START_OR_RESUME(0x07),
    STOP_OR_PAUSE(0x08),
    RESPONSE_CODE(0x80.toByte());

    companion object {
        fun fromValue(value: Byte): FtmsOpCode? = entries.firstOrNull { it.value == value }
    }
}

enum class FtmsResultCode(val value: Byte) {
    SUCCESS(0x01),
    OP_CODE_NOT_SUPPORTED(0x02),
    INVALID_PARAMETER(0x03),
    OPERATION_FAILED(0x04),
    CONTROL_NOT_PERMITTED(0x05);

    companion object {
        fun fromValue(value: Byte): FtmsResultCode? = entries.firstOrNull { it.value == value }
    }
}
