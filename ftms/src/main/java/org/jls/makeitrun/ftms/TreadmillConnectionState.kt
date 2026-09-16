package org.jls.makeitrun.ftms

sealed interface TreadmillConnectionState {

    data object Disconnected : TreadmillConnectionState

    data object Connecting : TreadmillConnectionState

    data object DiscoveringServices : TreadmillConnectionState

    data object Reconnecting : TreadmillConnectionState

    data class Connected(
        val address: String,
        val canBeControlled: Boolean,
    ) : TreadmillConnectionState

    data class Failed(val reason: String) : TreadmillConnectionState
}

class FtmsException(message: String) : Exception(message)
