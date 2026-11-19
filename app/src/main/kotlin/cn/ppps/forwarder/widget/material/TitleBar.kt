package cn.ppps.forwarder.widget.material

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.appbar.MaterialToolbar
import cn.ppps.forwarder.R

/** Compatibility API only: all drawing, menus and accessibility belong to MaterialToolbar. */
class TitleBar(context: Context) : LinearLayout(context) {
    private val toolbar = MaterialToolbar(context)
    init {
        orientation = VERTICAL
        addView(toolbar, LayoutParams(-1, -2))
        toolbar.minimumHeight = (64 * resources.displayMetrics.density).toInt()
        toolbar.navigationContentDescription = context.getString(R.string.navigation_go_back)
        toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    }
    fun setTitle(text: CharSequence?) = apply { toolbar.title = text }
    fun setTitle(id: Int) = apply { toolbar.setTitle(id) }
    fun setSubTitle(text: CharSequence?) = apply { toolbar.subtitle = text }
    fun setImmersive(immersive: Boolean) = this
    fun setLeftImageResource(id: Int) = apply {
        toolbar.setNavigationIcon(id)
        toolbar.navigationContentDescription = context.getString(if (id == R.drawable.ic_action_menu || id == R.drawable.ic_settings_menu) R.string.description_navigation_main else R.string.navigation_go_back)
    }
    fun setLeftClickListener(listener: View.OnClickListener) = apply { toolbar.setNavigationOnClickListener(listener) }
    fun disableLeftView() = apply { toolbar.navigationIcon = null }
    fun setActionTextColor(color: Int) = this // Material theme owns action and title colors.
    fun addOverflowAction(label: Int, action: () -> Unit) = apply {
        toolbar.menu.add(label).apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
            setOnMenuItemClickListener { action(); true }
        }
    }
    abstract class ImageAction(val icon: Int) { abstract fun performAction(view: View) }
    fun addAction(action: ImageAction) = apply {
        val label = when(action.icon) {
            R.drawable.ic_add -> R.string.add_item
            R.drawable.ic_save -> R.string.save
            R.drawable.ic_delete -> R.string.del
            R.drawable.ic_copy -> R.string.copy
            R.drawable.ic_logout -> R.string.m3_logout
            R.drawable.ic_menu_notifications_white -> R.string.m3_notifications
            R.drawable.ic_restore -> R.string.m3_restore
            R.drawable.ic_refresh -> R.string.refresh
            R.drawable.ic_filter -> R.string.m3_filter
            R.drawable.ic_logcat -> R.string.menu_logcat
            R.drawable.ic_query -> R.string.search
            else -> error("Missing toolbar action label: $action")
        }
        toolbar.menu.add(label).apply {
            setIcon(action.icon)
            icon?.let { androidx.core.graphics.drawable.DrawableCompat.setTint(it.mutate(), com.google.android.material.color.MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)) }
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener { action.performAction(toolbar); true }
        }
    }
}

object TitleUtils {
    fun addTitleBarDynamic(root: ViewGroup, title: String?, back: View.OnClickListener): TitleBar {
        return TitleBar(root.context).apply {
            setTitle(title)
            setLeftImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setLeftClickListener(back)
            root.addView(this, 0, ViewGroup.LayoutParams(-1, -2))
        }
    }
}
