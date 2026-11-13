package cn.ppps.forwarder.entity.condition

import cn.ppps.forwarder.R
import java.io.Serializable

data class LocationSetting(
    var description: String = "", //Description
    var type: String = "to", //Monitor Type："to"：Location of arrival；"leave"：Leave the place.
    var calcType: String = "distance", //Method of calculation："distance"：Calculating Distance；"address"：Address Match
    var longitude: Double = 0.0, //Longitude
    var latitude: Double = 0.0, //Latitude
    var distance: Double = 0.0, //Distance
    var address: String = "", //Address
) : Serializable {

    fun getCalcTypeCheckId(): Int {
        return when (calcType) {
            "distance" -> R.id.rb_calc_type_distance
            "address" -> R.id.rb_calc_type_address
            else -> R.id.rb_calc_type_distance
        }
    }

}
