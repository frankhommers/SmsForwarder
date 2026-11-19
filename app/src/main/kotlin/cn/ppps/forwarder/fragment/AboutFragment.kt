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
import cn.ppps.forwarder.utils.update.UpdateInstaller
import cn.ppps.forwarder.utils.update.UpdateManager
import cn.ppps.forwarder.utils.update.UpdateResult
import cn.ppps.forwarder.utils.update.UpdateScheduler
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import cn.ppps.forwarder.widget.material.TitleBar
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import cn.ppps.forwarder.widget.material.TargetInfoRow as SuperTextView
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
        binding!!.menuVersion.setLeftString(String.format(resources.getString(R.string.about_app_version), AppUtils.getAppVersionName()) +
            "\n" + getString(R.string.updater_installed_build, AppUtils.getAppVersionCode().toString()))
        binding!!.menuCache.setLeftString(String.format(resources.getString(R.string.about_cache_size), CacheUtils.getTotalCacheSize(requireContext())))

        if (App.FrpclibInited) {
            binding!!.menuFrpc.setLeftString(String.format(resources.getString(R.string.about_frpc_version), Frpclib.getVersion()))
            binding!!.menuFrpc.visibility = View.VISIBLE
        }

        val dateFormat = SimpleDateFormat("yyyy", Locale.CHINA)
        val currentYear = dateFormat.format(Date())
        binding!!.copyright.text = java.lang.String.format(resources.getString(R.string.about_copyright), currentYear)

        // In-app updater (GitHub Releases of the fork). The preview-program row stays hidden.
        binding!!.scbAutoCheckUpdate.isChecked = SettingUtils.autoCheckUpdate
        binding!!.scbAutoCheckUpdate.isEnabled = true
        binding!!.btnUpdate.isEnabled = true
        binding!!.menuJoinPreviewProgram.visibility = View.GONE
        binding!!.sbJoinPreviewProgram.isEnabled = false
        renderUpdateState()
        binding!!.menuDonation.visibility = View.GONE
        binding!!.menuWechatMiniprogram.visibility = View.GONE

    }

    override fun initListeners() {
        binding!!.btnUpdate.setOnClickListener {
            // 'Check now' works whether or not automatic checks are on.
            if (!UpdateManager.checkNowAsync(requireContext())) XToastUtils.info(R.string.updater_busy)
            renderUpdateState()
        }
        binding!!.scbAutoCheckUpdate.setOnCheckedChangeListener { _, checked ->
            if (checked == SettingUtils.autoCheckUpdate) return@setOnCheckedChangeListener
            SettingUtils.autoCheckUpdate = checked
            UpdateScheduler.apply(requireContext())
        }
        binding!!.btnUpdatePermission.setOnClickListener { explainInstallPermission() }
        binding!!.btnUpdateInstall.setOnClickListener {
            // Re-open the pending system confirmation; if this process no longer holds it (for example the app was
            // restarted), start a clean pass, which abandons the stale session and downloads and verifies again.
            if (!UpdateInstaller.openPendingConfirmation(requireContext()) && !UpdateManager.checkNowAsync(requireContext())) XToastUtils.info(R.string.updater_busy)
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
        // Formerly a "Gitee" button that opened the same upstream GitHub page; now the fork's own repository.
        binding!!.btnGitee.setOnClickListener {
            AgentWebActivity.goWeb(context, getString(R.string.infra_form_url_project_fork))
        }

        binding!!.menuJoinPreviewProgram.setOnSuperTextViewClickListener(this)
        binding!!.menuVersion.setOnSuperTextViewClickListener(this)
        binding!!.menuWechatMiniprogram.setOnSuperTextViewClickListener(this)
        binding!!.menuDonation.setOnSuperTextViewClickListener(this)
        binding!!.menuUserProtocol.setOnSuperTextViewClickListener(this)
        binding!!.menuPrivacyProtocol.setOnSuperTextViewClickListener(this)
    }

    private val updatePrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        view?.post { if (binding != null) renderUpdateState() }
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.prefs(requireContext()).registerOnSharedPreferenceChangeListener(updatePrefsListener)
        val prefs = UpdateManager.prefs(requireContext())
        // Back from the 'Install unknown apps' screen with the permission granted: continue with the verified file.
        if (prefs.getString(UpdateManager.KEY_RESULT, "") == UpdateResult.INSTALL_PERMISSION && UpdateInstaller.canRequestInstalls(requireContext())) {
            UpdateManager.resumeInstallAsync(requireContext())
        }
        renderUpdateState()
    }

    override fun onPause() {
        UpdateManager.prefs(requireContext()).unregisterOnSharedPreferenceChangeListener(updatePrefsListener)
        super.onPause()
    }

    private fun explainInstallPermission() {
        MaterialDialog.Builder(requireContext())
            .title(R.string.updater_permission_title)
            .content(R.string.updater_permission_text)
            .positiveText(R.string.updater_permission_open)
            .negativeText(R.string.cancel)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                try { startActivity(UpdateInstaller.unknownSourcesSettingsIntent(requireContext())) } catch (e: Exception) { XToastUtils.error(e.message.toString()) }
            }
            .show()
    }

    /** Installed version, last check and its result, download progress. */
    private fun renderUpdateState() {
        val b = binding ?: return
        val ctx = context ?: return
        val prefs = UpdateManager.prefs(ctx)
        val last = prefs.getLong(UpdateManager.KEY_LAST_CHECK, 0)
        val kind = prefs.getString(UpdateManager.KEY_RESULT, "") ?: ""
        val arg = prefs.getString(UpdateManager.KEY_RESULT_ARG, "") ?: ""
        val busy = prefs.getBoolean(UpdateManager.KEY_RUNNING, false) && UpdateManager.isRunning
        b.tvUpdateLastCheck.text = if (last <= 0) getString(R.string.updater_never_checked) else getString(
            R.string.updater_last_check,
            android.text.format.DateUtils.formatDateTime(ctx, last, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME or android.text.format.DateUtils.FORMAT_SHOW_YEAR)
        )
        val statusText = when {
            kind == UpdateManager.CHECKING && busy -> getString(R.string.updater_checking)
            kind == UpdateManager.CHECKING -> ""
            kind.isEmpty() -> ""
            else -> UpdateResult.describe(ctx, kind, arg)
        }
        b.tvUpdateStatus.text = statusText
        b.tvUpdateStatus.visibility = if (statusText.isEmpty()) View.GONE else View.VISIBLE
        b.tvUpdateStatus.setTextColor(com.google.android.material.color.MaterialColors.getColor(b.tvUpdateStatus,
            if (UpdateResult.isError(kind) && kind != UpdateManager.CHECKING) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOnSurface))
        val downloading = busy && kind == UpdateResult.DOWNLOADING
        val total = prefs.getLong(UpdateManager.KEY_PROGRESS_TOTAL, 0)
        val done = prefs.getLong(UpdateManager.KEY_PROGRESS_DONE, 0)
        b.progressUpdate.visibility = if (busy) View.VISIBLE else View.GONE
        b.tvUpdateProgress.visibility = if (downloading && total > 0) View.VISIBLE else View.GONE
        if (downloading && total > 0) {
            if (b.progressUpdate.isIndeterminate) { b.progressUpdate.visibility = View.INVISIBLE; b.progressUpdate.isIndeterminate = false; b.progressUpdate.visibility = View.VISIBLE }
            b.progressUpdate.max = 1000
            b.progressUpdate.setProgressCompat((done * 1000 / total).toInt(), false)
            b.tvUpdateProgress.text = getString(R.string.updater_progress, android.text.format.Formatter.formatShortFileSize(ctx, done), android.text.format.Formatter.formatShortFileSize(ctx, total))
        } else if (busy && !b.progressUpdate.isIndeterminate) {
            b.progressUpdate.visibility = View.INVISIBLE; b.progressUpdate.isIndeterminate = true; b.progressUpdate.visibility = View.VISIBLE
        }
        b.btnUpdate.isEnabled = !busy
        b.btnUpdateInstall.visibility = if (kind == UpdateResult.WAITING_CONFIRMATION && !busy) View.VISIBLE else View.GONE
        b.btnUpdatePermission.visibility = if (kind == UpdateResult.INSTALL_PERMISSION && !UpdateInstaller.canRequestInstalls(ctx)) View.VISIBLE else View.GONE
        if (b.scbAutoCheckUpdate.isChecked != SettingUtils.autoCheckUpdate) b.scbAutoCheckUpdate.isChecked = SettingUtils.autoCheckUpdate
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
                    XToastUtils.error(getString(R.string.copy_wechat_sm4))
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