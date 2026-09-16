package org.jls.makeitrun.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jls.makeitrun.session.RegulationResponsiveness
import javax.inject.Inject
import javax.inject.Singleton

private val Context.heartRateDataStore: DataStore<Preferences> by
    preferencesDataStore("heart_rate_sensor")

interface HeartRateSensorStore {
    val profile: Flow<HeartRateSensorProfile>
    suspend fun current(): HeartRateSensorProfile
    suspend fun rememberSensor(address: String, name: String?)
    suspend fun forgetSensor()
    suspend fun setResponsiveness(responsiveness: RegulationResponsiveness)
}

@Singleton
class HeartRateSensorRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HeartRateSensorStore {

    override val profile: Flow<HeartRateSensorProfile> =
        context.heartRateDataStore.data.map { preferences ->
            HeartRateSensorProfile(
                address = preferences[KEY_ADDRESS],
                name = preferences[KEY_NAME],
                responsiveness = RegulationResponsiveness.fromName(preferences[KEY_RESPONSIVENESS]),
            )
        }

    override suspend fun current(): HeartRateSensorProfile = profile.first()

    override suspend fun rememberSensor(address: String, name: String?) {
        context.heartRateDataStore.edit { preferences ->
            preferences[KEY_ADDRESS] = address
            name?.let { preferences[KEY_NAME] = it }
        }
    }

    override suspend fun forgetSensor() {
        context.heartRateDataStore.edit { preferences ->
            preferences.remove(KEY_ADDRESS)
            preferences.remove(KEY_NAME)
        }
    }

    override suspend fun setResponsiveness(responsiveness: RegulationResponsiveness) {
        context.heartRateDataStore.edit { it[KEY_RESPONSIVENESS] = responsiveness.name }
    }

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("address")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_RESPONSIVENESS = stringPreferencesKey("responsiveness")
    }
}

data class HeartRateSensorProfile(
    val address: String?,
    val name: String?,
    val responsiveness: RegulationResponsiveness,
) {
    val isKnown: Boolean get() = address != null
}
