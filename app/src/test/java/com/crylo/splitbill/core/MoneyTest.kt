package com.crylo.splitbill.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesCommonInputs() {
        assertEquals(123450L, Money.parse("1,234.5"))
        assertEquals(9900L, Money.parse("₹ 99"))
        assertEquals(5L, Money.parse(".05"))
        assertEquals(100L, Money.parse("1."))
        assertEquals(0L, Money.parse("0"))
    }

    @Test fun rejectsBadInputs() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("."))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("12a"))
        assertNull(Money.parse("99999999999999"))
        assertNull(Money.parse("101", max = 10_000))
    }

    @Test fun formatsRupeesWithIndianGrouping() {
        assertEquals("₹1,23,45,678.90", Money.format(1_234_567_890L, "₹"))
        assertEquals("₹999.00", Money.format(99_900L, "₹"))
        assertEquals("₹1,000.00", Money.format(100_000L, "₹"))
        assertEquals("-₹0.05", Money.format(-5L, "₹"))
    }

    @Test fun formatsOtherCurrenciesInThousands() {
        assertEquals("$12,345,678.90", Money.format(1_234_567_890L, "$"))
        assertEquals("€0.00", Money.format(0L, "€"))
    }

    @Test fun plainAndCompactForms() {
        assertEquals("1234.50", Money.plain(123450L))
        assertEquals("0.07", Money.plain(7L))
        assertEquals("33.33", Money.compact(3333L))
        assertEquals("12.5", Money.compact(1250L))
        assertEquals("1", Money.compact(100L))
    }
}
