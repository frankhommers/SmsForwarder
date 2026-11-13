package cn.ppps.forwarder.entity

import cn.ppps.forwarder.R
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class CallInfo(
    //Name
    var name: String = "",
    //Number
    var number: String = "",
    //Get Call Date
    var dateLong: Long = 0L,
    //Call duration in seconds
    var duration: Int = 0,
    //Call type：1.Incoming call ended 2.Outgoing call ended 3.Missed call 4.Incoming call alert 5.Incoming call answered 6.Outgoing call dialed
    var type: Int = 1,
    //Called number
    @SerializedName("via_number")
    var viaNumber: String = "",
    //SIM slotID： 0=Sim1, 1=Sim2, -1=Failed to fetch
    @SerializedName("sim_id")
    var simId: Int = -1,
    //SIM subscription ID
    @SerializedName("sub_id")
    var subId: Int = 0,
    //Whether the call was forwarded
    @SerializedName("is_forwarded")
    var isForwarded: Boolean = false
) : Serializable {

    val typeImageId: Int
        get() {
            return when (type) {
                1 -> R.drawable.ic_phone_in
                2 -> R.drawable.ic_phone_out
                else -> R.drawable.ic_phone_missed
            }
        }

    val simImageId: Int
        get() {
            return when (simId) {
                0 -> R.drawable.ic_sim1
                1 -> R.drawable.ic_sim2
                else -> R.drawable.ic_sim
            }
        }

    override fun toString(): String {
        return "CallInfo{" +
                "name='" + name + '\'' +
                ", number='" + number + '\'' +
                ", dateLong=" + dateLong +
                ", duration=" + duration +
                ", type=" + type +
                ", viaNumber=" + viaNumber +
                ", simId=" + simId +
                '}'
    }
}