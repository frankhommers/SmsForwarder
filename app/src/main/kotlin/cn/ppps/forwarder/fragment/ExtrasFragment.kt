package cn.ppps.forwarder.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import cn.ppps.forwarder.R
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentExtrasBinding
import cn.ppps.forwarder.widget.material.TitleBar
import com.xuexiang.xpage.annotation.Page

/**
 * Extras primary tab: the tools that are not a forwarding setting. Each row opens through
 * MainActivity.openRoute, so permission/loading/unavailable guards are the same as every other entry
 * point. Showing this list starts nothing; each tool opens as its own page, so Back returns here.
 */
@Page(name = "Extras")
class ExtrasFragment : BaseFragment<FragmentExtrasBinding?>() {

    internal val tools = listOf(
        SettingsNavigation.Destination("tasks", R.string.menu_tasks, R.string.extras_tasks_summary, MainActivity.Route.TASKS),
        SettingsNavigation.Destination("server", R.string.menu_server, R.string.extras_server_summary, MainActivity.Route.SERVER),
        SettingsNavigation.Destination("client", R.string.menu_client, R.string.extras_client_summary, MainActivity.Route.CLIENT),
        SettingsNavigation.Destination("tunnel", R.string.menu_frpc, R.string.extras_tunnel_summary, MainActivity.Route.TUNNEL),
        SettingsNavigation.Destination("app_list", R.string.menu_apps, R.string.extras_apps_summary, MainActivity.Route.APP_LIST)
    )

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): FragmentExtrasBinding =
        FragmentExtrasBinding.inflate(inflater, container, false)

    override fun initTitle(): TitleBar? {
        val titleBar = super.initTitle()!!.setImmersive(false)
        // Root tab: the bottom bar/rail is the only primary navigation, so no hamburger or fake Back.
        titleBar.disableLeftView()
        titleBar.setTitle(R.string.settings_extras)
        return titleBar
    }

    override fun initViews() {
        val list = binding!!.extrasList
        tools.forEach { d ->
            list.addView(navigationRow(requireContext(), "route-${d.key}", d.title, getString(d.summary)) {
                (activity as? MainActivity)?.openRoute(d.route)
            })
        }
    }

    override fun initListeners() {}
}
