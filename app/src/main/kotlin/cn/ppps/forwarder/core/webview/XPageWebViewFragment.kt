package cn.ppps.forwarder.core.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentAgentwebBinding
import cn.ppps.forwarder.utils.XToastUtils
import com.just.agentweb.action.PermissionInterceptor
import com.just.agentweb.core.AgentWeb
import com.just.agentweb.core.client.DefaultWebClient
import com.just.agentweb.core.client.MiddlewareWebChromeBase
import com.just.agentweb.core.client.MiddlewareWebClientBase
import com.just.agentweb.core.client.WebListenerManager
import com.just.agentweb.core.web.AbsAgentWebSettings
import com.just.agentweb.core.web.AgentWebConfig
import com.just.agentweb.core.web.IAgentWebSettings
import com.just.agentweb.download.AgentWebDownloader.Extra
import com.just.agentweb.download.DefaultDownloadImpl
import com.just.agentweb.download.DownloadListenerAdapter
import com.just.agentweb.download.DownloadingService
import com.just.agentweb.widget.IWebLayout
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xpage.base.XPageActivity
import com.xuexiang.xpage.base.XPageFragment
import com.xuexiang.xpage.core.PageOption
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xutil.common.logger.Logger
import com.xuexiang.xutil.net.JsonUtil

/**
 * UseXPageFragment
 *
 * @author xuexiang
 * @since 2019-05-26 18:15
 */
@Suppress("DEPRECATION", "unused", "UNUSED_PARAMETER", "NAME_SHADOWING", "OVERRIDE_DEPRECATION")
@Page(params = [AgentWebFragment.KEY_URL])
class XPageWebViewFragment : BaseFragment<FragmentAgentwebBinding?>(), View.OnClickListener {
    private var mAgentWeb: AgentWeb? = null
    private var mPopupMenu: PopupMenu? = null
    private var mDownloadingService: DownloadingService? = null
    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentAgentwebBinding {
        return FragmentAgentwebBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        return null
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        mAgentWeb = AgentWeb.with(this) //ImportAgentWeb. The parent control.
            .setAgentWebParent(
                (rootView as LinearLayout),
                -1,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            ) //Set progress bar colour and height，-1As default, height is2，In unitsdp.
            .useDefaultIndicator(-1, 3) //Settings IAgentWebSettings.
            .setAgentWebWebSettings(settings) //WebViewClient ， with WebView Use consistent, but do not accessWebViewCallsetWebViewClient(xx)method,overridesAgentWeb DefaultWebClient,and also disables the corresponding middleware
            .setWebViewClient(mWebViewClient) //WebChromeClient
            .setWebChromeClient(mWebChromeClient) //SettingsWebChromeClientmiddleware; supports multiple instancesWebChromeClient，AgentWeb 3.0.0 added
            .useMiddlewareWebChrome(middlewareWebChrome) //SettingsWebViewClientmiddleware; supports multiple instancesWebViewClient， AgentWeb 3.0.0 added
            .useMiddlewareWebClient(middlewareWebClient) //Permission Interception 2.0.0 added
            .setPermissionInterceptor(mPermissionInterceptor) //Strict Mode Android 4.2.2 The following will give up the injection object, usingAgentWebViewhas no effect
            .setSecurityType(AgentWeb.SecurityType.STRICT_CHECK) //CustomUI  AgentWeb3.0.0 added
            .setAgentWebUIController(UIController(requireActivity())) //Parameters1is the error layout; parameter2Click to refresh controlsID -1Means click on the entire layout to refresh， AgentWeb 3.0.0 added
            .setMainFrameErrorView(R.layout.agentweb_error_page, -1)
            .setWebLayout(webLayout) //Ask before opening another app AgentWeb 3.0.0 added
            .setOpenOtherPageWays(DefaultWebClient.OpenOtherPageWays.DISALLOW) //Intercept URLs without a matching pageUrl AgentWeb 3.0.0 added
            .interceptUnkownUrl() //CreateAgentWeb.
            .createAgentWeb()
            .ready() //Settings WebSettings.
            //WebViewLoadurladdress on the page and display.
            .go(url)
        if (App.isDebug) {
            AgentWebConfig.debug()
        }
        pageNavigator(View.GONE)
        // Get AgentWeb Bottom Control
        addBackgroundChild(mAgentWeb!!.webCreator.webParentLayout)

        // AgentWeb does notWebViewcover all functionality; some settings AgentWeb are not exposed; configure throughWebViewthe underlying API
        mAgentWeb!!.webCreator.webView.overScrollMode = WebView.OVER_SCROLL_NEVER
    }

    private val webLayout: IWebLayout<*, *>
        get() = WebLayout(activity)

    private fun addBackgroundChild(frameLayout: FrameLayout) {
        val textView = TextView(frameLayout.context)
        textView.text = getString(R.string.provided_by_agentweb)
        textView.textSize = 16f
        textView.setTextColor(Color.parseColor("#727779"))
        frameLayout.setBackgroundColor(Color.parseColor("#272b2d"))
        val params = FrameLayout.LayoutParams(-2, -2)
        params.gravity = Gravity.CENTER_HORIZONTAL
        val scale = frameLayout.context.resources.displayMetrics.density
        params.topMargin = (15 * scale + 0.5f).toInt()
        frameLayout.addView(textView, 0, params)
    }

    override fun initListeners() {
        binding!!.includeTitle.ivBack.setOnClickListener(this)
        binding!!.includeTitle.ivFinish.setOnClickListener(this)
        binding!!.includeTitle.ivMore.setOnClickListener(this)
    }

    private fun pageNavigator(tag: Int) {
        //Returning Navigation Buttons
        binding!!.includeTitle.ivBack.visibility = tag
        binding!!.includeTitle.viewLine.visibility = tag
    }

    @SingleClick
    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.iv_back) {
            // trueindicatesAgentWebThe incident was dealt with.
            if (!mAgentWeb!!.back()) {
                popToBack()
            }
        } else if (id == R.id.iv_finish) {
            popToBack()
        } else if (id == R.id.iv_more) {
            showPoPup(view)
        }
    }
    //=====================Download============================//
    /**
     * Update on AgentWeb 4.0.0，Download listening
     */
    private var mDownloadListenerAdapter: DownloadListenerAdapter =
        object : DownloadListenerAdapter() {
            /**
             *
             * @param url                Download Link
             * @param userAgent          UserAgent
             * @param contentDisposition ContentDisposition
             * @param mimeType           Type of media with resources
             * @param contentLength      File Length
             * @param extra              Download configuration, the user can use Extra Modify Downloadicon ， Closes the progress bar, whether or not to force download.
             * @return true indicates that the application handled the download event ， false delegate to AgentWeb Download
             */
            override fun onStart(
                url: String,
                userAgent: String,
                contentDisposition: String,
                mimeType: String,
                contentLength: Long,
                extra: Extra,
            ): Boolean {
                Logger.i("onStart:$url")
                // Enable resumable downloads
                extra.setOpenBreakPointDownload(true) //Downloads the notice.icon
                    .setIcon(R.drawable.ic_file_download_black_24dp) // Connection timeout
                    .setConnectTimeOut(6000) // in8KBunits, default60s ，if60sUnable to read from network stream8KBbytes, throw an exception
                    .setBlockMaxTime(10 * 60 * 1000) // Downloaded timeout
                    .setDownloadTimeOut(Long.MAX_VALUE) // Serial downloads are more resource-efficient.
                    .setParallelDownload(false) // false Close Progress Notification
                    .setEnableIndicator(true) // Custom Request Header
                    .addHeader("Cookie", "xx") // Auto-open after download
                    .setAutoOpen(true).isForceDownload = true
                return false
            }

            /**
             *
             * No implementation is needed if pausing or stopping downloads is unnecessary
             * @param url
             * @param downloadingService  Users can access DownloadingService#shutdownNow End Download
             */
            override fun onBindService(url: String, downloadingService: DownloadingService) {
                super.onBindService(url, downloadingService)
                mDownloadingService = downloadingService
                Logger.i("onBindService:$url  DownloadingService:$downloadingService")
            }

            /**
             * callbackonUnbindServicemethod to release DownloadingService.
             * @param url
             * @param downloadingService
             */
            override fun onUnbindService(url: String, downloadingService: DownloadingService) {
                super.onUnbindService(url, downloadingService)
                mDownloadingService = null
                Logger.i("onUnbindService:$url")
            }

            /**
             *
             * @param url  Download Link
             * @param loaded  Length of Downloaded
             * @param length    Total size of files
             * @param usedTime   Time-consuming, unitsms
             * This callback runs on a worker thread named AsyncTask #XX or AgentWeb # XX
             */
            override fun onProgress(url: String, loaded: Long, length: Long, usedTime: Long) {
                val mProgress = (loaded / length.toFloat() * 100).toInt()
                Logger.i("onProgress:$mProgress")
                super.onProgress(url, loaded, length, usedTime)
            }

            /**
             *
             * @param path Absolute Path to File
             * @param url  Downloads
             * @param throwable    Return the exception to the caller on failure
             * @return true Indicates that the user has processed the event that completed the download ，false Default HandoverAgentWeb Process
             */
            override fun onResult(path: String, url: String, throwable: Throwable): Boolean {
                //Download successful
                //if (null == throwable) {
                //do you work
                //} else { //Download failed
                //}
                // true  Do not send a download-complete notification , Or open the file.
                return false
            }
        }
    /**
     * AgentWeb 4.0.0 It's been deleted from the inside. DownloadListener Listening and relatedAPI ，Move Download the component into a separate library，
     * If you need to use AgentWeb Download Partially, please rely on compile 'com.just.agentweb:download:4.0.0 ，
     * Customize if you need to listen to download results AgentWebSetting ， New Out DefaultDownloadImpl，ImportDownloadListenerAdapter
     * Observe progress or results as shown below; if neither is needed setDownloader The example can be ignored.
     * @return WebListenerManager
     */
    /**
     * Download Service Settings
     *
     * @return IAgentWebSettings
     */
    private val settings: IAgentWebSettings<*>
        get() = object : AbsAgentWebSettings() {
            private val mAgentWeb: AgentWeb? = null
            override fun bindAgentWebSupport(agentWeb: AgentWeb) {
                this.mAgentWeb = agentWeb
            }

            /**
             * AgentWeb 4.0.0 It's been deleted from the inside. DownloadListener Listening and relatedAPI ，Move Download the component into a separate library，
             * If you need to use AgentWeb Download Partially, please rely on compile 'com.just.agentweb:download:4.0.0 ，
             * Customize if you need to listen to download results AgentWebSetting ， New Out DefaultDownloadImpl，ImportDownloadListenerAdapter
             * Observe progress or results as shown below; if neither is needed setDownloader The example can be ignored.
             * @return WebListenerManager
             */
            override fun setDownloader(
                webView: WebView,
                downloadListener: DownloadListener,
            ): WebListenerManager {
                return super.setDownloader(
                    webView,
                    DefaultDownloadImpl
                        .create(
                            activity!!,
                            webView,
                            mDownloadListenerAdapter,
                            mDownloadListenerAdapter,
                            mAgentWeb.permissionInterceptor
                        )
                )
            }
        }
    //===================WebChromeClient and WebViewClient===========================//
    /**
     * page blank, checkschemeAdd， scheme://host:port/path?query&query .
     *
     * @return mUrl
     */
    val url: String
        get() {
            var target = ""
            val bundle = arguments
            if (bundle != null) {
                target = bundle.getString(AgentWebFragment.KEY_URL).toString()
            }
            if (TextUtils.isEmpty(target)) {
                target = "https://github.com/xuexiangjys"
            }
            return target
        }

    /**
     * Related to browsers, including andJSInteracting
     */
    private var mWebChromeClient: WebChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            //Web Load Progress
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            var title = title
            super.onReceivedTitle(view, title)
            if (!TextUtils.isEmpty(title)) {
                if (title.length > 10) {
                    title = title.substring(0, 10) + "..."
                }
                binding!!.includeTitle.toolbarTitle.text = title
            }
        }
    }

    /**
     * and web pagesurlLoad Related, Statistics Load Time
     */
    @Suppress("DEPRECATION")
    private var mWebViewClient: WebViewClient = object : WebViewClient() {
        private val mTimer = HashMap<String, Long?>()
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return shouldOverrideUrlLoading(view, request.url.toString() + "")
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            return super.shouldInterceptRequest(view, request)
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            //intent:// schemeprocessing if you returnfalse ， delegate to DefaultWebClient Process, the default opens theActivity  ， ifActivityIf it doesn't exist, jump to the app market..  true Means intercept.
            //For example, Youku video playback ，intent://play?...package=com.youku.phone;end;
            //Youku attempts to open its own app; return true  to play inside the app H5 and prevent the external app from opening; returning false ， DefaultWebClient  usesintent The protocol handles the address, first matching the application 's non-existent, if it exists, calling the application to play, and if it does not exist, skips to the application market to download the application .
            return url.startsWith("intent://") && url.contains("com.youku.phone")
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap) {
            mTimer[url] = System.currentTimeMillis()
            //if (url == url) {
            //    pageNavigator(View.GONE)
            //} else {
            pageNavigator(View.VISIBLE)
            //}
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (mTimer[url] != null) {
                val overTime = System.currentTimeMillis()
                val startTime = mTimer[url]
                //Length of use of statistical pages
                Logger.i(" page mUrl:" + url + "  used time:" + (overTime - startTime!!))
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
        }

        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String,
            failingUrl: String,
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
        }
    }
    //=====================Menu========================//
    /**
     * Show More Menu
     *
     * @param view Menu Attached toViewbelow
     */
    private fun showPoPup(view: View) {
        if (mPopupMenu == null) {
            mPopupMenu = PopupMenu(requireContext(), view)
            mPopupMenu!!.inflate(R.menu.menu_toolbar_web)
            mPopupMenu!!.setOnMenuItemClickListener(mOnMenuItemClickListener)
        }
        mPopupMenu!!.show()
    }

    /**
     * Menu Events
     */
    private val mOnMenuItemClickListener = PopupMenu.OnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.refresh -> {
                if (mAgentWeb != null) {
                    mAgentWeb!!.urlLoader.reload() // Refresh
                }
                return@OnMenuItemClickListener true
            }

            R.id.copy -> {
                if (mAgentWeb != null) {
                    mAgentWeb!!.webCreator.webView.url?.let { toCopy(context, it) }
                }
                return@OnMenuItemClickListener true
            }

            R.id.default_browser -> {
                if (mAgentWeb != null) {
                    mAgentWeb!!.webCreator.webView.url?.let { openBrowser(it) }
                }
                return@OnMenuItemClickListener true
            }

            R.id.share -> {
                if (mAgentWeb != null) {
                    mAgentWeb!!.webCreator.webView.url?.let { shareWebUrl(it) }
                }
                return@OnMenuItemClickListener true
            }

            else -> false
        }
    }

    /**
     * Open Browser
     *
     * @param targetUrl Address opened by external browser
     */
    private fun openBrowser(targetUrl: String) {
        if (TextUtils.isEmpty(targetUrl) || targetUrl.startsWith("file://")) {
            XToastUtils.toast(targetUrl + getString(R.string.cannot_open_with_browser))
            return
        }
        val intent = Intent()
        intent.action = "android.intent.action.VIEW"
        val uri = Uri.parse(targetUrl)
        intent.data = uri
        startActivity(intent)
    }

    /**
     * Share web links
     *
     * @param url Web links
     */
    private fun shareWebUrl(url: String) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(Intent.EXTRA_TEXT, url)
        shareIntent.type = "text/plain"
        //Set the title of the share list and always show the share list
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_to)))
    }

    /**
     * Copy String
     *
     * @param context
     * @param text
     */
    private fun toCopy(context: Context?, text: String) {
        val manager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(null, text))
    }

    //===================Life cycle management===========================//
    override fun onResume() {
        if (mAgentWeb != null) {
            mAgentWeb!!.webLifeCycle.onResume() //Resume
        }
        super.onResume()
    }

    override fun onPause() {
        if (mAgentWeb != null) {
            mAgentWeb!!.webLifeCycle.onPause() //Pause all in-appWebView ， CallmWebView.resumeTimers();/mAgentWeb.getWebLifeCycle().onResume(); Restore.
        }
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return mAgentWeb != null && mAgentWeb!!.handleKeyEvent(keyCode, event)
    }

    override fun onDestroyView() {
        if (mAgentWeb != null) {
            mAgentWeb!!.destroy()
        }
        super.onDestroyView()
    }
    //===================Middleware===========================//// Intercept. url，Do not execute DefaultWebClient#shouldOverrideUrlLoading
    // Execute DefaultWebClient#shouldOverrideUrlLoading
    // do you work
    /**
     * MiddlewareWebClientBase Yes AgentWeb 3.0.0 It provides a powerful function.，
     * If user needs to use AgentWeb The function provided, do not want to rewrite WebClientViewmethod
     * overrideAgentWebprovided functionality, then MiddlewareWebClientBase is
     * a suitable choice
     */
    private val middlewareWebClient: MiddlewareWebClientBase
        get() = object : MiddlewareWebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // Intercept. url，Do not execute DefaultWebClient#shouldOverrideUrlLoading
                if (url.startsWith("agentweb")) {
                    return true
                }
                // Execute DefaultWebClient#shouldOverrideUrlLoading
                return super.shouldOverrideUrlLoading(view, url)
                // do you work
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                return super.shouldOverrideUrlLoading(view, request)
            }
        }
    private val middlewareWebChrome: MiddlewareWebChromeBase
        get() = object : MiddlewareChromeClient() {}

    /**
     * Permission to request interceptor.
     */
    private var mPermissionInterceptor = PermissionInterceptor { url, permissions, action ->

        /**
         * PermissionInterceptor can url1 Allow Authorization， url2 denies permission
         * @param url
         * @param permissions
         * @param action
         * @return true TheUrlIntercepting page requests. ，false It means no interception.
         */
        /**
         * PermissionInterceptor can url1 Allow Authorization， url2 denies permission
         * @param url
         * @param permissions
         * @param action
         * @return true TheUrlIntercepting page requests. ，false It means no interception.
         */
        Logger.i("mUrl:" + url + "  permission:" + JsonUtil.toJson(permissions) + " action:" + action)
        false
    }

    companion object {
        /**
         * Open Web Page
         *
         * @param xPageActivity
         * @param url
         * @return
         */
        fun openUrl(xPageActivity: XPageActivity?, url: String?): Fragment {
            return PageOption.to(XPageWebViewFragment::class.java)
                .putString(AgentWebFragment.KEY_URL, url)
                .open(xPageActivity!!)
        }

        /**
         * Open Web Page
         *
         * @param fragment
         * @param url
         * @return
         */
        fun openUrl(fragment: XPageFragment?, url: String?): Fragment {
            return PageOption.to(XPageWebViewFragment::class.java)
                .setNewActivity(true)
                .putString(AgentWebFragment.KEY_URL, url)
                .open(fragment!!)
        }
    }
}