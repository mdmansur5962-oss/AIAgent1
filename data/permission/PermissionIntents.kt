package com.aiagent.assistant.data.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import com.aiagent.assistant.domain.permission.PermissionId

object PermissionIntents {

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** যে permission Settings-এর আলাদা page-এ চালু করতে হয়, সেই page-এর Intent */
    fun settingsFor(context: Context, id: PermissionId): Intent {
        val pkgUri = Uri.parse("package:${context.packageName}")
        return when (id) {
            PermissionId.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            PermissionId.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            PermissionId.NOTIFICATION_LISTENER,
            PermissionId.MEDIA_CONTROL -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            PermissionId.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            PermissionId.ALARM_REMINDER ->
                if (Build.VERSION.SDK_INT >= 31) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
                } else {
                    appDetails(context)
                }
            PermissionId.APP_USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            PermissionId.INSTALL_UNKNOWN_APPS -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
            PermissionId.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            PermissionId.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            PermissionId.BIOMETRIC ->
                if (Build.VERSION.SDK_INT >= 30) {
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
            else -> appDetails(context)
        }
    }
}
