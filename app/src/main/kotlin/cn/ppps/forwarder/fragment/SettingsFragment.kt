package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.adapter.spinner.AppListAdapterItem
import cn.ppps.forwarder.adapter.spinner.AppListSpinnerAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentSettingsBinding
import cn.ppps.forwarder.entity.SimInfo
import cn.ppps.forwarder.fragment.client.CloneFragment
import cn.ppps.forwarder.receiver.BootCompletedReceiver
import cn.ppps.forwarder.service.BluetoothScanService
import cn.ppps.forwarder.service.ForegroundService
import cn.ppps.forwarder.service.LocationService
import cn.ppps.forwarder.service.NotificationService
import cn.ppps.forwarder.utils.ACTION_RESTART
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.ACTION_STOP
import cn.ppps.forwarder.utils.ACTION_UPDATE_NOTIFICATION
import cn.ppps.forwarder.utils.AppUtils.getAppPackageName
import cn.ppps.forwarder.utils.BluetoothUtils
import cn.ppps.forwarder.utils.CommonUtils
import cn.ppps.forwarder.utils.DataProvider
import cn.ppps.forwarder.utils.EVENT_LOAD_APP_LIST
import cn.ppps.forwarder.utils.EXTRA_UPDATE_NOTIFICATION
import cn.ppps.forwarder.utils.KEY_DEFAULT_SELECTION
import cn.ppps.forwarder.utils.KeepAliveUtils
import cn.ppps.forwarder.utils.LocationUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.PhoneUtils
import cn.ppps.forwarder.utils.ProximitySensorScreenHelper
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import cn.ppps.forwarder.widget.GuideTipsDialog
import cn.ppps.forwarder.workers.LoadAppListWorker
import com.hjq.language.LocaleContract
import com.hjq.language.MultiLanguages
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.jeremyliao.liveeventbus.LiveEventBus
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xpage.core.PageOption
import cn.ppps.forwarder.widget.material.TitleBar
import cn.ppps.forwarder.widget.material.TargetCheckBox as SmoothCheckBox
import cn.ppps.forwarder.widget.material.TargetSwitch as SwitchButton
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import cn.ppps.forwarder.widget.material.TargetSlider as XSeekBar
import cn.ppps.forwarder.widget.material.OptionsPickerBuilder
import cn.ppps.forwarder.widget.material.OnOptionsSelectListener
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.XUtil.getPackageManager
import com.xuexiang.xutil.file.FileUtils
import java.util.Locale

@Suppress("SpellCheckingInspection", "PrivatePropertyName")
@Page(name = "General settings")
class SettingsFragment : BaseFragment<FragmentSettingsBinding?>(), View.OnClickListener {

    private val TAG: String = SettingsFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private val mTimeOption = DataProvider.timePeriodOption
    private var initViewsFinished = false
    internal var settingsNavigation: SettingsNavigation? = null
        private set

    private var restoredDetail: String? = null
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        restoredDetail = savedInstanceState?.getString("settings.detail")
    }
    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putString("settings.detail", settingsNavigation?.current)
        super.onSaveInstanceState(outState)
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        settingsNavigation?.updateBackOwnership()
    }
    override fun consumeNavigationBack(): Boolean = settingsNavigation?.consumeBack() == true || super.consumeNavigationBack()

    internal fun openBackup() {
        PageOption.to(CloneFragment::class.java).putInt(KEY_DEFAULT_SELECTION, 1)
            .setNewActivity(true).open(this)
    }

    //InstalledAppInfo List
    private val appListSpinnerList = ArrayList<AppListAdapterItem>()
    private lateinit var appListSpinnerAdapter: AppListSpinnerAdapter<*>
    private val appListObserver = Observer { it: String ->
        Log.d(TAG, "EVENT_LOAD_APP_LIST: $it")
        initAppSpinner()
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        // Root tab: the bottom bar is the only primary navigation, so no hamburger or fake Back.
        titleBar!!.disableLeftView()
        titleBar!!.setTitle(R.string.menu_settings)
        titleBar!!.addOverflowAction(R.string.settings_setup) { GuideTipsDialog.showTipsForce(requireContext()) }
        titleBar!!.addOverflowAction(R.string.settings_backup) { openBackup() }
        return titleBar
    }

    @SuppressLint("NewApi", "SetTextI18n")
    override fun initViews() {

        //Forward SMS broadcasts
        switchEnableSms(binding!!.sbEnableSms)
        //Transmitting call records
        switchEnablePhone(binding!!.sbEnablePhone, binding!!.scbCallType1, binding!!.scbCallType2, binding!!.scbCallType3, binding!!.scbCallType4, binding!!.scbCallType5, binding!!.scbCallType6)
        //Forward application notifications
        switchEnableAppNotify(binding!!.sbEnableAppNotify, binding!!.scbCancelAppNotify, binding!!.scbNotUserPresent)

        //Bluetooth discovery service
        switchEnableBluetooth(binding!!.sbEnableBluetooth, binding!!.layoutBluetoothSetting, binding!!.xsbScanInterval, binding!!.scbIgnoreAnonymous)
        //GPSPositioning function
        switchEnableLocation(binding!!.sbEnableLocation, binding!!.layoutLocationSetting, binding!!.rgAccuracy, binding!!.rgPowerRequirement, binding!!.xsbMinInterval, binding!!.xsbMinDistance)
        //SMS Command
        switchEnableSmsCommand(binding!!.sbEnableSmsCommand, binding!!.etSafePhone)
        //Turn off screen near the earpiece
        switchEnableCloseToEarpieceTurnOffScreen(binding!!.layoutEnableCloseToEarpieceTurnOffScreen, binding!!.sbEnableCloseToEarpieceTurnOffScreen)
        //Load installed applications asynchronously on startupAppInformation
        switchEnableLoadAppList(binding!!.sbEnableLoadAppList, binding!!.scbLoadUserApp, binding!!.scbLoadSystemApp)
        //Setup to automatically eliminate additionalAPPNotifications
        editExtraAppList(binding!!.etAppList)
        //SettingsAPPNotification key word blacklist
        editAppNotifyBlacklist(binding!!.etAppNotifyBlacklist)
        //Automatically filter for how long to repeat messages
        binding!!.xsbDuplicateMessagesLimits.setDefaultValue(SettingUtils.duplicateMessagesLimits)
        binding!!.xsbDuplicateMessagesLimits.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            SettingUtils.duplicateMessagesLimits = newValue
        }
        //Do not disturb(Disable forwarding)Period
        binding!!.tvSilentPeriod.text = mTimeOption[SettingUtils.silentPeriodStart] + " ~ " + mTimeOption[SettingUtils.silentPeriodEnd]
        binding!!.scbSilentPeriodLogs.isChecked = SettingUtils.enableSilentPeriodLogs
        binding!!.scbSilentPeriodLogs.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableSilentPeriodLogs = isChecked
        }

        //Start on boot
        checkWithReboot(binding!!.sbWithReboot, binding!!.tvAutoStartup)
        //Ignore battery optimization settings
        batterySetting(binding!!.layoutBatterySetting, binding!!.sbBatterySetting)
        //Hide from recent tasks
        switchExcludeFromRecents(binding!!.layoutExcludeFromRecents, binding!!.sbExcludeFromRecents)
        //CactusEnhanced keep-alive mechanisms
        switchEnableCactus(binding!!.sbEnableCactus, binding!!.scbPlaySilenceMusic, binding!!.scbOnePixelActivity, binding!!.layoutMusicInterval, binding!!.xsbMusicInterval)
        //Interface request failed to retry time interval
        editRetryDelayTime(binding!!.xsbRetryTimes, binding!!.xsbDelayTime, binding!!.xsbTimeout)

        //Device Remarks
        editAddExtraDeviceMark(binding!!.etExtraDeviceMark)
        //SIM1Primary Key
        editAddSubidSim1(binding!!.etSubidSim1)
        //SIM1Remarks
        editAddExtraSim1(binding!!.etExtraSim1)

        // sim Hide when only one SIM slot exists SIM2 Settings
        if (PhoneUtils.getSimSlotCount() != 1) {
            //SIM2Primary Key
            editAddSubidSim2(binding!!.etSubidSim2)
            //SIM2Remarks
            editAddExtraSim2(binding!!.etExtraSim2)
        } else {
            binding!!.layoutSim2.visibility = View.GONE
        }
        //Content of notice
        editNotifyContent(binding!!.etNotifyContent)
        //Enable custom template
        switchSmsTemplate(binding!!.sbSmsTemplate)
        //Custom Template
        editSmsTemplate(binding!!.etSmsTemplate)
        //Pure client mode
        switchDirectlyToClient(binding!!.sbDirectlyToClient)
        //Pure Auto Task Mode
        switchDirectlyToTask(binding!!.sbDirectlyToTask)
        //Debug Mode
        switchDebugMode(binding!!.sbDebugMode)
        //Multilingual Settings
        switchLanguage(binding!!.rgMainLanguages)

        initViewsFinished = true
        settingsNavigation = SettingsNavigation(this, binding!!, titleBar!!)
        // Only preference routes are details now; a stale "extras" key from the former Settings panel shows the overview.
        restoredDetail?.takeIf { key -> settingsNavigation!!.entries.any { it.key == key } }?.let { settingsNavigation?.open(it) }
        restoredDetail = null
    }

    override fun onResume() {
        super.onResume()
        //InitializeAPPDrawdown List
        initAppSpinner()
        settingsNavigation?.refresh()
    }

    override fun initListeners() {
        binding!!.btnSilentPeriod.setOnClickListener(this)
        binding!!.btnExtraDeviceMark.setOnClickListener(this)
        binding!!.btnExtraSim1.setOnClickListener(this)
        binding!!.btnExtraSim2.setOnClickListener(this)
        binding!!.btnExportLog.setOnClickListener(this)

        //Listener installedAppInformation list loaded event
        LiveEventBus.get(EVENT_LOAD_APP_LIST, String::class.java).observeStickyForever(appListObserver)
    }

    @SuppressLint("SetTextI18n")
    @SingleClick
    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_silent_period -> {
                OptionsPickerBuilder(context, OnOptionsSelectListener { _: View?, options1: Int, options2: Int, _: Int ->
                    SettingUtils.silentPeriodStart = options1
                    SettingUtils.silentPeriodEnd = options2
                    val txt = mTimeOption[options1] + " ~ " + mTimeOption[options2]
                    binding!!.tvSilentPeriod.text = txt
                    XToastUtils.toast(txt)
                    return@OnOptionsSelectListener false
                }).setTitleText(getString(R.string.select_time_period)).setSelectOptions(SettingUtils.silentPeriodStart, SettingUtils.silentPeriodEnd).build<Any>().also {
                    it.setNPicker(mTimeOption, mTimeOption)
                    it.show()
                }
            }

            R.id.btn_extra_device_mark -> {
                binding!!.etExtraDeviceMark.setText(PhoneUtils.getDeviceName())
                return
            }

            R.id.btn_extra_sim1 -> {
                App.SimInfoList = PhoneUtils.getSimMultiInfo()
                if (App.SimInfoList.isEmpty()) {
                    XToastUtils.error(R.string.tip_can_not_get_sim_infos)
                    XXPermissions.startPermissionActivity(
                        requireContext(), PermissionLists.getReadPhoneStatePermission()
                    )
                    return
                }
                Log.d(TAG, "SIM inventory refreshed; details omitted")
                if (!App.SimInfoList.containsKey(0)) {
                    XToastUtils.error(
                        String.format(
                            getString(R.string.tip_can_not_get_sim_info), 1
                        )
                    )
                    return
                }
                val simInfo: SimInfo? = App.SimInfoList[0]
                binding!!.etSubidSim1.setText(simInfo?.mSubscriptionId.toString())
                binding!!.etExtraSim1.setText(simInfo?.mCarrierName.toString() + "_" + simInfo?.mNumber.toString())
                return
            }

            R.id.btn_extra_sim2 -> {
                App.SimInfoList = PhoneUtils.getSimMultiInfo()
                if (App.SimInfoList.isEmpty()) {
                    XToastUtils.error(R.string.tip_can_not_get_sim_infos)
                    XXPermissions.startPermissionActivity(
                        requireContext(), PermissionLists.getReadPhoneStatePermission()
                    )
                    return
                }
                Log.d(TAG, "SIM inventory refreshed; details omitted")
                if (!App.SimInfoList.containsKey(1)) {
                    XToastUtils.error(
                        String.format(
                            getString(R.string.tip_can_not_get_sim_info), 2
                        )
                    )
                    return
                }
                val simInfo: SimInfo? = App.SimInfoList[1]
                binding!!.etSubidSim2.setText(simInfo?.mSubscriptionId.toString())
                binding!!.etExtraSim2.setText(simInfo?.mCarrierName.toString() + "_" + simInfo?.mNumber.toString())
                return
            }

            R.id.btn_export_log -> {
                XXPermissions.with(this)
                    // Request for storage privileges
                    .permission(PermissionLists.getManageExternalStoragePermission())
                    .request(object : OnPermissionCallback {
                        override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                            val allGranted = deniedList.isEmpty()
                            if (!allGranted) {
                                // Check whether the user selected Never ask again for denied permissions
                                val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                                if (doNotAskAgain) {
                                    XToastUtils.error(R.string.toast_denied_never)
                                    XXPermissions.startPermissionActivity(requireContext(), deniedList)
                                }
                                // Logic of failure in processing requests for permission
                                XToastUtils.error(R.string.toast_denied)
                                return
                            }
                            try {
                                val srcDirPath = App.context.cacheDir.absolutePath + "/logs"
                                val destDirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + "/SmsForwarder"
                                if (FileUtils.copyDir(srcDirPath, destDirPath, null)) {
                                    XToastUtils.success(getString(R.string.log_export_success) + destDirPath)
                                } else {
                                    XToastUtils.error(getString(R.string.log_export_failed))
                                }
                            } catch (e: Exception) {
                                XToastUtils.error(getString(R.string.log_export_failed) + e.message)
                                e.printStackTrace()
                            }
                        }
                    })
                return
            }

            else -> {}
        }
    }

    //Forward SMS
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun switchEnableSms(sbEnableSms: SwitchButton) {
        sbEnableSms.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableSms = isChecked
            if (isChecked) {
                XXPermissions.with(this)
                    // Receive WAP Send Message
                    .permission(PermissionLists.getReceiveWapPushPermission())
                    // Receive MMS
                    .permission(PermissionLists.getReceiveMmsPermission())
                    // Receive SMS
                    .permission(PermissionLists.getReceiveSmsPermission())
                    // Send SMS
                    //.permission(PermissionLists.getSendSmsPermission())
                    // Read Text Messages
                    .permission(PermissionLists.getReadSmsPermission())
                    .request(object : OnPermissionCallback {
                        override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                            val allGranted = deniedList.isEmpty()
                            if (!allGranted) {
                                // Check whether the user selected Never ask again for denied permissions
                                val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                                if (doNotAskAgain) {
                                    XToastUtils.error(R.string.toast_denied_never)
                                    // Jump to Apply Permission System Settings page if refused permanently
                                    XXPermissions.startPermissionActivity(requireContext(), deniedList)
                                }
                                // Logic of failure in processing requests for permission
                                XToastUtils.warning(getString(R.string.forward_sms) + ": " + getString(R.string.toast_granted_part))
                                SettingUtils.enableSms = false
                                sbEnableSms.isChecked = false
                                return
                            }
                            // Logic of successful processing of requests for authority
                            XToastUtils.info(R.string.toast_granted_all)
                        }
                    })
            }
        }
        sbEnableSms.isChecked = SettingUtils.enableSms
    }

    private fun noCallEventSelected() = !SettingUtils.enableCallType1 && !SettingUtils.enableCallType2 && !SettingUtils.enableCallType3 &&
        !SettingUtils.enableCallType4 && !SettingUtils.enableCallType5 && !SettingUtils.enableCallType6

    //Forward call events
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun switchEnablePhone(sbEnablePhone: SwitchButton, scbCallType1: SmoothCheckBox, scbCallType2: SmoothCheckBox, scbCallType3: SmoothCheckBox, scbCallType4: SmoothCheckBox, scbCallType5: SmoothCheckBox, scbCallType6: SmoothCheckBox) {
        scbCallType1.isChecked = SettingUtils.enableCallType1
        scbCallType2.isChecked = SettingUtils.enableCallType2
        scbCallType3.isChecked = SettingUtils.enableCallType3
        scbCallType4.isChecked = SettingUtils.enableCallType4
        scbCallType5.isChecked = SettingUtils.enableCallType5
        scbCallType6.isChecked = SettingUtils.enableCallType6
        var restored = false
        sbEnablePhone.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            // The master switch always turns on; with no event selected nothing is forwarded (CallReceiver checks each type).
            SettingUtils.enablePhone = isChecked
            if (isChecked) {
                if (restored && noCallEventSelected()) XToastUtils.info(R.string.settings_calls_none_selected)
                XXPermissions.with(this)
                    // Read Phone Status
                    .permission(PermissionLists.getReadPhoneStatePermission())
                    // Read cell number.
                    .permission(PermissionLists.getReadPhoneNumbersPermission())
                    // Read call records
                    .permission(PermissionLists.getReadCallLogPermission())
                    // Read Contacts
                    .permission(PermissionLists.getReadContactsPermission())
                    .request(object : OnPermissionCallback {
                        override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                            val allGranted = deniedList.isEmpty()
                            if (!allGranted) {
                                // Check whether the user selected Never ask again for denied permissions
                                val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                                if (doNotAskAgain) {
                                    XToastUtils.error(R.string.toast_denied_never)
                                    XXPermissions.startPermissionActivity(requireContext(), deniedList)
                                }
                                // Logic of failure in processing requests for permission
                                XToastUtils.error(getString(R.string.forward_calls) + ": " + getString(R.string.toast_denied))
                                SettingUtils.enablePhone = false
                                sbEnablePhone.isChecked = false
                                return
                            }
                        }
                    })
            }
        }
        sbEnablePhone.isChecked = SettingUtils.enablePhone
        restored = true
        scbCallType1.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableCallType1 = isChecked
            // Unchecking the last event keeps the master on and the boxes usable; it only explains the effect.
            if (!isChecked && SettingUtils.enablePhone && noCallEventSelected()) XToastUtils.info(R.string.settings_calls_none_selected)
        }
        scbCallType2.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableCallType2 = isChecked
            // Unchecking the last event keeps the master on and the boxes usable; it only explains the effect.
            if (!isChecked && SettingUtils.enablePhone && noCallEventSelected()) XToastUtils.info(R.string.settings_calls_none_selected)
        }
        scbCallType3.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableCallType3 = isChecked
            // Unchecking the last event keeps the master on and the boxes usable; it only explains the effect.
            if (!isChecked && SettingUtils.enablePhone && noCallEventSelected()) XToastUtils.info(R.string.settings_calls_none_selected)
        }
        scbCallType4.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableCallType4 = isChecked
            // Unchecking the last event keeps the master on and the boxes usable; it only explains the effect.
            if (!isChecked && SettingUtils.enablePhone && noCallEventSelected()) XToastUtils.info(R.string.settings_calls_none_selected)
        }
        scbCallType5.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableCallType5 = isChecked
            // Unchecking the last event keeps the master on and the boxes usable; it only explains the effect.
            if (!isChecked && SettingUtils.enablePhone && noCallEventSelected()) XToastUtils.info(R.string.settings_calls_none_selected)
        }
        scbCallType6.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableCallType6 = isChecked
            // Unchecking the last event keeps the master on and the boxes usable; it only explains the effect.
            if (!isChecked && SettingUtils.enablePhone && noCallEventSelected()) XToastUtils.info(R.string.settings_calls_none_selected)
        }
    }

    //Forward application notifications
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun switchEnableAppNotify(sbEnableAppNotify: SwitchButton, scbCancelAppNotify: SmoothCheckBox, scbNotUserPresent: SmoothCheckBox) {
        sbEnableAppNotify.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            binding!!.layoutOptionalAction.visibility = View.VISIBLE
            SettingUtils.enableAppNotify = isChecked
            if (isChecked) {
                XXPermissions.with(this)
                    .permission(
                        PermissionLists.getBindNotificationListenerServicePermission(
                            NotificationService::class.java
                        )
                    )
                    .request(object : OnPermissionCallback {
                        override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                            val allGranted = deniedList.isEmpty()
                            if (!allGranted) {
                                Log.e(TAG, "onGranted: permissions=$deniedList, allGranted=false")
                                SettingUtils.enableAppNotify = false
                                sbEnableAppNotify.isChecked = false
                                XToastUtils.error(R.string.tips_notification_listener)
                                return
                            }
                            // Logic of successful processing of requests for authority
                            SettingUtils.enableAppNotify = true
                            sbEnableAppNotify.isChecked = true
                            CommonUtils.toggleNotificationListenerService(requireContext())
                        }
                    })
            }
        }
        val isEnable = SettingUtils.enableAppNotify
        sbEnableAppNotify.isChecked = isEnable
        binding!!.layoutOptionalAction.visibility = View.VISIBLE

        scbCancelAppNotify.isChecked = SettingUtils.enableCancelAppNotify
        scbCancelAppNotify.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableCancelAppNotify = isChecked
        }
        scbNotUserPresent.isChecked = SettingUtils.enableNotUserPresent
        scbNotUserPresent.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableNotUserPresent = isChecked
        }
    }

    //Bluetooth discovery service
    private fun switchEnableBluetooth(@SuppressLint("UseSwitchCompatOrMaterialCode") sbEnableBluetooth: SwitchButton, layoutBluetoothSetting: LinearLayout, xsbScanInterval: XSeekBar, scbIgnoreAnonymous: SmoothCheckBox) {
        sbEnableBluetooth.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableBluetooth = isChecked
            layoutBluetoothSetting.visibility = View.VISIBLE
            if (isChecked) {
                XXPermissions.with(this)
                    .permission(PermissionLists.getBluetoothScanPermission())
                    .permission(PermissionLists.getBluetoothConnectPermission())
                    .permission(PermissionLists.getBluetoothAdvertisePermission())
                    .permission(PermissionLists.getAccessFineLocationPermission())
                    .request(object : OnPermissionCallback {
                        override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                            val allGranted = deniedList.isEmpty()
                            if (!allGranted) {
                                // Check whether the user selected Never ask again for denied permissions
                                val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                                if (doNotAskAgain) {
                                    XToastUtils.error(R.string.toast_denied_never)
                                    XXPermissions.startPermissionActivity(requireContext(), deniedList)
                                }
                                // Logic of failure in processing requests for permission
                                XToastUtils.warning(getString(R.string.enable_bluetooth) + ": " + getString(R.string.toast_granted_part))
                                SettingUtils.enableBluetooth = false
                                sbEnableBluetooth.isChecked = false
                                restartBluetoothService(ACTION_STOP)
                                return
                            }
                            restartBluetoothService(ACTION_START)
                        }
                    })
            } else {
                restartBluetoothService(ACTION_STOP)
            }
        }
        val isEnable = SettingUtils.enableBluetooth
        sbEnableBluetooth.isChecked = isEnable
        layoutBluetoothSetting.visibility = View.VISIBLE

        //Scan Bluetooth device interval
        xsbScanInterval.setDefaultValue((SettingUtils.bluetoothScanInterval / 1000).toInt())
        xsbScanInterval.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            if (newValue * 1000L != SettingUtils.bluetoothScanInterval) {
                SettingUtils.bluetoothScanInterval = newValue * 1000L
                restartBluetoothService()
            }
        }

        //Whether to ignore anonymous devices
        scbIgnoreAnonymous.isChecked = SettingUtils.bluetoothIgnoreAnonymous
        scbIgnoreAnonymous.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.bluetoothIgnoreAnonymous = isChecked
            restartBluetoothService()
        }

    }

    //Restart Bluetooth Scan Service
    private fun restartBluetoothService(action: String = ACTION_RESTART) {
        if (!initViewsFinished) return
        if (!SettingUtils.enableBluetooth && action == ACTION_RESTART) return
        Log.d(TAG, "restartBluetoothService, action: $action")
        val serviceIntent = Intent(requireContext(), BluetoothScanService::class.java)
        //If Bluetooth is enabled, but the system bluetooth is not available, turn it off.
        if (SettingUtils.enableBluetooth && (!BluetoothUtils.isBluetoothEnabled() || !BluetoothUtils.hasBluetoothCapability(App.context))) {
            XToastUtils.error(getString(R.string.toast_bluetooth_not_enabled))
            SettingUtils.enableBluetooth = false
            binding!!.sbEnableBluetooth.isChecked = false
            binding!!.layoutBluetoothSetting.visibility = View.GONE
            serviceIntent.action = ACTION_STOP
        } else {
            serviceIntent.action = action
        }
        requireContext().startService(serviceIntent)
    }

    //GPSPositioning services
    private fun switchEnableLocation(@SuppressLint("UseSwitchCompatOrMaterialCode") sbEnableLocation: SwitchButton, layoutLocationSetting: LinearLayout, rgAccuracy: RadioGroup, rgPowerRequirement: RadioGroup, xsbMinInterval: XSeekBar, xsbMinDistance: XSeekBar) {
        sbEnableLocation.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableLocation = isChecked
            layoutLocationSetting.visibility = View.VISIBLE
            if (isChecked) {
                XXPermissions.with(this)
                    .permission(PermissionLists.getAccessCoarseLocationPermission())
                    .permission(PermissionLists.getAccessFineLocationPermission())
                    .permission(PermissionLists.getAccessBackgroundLocationPermission())
                    .request(object : OnPermissionCallback {
                        override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                            val allGranted = deniedList.isEmpty()
                            if (!allGranted) {
                                // Check whether the user selected Never ask again for denied permissions
                                val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                                if (doNotAskAgain) {
                                    XToastUtils.error(getString(R.string.enable_location) + ": " + getString(R.string.toast_denied_never))
                                    XXPermissions.startPermissionActivity(requireContext(), deniedList)
                                }
                                // Logic of failure in processing requests for permission
                                XToastUtils.error(getString(R.string.enable_location) + ": " + getString(R.string.toast_denied))
                                SettingUtils.enableLocation = false
                                sbEnableLocation.isChecked = false
                                restartLocationService(ACTION_STOP)
                                return
                            }
                            restartLocationService(ACTION_START)
                        }
                    })
            } else {
                restartLocationService(ACTION_STOP)
            }
        }
        val isEnable = SettingUtils.enableLocation
        sbEnableLocation.isChecked = isEnable
        layoutLocationSetting.visibility = View.VISIBLE

        //Set position accuracy: high precision (default)）
        rgAccuracy.check(
            when (SettingUtils.locationAccuracy) {
                Criteria.ACCURACY_FINE -> R.id.rb_accuracy_fine
                Criteria.ACCURACY_COARSE -> R.id.rb_accuracy_coarse
                Criteria.NO_REQUIREMENT -> R.id.rb_accuracy_no_requirement
                else -> R.id.rb_accuracy_fine
            }
        )
        rgAccuracy.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            SettingUtils.locationAccuracy = when (checkedId) {
                R.id.rb_accuracy_fine -> Criteria.ACCURACY_FINE
                R.id.rb_accuracy_coarse -> Criteria.ACCURACY_COARSE
                R.id.rb_accuracy_no_requirement -> Criteria.NO_REQUIREMENT
                else -> Criteria.ACCURACY_FINE
            }
            restartLocationService()
        }

        //Set electricity consumption: low electricity consumption (default)）
        rgPowerRequirement.check(
            when (SettingUtils.locationPowerRequirement) {
                Criteria.POWER_HIGH -> R.id.rb_power_requirement_high
                Criteria.POWER_MEDIUM -> R.id.rb_power_requirement_medium
                Criteria.POWER_LOW -> R.id.rb_power_requirement_low
                Criteria.NO_REQUIREMENT -> R.id.rb_power_requirement_no_requirement
                else -> R.id.rb_power_requirement_low
            }
        )
        rgPowerRequirement.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            SettingUtils.locationPowerRequirement = when (checkedId) {
                R.id.rb_power_requirement_high -> Criteria.POWER_HIGH
                R.id.rb_power_requirement_medium -> Criteria.POWER_MEDIUM
                R.id.rb_power_requirement_low -> Criteria.POWER_LOW
                R.id.rb_power_requirement_no_requirement -> Criteria.NO_REQUIREMENT
                else -> Criteria.POWER_LOW
            }
            restartLocationService()
        }

        //Set the minimum time interval (in milliseconds); default interval：10000milliseconds, minimum interval：1000milliseconds
        xsbMinInterval.setDefaultValue((SettingUtils.locationMinInterval / 1000).toInt())
        xsbMinInterval.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            if (newValue * 1000L != SettingUtils.locationMinInterval) {
                SettingUtils.locationMinInterval = newValue * 1000L
                restartLocationService()
            }
        }

        //Set position to update minimum distance (m); default distance：0meters
        xsbMinDistance.setDefaultValue(SettingUtils.locationMinDistance)
        xsbMinDistance.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            if (newValue != SettingUtils.locationMinDistance) {
                SettingUtils.locationMinDistance = newValue
                restartLocationService()
            }
        }
    }

    //Restart location service
    private fun restartLocationService(action: String = ACTION_RESTART) {
        if (!initViewsFinished) return
        if (!SettingUtils.enableLocation && action == ACTION_RESTART) return
        Log.d(TAG, "restartLocationService, action: $action")
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        //If the locator function is enabled, but the system locator function is not available, turn it off.
        if (SettingUtils.enableLocation && (!LocationUtils.isLocationEnabled(App.context) || !LocationUtils.hasLocationCapability(App.context))) {
            XToastUtils.error(getString(R.string.toast_location_not_enabled))
            SettingUtils.enableLocation = false
            binding!!.sbEnableLocation.isChecked = false
            binding!!.layoutLocationSetting.visibility = View.GONE
            serviceIntent.action = ACTION_STOP
        } else {
            serviceIntent.action = action
        }
        requireContext().startService(serviceIntent)
    }

    //Accept SMS Command
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun switchEnableSmsCommand(sbEnableSmsCommand: SwitchButton, etSafePhone: EditText) {
        sbEnableSmsCommand.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableSmsCommand = isChecked
            etSafePhone.visibility = View.VISIBLE
            if (isChecked) {
                XXPermissions.with(this)
                    // System Settings
                    .permission(PermissionLists.getWriteSettingsPermission())
                    // Receive SMS
                    .permission(PermissionLists.getReceiveSmsPermission())
                    // Send SMS
                    .permission(PermissionLists.getSendSmsPermission())
                    // Read Text Messages
                    .permission(PermissionLists.getReadSmsPermission())
                    .request(object : OnPermissionCallback {
                        override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                            val allGranted = deniedList.isEmpty()
                            if (!allGranted) {
                                // Check whether the user selected Never ask again for denied permissions
                                val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                                if (doNotAskAgain) {
                                    XToastUtils.error(R.string.toast_denied_never)
                                    XXPermissions.startPermissionActivity(requireContext(), deniedList)
                                }
                                // Logic of failure in processing requests for permission
                                XToastUtils.error(getString(R.string.sms_command) + ": " + getString(R.string.toast_denied))
                                SettingUtils.enableSmsCommand = false
                                sbEnableSmsCommand.isChecked = false
                                return
                            }
                        }
                    })
            }
        }
        val isEnable = SettingUtils.enableSmsCommand
        sbEnableSmsCommand.isChecked = isEnable
        etSafePhone.visibility = View.VISIBLE

        etSafePhone.setText(SettingUtils.smsCommandSafePhone)
        etSafePhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.smsCommandSafePhone = etSafePhone.text.toString().trim().removeSuffix("\n")
            }
        })
    }

    //Turn off screen near the earpiece
    private fun switchEnableCloseToEarpieceTurnOffScreen(
        layoutEnableCloseToEarpieceTurnOffScreen: View,
        sbEnableCloseToEarpieceTurnOffScreen: SwitchButton
    ) {
        if (!ProximitySensorScreenHelper.isEnable()) {
            layoutEnableCloseToEarpieceTurnOffScreen.visibility = View.GONE
            return
        }
        sbEnableCloseToEarpieceTurnOffScreen.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableCloseToEarpieceTurnOffScreen = isChecked
            ProximitySensorScreenHelper.refresh(requireContext().applicationContext)
        }
        sbEnableCloseToEarpieceTurnOffScreen.isChecked =
            SettingUtils.enableCloseToEarpieceTurnOffScreen
    }

    //Setup to automatically eliminate additionalAPPNotifications
    private fun editExtraAppList(textAppList: EditText) {
        textAppList.setText(SettingUtils.cancelExtraAppNotify)
        textAppList.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.cancelExtraAppNotify = textAppList.text.toString().trim().replace("\r", "").replace("\n+", "\n").removeSuffix("\n")
            }
        })
    }

    //SettingsAPPNotification key word blacklist
    private fun editAppNotifyBlacklist(textBlacklist: EditText) {
        textBlacklist.setText(SettingUtils.appNotifyBlacklist)
        textBlacklist.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.appNotifyBlacklist = textBlacklist.text.toString().trim().replace("\r", "").replace("\n+", "\n").removeSuffix("\n")
            }
        })
    }

    //Load installed applications asynchronously on startupAppInformation
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun switchEnableLoadAppList(sbEnableLoadAppList: SwitchButton, scbLoadUserApp: SmoothCheckBox, scbLoadSystemApp: SmoothCheckBox) {
        val isEnable: Boolean = SettingUtils.enableLoadAppList
        sbEnableLoadAppList.isChecked = isEnable

        sbEnableLoadAppList.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            // The master switch always turns on; with no app type selected nothing is loaded.
            SettingUtils.enableLoadAppList = isChecked
            if (isChecked && !SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList) {
                XToastUtils.info(getString(R.string.load_app_list_toast))
            } else if (isChecked) {
                XToastUtils.info(getString(R.string.loading_app_list))
                val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
                WorkManager.getInstance(XUtil.getContext()).enqueue(request)
            }
        }
        scbLoadUserApp.isChecked = SettingUtils.enableLoadUserAppList
        scbLoadUserApp.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableLoadUserAppList = isChecked
            if (!isChecked && SettingUtils.enableLoadAppList && !SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList) {
                XToastUtils.info(getString(R.string.load_app_list_toast))
            }
            if (isChecked && SettingUtils.enableLoadAppList && App.UserAppList.isEmpty()) {
                XToastUtils.info(getString(R.string.loading_app_list))
                val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
                WorkManager.getInstance(XUtil.getContext()).enqueue(request)
            }
        }
        scbLoadSystemApp.isChecked = SettingUtils.enableLoadSystemAppList
        scbLoadSystemApp.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableLoadSystemAppList = isChecked
            if (!isChecked && SettingUtils.enableLoadAppList && !SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList) {
                XToastUtils.info(getString(R.string.load_app_list_toast))
            }
            if (isChecked && SettingUtils.enableLoadAppList && App.SystemAppList.isEmpty()) {
                XToastUtils.info(getString(R.string.loading_app_list))
                val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
                WorkManager.getInstance(XUtil.getContext()).enqueue(request)
            }
        }
    }

    //Start on boot
    private fun checkWithReboot(@SuppressLint("UseSwitchCompatOrMaterialCode") sbWithReboot: SwitchButton, tvAutoStartup: TextView) {
        tvAutoStartup.text = getAutoStartTips()

        //Get Component
        val cm = ComponentName(getAppPackageName(), BootCompletedReceiver::class.java.name)
        val pm: PackageManager = getPackageManager()
        val state = pm.getComponentEnabledSetting(cm)
        sbWithReboot.isChecked = !(state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
        sbWithReboot.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            try {
                val newState = if (isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                pm.setComponentEnabledSetting(cm, newState, PackageManager.DONT_KILL_APP)
                if (isChecked) startToAutoStartSetting(requireContext())
            } catch (e: Exception) {
                XToastUtils.error(e.message.toString())
            }
        }
    }

    //Battery optimization settings
    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("UseSwitchCompatOrMaterialCode", "ObsoleteSdkInt")
    private fun batterySetting(layoutBatterySetting: LinearLayout, sbBatterySetting: SwitchButton) {
        //Android6.0and earlier do not support ignoring battery optimization
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            layoutBatterySetting.visibility = View.GONE
            return
        }

        try {
            val isIgnoreBatteryOptimization: Boolean = KeepAliveUtils.isIgnoreBatteryOptimization(requireActivity())
            sbBatterySetting.isChecked = isIgnoreBatteryOptimization
            sbBatterySetting.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked && !isIgnoreBatteryOptimization) {
                    KeepAliveUtils.ignoreBatteryOptimization(requireActivity())
                } else if (isChecked) {
                    XToastUtils.info(R.string.isIgnored)
                    sbBatterySetting.isChecked = true
                } else {
                    XToastUtils.info(R.string.isIgnored2)
                    sbBatterySetting.isChecked = isIgnoreBatteryOptimization
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    //Hide from recent tasks
    @SuppressLint("ObsoleteSdkInt,UseSwitchCompatOrMaterialCode")
    private fun switchExcludeFromRecents(layoutExcludeFromRecents: LinearLayout, sbExcludeFromRecents: SwitchButton) {
        //Android6.0and earlier do not support hiding from recent tasks
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            layoutExcludeFromRecents.visibility = View.GONE
            return
        }
        sbExcludeFromRecents.isChecked = SettingUtils.enableExcludeFromRecents
        sbExcludeFromRecents.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableExcludeFromRecents = isChecked
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val am = App.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.let {
                    val tasks = it.appTasks
                    if (!tasks.isNullOrEmpty()) {
                        tasks[0].setExcludeFromRecents(true)
                    }
                }
            }
        }
    }

    //CactusEnhanced keep-alive mechanisms
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun switchEnableCactus(sbEnableCactus: SwitchButton, scbPlaySilenceMusic: SmoothCheckBox, scbOnePixelActivity: SmoothCheckBox, layoutMusicInterval: LinearLayout, xsbMusicInterval: XSeekBar) {
        val layoutCactusOptional: LinearLayout = binding!!.layoutCactusOptional
        val isEnable: Boolean = SettingUtils.enableCactus
        sbEnableCactus.isChecked = isEnable
        layoutCactusOptional.visibility = View.VISIBLE
        layoutMusicInterval.visibility = View.VISIBLE

        sbEnableCactus.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            layoutCactusOptional.visibility = View.VISIBLE
            layoutMusicInterval.visibility = View.VISIBLE
            SettingUtils.enableCactus = isChecked
            XToastUtils.warning(getString(R.string.need_to_restart))
        }

        scbPlaySilenceMusic.isChecked = SettingUtils.enablePlaySilenceMusic
        scbPlaySilenceMusic.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enablePlaySilenceMusic = isChecked
            layoutMusicInterval.visibility = View.VISIBLE
            XToastUtils.warning(getString(R.string.need_to_restart))
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            binding!!.layoutOnePixelActivity.visibility = View.VISIBLE
        }
        scbOnePixelActivity.isChecked = SettingUtils.enableOnePixelActivity
        scbOnePixelActivity.setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
            SettingUtils.enableOnePixelActivity = isChecked
            XToastUtils.warning(getString(R.string.need_to_restart))
        }

        xsbMusicInterval.setDefaultValue(SettingUtils.musicInterval)
        xsbMusicInterval.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            if (newValue != SettingUtils.musicInterval) {
                SettingUtils.musicInterval = newValue
                XToastUtils.warning(getString(R.string.need_to_restart))
            }
        }
    }

    //Interface request failed to retry time interval
    private fun editRetryDelayTime(xsbRetryTimes: XSeekBar, xsbDelayTime: XSeekBar, xsbTimeout: XSeekBar) {
        xsbRetryTimes.setDefaultValue(SettingUtils.requestRetryTimes)
        xsbRetryTimes.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            SettingUtils.requestRetryTimes = newValue
            binding!!.layoutDelayTime.visibility = View.VISIBLE
        }
        xsbDelayTime.setDefaultValue(SettingUtils.requestDelayTime)
        xsbDelayTime.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            SettingUtils.requestDelayTime = newValue
        }
        xsbTimeout.setDefaultValue(SettingUtils.requestTimeout)
        xsbTimeout.setOnSeekBarListener { _: XSeekBar?, newValue: Int ->
            SettingUtils.requestTimeout = newValue
        }
    }

    //Set Device Name
    private fun editAddExtraDeviceMark(etExtraDeviceMark: EditText) {
        etExtraDeviceMark.setText(SettingUtils.extraDeviceMark)
        etExtraDeviceMark.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.extraDeviceMark = etExtraDeviceMark.text.toString().trim()
            }
        })
    }

    //SettingsSIM1Primary Key
    private fun editAddSubidSim1(etSubidSim1: EditText) {
        etSubidSim1.setText("${SettingUtils.subidSim1}")
        etSubidSim1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val v = etSubidSim1.text.toString()
                SettingUtils.subidSim1 = if (!TextUtils.isEmpty(v)) {
                    v.toIntOrNull() ?: return
                } else {
                    1
                }
            }
        })
    }

    //SettingsSIM2Primary Key
    private fun editAddSubidSim2(etSubidSim2: EditText) {
        etSubidSim2.setText("${SettingUtils.subidSim2}")
        etSubidSim2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val v = etSubidSim2.text.toString()
                SettingUtils.subidSim2 = if (!TextUtils.isEmpty(v)) {
                    v.toIntOrNull() ?: return
                } else {
                    2
                }
            }
        })
    }

    //SettingsSIM1Remarks
    private fun editAddExtraSim1(etExtraSim1: EditText) {
        etExtraSim1.setText(SettingUtils.extraSim1)
        etExtraSim1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.extraSim1 = etExtraSim1.text.toString().trim()
            }
        })
    }

    //SettingsSIM2Remarks
    private fun editAddExtraSim2(etExtraSim2: EditText) {
        etExtraSim2.setText(SettingUtils.extraSim2)
        etExtraSim2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.extraSim2 = etExtraSim2.text.toString().trim()
            }
        })
    }

    //Set Notification Contents
    private fun editNotifyContent(etNotifyContent: EditText) {
        etNotifyContent.setText(SettingUtils.notifyContent)
        etNotifyContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val notifyContent = etNotifyContent.text.toString().trim()
                SettingUtils.notifyContent = notifyContent
                val updateIntent = Intent(context, ForegroundService::class.java)
                updateIntent.action = ACTION_UPDATE_NOTIFICATION
                updateIntent.putExtra(EXTRA_UPDATE_NOTIFICATION, notifyContent)
                context?.let { ContextCompat.startForegroundService(it, updateIntent) }
            }
        })
    }

    //Enable custom templates when setting forward
    @SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
    private fun switchSmsTemplate(sbSmsTemplate: SwitchButton) {
        val isOn: Boolean = SettingUtils.enableSmsTemplate
        sbSmsTemplate.isChecked = isOn
        val layoutSmsTemplate: LinearLayout = binding!!.layoutSmsTemplate
        layoutSmsTemplate.visibility = if (isOn) View.VISIBLE else View.GONE
        val etSmsTemplate: EditText = binding!!.etSmsTemplate
        sbSmsTemplate.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            layoutSmsTemplate.visibility = if (isChecked) View.VISIBLE else View.GONE
            SettingUtils.enableSmsTemplate = isChecked
            if (!isChecked) {
                etSmsTemplate.setText(
                    """
                    ${getString(R.string.tag_from)}
                    ${getString(R.string.tag_sms)}
                    ${getString(R.string.tag_card_slot)}
                    SubId：${getString(R.string.tag_card_subid)}
                    ${getString(R.string.tag_receive_time)}
                    ${getString(R.string.tag_device_name)}
                    """.trimIndent()
                )
            }
        }
    }

    //Set up message template for forwarding
    private fun editSmsTemplate(textSmsTemplate: EditText) {
        //Create Tab Button
        CommonUtils.createTagButtons(requireContext(), binding!!.glSmsTemplate, textSmsTemplate, "all")
        textSmsTemplate.setText(SettingUtils.smsTemplate)
        textSmsTemplate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                SettingUtils.smsTemplate = textSmsTemplate.text.toString().trim()
            }
        })
    }

    //Pure client mode
    private fun switchDirectlyToClient(@SuppressLint("UseSwitchCompatOrMaterialCode") switchDirectlyToClient: SwitchButton) {
        switchDirectlyToClient.isChecked = SettingUtils.enablePureClientMode
        switchDirectlyToClient.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enablePureClientMode = isChecked
            if (isChecked) {
                MaterialDialog.Builder(requireContext()).content(getString(R.string.enabling_pure_client_mode)).positiveText(R.string.lab_yes).onPositive { _: MaterialDialog?, _: DialogAction? ->
                    XUtil.exitApp()
                }.negativeText(R.string.lab_no).show()
            }
        }
    }

    //Pure Auto Task Mode
    private fun switchDirectlyToTask(@SuppressLint("UseSwitchCompatOrMaterialCode") switchDirectlyToTask: SwitchButton) {
        switchDirectlyToTask.isChecked = SettingUtils.enablePureTaskMode
        switchDirectlyToTask.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enablePureTaskMode = isChecked
            if (isChecked) {
                MaterialDialog.Builder(requireContext()).content(getString(R.string.enabling_pure_client_mode)).positiveText(R.string.lab_yes).onPositive { _: MaterialDialog?, _: DialogAction? ->
                    XUtil.exitApp()
                }.negativeText(R.string.lab_no).show()
            }
        }
    }

    //Debug Mode
    private fun switchDebugMode(@SuppressLint("UseSwitchCompatOrMaterialCode") switchDebugMode: SwitchButton) {
        switchDebugMode.isChecked = SettingUtils.enableDebugMode
        switchDebugMode.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SettingUtils.enableDebugMode = isChecked
            App.isDebug = isChecked
        }
    }

    //Multilingual Settings
    private fun switchLanguage(rgMainLanguages: RadioGroup) {
        val context = App.context
        rgMainLanguages.check(
            if (MultiLanguages.isSystemLanguage(context)) {
                R.id.rb_main_language_auto
            } else {
                when (MultiLanguages.getAppLanguage(context)) {
                    Locale("nl") -> R.id.rb_main_language_nl
                    LocaleContract.getEnglishLocale() -> R.id.rb_main_language_en
                    else -> R.id.rb_main_language_auto
                }
            }
        )

        rgMainLanguages.setOnCheckedChangeListener { _, checkedId ->
            val oldLang = MultiLanguages.getAppLanguage(context)
            var newLang = MultiLanguages.getSystemLanguage(context)
            //SettingUtils.isFlowSystemLanguage = false
            when (checkedId) {
                R.id.rb_main_language_auto -> {
                    // Just to trigger it.onAppLocaleChange
                    MultiLanguages.setAppLanguage(context, newLang)
                    // SettingUtils.isFlowSystemLanguage = true
                    // Follow the system
                    MultiLanguages.clearAppLanguage(context)
                }

                R.id.rb_main_language_nl -> {
                    // Persist the explicit Dutch selection across restarts.
                    newLang = Locale("nl")
                    MultiLanguages.setAppLanguage(context, newLang)
                }

                R.id.rb_main_language_en -> {
                    // English
                    newLang = LocaleContract.getEnglishLocale()
                    MultiLanguages.setAppLanguage(context, newLang)
                }
            }

            // Restart Application
            Log.d(TAG, "oldLang: $oldLang, newLang: $newLang")
            if (oldLang.toString() != newLang.toString()) {
                //CommonUtils.switchLanguage(oldLang, newLang)
                XToastUtils.toast(R.string.multi_languages_toast)
                //Restart After Switching LanguageAPP
                Thread.sleep(200)
                val intent = Intent(App.context, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }

    //Retrieving current cell phone brand
    private fun getAutoStartTips(): String {
        return when (Build.BRAND.lowercase(Locale.ROOT)) {
            "huawei" -> getString(R.string.auto_start_huawei)
            "honor" -> getString(R.string.auto_start_honor)
            "xiaomi" -> getString(R.string.auto_start_xiaomi)
            "redmi" -> getString(R.string.auto_start_redmi)
            "oppo" -> getString(R.string.auto_start_oppo)
            "vivo" -> getString(R.string.auto_start_vivo)
            "meizu" -> getString(R.string.auto_start_meizu)
            "samsung" -> getString(R.string.auto_start_samsung)
            "letv" -> getString(R.string.auto_start_letv)
            "smartisan" -> getString(R.string.auto_start_smartisan)
            else -> getString(R.string.auto_start_unknown)
        }
    }

    //IntentJump to[Start-up]Device-specific startup settings
    private val hashMap = object : HashMap<String?, List<String?>?>() {
        init {
            put(
                "Xiaomi", listOf(
                    "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",  //MIUI10_9.8.1(9.0)
                    "com.miui.securitycenter"
                )
            )
            put(
                "samsung", listOf(
                    "com.samsung.android.sm_cn/com.samsung.android.sm.ui.ram.AutoRunActivity", "com.samsung.android.sm_cn/com.samsung.android.sm.ui.appmanagement.AppManagementActivity", "com.samsung.android.sm_cn/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity", "com.samsung.android.sm_cn/.ui.ram.RamActivity", "com.samsung.android.sm_cn/.app.dashboard.SmartManagerDashBoardActivity", "com.samsung.android.sm/com.samsung.android.sm.ui.ram.AutoRunActivity", "com.samsung.android.sm/com.samsung.android.sm.ui.appmanagement.AppManagementActivity", "com.samsung.android.sm/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity", "com.samsung.android.sm/.ui.ram.RamActivity", "com.samsung.android.sm/.app.dashboard.SmartManagerDashBoardActivity", "com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity", "com.samsung.android.sm_cn", "com.samsung.android.sm"
                )
            )
            put(
                "HUAWEI", listOf(
                    "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",  //EMUI9.1.0(Ark,9.0)
                    "com.huawei.systemmanager/.appcontrol.activity.StartupAppControlActivity", "com.huawei.systemmanager/.optimize.process.ProtectActivity", "com.huawei.systemmanager/.optimize.bootstart.BootStartActivity", "com.huawei.systemmanager" //The last line can write the name of the package, So if the signature class path is in some new versionROMif not found, open the corresponding security center/phone manager home page.
                )
            )
            put(
                "vivo", listOf(
                    "com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager", "com.iqoo.secure/.safeguard.PurviewTabActivity", "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity",  //"com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity", //It's a white list., Not self-start
                    "com.iqoo.secure", "com.vivo.permissionmanager"
                )
            )
            put(
                "Meizu", listOf(
                    "com.meizu.safe/.permission.SmartBGActivity",  //Flyme7.3.0(7.1.2)
                    "com.meizu.safe/.permission.PermissionMainActivity",  //On the Internet.
                    "com.meizu.safe"
                )
            )
            put(
                "OPPO", listOf(
                    "com.coloros.safecenter/.startupapp.StartupAppListActivity", "com.coloros.safecenter/.permission.startup.StartupAppListActivity", "com.oppo.safe/.permission.startup.StartupAppListActivity", "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity", "com.coloros.safecenter/com.coloros.privacypermissionsentry.PermissionTopActivity", "com.coloros.safecenter", "com.oppo.safe", "com.coloros.oppoguardelf"
                )
            )
            put(
                "oneplus", listOf(
                    "com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity", "com.oneplus.security"
                )
            )
            put(
                "letv", listOf(
                    "com.letv.android.letvsafe/.AutobootManageActivity", "com.letv.android.letvsafe/.BackgroundAppManageActivity",  //Apply protection
                    "com.letv.android.letvsafe"
                )
            )
            put(
                "zte", listOf(
                    "com.zte.heartyservice/.autorun.AppAutoRunManager", "com.zte.heartyservice"
                )
            )

            //Gionee
            put(
                "F", listOf(
                    "com.gionee.softmanager/.MainActivity", "com.gionee.softmanager"
                )
            )

            //Undetermined below(I don't know the name of the manufacturer.)
            put(
                "smartisanos", listOf(
                    "com.smartisanos.security/.invokeHistory.InvokeHistoryActivity", "com.smartisanos.security"
                )
            )

            //360
            put(
                "360", listOf(
                    "com.yulong.android.coolsafe/.ui.activity.autorun.AutoRunListActivity", "com.yulong.android.coolsafe"
                )
            )

            //360
            put(
                "ulong", listOf(
                    "com.yulong.android.coolsafe/.ui.activity.autorun.AutoRunListActivity", "com.yulong.android.coolsafe"
                )
            )

            //Coolpad
            put(
                "coolpad" /*The manufacturer's name is not certain.*/, listOf(
                    "com.yulong.android.security/com.yulong.android.seccenter.tabbarmain", "com.yulong.android.security"
                )
            )

            //Lenovo
            put(
                "lenovo" /*The manufacturer's name is not certain.*/, listOf(
                    "com.lenovo.security/.purebackground.PureBackgroundActivity", "com.lenovo.security"
                )
            )
            put(
                "htc" /*The manufacturer's name is not certain.*/, listOf(
                    "com.htc.pitroad/.landingpage.activity.LandingPageActivity", "com.htc.pitroad"
                )
            )

            //ASUS
            put(
                "asus" /*The manufacturer's name is not certain.*/, listOf(
                    "com.asus.mobilemanager/.MainActivity", "com.asus.mobilemanager"
                )
            )
        }
    }

    //Open auto-start settings
    private fun startToAutoStartSetting(context: Context) {
        Log.e("Util", "******************The current phone model is:" + Build.MANUFACTURER)
        val entries: MutableSet<MutableMap.MutableEntry<String?, List<String?>?>> = hashMap.entries
        var has = false
        for ((manufacturer, actCompatList) in entries) {
            if (Build.MANUFACTURER.equals(manufacturer, ignoreCase = true)) {
                if (actCompatList != null) {
                    for (act in actCompatList) {
                        try {
                            var intent: Intent?
                            if (act?.contains("/") == true) {
                                intent = Intent()
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val componentName = ComponentName.unflattenFromString(act)
                                intent.component = componentName
                            } else {
                                //Not found? The online approach is to jump to settings.... It's basically meaningless, basically self-starting this function is the kind of safety housekeeper that third-party manufacturers write themselves.app
                                //That's why I jumped directly to the corresponding security housekeeper./Security Centre
                                intent = act?.let { context.packageManager.getLaunchIntentForPackage(it) }
                            }
                            context.startActivity(intent)
                            has = true
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.e("Util", "******************e:" + e.message)
                        }
                    }
                }
            }
        }
        if (!has) {
            XToastUtils.info(R.string.tips_compatible_solution)
            try {
                val intent = Intent()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                intent.data = Uri.fromParts("package", context.packageName, null)
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("Util", "******************e:" + e.message)
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        LiveEventBus.get(EVENT_LOAD_APP_LIST, String::class.java).removeObserver(appListObserver)
        settingsNavigation = null
        initViewsFinished = false
        super.onDestroyView()
    }

    //InitializeAPPDrawdown List
    private fun initAppSpinner() {

        //When asynchronous installed-app loading is disabledAppdo not show installed apps when information loading is disabledAPPdropdown
        if (!SettingUtils.enableLoadAppList || (!SettingUtils.enableLoadUserAppList && !SettingUtils.enableLoadSystemAppList)) return

        if (App.UserAppList.isEmpty() && App.SystemAppList.isEmpty()) {
            //XToastUtils.info(getString(R.string.loading_app_list))
            val request = OneTimeWorkRequestBuilder<LoadAppListWorker>().build()
            WorkManager.getInstance(XUtil.getContext()).enqueue(request)
            return
        }

        appListSpinnerList.clear()
        if (SettingUtils.enableLoadUserAppList) {
            for (appInfo in App.UserAppList) {
                if (TextUtils.isEmpty(appInfo.packageName)) continue
                appListSpinnerList.add(AppListAdapterItem(appInfo.name, appInfo.icon, appInfo.packageName))
            }
        }
        if (SettingUtils.enableLoadSystemAppList) {
            for (appInfo in App.SystemAppList) {
                if (TextUtils.isEmpty(appInfo.packageName)) continue
                appListSpinnerList.add(AppListAdapterItem(appInfo.name, appInfo.icon, appInfo.packageName))
            }
        }

        //The list is empty and does not show drop-down frames
        if (appListSpinnerList.isEmpty()) return

        appListSpinnerAdapter = AppListSpinnerAdapter(appListSpinnerList).setIsFilterKey(true).setFilterColor(String.format("#%06X", com.google.android.material.color.MaterialColors.getColor(binding!!.root, com.google.android.material.R.attr.colorPrimary) and 0xFFFFFF)).setBackgroundSelector(R.drawable.selector_custom_spinner_bg)
        binding!!.spApp.setAdapter(appListSpinnerAdapter)
        binding!!.spApp.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
            try {
                val appInfo = appListSpinnerAdapter.getItemSource(position) as AppListAdapterItem
                CommonUtils.insertOrReplaceText2Cursor(binding!!.etAppList, appInfo.packageName.toString() + "\n")
            } catch (e: Exception) {
                XToastUtils.error(e.message.toString())
            }
        }
        binding!!.layoutSpApp.visibility = View.VISIBLE

    }

}
