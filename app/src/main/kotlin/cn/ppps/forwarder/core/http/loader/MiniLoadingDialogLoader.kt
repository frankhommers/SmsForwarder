package cn.ppps.forwarder.core.http.loader

import android.content.Context
import com.xuexiang.xhttp2.subsciber.impl.IProgressLoader
import com.xuexiang.xhttp2.subsciber.impl.OnProgressCancelListener
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Default Progress Loading
 *
 * @author xuexiang
 * @since 2019-11-18 23:07
 */
class MiniLoadingDialogLoader @JvmOverloads constructor(
    context: Context?,
    msg: String? = "Loading...",
) : IProgressLoader {
    /**
     * ProgressloadingDialog
     */
    private val mDialog: AlertDialog?

    /**
     * Progress-dialog cancellation listener
     */
    private var mOnProgressCancelListener: OnProgressCancelListener? = null
    override fun isLoading(): Boolean {
        return mDialog != null && mDialog.isShowing
    }

    override fun updateMessage(msg: String) {
        mDialog?.setMessage(msg)
    }

    override fun showLoading() {
        if (mDialog != null && !mDialog.isShowing) {
            mDialog.show()
        }
    }

    override fun dismissLoading() {
        if (mDialog != null && mDialog.isShowing) {
            mDialog.dismiss()
        }
    }

    override fun setCancelable(flag: Boolean) {
        mDialog!!.setCancelable(flag)
        if (flag) {
            mDialog.setOnCancelListener {
                if (mOnProgressCancelListener != null) {
                    mOnProgressCancelListener!!.onCancelProgress()
                }
            }
        }
    }

    override fun setOnProgressCancelListener(listener: OnProgressCancelListener) {
        mOnProgressCancelListener = listener
    }

    init {
        mDialog = context?.let {
            MaterialAlertDialogBuilder(it).setMessage(msg)
                .setView(CircularProgressIndicator(it).apply { isIndeterminate = true })
                .create()
        }
    }
}