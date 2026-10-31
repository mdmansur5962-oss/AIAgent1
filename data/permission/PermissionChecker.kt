package com.aiagent.assistant.data.permission

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.Manifest
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aiagent.assistant.domain.permission.PermissionCatalog
import com.aiagent.assistant.domain.permission.PermissionDef
import com.aiagent.assistant.domain.permission.PermissionId
import com.aiagent.assistant.domain.permission.PermissionKind
import com.aiagent.assistant.domain.permission.PermissionStatus
import com.aiagent.assistant.service.accessibility.AgentAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sdk: Int get() = Build.VERSION.SDK_INT

    fun status(id: PermissionId): PermissionStatus = status(PermissionCatalog.byId(id))

    fun status(def: PermissionDef): PermissionStatus = when (def.id) {
        PermissionId.ACCESSIBILITY -> flag(isAccessibilityEnabled())
        PermissionId.OVERLAY -> flag(Settings.canDrawOverlays(context))
        PermissionId.NOTIFICATION_LISTENER,
        PermissionId.MEDIA_CONTROL -> flag(isNotificationListenerEnabled())
        PermissionId.BATTERY_OPTIMIZATION -> flag(isIgnoringBatteryOptimizations())
        PermissionId.ALARM_REMINDER -> flag(canScheduleExactAlarms())
        PermissionId.APP_USAGE -> flag(hasUsageAccess())
        PermissionId.INSTALL_UNKNOWN_APPS -> flag(context.packageManager.canRequestPackageInstalls())
        PermissionId.WRITE_SETTINGS -> flag(Settings.System.canWrite(context))
        PermissionId.DND_ACCESS -> flag(notificationManager().isNotificationPolicyAccessGranted)
        PermissionId.BIOMETRIC -> biometricStatus()
        PermissionId.SPEECH_RECOGNITION ->
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                PermissionStatus.NOT_SUPPORTED
            } else {
                flag(allGranted(def.runtime(sdk)))
            }
        PermissionId.BACKGROUND_LOCATION ->
            if (sdk < 29) status(PermissionId.LOCATION) else flag(allGranted(def.runtime(sdk)))
        PermissionId.NFC ->
            if (NfcAdapter.getDefaultAdapter(context) == null) {
                PermissionStatus.NOT_SUPPORTED
            } else {
                flag(allGranted(def.normal))
            }
        else -> when (def.kind) {
            PermissionKind.RUNTIME -> flag(allGranted(def.runtime(sdk)))
            PermissionKind.NORMAL -> flag(allGranted(def.normal))
            PermissionKind.ON_DEMAND -> PermissionStatus.ON_DEMAND
            PermissionKind.INFO -> PermissionStatus.NOT_NEEDED
            PermissionKind.SPECIAL -> PermissionStatus.DENIED
        }
    }

    /** এখনো যে runtime permission-গুলো চাইতে হবে */
    fun runtimeToRequest(def: PermissionDef): List<String> {
        if (def.id == PermissionId.BACKGROUND_LOCATION &&
            !isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            // আগে foreground location দরকার
            return PermissionCatalog.byId(PermissionId.LOCATION).runtime(sdk).filter { !isGranted(it) }
        }
        return def.runtime(sdk).filter { !isGranted(it) }
    }

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Permission-টা AndroidManifest.xml-এ declare করা আছে কিনা */
    fun isDeclared(permission: String): Boolean = requestedPermissions().contains(permission)

    // ─────────── helpers ───────────

    private fun flag(granted: Boolean): PermissionStatus =
        if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED

    private fun allGranted(permissions: List<String>): Boolean = permissions.all { isGranted(it) }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, AgentAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun isNotificationListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (sdk < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (sdk >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun biometricStatus(): PermissionStatus =
        when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> PermissionStatus.GRANTED
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> PermissionStatus.DENIED
            else -> PermissionStatus.NOT_SUPPORTED
        }

    @Suppress("DEPRECATION")
    private fun requestedPermissions(): Array<String> {
        val pm = context.packageManager
        val info = if (sdk >= 33) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        return info.requestedPermissions ?: emptyArray()
    }
}
