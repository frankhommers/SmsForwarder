package cn.ppps.forwarder.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cn.ppps.forwarder.App
import cn.ppps.forwarder.BuildConfig
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.webview.AgentWebActivity
import cn.ppps.forwarder.databinding.FragmentAboutBinding
import cn.ppps.forwarder.utils.AppUtils
import cn.ppps.forwarder.utils.CacheUtils
import cn.ppps.forwarder.utils.CommonUtils.Companion.gotoProtocol
import cn.ppps.forwarder.utils.CommonUtils.Companion.previewMarkdown
import cn.ppps.forwarder.utils.CommonUtils.Companion.previewPicture
import cn.ppps.forwarder.utils.CommonUtils.Companion.restartApplication
import cn.ppps.forwarder.utils.HistoryUtils
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xui.widget.textview.supertextview.SuperTextView
import frpclib.Frpclib
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Page(name = "About Software")
class AboutFragment : BaseFragment<FragmentAboutBinding?>(), SuperTextView.OnSuperTextViewClickListener {

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentAboutBinding {
        return FragmentAboutBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        val titleBar = super.initTitle()!!.setImmersive(false)
        titleBar.setTitle(R.string.menu_about)
        return titleBar
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        binding!!.menuVersion.setLeftString(String.format(resources.getString(R.string.about_app_version), AppUtils.getAppVersionName()))
        binding!!.menuCache.setLeftString(String.format(resources.getString(R.string.about_cache_size), CacheUtils.getTotalCacheSize(requireContext())))

        if (App.FrpclibInited) {
            binding!!.menuFrpc.setLeftString(String.format(resources.getString(R.string.about_frpc_version), Frpclib.getVersion()))
            binding!!.menuFrpc.visibility = View.VISIBLE
        }

        val dateFormat = SimpleDateFormat("yyyy", Locale.CHINA)
        val currentYear = dateFormat.format(Date())
        binding!!.copyright.text = java.lang.String.format(resources.getString(R.string.about_copyright), currentYear)

        binding!!.scbAutoCheckUpdate.isChecked = false
        binding!!.scbAutoCheckUpdate.isEnabled = false
        binding!!.menuJoinPreviewProgram.visibility = View.GONE
        binding!!.sbJoinPreviewProgram.isEnabled = false
        binding!!.btnUpdate.isEnabled = false
        binding!!.menuDonation.visibility = View.GONE
        binding!!.menuWechatMiniprogram.visibility = View.GONE

    }

    override fun initListeners() {
        binding!!.btnUpdate.setOnClickListener {
            XToastUtils.info(R.string.privacy_updates_disabled)
        }
        binding!!.btnCache.setOnClickListener {
            HistoryUtils.clearPreference()
            CacheUtils.clearAllCache(requireContext())
            XToastUtils.success(R.string.about_cache_purged)
            binding!!.menuCache.setLeftString(String.format(resources.getString(R.string.about_cache_size), CacheUtils.getTotalCacheSize(requireContext())))
        }
        binding!!.btnFrpc.setOnClickListener {
            try {
                val soFile = File(context?.filesDir?.absolutePath + "/libs/libgojni.so")
                if (soFile.exists()) soFile.delete()
                MaterialDialog.Builder(requireContext())
                    .iconRes(R.drawable.ic_menu_frpc)
                    .title(R.string.menu_frpc)
                    .content(R.string.about_frpc_deleted)
                    .cancelable(false)
                    .positiveText(R.string.confirm)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        restartApplication()
                    }
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("AboutFragment", "btnFrpc.setOnClickListener error: ${e.message}")
                XToastUtils.error(e.message.toString())
            }
        }
        binding!!.btnGithub.setOnClickListener {
            AgentWebActivity.goWeb(context, getString(R.string.url_project_github))
        }
        binding!!.btnGitee.setOnClickListener {
            AgentWebActivity.goWeb(context, getString(R.string.url_project_gitee))
        }

        binding!!.menuJoinPreviewProgram.setOnSuperTextViewClickListener(this)
        binding!!.menuVersion.setOnSuperTextViewClickListener(this)
        binding!!.menuWechatMiniprogram.setOnSuperTextViewClickListener(this)
        binding!!.menuDonation.setOnSuperTextViewClickListener(this)
        binding!!.menuUserProtocol.setOnSuperTextViewClickListener(this)
        binding!!.menuPrivacyProtocol.setOnSuperTextViewClickListener(this)
    }

    @SingleClick
    override fun onClick(v: SuperTextView) {
        when (v.id) {
            R.id.menu_join_preview_program -> {
                XToastUtils.info(getString(R.string.join_preview_program_tips))
            }

            R.id.menu_version -> {
                XToastUtils.info(
                    String.format(
                        getString(R.string.about_app_version_tips),
                        AppUtils.getAppVersionName(),
                        AppUtils.getAppVersionCode(),
                        BuildConfig.BUILD_TIME,
                        BuildConfig.GIT_COMMIT_ID
                    )
                )
            }

            R.id.menu_donation -> {
                XToastUtils.info(R.string.privacy_updates_disabled)
            }

            R.id.menu_wechat_miniprogram -> {
                if (HttpServerUtils.safetyMeasures != 3) {
                    XToastUtils.error("The WeChat mini-program only supportsSM4encrypted transport. Change the security mode under Remote control - Server！")
                    //return
                }
                XToastUtils.info(R.string.privacy_updates_disabled)
            }

            R.id.menu_user_protocol -> {
                gotoProtocol(this, isPrivacy = false, isImmersive = false)
            }

            R.id.menu_privacy_protocol -> {
                gotoProtocol(this, isPrivacy = true, isImmersive = false)
            }
        }
    }
}