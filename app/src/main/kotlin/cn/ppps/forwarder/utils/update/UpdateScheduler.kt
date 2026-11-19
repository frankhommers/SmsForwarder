package cn.ppps.forwarder.utils.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import cn.ppps.forwarder.service.ForegroundService
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.AUTO_CHECK_UPDATE
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.SharedPreference
import java.util.concurrent.TimeUnit

/**
 * Daily background check. WorkManager keeps periodic work across reboots (it reschedules after
 * BOOT_COMPLETED itself) and runs it only with a network connection. The worker additionally refuses to
 * contact GitHub when the previous automatic pass started less than 24 hours ago (see [decide]), so restarts,
 * reschedules, a changed clock or a replaced package never make it run more often than once a day.
 */
object UpdateScheduler {
    private const val TAG = "Updater"
    const val WORK_NAME = "daily_update_check"
    private const val MIGRATION_KEY = "updater_defaults_version"
    const val MIN_GAP_HOURS = 24L

    /**
     * Default ON for every install, once: the old key could be absent (never touched) or false (stored by the
     * removed upstream updater, or the old forced-off screen). Both predate this updater, so the first start of
     * a build with this updater turns automatic checks on one time; after that only the user's choice counts.
     */
    fun migrateDefaults() {
        val prefs = SharedPreference.preference
        if (prefs.getInt(MIGRATION_KEY, 0) >= 1) return
        prefs.edit().putBoolean(AUTO_CHECK_UPDATE, true).putInt(MIGRATION_KEY, 1).apply()
        Log.i(TAG, "automatic update checks defaulted to on")
    }

    /** Schedules or cancels the daily check according to the setting. Safe to call repeatedly (KEEP). */
    fun apply(context: Context) {
        val wm = try { WorkManager.getInstance(context.applicationContext) } catch (e: Exception) { Log.w(TAG, "WorkManager unavailable: $e"); return }
        if (!SettingUtils.autoCheckUpdate) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(UpdatePolicy.CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .addTag(WORK_NAME)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    enum class Action { RUN, NOT_DUE, RESET_CLOCK, OFFLINE_RETRY, BUSY }

    /**
     * Pure decision of one automatic run. [lastStart] is when the previous automatic network pass STARTED
     * (stamped before its first request, whatever its outcome).
     * - Less than 24 hours since then: NOT_DUE.
     * - Clock earlier than the stamp (time changed backwards): NOT_DUE while the stamp is at most 24 hours ahead;
     *   further ahead the stamp is implausible and is reset to now (RESET_CLOCK), which also does not run now.
     * - No network: OFFLINE_RETRY (nothing is sent; WorkManager retries with backoff).
     * - Another pass already running (manual Check now): BUSY, not counted as today's automatic check.
     */
    fun decide(now: Long, lastStart: Long, online: Boolean, busy: Boolean): Action {
        val day = TimeUnit.HOURS.toMillis(MIN_GAP_HOURS)
        if (lastStart > 0L) {
            if (now < lastStart) return if (lastStart - now > day) Action.RESET_CLOCK else Action.NOT_DUE
            if (now - lastStart < day) return Action.NOT_DUE
        }
        if (!online) return Action.OFFLINE_RETRY
        if (busy) return Action.BUSY
        return Action.RUN
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        if (!SettingUtils.autoCheckUpdate) return Result.success()
        val prefs = UpdateManager.prefs(ctx)
        val now = System.currentTimeMillis()
        when (UpdateScheduler.decide(now, prefs.getLong(UpdateManager.KEY_LAST_AUTO_CHECK, 0), online(ctx), UpdateManager.isRunning)) {
            UpdateScheduler.Action.NOT_DUE, UpdateScheduler.Action.BUSY -> return Result.success()
            UpdateScheduler.Action.RESET_CLOCK -> { prefs.edit().putLong(UpdateManager.KEY_LAST_AUTO_CHECK, now).apply(); return Result.success() }
            UpdateScheduler.Action.OFFLINE_RETRY -> return if (runAttemptCount < 3) Result.retry() else Result.success()
            UpdateScheduler.Action.RUN -> {}
        }
        // Stamp the start before the first request: any pass that may have reached GitHub counts as today's check,
        // including one that fails halfway or is rate-limited. No retry until the next day.
        val started = UpdateManager.run(ctx, interactive = false) { prefs.edit().putLong(UpdateManager.KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).commit() }
        return Result.success().also { if (!started) android.util.Log.i("Updater", "automatic check skipped: a check is already running") }
    }

    @Suppress("DEPRECATION")
    private fun online(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return cm.activeNetworkInfo?.isConnected == true
    }
}

/**
 * After an update Android kills the old process and sends MY_PACKAGE_REPLACED to the new version (a protected
 * broadcast only the system can send). Starting this receiver creates the process, so App.onCreate starts the
 * foreground forwarding service exactly as after BOOT_COMPLETED; this receiver makes sure of it, records the
 * installed version and keeps the daily check scheduled.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i("Updater", "package replaced, now ${UpdateManager.installedVersionCode(context)}")
        if (!SettingUtils.enablePureClientMode && !ForegroundService.isRunning) {
            try {
                val service = Intent(context, ForegroundService::class.java).setAction(ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
            } catch (e: Exception) { Log.w("Updater", "foreground service not started: $e") }
        }
        val prefs = UpdateManager.prefs(context)
        val pending = UpdatePolicy.parseManifest(prefs.getString(UpdateManager.KEY_PENDING_MANIFEST, "") ?: "")
        if (pending != null && pending.versionCode.toLong() == UpdateManager.installedVersionCode(context)) {
            UpdateManager.recordInstallState(context, UpdateResult.INSTALLED, pending.versionName)
            prefs.edit().remove(UpdateManager.KEY_PENDING_MANIFEST).apply()
        }
        UpdateManager.apkFile(context).delete()
        UpdateInstaller.cancelNotification(context)
        UpdateScheduler.apply(context)
    }
}
