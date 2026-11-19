package cn.ppps.forwarder.fragment.condition

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentTasksConditionToAddressBinding
import cn.ppps.forwarder.service.LocationService
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.KEY_BACK_DATA_CONDITION
import cn.ppps.forwarder.utils.KEY_BACK_DESCRIPTION_CONDITION
import cn.ppps.forwarder.utils.KEY_EVENT_DATA_CONDITION
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.TASK_CONDITION_TO_ADDRESS
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import cn.ppps.forwarder.widget.material.TitleBar
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog

@Page(name = "ToAddress")
@Suppress("PrivatePropertyName", "SameParameterValue")
class ToAddressFragment : BaseFragment<FragmentTasksConditionToAddressBinding?>(), View.OnClickListener {

    private val TAG: String = ToAddressFragment::class.java.simpleName
    private var titleBar: TitleBar? = null

    @JvmField
    @AutoWired(name = KEY_EVENT_DATA_CONDITION)
    var eventData: String? = null

    /** View-owned form logic; validation runs only on Apply. */
    var editor: LocationConditionEditor? = null

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentTasksConditionToAddressBinding {
        return FragmentTasksConditionToAddressBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.task_to_address)
        return titleBar
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        val b = binding!!
        editor = LocationConditionEditor(
            "to", eventData, getString(R.string.task_to_address_tips), getString(R.string.to_address_distance_description),
            b.rgCalcType, b.rbCalcTypeDistance, b.rbCalcTypeAddress, b.layoutCalcTypeDistance, b.layoutCalcTypeAddress,
            b.conditionFormLocationLegacyNotice, b.etLongitude, b.etLatitude, b.etDistance, b.etAddress, b.tvDescription,
        ).also { it.bind() }
    }

    override fun onDestroyView() {
        editor = null
        super.onDestroyView()
    }

    @SuppressLint("SetTextI18n")
    override fun initListeners() {
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
        binding!!.btnCurrentCoordinates.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_current_coordinates -> {
                    if (!App.LocationClient.isStarted()) {
                        MaterialDialog.Builder(requireContext())
                            .iconRes(R.drawable.auto_task_icon_location)
                            .title(R.string.enable_location)
                            .content(R.string.enable_location_dialog)
                            .cancelable(false)
                            .positiveText(R.string.lab_yes)
                            .negativeText(R.string.lab_no)
                            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                                SettingUtils.enableLocation = true
                                val serviceIntent = Intent(requireContext(), LocationService::class.java)
                                serviceIntent.action = ACTION_START
                                requireContext().startService(serviceIntent)
                            }.show()
                        return
                    }

                    // 0/0 is a valid point; only the absence of an actual cached fix is a failure.
                    if (editor?.fillFrom(HttpServerUtils.apiLocationCache) != true) {
                        XToastUtils.error(getString(R.string.location_failed), 30000)
                        return
                    }
                    XToastUtils.success(getString(R.string.location_validation_filled))
                }

                R.id.btn_del -> {
                    popToBack()
                    return
                }

                R.id.btn_save -> {
                    val applied = editor?.apply() ?: return
                    val intent = Intent()
                    intent.putExtra(KEY_BACK_DESCRIPTION_CONDITION, applied.description)
                    intent.putExtra(KEY_BACK_DATA_CONDITION, applied.json)
                    setFragmentResult(TASK_CONDITION_TO_ADDRESS, intent)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString(), 30000)
            e.printStackTrace()
            Log.e(TAG, "onClick error:$e")
        }
    }
}
