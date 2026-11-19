package cn.ppps.forwarder.activity

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseActivity
import cn.ppps.forwarder.databinding.ActivityMainBinding
import cn.ppps.forwarder.fragment.AboutFragment
import cn.ppps.forwarder.fragment.AppListFragment
import cn.ppps.forwarder.fragment.ClientFragment
import cn.ppps.forwarder.fragment.ExtrasFragment
import cn.ppps.forwarder.fragment.HelpFragment
import cn.ppps.forwarder.fragment.LogsFragment
import cn.ppps.forwarder.fragment.RulesFragment
import cn.ppps.forwarder.fragment.SendersFragment
import cn.ppps.forwarder.fragment.ServerFragment
import cn.ppps.forwarder.fragment.SettingsFragment
import cn.ppps.forwarder.fragment.TasksFragment
import cn.ppps.forwarder.service.ForegroundService
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.EVENT_LOAD_APP_LIST
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import cn.ppps.forwarder.workers.LoadAppListWorker
import com.jeremyliao.liveeventbus.LiveEventBus
import com.xuexiang.xui.XUI.getContext
import com.xuexiang.xui.utils.WidgetUtils

@Suppress("PrivatePropertyName", "unused", "DEPRECATION")
class MainActivity : BaseActivity<ActivityMainBinding?>() {

    /**
     * Every main-screen destination. The five tab routes are the only primary navigation (bottom bar /
     * rail), in declaration order; the tools are reached from the Extras tab, Help/About from Settings.
     * Tab ids are the menu item ids and are stored in saved state: existing ids keep their meaning and
     * Extras takes the new id 5, so a restored "navigation.tab" value is never reinterpreted.
     */
    enum class Route(val tab: Int = 0) {
        LOGS(1), RULES(2), TARGETS(3), EXTRAS(5), SETTINGS(4),
        TASKS, SERVER, CLIENT, TUNNEL, APP_LIST, HELP, ABOUT;
        val isTab: Boolean get() = tab != 0
        val key: String get() = name.lowercase(java.util.Locale.ROOT)
    }

    private val TAG: String = MainActivity::class.java.simpleName
    private var needToAppListFragment = false
    private var selectedTab = Route.LOGS.tab

    private lateinit var mTabLayout: com.google.android.material.navigation.NavigationBarView

    override fun viewBindingInflate(inflater: LayoutInflater?): ActivityMainBinding {
        return ActivityMainBinding.inflate(inflater!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedTab = savedInstanceState?.getInt("navigation.tab", Route.LOGS.tab) ?: Route.LOGS.tab
        initViews()
        openAboutForUpdate(intent)

        //Hide from recent tasks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && SettingUtils.enableExcludeFromRecents) {
            val am = App.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.let {
                val tasks = it.appTasks
                if (!tasks.isNullOrEmpty()) {
                    tasks[0].setExcludeFromRecents(true)
                }
            }
        }

        //Check for access to notification
        XXPermissions.with(this)
            .permission(PermissionLists.getNotificationServicePermission())
            .permission(PermissionLists.getPostNotificationsPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                    val allGranted = deniedList.isEmpty()
                    if (!allGranted) {
                        XToastUtils.error(R.string.tips_notification)
                        return
                    }
                    //Start foreground service
                    if (!ForegroundService.isRunning) {
                        val serviceIntent = Intent(getTopActivity(), ForegroundService::class.java)
                        serviceIntent.action = ACTION_START
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    }
                }
            })

        //Listener installedAppInformation list loaded event
        LiveEventBus.get(EVENT_LOAD_APP_LIST, String::class.java).observe(this) {
            if (needToAppListFragment) {
                openNewPage(AppListFragment::class.java)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        openAboutForUpdate(intent)
    }

    /** The updater's notification opens the About screen, where the update state and actions are shown. */
    private fun openAboutForUpdate(intent: Intent?) {
        if (intent?.getBooleanExtra(cn.ppps.forwarder.utils.update.UpdateInstaller.EXTRA_OPEN_ABOUT, false) != true) return
        intent.removeExtra(cn.ppps.forwarder.utils.update.UpdateInstaller.EXTRA_OPEN_ABOUT)
        openRoute(Route.ABOUT)
    }

    override val isSupportSlideBack: Boolean
        get() = false

    private fun initViews() {
        WidgetUtils.clearActivityBackground(this)
        initTab()
    }

    private fun initTab() {
        mTabLayout = binding!!.tabs as com.google.android.material.navigation.NavigationBarView
        addPrimaryTabs(mTabLayout)
        val tabs = Route.values().filter { it.isTab }
        mTabLayout.setOnItemSelectedListener { item ->
            selectedTab = item.itemId
            needToAppListFragment = false
            when (tabs.firstOrNull { it.tab == item.itemId }) {
                Route.LOGS -> switchPage(LogsFragment::class.java)
                Route.RULES -> switchPage(RulesFragment::class.java)
                Route.TARGETS -> switchPage(SendersFragment::class.java)
                Route.EXTRAS -> switchPage(ExtrasFragment::class.java)
                Route.SETTINGS -> switchPage(SettingsFragment::class.java)
                else -> return@setOnItemSelectedListener false
            }
            true
        }
        mTabLayout.selectedItemId = selectedTab
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("navigation.tab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    /** Back at a root tab keeps the app (and its services) alive instead of finishing the shell. */
    override fun leaveNavigationRoot() { moveTaskToBack(true) }

    /**
     * Single entry point for main-screen destinations, with the same guards the former side menu used:
     * tabs switch the retained root fragment; App list keeps its permission/loading flow; the tunnel only
     * explains why it is unavailable (no download or native loading).
     */
    fun openRoute(route: Route) {
        needToAppListFragment = false
        when (route) {
            Route.LOGS, Route.RULES, Route.TARGETS, Route.EXTRAS, Route.SETTINGS -> mTabLayout.selectedItemId = route.tab
            Route.TASKS -> openNewPage(TasksFragment::class.java)
            Route.SERVER -> openNewPage(ServerFragment::class.java)
            Route.CLIENT -> openNewPage(ClientFragment::class.java)
            Route.TUNNEL -> com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_frpc)
                .setMessage(R.string.privacy_frpc_disabled)
                .setPositiveButton(R.string.m3_close, null)
                .show()
            Route.APP_LIST -> openAppList()
            Route.HELP -> openNewPage(HelpFragment::class.java) // offline; online docs only via its button
            Route.ABOUT -> openNewPage(AboutFragment::class.java)
        }
    }

    private fun openAppList() {
        //Check to see if application list permissions are obtained
        XXPermissions.with(this)
            .permission(PermissionLists.getGetInstalledAppsPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                    val allGranted = deniedList.isEmpty()
                    if (!allGranted) {
                        // Check whether the user selected Never ask again for denied permissions
                        val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(getTopActivity(), deniedList)
                        if (doNotAskAgain) {
                            XXPermissions.startPermissionActivity(getContext(), deniedList)
                        }
                        // Logic of failure in processing requests for permission
                        XToastUtils.error(R.string.tips_get_installed_apps)
                        return
                    }
                    // Logic of successful processing of requests for authority
                    if (App.UserAppList.isEmpty() && App.SystemAppList.isEmpty()) {
                        XToastUtils.info(getString(R.string.loading_app_list))
                        val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
                        WorkManager.getInstance(getContext()).enqueue(request)
                        needToAppListFragment = true
                        return
                    }
                    openNewPage(AppListFragment::class.java)
                }
            })
    }

    companion object {
        /** The five primary destinations in bar order; the same menu feeds the phone bottom bar and the wide-screen rail. */
        fun addPrimaryTabs(bar: com.google.android.material.navigation.NavigationBarView) {
            val titles = intArrayOf(R.string.menu_logs, R.string.menu_rules, R.string.menu_senders, R.string.settings_extras, R.string.menu_settings)
            val icons = intArrayOf(R.drawable.selector_icon_tabbar_logs, R.drawable.selector_icon_tabbar_rules, R.drawable.selector_icon_tabbar_senders, R.drawable.selector_icon_tabbar_extras, R.drawable.selector_icon_tabbar_settings)
            val tabs = Route.values().filter { it.isTab }
            check(tabs.size == titles.size && tabs.size == icons.size)
            tabs.forEachIndexed { index, route -> bar.menu.add(0, route.tab, index, titles[index]).setIcon(icons[index]) }
            bar.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
            // Five items on a narrow phone leave 64-72dp each, so long labels (Bestemmingen, Instellingen) cannot stay
            // on one line at the user's font scale. Labels keep their sp size and wrap (hyphenated) onto a taller bar;
            // when one would need more than two lines only the selected label is shown. Never shrinks the text.
            cn.ppps.forwarder.widget.material.NavigationLabels.install(bar)
        }
    }

}
