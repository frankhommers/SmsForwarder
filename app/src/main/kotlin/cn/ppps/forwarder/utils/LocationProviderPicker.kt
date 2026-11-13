package cn.ppps.forwarder.utils

import android.content.Context
import android.location.Criteria
import android.location.LocationManager

/**
 * Positioning provider Select.
 *
 * The original implementation passed both accuracy and power requirements to `getBestProvider`，It's up to the library to choose. provider.
 * But... `getBestProvider` treats `accuracy` with `powerRequirement` Also as a filter condition，
 * The criteria may have no common provider (e.g. precise location with + Low power consumption.'），
 * This is when the library returns to default, actually uses it. provider This is not consistent with the user settings.
 * And... `fused` provider may decide independently to activate GNSS，rendering the coarse-location preference ineffective
 *
 * Change here to specify by Precision provider，Electricity consumption is no longer part of the screening.
 *
 * Set Value Contrast（SettingsFragment）：
 *  Location precision: = ACCURACY_FINE(1)，Blur = ACCURACY_COARSE(2)，No request. = NO_REQUIREMENT(0)
 */
object LocationProviderPicker {

    private fun lm(context: Context): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun hasProvider(context: Context, provider: String): Boolean = try {
        lm(context)?.allProviders?.contains(provider) == true
    } catch (e: Exception) {
        false
    }

    private fun isEnabled(context: Context, provider: String): Boolean = try {
        lm(context)?.isProviderEnabled(provider) == true
    } catch (e: Exception) {
        false
    }

    private fun networkOrNull(context: Context): String? =
        if (hasProvider(context, LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else null

    private fun gpsOrNull(context: Context): String? =
        if (isEnabled(context, LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else null

    /**
     * provider Is it really available: it's in the system and it's not turned off by the user.
     *
     * Just checking. [LocationManager.getProviders] Not enough.——GPS On Switch Off provider Still exists.，
     * Registering a listener succeeds but never produces callbacks and wastes power
     */
    fun isReady(context: Context, provider: String): Boolean =
        hasProvider(context, provider) && isEnabled(context, provider)

    /** Coarse-only means explicitly selecting coarse location */
    fun coarseOnly(): Boolean = SettingUtils.locationAccuracy == Criteria.ACCURACY_COARSE

    /**
     * Which one to use? provider.
     *
     *  - Fuzzy position: use only network，Never light it up. GNSS
     *  - Exact / No request.：GPS Priority，GPS Back when not available network
     *
     * Back null Let the system decide (not expected in normal operation)
     */
    fun primary(context: Context): String? {
        if (coarseOnly()) return networkOrNull(context)
        return gpsOrNull(context) ?: networkOrNull(context)
    }
}
