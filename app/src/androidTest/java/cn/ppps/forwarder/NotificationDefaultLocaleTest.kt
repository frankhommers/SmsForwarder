package cn.ppps.forwarder

import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.SharedPreference
import cn.ppps.forwarder.utils.SP_NOTIFY_CONTENT
import com.hjq.language.MultiLanguages
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/** Local preference/locale regression: no forwarding and no service activation. */
class NotificationDefaultLocaleTest {
    @Test fun unsetDefaultFollowsLocaleButStoredTextIsPreserved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SharedPreference.preference
        val existed = prefs.contains(SP_NOTIFY_CONTENT)
        val saved = prefs.getString(SP_NOTIFY_CONTENT, null)
        val oldLanguage = MultiLanguages.getAppLanguage(context)
        try {
            prefs.edit().remove(SP_NOTIFY_CONTENT).commit()
            val defaults = mutableListOf<String>()
            for (language in listOf("en", "nl", "en")) {
                MultiLanguages.setAppLanguage(context, Locale(language))
                val config = android.content.res.Configuration(context.resources.configuration)
                config.setLocale(Locale(language))
                val expected = context.createConfigurationContext(config).getString(R.string.notification_content)
                assertEquals("Unset default must follow $language without process restart", expected, SettingUtils.notifyContent)
                assertFalse("Reading a default must not store it", prefs.contains(SP_NOTIFY_CONTENT))
                defaults.add(expected)
            }
            assertNotEquals(defaults[0], defaults[1])
            for (stored in listOf("My custom notice", defaults[1], "")) {
                SettingUtils.notifyContent = stored
                for (language in listOf("en", "nl")) {
                    MultiLanguages.setAppLanguage(context, Locale(language))
                    assertEquals("Stored content must never be translated or cleared", stored, SettingUtils.notifyContent)
                }
            }
        } finally {
            if (existed) prefs.edit().putString(SP_NOTIFY_CONTENT, saved).commit()
            else prefs.edit().remove(SP_NOTIFY_CONTENT).commit()
            MultiLanguages.setAppLanguage(context, oldLanguage)
        }
    }
}
