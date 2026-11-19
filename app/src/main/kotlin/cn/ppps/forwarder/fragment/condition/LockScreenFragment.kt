package cn.ppps.forwarder.fragment.condition

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.Gson
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentTasksConditionLockScreenBinding
import cn.ppps.forwarder.entity.condition.LockScreenSetting
import cn.ppps.forwarder.utils.KEY_BACK_DATA_CONDITION
import cn.ppps.forwarder.utils.KEY_BACK_DESCRIPTION_CONDITION
import cn.ppps.forwarder.utils.KEY_EVENT_DATA_CONDITION
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.TASK_CONDITION_LOCK_SCREEN
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import cn.ppps.forwarder.widget.material.TitleBar

@Page(name = "LockScreen")
@Suppress("PrivatePropertyName", "SameParameterValue")
class LockScreenFragment : BaseFragment<FragmentTasksConditionLockScreenBinding?>(), View.OnClickListener {

    private val TAG: String = LockScreenFragment::class.java.simpleName
    private var titleBar: TitleBar? = null

    @JvmField
    @AutoWired(name = KEY_EVENT_DATA_CONDITION)
    var eventData: String? = null

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentTasksConditionLockScreenBinding {
        return FragmentTasksConditionLockScreenBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.task_lock_screen)
        return titleBar
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        binding!!.rgAction.setOnCheckedChangeListener { _, checkedId ->
            binding!!.xsbTimeAfterScreenOff.visibility = View.GONE
            binding!!.xsbTimeAfterScreenLocked.visibility = View.GONE
            binding!!.xsbTimeAfterScreenOn.visibility = View.GONE
            binding!!.xsbTimeAfterScreenUnlocked.visibility = View.GONE
            when (checkedId) {
                R.id.rb_action_screen_on -> binding!!.xsbTimeAfterScreenOn.visibility = View.VISIBLE
                R.id.rb_action_screen_unlocked -> binding!!.xsbTimeAfterScreenUnlocked.visibility = View.VISIBLE
                R.id.rb_action_screen_locked -> binding!!.xsbTimeAfterScreenLocked.visibility = View.VISIBLE
                else -> binding!!.xsbTimeAfterScreenOff.visibility = View.VISIBLE
            }
            syncDelayReadouts()
            checkSetting(true)
        }

        if (eventData != null) {
            val settingVo = Gson().fromJson(eventData, LockScreenSetting::class.java)
            binding!!.tvDescription.text = settingVo.description
            binding!!.xsbTimeAfterScreenOff.setDefaultValue(settingVo.timeAfterScreenOff)
            binding!!.xsbTimeAfterScreenOn.setDefaultValue(settingVo.timeAfterScreenOn)
            binding!!.xsbTimeAfterScreenLocked.setDefaultValue(settingVo.timeAfterScreenLocked)
            binding!!.xsbTimeAfterScreenUnlocked.setDefaultValue(settingVo.timeAfterScreenUnlocked)
            binding!!.rgAction.check(settingVo.getActionCheckId())
            binding!!.sbCheckAgain.isChecked = settingVo.checkAgain
        } else {
            binding!!.xsbTimeAfterScreenOff.setDefaultValue(0)
            binding!!.xsbTimeAfterScreenOn.setDefaultValue(0)
            binding!!.xsbTimeAfterScreenLocked.setDefaultValue(0)
            binding!!.xsbTimeAfterScreenUnlocked.setDefaultValue(0)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun initListeners() {
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
        binding!!.xsbTimeAfterScreenOff.setOnSeekBarListener { _, _ ->
            checkSetting(true)
        }
        binding!!.xsbTimeAfterScreenOn.setOnSeekBarListener { _, _ ->
            checkSetting(true)
        }
        binding!!.xsbTimeAfterScreenLocked.setOnSeekBarListener { _, _ ->
            checkSetting(true)
        }
        binding!!.xsbTimeAfterScreenUnlocked.setOnSeekBarListener { _, _ ->
            checkSetting(true)
        }
        binding!!.sbCheckAgain.setOnCheckedChangeListener { _, _ ->
            checkSetting(true)
        }
        // Visible value readouts. Added after setOnSeekBarListener (which clears change listeners); display only.
        listOf(binding!!.xsbTimeAfterScreenOff, binding!!.xsbTimeAfterScreenLocked, binding!!.xsbTimeAfterScreenOn, binding!!.xsbTimeAfterScreenUnlocked).forEach { slider ->
            slider.addOnChangeListener { _, _, _ -> syncDelayReadouts() }
        }
        syncDelayReadouts()
    }

    // Each readout shows its own slider's delay and follows that slider's visibility.
    private fun syncDelayReadouts() {
        val b = binding ?: return
        listOf(
            b.conditionFormScreenOffValue to b.xsbTimeAfterScreenOff,
            b.conditionFormScreenLockedValue to b.xsbTimeAfterScreenLocked,
            b.conditionFormScreenOnValue to b.xsbTimeAfterScreenOn,
            b.conditionFormScreenUnlockedValue to b.xsbTimeAfterScreenUnlocked,
        ).forEach { (readout, slider) ->
            val minutes = slider.selectedNumber
            readout.text = if (minutes > 0) getString(R.string.condition_form_delay_value, minutes) else getString(R.string.condition_form_delay_none)
            readout.visibility = slider.visibility
        }
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {

                R.id.btn_del -> {
                    popToBack()
                    return
                }

                R.id.btn_save -> {
                    val settingVo = checkSetting()
                    val intent = Intent()
                    intent.putExtra(KEY_BACK_DESCRIPTION_CONDITION, settingVo.description)
                    intent.putExtra(KEY_BACK_DATA_CONDITION, Gson().toJson(settingVo))
                    setFragmentResult(TASK_CONDITION_LOCK_SCREEN, intent)
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

    //Check Settings
    @SuppressLint("SetTextI18n")
    private fun checkSetting(updateView: Boolean = false): LockScreenSetting {
        val actionCheckId = binding!!.rgAction.checkedRadioButtonId
        val timeAfterScreenOff = binding!!.xsbTimeAfterScreenOff.selectedNumber
        val timeAfterScreenOn = binding!!.xsbTimeAfterScreenOn.selectedNumber
        val timeAferScreenLocked = binding!!.xsbTimeAfterScreenLocked.selectedNumber
        val timeAfterScreenUnlocked = binding!!.xsbTimeAfterScreenUnlocked.selectedNumber
        val checkAgain = binding!!.sbCheckAgain.isChecked
        val settingVo = LockScreenSetting(actionCheckId, timeAfterScreenOff, timeAfterScreenOn, timeAferScreenLocked, timeAfterScreenUnlocked, checkAgain)

        if (updateView) {
            binding!!.tvDescription.text = settingVo.description
        }

        return settingVo
    }
}