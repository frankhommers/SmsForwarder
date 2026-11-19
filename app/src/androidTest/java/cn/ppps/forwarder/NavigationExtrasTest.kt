package cn.ppps.forwarder

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.AboutFragment
import cn.ppps.forwarder.fragment.ExtrasFragment
import cn.ppps.forwarder.fragment.HelpFragment
import cn.ppps.forwarder.fragment.LogsFragment
import cn.ppps.forwarder.fragment.RulesFragment
import cn.ppps.forwarder.fragment.SendersFragment
import cn.ppps.forwarder.fragment.SettingsFragment
import cn.ppps.forwarder.fragment.TasksFragment
import cn.ppps.forwarder.fragment.navigationRow
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.widget.material.NavigationLabels
import com.google.android.material.navigation.NavigationBarView
import org.junit.Assert.*
import org.junit.Test

/**
 * Five primary tabs (Logs, Rules, Targets, Extras, Settings) on the bottom bar and the wide-screen rail; the tools
 * live on the Extras tab, Help/About in Settings. Local only: never opens Server or Client (no sockets), never
 * sends, never checks for updates. Captures go to files/navigation5/<language>/ on the device.
 */
class NavigationExtrasTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val language get() = InstrumentationRegistry.getArguments().getString("language", "en")
    private fun settle() { ins.waitForIdleSync(); Thread.sleep(500) }
    private fun walk(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList()
    private fun dir(lang: String = language) = java.io.File(ins.targetContext.getExternalFilesDir(null), "navigation5/$lang").apply { mkdirs() }
    private fun shot(name: String) {
        settle()
        java.io.FileOutputStream(java.io.File(dir(), "$name.png")).use { ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun save(view: View, lang: String, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        java.io.FileOutputStream(java.io.File(dir(lang), "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun prepare() {
        com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext, java.util.Locale(language))
        SettingUtils.enableSms = false; SettingUtils.enablePhone = false; SettingUtils.enableAppNotify = false
        SettingUtils.enableLocation = false; SettingUtils.enableBluetooth = false; SettingUtils.enableCactus = false
        SettingUtils.enableLoadAppList = false; SettingUtils.enableSmsCommand = false
        SettingUtils.enablePureClientMode = false; SettingUtils.enablePureTaskMode = false
    }
    private fun resumed(): Activity? { var a: Activity? = null; ins.runOnMainSync { a = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull() }; return a }
    private fun <T : androidx.fragment.app.Fragment> awaitVisible(klass: Class<T>): Pair<Activity, T> {
        repeat(40) {
            val a = resumed()
            val f = (a as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager?.fragments?.filterIsInstance(klass)?.firstOrNull { it.isVisible }
            if (a != null && f != null) return a to f
            Thread.sleep(100)
        }
        error("${klass.simpleName} not visible")
    }
    private fun toolbarNavigation(root: View) = walk(root).filterIsInstance<com.google.android.material.appbar.MaterialToolbar>().first().navigationIcon
    private fun bar(main: MainActivity) = main.findViewById<NavigationBarView>(R.id.tabs)
    private fun visibleRoot(main: MainActivity) = main.supportFragmentManager.fragments.filter { it.isVisible && rootFragments.values.any { k -> k.isInstance(it) } }.single()

    private val rootFragments = mapOf(
        MainActivity.Route.LOGS to LogsFragment::class.java, MainActivity.Route.RULES to RulesFragment::class.java,
        MainActivity.Route.TARGETS to SendersFragment::class.java, MainActivity.Route.EXTRAS to ExtrasFragment::class.java,
        MainActivity.Route.SETTINGS to SettingsFragment::class.java
    )

    private fun visibleIn(view: View, ancestor: View): Boolean {
        var v: View? = view
        while (v != null && v !== ancestor) {
            if (v.visibility != View.VISIBLE || v.alpha <= 0f) return false
            v = v.parent as? View
        }
        return v === ancestor && ancestor.visibility == View.VISIBLE
    }
    private fun rectIn(ancestor: ViewGroup, view: View) = Rect(0, 0, view.width, view.height).also { ancestor.offsetDescendantRectToMyCoords(view, it) }
    /** Pixels inside [r] that differ clearly from [background]: the label's text is actually drawn there. */
    private fun ink(bitmap: Bitmap, r: Rect, background: Int): Int {
        var n = 0
        for (x in maxOf(0, r.left) until minOf(bitmap.width, r.right)) for (y in maxOf(0, r.top) until minOf(bitmap.height, r.bottom)) {
            val c = bitmap.getPixel(x, y)
            val diff = Math.abs(Color.alpha(c) - Color.alpha(background)) + Math.abs(Color.red(c) - Color.red(background)) +
                Math.abs(Color.green(c) - Color.green(background)) + Math.abs(Color.blue(c) - Color.blue(background))
            if (diff > 96) n++
        }
        return n
    }
    private fun modeName(mode: Int) = if (mode == NavigationBarView.LABEL_VISIBILITY_LABELED) "labeled" else if (mode == NavigationBarView.LABEL_VISIBILITY_SELECTED) "selected" else "mode$mode"

    /** Lines [label]'s complete text needs at [width] with its own paint, hyphenation and break strategy; -1 for a forced mid-word break. */
    private fun linesAt(label: TextView, width: Int): Int {
        val text = label.text
        val paint = android.text.TextPaint(label.paint)
        val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(1, width))
            .setHyphenationFrequency(label.hyphenationFrequency).setBreakStrategy(label.breakStrategy).setIncludePad(label.includeFontPadding)
            .build()
        return if (NavigationLabels.breaksInsideWord(text, layout, paint)) -1 else layout.lineCount
    }

    private class LabelReport(val mode: Int, val lines: List<Int>, val textSizePx: Float, val barHeightDp: Float)

    /**
     * Readable primary navigation, checked on the laid-out views (and, with [bitmap], on the drawn pixels):
     * every label is Material's 12sp label size at this font scale (never shrunk), complete (no ellipsis, all
     * characters laid out), at most two lines while all labels show; otherwise only the selected label shows and
     * that is only allowed when some label needs a third line. Visible labels are drawn, fully inside their item,
     * below the icon and not clipped; items do not overlap and keep 48dp targets. Every item keeps its full title as
     * accessible name (and tooltip), whether or not its label is shown.
     */
    private fun assertReadableLabels(bar: NavigationBarView, where: String, bitmap: Bitmap? = null): LabelReport {
        val metrics = bar.resources.displayMetrics
        val density = metrics.density
        val expectedPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, metrics)
        val items = NavigationLabels.items(bar)
        assertEquals("$where: five items", 5, items.size)
        val mode = bar.labelVisibilityMode
        assertTrue("$where: labelled or selected-label mode, got ${modeName(mode)}", mode == NavigationBarView.LABEL_VISIBILITY_LABELED || mode == NavigationBarView.LABEL_VISIBILITY_SELECTED)
        val vertical = bar is com.google.android.material.navigationrail.NavigationRailView
        val ordered = items.sortedBy { if (vertical) it.top else it.left }
        for (i in 0 until ordered.size - 1) assertTrue("$where: items do not overlap", if (vertical) ordered[i].bottom <= ordered[i + 1].top else ordered[i].right <= ordered[i + 1].left)
        for (item in items) {
            val r = rectIn(bar, item)
            assertTrue("$where: item inside the bar ($r in ${bar.width}x${bar.height})", r.left >= 0 && r.top >= 0 && r.right <= bar.width && r.bottom <= bar.height)
        }
        if (!vertical) assertTrue("$where: bar keeps at least Material's 80dp (${bar.height / density}dp)", bar.height >= 80 * density - 1)
        val lines = mutableListOf<Int>()
        for (item in items) {
            val title = bar.menu.findItem(item.id).title.toString()
            assertEquals("$where: accessible name is the full title", title, item.contentDescription?.toString())
            if (Build.VERSION.SDK_INT >= 26) assertEquals("$where: tooltip is the full title", title, item.tooltipText?.toString())
            assertTrue("$where '$title' 48dp touch target", item.width >= 48 * density - 1 && item.height >= 48 * density - 1)
            val labels = NavigationLabels.labelsOf(item)
            assertEquals(2, labels.size)
            for (label in labels) {
                assertEquals("$where '$title' label text", title, label.text.toString())
                assertEquals("$where '$title' scalable 12sp label at this font scale, never shrunk", expectedPx, label.textSize, 0.6f)
                val layout = label.layout
                assertNotNull("$where '$title' laid out", layout)
                for (l in 0 until layout.lineCount) assertEquals("$where '$title' not ellipsized", 0, layout.getEllipsisCount(l))
                assertEquals("$where '$title' every character laid out", title.length, layout.getLineEnd(layout.lineCount - 1))
            }
            val shown = labels.firstOrNull { visibleIn(it, item) } ?: labels.first()
            val forced = NavigationLabels.breaksInsideWord(title, shown.layout, shown.paint)
            val needed = labels.maxOf { it.layout.lineCount }
            lines += needed
            val visible = labels.filter { visibleIn(it, item) }
            if (mode == NavigationBarView.LABEL_VISIBILITY_LABELED || item.id == bar.selectedItemId) {
                assertEquals("$where '$title' exactly one visible label", 1, visible.size)
                val label = visible.single()
                if (mode == NavigationBarView.LABEL_VISIBILITY_LABELED) assertTrue("$where '$title' at most ${NavigationLabels.MAX_LINES} lines ($needed)", label.lineCount <= NavigationLabels.MAX_LINES)
                assertFalse("$where '$title' no word broken without a hyphen", forced)
                for (l in 0 until label.layout.lineCount) assertTrue("$where '$title' line $l fits its label", label.layout.getLineWidth(l) <= label.width - label.totalPaddingLeft - label.totalPaddingRight + 1f)
                assertEquals("$where '$title' drawn unscaled", 1f, label.scaleX, 0.01f)
                assertEquals("$where '$title' drawn unscaled", 1f, label.scaleY, 0.01f)
                val r = rectIn(item, label)
                val icon = item.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_container)
                val iconRect = rectIn(item, icon)
                assertTrue("$where '$title' inside its item ($r in ${item.width}x${item.height})", r.left >= 0 && r.top >= 0 && r.right <= item.width && r.bottom <= item.height)
                assertTrue("$where '$title' below the icon, no overlap ($r vs icon $iconRect)", r.top >= iconRect.bottom)
                // On the rail every item is as tall as the tallest one: a label must still hang right below its own
                // icon, not drift down towards the next item's icon.
                if (vertical) assertTrue("$where '$title' rail label directly below its icon (${(r.top - iconRect.bottom) / density}dp)", r.top - iconRect.bottom <= 8 * density)
                assertTrue("$where '$title' not clipped vertically", label.height >= label.layout.height)
                // Material keeps a one-line label's baseline itemPaddingBottom above the item's bottom edge; a wrapped
                // label must keep that inset below its last line instead of sitting on the bar edge.
                val lastBaseline = r.top + label.totalPaddingTop + label.layout.getLineBaseline(label.lineCount - 1)
                assertTrue("$where '$title' last line keeps the ${bar.itemPaddingBottom / density}dp bottom inset (${(item.height - lastBaseline) / density}dp)",
                    item.height - lastBaseline >= bar.itemPaddingBottom - 1)
                if (bitmap != null) {
                    val inBar = rectIn(bar, label)
                    val itemInBar = rectIn(bar, item)
                    val background = bitmap.getPixel(itemInBar.left + 2, itemInBar.bottom - 2)
                    val drawn = ink(bitmap, inBar, background)
                    assertTrue("$where '$title' text actually drawn ($drawn ink pixels)", drawn >= 8 * label.lineCount)
                }
            } else {
                assertTrue("$where '$title' unselected label hidden in selected-label mode", visible.isEmpty())
            }
        }
        // Independent oracle for the mode: with the width shared equally, measure BOTH label styles of every item
        // (the inactive one and the bold active one Material shows when that tab is selected). Selected-label mode is
        // right exactly when some style of some label needs a third line or a forced mid-word break; otherwise every
        // label must show. Per item: -1 when either style breaks inside a word, else the larger line count.
        val shared = if (vertical) items.minOf { it.width } else (items.first().parent as View).width / items.size
        val sharedLines = items.map { item ->
            val counts = NavigationLabels.labelsOf(item).map { linesAt(it, shared - item.paddingLeft - item.paddingRight) }
            if (counts.any { it < 0 }) -1 else counts.max()
        }
        val needsSelected = sharedLines.any { it < 0 || it > NavigationLabels.MAX_LINES }
        if (mode == NavigationBarView.LABEL_VISIBILITY_SELECTED) {
            assertTrue("$where: selected-label mode only when a label (inactive or bold active) needs a third line or a forced mid-word break: $sharedLines", needsSelected)
        } else {
            assertTrue("$where: all labels fit two lines: $lines", lines.all { it <= NavigationLabels.MAX_LINES })
            assertFalse("$where: labelled mode although a label (inactive or bold active) needs a third line or a forced break: $sharedLines", needsSelected)
        }
        return LabelReport(mode, lines, expectedPx, bar.height / density)
    }

    @Test fun fiveTabsRootSelectionToolBackRecreationAndResume() {
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var main: MainActivity
            scenario.onActivity { main = it }; settle()
            ins.runOnMainSync {
                assertTrue(walk(main.window.decorView).none { it.javaClass.name.endsWith("DrawerLayout") || it is com.google.android.material.navigation.NavigationView })
                val menu = bar(main).menu
                assertEquals(5, menu.size())
                assertEquals("approved order", listOf(1, 2, 3, 5, 4), (0 until menu.size()).map { menu.getItem(it).itemId })
                val r = main.resources
                assertEquals(listOf(R.string.menu_logs, R.string.menu_rules, R.string.menu_senders, R.string.settings_extras, R.string.menu_settings).map { r.getString(it) },
                    (0 until menu.size()).map { menu.getItem(it).title.toString() })
                val report = assertReadableLabels(bar(main), "device font${main.resources.configuration.fontScale}")
                java.io.File(dir(), "device-labels.txt").writeText("fontScale=${main.resources.configuration.fontScale} widthDp=${main.resources.configuration.screenWidthDp} mode=${modeName(report.mode)} lines=${report.lines} barHeightDp=${report.barHeightDp}\n")
            }
            for ((route, klass) in rootFragments) {
                ins.runOnMainSync { bar(main).selectedItemId = route.tab }; settle()
                ins.runOnMainSync {
                    val visible = visibleRoot(main)
                    assertTrue("${route.key}: ${visible.javaClass.simpleName}", klass.isInstance(visible))
                    assertEquals(route.tab, bar(main).selectedItemId)
                    assertTrue(bar(main).menu.findItem(route.tab).isChecked)
                    assertNull("${route.key}: root toolbar has no hamburger or fake Back", toolbarNavigation(visible.requireView()))
                }
                shot("root-${route.key}")
            }
            // Extras is a real tab: its own fragment, its own selected item, every tool row present.
            ins.runOnMainSync { main.openRoute(MainActivity.Route.EXTRAS) }; settle()
            val extras = awaitVisible(ExtrasFragment::class.java).second
            ins.runOnMainSync {
                assertEquals(MainActivity.Route.EXTRAS.tab, bar(main).selectedItemId)
                assertEquals(setOf(MainActivity.Route.TASKS, MainActivity.Route.SERVER, MainActivity.Route.CLIENT, MainActivity.Route.TUNNEL, MainActivity.Route.APP_LIST), extras.tools.map { it.route }.toSet())
                for (d in extras.tools) assertTrue(d.key, walk(extras.requireView()).first { it.tag == "settings-row-route-${d.key}" }.isShown)
            }
            shot("extras")
            // Unavailable tunnel only explains itself; the shell stays on Extras.
            ins.runOnMainSync { walk(extras.requireView()).first { it.tag == "settings-row-route-tunnel" }.performClick() }; settle()
            assertSame(main, resumed()); assertTrue(extras.isVisible)
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.privacy_frpc_disabled))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            shot("tunnel-explanation")
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.m3_close))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.click())
            settle()
            // Tasks opens through the shared handler; physical Back returns to the Extras tab.
            ins.runOnMainSync { walk(extras.requireView()).first { it.tag == "settings-row-route-tasks" }.performClick() }; settle()
            awaitVisible(TasksFragment::class.java); shot("tasks")
            Espresso.pressBack(); settle()
            assertSame(main, resumed())
            ins.runOnMainSync { assertTrue(visibleRoot(main) is ExtrasFragment); assertEquals(5, bar(main).selectedItemId) }
            shot("extras-after-tool-back")
            // Selected tab survives recreation and a stop/resume cycle.
            scenario.recreate(); settle(); scenario.onActivity { main = it }
            ins.runOnMainSync { assertTrue(visibleRoot(main) is ExtrasFragment); assertEquals(5, bar(main).selectedItemId); assertReadableLabels(bar(main), "recreated") }
            shot("extras-recreated")
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED); settle(); scenario.onActivity { main = it }
            ins.runOnMainSync { assertTrue(visibleRoot(main) is ExtrasFragment); assertEquals(5, bar(main).selectedItemId) }
            // Existing ids keep their meaning across recreation (Settings is still id 4).
            ins.runOnMainSync { bar(main).selectedItemId = MainActivity.Route.SETTINGS.tab }; settle()
            scenario.recreate(); settle(); scenario.onActivity { main = it }
            ins.runOnMainSync { assertTrue(visibleRoot(main) is SettingsFragment); assertEquals(4, bar(main).selectedItemId) }
            // Physical Back at a root tab keeps the shell: MainActivity is moved to the background, not finished.
            ins.runOnMainSync { bar(main).selectedItemId = MainActivity.Route.EXTRAS.tab }; settle()
            Espresso.pressBackUnconditionally(); settle()
            assertFalse(main.isFinishing)
        }
    }

    @Test fun settingsHasHelpInfoButNoDuplicateExtras() {
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var main: MainActivity
            scenario.onActivity { main = it; it.openRoute(MainActivity.Route.SETTINGS) }; settle()
            val settings = main.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            val nav = settings.settingsNavigation!!
            val b = settings.binding!!
            assertEquals("24 preference routes unchanged", 24, nav.entries.size)
            ins.runOnMainSync {
                val tags = walk(settings.requireView()).mapNotNull { it.tag as? String }
                assertFalse("No Extras row in Settings", tags.contains("settings-row-extras"))
                for (tool in listOf("tasks", "server", "client", "tunnel", "app_list")) assertFalse("$tool only on the Extras tab", tags.contains("settings-row-route-$tool"))
                val extrasLabel = main.getString(R.string.settings_extras)
                assertTrue("No Extras text in Settings", walk(b.settingsOverview).filterIsInstance<TextView>().none { it.text.toString() == extrasLabel })
                assertEquals(setOf(MainActivity.Route.HELP, MainActivity.Route.ABOUT), nav.helpInfo.map { it.route }.toSet())
                assertEquals(4, bar(main).selectedItemId)
            }
            shot("settings-overview")
            for ((key, klass) in listOf("guide" to HelpFragment::class.java, "about" to AboutFragment::class.java)) {
                val row = walk(b.settingsOverview).first { it.tag == "settings-row-route-$key" }
                ins.runOnMainSync { row.requestRectangleOnScreen(android.graphics.Rect(0, 0, row.width, row.height), true); row.performClick() }; settle()
                awaitVisible(klass); shot("help-info-$key")
                Espresso.pressBack(); settle()
                assertSame(main, resumed())
                ins.runOnMainSync { assertTrue(visibleRoot(main) is SettingsFragment); assertEquals(4, bar(main).selectedItemId) }
            }
        }
    }

    private fun renderContext(lang: String, widthDp: Int, fontScale: Float): Context {
        val base = ins.targetContext
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(java.util.Locale(lang))
        configuration.screenWidthDp = widthDp
        configuration.smallestScreenWidthDp = minOf(widthDp, configuration.screenHeightDp)
        configuration.fontScale = fontScale
        // Own, unshared Resources: createConfigurationContext can hand back the process's cached ResourcesImpl, which
        // MultiLanguages.setAppLanguage (prepare() in another test) mutates in place with updateConfiguration, so a
        // render context could silently resolve the previous app language instead of [lang] (measured on API 35).
        val metrics = android.util.DisplayMetrics().apply { setTo(base.resources.displayMetrics) }
        @Suppress("DEPRECATION")
        val resources = android.content.res.Resources(base.assets, metrics, configuration)
        val context = object : android.content.ContextWrapper(base) {
            override fun getResources() = resources
            override fun getAssets() = resources.assets
        }
        assertEquals("render context resolves the requested language", lang, resources.configuration.locales[0].language)
        assertEquals("render context resolves the requested font scale", fontScale, resources.configuration.fontScale, 0.001f)
        return ContextThemeWrapper(context, R.style.AppTheme)
    }
    private fun layout(view: View, widthPx: Int, heightPx: Int?) {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            if (heightPx == null) View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED) else View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    /** Inflates the real activity_main for this configuration, adds the tabs and lays it out until the labels settle. */
    private fun renderBar(lang: String, widthDp: Int, fontScale: Float, selected: MainActivity.Route): NavigationBarView {
        val context = renderContext(lang, widthDp, fontScale)
        val density = context.resources.displayMetrics.density
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null) as ViewGroup
        val bar = root.findViewById<NavigationBarView>(R.id.tabs)
        MainActivity.addPrimaryTabs(bar)
        bar.selectedItemId = selected.tab
        val w = (widthDp * density).toInt(); val h = (640 * density).toInt()
        val labels = NavigationLabels.of(bar)!!
        var passes = 0
        do { layout(root, w, h); passes++ } while (labels.update() && passes < 5)
        layout(root, w, h)
        assertFalse("$lang/$widthDp/$fontScale: labels settle", labels.update())
        return bar
    }

    /**
     * Bottom bar (320/360/411dp) and rail (840dp) in English and Dutch at font scale 1.0, 1.3 and 2.0, every tab
     * selected in turn. Font scale is applied for real (configuration context), so label size must follow it.
     * Writes navigation5/labels-matrix.tsv and one capture per configuration with Targets selected.
     */
    @Test fun barAndRailLabelsReadableAcrossWidthsAndFontScales() {
        val matrix = StringBuilder("lang\twidthDp\tfontScale\tshell\tmode\tlines(L,R,T,E,S)\tlabelPx\tdensity\tbarHeightDp\n")
        val sizes = mutableMapOf<String, Float>()
        var checked = 0
        ins.runOnMainSync {
            for (lang in listOf("en", "nl")) for (widthDp in listOf(320, 360, 411, 840)) for (fontScale in listOf(1.0f, 1.3f, 2.0f)) {
                val wide = widthDp >= 600
                val where = "$lang/${widthDp}dp/font$fontScale"
                val reports = mutableListOf<LabelReport>()
                for (route in rootFragments.keys) {
                    val bar = renderBar(lang, widthDp, fontScale, route)
                    assertEquals("$where shell type", wide, bar is com.google.android.material.navigationrail.NavigationRailView)
                    assertEquals("$where order", listOf(1, 2, 3, 5, 4), (0 until bar.menu.size()).map { bar.menu.getItem(it).itemId })
                    assertEquals("$where titles", if (lang == "nl") listOf("Logboek", "Regels", "Bestemmingen", "Extra's", "Instellingen") else listOf("Logs", "Rules", "Targets", "Extras", "Settings"),
                        (0 until bar.menu.size()).map { bar.menu.getItem(it).title.toString() })
                    assertTrue("$where font scale applied", Math.abs(bar.resources.configuration.fontScale - fontScale) < 0.01f)
                    val bitmap = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
                    bar.draw(android.graphics.Canvas(bitmap))
                    val report = assertReadableLabels(bar, "$where/${route.key} selected", bitmap)
                    reports += report
                    // Mode, line counts and height do not depend on which tab is selected (no jumping bar).
                    assertEquals("$where mode stable across selection", reports.first().mode, report.mode)
                    if (report.mode == NavigationBarView.LABEL_VISIBILITY_LABELED) assertEquals("$where lines stable across selection", reports.first().lines, report.lines)
                    assertEquals("$where bar height stable across selection", reports.first().barHeightDp, report.barHeightDp, 0.5f)
                    if (route == MainActivity.Route.TARGETS) {
                        java.io.FileOutputStream(java.io.File(dir(lang), "render-${if (wide) "rail" else "bar"}-${widthDp}dp-font$fontScale.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    checked++
                }
                val r = reports.first()
                // Approved minimum: every label shown on phones of 360dp and up at font scale 1.3, and on the rail.
                if ((wide || widthDp >= 360) && fontScale <= 1.3f) assertEquals("$where shows all five labels", NavigationBarView.LABEL_VISIBILITY_LABELED, r.mode)
                sizes["$lang/$widthDp/$fontScale"] = r.textSizePx
                matrix.append("$lang\t$widthDp\t$fontScale\t${if (wide) "rail" else "bar"}\t${modeName(r.mode)}\t${r.lines.joinToString(",")}\t${r.textSizePx}\t${ins.targetContext.resources.displayMetrics.density}\t${r.barHeightDp}\n")
            }
        }
        // Responsive to the user's font scale: larger scale, larger labels (the rejected helper ignored it).
        for (lang in listOf("en", "nl")) for (widthDp in listOf(320, 360, 411, 840)) {
            assertTrue("$lang/$widthDp 1.3 > 1.0", sizes.getValue("$lang/$widthDp/1.3") > sizes.getValue("$lang/$widthDp/1.0") * 1.2f)
            assertTrue("$lang/$widthDp 2.0 > 1.3", sizes.getValue("$lang/$widthDp/2.0") > sizes.getValue("$lang/$widthDp/1.3"))
        }
        java.io.File(ins.targetContext.getExternalFilesDir(null), "navigation5/labels-matrix.tsv").apply { parentFile?.mkdirs() }.writeText(matrix.toString())
        assertEquals(2 * 4 * 3 * 5, checked)
    }

    /** The Extras tab content at 320dp and font scale 1.3 in English and Dutch: complete wrapped text, 48dp rows. */
    @Test fun extrasRowsRenderCompletelyAtNarrowWidthAndLargeFont() {
        var rows = 0
        ins.runOnMainSync {
            for (lang in listOf("en", "nl")) {
                val context = renderContext(lang, 320, 1.3f)
                val density = context.resources.displayMetrics.density
                val list = LayoutInflater.from(context).inflate(R.layout.fragment_extras, LinearLayout(context), false) as ViewGroup
                val content = list.findViewById<LinearLayout>(R.id.extras_list)
                val tools = ExtrasFragment().tools
                for (d in tools) content.addView(navigationRow(context, "route-${d.key}", d.title, context.getString(d.summary)) {})
                layout(content, (320 * density).toInt(), null)
                for (d in tools) {
                    val row = walk(content).first { it.tag == "settings-row-route-${d.key}" }
                    assertTrue("$lang ${d.key} touch target", row.height >= 48 * density - 1)
                    assertTrue("$lang ${d.key} inside width", row.right <= content.width)
                    for (text in walk(row).filterIsInstance<TextView>().filter { it.text.length > 1 }) {
                        val layout = text.layout
                        assertNotNull(layout)
                        assertTrue("$lang ${d.key} '${text.text}' not ellipsized", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
                        assertEquals("$lang ${d.key} '${text.text}' complete", text.text.length, layout.getLineEnd(layout.lineCount - 1))
                        assertTrue("$lang ${d.key} text fully inside the row", text.height >= layout.height)
                    }
                    rows++
                }
                assertTrue("font scale applied", context.resources.configuration.fontScale > 1.29f)
                save(content, lang, "render-extras-320dp-font1.3")
            }
        }
        assertEquals(10, rows)
    }
}
