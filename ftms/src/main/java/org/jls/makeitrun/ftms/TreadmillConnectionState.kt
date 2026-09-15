package org.jls.makeitrun.ftms

/** Etapes traversees lors de l'etablissement d'une liaison avec un tapis. */
sealed interface TreadmillConnectionState {

    data object Disconnected : TreadmillConnectionState

    data object Connecting : TreadmillConnectionState

    /** La liaison est etablie, les services GATT sont en cours d'inventaire. */
    data object DiscoveringServices : TreadmillConnectionState

    /**
     * @property canBeControlled faux si le tapis expose ses mesures mais pas le Control Point :
     * on peut alors lire la seance sans pouvoir la piloter.
     */
    data class Connected(
        val address: String,
        val canBeControlled: Boolean,
    ) : TreadmillConnectionState

    data class Failed(val reason: String) : TreadmillConnectionState
}

/** Erreur protocolaire FTMS, distincte des erreurs Bluetooth de plus bas niveau. */
class FtmsException(message: String) : Exception(message)
