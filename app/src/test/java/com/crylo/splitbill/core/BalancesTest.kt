package com.crylo.splitbill.core

import com.crylo.splitbill.core.TestData.expense
import com.crylo.splitbill.core.TestData.settlement
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class BalancesTest {
    @Test fun payerIsOwedTheOthersShares() {
        val net = Balances.compute(listOf("a", "b", "c"), listOf(expense("e", 900, "a", listOf("a", "b", "c"))), emptyList())
        assertEquals(mapOf("a" to 600L, "b" to -300L, "c" to -300L), net)
    }

    @Test fun multiplePayersAndUnevenSplits() {
        val e = Expense(
            "e", TestData.GID, "Hotel", 10_000, 0,
            payers = listOf(Portion("a", 6_000), Portion("b", 4_000)),
            splitMode = SplitMode.EXACT,
            splits = listOf(SplitEntry("a", 2_000, 2_000), SplitEntry("b", 3_000, 3_000), SplitEntry("c", 5_000, 5_000)),
            createdAt = 0, updatedAt = 0,
        )
        assertEquals(mapOf("a" to 4_000L, "b" to 1_000L, "c" to -5_000L), Balances.compute(listOf("a", "b", "c"), listOf(e), emptyList()))
    }

    @Test fun settlementMovesBalanceTowardZero() {
        val net = Balances.compute(
            listOf("a", "b"),
            listOf(expense("e", 1_000, "a", listOf("a", "b"))),
            listOf(settlement("s", from = "b", to = "a", amount = 500)),
        )
        assertEquals(mapOf("a" to 0L, "b" to 0L), net)
    }

    @Test fun membersWithNoActivityAreListedAtZero() {
        assertEquals(mapOf("a" to 0L, "b" to 0L), Balances.compute(listOf("a", "b"), emptyList(), emptyList()))
    }

    @Test fun balancesAlwaysNetToZero() {
        val random = Random(7)
        val members = (0 until 8).map { "m$it" }
        repeat(500) {
            val expenses = List(random.nextInt(1, 20)) { i ->
                val amount = random.nextLong(1, 1_000_000)
                val mode = SplitMode.entries.random(random)
                val among = members.shuffled(random).take(random.nextInt(1, members.size + 1))
                val inputs = when (mode) {
                    SplitMode.EQUAL -> members.map { it to if (it in among) 1L else 0L }
                    SplitMode.SHARES -> members.map { it to if (it in among) random.nextLong(1, 500) else 0L }
                    SplitMode.EXACT -> members.zip(Splitter.allocate(amount, members.map { random.nextLong(0, 10) + 1 }))
                    SplitMode.PERCENT -> members.zip(Splitter.allocate(Splitter.FULL_PERCENT, members.map { random.nextLong(1, 10) }))
                }
                val split = Splitter.split(amount, mode, inputs) as SplitOutcome.Valid
                val payerWeights = members.map { if (random.nextInt(3) == 0) 1L else 0L }.let { w -> if (w.sum() == 0L) listOf(1L) + w.drop(1) else w }
                val payers = members.zip(Splitter.allocate(amount, payerWeights)).filter { it.second > 0 }.map { Portion(it.first, it.second) }
                Expense("e$i", TestData.GID, "x", amount, 0, Category.GENERAL, payers, mode, split.entries, 0, 0)
            }
            val settlements = List(random.nextInt(0, 5)) { i ->
                val (from, to) = members.shuffled(random).take(2)
                settlement("s$i", from, to, random.nextLong(1, 100_000))
            }
            val net = Balances.compute(members, expenses, settlements)
            assertEquals(0L, net.values.sum())
        }
    }
}
