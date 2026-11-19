package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.TaskSettingAdapter
import cn.ppps.forwarder.adapter.WidgetItemAdapter
import cn.ppps.forwarder.adapter.base.ItemMoveCallback
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Task
import cn.ppps.forwarder.databinding.FragmentTasksEditBinding
import cn.ppps.forwarder.entity.TaskSetting
import cn.ppps.forwarder.entity.condition.CronSetting
import cn.ppps.forwarder.service.LocationService
import cn.ppps.forwarder.utils.*
import cn.ppps.forwarder.utils.task.CronJobScheduler
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xpage.base.XPageFragment
import com.xuexiang.xpage.core.PageOption
import com.xuexiang.xpage.enums.CoreAnim
import com.xuexiang.xpage.model.PageInfo
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.adapter.recyclerview.RecyclerViewHolder
import com.xuexiang.xui.utils.DensityUtils
import com.xuexiang.xui.utils.WidgetUtils
import cn.ppps.forwarder.widget.material.TitleBar
import com.google.android.material.textview.MaterialTextView as XUIAlphaTextView
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import gatewayapps.crondroid.CronExpression
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import java.util.*


@Page(name = "Automated task editor")
@Suppress("PrivatePropertyName", "DEPRECATION", "UNUSED_PARAMETER", "EmptyMethod", "NotifyDataSetChanged")
class TasksEditFragment : BaseFragment<FragmentTasksEditBinding?>(), View.OnClickListener, RecyclerViewHolder.OnItemClickListener<PageInfo> {

    private val TAG: String = TasksEditFragment::class.java.simpleName
    private val that = this
    private var titleBar: TitleBar? = null
    private val dialog: BottomSheetDialog by lazy { BottomSheetDialog(requireContext()) }

    @JvmField
    @AutoWired(name = KEY_TASK_ID)
    var taskId: Long = 0

    @JvmField
    @AutoWired(name = KEY_TASK_TYPE)
    var taskType: Int = 0

    @JvmField
    @AutoWired(name = KEY_TASK_CLONE)
    var isClone: Boolean = false

    private lateinit var conditionsRecyclerView: RecyclerView
    private lateinit var conditionsAdapter: TaskSettingAdapter
    private var conditionsList = mutableListOf<TaskSetting>()

    private lateinit var actionsRecyclerView: RecyclerView
    private lateinit var actionsAdapter: TaskSettingAdapter
    private var actionsList = mutableListOf<TaskSetting>()
    private var restoredDraft = false
    private var draftName = ""
    private var draftEnabled = false
    private var formLoad: Disposable? = null
    private var resultKey = "task.result." + UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.containsKey("draft.conditions") == true) {
            restoredDraft = true
            resultKey = savedInstanceState.getString("draft.result")!!
            draftName = savedInstanceState.getString("draft.name").orEmpty()
            draftEnabled = savedInstanceState.getBoolean("draft.enabled")
            conditionsList = Gson().fromJson(savedInstanceState.getString("draft.conditions"), Array<TaskSetting>::class.java).toMutableList()
            actionsList = Gson().fromJson(savedInstanceState.getString("draft.actions"), Array<TaskSetting>::class.java).toMutableList()
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putString("draft.result", resultKey)
        outState.putString("draft.name", binding?.etName?.text?.toString() ?: draftName)
        outState.putBoolean("draft.enabled", binding?.sbStatus?.isChecked ?: draftEnabled)
        outState.putString("draft.conditions", Gson().toJson(conditionsList))
        outState.putString("draft.actions", Gson().toJson(actionsList))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        formLoad?.dispose(); formLoad = null
        binding?.let { draftName = it.etName.text.toString(); draftEnabled = it.sbStatus.isChecked; restoredDraft = true }
        if (dialog.isShowing) dialog.dismiss()
        super.onDestroyView()
    }

    private var TASK_CONDITION_FRAGMENT_LIST = listOf(
        PageInfo(
            getString(R.string.task_cron),
            "cn.ppps.forwarder.fragment.condition.CronFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_custom_time,
        ),
        PageInfo(
            getString(R.string.task_to_address),
            "cn.ppps.forwarder.fragment.condition.ToAddressFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_to_address,
        ),
        PageInfo(
            getString(R.string.task_leave_address),
            "cn.ppps.forwarder.fragment.condition.LeaveAddressFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_leave_address,
        ),
        PageInfo(
            getString(R.string.task_network),
            "cn.ppps.forwarder.fragment.condition.NetworkFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_network
        ),
        PageInfo(
            getString(R.string.task_sim),
            "cn.ppps.forwarder.fragment.condition.SimFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_sim
        ),
        PageInfo(
            getString(R.string.task_battery),
            "cn.ppps.forwarder.fragment.condition.BatteryFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_battery
        ),
        PageInfo(
            getString(R.string.task_charge),
            "cn.ppps.forwarder.fragment.condition.ChargeFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_charge
        ),
        PageInfo(
            getString(R.string.task_lock_screen),
            "cn.ppps.forwarder.fragment.condition.LockScreenFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_lock_screen
        ),
        PageInfo(
            getString(R.string.task_sms),
            "cn.ppps.forwarder.fragment.condition.MsgFragment",
            "sms",
            CoreAnim.slide,
            R.drawable.auto_task_icon_sms
        ),
        PageInfo(
            getString(R.string.task_call),
            "cn.ppps.forwarder.fragment.condition.MsgFragment",
            "call",
            CoreAnim.slide,
            R.drawable.auto_task_icon_incall
        ),
        PageInfo(
            getString(R.string.task_app),
            "cn.ppps.forwarder.fragment.condition.MsgFragment",
            "app",
            CoreAnim.slide,
            R.drawable.auto_task_icon_start_activity
        ),
        PageInfo(
            getString(R.string.task_bluetooth),
            "cn.ppps.forwarder.fragment.condition.BluetoothFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_bluetooth
        ),
    )

    private var TASK_ACTION_FRAGMENT_LIST = listOf(
        PageInfo(
            getString(R.string.task_sendsms),
            "cn.ppps.forwarder.fragment.action.SendSmsFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_sms
        ),
        PageInfo(
            getString(R.string.task_notification),
            "cn.ppps.forwarder.fragment.action.NotificationFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_notification,
        ),
        PageInfo(
            getString(R.string.task_cleaner),
            "cn.ppps.forwarder.fragment.action.CleanerFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_cleaner
        ),
        PageInfo(
            getString(R.string.task_settings),
            "cn.ppps.forwarder.fragment.action.SettingsFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_settings
        ),
        PageInfo(
            getString(R.string.task_frpc),
            "cn.ppps.forwarder.fragment.action.FrpcFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_frpc
        ),
        PageInfo(
            getString(R.string.task_http_server),
            "cn.ppps.forwarder.fragment.action.HttpServerFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_http_server
        ),
        PageInfo(
            getString(R.string.task_rule),
            "cn.ppps.forwarder.fragment.action.RuleFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_rule
        ),
        PageInfo(
            getString(R.string.task_sender),
            "cn.ppps.forwarder.fragment.action.SenderFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_sender
        ),
        PageInfo(
            getString(R.string.task_alarm),
            "cn.ppps.forwarder.fragment.action.AlarmFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_alarm
        ),
        PageInfo(
            getString(R.string.task_resend),
            "cn.ppps.forwarder.fragment.action.ResendFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_resend
        ),
        PageInfo(
            getString(R.string.task_task),
            "cn.ppps.forwarder.fragment.action.TaskActionFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_task
        ),
        PageInfo(
            getString(R.string.task_wol),
            "cn.ppps.forwarder.fragment.action.WolFragment",
            "",
            CoreAnim.slide,
            R.drawable.auto_task_icon_wol
        ),
    )

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentTasksEditBinding {
        return FragmentTasksEditBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        titleBar!!.setTitle(R.string.menu_tasks)
        return titleBar
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        conditionsRecyclerView = findViewById(R.id.recycler_conditions)
        actionsRecyclerView = findViewById(R.id.recycler_actions)

        // Initialize RecyclerView and Adapter
        initRecyclerViews()

        // Set drag sorting
        val conditionsCallback = ItemMoveCallback(object : ItemMoveCallback.Listener {
            override fun onItemMove(fromPosition: Int, toPosition: Int) {
                Log.d(TAG, "onItemMove: $fromPosition $toPosition")
                conditionsAdapter.onItemMove(fromPosition, toPosition)
                conditionsList = conditionsAdapter.itemList
            }

            override fun onDragFinished() {
                //conditionsListKeep withadapterconsistent
                conditionsList = conditionsAdapter.itemList
                //conditionsAdapter.notifyDataSetChanged()
            }
        })

        val itemTouchHelperConditions = ItemTouchHelper(conditionsCallback)
        itemTouchHelperConditions.attachToRecyclerView(conditionsRecyclerView)
        conditionsAdapter.setTouchHelper(itemTouchHelperConditions)

        val actionsCallback = ItemMoveCallback(object : ItemMoveCallback.Listener {
            override fun onItemMove(fromPosition: Int, toPosition: Int) {
                Log.d(TAG, "onItemMove: $fromPosition $toPosition")
                actionsAdapter.onItemMove(fromPosition, toPosition)
                actionsList = actionsAdapter.itemList
            }

            override fun onDragFinished() {
                //actionsListKeep withadapterconsistent
                actionsList = actionsAdapter.itemList
                //actionsAdapter.notifyDataSetChanged()
            }
        })

        val itemTouchHelperActions = ItemTouchHelper(actionsCallback)
        itemTouchHelperActions.attachToRecyclerView(actionsRecyclerView)
        actionsAdapter.setTouchHelper(itemTouchHelperActions)

        if (taskId <= 0) { //Add
            titleBar?.setSubTitle(getString(R.string.add_task))
            binding!!.btnDel.setText(R.string.discard)
        } else { //Edit & Cloning
            binding!!.btnDel.setText(R.string.del)
            if (!restoredDraft) initForm()
        }
    }

    override fun initListeners() {
        parentFragmentManager.setFragmentResultListener(resultKey, viewLifecycleOwner) { _, result ->
            onFragmentResult(result.getInt("request"), result.getInt("result"), result.getParcelable("data"))
        }
        if (restoredDraft) {
            binding!!.etName.setText(draftName)
            binding!!.sbStatus.isChecked = draftEnabled
            binding!!.layoutAddCondition.visibility = if (conditionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
            binding!!.layoutAddAction.visibility = if (actionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
        }
        binding!!.layoutAddCondition.setOnClickListener(this)
        binding!!.layoutAddAction.setOnClickListener(this)
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
    }

    @SuppressLint("InflateParams")
    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.layout_add_condition -> {
                    val bottomSheet: View = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_task_condition_bottom_sheet, null)
                    val tvTitle: TextView = bottomSheet.findViewById(R.id.tv_title)
                    tvTitle.text = if (conditionsList.isEmpty()) getString(R.string.select_task_trigger) else getString(R.string.select_task_condition)

                    val recyclerView: RecyclerView = bottomSheet.findViewById(R.id.recyclerView)
                    WidgetUtils.initGridRecyclerView(recyclerView, if (resources.configuration.screenWidthDp < 600) 2 else 4, DensityUtils.dp2px(1f))
                    val widgetItemAdapter = WidgetItemAdapter(TASK_CONDITION_FRAGMENT_LIST)
                    widgetItemAdapter.setOnItemClickListener(that)
                    recyclerView.adapter = widgetItemAdapter

                    val bottomSheetCloseButton: XUIAlphaTextView = bottomSheet.findViewById(R.id.bottom_sheet_close_button)
                    bottomSheetCloseButton.setOnClickListener { dialog.dismiss() }

                    dialog.setContentView(bottomSheet)
                    dialog.setCancelable(true)
                    dialog.setCanceledOnTouchOutside(true)
                    dialog.show()
                    // Keep the close action in the viewport; only the choices scroll.
                    val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    sheet?.let {
                        it.layoutParams.height = (resources.displayMetrics.heightPixels * 0.85f).toInt()
                        it.requestLayout()
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                R.id.layout_add_action -> {
                    val bottomSheet: View = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_task_action_bottom_sheet, null)
                    val recyclerView: RecyclerView = bottomSheet.findViewById(R.id.recyclerView)

                    WidgetUtils.initGridRecyclerView(recyclerView, if (resources.configuration.screenWidthDp < 600) 2 else 4, DensityUtils.dp2px(1f))
                    val widgetItemAdapter = WidgetItemAdapter(TASK_ACTION_FRAGMENT_LIST)
                    widgetItemAdapter.setOnItemClickListener(that)
                    recyclerView.adapter = widgetItemAdapter

                    val bottomSheetCloseButton: XUIAlphaTextView = bottomSheet.findViewById(R.id.bottom_sheet_close_button)
                    bottomSheetCloseButton.setOnClickListener { dialog.dismiss() }

                    dialog.setContentView(bottomSheet)
                    dialog.setCancelable(true)
                    dialog.setCanceledOnTouchOutside(true)
                    dialog.show()
                    // Keep the close action in the viewport; only the choices scroll.
                    val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    sheet?.let {
                        it.layoutParams.height = (resources.displayMetrics.heightPixels * 0.85f).toInt()
                        it.requestLayout()
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                R.id.btn_test -> {
                    val taskNew = checkForm()
                    testTask(taskNew)
                    return
                }

                R.id.btn_del -> {
                    if (taskId <= 0 || isClone) {
                        popToBack()
                        return
                    }

                    //TODO: Confirm before Delete
                    return
                }

                R.id.btn_save -> {
                    val taskNew = checkForm()
                    if (isClone) taskNew.id = 0
                    //Save task
                    if (taskNew.id > 0) {
                        Core.task.update(taskNew)
                    } else {
                        taskNew.id = Core.task.insert(taskNew)
                    }
                    //Apply Tasks
                    applyTask(taskNew)
                    XToastUtils.success(R.string.tipSaveSuccess)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString())
            e.printStackTrace()
            Log.e(TAG, "onClick error: ${e.message}")
        }
    }

    //Initializing forms
    private fun initForm() {
        Core.task.get(taskId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(object : SingleObserver<Task> {
            override fun onSubscribe(d: Disposable) { formLoad = d }

            override fun onError(e: Throwable) {
                e.printStackTrace()
                Log.e(TAG, "initForm error: ${e.message}")
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onSuccess(task: Task) {
                if (binding == null || restoredDraft) return
                if (isClone) {
                    titleBar?.setSubTitle(getString(R.string.clone_task))
                    binding!!.btnDel.setText(R.string.discard)
                } else {
                    titleBar?.setSubTitle(getString(R.string.edit_task))
                }
                binding!!.etName.setText(task.name)
                binding!!.sbStatus.isChecked = task.status == STATUS_ON
                try {
                    if (task.conditions.isNotEmpty()) {
                        val conditionList = Gson().fromJson(task.conditions, Array<TaskSetting>::class.java).toMutableList()
                        for (condition in conditionList) {
                            this@TasksEditFragment.conditionsList.add(condition)
                        }
                        conditionsAdapter.notifyDataSetChanged()
                        binding!!.layoutAddCondition.visibility = if (this@TasksEditFragment.conditionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
                    }
                    if (task.actions.isNotEmpty()) {
                        val actionList = Gson().fromJson(task.actions, Array<TaskSetting>::class.java).toMutableList()
                        for (action in actionList) {
                            this@TasksEditFragment.actionsList.add(action)
                        }
                        actionsAdapter.notifyDataSetChanged()
                        binding!!.layoutAddAction.visibility = if (this@TasksEditFragment.actionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(TAG, "initForm error: ${e.message}")
                    XToastUtils.error(e.message.toString())
                }
            }
        })
    }

    //Check forms before submitting
    private fun checkForm(): Task {
        val taskName = binding!!.etName.text.toString().trim()
        if (taskName.isEmpty()) {
            throw Exception(getString(R.string.invalid_task_name))
        }
        if (conditionsList.size <= 0) {
            throw Exception(getString(R.string.invalid_conditions))
        }
        if (actionsList.size <= 0) {
            throw Exception(getString(R.string.invalid_actions))
        }

        //SMS./Call broadcast/APPNotification conditions must be first
        for (i in 1 until conditionsList.size) {
            if (conditionsList[i].type == TASK_CONDITION_SMS || conditionsList[i].type == TASK_CONDITION_CALL || conditionsList[i].type == TASK_CONDITION_APP) {
                throw Exception(getString(R.string.msg_condition_must_be_trigger))
            }
        }

        val lastExecTime = Date()
        // Set the millisecond part to 0，Avoid duplication of tasks due to the different milliseconds
        lastExecTime.time = lastExecTime.time / 1000 * 1000
        var nextExecTime = lastExecTime
        val firstCondition = conditionsList[0]
        taskType = firstCondition.type

        when (taskType) {
            TASK_CONDITION_CRON -> {
                //Check time settings for timed jobs
                val cronSetting = Gson().fromJson(firstCondition.setting, CronSetting::class.java)
                if (cronSetting.expression.isEmpty()) {
                    throw Exception(getString(R.string.invalid_cron))
                }
                val cronExpression = CronExpression(cronSetting.expression)
                nextExecTime = cronExpression.getNextValidTimeAfter(lastExecTime)
            }
        }

        //Build task description
        val description = StringBuilder()
        description.append(getString(R.string.task_conditions)).append(" ")
        description.append(conditionsList.map { it.description }.toTypedArray().joinToString(","))
        description.append(" ").append(getString(R.string.task_actions)).append(" ")
        description.append(actionsList.map { it.description }.toTypedArray().joinToString(","))

        val status = if (binding!!.sbStatus.isChecked) STATUS_ON else STATUS_OFF
        return Task(
            taskId, taskType, taskName, description.toString(), Gson().toJson(conditionsList), Gson().toJson(actionsList), status, lastExecTime, nextExecTime
        )
    }

    //Test Tasks
    private fun testTask(task: Task) {
    }

    //Apply Tasks
    private fun applyTask(task: Task) {
        when (task.type) {
            //Scheduled task
            TASK_CONDITION_CRON -> {
                if (task.id <= 0) return
                //Cancel the timer for the old task & Set New Timer
                CronJobScheduler.cancelTask(task.id)
                CronJobScheduler.scheduleTask(task)
            }
        }
    }

    @SingleClick
    override fun onItemClick(itemView: View, widgetInfo: PageInfo, pos: Int) {
        try {
            dialog.dismiss()
            Log.d(TAG, "onItemClick: $widgetInfo")
            //It's the condition or the action.
            if (widgetInfo.classPath.contains(".condition.")) {
                val typeCondition = pos + KEY_BACK_CODE_CONDITION
                //SMS broadcasts, call broadcasts andAPPnotification conditions must be submitted as triggers
                if ((typeCondition == TASK_CONDITION_SMS || typeCondition == TASK_CONDITION_CALL || typeCondition == TASK_CONDITION_APP) && actionsList.isNotEmpty()) {
                    XToastUtils.error(getString(R.string.msg_condition_must_be_trigger))
                    return
                }
                //Determines whether this type of condition has been added
                for (item in conditionsList) {
                    //Attention.：TASK_CONDITION_XXX Enumeration values equal to TASK_CONDITION_FRAGMENT_LIST Index plus KEY_BACK_CODE_CONDITION，It's unchangeable.
                    if (item.type == typeCondition) {
                        XToastUtils.error(getString(R.string.condition_already_exists))
                        return
                    }

                    //You have to start a location service to use the entry or exit location, type condition.
                    if ((typeCondition == TASK_CONDITION_TO_ADDRESS || typeCondition == TASK_CONDITION_LEAVE_ADDRESS) && !App.LocationClient.isStarted()) {
                        MaterialDialog.Builder(requireContext())
                            .iconRes(R.drawable.auto_task_icon_location)
                            .title(R.string.enable_location)
                            .content(R.string.enable_location_dialog)
                            .cancelable(false)
                            .positiveText(R.string.lab_yes)
                            .negativeText(R.string.lab_no).onPositive { _: MaterialDialog?, _: DialogAction? ->
                                SettingUtils.enableLocation = true
                                val serviceIntent = Intent(requireContext(), LocationService::class.java)
                                serviceIntent.action = ACTION_START
                                requireContext().startService(serviceIntent)
                            }.show()
                        return
                    }

                    //Arrival and departure conditions are mutually exclusive
                    if ((typeCondition == TASK_CONDITION_TO_ADDRESS || typeCondition == TASK_CONDITION_LEAVE_ADDRESS) && (item.type == TASK_CONDITION_TO_ADDRESS || item.type == TASK_CONDITION_LEAVE_ADDRESS)) {
                        XToastUtils.error(getString(R.string.only_one_location_condition))
                        return
                    }

                    //SMS broadcasts, call broadcasts andAPPnotification conditions are mutually exclusive
                    if ((typeCondition == TASK_CONDITION_SMS || typeCondition == TASK_CONDITION_CALL || typeCondition == TASK_CONDITION_APP) && (item.type == TASK_CONDITION_SMS || item.type == TASK_CONDITION_CALL || item.type == TASK_CONDITION_APP)) {
                        XToastUtils.error(getString(R.string.only_one_msg_condition))
                        return
                    }
                }
            } else {
                val typeAction = pos + KEY_BACK_CODE_ACTION
                //Determines whether this type of action has been added
                for (item in actionsList) {
                    //Attention.：TASK_ACTION_XXX Enumeration values equal to TASK_ACTION_FRAGMENT_LIST Index plus KEY_BACK_CODE_ACTION，It's unchangeable.
                    if (item.type == typeAction) {
                        XToastUtils.error(getString(R.string.action_already_exists))
                        return
                    }
                }
            }

            @Suppress("UNCHECKED_CAST") PageOption.to(Class.forName(widgetInfo.classPath) as Class<XPageFragment>) //Targetfragment
                .putString("task.result.receiver", resultKey)
                .putInt("task.result.request", 0) //requestCode: 0 Add,>0 Edit（itemListXxx Indexes added1）
                .putString(KEY_EVENT_PARAMS_CONDITION, widgetInfo.params)
                .open(this)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "onItemClick error: ${e.message}")
            XToastUtils.error(e.message.toString())
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onFragmentResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onFragmentResult(requestCode, resultCode, data)
        Log.d(TAG, "requestCode:$requestCode resultCode:$resultCode")
        if (data != null) {
            val extras = data.extras ?: return

            val description: String?
            var setting: String? = null
            if (resultCode in KEY_BACK_CODE_CONDITION..KEY_BACK_CODE_CONDITION + 999) {
                setting = extras.getString(KEY_BACK_DATA_CONDITION) ?: return
                //Attention.：TASK_CONDITION_XXX Enumeration values equal to TASK_CONDITION_FRAGMENT_LIST Index plus KEY_BACK_CODE_CONDITION，It's unchangeable.
                val widgetInfoIndex = resultCode - KEY_BACK_CODE_CONDITION
                if (widgetInfoIndex >= TASK_CONDITION_FRAGMENT_LIST.size) return
                val widgetInfo = TASK_CONDITION_FRAGMENT_LIST[widgetInfoIndex]
                description = extras.getString(KEY_BACK_DESCRIPTION_CONDITION) ?: widgetInfo.name.toString()
                val taskSetting = TaskSetting(resultCode, widgetInfo.name, description, setting, requestCode)
                //requestCode: Equals conditionsList Indexes added1
                if (requestCode == 0) {
                    taskSetting.position = conditionsList.size
                    conditionsList.add(taskSetting)
                    conditionsAdapter.notifyItemInserted(conditionsList.size - 1)
                } else {
                    conditionsList[requestCode - 1] = taskSetting
                    conditionsAdapter.notifyItemChanged(requestCode - 1)
                }
                //conditionsAdapter.notifyDataSetChanged()
                binding!!.layoutAddCondition.visibility = if (conditionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
            } else if (resultCode in KEY_BACK_CODE_ACTION..KEY_BACK_CODE_ACTION + 999) {
                setting = extras.getString(KEY_BACK_DATA_ACTION) ?: return
                //Attention.：TASK_ACTION_XXX Enumeration values equal to TASK_ACTION_FRAGMENT_LIST Index plus KEY_BACK_CODE_ACTION，It's unchangeable.
                val widgetInfoIndex = resultCode - KEY_BACK_CODE_ACTION
                if (widgetInfoIndex >= TASK_ACTION_FRAGMENT_LIST.size) return
                val widgetInfo = TASK_ACTION_FRAGMENT_LIST[widgetInfoIndex]
                description = extras.getString(KEY_BACK_DESCRIPTION_ACTION) ?: widgetInfo.name.toString()
                val taskSetting = TaskSetting(resultCode, widgetInfo.name, description, setting, requestCode)
                //requestCode: Equals actionsList Indexes added1
                if (requestCode == 0) {
                    taskSetting.position = actionsList.size
                    actionsList.add(taskSetting)
                    actionsAdapter.notifyItemInserted(actionsList.size - 1)
                } else {
                    actionsList[requestCode - 1] = taskSetting
                    actionsAdapter.notifyItemChanged(requestCode - 1)
                }
                //actionsAdapter.notifyDataSetChanged()
                binding!!.layoutAddAction.visibility = if (actionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
            }
            Log.d(TAG, "requestCode:$requestCode resultCode:$resultCode")
        }
    }

    private fun initRecyclerViews() {
        conditionsAdapter = TaskSettingAdapter(conditionsList, { position -> removeCondition(position) }, { position -> editCondition(position) })

        actionsAdapter = TaskSettingAdapter(actionsList, { position -> removeAction(position) }, { position -> editAction(position) })

        conditionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = conditionsAdapter
        }

        actionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = actionsAdapter
        }
    }

    private fun editCondition(position: Int) {
        // Edit condition items
        // Based on position Edit selected items
        val condition = conditionsList[position]
        Log.d(TAG, "editCondition: $position")

        val widgetInfoIndex = condition.type - KEY_BACK_CODE_CONDITION
        //To judge if there is.
        if (widgetInfoIndex < 0 || widgetInfoIndex >= TASK_CONDITION_FRAGMENT_LIST.size) return
        val widgetInfo = TASK_CONDITION_FRAGMENT_LIST[condition.type - KEY_BACK_CODE_CONDITION]
        @Suppress("UNCHECKED_CAST")
        PageOption.to(Class.forName(widgetInfo.classPath) as Class<XPageFragment>) //Targetfragment
            .putString("task.result.receiver", resultKey)
            .putInt("task.result.request", position + 1) //requestCode: 0 Add,>0 Edit（conditionsList Indexes added1）
            .putString(KEY_EVENT_DATA_CONDITION, condition.setting)
            .putString(KEY_EVENT_PARAMS_CONDITION, widgetInfo.params)
            .open(this)
    }

    private fun removeCondition(position: Int) {
        conditionsList.removeAt(position)
        conditionsAdapter.notifyItemRemoved(position)
        conditionsAdapter.notifyItemRangeChanged(position, conditionsList.size) // Update Index
        binding!!.layoutAddCondition.visibility = if (conditionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
    }

    private fun editAction(position: Int) {
        // Edit action items
        // Based on position Edit selected items
        val action = actionsList[position]
        Log.d(TAG, "editAction: $position")

        val widgetInfoIndex = action.type - KEY_BACK_CODE_ACTION
        //To judge if there is.
        if (widgetInfoIndex < 0 || widgetInfoIndex >= TASK_ACTION_FRAGMENT_LIST.size) return
        val widgetInfo = TASK_ACTION_FRAGMENT_LIST[action.type - KEY_BACK_CODE_ACTION]
        @Suppress("UNCHECKED_CAST")
        PageOption.to(Class.forName(widgetInfo.classPath) as Class<XPageFragment>) //Targetfragment
            .putString("task.result.receiver", resultKey)
            .putInt("task.result.request", position + 1) //requestCode: 0 Add,>0 Edit（actionsList Indexes added1）
            .putString(KEY_EVENT_DATA_ACTION, action.setting)
            .open(this)
    }

    private fun removeAction(position: Int) {
        actionsList.removeAt(position)
        actionsAdapter.notifyItemRemoved(position)
        actionsAdapter.notifyItemRangeChanged(position, actionsList.size) // Update Index
        binding!!.layoutAddAction.visibility = if (actionsList.size >= MAX_SETTING_NUM) View.GONE else View.VISIBLE
    }
}