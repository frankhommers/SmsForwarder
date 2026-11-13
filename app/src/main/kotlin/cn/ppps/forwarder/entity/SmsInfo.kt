package cn.ppps.forwarder.entity

import com.google.gson.annotations.SerializedName
import cn.ppps.forwarder.R
import java.io.Serializable

data class SmsInfo(
    // Contact name
    var name: String = "",
    // Contact number
    var number: String = "",
    // Text Message Contents
    var content: String = "",
    // SMS Time
    var date: Long = 0L,
    // SMS Type: 1=Receive, 2=Send
    var type: Int = 1,
    // SIM slotID： 0=Sim1, 1=Sim2, -1=Failed to fetch
    @SerializedName("sim_id")
    var simId: Int = -1,
    // SIM subscription ID
    @SerializedName("sub_id")
    var subId: Int = 0,
) : Serializable {

    val typeImageId: Int = R.drawable.ic_sms

    val simImageId: Int
        get() {
            return when (simId) {
                0 -> R.drawable.ic_sim1
                1 -> R.drawable.ic_sim2
                else -> R.drawable.ic_sim
            }
        }
}
