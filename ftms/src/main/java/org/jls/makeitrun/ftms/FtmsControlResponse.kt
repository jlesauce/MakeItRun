package org.jls.makeitrun.ftms

/**
 * Reponse indiquee par la machine apres l'ecriture d'une commande sur le Control Point.
 *
 * La trame fait trois octets : 0x80, le code de la commande concernee, puis le resultat.
 */
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
