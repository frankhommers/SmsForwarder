package cn.ppps.forwarder.utils.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Runs one update pass: release list -> newest valid manifest -> download -> verification -> installer.
 * State is kept in the "updater" preferences so the About screen and the background worker share it.
 */
object UpdateManager {
    private const val TAG = "Updater"
    const val PREFS = "updater"
    const val KEY_LAST_CHECK = "last_check_time"
    const val KEY_LAST_AUTO_CHECK = "last_auto_check_time"
    const val KEY_RESULT = "result"
    const val KEY_RESULT_ARG = "result_arg"
    const val KEY_RUNNING = "running"
    const val KEY_PROGRESS_DONE = "progress_done"
    const val KEY_PROGRESS_TOTAL = "progress_total"
    const val KEY_PENDING_MANIFEST = "pending_manifest"

    private val pass = SinglePass()

    fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun updateDir(context: Context) = File(context.applicationContext.filesDir, "updates").apply { mkdirs() }
    fun apkFile(context: Context) = File(updateDir(context), "update.apk")

    val isRunning: Boolean get() = pass.busy

    private fun record(context: Context, kind: String, arg: String = "", checked: Boolean = false) {
        val e = prefs(context).edit().putString(KEY_RESULT, kind).putString(KEY_RESULT_ARG, arg)
        if (checked) e.putLong(KEY_LAST_CHECK, System.currentTimeMillis())
        e.apply()
        Log.i(TAG, "result=$kind ${if (arg.isEmpty()) "" else "($arg)"}")
    }

    fun recordInstallState(context: Context, kind: String, arg: String) = record(context, kind, arg)

    /** Starts a manual pass on a background thread; false when one is already running. */
    fun checkNowAsync(context: Context): Boolean {
        val app = context.applicationContext
        if (pass.busy) return false
        Thread({ run(app, interactive = true) }, "updater").start()
        return true
    }

    /**
     * Blocking pass, used by the worker (interactive = false) and by checkNowAsync. Returns false without doing
     * anything when another pass is running. [onStart] runs once the pass owns the lock, before any request.
     */
    fun run(context: Context, interactive: Boolean, onStart: () -> Unit = {}): Boolean = pass.tryRun { runLocked(context, interactive, onStart) }

    private fun runLocked(context: Context, interactive: Boolean, onStart: () -> Unit) {
        onStart()
        val p = prefs(context)
        p.edit().putBoolean(KEY_RUNNING, true).putLong(KEY_PROGRESS_DONE, 0).putLong(KEY_PROGRESS_TOTAL, 0).apply()
        try {
            UpdateInstaller.abandonOwnSessions(context)
            record(context, CHECKING)
            val candidate = findCandidate(context) ?: return
            if (Build.VERSION.SDK_INT < 21) { record(context, UpdateResult.INSTALL_UNSUPPORTED, candidate.manifest.versionName, checked = true); return }
            record(context, UpdateResult.DOWNLOADING, candidate.manifest.versionName, checked = true)
            val file = apkFile(context)
            file.delete()
            val (sha, size) = UpdateTransport.download(candidate.apk.downloadUrl, candidate.manifest.apkSize, file) { done, total ->
                p.edit().putLong(KEY_PROGRESS_DONE, done).putLong(KEY_PROGRESS_TOTAL, total).apply()
            }
            val failure = UpdatePolicy.verify(candidate.manifest, facts(context, file, size, sha))
            if (failure != null) { file.delete(); record(context, UpdateResult.fromFailure(failure), candidate.manifest.versionName); return }
            p.edit().putString(KEY_PENDING_MANIFEST, manifestJson(candidate.manifest)).apply()
            record(context, UpdateResult.VERIFIED, candidate.manifest.versionName)
            UpdateInstaller.installVerified(context, file, candidate.manifest, interactive)
        } catch (e: UpdateException) {
            apkFile(context).delete()
            record(context, e.kind, e.arg, checked = true)
        } catch (e: Exception) {
            Log.e(TAG, "update pass failed: $e")
            apkFile(context).delete()
            record(context, UpdateResult.STORAGE, checked = true)
        } finally {
            p.edit().putBoolean(KEY_RUNNING, false).apply()
        }
    }

    const val CHECKING = "checking"

    /** Reads the release list and up to MAX_MANIFESTS_PER_CHECK manifests; records UP_TO_DATE or NO_MANIFEST itself. */
    private fun findCandidate(context: Context): UpdatePolicy.Candidate? {
        val installed = installedVersionCode(context)
        val list = UpdateTransport.getText(UpdatePolicy.RELEASES_URL, UpdatePolicy.MAX_LIST_BYTES)
        val releases = try { UpdatePolicy.parseReleases(list) } catch (e: Exception) { throw UpdateException(UpdateResult.BAD_LIST) }
        val newerTags = UpdatePolicy.orderedReleases(releases).filter { UpdatePolicy.isNewer(UpdatePolicy.UNIVERSAL_BASE + UpdatePolicy.tagBuildNumber(it.tag)!!, installed) }
        for (release in newerTags.take(UpdatePolicy.MAX_MANIFESTS_PER_CHECK)) {
            val asset = UpdatePolicy.manifestAsset(release) ?: continue
            val text = UpdateTransport.getText(asset.downloadUrl, UpdatePolicy.MAX_MANIFEST_BYTES)
            val candidate = UpdatePolicy.candidate(release, UpdatePolicy.parseManifest(text)) ?: continue
            if (UpdatePolicy.isNewer(candidate.manifest.versionCode.toLong(), installed)) {
                record(context, UpdateResult.AVAILABLE, candidate.manifest.versionName, checked = true)
                return candidate
            }
        }
        record(context, if (newerTags.isEmpty()) UpdateResult.UP_TO_DATE else UpdateResult.NO_MANIFEST, checked = true)
        return null
    }

    @Suppress("DEPRECATION")
    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    /** Signing certificates (SHA-256 hex) of a package info; empty when unreadable. */
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    fun certificates(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val sha = MessageDigest.getInstance("SHA-256")
        fun hex(list: Array<android.content.pm.Signature>?) = list?.map { sig -> sha.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
        // Compare the CURRENT signer(s) only. The key-rotation lineage (signing certificate history) is deliberately not
        // used: an APK whose lineage merely includes our certificate is not signed by it.
        val current = if (Build.VERSION.SDK_INT >= 28) hex(info.signingInfo?.apkContentsSigners) else null
        return UpdatePolicy.currentSigners(current, hex(info.signatures))
    }

    @Suppress("DEPRECATION")
    private fun flags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    /** The running app's certificate, read from PackageManager (never from the manifest, never hardcoded). */
    fun installedCertificates(context: Context): Set<String> = certificates(context.packageManager.getPackageInfo(context.packageName, flags()))

    @Suppress("DEPRECATION")
    fun archiveInfo(context: Context, file: File): PackageInfo? {
        val pm = context.packageManager
        var info = pm.getPackageArchiveInfo(file.absolutePath, flags())
        // Some releases return no signingInfo for archives; fall back to the legacy signature list.
        if (info != null && certificates(info).isEmpty()) info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES) ?: info
        return info
    }

    @Suppress("DEPRECATION")
    fun facts(context: Context, file: File, size: Long, sha256: String): UpdatePolicy.Facts {
        val info = archiveInfo(context, file)
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
        return UpdatePolicy.Facts(
            fileSize = size, fileSha256 = sha256, apkPackage = info?.packageName, apkVersionCode = code,
            apkCertSha256 = certificates(info), installedVersionCode = installedVersionCode(context),
            installedCertSha256 = installedCertificates(context),
        )
    }

    fun sha256(file: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(64 * 1024); while (true) { val n = input.read(b); if (n < 0) break; d.update(b, 0, n) } }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    fun manifestJson(m: UpdatePolicy.Manifest): String = com.google.gson.JsonObject().apply {
        addProperty("format", UpdatePolicy.MANIFEST_FORMAT); addProperty("packageName", m.packageName)
        addProperty("buildNumber", m.buildNumber); addProperty("versionCode", m.versionCode); addProperty("versionName", m.versionName)
        addProperty("apk", m.apkName); addProperty("apkSize", m.apkSize); addProperty("apkSha256", m.apkSha256); addProperty("certSha256", m.certSha256)
    }.toString()

    /** Re-verifies the kept download (e.g. after the user granted the install permission) and installs it. */
    fun resumeInstallAsync(context: Context): Boolean {
        val app = context.applicationContext
        if (pass.busy) return false
        Thread({ pass.tryRun {
            try {
                val m = UpdatePolicy.parseManifest(prefs(app).getString(KEY_PENDING_MANIFEST, "") ?: "")
                val file = apkFile(app)
                if (m == null || !file.exists()) { record(app, UpdateResult.STORAGE); return@tryRun }
                val failure = UpdatePolicy.verify(m, facts(app, file, file.length(), sha256(file)))
                if (failure != null) { file.delete(); record(app, UpdateResult.fromFailure(failure), m.versionName); return@tryRun }
                UpdateInstaller.installVerified(app, file, m, interactive = true)
            } catch (e: Exception) {
                record(app, UpdateResult.INSTALL_FAILED, e.message ?: "")
            }
        } }, "updater-install").start()
        return true
    }
}
