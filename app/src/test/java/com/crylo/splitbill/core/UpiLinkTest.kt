package com.crylo.splitbill.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiLinkTest {
    @Test fun buildsEncodedLink() {
        assertEquals(
            "upi://pay?pa=asha@okicici&pn=Asha%20K&am=1234.50&cu=INR&tn=Goa%20trip%20%26%20more",
            UpiLink.build("asha@okicici", "Asha K", 123_450, "Goa trip & more"),
        )
    }

    @Test fun validatesVpa() {
        assertTrue(UpiLink.isValidVpa("asha.k-1@okhdfcbank"))
        assertTrue(UpiLink.isValidVpa(" 9876543210@ybl "))
        assertFalse(UpiLink.isValidVpa("asha"))
        assertFalse(UpiLink.isValidVpa("asha@"))
        assertFalse(UpiLink.isValidVpa("a b@ybl"))
        assertFalse(UpiLink.isValidVpa("asha@1bank"))
    }
}
