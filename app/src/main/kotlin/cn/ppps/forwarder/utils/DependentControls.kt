package cn.ppps.forwarder.utils

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import android.widget.EditText
import cn.ppps.forwarder.R

/** View-owned dependency states. Never changes stored choices or replaces persistence listeners. */
object DependentControls {
    fun install(root: View) {
        fun enabledTree(view: View, enabled: Boolean) {
            view.isEnabled = enabled
            if (view is ViewGroup) for (i in 0 until view.childCount) enabledTree(view.getChildAt(i), enabled)
            (view.getTag(R.id.item_rows_controller) as? ItemRows)?.let { enabledTree(it.view, enabled) }
        }
        fun group(masterId: Int, vararg children: Int) {
            val master = root.findViewById<CompoundButton>(masterId) ?: return
            children.forEach { id ->
                root.findViewById<View>(id)?.let { child ->
                    enabledTree(child, master.isChecked)
                    child.alpha = if (master.isChecked) 1f else 0.45f
                    // Dynamically generated rows are siblings of the hidden backing field.
                    if (child is EditText) (child.getTag(R.id.item_rows_controller) as? ItemRows)?.view?.alpha = child.alpha
                }
            }
        }
        fun update() {
            group(R.id.sb_enable_phone, R.id.scb_call_type1, R.id.scb_call_type2, R.id.scb_call_type3, R.id.scb_call_type4, R.id.scb_call_type5, R.id.scb_call_type6)
            group(R.id.sb_enable_app_notify, R.id.layout_optional_action)
            group(R.id.sb_enable_bluetooth, R.id.layout_bluetooth_setting)
            group(R.id.sb_enable_location, R.id.layout_location_setting)
            group(R.id.sb_enable_sms_command, R.id.et_safe_phone)
            group(R.id.sb_enable_load_app_list, R.id.scb_load_user_app, R.id.scb_load_system_app)
            group(R.id.sb_enable_cactus, R.id.layout_cactus_optional, R.id.layout_music_interval)
            group(R.id.sb_sms_template, R.id.layout_sms_template)
            val retries = root.findViewById<cn.ppps.forwarder.widget.material.TargetSlider>(R.id.xsb_retry_times)
            if (retries != null) root.findViewById<View>(R.id.layout_delay_time)?.let {
                enabledTree(it, retries.value > 0f); it.alpha = if (retries.value > 0f) 1f else 0.45f
            }
        }
        val listener = ViewTreeObserver.OnPreDrawListener { update(); true }
        var observer: ViewTreeObserver? = null
        fun attach() {
            observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
            observer = root.viewTreeObserver
            observer?.addOnPreDrawListener(listener)
            update()
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = attach()
            override fun onViewDetachedFromWindow(v: View) {
                observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
                observer = null
            }
        })
        if (root.isAttachedToWindow) attach()
        // Changes to checked state request a redraw, but not necessarily a layout.
        fun watch(view: View) {
            if (view is cn.ppps.forwarder.widget.material.TargetSwitch) view.dependencyStateChanged = { update() }
            if (view is ViewGroup) for (i in 0 until view.childCount) watch(view.getChildAt(i))
        }
        watch(root)
        update()
    }
}
