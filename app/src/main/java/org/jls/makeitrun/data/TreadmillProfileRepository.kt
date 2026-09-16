package org.jls.makeitrun.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jls.makeitrun.ftms.TreadmillCapabilities
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("treadmill_profile")

@Singleton
class TreadmillProfileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    val profile: Flow<TreadmillProfile> = context.dataStore.data.map { preferences ->
        TreadmillProfile(
            address = preferences[KEY_ADDRESS],
            name = preferences[KEY_NAME],
            capabilities = preferences[KEY_CAPABILITIES]?.let(::decodeCapabilities),
            showPaceInsteadOfSpeed = preferences[KEY_SHOW_PACE] ?: true,
        )
    }

    suspend fun rememberTreadmill(
        address: String,
        name: String?,
        capabilities: TreadmillCapabilities?,
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADDRESS] = address
            name?.let { preferences[KEY_NAME] = it }
            capabilities?.let { preferences[KEY_CAPABILITIES] = encodeCapabilities(it) }
        }
    }

    suspend fun setShowPaceInsteadOfSpeed(showPace: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_PACE] = showPace }
    }

    private fun encodeCapabilities(capabilities: TreadmillCapabilities): String =
        json.encodeToString(StoredCapabilities.from(capabilities))

    private fun decodeCapabilities(stored: String): TreadmillCapabilities? =
        runCatching { json.decodeFromString<StoredCapabilities>(stored).toCapabilities() }
            .onFailure { Timber.w(it, "Capacites memorisees illisibles") }
            .getOrNull()

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("address")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_CAPABILITIES = stringPreferencesKey("capabilities")
        val KEY_SHOW_PACE = booleanPreferencesKey("show_pace")
    }
}

data class TreadmillProfile(
    val address: String?,
    val name: String?,
    val capabilities: TreadmillCapabilities?,
    val showPaceInsteadOfSpeed: Boolean,
) {
    val isEmpty: Boolean get() = capabilities == null
}

@Serializable
private data class StoredCapabilities(
    val speedMin: Double?,
    val speedMax: Double?,
    val speedIncrement: Double?,
    val inclinationMin: Double?,
    val inclinationMax: Double?,
    val inclinationIncrement: Double?,
    val canSetTargetSpeed: Boolean,
    val canSetTargetInclination: Boolean,
    val canStartAndStop: Boolean,
) {

    fun toCapabilities() = TreadmillCapabilities(
        speedRange = range(speedMin, speedMax, speedIncrement),
        inclinationRange = range(inclinationMin, inclinationMax, inclinationIncrement),
        canSetTargetSpeed = canSetTargetSpeed,
        canSetTargetInclination = canSetTargetInclination,
        canStartAndStop = canStartAndStop,
    )

    private fun range(min: Double?, max: Double?, increment: Double?) =
        if (min != null && max != null && increment != null) {
            TreadmillCapabilities.ValueRange(min, max, increment)
        } else {
            null
        }

    companion object {
        fun from(capabilities: TreadmillCapabilities) = StoredCapabilities(
            speedMin = capabilities.speedRange?.minimum,
            speedMax = capabilities.speedRange?.maximum,
            speedIncrement = capabilities.speedRange?.increment,
            inclinationMin = capabilities.inclinationRange?.minimum,
            inclinationMax = capabilities.inclinationRange?.maximum,
            inclinationIncrement = capabilities.inclinationRange?.increment,
            canSetTargetSpeed = capabilities.canSetTargetSpeed,
            canSetTargetInclination = capabilities.canSetTargetInclination,
            canStartAndStop = capabilities.canStartAndStop,
        )
    }
}
