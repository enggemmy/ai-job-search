package com.unifiedstreaminghub.poc.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.unifiedstreaminghub.poc.domain.HandlerInfo
import com.unifiedstreaminghub.poc.domain.IntentGateway
import com.unifiedstreaminghub.poc.domain.StartResult

/**
 * Production [IntentGateway]. Pass an Activity context so the launch joins the app's task;
 * a non-Activity context gets FLAG_ACTIVITY_NEW_TASK added.
 *
 * Package visibility: [resolveHandlers] relies on the `<queries>` VIEW/BROWSABLE/https intent
 * declared in AndroidManifest.xml. Without it, Android 11+ returns an empty list.
 */
class AndroidIntentGateway(private val context: Context) : IntentGateway {

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
