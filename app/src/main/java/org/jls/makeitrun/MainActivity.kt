package org.jls.makeitrun

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.session.WorkoutSessionEngine
import org.jls.makeitrun.ui.MakeItRunApp
import org.jls.makeitrun.ui.PermissionGate
import org.jls.makeitrun.ui.theme.MakeItRunTheme
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var client: FtmsTreadmillClient

    @Inject
    lateinit var engine: WorkoutSessionEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sessionToResume = engine.runningWorkoutId
        setContent {
            MakeItRunTheme {
                PermissionGate {
                    MakeItRunApp(sessionToResume = sessionToResume)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !engine.state.value.isActive) {
            Timber.i("Fermeture de l'application, deconnexion du tapis")
            client.disconnect()
        }
    }
}
