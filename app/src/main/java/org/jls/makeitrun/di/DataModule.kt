package org.jls.makeitrun.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import org.jls.makeitrun.data.MakeItRunDatabase
import org.jls.makeitrun.data.TreadmillProfileRepository
import org.jls.makeitrun.data.TreadmillProfileStore
import org.jls.makeitrun.data.WorkoutDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MakeItRunDatabase =
        Room.databaseBuilder(context, MakeItRunDatabase::class.java, MakeItRunDatabase.NAME)
            .addMigrations(*MakeItRunDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideWorkoutDao(database: MakeItRunDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideTreadmillProfileStore(
        repository: TreadmillProfileRepository,
    ): TreadmillProfileStore = repository
}
