package cn.ppps.forwarder.utils

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import cn.ppps.forwarder.App
import cn.ppps.forwarder.App.Companion.APP_TAG_MAP
import cn.ppps.forwarder.App.Companion.BATTERY_TAG_MAP
import cn.ppps.forwarder.App.Companion.CALL_TAG_MAP
import cn.ppps.forwarder.App.Companion.COMMON_TAG_MAP
import cn.ppps.forwarder.App.Companion.LOCATION_TAG_MAP
import cn.ppps.forwarder.App.Companion.NETWORK_TAG_MAP
import cn.ppps.forwarder.App.Companion.SMS_TAG_MAP
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.webview.AgentWebActivity
import cn.ppps.forwarder.core.webview.AgentWebFragment
import cn.ppps.forwarder.entity.ImageInfo
import cn.ppps.forwarder.fragment.MarkdownFragment
import cn.ppps.forwarder.fragment.ServiceProtocolFragment
import cn.ppps.forwarder.service.NotificationService
import com.xuexiang.xpage.base.XPageFragment
import com.xuexiang.xpage.core.PageOption
import com.xuexiang.xui.utils.ColorUtils
import cn.ppps.forwarder.widget.material.DialogLoader
import cn.ppps.forwarder.widget.material.DialogAction
import cn.ppps.forwarder.widget.material.MaterialDialog
import cn.ppps.forwarder.widget.material.MaterialDialog.SingleButtonCallback
import com.xuexiang.xui.widget.imageview.preview.PreviewBuilder
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.common.StringUtils
import com.xuexiang.xutil.resource.ResUtils.getColor
import com.xuexiang.xutil.resource.ResUtils.getDrawable
import com.xuexiang.xutil.resource.ResUtils.getString
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.regex.Pattern

/**
 * Common Tool Class
 */
@Suppress("RegExpRedundantEscape", "unused", "RegExpUnnecessaryNonCapturingGroup")
class CommonUtils private constructor() {
    companion object {
        /**
         * Here, fill out your application privacy policy web address.
         */
        private const val PRIVACY_URL = "protocol/privacy_protocol.txt"

        /**
         * Show a hint for privacy policy
         *
         * @param context
         * @param submitListener Consent listener
         * @return
         */
        @Suppress("SameParameterValue", "NAME_SHADOWING")
        @JvmStatic
        fun showPrivacyDialog(context: Context, submitListener: SingleButtonCallback?): Dialog {
            val dialog = MaterialDialog.Builder(context).title(R.string.title_reminder).autoDismiss(false).cancelable(false).positiveText(R.string.lab_agree).onPositive { dialog1: MaterialDialog, which: DialogAction? ->
                if (submitListener != null) {
                    submitListener.onClick(dialog1, which!!)
                } else {
                    dialog1.dismiss()
                }
            }.negativeText(R.string.lab_disagree).onNegative { dialog, _ ->
                dialog.dismiss()
                DialogLoader.getInstance().showConfirmDialog(
                    context, getString(R.string.title_reminder), String.format(
                        getString(R.string.content_privacy_explain_again), getString(R.string.app_name)
                    ), getString(R.string.lab_look_again), { dialog, _ ->
                        dialog.dismiss()
                        showPrivacyDialog(context, submitListener)
                    }, getString(R.string.lab_still_disagree)
                ) { dialog, _ ->
                    dialog.dismiss()
                    DialogLoader.getInstance().showConfirmDialog(
                        context, getString(R.string.content_think_about_it_again), getString(R.string.lab_look_again), { dialog, _ ->
                            dialog.dismiss()
                            showPrivacyDialog(context, submitListener)
                        }, getString(R.string.lab_exit_app)
                    ) { dialog, _ ->
                        dialog.dismiss()
                        XUtil.exitApp()
                    }
                }
            }.build()
            dialog.setContent(getPrivacyContent(context))
            //Start responding to the click event
            dialog.contentView!!.movementMethod = LinkMovementMethod.getInstance()
            dialog.show()
            return dialog
        }

        /**
         * @return Privacy policy statement
         */
        private fun getPrivacyContent(context: Context): SpannableStringBuilder {
            return SpannableStringBuilder().append("    ").append(getString(R.string.privacy_content_1)).append(" ").append(getString(R.string.app_name)).append("!\n").append("    ").append(getString(R.string.privacy_content_2)).append("    ").append(getString(R.string.privacy_content_3)).append(getPrivacyLink(context, PRIVACY_URL)).append(getString(R.string.privacy_content_4)).append("    ").append(getString(R.string.privacy_content_5)).append(getPrivacyLink(context, PRIVACY_URL)).append(getString(R.string.privacy_content_6)).append("    ").append(getString(R.string.privacy_content_7))
        }

        /**
         * @param context Links to privacy policy
         * @return
         */
        @Suppress("SameParameterValue")
        private fun getPrivacyLink(context: Context, privacyUrl: String): SpannableString {
            val privacyName = String.format(
                getString(R.string.lab_privacy_name), getString(R.string.app_name)
            )
            val spannableString = SpannableString(privacyName)
            spannableString.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    MaterialDialog.Builder(context)
                        .title(R.string.title_privacy_protocol)
                        .content(context.assets.open(privacyUrl).bufferedReader().use { it.readText() })
                        .positiveText(R.string.confirm)
                        .show()
                }
            }, 0, privacyName.length, Spanned.SPAN_MARK_MARK)
            return spannableString
        }

        /**
         * Request Browser
         *
         * @param url
         */
        @JvmStatic
        fun goWeb(context: Context, url: String?) {
            val intent = Intent(context, AgentWebActivity::class.java)
            intent.putExtra(AgentWebFragment.KEY_URL, url)
            context.startActivity(intent)
        }

        /**
         * Open user and privacy protocols
         *
         * @param fragment
         * @param isPrivacy   Is it a privacy agreement?
         * @param isImmersive Whether immersion
         */
        @JvmStatic
        fun gotoProtocol(fragment: XPageFragment?, isPrivacy: Boolean, isImmersive: Boolean) {
            PageOption.to(ServiceProtocolFragment::class.java).putString(
                ServiceProtocolFragment.KEY_PROTOCOL_TITLE, if (isPrivacy) getString(R.string.title_privacy_protocol) else getString(
                    R.string.title_user_protocol
                )
            ).putBoolean(ServiceProtocolFragment.KEY_IS_IMMERSIVE, isImmersive).open(fragment!!)
        }

        /**
         * Is it a dark color?
         *
         * @param color
         * @return
         */
        @JvmStatic
        fun isColorDark(@ColorInt color: Int): Boolean {
            return ColorUtils.isColorDark(color, 0.382)
        }

        //Insert Focus Position Text
        fun insertOrReplaceText2Cursor(editText: EditText, str: String) {
            if (TextUtils.isEmpty(str)) return

            editText.requestFocus()

            val editable = editText.text ?: return
            // Inserts at the selection start (a selection is kept, not replaced); no cursor appends. Editable.insert
            // keeps spans and the IME/undo history. Same rule as the unit-tested TemplateInsertion.
            val cursor = cn.ppps.forwarder.utils.template.TemplateInsertion.position(editText.selectionStart, editable.length)

            editable.insert(cursor, str)
            val newCursor = (cursor + str.length).coerceAtMost(editable.length)
            editText.setSelection(newCursor)
        }

        //==========Picture Preview===========//
        /**
         * Large Diagram Preview
         *
         * @param fragment
         * @param url      Picture Resources
         * @param view     Smallchart Load Controls
         */
        fun previewPicture(fragment: Fragment?, url: String, view: View?) {
            if (fragment == null || StringUtils.isEmpty(url)) {
                return
            }
            val bounds = Rect()
            view?.getGlobalVisibleRect(bounds)
            PreviewBuilder.from(fragment).setImgs(ImageInfo.newInstance(url, bounds)).setCurrentIndex(0).setSingleFling(true).setProgressColor(R.color.xui_config_color_main_theme).setType(PreviewBuilder.IndicatorType.Number).start()
        }

        /**
         * OpenMarkdownLink & Render
         *
         * @param fragment
         * @param url   MarkdownLink
         * @param isImmersive Whether immersion
         */
        @JvmStatic
        fun previewMarkdown(fragment: XPageFragment?, title: String, url: String, isImmersive: Boolean) {
            PageOption.to(MarkdownFragment::class.java).putString(MarkdownFragment.KEY_MD_TITLE, title).putString(MarkdownFragment.KEY_MD_URL, url).putBoolean(MarkdownFragment.KEY_IS_IMMERSIVE, isImmersive).open(fragment!!)
        }

        //Checks the validity of labels in custom templates
        fun checkTemplateTag(template: String): String {
            val tagRegex = "\\{\\{[^#]+###([^=]+)===(.*?)\\}\\}".toRegex()
            tagRegex.findAll(template).forEach {
                try {
                    it.groupValues[1].toRegex()
                    //TODO:How do you test the correctness of the reverse reference?？
                    /*val replacement = it.groupValues[2]
                    if (replacement.isNotEmpty()) {
                        "pppscn/SmsForwarder".replace(regex, replacement)
                    }*/
                } catch (e: Exception) {
                    return String.format(getString(R.string.invalid_tag), it.value, e.message)
                }
            }
            return ""
        }

        //Is it legal or not?url
        fun checkUrl(url: String?, emptyResult: Boolean = false): Boolean {
            if (url.isNullOrEmpty()) return emptyResult

            val regex = Regex("^https?://\\S+\$")
            return regex.matches(url)
        }

        //Is it legal or not?URL Scheme
        fun checkUrlScheme(url: String?, emptyResult: Boolean = false): Boolean {
            if (url.isNullOrEmpty()) return emptyResult

            val regex = Regex("^[a-zA-Z\\d]+://\\S+\$")
            return regex.matches(url)
        }

        //Is it legal or not?IPAddress
        fun checkIP(ip: String): String {
            if (TextUtils.isEmpty(ip)) return "Neither"

            if (ip.contains(".")) {
                val chunkIPv4 = "([\\d]|[1-9][\\d]|1[\\d][\\d]|2[0-4][\\d]|25[0-5])"
                val pattenIPv4 = Pattern.compile("^($chunkIPv4\\.){3}$chunkIPv4$")
                return if (pattenIPv4.matcher(ip).matches()) "IPv4" else "Neither"
            } else if (ip.contains(":")) {
                val chunkIPv6 = "([\\da-fA-F]{1,4})"
                val pattenIPv6 = Pattern.compile("^($chunkIPv6\\:){7}$chunkIPv6$")
                return if (pattenIPv6.matcher(ip).matches()) "IPv6" else "Neither"
            }
            return "Neither"
        }

        //Legitimate Domain Name
        fun checkDomain(domain: String): Boolean {
            val pattenDomain = Pattern.compile("^(?=^.{3,255}$)(?:(?:(?:[a-zA-Z\\d]|[a-zA-Z\\d][a-zA-Z\\d\\-]*[a-zA-Z\\d])\\.){1,126}(?:[A-Za-z\\d]|[A-Za-z\\d][A-Za-z\\d\\-]*[A-Za-z\\d]))$")
            return pattenDomain.matcher(domain).matches()
        }

        //Legitimate port number
        fun checkPort(port: String): Boolean {
            if (TextUtils.isEmpty(port)) return false
            val pattenPort = Pattern.compile("^((6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])|[0-5]?\\d{0,4})$")
            return pattenPort.matcher(port).matches()
        }

        fun checkEmail(email: String): Boolean {
            val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\$")
            return emailRegex.matches(email)
        }

        //Enable notification listening service
        fun isNotificationListenerServiceEnabled(context: Context): Boolean {
            val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
            return packageNames.contains(context.packageName)
        }

        //Toggle notification listener service
        fun toggleNotificationListenerService(context: Context) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                ComponentName(context.applicationContext, NotificationService::class.java), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                ComponentName(context.applicationContext, NotificationService::class.java), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        }

        //Get localIPAddress
        fun getIPAddresses(): List<String> {
            val ipAddresses = mutableListOf<String>()

            try {
                val networkInterfaces = NetworkInterface.getNetworkInterfaces()

                while (networkInterfaces.hasMoreElements()) {
                    val networkInterface = networkInterfaces.nextElement()
                    val addresses = networkInterface.inetAddresses

                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()

                        if (address is Inet4Address || address is Inet6Address) {
                            address.hostAddress?.let { ipAddresses.add(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("CommonUtils", "getIPAddresses: ${e.message}")
            }

            return ipAddresses
        }

        fun removeInterfaceFromIP(ipAddress: String): String {
            val index = ipAddress.indexOf("%")
            return if (index != -1) {
                ipAddress.substring(0, index)
            } else {
                ipAddress
            }
        }

        fun restartApplication() {
            val context = App.context
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName = intent?.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
            XUtil.exitApp()
        }

        /**
         * Template field actions: "Insert data" (a Material list with each item's name, exact token and a sample
         * value; the token is inserted unchanged at the cursor) and a local "Preview" with sample data.
         * Which tokens are offered per context, their order and exclusions are unchanged; see TemplatePicker.
         */
        fun createTagButtons(context: Context, gridLayout: GridLayout, editText: EditText, scene: String = "basic", excludeButtons: Array<String> = emptyArray()) {
            gridLayout.removeAllViews()
            cn.ppps.forwarder.widget.material.TemplatePicker.install(context, gridLayout, editText, scene, templateTags(scene, excludeButtons))
        }

        /** Tokens offered for a template field, in the same order and with the same rules as the former tag grid. */
        fun templateTags(scene: String = "basic", excludeButtons: Array<String> = emptyArray()): List<cn.ppps.forwarder.widget.material.TemplatePicker.Item> {
            // Converts the excluded button to a collection that is easy to search for
            val excludeSet = excludeButtons.toSet()

            // Build button data dynamically for the context
            val allButtons = when (scene) {
                "sms" -> SMS_TAG_MAP
                "call" -> CALL_TAG_MAP
                "app" -> APP_TAG_MAP
                else -> CALL_TAG_MAP + SMS_TAG_MAP + APP_TAG_MAP
            }.toMutableMap()

            if (SettingUtils.enableLocation) {
                allButtons += LOCATION_TAG_MAP
            }
            if (scene == "all") {
                allButtons += BATTERY_TAG_MAP
                allButtons += NETWORK_TAG_MAP
            }
            allButtons += COMMON_TAG_MAP

            // For calls {{SMS}} carries the call details (PhoneUtils.getCallMsg), not a notification text.
            if (scene == "call") allButtons[getString(R.string.tag_sms)] = getString(R.string.template_label_call_details)

            return allButtons.filterKeys { !excludeSet.contains(it) }.map { (tag, label) -> cn.ppps.forwarder.widget.material.TemplatePicker.Item(tag, label) }
        }

    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}
