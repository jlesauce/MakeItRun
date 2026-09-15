package org.jls.makeitrun.ftms

/**
 * Codes d'operation ecrits sur la caracteristique Fitness Machine Control Point (0x2AD9).
 *
 * La machine refuse toute commande de pilotage tant que [REQUEST_CONTROL] n'a pas ete
 * accepte : c'est toujours la premiere ecriture a effectuer apres la connexion.
 */
enum class FtmsOpCode(val value: Byte) {
    REQUEST_CONTROL(0x00),
    RESET(0x01),

    /** Vitesse cible en km/h, encodee sur 2 octets little-endian avec une resolution de 0,01. */
    SET_TARGET_SPEED(0x02),

    /** Pente cible en pourcentage, encodee sur 2 octets signes avec une resolution de 0,1. */
    SET_TARGET_INCLINATION(0x03),

    START_OR_RESUME(0x07),

    /** Arret ou pause : le parametre vaut 0x01 pour stop, 0x02 pour pause. */
    STOP_OR_PAUSE(0x08),

    /** Prefixe des reponses renvoyees par la machine par notification. */
    RESPONSE_CODE(0x80.toByte());

    companion object {
        fun fromValue(value: Byte): FtmsOpCode? = entries.firstOrNull { it.value == value }
    }
}

/** Codes de resultat presents dans les notifications [FtmsOpCode.RESPONSE_CODE]. */
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
