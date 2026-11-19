package cn.ppps.forwarder.server.controller

import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.App
import cn.ppps.forwarder.server.model.BaseRequest
import cn.ppps.forwarder.server.model.ConfigData
import cn.ppps.forwarder.utils.AppUtils
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.PhoneUtils
import cn.ppps.forwarder.utils.SettingUtils
import com.yanzhenjie.andserver.annotation.*

@Suppress("PrivatePropertyName")
@RestController
@RequestMapping(path = ["/config"])
class ConfigController {

    private val TAG: String = CloneController::class.java.simpleName

    //Remote Check Configuration
    @CrossOrigin(methods = [RequestMethod.POST])
    @PostMapping("/query")
    fun test(@RequestBody bean: BaseRequest<*>): ConfigData {
        Log.d(TAG, "Local event processed")

        //Get SIM slot information
        if (App.SimInfoList.isEmpty()) {
            App.SimInfoList = PhoneUtils.getSimMultiInfo()
        }
        Log.d(TAG, "SIM inventory refreshed; details omitted")

        return ConfigData(
            HttpServerUtils.enableApiClone,
            HttpServerUtils.enableApiSmsSend,
            HttpServerUtils.enableApiSmsQuery,
            HttpServerUtils.enableApiCallQuery,
            HttpServerUtils.enableApiContactQuery,
            HttpServerUtils.enableApiContactAdd,
            HttpServerUtils.enableApiBatteryQuery,
            HttpServerUtils.enableApiWol,
            HttpServerUtils.enableApiLocation,
            HttpServerUtils.enableApiConfigQuery,
            HttpServerUtils.safetyMeasures,
            SettingUtils.extraDeviceMark,
            SettingUtils.extraSim1,
            SettingUtils.extraSim2,
            App.SimInfoList,
            AppUtils.getAppVersionCode(),
            AppUtils.getAppVersionName(),
        )
    }

}