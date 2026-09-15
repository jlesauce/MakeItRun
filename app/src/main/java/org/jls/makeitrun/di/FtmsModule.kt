package org.jls.makeitrun.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jls.makeitrun.ftms.FtmsScanner
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FtmsModule {

    @Provides
    @Singleton
    fun provideFtmsScanner(@ApplicationContext context: Context): FtmsScanner =
        FtmsScanner(context)

    /**
     * Le client est unique et vit aussi longtemps que l'application : la liaison avec le tapis
     * doit survivre a une rotation d'ecran ou a un passage en arriere-plan, sans quoi la seance
     * serait interrompue. Son scope propre est donc independant de celui des ecrans.
     */
    @Provides
    @Singleton
    fun provideFtmsTreadmillClient(@ApplicationContext context: Context): FtmsTreadmillClient =
        FtmsTreadmillClient(
            context = context,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
}
