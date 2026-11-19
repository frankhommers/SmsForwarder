package cn.ppps.forwarder.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source gate for the navigation shell: the five bottom/rail tabs (Logs, Rules, Targets, Extras, Settings) are the
 * only primary navigation, no side menu exists, root toolbars carry no hamburger/fake Back, the tools are reached
 * from the Extras tab and Help/About from Settings, all through MainActivity.openRoute.
 */
class NavigationShellTest {
    private val res = File("src/main/res")
    private val kt = File("src/main/kotlin/cn/ppps/forwarder")
    private val main get() = File(kt, "activity/MainActivity.kt").readText()
    private val nav get() = File(kt, "fragment/SettingsNavigation.kt").readText()
    private val extras get() = File(kt, "fragment/ExtrasFragment.kt").readText()

    /** (name, tab id or null) in declaration order. */
    private fun routeIds(): List<Pair<String, Int?>> {
        val body = main.substringAfter("enum class Route").substringAfter("{").substringBefore(";")
        return Regex("""([A-Z_]+)(\((\d+)\))?""").findAll(body).map { it.groupValues[1] to it.groupValues[3].toIntOrNull() }.toList()
    }
    private fun routes(): List<Pair<String, Boolean>> = routeIds().map { it.first to (it.second != null) }

    @Test fun shellHasNoDrawer() {
        for (dir in listOf("layout", "layout-w600dp")) {
            val text = File(res, "$dir/activity_main.xml").readText()
            assertFalse(dir, Regex("DrawerLayout|material\\.navigation\\.NavigationView|navigation_drawer|SlidingRootNav").containsMatchIn(text))
        }
        assertTrue(File(res, "layout/activity_main.xml").readText().contains("BottomNavigationView"))
        assertTrue(File(res, "layout-w600dp/activity_main.xml").readText().contains("NavigationRailView"))
        val sources = kt.walkTopDown().filter { it.extension == "kt" }.toList()
        for (f in sources) {
            val text = f.readText()
            assertFalse(f.path, Regex("openMenu\\(|closeMenu\\(|isMenuOpen\\(|DrawerAdapter|slidingrootnav|GravityCompat.START").containsMatchIn(text))
        }
        assertFalse(File(kt, "adapter/menu").exists())
        assertFalse(Regex("name=\"menu_(titles|icons)\"").containsMatchIn(File(res, "values/arrays.xml").readText()))
    }

    @Test fun exactlyFivePrimaryTabsInApprovedOrder() {
        val all = routes()
        assertEquals(listOf("LOGS", "RULES", "TARGETS", "EXTRAS", "SETTINGS"), all.filter { it.second }.map { it.first })
        assertTrue("Destinations beyond the tabs exist", all.count { !it.second } >= 5)
        assertTrue(main.contains("Route.values().filter { it.isTab }"))
        // Bar order comes from the enum; the labels/icons arrays follow the same order.
        val tabs = main.substringAfter("fun addPrimaryTabs")
        val titles = tabs.substringAfter("val titles = intArrayOf(").substringBefore(")")
        assertEquals("R.string.menu_logs, R.string.menu_rules, R.string.menu_senders, R.string.settings_extras, R.string.menu_settings", titles)
        assertTrue(tabs.substringAfter("val icons = intArrayOf(").substringBefore(")").contains("R.drawable.selector_icon_tabbar_extras"))
        assertTrue(main.contains("Route.EXTRAS -> switchPage(ExtrasFragment::class.java)"))
        // Bottom bar and rail are both built by the same function on the same R.id.tabs view.
        assertTrue(main.contains("addPrimaryTabs(mTabLayout)"))
        for (dir in listOf("layout", "layout-w600dp")) assertTrue(File(res, "$dir/activity_main.xml").readText().contains("android:id=\"@+id/tabs\""))
    }

    @Test fun storedTabIdsKeepTheirMeaning() {
        // "navigation.tab" saves the menu item id: the four existing ids are unchanged and Extras takes a new one.
        val ids = routeIds().filter { it.second != null }.toMap()
        assertEquals(mapOf("LOGS" to 1, "RULES" to 2, "TARGETS" to 3, "SETTINGS" to 4, "EXTRAS" to 5), ids)
        assertTrue(main.contains("savedInstanceState?.getInt(\"navigation.tab\", Route.LOGS.tab)"))
        assertTrue(main.contains("outState.putInt(\"navigation.tab\", selectedTab)"))
        assertTrue(main.contains("mTabLayout.selectedItemId = selectedTab"))
        // Startup modes and the updater intent are untouched.
        val splash = File(kt, "activity/SplashActivity.kt").readText()
        assertTrue(splash.contains("SettingUtils.enablePureTaskMode") && splash.contains("TaskActivity::class.java"))
        assertTrue(splash.contains("SettingUtils.enablePureClientMode") && splash.contains("ClientActivity::class.java"))
    }

    @Test fun rootToolbarsHaveNoHamburgerOrFakeBack() {
        for (name in listOf("Logs", "Rules", "Senders", "Extras", "Settings")) {
            val src = File(kt, "fragment/${name}Fragment.kt").readText()
            val title = src.substringAfter("override fun initTitle()").substringBefore("return titleBar")
            assertTrue(name, title.contains("disableLeftView()"))
            assertFalse(name, title.contains("setLeftImageResource") || title.contains("setLeftClickListener"))
        }
        val overview = nav.substringAfter("fun overview()")
        assertTrue(overview.substringBefore("refresh()").contains("disableLeftView()"))
        assertFalse(overview.contains("ic_settings_menu") || overview.contains("ic_action_menu"))
    }

    @Test fun everyNonTabRouteHasExactlyOneHome() {
        val tools = setOf("TASKS", "SERVER", "CLIENT", "TUNNEL", "APP_LIST")
        val helpInfo = setOf("HELP", "ABOUT")
        assertEquals(tools + helpInfo, routes().filter { !it.second }.map { it.first }.toSet())
        for (route in tools) {
            assertTrue("$route on the Extras tab", extras.contains("MainActivity.Route.$route)"))
            assertFalse("$route not duplicated in Settings", nav.contains("MainActivity.Route.$route)"))
        }
        for (route in helpInfo) {
            assertTrue("$route stays in Settings", nav.contains("MainActivity.Route.$route)"))
            assertFalse(extras.contains("MainActivity.Route.$route)"))
        }
        // No duplicate Extras overview row or Extras detail panel remains in Settings.
        assertFalse(Regex("row\\(EXTRAS|detail-\\\$EXTRAS|settings_extras_summary|const val EXTRAS").containsMatchIn(nav))
        assertFalse(File(res, "values/navigation_extras.xml").readText().contains("settings_extras_summary"))
        // Rows go through the activity handler, never directly to the fragment classes.
        assertTrue(nav.contains("(fragment.activity as? MainActivity)?.openRoute(route)"))
        assertTrue(extras.contains("(activity as? MainActivity)?.openRoute(d.route)"))
        for (src in listOf(nav, extras)) assertFalse(Regex("openNewPage\\(|PageOption.to\\((Tasks|Server|Client|AppList|Help|About)Fragment").containsMatchIn(src))
        // A saved "extras" detail key from the former Settings panel is ignored rather than crashing.
        assertTrue(File(kt, "fragment/SettingsFragment.kt").readText().contains("settingsNavigation!!.entries.any { it.key == key }"))
    }

    @Test fun routeHandlerKeepsOriginalGuards() {
        val handler = main.substringAfter("fun openRoute(route: Route)")
        val tunnel = handler.substringAfter("Route.TUNNEL ->").substringBefore("Route.APP_LIST ->")
        assertTrue(tunnel.contains("MaterialAlertDialogBuilder(this)"))
        assertTrue(tunnel.contains(".setMessage(R.string.privacy_frpc_disabled)"))
        assertTrue(tunnel.contains(".setPositiveButton(R.string.m3_close, null)"))
        assertFalse(tunnel.contains("XToastUtils") || tunnel.contains("openNewPage") || tunnel.contains("startService"))
        assertFalse(main.contains("FrpcFragment"))
        val apps = main.substringAfter("private fun openAppList()")
        assertTrue(apps.contains("getGetInstalledAppsPermission()"))
        assertTrue(apps.contains("LoadAppListWorker") && apps.contains("needToAppListFragment = true"))
        assertTrue(main.contains("EVENT_LOAD_APP_LIST") && main.contains("if (needToAppListFragment)"))
        // Updater notification still lands on About through the same handler.
        assertTrue(main.substringAfter("private fun openAboutForUpdate").substringBefore("override").contains("openRoute(Route.ABOUT)"))
        assertTrue(main.contains("override fun leaveNavigationRoot() { moveTaskToBack(true) }"))
        // Opening Settings/Extras must not start a server, request or worker on its own.
        for (src in listOf(nav, extras)) assertFalse(Regex("startService|XHttp|OkHttp|WorkManager|enqueue|checkNow").containsMatchIn(src))
    }

    @Test fun newCopyExistsInDefaultEnglishAndDutch() {
        fun keys(dir: String) = Regex("name=\"([a-z_]+)\"").findAll(File(res, "$dir/navigation_extras.xml").readText()).map { it.groupValues[1] }.toSet()
        val base = keys("values")
        assertTrue(base.containsAll(listOf("settings_extras", "settings_extras_intro", "settings_help_info", "settings_about_updates")))
        assertEquals(base, keys("values-en")); assertEquals(base, keys("values-nl"))
        // Tab labels: approved EN/NL wording, existing NL spelling of Extra's kept.
        fun label(dir: String, file: String, key: String) = Regex("name=\"$key\">([^<]*)<").find(File(res, "$dir/$file").readText())!!.groupValues[1]
        assertEquals(listOf("Logs", "Rules", "Targets", "Extras", "Settings"), listOf(label("values-en", "strings.xml", "menu_logs"), label("values-en", "strings.xml", "menu_rules"), label("values-en", "strings.xml", "menu_senders"), label("values-en", "navigation_extras.xml", "settings_extras"), label("values-en", "strings.xml", "menu_settings")))
        assertEquals(listOf("Logboek", "Regels", "Bestemmingen", "Extra\\'s", "Instellingen"), listOf(label("values-nl", "strings.xml", "menu_logs"), label("values-nl", "strings.xml", "menu_rules"), label("values-nl", "strings.xml", "menu_senders"), label("values-nl", "navigation_extras.xml", "settings_extras"), label("values-nl", "strings.xml", "menu_settings")))
        for (k in Regex("R\\.string\\.((?:extras|settings)_[a-z_]+)").findAll(nav + extras + main).map { it.groupValues[1] }.toSet()) {
            assertTrue("$k defined", Regex("name=\"$k\"").containsMatchIn(res.resolve("values").listFiles()!!.joinToString("") { it.readText() }))
        }
    }

    @Test fun helpDescribesTheExtrasTab() {
        val en = File("src/main/assets/help/help.md").readText()
        val nl = File("src/main/assets/help/help_nl.md").readText()
        assertTrue(en.contains("The **Extras** tab") && !en.contains("top of Settings"))
        assertTrue(nl.contains("Het tabblad **Extra's**") && !nl.contains("bovenaan Instellingen"))
    }

    /** Labels keep a scalable sp size and wrap onto a taller bar; nothing shrinks them to fit (rejected approach). */
    @Test fun navigationLabelsScaleWithFontAndNeverShrink() {
        assertFalse("shrink-to-fit helper removed", File(kt, "widget/material/NavigationLabelFit.kt").exists())
        val source = File(kt, "widget/material/NavigationLabels.kt").readText()
        assertTrue(source.contains("label.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)"))
        assertFalse("no pixel/dp label sizing", Regex("COMPLEX_UNIT_PX|setTextSize\\(TypedValue.COMPLEX_UNIT_DIP|FLOOR|fittedSize|letterSpacing = 0").containsMatchIn(source))
        assertTrue(source.contains("label.maxLines = Int.MAX_VALUE") && source.contains("label.ellipsize = null"))
        assertTrue(source.contains("HYPHENATION_FREQUENCY_NORMAL"))
        assertTrue("forced mid-word breaks count as not fitting", source.contains("if (breaksInsideWord(label.text, it, label.paint)) MAX_LINES + 1"))
        assertFalse("accessible names stay Material's full titles", Regex("contentDescription\\s*=|setContentDescription|setTooltipText").containsMatchIn(source))
        assertTrue(main.contains("NavigationLabels.install(bar)"))
        assertFalse(main.contains("NavigationLabelFit"))

        val n = cn.ppps.forwarder.widget.material.NavigationLabels
        val labeled = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
        val selected = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED
        assertEquals(2, n.MAX_LINES)
        assertEquals(labeled, n.visibilityMode(listOf(1, 1, 2, 1, 2)))
        assertEquals("a third line hides the unselected labels instead of shrinking", selected, n.visibilityMode(listOf(1, 1, 3, 1, 2)))
        assertEquals("never below Material's height", 80, n.requiredHeight(80, 12, 32, 4, 16, 16))
        assertEquals("grows for two lines", 12 + 32 + 4 + 40 + 16, n.requiredHeight(80, 12, 32, 4, 40, 16))
    }
}
