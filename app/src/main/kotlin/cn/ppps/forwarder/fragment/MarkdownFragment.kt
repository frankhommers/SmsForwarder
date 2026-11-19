package cn.ppps.forwarder.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import br.tiagohm.markdownview.css.styles.Github
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentMarkdownBinding
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import cn.ppps.forwarder.widget.material.TitleBar

@Page(name = "Markdown")
class MarkdownFragment : BaseFragment<FragmentMarkdownBinding?>() {
    companion object {
        const val KEY_MD_TITLE = "key_md_title"
        const val KEY_MD_URL = "key_md_url"
        const val KEY_IS_IMMERSIVE = "key_is_immersive"
    }

    @JvmField
    @AutoWired(name = KEY_MD_TITLE)
    var title: String? = null

    @JvmField
    @AutoWired(name = KEY_MD_URL)
    var url: String? = null

    @JvmField
    @AutoWired(name = KEY_IS_IMMERSIVE)
    var isImmersive = false

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentMarkdownBinding {
        return FragmentMarkdownBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        return super.initTitle()!!.setTitle(title).setImmersive(isImmersive)
    }

    /**
     * Initialize controls
     */
    override fun initViews() {
        // No remote markdown, fonts, images or scripts, even if this legacy page is routed to.
        binding!!.markdownView.settings.blockNetworkLoads = true
        binding!!.markdownView.addStyleSheet(Github())
        binding!!.markdownView.loadMarkdown(getString(cn.ppps.forwarder.R.string.copy_markdown_disabled))
    }

}