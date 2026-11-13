package cn.ppps.forwarder.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import cn.ppps.forwarder.App
import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.utils.ACTION_RESTART
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.ACTION_STOP
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.LocationUtils
import cn.ppps.forwarder.utils.LocationProviderPicker
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.TASK_CONDITION_LEAVE_ADDRESS
import cn.ppps.forwarder.utils.TASK_CONDITION_TO_ADDRESS
import cn.ppps.forwarder.utils.TaskWorker
import cn.ppps.forwarder.utils.task.TaskUtils
import cn.ppps.forwarder.workers.LocationWorker
import com.king.location.LocationErrorCode
import com.king.location.OnExceptionListener
import com.king.location.OnLocationListener
import com.xuexiang.xaop.util.PermissionUtils
import java.util.Date

@SuppressLint("SimpleDateFormat")
@Suppress("PrivatePropertyName", "DEPRECATION")
class LocationService : Service() {

    private val TAG: String = LocationService::class.java.simpleName
    private val locationStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                handleLocationStatusChanged()
            }
        }
    }

    companion object {
        var isRunning = false
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate: ")
        super.onCreate()

        if (!SettingUtils.enableLocation) return

        //Registered broadcast receiver
        registerReceiver(locationStatusReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        startService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent == null) return START_NOT_STICKY
        Log.i(TAG, "onStartCommand: ${intent.action}")

        when {
            intent.action == ACTION_START && !isRunning -> startService()
            intent.action == ACTION_STOP && isRunning -> stopService()
            intent.action == ACTION_RESTART -> restartLocation()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: ")
        super.onDestroy()

        if (!SettingUtils.enableLocation) return
        stopService()
        //in Service Unregister broadcast receivers on destruction
        unregisterReceiver(locationStatusReceiver)
    }

    private fun startService() {
        try {
            //Clear Cache
            HttpServerUtils.apiLocationCache = LocationInfo()
            TaskUtils.locationInfoOld = LocationInfo()

            if (SettingUtils.enableLocation && PermissionUtils.isGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)) {

                //Set up a locator listening
                App.LocationClient.setOnLocationListener(object : OnLocationListener() {
                    override fun onLocationChanged(location: Location) {
                        //Location Information
                        Log.d(TAG, "onLocationChanged(location = ${location})")

                        val locationInfoNew = LocationInfo(
                            location.longitude, location.latitude, "", App.DateFormat.format(Date(location.time)), location.provider.toString()
                        )

                        // Coordinates remain available; no online reverse geocoding.

                        Log.d(TAG, "locationInfoNew = $locationInfoNew")
                        HttpServerUtils.apiLocationCache = locationInfoNew
                        TaskUtils.locationInfoNew = locationInfoNew

                        //Trigger Auto Job
                        val locationInfoOld = TaskUtils.locationInfoOld
                        if (locationInfoOld.longitude != locationInfoNew.longitude || locationInfoOld.latitude != locationInfoNew.latitude || locationInfoOld.address != locationInfoNew.address) {
                            Log.d(TAG, "locationInfoOld = $locationInfoOld")

                            val gson = Gson()
                            val locationJsonOld = gson.toJson(locationInfoOld)
                            val locationJsonNew = gson.toJson(locationInfoNew)
                            enqueueLocationWorkerRequest(TASK_CONDITION_TO_ADDRESS, locationJsonOld, locationJsonNew)
                            enqueueLocationWorkerRequest(TASK_CONDITION_LEAVE_ADDRESS, locationJsonOld, locationJsonNew)

                            TaskUtils.locationInfoOld = locationInfoNew
                        }
                    }
                })

                //Set up an abnormal listening
                App.LocationClient.setOnExceptionListener(object : OnExceptionListener {
                    override fun onException(@LocationErrorCode errorCode: Int, e: Exception) {
                        //We've got a location anomaly. && Try repositioning
                        Log.w(TAG, "onException(errorCode = ${errorCode}, e = ${e})")
                        restartLocation()
                    }
                })

                restartLocation()
                isRunning = true
            } else if (!SettingUtils.enableLocation && App.LocationClient.isStarted()) {
                Log.d(TAG, "stopLocation")
                App.LocationClient.stopLocation()
                isRunning = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "startService: ${e.message}")
            isRunning = false
        }
    }

    private fun stopService() {
        //Clear Cache
        HttpServerUtils.apiLocationCache = LocationInfo()
        TaskUtils.locationInfoOld = LocationInfo()

        isRunning = try {
            //If we start locating, we'll stop.
            if (SettingUtils.enableLocation && App.LocationClient.isStarted()) {
                App.LocationClient.stopLocation()
            }
            stopForeground(true)
            stopSelf()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "stopService: ${e.message}")
            true
        }
    }

    private fun restartLocation() {
        //If we start locating, we'll stop.
        if (App.LocationClient.isStarted()) {
            App.LocationClient.stopLocation()
        }
        if (LocationUtils.isLocationEnabled(App.context) && LocationUtils.hasLocationCapability(App.context)) {
            //Position configuration parameters according to specific needs (only some main parameters are listed here)）
            val locationOption = App.LocationClient.getLocationOption().setAccuracy(SettingUtils.locationAccuracy)//Set position accuracy: high precision
                .setPowerRequirement(SettingUtils.locationPowerRequirement) //Set electricity consumption: low electricity consumption
                .setMinTime(SettingUtils.locationMinInterval)//Set the minimum time interval (in milliseconds); default interval：10000milliseconds, minimum interval：1000milliseconds
                .setMinDistance(SettingUtils.locationMinDistance)//Set position to update minimum distance (m); default distance：0meters
                .setOnceLocation(false)//Whether or not to position only once, default to false，When Set As true , the position will be automatically suspended after a single position.
                .setLastKnownLocation(false)//Sets whether or not to retrieve the last known cache position, default to true
                .apply {
                    //Important: explicitly select provider，Go around. getBestProvider Uncertainty under strict conditions.
                    //getBestProvider treats accuracy with powerRequirement Also as a filter condition，
                    //The criteria may have no common provider (e.g. precise location with + Low power consumption, at which point the library returns its default behavior.，
                    //Actual use provider This is not consistent with the user settings.
                    //Coarse location must also explicitly select network，Or else. fused/GPS It could still be pulled up.，
                    //Otherwise the user's coarse-location-only preference is ignored
                    LocationProviderPicker.primary(App.context)?.let { setProvider(it) }
                }
            //Set positioning configuration parameters
            App.LocationClient.setLocationOption(locationOption)
            App.LocationClient.startLocation()
        } else {
            Log.w(TAG, "onException: GPSUnopened")
        }
    }

    private fun enqueueLocationWorkerRequest(
        conditionType: Int, locationJsonOld: String, locationJsonNew: String
    ) {
        val locationWorkerRequest = OneTimeWorkRequestBuilder<LocationWorker>().setInputData(
            workDataOf(
                TaskWorker.CONDITION_TYPE to conditionType, "locationJsonOld" to locationJsonOld, "locationJsonNew" to locationJsonNew
            )
        ).build()

        WorkManager.getInstance(applicationContext).enqueue(locationWorkerRequest)
    }

    private fun handleLocationStatusChanged() {
        //Deal with state changes
        if (LocationUtils.isLocationEnabled(App.context) && LocationUtils.hasLocationCapability(App.context)) {
            //Enabled
            Log.d(TAG, "handleLocationStatusChanged: Enabled")
            if (SettingUtils.enableLocation && !App.LocationClient.isStarted()) {
                App.LocationClient.startLocation()
            }
        } else {
            //Disabled
            Log.d(TAG, "handleLocationStatusChanged: Disabled")
            if (SettingUtils.enableLocation && App.LocationClient.isStarted()) {
                App.LocationClient.stopLocation()
            }
        }
    }

}