package org.jls.makeitrun.heartrate

sealed interface HeartRateConnectionState {

    data object Disconnected : HeartRateConnectionState

    data object Connecting : HeartRateConnectionState

    data object DiscoveringServices : HeartRateConnectionState

    data object Reconnecting : HeartRateConnectionState

    data class Connected(val address: String) : HeartRateConnectionState

    data class Failed(val reason: String) : HeartRateConnectionState
}

class HeartRateException(message: String) : Exception(message)
