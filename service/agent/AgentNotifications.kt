package com.aiagent.assistant.service.agent

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.aiagent.assistant.MainActivity
import com.aiagent.assistant.R
import com.aiagent.assistant.domain.settings.AppSettings
import com.aiagent.assistant.presentation.navigation.LaunchRouter

object AgentNotifications {

    const val CHANNEL_STATUS = "agent_status"
    const val CHANNEL_REPLIES = "agent_replies"

    const val ID_STATUS = 4101
    const val ID_REPLY = 4102
    const val ID_ERROR = 4103

    const val KEY_REPLY = "key_agent_reply"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Background agent · ব্যাকগ্রাউন্ড এজেন্ট", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that the assistant is running in the background"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REPLIES, "Assistant replies · উত্তর", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Answers to questions asked from the notification or by voice"
            }
        )
    }

    private fun openApp(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(LaunchRouter.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun replyAction(context: Context, assistantName: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Ask $assistantName…")
            .build()
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, AgentActionReceiver::class.java).setAction(AgentActionReceiver.ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_agent, "Ask · জিজ্ঞেস", pending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    /** সবসময় থাকা notification (foreground service-এর) */
    fun status(context: Context, settings: AppSettings, statusText: String, wakeActive: Boolean): Notification {
        val stopPending = PendingIntent.getService(
            context,
            12,
            Intent(context, AgentService::class.java).setAction(AgentService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            statusText.isNotBlank() -> statusText
            wakeActive -> "Listening for \"${settings.wakeWord}\" · \"${settings.wakeWord}\" শুনছি"
            else -> "Ready · এখান থেকেই জিজ্ঞেস করুন"
        }
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("${settings.appName} is running · চালু আছে")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, "home", 13))
            .addAction(replyAction(context, settings.assistantName))
            .addAction(R.drawable.ic_stat_agent, "Talk · কথা বলুন", openApp(context, "voice?chatId=-1", 14))
            .addAction(R.drawable.ic_stat_agent, "Stop · বন্ধ", stopPending)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showReply(context: Context, settings: AppSettings, chatId: Long, prompt: String, reply: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(settings.assistantName)
            .setContentText(reply.take(100))
            .setSubText(prompt.take(40))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openApp(context, "chat/$chatId", 15))
            .addAction(replyAction(context, settings.assistantName))
            .build()
        notify(context, ID_REPLY, notification)
    }

    @SuppressLint("MissingPermission")
    fun showError(context: Context, settings: AppSettings, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("${settings.assistantName} · ⚠")
            .setContentText(message.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "chat", 16))
            .build()
        notify(context, ID_ERROR, notification)
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, notification: Notification) {
        notify(context, ID_STATUS, notification)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notification-এর অনুমতি না থাকলে দেখানো যায় না
        }
    }
}
