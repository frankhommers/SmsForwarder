package cn.ppps.forwarder.core.webview

import android.app.Activity
import android.os.Handler
import cn.ppps.forwarder.utils.Log
import android.webkit.WebView
import com.just.agentweb.core.web.AgentWebUIControllerImplBase
import java.lang.ref.WeakReference

/**
 * If you need to change a particular one,AgentWeb internal dialog; see the example below
 * You have to cross-reference it. DefaultUIController The way it's written becauseUICustomization is flexible, but the callback contract is fixed and must be honored
 *
 * @author xuexiang
 * @since 2019-10-30 23:18
 */
@Suppress("unused")
class UIController(activity: Activity) : AgentWebUIControllerImplBase() {
    private val mActivity: WeakReference<Activity> = WeakReference(activity)
    override fun onShowMessage(message: String, from: String) {
        super.onShowMessage(message, from)
        Log.i(TAG, "message:$message")
    }

    override fun onSelectItemsPrompt(
        view: WebView,
        url: String,
        items: Array<String>,
        callback: Handler.Callback,
    ) {
        // Use defaultUI
        super.onSelectItemsPrompt(view, url, items, callback)
    }

}