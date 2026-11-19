package cn.ppps.forwarder.activity

import android.annotation.SuppressLint
import android.view.KeyEvent
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.CommonUtils.Companion.showPrivacyDialog
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.SettingUtils.Companion.isAgreePrivacy
import com.xuexiang.xui.utils.KeyboardUtils
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import com.xuexiang.xutil.app.ActivityUtils
import me.jessyan.autosize.internal.CancelAdapt

@Suppress("PropertyName")
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity(), CancelAdapt {

    val TAG: String = SplashActivity::class.java.simpleName

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.hjq.language.MultiLanguages.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isAgreePrivacy) {
            whereToJump()
        } else {
            showPrivacyDialog(this) { dialog: MaterialDialog, _: DialogAction? ->
                dialog.dismiss()
                isAgreePrivacy = true
                whereToJump()
            }
        }
    }

    private fun whereToJump() {
        if (SettingUtils.enablePureTaskMode) {
            ActivityUtils.startActivity(TaskActivity::class.java)
        } else if (SettingUtils.enablePureClientMode) {
            ActivityUtils.startActivity(ClientActivity::class.java)
        } else {
            ActivityUtils.startActivity(MainActivity::class.java)
        }
        finish()
    }

    /**
     * Menu, Return Key Response
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return KeyboardUtils.onDisableBackKeyDown(keyCode) && super.onKeyDown(keyCode, event)
    }
}