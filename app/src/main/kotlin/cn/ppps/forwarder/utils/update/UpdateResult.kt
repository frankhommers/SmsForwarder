package cn.ppps.forwarder.utils.update

import android.content.Context
import cn.ppps.forwarder.R

/** Result kinds of a check, download, verification or install, persisted and rendered on the About screen. */
object UpdateResult {
    const val UP_TO_DATE = "up_to_date"
    const val AVAILABLE = "available"
    const val DOWNLOADING = "downloading"
    const val VERIFIED = "verified"
    const val WAITING_CONFIRMATION = "waiting_confirmation"
    const val INSTALLING = "installing"
    const val INSTALLED = "installed"

    const val NETWORK = "network"
    const val RATE_LIMITED = "rate_limited"
    const val HTTP = "http"
    const val BAD_LIST = "bad_list"
    const val NO_MANIFEST = "no_manifest"
    const val REDIRECT_REFUSED = "redirect_refused"
    const val TOO_LARGE = "too_large"
    const val STORAGE = "storage"
    const val SIZE_MISMATCH = "size_mismatch"
    const val SHA256_MISMATCH = "sha256_mismatch"
    const val NOT_AN_APK = "not_an_apk"
    const val WRONG_PACKAGE = "wrong_package"
    const val NOT_NEWER = "not_newer"
    const val MANIFEST_VERSION_MISMATCH = "manifest_version_mismatch"
    const val CERT_MISMATCH = "cert_mismatch"
    const val CERT_UNREADABLE = "cert_unreadable"
    const val INSTALL_FAILED = "install_failed"
    const val INSTALL_UNSUPPORTED = "install_unsupported"
    const val INSTALL_PERMISSION = "install_permission"

    fun fromFailure(f: UpdatePolicy.Failure): String = when (f) {
        UpdatePolicy.Failure.SIZE_MISMATCH -> SIZE_MISMATCH
        UpdatePolicy.Failure.SHA256_MISMATCH -> SHA256_MISMATCH
        UpdatePolicy.Failure.NOT_AN_APK -> NOT_AN_APK
        UpdatePolicy.Failure.WRONG_PACKAGE -> WRONG_PACKAGE
        UpdatePolicy.Failure.NOT_NEWER -> NOT_NEWER
        UpdatePolicy.Failure.MANIFEST_VERSION_MISMATCH -> MANIFEST_VERSION_MISMATCH
        UpdatePolicy.Failure.CERT_MISMATCH -> CERT_MISMATCH
        UpdatePolicy.Failure.CERT_UNREADABLE -> CERT_UNREADABLE
    }

    fun isError(kind: String) = kind !in setOf(UP_TO_DATE, AVAILABLE, DOWNLOADING, VERIFIED, WAITING_CONFIRMATION, INSTALLING, INSTALLED, "")

    /** User-facing text for a result; [arg] is a version name, HTTP status or host name depending on the kind. */
    fun describe(context: Context, kind: String, arg: String): String = when (kind) {
        UP_TO_DATE -> context.getString(R.string.updater_up_to_date)
        AVAILABLE -> context.getString(R.string.updater_available, arg)
        DOWNLOADING -> context.getString(R.string.updater_downloading_version, arg)
        VERIFIED -> context.getString(R.string.updater_verified, arg)
        WAITING_CONFIRMATION -> context.getString(R.string.updater_waiting_confirmation, arg)
        INSTALLING -> context.getString(R.string.updater_installing, arg)
        INSTALLED -> context.getString(R.string.updater_installed, arg)
        NETWORK -> context.getString(R.string.updater_error_network)
        RATE_LIMITED -> context.getString(R.string.updater_error_rate_limited)
        HTTP -> context.getString(R.string.updater_error_http, arg)
        BAD_LIST -> context.getString(R.string.updater_error_bad_list)
        NO_MANIFEST -> context.getString(R.string.updater_error_no_manifest)
        REDIRECT_REFUSED -> context.getString(R.string.updater_error_redirect, arg)
        TOO_LARGE -> context.getString(R.string.updater_error_too_large)
        STORAGE -> context.getString(R.string.updater_error_storage)
        SIZE_MISMATCH -> context.getString(R.string.updater_error_size)
        SHA256_MISMATCH -> context.getString(R.string.updater_error_sha256)
        NOT_AN_APK -> context.getString(R.string.updater_error_not_apk)
        WRONG_PACKAGE -> context.getString(R.string.updater_error_package)
        NOT_NEWER -> context.getString(R.string.updater_error_not_newer)
        MANIFEST_VERSION_MISMATCH -> context.getString(R.string.updater_error_manifest_version)
        CERT_MISMATCH -> context.getString(R.string.updater_error_certificate)
        CERT_UNREADABLE -> context.getString(R.string.updater_error_certificate_unreadable)
        INSTALL_FAILED -> context.getString(R.string.updater_error_install, arg)
        INSTALL_UNSUPPORTED -> context.getString(R.string.updater_error_install_unsupported)
        INSTALL_PERMISSION -> context.getString(R.string.updater_error_install_permission)
        else -> context.getString(R.string.updater_never_checked)
    }
}
