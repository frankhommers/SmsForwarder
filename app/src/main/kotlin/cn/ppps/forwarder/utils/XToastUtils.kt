package cn.ppps.forwarder.utils

import android.annotation.SuppressLint
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.xuexiang.xui.XUI
import android.widget.Toast

/**
 * xtoast Tool Class
 *
 * @author xuexiang
 * @since 2019-06-30 19:04
 */
class XToastUtils private constructor() {
    @SuppressLint("CheckResult")
    companion object {
        //======General toast=======//
        @MainThread
        fun toast(message: CharSequence) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun toast(@StringRes message: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun toast(message: CharSequence, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun toast(@StringRes message: Int, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        //======Error=======//
        @MainThread
        fun error(throwable: Throwable) {
            Toast.makeText(XUI.getContext(), throwable.message!!, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun error(message: CharSequence) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun error(@StringRes message: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun error(message: CharSequence, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun error(@StringRes message: Int, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        //======Success=======//
        @MainThread
        fun success(message: CharSequence) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun success(@StringRes message: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun success(message: CharSequence, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun success(@StringRes message: Int, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        //======Message [Blue]=======//
        @MainThread
        fun info(message: CharSequence) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun info(@StringRes message: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun info(message: CharSequence, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun info(@StringRes message: Int, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        //=======Warning [yellow]======//
        @MainThread
        fun warning(message: CharSequence) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun warning(@StringRes message: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun warning(message: CharSequence, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }

        @MainThread
        fun warning(@StringRes message: Int, duration: Int) {
            Toast.makeText(XUI.getContext(), message, Toast.LENGTH_LONG).show()
        }


    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}