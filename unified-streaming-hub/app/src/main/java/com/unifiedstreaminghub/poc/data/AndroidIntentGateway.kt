package com.unifiedstreaminghub.poc.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.unifiedstreaminghub.poc.domain.HandlerInfo
import com.unifiedstreaminghub.poc.domain.InstallInfo
import com.unifiedstreaminghub.poc.domain.InstallState
import com.unifiedstreaminghub.poc.domain.IntentGateway
import com.unifiedstreaminghub.poc.domain.StartResult

/**
 * Production [IntentGateway]. Pass an Activity context so launches join the app's task;
 * a non-Activity context gets FLAG_ACTIVITY_NEW_TASK added.
 *
 * Package visibility (Android 11+): [installInfo] and [launchApp] only see the four provider
 * packages listed in the manifest `<queries>`; [resolveHandlers] relies on the declared
 * VIEW/BROWSABLE/https intent. Needs no root, no accessibility service, no QUERY_ALL_PACKAGES.
 */
class AndroidIntentGateway(private val context: Context) : IntentGateway {

    override fun installInfo(packageName: String): InstallInfo {
        val pm = context.packageManager
        return try {
            val info = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            val enabled = info.applicationInfo?.enabled ?: true
            InstallInfo(
                InstallState.INSTALLED,
                info.versionName,
                if (enabled) null else "disabled in system settings",
            )
        } catch (e: PackageManager.NameNotFoundException) {
            InstallInfo(InstallState.NOT_INSTALLED)
        } catch (e: RuntimeException) {
            InstallInfo(InstallState.UNKNOWN, detail = "${e::class.java.simpleName}: ${e.message}")
        }
    }

    override fun resolveHandlers(url: String): List<HandlerInfo> {
        val pm = context.packageManager
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        return try {
            pm.queryIntentActivities(viewIntent(url), flags)
                .map { HandlerInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.packageName }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    override fun launchApp(packageName: String): StartResult {
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (e: RuntimeException) {
            return StartResult.Failed("${e::class.java.simpleName}: ${e.message}")
        } ?: return StartResult.NoActivity
        return start(intent)
    }

    override fun startInPackage(packageName: String, url: String): StartResult =
        start(viewIntent(url).setPackage(packageName))

    override fun startRequiringNonBrowser(url: String): StartResult =
        start(viewIntent(url).addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER))

    override fun startAny(url: String): StartResult = start(viewIntent(url))

    private fun viewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun start(intent: Intent): StartResult {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            StartResult.Started
        } catch (e: ActivityNotFoundException) {
            StartResult.NoActivity
        } catch (e: SecurityException) {
            StartResult.Failed("SecurityException: ${e.message}")
        } catch (e: RuntimeException) {
            StartResult.Failed("${e::class.java.simpleName}: ${e.message}")
        }
    }
}

object Diagnostics {
    const val TAG = "UnifiedHub"

    /** Sink for [com.unifiedstreaminghub.poc.domain.ProviderLauncher]. View with `adb logcat -s UnifiedHub`. */
    val logcat: (String) -> Unit = { Log.i(TAG, it) }

    fun deviceSummary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
