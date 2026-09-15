package org.jls.makeitrun

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Point d'entree de l'application. L'annotation [HiltAndroidApp] declenche la generation
 * du conteneur d'injection de dependances utilise par toute l'application.
 */
@HiltAndroidApp
class MakeItRunApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
