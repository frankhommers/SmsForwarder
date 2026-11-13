package cn.ppps.forwarder

import com.xuexiang.xui.widget.picker.widget.utils.ChinaDateUtils
import com.xuexiang.xutil.data.DateUtils
import com.xuexiang.xutil.data.ConvertTools
import com.xuexiang.xhttp2.exception.ApiException
import com.zzhoujay.richtext.exceptions.BitmapCacheNotFoundException
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class EnglishDependencyTest {
    @Test fun lunarCalculationAndNumericLabels() {
        val date = ChinaDateUtils.calElement(2024, 2, 10)
        assertEquals(2024L, date[0])
        assertEquals(1L, date[1])
        assertEquals(1L, date[2])
        assertEquals(2, ChinaDateUtils.leapMonth(2023))
        assertEquals(13, ChinaDateUtils.getMonths(2023).size)
        assertEquals("Leap 2 month ", ChinaDateUtils.getMonths(2023)[2])
        assertEquals((1..30).map { it.toString() }, ChinaDateUtils.getLunarDays(30))
        assertEquals("Dragon", ChinaDateUtils.AnimalsYear(2024))
        assertEquals("Jia-Chen year", ChinaDateUtils.getLunarYearText(2024))
    }

    @Test fun englishDatesAndUnits() {
        assertEquals("Dragon", DateUtils.getChineseZodiac(2024))
        assertEquals("Aquarius", DateUtils.getZodiac(2, 1))
        assertEquals("Just now", DateUtils.getFriendlyTimeSpanByNow(System.currentTimeMillis()))
        assertArrayEquals(byteArrayOf(0, 127, -1), ConvertTools.hexStringToByteArray("007fff"))
    }

    @Test fun englishErrorsPreserveFailureTypes() {
        assertEquals("Bitmap cache does not exist", BitmapCacheNotFoundException().message)
        val timeout = ApiException.handleException(SocketTimeoutException("timed out"))
        assertTrue(timeout.message!!.contains("timed out"))
        val certificate = ApiException.handleException(SSLHandshakeException("bad certificate"))
        assertEquals("Certificate verification failed", certificate.message)
    }

    @Test fun aspectSingletonAbiPreserved() {
        for (name in listOf("IOThreadAspectJ", "MainThreadAspectJ", "InterceptAspectJ", "PermissionAspectJ", "SingleClickAspectJ")) {
            val type = Class.forName("com.xuexiang.xaop.aspectj.$name")
            assertEquals(true, type.getMethod("hasAspect").invoke(null))
            assertNotNull(type.getMethod("aspectOf").invoke(null))
        }
    }
}
