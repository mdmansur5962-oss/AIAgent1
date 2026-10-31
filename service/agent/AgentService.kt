package com.aiagent.assistant.service.agent

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aiagent.assistant.MainActivity
import com.aiagent.assistant.data.local.SettingsRepository
import com.aiagent.assistant.domain.settings.AppSettings
import com.aiagent.assistant.presentation.navigation.LaunchRouter
import com.aiagent.assistant.service.voice.SpeakResult
import com.aiagent.assistant.service.voice.SpeechOutput
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background Agent: foreground service (সবসময় থাকা notification), ভাসমান বাবল, ব্যাকগ্রাউন্ড wake word,
 * আর notification থেকে প্রশ্ন-উত্তর। Task queue চালায় AgentController।
 */
@AndroidEntryPoint
class AgentService : Service() {

    @Inject
    lateinit var controller: AgentController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubble: BubbleOverlay? = null
    private var wake: BackgroundWake? = null
    private var output: SpeechOutput? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var micForeground = false
    private var statusText = ""

    @Volatile
    private var latest = AppSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AgentNotifications.ensureChannels(this)
        controller.onServiceStarted()
        scope.launch {
            settingsRepository.settings.collect { settings ->
                latest = settings
                reconcile(settings)
            }
        }
        scope.launch {
            controller.events.collect { event -> handleEvent(event) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFromUser()
            return START_NOT_STICKY
        }
        // ৫ সেকেন্ডের মধ্যে foreground-এ যেতে হয়, তাই আগে সাধারণ ধরনে শুরু; wake word লাগলে পরে mic যোগ হয়
        goForeground(includeMic = micForeground)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        bubble?.hide()
        bubble = null
        wake?.stop()
        wake = null
        output?.shutdown()
        output = null
        releaseWakeLock()
        controller.onServiceStopped()
        super.onDestroy()
    }

    // ───────────── foreground ─────────────

    private fun goForeground(includeMic: Boolean): Boolean {
        val notification = AgentNotifications.status(this, latest, statusText, wake != null)
        var type = 0
        if (Build.VERSION.SDK_INT >= 34) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (includeMic && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        return try {
            ServiceCompat.startForeground(this, AgentNotifications.ID_STATUS, notification, type)
            micForeground = includeMic
            true
        } catch (e: Exception) {
            // mic-সহ ব্যর্থ হলে (যেমন অ্যাপ সামনে নেই) mic ছাড়া আবার চেষ্টা
            if (includeMic) {
                false
            } else {
                stopSelf()
                false
            }
        }
    }

    private fun refreshStatus() {
        AgentNotifications.update(this, AgentNotifications.status(this, latest, statusText, wake != null))
    }

    // ───────────── settings → what runs ─────────────

    private fun reconcile(settings: AppSettings) {
        // বাবল
        if (settings.bubbleEnabled && Settings.canDrawOverlays(this)) {
            val letter = settings.appName.trim().firstOrNull()?.uppercase() ?: "M"
            val existing = bubble
            if (existing == null) {
                bubble = BubbleOverlay(this, bubbleCallbacks).also { it.show(letter) }
            } else {
                existing.show(letter)
            }
        } else {
            bubble?.hide()
            bubble = null
        }

        // ব্যাকগ্রাউন্ড wake word
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (settings.wakeWordEnabled && micGranted) {
            startWake()
        } else {
            if (settings.wakeWordEnabled && !micGranted) {
                controller.setWakeError("Microphone permission is missing · মাইকের অনুমতি নেই")
            }
            stopWake()
        }
        refreshStatus()
    }

    private fun startWake() {
        if (wake != null) return
        if (!micForeground && !goForeground(includeMic = true)) {
            controller.setWakeError(
                "Wake word needs the app to be opened once · wake word চালাতে অ্যাপটা একবার খুলুন"
            )
            goForeground(includeMic = false)
            return
        }
        val engine = output ?: SpeechOutput(applicationContext).also { output = it }
        engine.hashCode() // TTS আগে থেকে প্রস্তুত রাখা
        wake = BackgroundWake(
            context = this,
            settings = { latest },
            onWake = { /* wake word শুনেছে */ },
            onCommand = { text ->
                wake?.pause()
                controller.enqueueAsk(text, spoken = true, source = "wake")
            },
            onFatal = { message ->
                controller.setWakeError(message)
                stopWake()
                refreshStatus()
            }
        ).also { it.start() }
        acquireWakeLock()
        controller.setWakeListening(true)
    }

    private fun stopWake() {
        val running = wake
        if (running != null) {
            running.stop()
            wake = null
        }
        releaseWakeLock()
        controller.setWakeListening(false)
        if (micForeground) goForeground(includeMic = false)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myra:wake").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ───────────── task events ─────────────

    private fun handleEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.TaskStarted -> {
                statusText = "Thinking… · ভাবছি: ${event.prompt.take(40)}"
                refreshStatus()
            }
            is AgentEvent.TaskDone -> {
                statusText = ""
                refreshStatus()
                AgentNotifications.showReply(this, latest, event.chatId, event.prompt, event.reply)
                if (event.spoken && latest.speakReplies) speak(event.reply) else wake?.resume()
            }
            is AgentEvent.TaskFailed -> {
                statusText = ""
                refreshStatus()
                AgentNotifications.showError(this, latest, event.message)
                wake?.resume()
            }
        }
    }

    private fun speak(reply: String) {
        val engine = output ?: SpeechOutput(applicationContext).also { output = it }
        val result = engine.speak(reply, null, latest.voiceStyle, latest.voiceSpeed, latest.voicePitch) {
            mainHandler.post { wake?.resume() }
        }
        if (result != SpeakResult.OK) wake?.resume()
    }

    // ───────────── bubble actions ─────────────

    private val bubbleCallbacks = object : BubbleOverlay.Callbacks {
        override fun onTalk() = openApp("voice?chatId=-1")
        override fun onOpenChat() = openApp("chat")
        override fun onStopAgent() = stopFromUser()
    }

    private fun openApp(route: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(LaunchRouter.EXTRA_ROUTE, route)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // পটভূমি থেকে অ্যাপ খোলা আটকে গেলে কিছু করার নেই
        }
    }

    /** ব্যবহারকারী বন্ধ করলে সেটিংসেও বন্ধ রাখে, যাতে ফোন চালু বা অ্যাপ খুললে আবার না চলে */
    private fun stopFromUser() {
        CoroutineScope(Dispatchers.IO).launch {
            settingsRepository.update { it.copy(backgroundAgentEnabled = false) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.aiagent.assistant.action.STOP_AGENT"

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, AgentService::class.java))
            true
        } catch (e: Exception) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentService::class.java))
        }
    }
}
