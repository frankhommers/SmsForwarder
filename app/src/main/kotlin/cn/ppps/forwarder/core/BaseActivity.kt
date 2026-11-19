package cn.ppps.forwarder.core

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.viewbinding.ViewBinding
import com.hjq.language.MultiLanguages
import cn.ppps.forwarder.utils.EVENT_TOAST_ERROR
import cn.ppps.forwarder.utils.EVENT_TOAST_INFO
import cn.ppps.forwarder.utils.EVENT_TOAST_SUCCESS
import cn.ppps.forwarder.utils.EVENT_TOAST_WARNING
import cn.ppps.forwarder.utils.XToastUtils
import com.jeremyliao.liveeventbus.LiveEventBus
import com.xuexiang.xpage.base.XPageActivity
import com.xuexiang.xpage.base.XPageFragment
import com.xuexiang.xpage.core.CoreSwitchBean
import com.xuexiang.xrouter.facade.service.SerializationService
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xutil.resource.ResUtils.isRtl

/**
 * Base containerActivity
 *
 * @author XUE
 * @since 2019/3/22 11:21
 */
@Suppress("MemberVisibilityCanBePrivate", "UNCHECKED_CAST", "DEPRECATION", "EmptyMethod")
open class BaseActivity<Binding : ViewBinding?> : XPageActivity(), me.jessyan.autosize.internal.CancelAdapt {
    /**
     * AccessBinding
     *
     * @return Binding
     */
    /**
     * ViewBinding
     */
    var binding: Binding? = null
        protected set

    override fun attachBaseContext(newBase: Context) {
        //Injecting Fonts
        //super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase))
        // Bind Language
        //super.attachBaseContext(ViewPumpContextWrapper.wrap(MultiLanguages.attach(newBase)))
        super.attachBaseContext(MultiLanguages.attach(newBase))
    }

    override fun getCustomRootView(): View? {
        binding = viewBindingInflate(layoutInflater)
        return if (binding != null) binding!!.root else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initStatusBarStyle()
        // XPage 3.3 replays its launch intent after FragmentManager restores the stack.
        // Suppress only that launch extra during restoration, retaining the full saved state.
        val launch = intent.getParcelableExtra<CoreSwitchBean>(CoreSwitchBean.KEY_SWITCH_BEAN)
        if (savedInstanceState != null) intent.removeExtra(CoreSwitchBean.KEY_SWITCH_BEAN)
        try {
            super.onCreate(savedInstanceState)
        } finally {
            if (launch != null) intent.putExtra(CoreSwitchBean.KEY_SWITCH_BEAN, launch)
        }
        registerSlideBack()

        //Toast for all kinds of events
        LiveEventBus.get(EVENT_TOAST_ERROR, String::class.java).observe(this) { msg: String ->
            XToastUtils.error(msg, 15000)
        }
        LiveEventBus.get(EVENT_TOAST_SUCCESS, String::class.java).observe(this) { msg: String ->
            XToastUtils.success(msg)
        }
        LiveEventBus.get(EVENT_TOAST_INFO, String::class.java).observe(this) { msg: String ->
            XToastUtils.info(msg)
        }
        LiveEventBus.get(EVENT_TOAST_WARNING, String::class.java).observe(this) { msg: String ->
            XToastUtils.warning(msg, 10000)
        }
    }

    /** One policy for physical Back and toolbar Up; never pop a lone root into a blank page. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { navigateBack() }

    open fun navigateBack() {
        val visible = supportFragmentManager.fragments.lastOrNull { it.isVisible }
        if ((visible as? BaseFragment<*>)?.consumeNavigationBack() == true) return
        if (supportFragmentManager.backStackEntryCount > 1) {
            supportFragmentManager.popBackStackImmediate()
        } else {
            leaveNavigationRoot()
        }
    }

    protected open fun leaveNavigationRoot() { finish() }

    /**
     * BuildViewBinding
     *
     * @param inflater  inflater
     * @return ViewBinding
     */
    protected open fun viewBindingInflate(inflater: LayoutInflater?): Binding? {
        return null
    }

    /**
     * Style for Initializing the Status Bar
     */
    protected open fun initStatusBarStyle() {}

    /**
     * Openfragment
     *
     * @param clazz          Page Class
     * @param addToBackStack Add to back stack
     * @return OpenedfragmentObject
     */
    fun <T : XPageFragment?> openPage(clazz: Class<T>?, addToBackStack: Boolean): T {
        val page = CoreSwitchBean(clazz)
            .setAddToBackStack(addToBackStack)
        return openPage(page) as T
    }

    /**
     * Openfragment
     *
     * @return OpenedfragmentObject
     */
    fun <T : XPageFragment?> openNewPage(clazz: Class<T>?): T {
        val page = CoreSwitchBean(clazz)
            .setNewActivity(true)
        return openPage(page) as T
    }

    /**
     * Switchfragment
     *
     * @param clazz Page Class
     * @return OpenedfragmentObject
     */
    fun <T : XPageFragment?> switchPage(clazz: Class<T>?): T {
        return changePage(clazz) as T
    }

    /**
     * Serialize object
     *
     * @param object
     * @return
     */
    fun serializeObject(`object`: Any?): String {
        return XRouter.getInstance().navigation(SerializationService::class.java)
            .object2Json(`object`)
    }

    override fun onRelease() {
        unregisterSlideBack()
        super.onRelease()
    }

    /**
     * Register swipe-back callback
     */
    protected fun registerSlideBack() { /* System back owns edge gestures. */ }

    /**
     * Unregister swipe-back callback
     */
    protected fun unregisterSlideBack() {}

    /**
     * @return Whether to support a slide back
     */
    protected open val isSupportSlideBack: Boolean
        get() {
            val page: CoreSwitchBean? = intent.getParcelableExtra(CoreSwitchBean.KEY_SWITCH_BEAN)
            return page == null || page.bundle == null || page.bundle.getBoolean(
                KEY_SUPPORT_SLIDE_BACK,
                true
            )
        }

    companion object {
        /**
         * Whether to support a slide back
         */
        const val KEY_SUPPORT_SLIDE_BACK = "key_support_slide_back"
    }
}