package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Rect
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import com.alibaba.android.vlayout.VirtualLayoutManager
import cn.ppps.forwarder.App.Companion.FORWARD_STATUS_MAP
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.MsgPagingAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.database.entity.LogsDetail
import cn.ppps.forwarder.database.entity.MsgAndLogs
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.viewmodel.BaseViewModelFactory
import cn.ppps.forwarder.database.viewmodel.MsgViewModel
import cn.ppps.forwarder.databinding.FragmentLogsBinding
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.scwang.smartrefresh.layout.api.RefreshLayout
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import cn.ppps.forwarder.widget.material.TitleBar
import cn.ppps.forwarder.widget.material.TargetCheckBox as SmoothCheckBox
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import androidx.fragment.app.DialogFragment
import com.xuexiang.xutil.data.DateUtils
import com.xuexiang.xutil.resource.ResUtils.getColors
import com.xuexiang.xutil.resource.ResUtils.getStringArray
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.xuexiang.xutil.tip.ToastUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone


@Suppress("PrivatePropertyName")
@Page(name = "Forward Log")
class LogsFragment : BaseFragment<FragmentLogsBinding?>(), MsgPagingAdapter.OnItemClickListener {

    private val TAG: String = LogsFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private var adapter = MsgPagingAdapter(this)
    private val viewModel by viewModels<MsgViewModel> { BaseViewModelFactory(context) }
    private var currentType: String = "sms"

    //Log Filter
    private var currentFilter: MutableMap<String, Any> = mutableMapOf()
    private var logsFilterPopup: AlertDialog? = null
    private var timePicker: DialogFragment? = null

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentLogsBinding {
        return FragmentLogsBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false)
        // Root tab: the bottom bar is the only primary navigation, so no hamburger or fake Back.
        titleBar!!.disableLeftView()
        titleBar!!.setTitle(R.string.menu_logs)
        titleBar!!.addAction(object : TitleBar.ImageAction(R.drawable.ic_delete) {
            @SingleClick
            override fun performAction(view: View) {
                MaterialDialog.Builder(requireContext())
                    .content(if (currentFilter.isEmpty()) R.string.delete_type_log_tips else R.string.delete_filter_log_tips)
                    .positiveText(R.string.lab_yes)
                    .negativeText(R.string.lab_no)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        try {
                            Log.d(TAG, "deleteAll, currentType:$currentType, currentFilter:$currentFilter")
                            viewModel.setType(currentType).setFilter(currentFilter).deleteAll()
                            reloadData()
                            XToastUtils.success(if (currentFilter.isEmpty()) R.string.delete_type_log_toast else R.string.delete_filter_log_toast)
                        } catch (e: Exception) {
                            e.message?.let { XToastUtils.error(it) }
                        }
                    }
                    .show()
            }
        })
        titleBar!!.addAction(object : TitleBar.ImageAction(R.drawable.ic_filter) {
            @SingleClick
            override fun performAction(view: View) {
                showLogsFilterDialog()
            }
        })
        return titleBar
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        val virtualLayoutManager = VirtualLayoutManager(requireContext())
        binding!!.recyclerView.layoutManager = virtualLayoutManager
        val viewPool = RecycledViewPool()
        binding!!.recyclerView.setRecycledViewPool(viewPool)
        viewPool.setMaxRecycledViews(0, 10)
        binding!!.recyclerView.isFocusableInTouchMode = false

        binding!!.tabBar.setTabTitles(getStringArray(R.array.type_param_option))
        binding!!.tabBar.setOnTabClickListener { _, position ->
            //XToastUtils.toast("It's hit.$title--$position")
            currentType = when (position) {
                1 -> "call"
                2 -> "app"
                else -> "sms"
            }
            // Switching tabs clears the filter, as before: its fields differ per log type.
            currentFilter = mutableMapOf()
            dismissLogsFilterDialog()
            reloadData()
        }
    }

    override fun initListeners() {
        binding!!.recyclerView.adapter = adapter

        //Pull to refresh
        binding!!.refreshLayout.setOnRefreshListener { refreshLayout: RefreshLayout ->
            //adapter.refresh()
            lifecycleScope.launch {
                viewModel.setType(currentType).setFilter(currentFilter).allMsg.collectLatest { adapter.submitData(it) }
            }
            refreshLayout.finishRefresh()
        }

        binding!!.refreshLayout.autoRefresh()
    }

    override fun onItemClicked(view: View?, item: MsgAndLogs) {

        val detailStr = StringBuilder()
        detailStr.append(getString(R.string.from)).append(item.msg.from).append("\n\n")
        if (!TextUtils.isEmpty(item.msg.simInfo)) {
            if (item.msg.type == "app") {
                detailStr.append(getString(R.string.title)).append(item.msg.simInfo).append("\n\n")
                detailStr.append(getString(R.string.msg)).append(item.msg.content).append("\n\n")
            } else {
                detailStr.append(getString(R.string.msg)).append(item.msg.content).append("\n\n")
                detailStr.append(getString(R.string.slot)).append(item.msg.simInfo).append("\n\n")
            }
        }
        @SuppressLint("SimpleDateFormat") val utcFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        detailStr.append(getString(R.string.time)).append(DateUtils.date2String(item.msg.time, utcFormatter))

        MaterialDialog.Builder(requireContext())
            .iconRes(item.msg.simImageId)
            .title(R.string.details)
            .content(detailStr.toString())
            .cancelable(true)
            .positiveText(R.string.del)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                viewModel.delete(item.msg.id)
                XToastUtils.success(R.string.delete_log_toast)
            }
            .neutralText(R.string.rematch)
            .neutralColor(getColors(R.color.red))
            .onNeutral { _: MaterialDialog?, _: DialogAction? ->
                XToastUtils.toast(R.string.rematch_toast)
                SendUtils.rematchSendMsg(item)
            }
            .show()
    }

    override fun onLogsClicked(view: View?, item: LogsDetail) {
        val ruleStr = StringBuilder()
        ruleStr.append(Rule.getRuleMatch(item.type, item.ruleFiled, item.ruleCheck, item.ruleValue, item.ruleSimSlot)).append(item.senderName)
        val detailStr = StringBuilder()
        detailStr.append(getString(R.string.rule)).append(ruleStr.toString()).append("\n\n")
        @SuppressLint("SimpleDateFormat") val utcFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        detailStr.append(getString(R.string.time)).append(DateUtils.date2String(item.time, utcFormatter)).append("\n\n")
        detailStr.append(getString(R.string.result)).append(FORWARD_STATUS_MAP[item.forwardStatus]).append("\n--------------------\n").append(item.forwardResponse)

        MaterialDialog.Builder(requireContext())
            .title(R.string.details)
            .content(detailStr.toString())
            .cancelable(true)
            .negativeText(R.string.resend)
            .onNegative { _: MaterialDialog?, _: DialogAction? ->
                XToastUtils.toast(R.string.resend_toast)
                SendUtils.retrySendMsg(item.id)
            }
            .show()
    }

    override fun onItemRemove(view: View?, id: Int) {}

    private fun reloadData() {
        viewModel.setType(currentType).setFilter(currentFilter)
        adapter.refresh()
        binding!!.recyclerView.scrollToPosition(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // MainActivity handles rotation itself, so an open dialog would keep the background insets resolved for
        // the old orientation (80dp top/bottom in portrait, 24dp in landscape). Rebuild it with the unapplied
        // draft. Not while a date/time picker is on top: the new dialog window would cover the picker.
        val dialog = logsFilterPopup ?: return
        if (!dialog.isShowing || timePicker?.isAdded == true) return
        val draft = readLogsFilter(dialog)
        dismissLogsFilterDialog()
        showLogsFilterDialog(draft)
    }

    override fun onDestroyView() {
        dismissLogsFilterDialog()
        super.onDestroyView()
    }

    private fun dismissLogsFilterDialog() {
        val dialog = logsFilterPopup ?: return
        logsFilterPopup = null
        dialog.dismiss()
    }

    /**
     * Filter dialog: a Material 3 alert dialog with a title and dialog_logs_filter (scrolling form plus its own
     * Reset / Cancel / Apply row). Built directly with MaterialAlertDialogBuilder and no message: the shared
     * MaterialDialog wrapper always sets one, which broke AlertDialogLayout's measuring and hid the buttons.
     * The form is filled from the applied filter on every open (or from [draft] after a rotation), so Cancel,
     * Back and tapping outside discard edits and never change the applied filter.
     */
    private fun showLogsFilterDialog(draft: LogsFilterSelection = LogsFilterSelection.fromFilter(currentFilter)) {
        if (logsFilterPopup?.isShowing == true) return
        val builder = MaterialAlertDialogBuilder(requireContext())
        // Inflate with the dialog's themed context so fields and buttons get the dialog surface colours.
        val content = View.inflate(builder.context, R.layout.dialog_logs_filter, null)
        configureLogsFilterFields(content)
        writeLogsFilter(content, draft)

        val dialog = builder
            .setTitle(R.string.logs_filter_title)
            .setView(content)
            .setCancelable(true)
            .create()
        dialog.setCanceledOnTouchOutside(true)
        // The keyboard shrinks the scrolling form, never the title or the action row.
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        content.findViewById<View>(R.id.btn_logs_filter_reset).setOnClickListener {
            currentFilter = mutableMapOf()
            reloadData()
            dismissLogsFilterDialog()
        }
        content.findViewById<View>(R.id.btn_logs_filter_cancel).setOnClickListener { dialog.cancel() }
        content.findViewById<View>(R.id.btn_logs_filter_apply).setOnClickListener {
            val selection = readLogsFilter(content)
            if (selection.endBeforeStart()) {
                // Such a period can never match; keep the dialog open and point at the field.
                val endLayout = content.findViewById<TextInputLayout>(R.id.til_logs_filter_end)
                endLayout.error = getString(R.string.logs_filter_end_before_start)
                endLayout.post { endLayout.requestRectangleOnScreen(Rect(0, 0, endLayout.width, endLayout.height), false) }
                return@setOnClickListener
            }
            currentFilter = selection.toFilter(currentType)
            reloadData()
            dismissLogsFilterDialog()
        }
        dialog.setOnDismissListener { if (logsFilterPopup === dialog) logsFilterPopup = null }
        logsFilterPopup = dialog
        dialog.show()
    }

    /** Shows the rows that apply to the current log type, with type-specific field labels. */
    private fun configureLogsFilterFields(content: View) {
        content.findViewById<LinearLayout>(R.id.layout_title).visibility = if (currentType == "app") View.VISIBLE else View.GONE
        content.findViewById<LinearLayout>(R.id.layout_sim_slot).visibility = if (currentType == "app") View.GONE else View.VISIBLE
        content.findViewById<LinearLayout>(R.id.layout_call_type).visibility = if (currentType == "call") View.VISIBLE else View.GONE
        content.findViewById<TextInputLayout>(R.id.til_logs_filter_from).hint = getString(
            when (currentType) {
                "app" -> R.string.logs_filter_from_app
                "call" -> R.string.logs_filter_from_number
                else -> R.string.logs_filter_from_sms
            }
        )
        content.findViewById<TextInputLayout>(R.id.til_logs_filter_message).hint =
            getString(if (currentType == "app") R.string.logs_filter_message_app else R.string.logs_filter_message)

        for ((layoutId, fieldId) in listOf(R.id.til_logs_filter_start to R.id.et_start_time, R.id.til_logs_filter_end to R.id.et_end_time)) {
            val layout = content.findViewById<TextInputLayout>(layoutId)
            val field = content.findViewById<EditText>(fieldId)
            // Read-only (no key listener, so no hardware-keyboard typing either); a tap or Enter opens the date
            // picker and then the time picker, and the end icon clears the field.
            field.keyListener = null
            field.setOnClickListener { showTimePicker(field.text.toString().trim(), layout.hint.toString(), fieldId) }
            field.isLongClickable = false
            layout.setEndIconOnClickListener { field.setText("") }
            fun syncClearIcon() { layout.isEndIconVisible = field.text?.isNotEmpty() == true }
            field.doAfterTextChanged {
                syncClearIcon()
                content.findViewById<TextInputLayout>(R.id.til_logs_filter_end).error = null
            }
            syncClearIcon()
        }
    }

    private fun writeLogsFilter(content: View, selection: LogsFilterSelection) {
        content.findViewById<EditText>(R.id.et_from).setText(selection.from)
        content.findViewById<EditText>(R.id.et_content).setText(selection.content)
        content.findViewById<EditText>(R.id.et_title).setText(selection.title)
        content.findViewById<EditText>(R.id.et_start_time).setText(selection.startTime)
        content.findViewById<EditText>(R.id.et_end_time).setText(selection.endTime)
        content.findViewById<RadioGroup>(R.id.rg_sim_slot).check(
            when (selection.simSlot) {
                0 -> R.id.rb_sim_slot_1
                1 -> R.id.rb_sim_slot_2
                else -> R.id.rb_sim_slot_0
            }
        )
        for ((type, id) in CALL_TYPE_BOXES) content.findViewById<SmoothCheckBox>(id).isChecked = type in selection.callTypes
        for ((status, id) in FORWARD_STATUS_BOXES) content.findViewById<SmoothCheckBox>(id).isChecked = status in selection.forwardStatuses
        content.findViewById<TextInputLayout>(R.id.til_logs_filter_end).error = null
    }

    private fun readLogsFilter(dialog: AlertDialog): LogsFilterSelection {
        val content = dialog.findViewById<View>(R.id.logs_filter_root) ?: return LogsFilterSelection.fromFilter(currentFilter)
        return readLogsFilter(content)
    }

    private fun readLogsFilter(content: View): LogsFilterSelection {
        fun text(id: Int) = content.findViewById<EditText>(id)?.text?.toString()?.trim().orEmpty()
        fun checked(id: Int) = content.findViewById<SmoothCheckBox>(id)?.isChecked == true
        return LogsFilterSelection(
            from = text(R.id.et_from),
            content = text(R.id.et_content),
            title = text(R.id.et_title),
            startTime = text(R.id.et_start_time),
            endTime = text(R.id.et_end_time),
            simSlot = when (content.findViewById<RadioGroup>(R.id.rg_sim_slot)?.checkedRadioButtonId) {
                R.id.rb_sim_slot_1 -> 0
                R.id.rb_sim_slot_2 -> 1
                else -> -1
            },
            callTypes = CALL_TYPE_BOXES.filter { checked(it.second) }.map { it.first }.toSet(),
            forwardStatuses = FORWARD_STATUS_BOXES.filter { checked(it.second) }.map { it.first }.toSet(),
        )
    }

    /**
     * Date, then time picker. A start time gets :00 seconds and an end time :59, so "until 14:30" includes
     * that whole minute. The result goes to the field of the dialog that is open when the user confirms.
     */
    private fun showTimePicker(time: String, title: String, fieldId: Int) {
        if (timePicker?.isAdded == true) return
        val calendar: Calendar = Calendar.getInstance()
        calendar.time = LogsFilterSelection.parseTime(time)?.let { Date(it) } ?: Date()
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        }
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(title).setSelection(utc.timeInMillis).build()
        picker.addOnPositiveButtonClickListener { day ->
            if (!isAdded) return@addOnPositiveButtonClickListener
            val selectedDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day }
            calendar.set(selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH), selectedDay.get(Calendar.DAY_OF_MONTH))
            val clock = MaterialTimePicker.Builder()
                .setTitleText(title)
                .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(calendar.get(Calendar.HOUR_OF_DAY)).setMinute(calendar.get(Calendar.MINUTE)).build()
            clock.addOnPositiveButtonClickListener {
                calendar.set(Calendar.HOUR_OF_DAY, clock.hour)
                calendar.set(Calendar.MINUTE, clock.minute)
                calendar.set(Calendar.SECOND, if (fieldId == R.id.et_end_time) 59 else 0)
                calendar.set(Calendar.MILLISECOND, 0)
                logsFilterPopup?.findViewById<EditText>(fieldId)
                    ?.setText(DateUtils.date2String(calendar.time, DateUtils.yyyyMMddHHmmss.get()))
            }
            timePicker = clock
            clock.show(parentFragmentManager, "log-time")
        }
        timePicker = picker
        picker.show(parentFragmentManager, "log-date")
    }

    private companion object {
        /** Call type value (Msg.call_type) -> checkbox. */
        val CALL_TYPE_BOXES = listOf(
            1 to R.id.scb_call_type1, 2 to R.id.scb_call_type2, 3 to R.id.scb_call_type3,
            4 to R.id.scb_call_type4, 5 to R.id.scb_call_type5, 6 to R.id.scb_call_type6,
        )

        /** Forward status value (Logs.forward_status) -> checkbox. */
        val FORWARD_STATUS_BOXES = listOf(0 to R.id.scb_forward_status_0, 1 to R.id.scb_forward_status_1, 2 to R.id.scb_forward_status_2)
    }
}
