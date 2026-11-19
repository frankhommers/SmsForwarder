package cn.ppps.forwarder.entity.condition

import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.utils.TASK_CONDITION_LEAVE_ADDRESS
import cn.ppps.forwarder.utils.TASK_CONDITION_TO_ADDRESS
import com.google.gson.Gson

/**
 * Pure rules for the Arrive/Leave location conditions. No Android views, no location requests.
 *
 * Address-keyword matching ("address") needs reverse geocoding, which this app does not do:
 * LocationService always stores an empty address. Such settings are kept unchanged as
 * unsupported legacy data; they are never converted or deleted here.
 */
object LocationConditionRules {

    const val CALC_DISTANCE = "distance"
    const val CALC_ADDRESS = "address"

    enum class Field { LONGITUDE, LATITUDE, RADIUS }

    data class Coordinates(val longitude: Double, val latitude: Double, val radius: Double)

    /** Either valid coordinates, or the fields that are invalid (in form order). */
    data class Result(val coordinates: Coordinates?, val invalid: List<Field>)

    fun isLegacyAddressMode(setting: LocationSetting?): Boolean = setting?.calcType == CALC_ADDRESS

    /** True for a stored Arrive/Leave condition that uses unsupported address matching. */
    fun isLegacyAddressCondition(type: Int, settingJson: String?): Boolean {
        if (type != TASK_CONDITION_TO_ADDRESS && type != TASK_CONDITION_LEAVE_ADDRESS) return false
        if (settingJson.isNullOrBlank()) return false
        return try {
            isLegacyAddressMode(Gson().fromJson(settingJson, LocationSetting::class.java))
        } catch (e: Exception) {
            false
        }
    }

    /** Accepts a decimal point or a single decimal comma; rejects blanks, NaN and infinities. */
    fun parse(text: CharSequence?): Double? {
        val raw = text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val normalized = if (!raw.contains('.') && raw.count { it == ',' } == 1) raw.replace(',', '.') else raw
        if (!Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun isValidLongitude(value: Double?): Boolean = value != null && value.isFinite() && value >= -180.0 && value <= 180.0
    fun isValidLatitude(value: Double?): Boolean = value != null && value.isFinite() && value >= -90.0 && value <= 90.0
    fun isValidRadius(value: Double?): Boolean = value != null && value.isFinite() && value > 0.0

    fun validate(longitude: CharSequence?, latitude: CharSequence?, radius: CharSequence?): Result {
        val lon = parse(longitude)
        val lat = parse(latitude)
        val rad = parse(radius)
        val invalid = buildList {
            if (!isValidLongitude(lon)) add(Field.LONGITUDE)
            if (!isValidLatitude(lat)) add(Field.LATITUDE)
            if (!isValidRadius(rad)) add(Field.RADIUS)
        }
        return Result(if (invalid.isEmpty()) Coordinates(lon!!, lat!!, rad!!) else null, invalid)
    }

    /**
     * Whether the cached location is an actual fix. LocationService stores the fix time and provider with
     * every fix and clears the cache to an empty LocationInfo; 0/0 is a valid point (equator/prime meridian).
     */
    fun hasCachedFix(location: LocationInfo?): Boolean =
        location != null && (location.time.isNotBlank() || location.provider.isNotBlank()) &&
            isValidLongitude(location.longitude) && isValidLatitude(location.latitude)

    fun isValidDistanceSetting(setting: LocationSetting): Boolean =
        isValidLongitude(setting.longitude) && isValidLatitude(setting.latitude) && isValidRadius(setting.distance)

    /** Text shown in a coordinate field; empty when nothing was chosen, so no default pretends to be a point. */
    fun fieldText(value: Double, blank: Boolean): String = if (blank) "" else value.toString()
}
