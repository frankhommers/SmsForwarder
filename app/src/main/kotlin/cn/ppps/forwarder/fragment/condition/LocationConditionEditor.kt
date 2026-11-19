package cn.ppps.forwarder.fragment.condition

import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import cn.ppps.forwarder.R
import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.entity.condition.LocationConditionRules
import cn.ppps.forwarder.entity.condition.LocationConditionRules.Field
import cn.ppps.forwarder.entity.condition.LocationSetting
import cn.ppps.forwarder.widget.material.FormValidation
import com.google.gson.Gson

/**
 * Shared form logic for Arrive at / Leave Location. View-owned: one instance per fragment view.
 *
 * - New conditions start with empty coordinate fields and no errors; validation runs only on Apply.
 * - Address-keyword mode cannot be chosen for new conditions (no address lookup exists).
 * - A saved address-mode condition opens as an unsupported legacy condition: its stored JSON is
 *   returned unchanged unless the user explicitly selects GPS coordinates.
 */
class LocationConditionEditor(
    private val type: String,
    private val eventData: String?,
    private val defaultDescription: String,
    private val descriptionFormat: String,
    private val calcType: RadioGroup,
    private val distanceOption: RadioButton,
    private val addressOption: RadioButton,
    private val distanceLayout: View,
    private val addressLayout: View,
    private val legacyNotice: View,
    private val longitude: EditText,
    private val latitude: EditText,
    private val radius: EditText,
    private val address: EditText,
    private val description: TextView,
) {
    private val stored: LocationSetting? = eventData?.let {
        try { Gson().fromJson(it, LocationSetting::class.java) } catch (e: Exception) { null }
    }
    val isLegacy: Boolean = LocationConditionRules.isLegacyAddressMode(stored)
    val isNew: Boolean = stored == null

    /** The result of a successful Apply. */
    data class Applied(val description: String, val json: String)

    fun bind() {
        val setting = stored ?: LocationSetting(defaultDescription, type)
        description.text = setting.description
        // A legacy address condition only shows coordinates it could actually have used; its unused
        // defaults (0/0, radius 0) are not presented as a chosen point. The stored JSON is untouched either way.
        val blank = isNew || (isLegacy && !LocationConditionRules.isValidDistanceSetting(setting))
        longitude.setText(LocationConditionRules.fieldText(setting.longitude, blank))
        latitude.setText(LocationConditionRules.fieldText(setting.latitude, blank))
        radius.setText(LocationConditionRules.fieldText(setting.distance, blank))
        address.setText(setting.address)
        // The stored keyword is shown for recognition only; it is never edited or matched.
        address.isEnabled = false
        // New address-keyword conditions cannot be created; the option stays visible but disabled.
        addressOption.isEnabled = false
        legacyNotice.visibility = if (isLegacy) View.VISIBLE else View.GONE
        calcType.setOnCheckedChangeListener { _, checkedId -> showMode(checkedId) }
        calcType.check(if (isLegacy) R.id.rb_calc_type_address else R.id.rb_calc_type_distance)
        showMode(calcType.checkedRadioButtonId)
        for (field in listOf(longitude, latitude, radius)) field.doAfterTextChanged { refreshDescription() }
    }

    private fun showMode(checkedId: Int) {
        val distance = checkedId != R.id.rb_calc_type_address
        distanceLayout.visibility = if (distance) View.VISIBLE else View.GONE
        addressLayout.visibility = if (distance) View.GONE else View.VISIBLE
        refreshDescription()
    }

    private fun distanceSelected() = calcType.checkedRadioButtonId == distanceOption.id

    /** Updates the summary only from valid input; never shows errors while typing. */
    private fun refreshDescription() {
        if (!distanceSelected()) {
            description.text = stored?.description ?: defaultDescription
            return
        }
        val coordinates = LocationConditionRules.validate(longitude.text, latitude.text, radius.text).coordinates ?: return
        description.text = format(coordinates)
    }

    private fun format(c: LocationConditionRules.Coordinates) = String.format(descriptionFormat, c.longitude, c.latitude, c.radius)

    /** Validates on Apply. Returns null and shows inline errors (first invalid field focused) when invalid. */
    fun apply(): Applied? {
        if (!distanceSelected()) {
            // Unsupported legacy address condition left as it was: return the stored data byte for byte.
            return if (isLegacy) Applied(stored!!.description, eventData!!) else null
        }
        val result = LocationConditionRules.validate(longitude.text, latitude.text, radius.text)
        val c = result.coordinates
        if (c == null) {
            // Show every error; the last call focuses, so walk in reverse to focus the first invalid field.
            for (field in result.invalid.reversed()) {
                when (field) {
                    Field.LONGITUDE -> FormValidation.show(longitude, longitude.context.getString(R.string.location_validation_longitude_error))
                    Field.LATITUDE -> FormValidation.show(latitude, latitude.context.getString(R.string.location_validation_latitude_error))
                    Field.RADIUS -> FormValidation.show(radius, radius.context.getString(R.string.location_validation_radius_error))
                }
            }
            return null
        }
        val text = format(c)
        description.text = text
        // Explicit GPS selection: the mode changes to distance; the old keyword stays in the data.
        val setting = LocationSetting(text, type, LocationConditionRules.CALC_DISTANCE, c.longitude, c.latitude, c.radius, address.text.toString())
        return Applied(text, Gson().toJson(setting))
    }

    /** Fills the coordinates from the cached fix. Returns false when there is no actual fix. */
    fun fillFrom(location: LocationInfo?): Boolean {
        if (!LocationConditionRules.hasCachedFix(location)) return false
        latitude.setText(location!!.latitude.toString())
        longitude.setText(location.longitude.toString())
        return true
    }
}
