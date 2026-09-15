package org.jls.makeitrun.ftms

import java.util.UUID

/**
 * Identifiants GATT definis par la specification Bluetooth SIG "Fitness Machine Service".
 *
 * Tous les UUID 16 bits sont etendus vers leur forme 128 bits via la Bluetooth Base UUID
 * `0000xxxx-0000-1000-8000-00805F9B34FB`.
 */
object FtmsUuids {

    private fun shortUuid(assignedNumber: Int): UUID =
        UUID.fromString("%08X-0000-1000-8000-00805F9B34FB".format(assignedNumber))

    /** Service Fitness Machine (0x1826). */
    val FITNESS_MACHINE_SERVICE: UUID = shortUuid(0x1826)

    /** Service Device Information (0x180A) : fabricant, modele, versions firmware. */
    val DEVICE_INFORMATION_SERVICE: UUID = shortUuid(0x180A)

    /** Fitness Machine Feature (0x2ACC) : capacites supportees par la machine. */
    val FITNESS_MACHINE_FEATURE: UUID = shortUuid(0x2ACC)

    /** Treadmill Data (0x2ACD) : notifications de vitesse, distance, duree, pente... */
    val TREADMILL_DATA: UUID = shortUuid(0x2ACD)

    /** Training Status (0x2AD3) : etat de l'entrainement en cours. */
    val TRAINING_STATUS: UUID = shortUuid(0x2AD3)

    /** Supported Speed Range (0x2AD4) : vitesses min/max et increment. */
    val SUPPORTED_SPEED_RANGE: UUID = shortUuid(0x2AD4)

    /** Supported Inclination Range (0x2AD5) : pentes min/max et increment. */
    val SUPPORTED_INCLINATION_RANGE: UUID = shortUuid(0x2AD5)

    /** Fitness Machine Control Point (0x2AD9) : envoi des commandes de pilotage. */
    val FITNESS_MACHINE_CONTROL_POINT: UUID = shortUuid(0x2AD9)

    /** Fitness Machine Status (0x2ADA) : notifications d'etat suite aux commandes. */
    val FITNESS_MACHINE_STATUS: UUID = shortUuid(0x2ADA)
}
