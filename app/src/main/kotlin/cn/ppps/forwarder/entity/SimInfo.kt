package cn.ppps.forwarder.entity

import com.google.gson.annotations.SerializedName
import java.io.Serializable

//SIMCard Info
data class SimInfo(
    //Carrier: China Mobile, China Unicom or China Telecom
    @SerializedName("carrier_name")
    var mCarrierName: String? = null,
    //Integrated circuit card identifier, i.e.SIMCard number.
    @SerializedName("icc_id")
    var mIccId: String? = null,
    //SIM slotid：-1=not inserted, 0=SIM slot1 ,1=SIM slot2
    @SerializedName("sim_slot_index")
    var mSimSlotIndex: Int = 0,
    //Number
    @SerializedName("number")
    var mNumber: String? = null,
    //Country code
    @SerializedName("country_iso")
    var mCountryIso: String? = null,
    //SIMof Subscription Id (SIMInsert Order)
    @SerializedName("subscription_id")
    var mSubscriptionId: Int = 0,
) : Serializable {
    override fun toString(): String {
        return "SimInfo{" +
                "mCarrierName=" + mCarrierName +
                ", mIccId=" + mIccId +
                ", mSimSlotIndex=" + mSimSlotIndex +
                ", mNumber=" + mNumber +
                ", mCountryIso=" + mCountryIso +
                ", mSubscriptionId=" + mSubscriptionId +
                '}'
    }
}
