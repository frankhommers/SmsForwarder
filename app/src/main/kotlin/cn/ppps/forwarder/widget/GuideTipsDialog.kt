package cn.ppps.forwarder.widget

import android.content.Context
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.http.entity.TipInfo
import cn.ppps.forwarder.utils.AppUtils
import cn.ppps.forwarder.utils.SharedPreference
import com.xuexiang.xaop.annotation.SingleClick
import androidx.appcompat.app.AlertDialog
import com.xuexiang.xutil.resource.ResUtils.getString
import com.zzhoujay.richtext.RichText

/**
 * Tips dialog
 *
 * @author xuexiang
 * @since 2019-08-22 17:02
 */
@Suppress("SameReturnValue")
class GuideTipsDialog(context: Context?, tips: List<TipInfo>) :
    AlertDialog(requireNotNull(context), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog), View.OnClickListener,
    CompoundButton.OnCheckedChangeListener {
    private var mTips: List<TipInfo>? = null
    private var mIndex = -1
    private var mTvPrevious: TextView? = null
    private var mTvNext: TextView? = null
    private var mTvTitle: TextView? = null
    private var mTvContent: TextView? = null

    /**
     * Initialize dialog
     */
    private fun initViews() {
        mTvTitle = findViewById(R.id.tv_title)
        mTvContent = findViewById(R.id.tv_content)
        val cbIgnore = findViewById<AppCompatCheckBox>(R.id.cb_ignore)
        val ivClose = findViewById<ImageView>(R.id.iv_close)
        mTvPrevious = findViewById(R.id.tv_previous)
        mTvNext = findViewById(R.id.tv_next)
        if (cbIgnore != null) {
            cbIgnore.isChecked = isIgnoreTips
            cbIgnore.setOnCheckedChangeListener(this)
        }
        ivClose?.setOnClickListener(this)
        mTvPrevious!!.setOnClickListener(this)
        mTvNext!!.setOnClickListener(this)
        mTvPrevious!!.isEnabled = false
        mTvNext!!.isEnabled = true
        // Back, outside tap and Close all dismiss; Previous/Next page without closing the dialog.
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        setOnShowListener {
            getButton(BUTTON_NEGATIVE)?.setOnClickListener { onClick(mTvPrevious!!) }
            getButton(BUTTON_POSITIVE)?.setOnClickListener { onClick(mTvNext!!) }
            syncButtons()
        }
    }

    /** Mirrors the legacy Previous/Next enabled state onto the dialog's Material buttons and shows the page number. */
    private fun syncButtons() {
        getButton(BUTTON_NEGATIVE)?.isEnabled = mTvPrevious?.isEnabled == true
        getButton(BUTTON_POSITIVE)?.isEnabled = mTvNext?.isEnabled == true
        val count = mTips?.size ?: 0
        findViewById<TextView>(R.id.tv_tip_page)?.apply {
            visibility = if (count > 1) View.VISIBLE else View.GONE
            text = context.getString(R.string.infra_form_tip_page, mIndex + 1, count)
        }
    }

    /**
     * Update Hint Information
     *
     * @param tips Hint Information
     */
    private fun updateTips(tips: List<TipInfo>) {
        mTips = tips
        if (mTips!!.isNotEmpty() && mTvContent != null) {
            mIndex = 0
            showRichText(mTips!![mIndex])
            mTvNext!!.isEnabled = mTips!!.size > 1
        }
        syncButtons()
    }

    /**
     * Toggle hint information
     *
     * @param index Index
     */
    private fun switchTipInfo(index: Int) {
        if (mTips != null && mTips!!.isNotEmpty() && mTvContent != null) {
            if (index >= 0 && index <= mTips!!.size - 1) {
                showRichText(mTips!![index])
                when (index) {
                    0 -> {
                        mTvPrevious!!.isEnabled = false
                        mTvNext!!.isEnabled = true
                    }

                    mTips!!.size - 1 -> {
                        mTvPrevious!!.isEnabled = true
                        mTvNext!!.isEnabled = false
                    }

                    else -> {
                        mTvPrevious!!.isEnabled = true
                        mTvNext!!.isEnabled = true
                    }
                }
                syncButtons()
            }
        }
    }

    /**
     * Show Rich Text
     *
     * @param tipInfo Hint Information
     */
    private fun showRichText(tipInfo: TipInfo) {
        mTvTitle!!.text = tipInfo.title
        // Bundled tips only: no images and no clickable remote links.
        RichText.fromHtml(tipInfo.content)
            .noImage(true)
            .clickable(false)
            .bind(this)
            .into(mTvContent)
    }

    @SingleClick(300)
    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.iv_close) {
            dismiss()
        } else if (id == R.id.tv_previous) {
            if (mIndex > 0) {
                mIndex--
                switchTipInfo(mIndex)
            }
        } else if (id == R.id.tv_next) {
            if (mIndex < mTips!!.size - 1) {
                mIndex++
                switchTipInfo(mIndex)
            }
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        setIsIgnoreTips(isChecked)
    }

    override fun onDetachedFromWindow() {
        RichText.clear(this)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val KEY_IS_IGNORE_TIPS =
            "cn.ppps.forwarder.widget.key_is_ignore_tips_"

        /**
         * Show Hints
         *
         * @param context Context
         */
        @JvmStatic
        fun showTips(context: Context?) {
            if (!isIgnoreTips) {
                showTipsForce(context)
            }
        }

        /**
         * Force display of hints
         *
         * @param context Context
         */
        @JvmStatic
        fun showTipsForce(context: Context?) {
            if (context == null) return
            val json = context.assets.open(if (context.resources.configuration.locale.language == "nl") "tips_nl.json" else "tips.json").bufferedReader().use { it.readText() }
            val data = Gson().fromJson(json, JsonObject::class.java)["Data"].asJsonArray
            val listType = object : TypeToken<List<TipInfo>>() {}.type
            GuideTipsDialog(context, Gson().fromJson<List<TipInfo>>(data, listType)).show()
        }

        fun setIsIgnoreTips(isIgnore: Boolean): Boolean {
            this.isIgnoreTips = isIgnore
            return true
        }

        var isIgnoreTips: Boolean by SharedPreference(KEY_IS_IGNORE_TIPS + AppUtils.getAppVersionCode(), false)

    }

    init {
        setTitle(R.string.settings_setup)
        setView(android.view.LayoutInflater.from(getContext()).inflate(R.layout.dialog_guide_tips, null))
        setButton(BUTTON_NEUTRAL, getContext().getString(R.string.m3_close)) { _, _ -> dismiss() }
        setButton(BUTTON_NEGATIVE, getContext().getString(R.string.label_previous)) { _, _ -> }
        setButton(BUTTON_POSITIVE, getContext().getString(R.string.label_next)) { _, _ -> }
        create()
        initViews()
        updateTips(tips)
    }
}
