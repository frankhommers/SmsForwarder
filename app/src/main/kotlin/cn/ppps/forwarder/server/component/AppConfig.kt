package cn.ppps.forwarder.server.component

import android.content.Context
import cn.ppps.forwarder.utils.HttpServerUtils
import com.xuexiang.xrouter.utils.TextUtils
import com.yanzhenjie.andserver.annotation.Config
import com.yanzhenjie.andserver.framework.config.WebConfig
import com.yanzhenjie.andserver.framework.website.AssetsWebsite
import com.yanzhenjie.andserver.framework.website.StorageWebsite

@Config
class AppConfig : WebConfig {

    override fun onConfig(context: Context, delegate: WebConfig.Delegate) {

        val serverWebPath = HttpServerUtils.serverWebPath
        if (!TextUtils.isEmpty(serverWebPath)) {
            // Add one at/sdcard/Download/Websites under Directory
            delegate.addWebsite(StorageWebsite(serverWebPath))
        } else {
            // Add one atassetsofwebWeb site of the directory
            delegate.addWebsite(AssetsWebsite(context, "/web/"))
        }

        /*delegate.setMultipart(
            Multipart.newBuilder()
                .allFileMaxSize(1024 * 1024 * 20) // Total size of individual request for all files
                .fileMaxSize(1024 * 1024 * 5) // Each file size requested individually
                .maxInMemorySize(1024 * 20) // Memory cache size
                .uploadTempDir(context.cacheDir) // Upload File Save Directory
                .build()
        )*/
    }

}