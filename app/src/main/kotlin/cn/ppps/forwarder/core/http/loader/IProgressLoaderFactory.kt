package cn.ppps.forwarder.core.http.loader

import android.content.Context
import com.xuexiang.xhttp2.subsciber.impl.IProgressLoader

/**
 * IProgressLoaderFactory interface implementation
 *
 * @author xuexiang
 * @since 2019-11-18 23:17
 */
interface IProgressLoaderFactory {
    /**
     * Create Progress Loader
     *
     * @param context
     * @return
     */
    fun create(context: Context?): IProgressLoader?

    /**
     * Create Progress Loader
     *
     * @param context
     * @param message Default Hint
     * @return
     */
    fun create(context: Context?, message: String?): IProgressLoader?
}