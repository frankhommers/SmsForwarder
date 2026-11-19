package cn.ppps.forwarder.fragment.senders

import android.text.TextUtils
import cn.ppps.forwarder.utils.WebhookEditorModel
import cn.ppps.forwarder.utils.ItemRows
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.fragment.app.viewModels
import com.google.gson.Gson
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.database.viewmodel.BaseViewModelFactory
import cn.ppps.forwarder.database.viewmodel.SenderViewModel
import cn.ppps.forwarder.databinding.FragmentSendersWebhookBinding
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.setting.WebhookSetting
import cn.ppps.forwarder.utils.CommonUtils
import cn.ppps.forwarder.utils.EVENT_TOAST_ERROR
import cn.ppps.forwarder.utils.KEY_SENDER_CLONE
import cn.ppps.forwarder.utils.KEY_SENDER_ID
import cn.ppps.forwarder.utils.KEY_SENDER_TEST
import cn.ppps.forwarder.utils.KEY_SENDER_TYPE
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import cn.ppps.forwarder.utils.sender.WebhookUtils
import com.jeremyliao.liveeventbus.LiveEventBus
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.utils.CountDownButtonHelper
import cn.ppps.forwarder.widget.material.TitleBar
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.net.Proxy
import java.util.Date

@Page(name = "Webhook")
@Suppress("PrivatePropertyName")
class WebhookFragment : BaseFragment<FragmentSendersWebhookBinding?>(), View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private val TAG: String = WebhookFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private val viewModel by viewModels<SenderViewModel> { BaseViewModelFactory(context) }
    private var mCountDownHelper: CountDownButtonHelper? = null
    private var originalJson: String? = null
    private var mode = WebhookEditorModel.Mode.PARAMETERS
    private var selectedMethod = "POST"
    private var updatingMode = false
    private lateinit var parameterRows: ItemRows
    private var headerItemMap = HashMap<Int, LinearLayout>(2)

    @JvmField
    @AutoWired(name = KEY_SENDER_ID)
    var senderId: Long = 0

    @JvmField
    @AutoWired(name = KEY_SENDER_TYPE)
    var senderType: Int = 0

    @JvmField
    @AutoWired(name = KEY_SENDER_CLONE)
    var isClone: Boolean = false

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSendersWebhookBinding {
        return FragmentSendersWebhookBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.webhook)
        return titleBar
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        parameterRows = ItemRows(binding!!.etWebParams, "&", true)
        updatePayloadUi()
        // Insert data / Preview for the request data: local only, never sends (see TemplatePicker).
        cn.ppps.forwarder.widget.material.TemplatePicker.installWebhook(
            binding!!.glWebhookParams,
            target = {
                val focused = binding!!.root.findFocus() as? EditText
                val inRows = generateSequence(focused?.parent as? View) { it.parent as? View }.any { it === parameterRows.view }
                when {
                    focused != null && (focused === binding!!.etWebParams || inRows) -> focused
                    mode != WebhookEditorModel.Mode.PARAMETERS -> binding!!.etWebParams
                    else -> null
                }
            },
            payload = { binding!!.etWebParams.text.toString() },
            method = { currentMethod() },
            jsonOrTextHeader = {
                // Read-only: unlike getHeadersFromHeaderItemMap this never marks a header row as invalid.
                val type = headerItemMap.values.firstOrNull { it.findViewById<EditText>(R.id.editTextHeaderKey).text.toString().trim().equals("Content-Type", true) }
                    ?.findViewById<EditText>(R.id.editTextHeaderValue)?.text?.toString().orEmpty()
                // JSON mode always ends up as a JSON body: Save adds the JSON content type for an array body.
                mode == WebhookEditorModel.Mode.JSON || type.contains("application/json") || type.startsWith("text/")
            },
            hasSecret = { binding!!.etSecret.text.toString().isNotEmpty() },
        )
        //Add a countdown to the test button to prevent repeated clicks
        mCountDownHelper = CountDownButtonHelper(binding!!.btnTest, SettingUtils.requestTimeout)
        mCountDownHelper!!.setOnCountDownListener(object : CountDownButtonHelper.OnCountDownListener {
            override fun onCountDown(time: Int) {
                binding!!.btnTest.text = String.format(getString(R.string.seconds_n), time)
            }

            override fun onFinished() {
                binding!!.btnTest.text = getString(R.string.test)
            }
        })

        //Add
        if (senderId <= 0) {
            titleBar?.setSubTitle(getString(R.string.add_sender))
            binding!!.btnDel.setText(R.string.discard)
            return
        }

        //Edit
        binding!!.btnDel.setText(R.string.del)
        Core.sender.get(senderId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(object : SingleObserver<Sender> {
            override fun onSubscribe(d: Disposable) {}

            override fun onError(e: Throwable) {
                e.printStackTrace()
                Log.e(TAG, "onError:$e")
            }

            override fun onSuccess(sender: Sender) {
                if (isClone) {
                    titleBar?.setSubTitle(getString(R.string.clone_sender) + ": " + sender.name)
                    binding!!.btnDel.setText(R.string.discard)
                } else {
                    titleBar?.setSubTitle(getString(R.string.edit_sender) + ": " + sender.name)
                }
                binding!!.etName.setText(sender.name)
                binding!!.sbEnable.isChecked = sender.status == 1
                val settingVo = Gson().fromJson(sender.jsonSetting, WebhookSetting::class.java)
                originalJson = sender.jsonSetting
                if (settingVo != null) {
                    updatingMode = true
                    binding!!.rgMethod.check(settingVo.getMethodCheckId())
                    selectedMethod = settingVo.method
                    binding!!.etWebServer.setText(settingVo.webServer)
                    binding!!.etSecret.setText(settingVo.secret)
                    binding!!.etResponse.setText(settingVo.response)
                    binding!!.etWebParams.setText(settingVo.webParams)
                    //set header
                    for ((key, value) in settingVo.headers) {
                        addHeaderItemLinearLayout(
                            headerItemMap, binding!!.layoutHeaders, key, value
                        )
                    }
                    binding!!.rgProxyType.check(settingVo.getProxyTypeCheckId())
                    binding!!.etProxyHost.setText(settingVo.proxyHost)
                    binding!!.etProxyPort.setText(settingVo.proxyPort)
                    binding!!.sbProxyAuthenticator.isChecked = settingVo.proxyAuthenticator == true
                    binding!!.etProxyUsername.setText(settingVo.proxyUsername)
                    binding!!.etProxyPassword.setText(settingVo.proxyPassword)
                    mode = WebhookEditorModel.infer(settingVo.method, settingVo.webParams, settingVo.headers)
                    updatingMode = false
                    updatePayloadUi()
                    setAdvanced(settingVo.headers.isNotEmpty() || settingVo.secret.isNotEmpty() || settingVo.response.isNotEmpty() ||
                        settingVo.proxyType != Proxy.Type.DIRECT || settingVo.proxyHost.isNotEmpty() || settingVo.proxyPort.isNotEmpty() ||
                        settingVo.proxyAuthenticator || settingVo.proxyUsername.isNotEmpty() || settingVo.proxyPassword.isNotEmpty())
                }
            }
        })
    }

    override fun initListeners() {
        cn.ppps.forwarder.widget.material.FormHelp.install(binding!!.btnHelpParams, R.string.webhook_params, R.string.webhook_params_dialog)
        cn.ppps.forwarder.widget.material.FormHelp.install(binding!!.btnHelpSecret, R.string.form_secret_label, R.string.webhook_sign_dialog)
        cn.ppps.forwarder.widget.material.FormHelp.install(binding!!.btnHelpResponse, R.string.form_response_label, R.string.webhook_response_dialog)
        cn.ppps.forwarder.widget.material.FormHelp.install(binding!!.btnHelpHeaders, R.string.headers, R.string.webhook_headers_dialog)
        binding!!.btnAdvanced.setOnClickListener { setAdvanced(binding!!.layoutAdvanced.visibility != View.VISIBLE) }
        binding!!.rgMethod.setOnCheckedChangeListener { _, _ ->
            if (!updatingMode) {
                val next = currentMethod()
                if ((binding!!.etWebParams.text.toString().isNotEmpty() || (mode == WebhookEditorModel.Mode.PARAMETERS && parameterRows.hasItems())) && next != selectedMethod) {
                    XToastUtils.error(R.string.webhook_mode_change)
                    updatingMode = true
                    binding!!.rgMethod.check(when(selectedMethod) { "GET" -> R.id.rb_method_get; "PUT" -> R.id.rb_method_put; "PATCH" -> R.id.rb_method_patch; else -> R.id.rb_method_post })
                    updatingMode = false
                } else { selectedMethod = next; if (next == "GET") mode = WebhookEditorModel.Mode.PARAMETERS; updatePayloadUi() }
            }
        }
        binding!!.rgPayloadMode.setOnCheckedChangeListener { _, id ->
            if (!updatingMode) {
                val next = when(id) { R.id.rb_payload_json -> WebhookEditorModel.Mode.JSON; R.id.rb_payload_legacy -> WebhookEditorModel.Mode.LEGACY; else -> WebhookEditorModel.Mode.PARAMETERS }
                if (next != mode && (binding!!.etWebParams.text.toString().isNotEmpty() || (mode == WebhookEditorModel.Mode.PARAMETERS && parameterRows.hasItems()))) XToastUtils.error(R.string.webhook_mode_change)
                else mode = next
                updatePayloadUi()
            }
        }
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
        binding!!.btnAddHeader.setOnClickListener {
            addHeaderItemLinearLayout(headerItemMap, binding!!.layoutHeaders, null, null)
        }
        binding!!.sbProxyAuthenticator.setOnCheckedChangeListener(this)
        binding!!.rgProxyType.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            if (checkedId == R.id.rb_proxyHttp || checkedId == R.id.rb_proxySocks) {
                binding!!.layoutProxyHost.visibility = View.VISIBLE
                binding!!.layoutProxyPort.visibility = View.VISIBLE
                binding!!.layoutProxyAuthenticator.visibility = if (binding!!.sbProxyAuthenticator.isChecked) View.VISIBLE else View.GONE
            } else {
                binding!!.layoutProxyHost.visibility = View.GONE
                binding!!.layoutProxyPort.visibility = View.GONE
                binding!!.layoutProxyAuthenticator.visibility = View.GONE
            }
        }
        LiveEventBus.get(KEY_SENDER_TEST, String::class.java).observe(this) { mCountDownHelper?.finish() }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        //Only one listener; no additional check is neededid
        binding!!.layoutProxyAuthenticator.visibility = if (isChecked) View.VISIBLE else View.GONE
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_test -> {
                    val settingVo = checkSetting()
                    val name = binding!!.etName.text.toString().trim().takeIf { it.isNotEmpty() } ?: getString(R.string.test_sender_name)
                    mCountDownHelper?.start()
                    Thread {
                        try {
                            val msgInfo = MsgInfo("sms", getString(R.string.test_phone_num), String.format(getString(R.string.test_sender_sms), name), Date(), getString(R.string.test_sim_info))
                            WebhookUtils.sendMsg(settingVo, msgInfo)
                        } catch (e: Exception) {
                            LiveEventBus.get(EVENT_TOAST_ERROR, String::class.java).post(getString(R.string.webhook_result_failed))
                        }
                        LiveEventBus.get(KEY_SENDER_TEST, String::class.java).post("finish")
                    }.start()
                    return
                }

                R.id.btn_del -> {
                    if (senderId <= 0 || isClone) {
                        popToBack()
                        return
                    }

                    MaterialDialog.Builder(requireContext()).title(R.string.delete_sender_title).content(R.string.delete_sender_tips).positiveText(R.string.lab_yes).negativeText(R.string.lab_no).onPositive { _: MaterialDialog?, _: DialogAction? ->
                        viewModel.delete(senderId)
                        XToastUtils.success(R.string.delete_sender_toast)
                        popToBack()
                    }.show()
                    return
                }

                R.id.btn_save -> {
                    val name = binding!!.etName.text.toString().trim()
                    if (TextUtils.isEmpty(name)) {
                        cn.ppps.forwarder.widget.material.FormValidation.show(binding!!.etName, getString(R.string.invalid_name))
                        return
                    }

                    val status = if (binding!!.sbEnable.isChecked) 1 else 0
                    val settingVo = checkSetting()
                    if (isClone) senderId = 0
                    val senderNew = Sender(senderId, senderType, name, WebhookEditorModel.merge(originalJson, Gson().toJsonTree(settingVo).asJsonObject), status)


                    viewModel.insertOrUpdate(senderNew)
                    XToastUtils.success(R.string.tipSaveSuccess)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString())

        }
    }

    private fun currentMethod() = when(binding!!.rgMethod.checkedRadioButtonId) {
        R.id.rb_method_get -> "GET"; R.id.rb_method_put -> "PUT"; R.id.rb_method_patch -> "PATCH"; else -> "POST"
    }

    private fun setAdvanced(expanded: Boolean) {
        binding!!.layoutAdvanced.visibility = if (expanded) View.VISIBLE else View.GONE
        binding!!.btnAdvanced.setText(if (expanded) R.string.webhook_advanced_hide else R.string.webhook_advanced_show)
    }

    private fun updatePayloadUi() {
        updatingMode = true
        binding!!.rbPayloadJson.visibility = if (currentMethod() == "GET") View.GONE else View.VISIBLE
        binding!!.rbPayloadLegacy.visibility = if (mode == WebhookEditorModel.Mode.LEGACY) View.VISIBLE else View.GONE
        binding!!.rgPayloadMode.check(when(mode) {
            WebhookEditorModel.Mode.JSON -> R.id.rb_payload_json
            WebhookEditorModel.Mode.LEGACY -> R.id.rb_payload_legacy
            else -> R.id.rb_payload_parameters
        })
        parameterRows.view.visibility = if (mode == WebhookEditorModel.Mode.PARAMETERS) View.VISIBLE else View.GONE
        binding!!.payloadTextContainer.visibility = if (mode == WebhookEditorModel.Mode.PARAMETERS) View.GONE else View.VISIBLE
        binding!!.etWebParams.visibility = if (mode == WebhookEditorModel.Mode.PARAMETERS) View.GONE else View.VISIBLE
        binding!!.payloadHelp.setText(when(mode) {
            WebhookEditorModel.Mode.JSON -> R.string.webhook_json_help
            WebhookEditorModel.Mode.LEGACY -> R.string.webhook_legacy_help
            else -> if (currentMethod() == "GET") R.string.webhook_query_help else R.string.webhook_form_help
        })
        updatingMode = false
    }

    private fun checkSetting(): WebhookSetting {
        val webServer = binding!!.etWebServer.text.toString().trim()
        val uri = try { java.net.URI(webServer) } catch (_: Exception) { null }
        if (uri == null || uri.scheme !in listOf("https", "http") || uri.host.isNullOrEmpty()) {
            cn.ppps.forwarder.widget.material.FormValidation.show(binding!!.etWebServer, getString(R.string.webhook_url_invalid))
            throw Exception(getString(R.string.webhook_url_invalid))
        }

        val method = when (binding!!.rgMethod.checkedRadioButtonId) {
            R.id.rb_method_get -> "GET"
            R.id.rb_method_put -> "PUT"
            R.id.rb_method_patch -> "PATCH"
            else -> "POST"
        }
        val secret = binding!!.etSecret.text.toString()
        val response = binding!!.etResponse.text.toString()
        val webParams = binding!!.etWebParams.text.toString()
        val headers = getHeadersFromHeaderItemMap(headerItemMap).toMutableMap()
        val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value.orEmpty()
        if (mode == WebhookEditorModel.Mode.PARAMETERS) {
            if (!parameterRows.validate()) throw Exception(getString(R.string.item_invalid))
            if (method != "GET" && (contentType.contains("application/json") || contentType.startsWith("text/")))
                throw Exception(getString(R.string.webhook_type_conflict))
        }
        if (mode == WebhookEditorModel.Mode.JSON) {
            if (!WebhookEditorModel.validJson(webParams)) throw Exception(getString(R.string.webhook_json_invalid))
            if (contentType.isNotEmpty() && !contentType.contains("application/json")) throw Exception(getString(R.string.webhook_type_conflict))
            if (contentType.isEmpty() && webParams.trim().startsWith("[")) headers["Content-Type"] = "application/json"
        }

        val proxyType: Proxy.Type = when (binding!!.rgProxyType.checkedRadioButtonId) {
            R.id.rb_proxyHttp -> Proxy.Type.HTTP
            R.id.rb_proxySocks -> Proxy.Type.SOCKS
            else -> Proxy.Type.DIRECT
        }
        val proxyHost = binding!!.etProxyHost.text.toString().trim()
        val proxyPort = binding!!.etProxyPort.text.toString().trim()

        if (proxyType != Proxy.Type.DIRECT && (TextUtils.isEmpty(proxyHost) || TextUtils.isEmpty(proxyPort))) {
            throw Exception(getString(R.string.invalid_host_or_port))
        }

        if (proxyType != Proxy.Type.DIRECT && proxyPort.toIntOrNull() !in 1..65535) throw Exception(getString(R.string.webhook_port_invalid))
        val proxyAuthenticator = binding!!.sbProxyAuthenticator.isChecked
        val proxyUsername = binding!!.etProxyUsername.text.toString()
        val proxyPassword = binding!!.etProxyPassword.text.toString()
        if (proxyAuthenticator && TextUtils.isEmpty(proxyUsername) && TextUtils.isEmpty(proxyPassword)) {
            throw Exception(getString(R.string.invalid_username_or_password))
        }

        return WebhookSetting(method, webServer, secret, response, webParams, headers, proxyType, proxyHost, proxyPort, proxyAuthenticator, proxyUsername, proxyPassword)
    }


    //headerSerial number
    private var headerItemId = 0

    /**
     * Dynamic Adds & Deletesheader
     *
     * @param headerItemMap                Managementitemofmap，For deleting designationsheader
     * @param linearLayoutWebNotifyHeaders Needs MountingitemofLinearLayout
     * @param key                          headerofkey，Do not set when empty
     * @param value                        headerofvalue，Do not set when empty
     */
    private fun addHeaderItemLinearLayout(
        headerItemMap: MutableMap<Int, LinearLayout>, linearLayoutWebNotifyHeaders: LinearLayout, key: String?, value: String?
    ) {
        val linearLayoutItemAddHeader = View.inflate(requireContext(), R.layout.item_add_header, null) as LinearLayout
        val imageViewRemoveHeader = linearLayoutItemAddHeader.findViewById<ImageView>(R.id.imageViewRemoveHeader)
        if (key != null && value != null) {
            val editTextHeaderKey = linearLayoutItemAddHeader.findViewById<EditText>(R.id.editTextHeaderKey)
            val editTextHeaderValue = linearLayoutItemAddHeader.findViewById<EditText>(R.id.editTextHeaderValue)
            editTextHeaderKey.setText(key)
            editTextHeaderValue.setText(value)
        }
        imageViewRemoveHeader.tag = headerItemId
        imageViewRemoveHeader.setOnClickListener { view2: View ->
            val itemId = view2.tag as Int
            linearLayoutWebNotifyHeaders.removeView(headerItemMap[itemId])
            headerItemMap.remove(itemId)
        }
        linearLayoutWebNotifyHeaders.addView(linearLayoutItemAddHeader)
        headerItemMap[headerItemId] = linearLayoutItemAddHeader
        headerItemId++
    }

    /**
     * FromEditTextFetching All in Controlsheaders
     *
     * @param headerItemMap Managementitemofmap
     * @return Allheaders
     */
    private fun getHeadersFromHeaderItemMap(headerItemMap: Map<Int, LinearLayout>): Map<String, String> {
        val headers: MutableMap<String, String> = HashMap()
        for (headerItem in headerItemMap.values) {
            val editTextHeaderKey = headerItem.findViewById<EditText>(R.id.editTextHeaderKey)
            val editTextHeaderValue = headerItem.findViewById<EditText>(R.id.editTextHeaderValue)
            val key = editTextHeaderKey.text.toString().trim()
            val value = editTextHeaderValue.text.toString()
            if (!key.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) || value.contains('\n') || value.contains('\r') || headers.keys.any { it.equals(key, true) }) {
                editTextHeaderKey.error = getString(R.string.webhook_header_invalid)
                throw Exception(getString(R.string.webhook_header_invalid))
            }
            headers[key] = value
        }
        return headers
    }

    override fun onDestroyView() {
        if (mCountDownHelper != null) mCountDownHelper!!.recycle()
        super.onDestroyView()
    }

}
