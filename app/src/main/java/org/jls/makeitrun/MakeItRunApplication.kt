package org.jls.makeitrun

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.SessionHistoryRepository
import org.jls.makeitrun.di.ApplicationScope
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MakeItRunApplication : Application() {

    @Inject
    lateinit var sessionHistory: SessionHistoryRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        scope.launch { sessionHistory.closeInterruptedSessions() }
    }
}
