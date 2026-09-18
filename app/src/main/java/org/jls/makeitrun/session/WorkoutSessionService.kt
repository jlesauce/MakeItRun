package org.jls.makeitrun.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jls.makeitrun.MainActivity
import org.jls.makeitrun.R
import org.jls.makeitrun.di.ApplicationScope
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.Formats
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutSessionService : Service() {

    @Inject
    lateinit var engine: WorkoutSessionEngine

    @Inject
    lateinit var client: FtmsTreadmillClient

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startInForeground(
            buildNotification(
                title = getString(R.string.app_name),
                text = getString(R.string.session_preparing),
            )
        )

        stateJob?.cancel()
        stateJob = scope.launch {
            var sessionStarted = false
            engine.state.collectLatest { state ->
                when {
                    state.isActive -> {
                        sessionStarted = true
                        notify(state.toNotificationTitle(), state.toNotificationText())
                    }

                    state is SessionState.Idle && !sessionStarted -> Unit
                    else -> stopWithoutNotification()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        runBlocking {
            withTimeoutOrNull(TREADMILL_STOP_TIMEOUT_MILLIS) { engine.stopAndAwaitTreadmill() }
                ?: Timber.w("Tapis non arrete avant la fermeture de l'application")
        }
        client.disconnect()
        stopWithoutNotification()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stateJob?.cancel()
        stateJob = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun stopWithoutNotification() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun SessionState.toNotificationTitle(): String = when (this) {
        is SessionState.Running -> progress.workoutName
        is SessionState.Paused -> progress.workoutName
        else -> getString(R.string.app_name)
    }

    private fun SessionState.toNotificationText(): String = when (this) {
        is SessionState.CountingDown ->
            getString(R.string.session_countdown_notification, secondsRemaining)

        is SessionState.Running -> getString(
            R.string.session_running_notification,
            progress.stepIndex + 1,
            progress.stepCount,
            getString(WorkoutStepLabels.typeNameRes(progress.currentStep.step.type)),
            progress.remaining.toText(),
        )

        is SessionState.Paused -> if (isTreadmillStopped) {
            getString(R.string.session_treadmill_stopped)
        } else {
            getString(
                R.string.session_paused_notification,
                progress.stepIndex + 1,
                progress.stepCount,
            )
        }

        else -> getString(R.string.session_preparing)
    }

    private fun StepRemaining.toText(): String = when (this) {
        is StepRemaining.Seconds -> Formats.duration(value)
        is StepRemaining.Meters -> Formats.distance(value)
    }

    private fun notify(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "workout_session"
        private const val NOTIFICATION_ID = 1
        private const val TREADMILL_STOP_TIMEOUT_MILLIS = 3_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WorkoutSessionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutSessionService::class.java))
        }
    }
}
