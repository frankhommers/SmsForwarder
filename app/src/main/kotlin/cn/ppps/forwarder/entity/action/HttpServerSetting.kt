package cn.ppps.forwarder.entity.action

import cn.ppps.forwarder.utils.HttpServerUtils
import java.io.Serializable

data class HttpServerSetting(
    var description: String = "", //Description
    var action: String = "start", //Actions: start=Start, stop=Stop
    var enableApiClone: Boolean = HttpServerUtils.enableApiClone, //Whether to enable a one-click clone
    var enableApiSmsSend: Boolean = HttpServerUtils.enableApiSmsSend, //Enable remote texting
    var enableApiSmsQuery: Boolean = HttpServerUtils.enableApiSmsQuery, //Enable remote checking of text messages
    var enableApiCallQuery: Boolean = HttpServerUtils.enableApiCallQuery, //Enable remote checking of call records
    var enableApiContactQuery: Boolean = HttpServerUtils.enableApiContactQuery, //Enable remote checking of contacts
    var enableApiContactAdd: Boolean = HttpServerUtils.enableApiContactAdd, //Enable adding contacts remotely
    var enableApiWol: Boolean = HttpServerUtils.enableApiWol, //Enable RemoteWOL
    var enableApiLocation: Boolean = HttpServerUtils.enableApiLocation, //Whether or not to enable remote search cell phones
    var enableApiBatteryQuery: Boolean = HttpServerUtils.enableApiBatteryQuery, //Enable remote battery queries
) : Serializable
