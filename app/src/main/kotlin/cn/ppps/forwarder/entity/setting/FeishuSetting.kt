package cn.ppps.forwarder.entity.setting

import cn.ppps.forwarder.R
import java.io.Serializable

data class FeishuSetting(
    var webhook: String = "",
    val secret: String = "",
    val msgType: String = "interactive",
    val titleTemplate: String = "",
    val messageCard: String = "", //Custom message card
    val atAll: Boolean = false,   //@Everyone
    val atOpenIds: String = "",   //@Designated person, comma separated open_id or user_id
) : Serializable {

    fun getMsgTypeCheckId(): Int {
        return if (msgType == "interactive") {
            R.id.rb_msg_type_interactive
        } else {
            R.id.rb_msg_type_text
        }
    }
}