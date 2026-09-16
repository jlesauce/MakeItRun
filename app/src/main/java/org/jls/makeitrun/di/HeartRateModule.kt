package org.jls.makeitrun.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.jls.makeitrun.heartrate.HeartRateClient
import org.jls.makeitrun.heartrate.HeartRateScanner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HeartRateModule {

    @Provides
    @Singleton
    fun provideHeartRateScanner(@ApplicationContext context: Context): HeartRateScanner =
        HeartRateScanner(context)

    @Provides
    @Singleton
    fun provideHeartRateClient(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): HeartRateClient = HeartRateClient(context = context, scope = scope)
}
