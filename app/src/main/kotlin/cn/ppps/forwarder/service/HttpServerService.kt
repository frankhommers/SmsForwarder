package cn.ppps.forwarder.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import cn.ppps.forwarder.utils.HTTP_SERVER_TIME_OUT
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import com.yanzhenjie.andserver.AndServer
import com.yanzhenjie.andserver.Server
import com.xuexiang.xutil.net.NetworkUtils
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@Suppress("PrivatePropertyName")
class HttpServerService : Service(), Server.ServerListener {

    private val TAG: String = HttpServerService::class.java.simpleName

    //Has it worked? startup，To avoid being present when not started onDestroy Trigger server lazy initialization
    private var serverStarted: Boolean = false

    private val server by lazy {
        AndServer.webServer(this)
            .inetAddress(resolveListenAddress())
            .port(HttpServerUtils.serverPort)
            .listener(this)
            .timeout(HTTP_SERVER_TIME_OUT, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveListenAddress(): InetAddress {
        if (HttpServerUtils.serverLocalIpOnly) return InetAddress.getByName("127.0.0.1")

        return try {
            NetworkUtils.getLocalInetAddress()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get local IP address: ${e.message}")
            InetAddress.getByName("127.0.0.1")
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        //Pure client mode
        if (SettingUtils.enablePureClientMode) return

        //Shared guard: UI toggles, boot startup and scheduled tasks must all satisfy，
        //Do not listen when the required key is missing; prevent unauthenticated access
        if (!HttpServerUtils.isServerSecretsConfigured()) {
            Log.e(TAG, "Security key not configured(safetyMeasures=${HttpServerUtils.safetyMeasures})，Refuse StartHTTPServices")
            stopSelf()
            return
        }

        Log.i(TAG, "onCreate: ")
        server.startup()
        serverStarted = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        //Pure client mode
        if (SettingUtils.enablePureClientMode) return

        Log.i(TAG, "onDestroy: ")
        //Do not touch the server unless startup succeeded server，Otherwise it will be lazily created only to be shut down
        if (serverStarted) {
            server.shutdown()
            serverStarted = false
        }
    }

    override fun onException(e: Exception?) {
        Log.i(TAG, "onException: ")
    }

    override fun onStarted() {
        Log.i(TAG, "onStarted: ")
    }

    override fun onStopped() {
        Log.i(TAG, "onStopped: ")
    }
}