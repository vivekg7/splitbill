package com.crylo.splitbill.core

import com.crylo.splitbill.core.TestData.expense
import com.crylo.splitbill.core.TestData.group
import com.crylo.splitbill.core.TestData.member
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTest {
    private val strings = SummaryStrings(
        title = "%1\$s",
        total = "Total spent: %1\$s (%2\$s expenses)",
        balancesHeader = "Balances",
        getsBack = "%1\$s gets back %2\$s",
        owes = "%1\$s owes %2\$s",
        settled = "%1\$s is settled up",
        planHeader = "Settle up",
        transfer = "%1\$s pays %2\$s %3\$s",
        allSettled = "Everyone is settled up",
        upiLine = "(UPI: %1\$s)",
        footer = "Shared from SplitBill",
    )

    @Test fun textReadsWellInWhatsApp() {
        val snapshot = GroupSnapshot(
            group(),
            listOf(member("a", "Asha", upi = "asha@ybl"), member("b", "Ravi"), member("c", "Meera")),
            listOf(expense("e", 1_20_000, "a", listOf("a", "b")), expense("f", 50_000, "c", listOf("c"))),
            emptyList(),
        )
        val expected = """
            *Goa trip*
            Total spent: ₹1,700.00 (2 expenses)

            *Balances*
            • Asha gets back ₹600.00
            • Ravi owes ₹600.00
            • Meera is settled up

            *Settle up*
            • Ravi pays Asha ₹600.00 (UPI: asha@ybl)

            _Shared from SplitBill_
        """.trimIndent()
        assertEquals(expected, Summary.text(snapshot, strings))
    }

    @Test fun settledGroupSaysSo() {
        val snapshot = GroupSnapshot(group(), listOf(member("a")), emptyList(), emptyList())
        assertEquals(listOf("Everyone is settled up"), Summary.lines(snapshot, strings).transfers)
    }
}
