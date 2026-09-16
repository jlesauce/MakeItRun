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
import org.jls.makeitrun.MainActivity
import org.jls.makeitrun.R
import org.jls.makeitrun.di.ApplicationScope
import org.jls.makeitrun.ftms.FtmsTreadmillClient
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
        startInForeground(buildNotification(getString(R.string.session_preparing)))

        stateJob?.cancel()
        stateJob = scope.launch {
            engine.state.collectLatest { state ->
                if (state.isActive) {
                    notify(state.toNotificationText())
                } else {
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        engine.stop()
        client.disconnect()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stateJob?.cancel()
        stateJob = null
        super.onDestroy()
    }

    private fun SessionState.toNotificationText(): String = when (this) {
        is SessionState.CountingDown ->
            getString(R.string.session_countdown_notification, secondsRemaining)

        is SessionState.Running -> getString(
            R.string.session_running_notification,
            progress.stepIndex + 1,
            progress.stepCount,
        )

        is SessionState.Paused -> getString(R.string.session_paused_notification)
        else -> getString(R.string.session_preparing)
    }

    private fun notify(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WorkoutSessionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutSessionService::class.java))
        }
    }
}
