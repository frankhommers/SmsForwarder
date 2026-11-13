package cn.ppps.forwarder.entity.setting

import cn.ppps.forwarder.R
import java.io.Serializable

data class SocketSetting(
    val method: String = "MQTT",
    var address: String = "", //IPAddress
    val port: Int = 0, //Port Number
    val msgTemplate: String = "", //Message Template
    val secret: String = "", //Sign Key
    val response: String = "", //Keywords for Successful Response
    val username: String = "", //Username
    val password: String = "", //Password
    val inCharset: String = "", //Input encoding
    val outCharset: String = "", //Output encoding
    val inMessageTopic: String = "", //Mqttonly: incoming topic to subscribe to
    val outMessageTopic: String = "", //Mqttonly: outgoing topic to publish to
    val uriType: String = "tcp", //Mqttonly: communication transport; defaulttcp
    val path: String = "", //Mqttonly: communication path forwsSettings for communicationuri，Resulting URL"${uriType}://${address}:${port}${path}"
    val clientId: String = "", //Mqttonly: clientID，Random value if empty
    val qos: Int = 0, //MqttExclusive，QoSQuality of services
    val retained: Boolean = false, //Mqttonly: retain messages（Retained Message）
) : Serializable {

    fun getMethodCheckId(): Int {
        return when (method) {
            "MQTT" -> R.id.rb_method_mqtt
            "TCP" -> R.id.rb_method_tcp
            "UDP" -> R.id.rb_method_udp
            else -> R.id.rb_method_mqtt
        }
    }

    fun getUriTypeCheckId(): Int {
        return when (uriType) {
            "ssl" -> R.id.rb_uriType_ssl
            else -> R.id.rb_uriType_tcp
        }
    }

    fun getQosCheckId(): Int {
        return when (qos) {
            1 -> R.id.rb_qos_1
            2 -> R.id.rb_qos_2
            else -> R.id.rb_qos_0
        }
    }

}