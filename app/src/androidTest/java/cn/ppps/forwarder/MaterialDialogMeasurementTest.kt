package cn.ppps.forwarder

import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Laid-out measurement and behaviour of the shared [MaterialDialog] wrapper on a device: tall custom dialogs keep
 * every action reachable, while plain message, dynamic setContent-before-show (privacy), choice-list, title,
 * callback, autoDismiss(false) and cancelability flows keep working. Local only: every dialog is built from local
 * views/strings inside the test; nothing is tested against rules, sent or loaded from the network.
 */
class MaterialDialogMeasurementTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private val open = mutableListOf<AlertDialog>()

    @Test fun cancelableRuleTesterCanCloseWithPhysicalBack() {
        val dialog = main {
            MaterialDialog.Builder(activity).title(R.string.rule_tester)
                .customView(activity.layoutInflater.inflate(R.layout.dialog_rule_test, null), true)
                .cancelable(true).autoDismiss(false).neutralText(R.string.action_back).build()
        }
        showAndSettle(dialog)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil("Back dismisses cancelable rule tester") { !dialog.isShowing }
    }

    @Before fun start() {
        activity = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        ins.waitForIdleSync()
    }

    @After fun finish() {
        ins.runOnMainSync { open.forEach { if (it.isShowing) it.dismiss() }; activity.finish() }
    }

    private fun waitUntil(label: String, condition: () -> Boolean) {
        repeat(100) { var ok = false; ins.runOnMainSync { ok = condition() }; if (ok) return; Thread.sleep(100) }
        fail("Timed out: $label")
    }

    private fun <T> main(block: () -> T): T { var value: T? = null; ins.runOnMainSync { value = block() }; @Suppress("UNCHECKED_CAST") return value as T }

    private fun showAndSettle(dialog: MaterialDialog): MaterialDialog {
        main { dialog.show(); open.add(dialog.alert) }
        waitUntil("dialog laid out") { dialog.isShowing && dialog.alert.window!!.decorView.let { it.isLaidOut && it.height > 0 } }
        // Measure the dialog itself, not an IME that a focused test field may have raised.
        main {
            val decor = dialog.alert.window!!.decorView
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(decor.windowToken, 0)
        }
        ins.waitForIdleSync()
        return dialog
    }

    private fun dp(value: Int) = value * activity.resources.displayMetrics.density

    private fun panel(dialog: MaterialDialog, id: Int): View? = dialog.alert.findViewById(id)

    /** The middle panels AppCompat's AlertDialogLayout measures; more than one visible makes it skip action reservation. */
    private fun visibleMiddlePanels(dialog: MaterialDialog) =
        listOf(androidx.appcompat.R.id.contentPanel, androidx.appcompat.R.id.customPanel).filter { panel(dialog, it)?.visibility == View.VISIBLE }

    /** Each given action is shown, >= 48dp tall, enabled and fully inside both the dialog window and the visible screen. */
    private fun assertActionsReachable(dialog: MaterialDialog, vararg buttons: Int) = main {
        val decor = dialog.alert.window!!.decorView
        val frame = android.graphics.Rect().also { decor.getWindowVisibleDisplayFrame(it) }
        val decorTop = IntArray(2).also { decor.getLocationOnScreen(it) }[1]
        val actionsTop = IntArray(2).also { panel(dialog, androidx.appcompat.R.id.buttonPanel)!!.getLocationOnScreen(it) }[1]
        val custom = panel(dialog, androidx.appcompat.R.id.customPanel)
        if (custom?.visibility == View.VISIBLE) {
            val customBottom = IntArray(2).also { custom.getLocationOnScreen(it) }[1] + custom.height
            assertTrue("custom body ($customBottom) must not overlap actions ($actionsTop)", customBottom <= actionsTop)
        }
        for (which in buttons) {
            val button = dialog.alert.getButton(which)
            assertNotNull("button $which", button)
            assertTrue("shown $which", button.isShown)
            assertTrue("button $which height ${button.height}px < 48dp", button.height >= dp(48) - 1)
            val top = IntArray(2).also { button.getLocationOnScreen(it) }[1]
            assertTrue("button $which inside window", top >= decorTop && top + button.height <= decorTop + decor.height)
            assertTrue("button $which on screen", top + button.height <= frame.bottom)
            assertTrue("button $which enabled", button.isEnabled && button.isClickable)
        }
    }

    /** Rule-tester style body (the real dialog_rule_test layout) followed by a block taller than the screen. */
    private fun tallBody(): View = main {
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(View.inflate(activity, R.layout.dialog_rule_test, null))
            addView(View(activity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.resources.displayMetrics.heightPixels * 2))
            addView(TextView(activity).apply { text = "end-of-body" })
        }
    }

    @Test fun tallCustomViewKeepsAllThreeActionsReachableAndScrolls() {
        var neutral = 0; var positive = 0; var negative = 0
        val body = tallBody()
        val dialog = showAndSettle(main {
            MaterialDialog.Builder(activity).iconRes(android.R.drawable.ic_dialog_email).title(R.string.rule_tester)
                .customView(body, true).cancelable(false).autoDismiss(false)
                .neutralText(R.string.action_back).onNeutral { d, which -> assertEquals(DialogAction.NEUTRAL, which); neutral++; d.dismiss() }
                .negativeText(R.string.cancel).onNegative { _, which -> assertEquals(DialogAction.NEGATIVE, which); negative++ }
                .positiveText(R.string.action_test).onPositive { _, which -> assertEquals(DialogAction.POSITIVE, which); positive++ }
                .build()
        })
        assertEquals(listOf(androidx.appcompat.R.id.customPanel), main { visibleMiddlePanels(dialog) })
        assertTrue("no phantom blank message", main { dialog.contentView?.isShown != true })
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
        // The body scrolls inside the remaining space instead of pushing the actions away.
        main {
            val scroll = generateSequence(body.parent) { it.parent }.filterIsInstance<NestedScrollView>().first()
            assertTrue("body must be scrollable", scroll.canScrollVertically(1))
            scroll.fullScroll(View.FOCUS_DOWN)
        }
        ins.waitForIdleSync()
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
        // autoDismiss(false): Test and Cancel only run callbacks; cancelable(false): Back keeps it open.
        main { dialog.alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); dialog.alert.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
        assertEquals(1, positive); assertEquals(1, negative)
        assertTrue(main { dialog.isShowing })
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        ins.waitForIdleSync()
        assertTrue("cancelable(false) must ignore Back", main { dialog.isShowing })
        main { dialog.alert.getButton(AlertDialog.BUTTON_NEUTRAL).performClick() }
        waitUntil("neutral callback dismissed") { !dialog.isShowing }
        assertEquals(1, neutral)
    }

    @Test fun messagePlusTallCustomViewShareOneBodyAndKeepActions() {
        val body = tallBody()
        val dialog = showAndSettle(main {
            MaterialDialog.Builder(activity).title(R.string.rule_tester).content("Local fixture message")
                .customView(body, true).positiveText(R.string.confirm).negativeText(R.string.cancel).build()
        })
        assertEquals(listOf(androidx.appcompat.R.id.customPanel), main { visibleMiddlePanels(dialog) })
        main {
            val message = dialog.contentView!!
            assertEquals("Local fixture message", message.text.toString())
            assertTrue(message.isShown)
            assertSame("message sits in the custom body, above the view", message.parent, body.parent)
            assertTrue((message.parent as ViewGroup).indexOfChild(message) < (body.parent as ViewGroup).indexOfChild(body))
        }
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)
        main { dialog.alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
        waitUntil("default autoDismiss closes") { !dialog.isShowing }
    }

    @Test fun customViewWithoutScrollWrapperAndDynamicMessageBeforeShow() {
        val body = main { View.inflate(activity, R.layout.dialog_rule_test, null) }
        val dialog = main {
            MaterialDialog.Builder(activity).title(R.string.rule_tester).customView(body, false).positiveText(R.string.confirm).build()
                .also { it.setContent("Set before show") }
        }
        showAndSettle(dialog)
        assertEquals(listOf(androidx.appcompat.R.id.customPanel), main { visibleMiddlePanels(dialog) })
        assertEquals("Set before show", main { dialog.contentView!!.text.toString() })
        assertTrue(main { dialog.contentView!!.isShown })
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE)
    }

    @Test fun plainMessageDialogKeepsMessageCallbacksAndCancelability() {
        var yes = 0; var no = 0
        val dialog = showAndSettle(main {
            MaterialDialog.Builder(activity).title(R.string.delete_rule_title).content(R.string.delete_rule_tips)
                .positiveText(R.string.lab_yes).negativeText(R.string.lab_no)
                .onPositive { _, _ -> yes++ }.onNegative { _, _ -> no++ }.build()
        })
        main {
            assertEquals(activity.getText(R.string.delete_rule_tips).toString(), dialog.contentView!!.text.toString())
            assertTrue(dialog.contentView!!.isShown)
            assertEquals(activity.getText(R.string.delete_rule_title).toString(), dialog.alert.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
            assertEquals(listOf(androidx.appcompat.R.id.contentPanel), visibleMiddlePanels(dialog))
        }
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)
        main { dialog.alert.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
        waitUntil("negative dismissed") { !dialog.isShowing }
        assertEquals(0, yes); assertEquals(1, no)
        // Default cancelable: Back dismisses without running a callback.
        val second = showAndSettle(main { MaterialDialog.Builder(activity).content("Local fixture").positiveText(R.string.lab_yes).onPositive { _, _ -> yes++ }.build() })
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil("Back cancels a cancelable dialog") { !second.isShowing }
        assertEquals(0, yes)
    }

    @Test fun privacyStyleDynamicContentBeforeShowKeepsLinksAndStaysOpen() {
        var agreed = 0
        val dialog = main {
            MaterialDialog.Builder(activity).title(R.string.title_reminder).autoDismiss(false).cancelable(false)
                .positiveText(R.string.lab_agree).onPositive { _, _ -> agreed++ }.negativeText(R.string.lab_disagree).build().also {
                    it.setContent(SpannableStringBuilder("Local privacy policy text"))
                    it.contentView!!.movementMethod = LinkMovementMethod.getInstance()
                }
        }
        showAndSettle(dialog)
        main {
            val message = dialog.contentView!!
            assertEquals("Local privacy policy text", message.text.toString())
            assertTrue(message.isShown)
            assertTrue(message.movementMethod is LinkMovementMethod)
            assertEquals(android.R.id.message, message.id)
            assertEquals(listOf(androidx.appcompat.R.id.contentPanel), visibleMiddlePanels(dialog))
        }
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)
        main { dialog.alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
        assertEquals(1, agreed)
        assertTrue("autoDismiss(false) keeps the privacy dialog open", main { dialog.isShowing })
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        ins.waitForIdleSync()
        assertTrue("cancelable(false) ignores Back", main { dialog.isShowing })
    }

    @Test fun choiceListKeepsSelectionTitleMessageAndVeto() {
        val picked = mutableListOf<Pair<Int, String>>()
        var accept = false
        val dialog = showAndSettle(main {
            MaterialDialog.Builder(activity).title("Local choices").content("Root: /local")
                .items(listOf<CharSequence>("alpha", "beta", "gamma"))
                .itemsCallbackSingleChoice(0) { _, _, index, text -> picked.add(index to text.toString()); accept }
                .positiveText(R.string.confirm).negativeText(R.string.cancel).build()
        })
        main {
            assertEquals("Local choices\nRoot: /local", dialog.alert.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
            assertTrue("no message panel above the list", dialog.alert.findViewById<View>(android.R.id.message)?.isShown != true)
            val list: ListView = dialog.alert.listView
            assertEquals(3, list.adapter.count)
            assertEquals(0, list.checkedItemPosition)
            list.performItemClick(list.getChildAt(2), 2, list.adapter.getItemId(2))
        }
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)
        main { dialog.alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
        assertEquals(listOf(2 to "gamma"), picked)
        assertTrue("callback returning false vetoes dismissal", main { dialog.isShowing })
        accept = true
        main { dialog.alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
        waitUntil("accepted choice dismisses") { !dialog.isShowing }
        assertEquals(listOf(2 to "gamma", 2 to "gamma"), picked)
    }

    @Test fun choiceListWithoutMessageKeepsCustomTitle() {
        var chosen = -1
        val dialog = showAndSettle(main {
            MaterialDialog.Builder(activity).title(R.string.server_history).items(arrayOf<CharSequence>("one", "two"))
                .itemsCallbackSingleChoice(1) { _, _, index, _ -> chosen = index; true }.positiveText(R.string.confirm).build()
        })
        main {
            assertEquals(activity.getText(R.string.server_history).toString(), dialog.alert.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
            assertEquals(1, dialog.alert.listView.checkedItemPosition)
        }
        assertActionsReachable(dialog, AlertDialog.BUTTON_POSITIVE)
        main { dialog.alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
        waitUntil("dismissed") { !dialog.isShowing }
        assertEquals(1, chosen)
    }

    @Test fun messageOnlyDialogWithoutActionsStillShowsMessage() {
        val dialog = showAndSettle(main { MaterialDialog.Builder(activity).content("Only a message").build() })
        main {
            assertEquals("Only a message", dialog.contentView!!.text.toString())
            assertTrue(dialog.contentView!!.isShown)
            assertFalse(dialog.alert.getButton(AlertDialog.BUTTON_POSITIVE)?.isShown == true)
        }
    }
}
