package com.aiagent.assistant.service.skills

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object PackageUtil {

    data class AppInfo(val label: String, val packageName: String)

    @Suppress("DEPRECATION")
    fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun firstInstalled(context: Context, packages: List<String>): String? =
        packages.firstOrNull { isInstalled(context, it) }

    /** ফোনে ইনস্টল করা, খোলা যায় এমন সব অ্যাপ (নাম অনুযায়ী সাজানো) */
    @Suppress("DEPRECATION")
    fun launchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
