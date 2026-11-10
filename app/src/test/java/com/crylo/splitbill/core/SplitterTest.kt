package com.crylo.splitbill.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SplitterTest {
    private fun shares(outcome: SplitOutcome): List<Long> =
        (outcome as SplitOutcome.Valid).entries.map { it.share }

    private fun ids(vararg values: Long) = values.mapIndexed { i, v -> "m$i" to v }

    @Test fun equalSplitGivesLeftoverPaiseToEarliestMembers() {
        assertEquals(listOf(3334L, 3333L, 3333L), shares(Splitter.split(10_000, SplitMode.EQUAL, ids(1, 1, 1))))
        assertEquals(listOf(26L, 26L, 25L, 25L), shares(Splitter.split(102, SplitMode.EQUAL, ids(1, 1, 1, 1))))
    }

    @Test fun equalSplitAmongSubsetLeavesOthersAtZero() {
        val result = Splitter.split(1_000, SplitMode.EQUAL, ids(1, 0, 1)) as SplitOutcome.Valid
        assertEquals(listOf(500L, 0L, 500L), result.entries.map { it.share })
        assertEquals(listOf(1L, 0L, 1L), result.entries.map { it.input })
    }

    @Test fun equalSplitNeedsSomeone() {
        assertEquals(
            SplitOutcome.Invalid(SplitProblem.NoParticipants),
            Splitter.split(1_000, SplitMode.EQUAL, ids(0, 0)),
        )
    }

    @Test fun zeroAmountIsInvalid() {
        assertEquals(SplitOutcome.Invalid(SplitProblem.NoAmount), Splitter.split(0, SplitMode.EQUAL, ids(1)))
    }

    @Test fun exactSplitMustAddUp() {
        assertEquals(listOf(700L, 300L), shares(Splitter.split(1_000, SplitMode.EXACT, ids(700, 300))))
        assertEquals(
            SplitOutcome.Invalid(SplitProblem.AmountMismatch(200)),
            Splitter.split(1_000, SplitMode.EXACT, ids(500, 300)),
        )
        assertEquals(
            SplitOutcome.Invalid(SplitProblem.AmountMismatch(-100)),
            Splitter.split(1_000, SplitMode.EXACT, ids(800, 300)),
        )
    }

    @Test fun sharesSplitIsProportionalWithLargestRemainder() {
        // 1 : 2 of ₹1.00 is 33.33... : 66.66...; the larger fraction gets the spare paisa.
        assertEquals(listOf(33L, 67L), shares(Splitter.split(100, SplitMode.SHARES, ids(100, 200))))
        // Fractional shares: 1.5 : 1 of ₹10.
        assertEquals(listOf(600L, 400L), shares(Splitter.split(1_000, SplitMode.SHARES, ids(150, 100))))
    }

    @Test fun percentSplitMustTotalHundred() {
        assertEquals(
            SplitOutcome.Invalid(SplitProblem.PercentMismatch(1)),
            Splitter.split(10_000, SplitMode.PERCENT, ids(3333, 3333, 3333)),
        )
        assertEquals(
            listOf(3334L, 3333L, 3333L),
            shares(Splitter.split(10_000, SplitMode.PERCENT, ids(3334, 3333, 3333))),
        )
        assertEquals(listOf(125L, 875L), shares(Splitter.split(1_000, SplitMode.PERCENT, ids(1250, 8750))))
    }

    @Test fun allocationAlwaysSumsToTotalAndStaysWithinOnePaisa() {
        val random = Random(42)
        repeat(5_000) {
            val total = random.nextLong(1, 10_000_000)
            val weights = List(random.nextInt(1, 12)) { random.nextLong(0, 5_000) }
            if (weights.sum() == 0L) return@repeat
            val parts = Splitter.allocate(total, weights)
            assertEquals(total, parts.sum())
            parts.forEachIndexed { i, part ->
                val exact = total.toDouble() * weights[i] / weights.sum()
                assertTrue("part $part vs $exact", part >= kotlin.math.floor(exact) && part <= kotlin.math.floor(exact) + 1)
                if (weights[i] == 0L) assertEquals(0L, part)
            }
        }
    }

    @Test fun allocationIsDeterministic() {
        val a = Splitter.allocate(1_001, listOf(1, 1, 1, 1, 1, 1, 1))
        val b = Splitter.allocate(1_001, listOf(1, 1, 1, 1, 1, 1, 1))
        assertEquals(a, b)
        assertEquals(listOf(143L, 143L, 143L, 143L, 143L, 143L, 143L), a)
        assertEquals(listOf(144L, 144L, 143L, 143L, 143L, 143L, 143L), Splitter.allocate(1_003, List(7) { 1L }))
    }

    @Test fun largestValuesDoNotOverflow() {
        val parts = Splitter.allocate(Money.MAX_AMOUNT, listOf(Money.MAX_WEIGHT, 1, 1))
        assertEquals(Money.MAX_AMOUNT, parts.sum())
    }

    @Test fun unpaidReportsRemainder() {
        assertEquals(300L, Splitter.unpaid(1_000, listOf(Portion("a", 700))))
        assertEquals(-50L, Splitter.unpaid(1_000, listOf(Portion("a", 700), Portion("b", 350))))
    }
}
