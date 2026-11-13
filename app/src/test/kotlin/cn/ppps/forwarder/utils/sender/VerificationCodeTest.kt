package cn.ppps.forwarder.utils.sender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeTest {
    @Test fun englishAndDutch() {
        assertEquals("123456", VerificationCode.extract("Your verification code is 123456."))
        assertEquals("123456", VerificationCode.extract("Uw verificatiecode: 123 456"))
        assertEquals("A1B2C3", VerificationCode.extract("Inlogcode: A1B2C3"))
        assertEquals("123456", VerificationCode.extract("123456 is your code"))
        assertEquals("123456", VerificationCode.extract("Verification code: G-123456"))
    }
    @Test fun noCodeDoesNotCopyMessage() {
        assertNull(VerificationCode.extract("Your verification code will arrive later."))
        assertNull(VerificationCode.extract("Call 12345678 tomorrow"))
        assertNull(VerificationCode.extract("code: 123456789"))
    }
}
