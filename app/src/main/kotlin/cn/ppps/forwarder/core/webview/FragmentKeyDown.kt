package cn.ppps.forwarder.core.webview

import android.view.KeyEvent

/**
 *
 *
 * @author xuexiang
 * @since 2019/1/4 PM11:32
 */
interface FragmentKeyDown {
    /**
     * fragmentButton listening.
     * @param keyCode
     * @param event
     * @return
     */
    fun onFragmentKeyDown(keyCode: Int, event: KeyEvent?): Boolean
}