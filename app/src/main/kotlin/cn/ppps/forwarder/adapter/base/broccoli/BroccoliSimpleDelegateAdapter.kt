@file:Suppress("unused")

package cn.ppps.forwarder.adapter.base.broccoli

import android.view.View
import com.alibaba.android.vlayout.LayoutHelper
import cn.ppps.forwarder.adapter.base.delegate.SimpleDelegateAdapter
import cn.ppps.forwarder.adapter.base.delegate.XDelegateAdapter
import com.xuexiang.xui.adapter.recyclerview.RecyclerViewHolder
import me.samlss.broccoli.Broccoli

/**
 * UseBroccoliBase skeleton-placeholder adapter
 *
 * @author xuexiang
 * @since 2021/1/9 4:52 PM
 */
@Suppress("unused")
abstract class BroccoliSimpleDelegateAdapter<T> : SimpleDelegateAdapter<T> {
    /**
     * Whether or not it has been loaded successfully
     */
    private var mHasLoad = false
    private val mBroccoliMap: MutableMap<View, Broccoli> = HashMap()

    constructor(layoutId: Int, layoutHelper: LayoutHelper) : super(layoutId, layoutHelper)
    constructor(layoutId: Int, layoutHelper: LayoutHelper, list: Collection<T>?) : super(
        layoutId,
        layoutHelper,
        list
    )

    constructor(layoutId: Int, layoutHelper: LayoutHelper?, data: Array<T>?) : super(
        layoutId,
        layoutHelper!!,
        data
    )

    override fun bindData(holder: RecyclerViewHolder, position: Int, item: T) {
        var broccoli = mBroccoliMap[holder.itemView]
        if (broccoli == null) {
            broccoli = Broccoli()
            mBroccoliMap[holder.itemView] = broccoli
        }
        if (mHasLoad) {
            broccoli.removeAllPlaceholders()
            onBindData(holder, item, position)
        } else {
            onBindBroccoli(holder, broccoli)
            broccoli.show()
        }
    }

    /**
     * Bind controls
     *
     * @param holder
     * @param model
     * @param position
     */
    protected abstract fun onBindData(holder: RecyclerViewHolder, model: T, position: Int)

    /**
     * Bind placeholder controls
     *
     * @param holder
     * @param broccoli
     */
    protected abstract fun onBindBroccoli(holder: RecyclerViewHolder, broccoli: Broccoli)

    override fun refresh(collection: Collection<T>?): XDelegateAdapter<*, *> {
        mHasLoad = true
        return super.refresh(collection)
    }

    /**
     * Release of resources to prevent leakage of memory
     */
    fun recycle() {
        for (broccoli in mBroccoliMap.values) {
            broccoli.removeAllPlaceholders()
        }
        mBroccoliMap.clear()
        clear()
    }
}