package cn.ppps.forwarder.fragment

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import cn.ppps.forwarder.R
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.databinding.FragmentSettingsBinding
import cn.ppps.forwarder.utils.SettingUtils as S
import cn.ppps.forwarder.widget.material.TitleBar
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView

/** List/detail navigation over the original view-owned controls and autosave listeners.
 * No copies of preferences, no second persistence path, and no misleading Cancel button.
 */
internal class SettingsNavigation(private val fragment: SettingsFragment, private val b: FragmentSettingsBinding, private val bar: TitleBar) {
    data class Entry(val key: String, val title: Int, val section: Int, val summary: () -> String)
    private val context get() = fragment.requireContext()
    private fun str(id: Int) = context.getString(id)
    private fun state(on: Boolean) = str(if (on) R.string.settings_on else R.string.settings_off)
    private fun seconds(n: Number) = "$n ${str(R.string.seconds)}"
    private fun count(raw: String) = raw.lines().count { it.isNotBlank() }
    private fun value(raw: String) = raw.ifBlank { str(R.string.settings_not_set) }
    private fun calls(): String {
        val flags = listOf(S.enableCallType1,S.enableCallType2,S.enableCallType3,S.enableCallType4,S.enableCallType5,S.enableCallType6)
        val labels = listOf(R.string.incoming_call_ended,R.string.outgoing_call_ended,R.string.missed_call,R.string.incoming_call_received,R.string.incoming_call_answered,R.string.outgoing_call_started)
        val selected = labels.filterIndexed { i, _ -> flags[i] }.map(::str)
        val events = if(selected.isEmpty()) str(R.string.settings_no_events) else selected.joinToString(", ")
        return state(S.enablePhone) + " · " + context.getString(R.string.settings_saved_events, events)
    }
    /** A main-screen destination opened through MainActivity.openRoute, keeping that handler's guards. */
    data class Destination(val key: String, val title: Int, val summary: Int, val route: MainActivity.Route)
    /** Help and About stay in Settings; the tools (Tasks, Server, Client, Tunnel, App list) are the Extras tab. */
    val helpInfo = listOf(
        Destination("guide",R.string.menu_help,R.string.settings_guide_summary,MainActivity.Route.HELP),
        Destination("about",R.string.settings_about_updates,R.string.settings_about_summary,MainActivity.Route.ABOUT)
    )
    val entries = listOf(
        Entry("sms",R.string.settings_sms,R.string.settings_forwarding) { state(S.enableSms) },
        Entry("calls",R.string.settings_calls,R.string.settings_forwarding) { calls() },
        Entry("notifications",R.string.settings_notifications,R.string.settings_forwarding) { state(S.enableAppNotify) },
        Entry("duplicates",R.string.settings_duplicates,R.string.settings_filtering) { if(S.duplicateMessagesLimits <= 0) state(false) else context.getString(R.string.settings_duplicate_interval, S.duplicateMessagesLimits) },
        Entry("quiet",R.string.settings_quiet,R.string.settings_filtering) { if(S.silentPeriodStart == S.silentPeriodEnd) state(false) else b.tvSilentPeriod.text.toString() },
        Entry("filters",R.string.settings_filters,R.string.settings_filtering) { context.getString(R.string.settings_item_count,count(S.appNotifyBlacklist)) },
        Entry("bluetooth",R.string.settings_bluetooth,R.string.settings_automation) { state(S.enableBluetooth) + " · " + seconds(S.bluetoothScanInterval / 1000) },
        Entry("location",R.string.settings_location,R.string.settings_automation) { state(S.enableLocation) + " · " + seconds(S.locationMinInterval / 1000) + " · ${S.locationMinDistance} ${str(R.string.meter)}" },
        Entry("commands",R.string.sms_command,R.string.settings_automation) { state(S.enableSmsCommand) + " · " + context.getString(R.string.settings_item_count,S.smsCommandSafePhone.split(',', ';').count { it.isNotBlank() }) },
        Entry("apps",R.string.settings_apps,R.string.settings_automation) { state(S.enableLoadAppList) + " · " + context.getString(R.string.settings_item_count,count(S.cancelExtraAppNotify)) },
        Entry("boot",R.string.auto_startup,R.string.settings_background) { state(b.sbWithReboot.isChecked) },
        Entry("battery",R.string.battery_setting,R.string.settings_background) { state(b.sbBatterySetting.isChecked) },
        Entry("accessibility",R.string.settings_accessibility,R.string.settings_background) { str(R.string.settings_system_settings) },
        Entry("keepalive",R.string.settings_keepalive,R.string.settings_background) { state(S.enableCactus) },
        Entry("recents",R.string.settings_recents,R.string.settings_background) { state(S.enableExcludeFromRecents) },
        Entry("proximity",R.string.settings_proximity,R.string.settings_background) { if(b.layoutEnableCloseToEarpieceTurnOffScreen.visibility == View.GONE) str(R.string.settings_unavailable) else state(S.enableCloseToEarpieceTurnOffScreen) },
        Entry("delivery",R.string.settings_retry,R.string.settings_delivery) { context.getString(R.string.settings_retry_summary,S.requestRetryTimes,S.requestDelayTime,S.requestTimeout) },
        Entry("device",R.string.device_name,R.string.settings_identity) { value(S.extraDeviceMark) },
        Entry("sim",R.string.settings_sim,R.string.settings_identity) { value(S.extraSim1) + " · " + value(S.extraSim2) },
        Entry("notification_text",R.string.notify_content_label,R.string.settings_identity) { value(S.notifyContent) },
        Entry("template",R.string.custom_template,R.string.settings_identity) { state(S.enableSmsTemplate) },
        Entry("language",R.string.multi_languages,R.string.settings_app) { if(com.hjq.language.MultiLanguages.isSystemLanguage(context)) str(R.string.follow_system) else if(com.hjq.language.MultiLanguages.getAppLanguage(context).language == "nl") "Nederlands" else "English" },
        Entry("startup",R.string.settings_startup,R.string.settings_app) { listOfNotNull(if(S.enablePureClientMode) str(R.string.pure_client_mode) else null,if(S.enablePureTaskMode) str(R.string.pure_task_mode) else null).joinToString(" · ").ifBlank { str(R.string.settings_main_screen) } },
        Entry("diagnostics",R.string.settings_diagnostics,R.string.settings_app) { state(S.enableDebugMode) }
    )
    private val summaries = mutableMapOf<String,TextView>()
    var current: String? = null
        private set
    private val back = object : OnBackPressedCallback(false) { override fun handleOnBackPressed() { if (!consumeBack()) { isEnabled=false; (fragment.requireActivity() as cn.ppps.forwarder.core.BaseActivity<*>).navigateBack() } } }
    private fun resourcesWidth() = context.resources.configuration.screenWidthDp
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    init {
        b.layoutSpApp.visibility = View.GONE
        // Keep original platform gate: one-pixel activity is supported through Android 9 only.
        b.layoutOnePixelActivity.visibility = if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) View.VISIBLE else View.GONE
        fragment.requireActivity().onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner,back)
        var section = 0
        entries.forEach { entry ->
            if(entry.section != section) {
                section = entry.section
                b.settingsOverview.addView(heading(section))
            }
            b.settingsOverview.addView(row(entry.key,entry.title,entry.summary()) { open(entry.key) })
        }
        b.settingsOverview.addView(row("backup",R.string.settings_backup,str(R.string.settings_backup_help)) { fragment.openBackup() })
        b.settingsOverview.addView(heading(R.string.settings_help_info))
        b.settingsOverview.addView(row("help",R.string.settings_setup,str(R.string.settings_setup_help)) { cn.ppps.forwarder.widget.GuideTipsDialog.showTipsForce(context) })
        helpInfo.forEach { d -> b.settingsOverview.addView(row("route-${d.key}",d.title,str(d.summary)) { openRoute(d.route) }) }
        b.detailAccessibility.addView(com.google.android.material.button.MaterialButton(context).apply {
            setText(R.string.settings_system_settings)
            setOnClickListener { fragment.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        // Autosave copy belongs in wrapping content, never a truncated toolbar subtitle.
        for(i in 0 until b.settingsDetails.childCount) {
            val panel = b.settingsDetails.getChildAt(i) as LinearLayout
            panel.addView(MaterialTextView(context).apply {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurfaceVariant))
                setText(R.string.settings_autosave);setPadding(0,0,0,dp(16))
            },0)
        }
        fun labelSliders(view: View) {
            if(view is cn.ppps.forwarder.widget.material.TargetSlider) {
                val group=view.parent as LinearLayout
                val title=group.getChildAt(group.indexOfChild(view)-1) as TextView
                val unit=group.getChildAt(group.indexOfChild(view)+1) as? TextView
                val base=title.text.toString();val suffix=unit?.text.toString()
                fun update() { title.text="$base · ${view.value.toInt()} $suffix" }
                update();view.addOnChangeListener { _, _, _ -> update() }
                unit?.visibility=View.GONE
            } else if(view is android.view.ViewGroup) for(i in 0 until view.childCount) labelSliders(view.getChildAt(i))
        }
        labelSliders(b.settingsDetails)
        // Full-width token actions on compact screens: long Dutch labels must not be cut at two lines.
        b.glSmsTemplate.columnCount = if(resourcesWidth() >= 600) 2 else 1
        for(i in 0 until b.glSmsTemplate.childCount) {
            (b.glSmsTemplate.getChildAt(i) as? TextView)?.maxLines = Int.MAX_VALUE
        }
        overview()
    }
    private fun heading(text: Int) = MaterialTextView(context).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        setTextColor(MaterialColors.getColor(this,com.google.android.material.R.attr.colorPrimary))
        setPadding(0,dp(if (b.settingsOverview.childCount == 0) 8 else 24),0,dp(8));setText(text)
        androidx.core.view.ViewCompat.setAccessibilityHeading(this,true)
    }
    /** Same handler (and permission/loading/unavailable guards) as every other main-screen entry point. */
    private fun openRoute(route: MainActivity.Route) { (fragment.activity as? MainActivity)?.openRoute(route) }
    private fun row(key: String, title: Int, summary: String, click: () -> Unit): View =
        navigationRow(context, key, title, summary, click).also { summaries[key] = it.findViewWithTag(ROW_SUMMARY) }
    fun updateBackOwnership() { back.isEnabled = current != null && !fragment.isHidden && fragment.isResumed }
    fun consumeBack(): Boolean {
        if (!fragment.isVisible || current == null) return false
        overview(); return true
    }
    fun refresh() { entries.forEach { summaries[it.key]?.text=it.summary() }; updateBackOwnership() }
    fun open(key: String) {
        val title=entries.first { it.key == key }.title
        current=key;updateBackOwnership()
        b.settingsOverviewScroll.visibility=View.GONE;b.settingsDetailScroll.visibility=View.VISIBLE
        for(i in 0 until b.settingsDetails.childCount) b.settingsDetails.getChildAt(i).visibility=View.GONE
        b.root.findViewById<View>(context.resources.getIdentifier("detail_$key","id",context.packageName)).visibility=View.VISIBLE
        bar.setTitle(title).setSubTitle(null).setLeftImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material).setLeftClickListener { overview() }
        b.settingsDetailScroll.scrollTo(0,0)
    }
    fun overview() {
        current=null;back.isEnabled=false
        b.settingsDetailScroll.visibility=View.GONE;b.settingsOverviewScroll.visibility=View.VISIBLE
        // Root tab: no hamburger and no fake Back; the bottom bar is the only primary navigation.
        bar.setTitle(R.string.menu_settings).setSubTitle(null).disableLeftView()
        refresh()
    }
    companion object { const val ROW_SUMMARY = "settings-row-summary" }
}

/** One overview row (title, current-value summary, chevron); shared by the Settings overview and the Extras tab. */
internal fun navigationRow(context: android.content.Context, key: String, title: Int, summary: String, click: () -> Unit): View {
    fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL
        minimumHeight=dp(72);setPadding(0,dp(12),0,dp(12));tag="settings-row-$key"
        val background = android.util.TypedValue();context.theme.resolveAttribute(android.R.attr.selectableItemBackground,background,true);setBackgroundResource(background.resourceId)
        isFocusable=true;setOnClickListener { click() }
    }
    val texts=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    texts.addView(MaterialTextView(context).apply { setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);setText(title) })
    texts.addView(MaterialTextView(context).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        setTextColor(MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurfaceVariant));text=summary;setPadding(0,dp(4),0,0)
        tag=SettingsNavigation.ROW_SUMMARY
    })
    row.addView(texts,LinearLayout.LayoutParams(0,-2,1f))
    row.addView(MaterialTextView(context).apply { text="›";textSize=24f;setPadding(dp(16),0,0,0);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(-2,-2))
    return row
}
