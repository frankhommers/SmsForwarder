package cn.ppps.forwarder.fragment.action

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.FrpcRecyclerAdapter
import cn.ppps.forwarder.adapter.base.ItemMoveCallback
import cn.ppps.forwarder.adapter.spinner.FrpcSpinnerAdapter
import cn.ppps.forwarder.adapter.spinner.FrpcSpinnerItem
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Frpc
import cn.ppps.forwarder.databinding.FragmentTasksActionFrpcBinding
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.TaskSetting
import cn.ppps.forwarder.entity.action.FrpcSetting
import cn.ppps.forwarder.utils.KEY_BACK_DATA_ACTION
import cn.ppps.forwarder.utils.KEY_BACK_DESCRIPTION_ACTION
import cn.ppps.forwarder.utils.KEY_EVENT_DATA_ACTION
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.TASK_ACTION_FRPC
import cn.ppps.forwarder.utils.TaskWorker
import cn.ppps.forwarder.utils.XToastUtils
import cn.ppps.forwarder.workers.ActionWorker
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.utils.CountDownButtonHelper
import cn.ppps.forwarder.widget.material.TitleBar
import com.xuexiang.xutil.resource.ResUtils.getDrawable
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.Date

@Page(name = "Frpc")
@Suppress("PrivatePropertyName", "DEPRECATION")
class FrpcFragment : BaseFragment<FragmentTasksActionFrpcBinding?>(), View.OnClickListener {

    private val TAG: String = FrpcFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private var mCountDownHelper: CountDownButtonHelper? = null

    //AllFrpcdropdown
    private var frpcListAll = mutableListOf<Frpc>()
    private val frpcSpinnerList = mutableListOf<FrpcSpinnerItem>()
    private lateinit var frpcSpinnerAdapter: FrpcSpinnerAdapter<*>

    //SelectedFrpcList
    private var frpcUid = ""
    private var frpcListSelected = mutableListOf<Frpc>()
    private lateinit var frpcRecyclerView: RecyclerView
    private lateinit var frpcRecyclerAdapter: FrpcRecyclerAdapter

    @JvmField
    @AutoWired(name = KEY_EVENT_DATA_ACTION)
    var eventData: String? = null

    // This fork ships no tunnel client (App.FrpclibInited is always false). The editor then explains that,
    // shows a saved action read-only and never changes or replaces its stored configuration.
    private val tunnelAvailable: Boolean get() = cn.ppps.forwarder.App.FrpclibInited

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentTasksActionFrpcBinding {
        return FragmentTasksActionFrpcBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.task_frpc)
        return titleBar
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        //Add a countdown to the test button to prevent repeated clicks
        mCountDownHelper = CountDownButtonHelper(binding!!.btnTest, 2)
        mCountDownHelper!!.setOnCountDownListener(object : CountDownButtonHelper.OnCountDownListener {
            override fun onCountDown(time: Int) {
                binding!!.btnTest.text = String.format(getString(R.string.seconds_n), time)
            }

            override fun onFinished() {
                binding!!.btnTest.text = getString(R.string.test)
                //AccessFrpcList
                getFrpcList()
            }
        })

        if (eventData != null) {
            val settingVo = Gson().fromJson(eventData, FrpcSetting::class.java)
            binding!!.rgFrpcState.check(if (settingVo.action == "start") R.id.rb_start_server else R.id.rb_stop_server)
            // Show the configurations stored in this action instead of an empty list.
            frpcListSelected.addAll(settingVo.frpcList.orEmpty()) // Gson leaves it null in very old JSON
        }

        //InitializeFrpcdropdown
        initFrpc()
        showAvailability()
    }

    private fun showAvailability() {
        val b = binding!!
        b.editorFormFrpcUnavailable.visibility = if (tunnelAvailable) View.GONE else View.VISIBLE
        if (tunnelAvailable) return
        // Nothing here can run: keep the stored choice visible, but do not offer to change, apply or test it.
        listOf(b.rbStartServer, b.rbStopServer, b.spFrpc, b.btnSave, b.btnTest).forEach { it.isEnabled = false }
    }

    override fun onDestroyView() {
        if (mCountDownHelper != null) mCountDownHelper!!.recycle()
        super.onDestroyView()
    }

    @SuppressLint("SetTextI18n")
    override fun initListeners() {
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        if (!tunnelAvailable && v.id != R.id.btn_del) return
        try {
            when (v.id) {
                R.id.btn_test -> {
                    mCountDownHelper?.start()
                    try {
                        val settingVo = checkSetting()
                        val taskAction = TaskSetting(TASK_ACTION_FRPC, getString(R.string.task_frpc), settingVo.description, Gson().toJson(settingVo), requestCode)
                        val taskActionsJson = Gson().toJson(arrayListOf(taskAction))
                        val msgInfo = MsgInfo("task", getString(R.string.task_frpc), settingVo.description, Date(), getString(R.string.task_frpc))
                        val actionData = Data.Builder().putLong(TaskWorker.TASK_ID, 0).putString(TaskWorker.TASK_ACTIONS, taskActionsJson).putString(TaskWorker.MSG_INFO, Gson().toJson(msgInfo)).build()
                        val actionRequest = OneTimeWorkRequestBuilder<ActionWorker>().setInputData(actionData).build()
                        WorkManager.getInstance().enqueue(actionRequest)
                    } catch (e: Exception) {
                        mCountDownHelper?.finish()
                        e.printStackTrace()
                        Log.e(TAG, "onClick error: ${e.message}")
                        XToastUtils.error(e.message.toString(), 30000)
                    }
                    return
                }

                R.id.btn_del -> {
                    popToBack()
                    return
                }

                R.id.btn_save -> {
                    val settingVo = checkSetting()
                    val intent = Intent()
                    intent.putExtra(KEY_BACK_DESCRIPTION_ACTION, settingVo.description)
                    intent.putExtra(KEY_BACK_DATA_ACTION, Gson().toJson(settingVo))
                    setFragmentResult(TASK_ACTION_FRPC, intent)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString(), 30000)
            e.printStackTrace()
            Log.e(TAG, "onClick error: ${e.message}")
        }
    }

    //InitializeFrpc
    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun initFrpc() {
        //InitializeFrpcdropdown
        binding!!.spFrpc.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
            try {
                val item = frpcSpinnerAdapter.getItemSource(position) as FrpcSpinnerItem
                frpcUid = item.uid
                if (frpcUid.isNotEmpty()) {
                    frpcListSelected.forEach {
                        if (frpcUid == it.uid) {
                            XToastUtils.warning(getString(R.string.frpc_contains_tips))
                            return@setOnItemClickListener
                        }
                    }
                    frpcListAll.forEach {
                        if (frpcUid == it.uid) {
                            frpcListSelected.add(it)
                        }
                    }
                    frpcRecyclerAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                XToastUtils.error(e.message.toString())
            }
        }

        // Initialized SelectedFrpcList RecyclerView and Adapter
        frpcRecyclerView = binding!!.recyclerFrpcs
        frpcRecyclerAdapter = FrpcRecyclerAdapter(frpcListSelected, if (!tunnelAvailable) null else { position ->
            frpcListSelected.removeAt(position)
            frpcRecyclerAdapter.notifyItemRemoved(position)
            frpcRecyclerAdapter.notifyItemRangeChanged(position, frpcListSelected.size) // Update Index
        })
        frpcRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = frpcRecyclerAdapter
        }
        val frpcMoveCallback = ItemMoveCallback(object : ItemMoveCallback.Listener {
            override fun onItemMove(fromPosition: Int, toPosition: Int) {
                Log.d(TAG, "onItemMove: $fromPosition $toPosition")
                frpcRecyclerAdapter.onItemMove(fromPosition, toPosition)
                frpcListSelected = frpcRecyclerAdapter.itemList
            }

            override fun onDragFinished() {
                frpcListSelected = frpcRecyclerAdapter.itemList
                //frpcRecyclerAdapter.notifyDataSetChanged()
            }
        })
        val frpcTouchHelper = ItemTouchHelper(frpcMoveCallback)
        frpcTouchHelper.attachToRecyclerView(frpcRecyclerView)
        frpcRecyclerAdapter.setTouchHelper(frpcTouchHelper)

        //AccessFrpcList
        getFrpcList()
    }

    //AccessFrpcList
    private fun getFrpcList() {
        Core.frpc.getAll().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(object : SingleObserver<List<Frpc>> {
            override fun onSubscribe(d: Disposable) {}

            override fun onError(e: Throwable) {
                e.printStackTrace()
                Log.e(TAG, "getFrpcList error: ${e.message}")
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onSuccess(frpcList: List<Frpc>) {
                if (frpcList.isEmpty()) {
                    if (tunnelAvailable) XToastUtils.error(R.string.add_frpc_first)
                    return
                }

                frpcSpinnerList.clear()
                frpcListAll = frpcList as MutableList<Frpc>
                for (frpc in frpcList) {
                    val name = if (frpc.name.length > 20) frpc.name.substring(0, 19) else frpc.name
                    frpcSpinnerList.add(FrpcSpinnerItem(name, getDrawable(frpc.imageId), frpc.uid, frpc.status))
                }
                frpcSpinnerAdapter = FrpcSpinnerAdapter(frpcSpinnerList).setIsFilterKey(true).setFilterColor(String.format("#%06X", com.google.android.material.color.MaterialColors.getColor(binding!!.root, com.google.android.material.R.attr.colorPrimary) and 0xFFFFFF)).setBackgroundSelector(R.drawable.selector_custom_spinner_bg)
                binding!!.spFrpc.setAdapter(frpcSpinnerAdapter)
                //frpcSpinnerAdapter.notifyDataSetChanged()

                //UpdatefrpcListSelectedStatus and Name
                frpcListSelected.forEach {
                    frpcListAll.forEach { frpc ->
                        if (it.uid == frpc.uid) {
                            it.name = frpc.name
                            it.autorun = frpc.autorun
                        }
                    }
                }
                frpcRecyclerAdapter.notifyDataSetChanged()

            }
        })
    }

    //Check Settings
    @SuppressLint("SetTextI18n")
    private fun checkSetting(): FrpcSetting {
        val description = StringBuilder()
        val action = if (binding!!.rgFrpcState.checkedRadioButtonId == R.id.rb_start_server) {
            description.append(getString(R.string.enable))
            "start"
        } else {
            description.append(getString(R.string.disable))
            "stop"
        }
        if (frpcListSelected.isEmpty()) {
            description.append(getString(R.string.all_auto_started_frpc))
        } else {
            description.append(getString(R.string.specified_frpc)).append(": ")
            description.append(frpcListSelected.joinToString(",") { it.name })
        }

        return FrpcSetting(description.toString(), action, frpcListSelected)
    }
}