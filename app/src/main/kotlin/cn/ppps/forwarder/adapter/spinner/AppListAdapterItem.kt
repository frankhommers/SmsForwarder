package cn.ppps.forwarder.adapter.spinner

import android.graphics.drawable.Drawable

@Suppress("unused")
class AppListAdapterItem(
    var name: String = "",
    var icon: Drawable? = null,
    var packageName: String? = null
) {

    // Note: Custom entities need to rewrite objects toString Method
    override fun toString(): String {
        return name
    }

    companion object {
        fun of(name: String): AppListAdapterItem {
            return AppListAdapterItem(name)
        }

        fun arrayOf(vararg titles: String): Array<AppListAdapterItem> {
            return titles.map { AppListAdapterItem(it) }.toTypedArray()
        }
    }
}
