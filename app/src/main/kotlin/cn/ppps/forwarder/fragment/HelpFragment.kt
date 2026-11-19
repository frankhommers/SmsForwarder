package cn.ppps.forwarder.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import br.tiagohm.markdownview.css.InternalStyleSheet
import br.tiagohm.markdownview.css.styles.Github
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.core.webview.AgentWebActivity
import cn.ppps.forwarder.databinding.InfraFormHelpBinding
import cn.ppps.forwarder.widget.material.TitleBar
import com.google.android.material.color.MaterialColors
import com.xuexiang.xpage.annotation.Page

/**
 * Offline help. Renders the bundled Markdown in assets/help with the existing MarkdownView, with WebView network
 * loads blocked before anything is loaded, so opening Help never contacts GitHub or any other host. The online
 * documentation opens only from the explicit button.
 */
@Page(name = "Help")
class HelpFragment : BaseFragment<InfraFormHelpBinding?>() {

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): InfraFormHelpBinding =
        InfraFormHelpBinding.inflate(inflater, container, false)

    override fun initTitle(): TitleBar? = super.initTitle()!!.setTitle(R.string.menu_help).setImmersive(false)

    override fun initViews() {
        val view = binding!!.infraFormHelpMarkdown
        view.settings.blockNetworkLoads = true
        view.addStyleSheet(Github())
        view.addStyleSheet(themeStyle())
        view.loadMarkdownFromAsset(if (resources.configuration.locale.language == "nl") HELP_NL else HELP_EN)
    }

    override fun initListeners() {
        binding!!.infraFormHelpOnline.setOnClickListener { AgentWebActivity.goWeb(context, getString(R.string.url_help)) }
    }

    /** Theme colours (light/dark) and the 16dp keyline for the local page. */
    private fun themeStyle(): InternalStyleSheet {
        val v = binding!!.root
        fun hex(attr: Int) = String.format("#%06X", 0xFFFFFF and MaterialColors.getColor(v, attr))
        return InternalStyleSheet().apply {
            addRule("body", "padding: 16px", "background-color: ${hex(com.google.android.material.R.attr.colorSurface)}",
                "color: ${hex(com.google.android.material.R.attr.colorOnSurface)}")
            addRule("h1, h2, h3", "color: ${hex(com.google.android.material.R.attr.colorOnSurface)}", "border-bottom: none")
            addRule("code", "color: ${hex(com.google.android.material.R.attr.colorOnSurface)}",
                "background-color: ${hex(com.google.android.material.R.attr.colorSurfaceVariant)}")
            addRule(".scrollup", "display: none")
        }
    }

    companion object {
        const val HELP_EN = "help/help.md"
        const val HELP_NL = "help/help_nl.md"
    }
}
