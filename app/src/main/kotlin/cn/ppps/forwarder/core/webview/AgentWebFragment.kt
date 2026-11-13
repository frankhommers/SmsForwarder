package cn.ppps.forwarder.core.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.XToastUtils
import com.just.agentweb.action.PermissionInterceptor
import com.just.agentweb.core.AgentWeb
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
import com.just.agentweb.utils.LogUtils
import com.just.agentweb.widget.IWebLayout
import com.xuexiang.xutil.net.JsonUtil

/**
 * UniversalWebViewPage
 *
 * @author xuexiang
 * @since 2019/1/4 PM11:13
 */
@Suppress(
    "unused",
    "ProtectedInFinal",
    "NAME_SHADOWING",
    "UNUSED_PARAMETER",
    "OVERRIDE_DEPRECATION"
)
class AgentWebFragment : Fragment(), FragmentKeyDown {
    private var mBackImageView: ImageView? = null
    private var mLineView: View? = null
    private var mFinishImageView: ImageView? = null
    private var mTitleTextView: TextView? = null
    private var mAgentWeb: AgentWeb? = null
    private var mMoreImageView: ImageView? = null
    private var mPopupMenu: PopupMenu? = null
    private var mDownloadingService: DownloadingService? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_agentweb, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mAgentWeb = AgentWeb.with(this) //ImportAgentWeb. The parent control.
            .setAgentWebParent(
                (view as LinearLayout),
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
            .setWebLayout(webLayout)
            .interceptUnkownUrl() //CreateAgentWeb.
            .createAgentWeb()
            .ready() //Settings WebSettings.
            //WebViewLoadurladdress on the page and display.
            .go(url)
        if (App.isDebug) {
            AgentWebConfig.debug()
        }

        // Get AgentWeb Bottom Control
        addBackgroundChild(mAgentWeb!!.webCreator.webParentLayout)
        initView(view)

        // AgentWeb does notWebViewcover all functionality; some settings AgentWeb are not exposed; configure throughWebViewthe underlying API
        mAgentWeb!!.webCreator.webView.overScrollMode = WebView.OVER_SCROLL_NEVER
    }

    protected val webLayout: IWebLayout<*, *>
        get() = WebLayout(activity)

    protected fun initView(view: View) {
        mBackImageView = view.findViewById(R.id.iv_back)
        mLineView = view.findViewById(R.id.view_line)
        mFinishImageView = view.findViewById(R.id.iv_finish)
        mTitleTextView = view.findViewById(R.id.toolbar_title)
        mBackImageView?.setOnClickListener(mOnClickListener)
        mFinishImageView?.setOnClickListener(mOnClickListener)
        mMoreImageView = view.findViewById(R.id.iv_more)
        mMoreImageView?.setOnClickListener(mOnClickListener)
        pageNavigator(View.GONE)
    }

    protected fun addBackgroundChild(frameLayout: FrameLayout) {
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

    private fun pageNavigator(tag: Int) {
        mBackImageView!!.visibility = tag
        mLineView!!.visibility = tag
    }

    private val mOnClickListener = View.OnClickListener { v ->
        when (v.id) {
            R.id.iv_back ->                     // trueindicatesAgentWebThe incident was dealt with.
                if (!mAgentWeb!!.back()) {
                    this.requireActivity().finish()
                }

            R.id.iv_finish -> this.requireActivity().finish()
            R.id.iv_more -> showPoPup(v)
            else -> {}
        }
    }
    //========================================//
    /**
     * Permission to request interceptor.
     */
    protected var mPermissionInterceptor = PermissionInterceptor { url, permissions, action ->

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
        Log.i(
            TAG,
            "mUrl:" + url + "  permission:" + JsonUtil.toJson(permissions) + " action:" + action
        )
        false
    }
    //=====================Download============================//
    /**
     * Update on AgentWeb 4.0.0，Download listening
     */
    protected var mDownloadListenerAdapter: DownloadListenerAdapter =
        object : DownloadListenerAdapter() {
            /**
             *
             * @param url                Download Link
             * @param userAgent          UserAgent
             * @param contentDisposition ContentDisposition
             * @param mimetype           Type of media with resources
             * @param contentLength      File Length
             * @param extra              Download configuration, the user can use Extra Modify Downloadicon ， Closes the progress bar, whether or not to force download.
             * @return true indicates that the application handled the download event ， false delegate to AgentWeb Download
             */
            override fun onStart(
                url: String,
                userAgent: String,
                contentDisposition: String,
                mimetype: String,
                contentLength: Long,
                extra: Extra,
            ): Boolean {
                LogUtils.i(TAG, "onStart:$url")
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
                LogUtils.i(TAG, "onBindService:$url  DownloadingService:$downloadingService")
            }

            /**
             * callbackonUnbindServicemethod to release DownloadingService.
             * @param url
             * @param downloadingService
             */
            override fun onUnbindService(url: String, downloadingService: DownloadingService) {
                super.onUnbindService(url, downloadingService)
                mDownloadingService = null
                LogUtils.i(TAG, "onUnbindService:$url")
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
                val mProgress = (loaded / java.lang.Float.valueOf(length.toFloat()) * 100).toInt()
                LogUtils.i(TAG, "onProgress:$mProgress")
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
     * @return IAgentWebSettings
     */
    val settings: IAgentWebSettings<*>
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
                downloadListener: DownloadListener?,
            ): WebListenerManager {
                return super.setDownloader(
                    webView,
                    DefaultDownloadImpl
                        .create(
                            requireActivity(),
                            webView,
                            mDownloadListenerAdapter,
                            mDownloadListenerAdapter,
                            this.mAgentWeb.permissionInterceptor
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
                target = bundle.getString(KEY_URL).toString()
            }
            if (TextUtils.isEmpty(target)) {
                target = "https://github.com/xuexiangjys"
            }
            return target
        }
    protected var mWebChromeClient: WebChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            Log.i(TAG, "onProgressChanged:$newProgress  view:$view")
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            var title = title
            super.onReceivedTitle(view, title)
            if (mTitleTextView != null && !TextUtils.isEmpty(title)) {
                if (title.length > 10) {
                    title = title.substring(0, 10) + "..."
                }
                mTitleTextView!!.text = title
            }
        }
    }

    @Suppress("DEPRECATION")
    protected var mWebViewClient: WebViewClient = object : WebViewClient() {
        private val timer = HashMap<String, Long?>()
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

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Log.i(TAG, "mUrl:$url onPageStarted  target:$url")
            timer[url] = System.currentTimeMillis()
            //if (url == url) {
            //    pageNavigator(View.GONE)
            //} else {
            pageNavigator(View.VISIBLE)
            //}
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (timer[url] != null) {
                val overTime = System.currentTimeMillis()
                val startTime = timer[url]
                Log.i(TAG, "  page mUrl:" + url + "  used time:" + (overTime - startTime!!))
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

    /*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }*/
    //========================Menu function================================//
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
                true
            }

            R.id.copy -> {
                if (mAgentWeb != null) {
                    mAgentWeb!!.webCreator.webView.url?.let { toCopy(context, it) }
                }
                true
            }

            R.id.default_browser -> {
                if (mAgentWeb != null) {
                    mAgentWeb!!.webCreator.webView.url?.let { openBrowser(it) }
                }
                true
            }

            R.id.share -> {
                if (mAgentWeb != null) {
                    mAgentWeb!!.webCreator.webView.url?.let { shareWebUrl(it) }
                }
                true
            }

            else -> false
        }
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
        mAgentWeb!!.webLifeCycle.onResume() //Resume
        super.onResume()
    }

    override fun onPause() {
        mAgentWeb!!.webLifeCycle.onPause() //Pause all in-appWebView ， CallmWebView.resumeTimers();/mAgentWeb.getWebLifeCycle().onResume(); Restore.
        super.onPause()
    }

    override fun onFragmentKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return mAgentWeb!!.handleKeyEvent(keyCode, event)
    }

    override fun onDestroyView() {
        mAgentWeb!!.webLifeCycle.onDestroy()
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
     *
     * @return
     */
    @Suppress("DEPRECATION")
    protected val middlewareWebClient: MiddlewareWebClientBase
        get() = object : MiddlewareWebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // Intercept. url，Do not execute DefaultWebClient#shouldOverrideUrlLoading
                if (url.startsWith("agentweb")) {
                    Log.i(TAG, "agentweb scheme ~")
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
    protected val middlewareWebChrome: MiddlewareWebChromeBase
        get() = object : MiddlewareChromeClient() {}

    companion object {
        const val KEY_URL = "com.xuexiang.xuidemo.base.webview.key_url"
        val TAG: String = AgentWebFragment::class.java.simpleName
        fun getInstance(url: String?): AgentWebFragment {
            val bundle = Bundle()
            bundle.putString(KEY_URL, url)
            return getInstance(bundle)
        }

        fun getInstance(bundle: Bundle?): AgentWebFragment {
            val fragment = AgentWebFragment()
            if (bundle != null) {
                fragment.arguments = bundle
            }
            return fragment
        }
    }
}