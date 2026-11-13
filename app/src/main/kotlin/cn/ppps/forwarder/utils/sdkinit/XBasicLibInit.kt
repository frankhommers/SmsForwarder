package cn.ppps.forwarder.utils.sdkinit

import android.app.Application
import cn.ppps.forwarder.App
import cn.ppps.forwarder.core.BaseActivity
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xaop.XAOP
import com.xuexiang.xhttp2.XHttp
import com.xuexiang.xhttp2.XHttpSDK
import com.xuexiang.xhttp2.cache.model.CacheMode
import com.xuexiang.xpage.PageConfig
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.XUI
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.common.StringUtils

/**
 * XInitialization of a series base
 *
 * @author xuexiang
 * @since 2019-06-30 23:54
 */
class XBasicLibInit private constructor() {
    companion object {
        /**
         * Initialise Base LibrarySDK
         */
        fun init(application: Application) {
            //Tool Class
            initXUtil(application)

            //Network Request Framework
            initXHttp2(application)

            //Page Frame
            initXPage(application)

            //Slice Frame
            initXAOP(application)

            //UIFramework
            initXUI(application)

            //Route frame
            initRouter(application)
        }

        /**
         * InitializeXUtilTool Class
         */
        private fun initXUtil(application: Application) {
            XUtil.init(application)
            XUtil.debug(App.isDebug)
        }

        /**
         * InitializeXHttp2
         */
        private fun initXHttp2(application: Application) {
            //Initialize the network request framework, which must be implemented first
            XHttpSDK.init(application)
            //Do it when debugging is required
            if (App.isDebug) {
                XHttpSDK.debug()
            }
            //Set the global base address requested for the network
            XHttpSDK.setBaseUrl("http://127.0.0.1/")
            //Sets a custom log print intercepter
            //XHttpSDK.debug(LoggingInterceptor())
            //Set Dynamic Parameters Add Interceptor
            //XHttpSDK.addInterceptor(CustomDynamicInterceptor())
            //Request invalidating interceptor.
            //XHttpSDK.addInterceptor(CustomExpiredInterceptor())
            //Set global timeout
            XHttp.getInstance()
                .debug(App.isDebug)
                .setCacheMode(CacheMode.NO_CACHE)
                .setTimeout(SettingUtils.requestTimeout * 1000L) //Single timeout
            //.setRetryCount(SettingUtils.requestRetryTimes) //Number of overtime retrys
            //.setRetryDelay(SettingUtils.requestDelayTime * 1000) //Timeout retry delay
            //.setRetryIncreaseDelay(SettingUtils.requestDelayTime * 1000) //Incremental timeout retry backoff
        }

        /**
         * InitializeXPagePage Frame
         */
        private fun initXPage(application: Application) {
            PageConfig.getInstance()
                .debug(App.isDebug)
                .setContainActivityClazz(BaseActivity::class.java)
                .init(application)
        }

        /**
         * InitializeXAOP
         */
        private fun initXAOP(application: Application) {
            XAOP.init(application)
            XAOP.debug(App.isDebug)
            //Configure permission-request interception and denial callbacks
            XAOP.setOnPermissionDeniedListener { permissionsDenied: List<String?>? ->
                XToastUtils.error(
                    "Permission denied.:" + StringUtils.listToString(permissionsDenied, ",")
                )
            }
        }

        /**
         * InitializeXUIFramework
         */
        private fun initXUI(application: Application) {
            XUI.init(application)
            XUI.debug(App.isDebug)
        }

        /**
         * Initialise route frame
         */
        private fun initRouter(application: Application) {
            // These two lines must precedeinitBefore, otherwise these configurations are ininitThe process will be invalid.
            if (App.isDebug) {
                XRouter.openLog() // Print Log
                XRouter.openDebug() // Enable debugging mode(If it'sInstantRunrequires debug mode during development; disable it in production,Otherwise there's a security risk.)
            }
            XRouter.init(application)
        }
    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}