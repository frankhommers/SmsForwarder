package cn.ppps.forwarder.core.webview

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.webkit.WebView
import android.webkit.JsResult
import android.webkit.JsPromptResult
import com.just.agentweb.core.web.AgentWebUIControllerImplBase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import java.lang.ref.WeakReference
import cn.ppps.forwarder.R

/** Preserve AgentWeb callbacks, render every application-owned prompt with Material 3. */
class UIController(activity: Activity) : AgentWebUIControllerImplBase() {
    private val owner = WeakReference(activity)
    private var loading: androidx.appcompat.app.AlertDialog? = null
    private fun message(callback: Handler.Callback, what: Int) { callback.handleMessage(Message.obtain().apply { this.what = what }) }
    override fun onShowMessage(message: String, from: String) { cn.ppps.forwarder.utils.XToastUtils.info(message) }
    override fun onJsAlert(view: WebView, url: String, message: String) {
        owner.get()?.let { MaterialAlertDialogBuilder(it).setMessage(message).setPositiveButton(R.string.confirm, null).show() }
    }
    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult) {
        owner.get()?.let {
            MaterialAlertDialogBuilder(it).setMessage(message).setPositiveButton(R.string.confirm) { _, _ -> result.confirm() }
                .setNegativeButton(R.string.cancel) { _, _ -> result.cancel() }.setOnCancelListener { result.cancel() }.show()
        } ?: result.cancel()
    }
    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult) {
        owner.get()?.let { context ->
            val field = TextInputEditText(context).apply { setText(defaultValue) }
            val input = TextInputLayout(context).apply { boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; hint = message; addView(field, android.widget.LinearLayout.LayoutParams(-1, -2)) }
            val content = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, 0)
                addView(input, android.widget.LinearLayout.LayoutParams(-1, -2))
            }
            MaterialAlertDialogBuilder(context).setView(content).setPositiveButton(R.string.confirm) { _, _ -> result.confirm(field.text.toString()) }
                .setNegativeButton(R.string.cancel) { _, _ -> result.cancel() }.setOnCancelListener { result.cancel() }.show()
        } ?: result.cancel()
    }
    override fun onSelectItemsPrompt(view: WebView, url: String, items: Array<String>, callback: Handler.Callback) {
        owner.get()?.let { MaterialAlertDialogBuilder(it).setItems(items) { _, index -> message(callback, index) }.setOnCancelListener { message(callback, -1) }.show() }
    }
    override fun onOpenPagePrompt(view: WebView, url: String, callback: Handler.Callback) {
        owner.get()?.let {
            MaterialAlertDialogBuilder(it).setMessage(R.string.lab_open_third_app).setPositiveButton(R.string.lab_yes) { _, _ -> message(callback, 1) }
                .setNegativeButton(R.string.lab_no) { _, _ -> message(callback, -1) }.setOnCancelListener { message(callback, -1) }.show()
        }
    }
    override fun onForceDownloadAlert(url: String, callback: Handler.Callback) {
        owner.get()?.let {
            MaterialAlertDialogBuilder(it).setMessage(com.just.agentweb.R.string.agentweb_honeycomblow)
                .setPositiveButton(com.just.agentweb.R.string.agentweb_download) { _, _ -> message(callback, 0) }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }
    override fun onLoading(message: String) {
        onCancelLoading()
        owner.get()?.let {
            loading = MaterialAlertDialogBuilder(it).setMessage(message)
                .setView(com.google.android.material.progressindicator.CircularProgressIndicator(it).apply { isIndeterminate = true }).show()
        }
    }
    override fun onCancelLoading() { loading?.dismiss(); loading = null }
    override fun onPermissionsDeny(permissions: Array<String>, permissionType: String, action: String) {
        owner.get()?.let { MaterialAlertDialogBuilder(it).setMessage(R.string.toast_denied).setPositiveButton(R.string.confirm, null).show() }
    }
}
