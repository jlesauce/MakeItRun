package org.jls.makeitrun.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jls.makeitrun.ftms.FtmsScanner
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FtmsModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                Timber.e(error, "Erreur non geree dans la liaison FTMS")
            }
    )

    @Provides
    @Singleton
    fun provideFtmsScanner(@ApplicationContext context: Context): FtmsScanner =
        FtmsScanner(context)

    @Provides
    @Singleton
    fun provideFtmsTreadmillClient(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): FtmsTreadmillClient = FtmsTreadmillClient(context = context, scope = scope)
}
