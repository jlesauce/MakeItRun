package org.jls.makeitrun.ftms

/**
 * Tapis detecte pendant un scan Bluetooth.
 *
 * @property address adresse MAC, stable et utilisee pour etablir la connexion.
 * @property rssi puissance du signal en dBm ; plus la valeur est proche de zero, plus le tapis
 * est proche. Vaut `null` pour un appareil deja apparie et non encore vu par le scan.
 * @property advertisesFitnessMachine vrai si l'appareil annonce le service FTMS dans sa trame
 * d'advertising. Un tapis compatible qui ne l'annonce pas reste pilotable, mais ne peut pas
 * etre trouve par un scan filtre.
 */
data class DiscoveredTreadmill(
    val name: String?,
    val address: String,
    val rssi: Int?,
    val advertisesFitnessMachine: Boolean,
)
