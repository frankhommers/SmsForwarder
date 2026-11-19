package cn.ppps.forwarder.entity.setting

import cn.ppps.forwarder.R
import java.io.Serializable

data class EmailSetting(
    var mailType: String = "",
    var fromEmail: String = "",
    var pwd: String = "",
    var nickname: String = "",
    var host: String = "",
    var port: String = "",
    var ssl: Boolean = false,
    var startTls: Boolean = false,
    var title: String = "",
    var recipients: MutableMap<String, Pair<String, String>> = mutableMapOf(),
    var toEmail: String = "",
    var keystore: String = "",
    var password: String = "",
    var encryptionProtocol: String = "Plain", //Encryption protocol: S/MIME,OpenPGP,OpenKeychain,Plain（Do not provide a certificate）
    var fromEmailAlias: String = "", //Mailbox alias
    var openKeychainSignKeyId: Long = 0L, //OpenKeychainSign KeyID（0=Do not sign）
    var openKeychainSignKeyDesc: String = "", //OpenKeychainSign key description (for interface echo only)）
    // S/MIME, OpenPGP, OpenKeychain only: send an unencrypted copy when encryption cannot be prepared for a recipient.
    // Default false, also when the property is absent from older stored JSON (safe default: never plaintext silently).
    var allowUnencryptedFallback: Boolean = false,
) : Serializable {

    companion object {
        /**
         * Serialize [setting] on top of the previously stored JSON so properties this version does not know
         * (written by a newer build or another fork) survive a save. Known properties keep their existing names.
         */
        @JvmStatic
        fun mergeJson(previousJson: String?, setting: EmailSetting): String {
            val gson = com.google.gson.Gson()
            val merged = try {
                if (previousJson.isNullOrBlank()) com.google.gson.JsonObject()
                else com.google.gson.JsonParser.parseString(previousJson).takeIf { it.isJsonObject }?.asJsonObject ?: com.google.gson.JsonObject()
            } catch (e: Exception) {
                com.google.gson.JsonObject()
            }
            for ((key, value) in gson.toJsonTree(setting).asJsonObject.entrySet()) merged.add(key, value)
            return gson.toJson(merged)
        }
    }

    fun getEncryptionProtocolCheckId(): Int {
        return when (encryptionProtocol) {
            "S/MIME" -> R.id.rb_encryption_protocol_smime
            "OpenPGP" -> R.id.rb_encryption_protocol_openpgp
            "OpenKeychain" -> R.id.rb_encryption_protocol_openkeychain
            else -> R.id.rb_encryption_protocol_plain
        }
    }
}
