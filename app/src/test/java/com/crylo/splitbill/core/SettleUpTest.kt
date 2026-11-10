package com.crylo.splitbill.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SettleUpTest {
    private fun apply(balances: Map<String, Long>, plan: List<Transfer>): Map<String, Long> {
        val out = balances.toMutableMap()
        plan.forEach {
            out[it.from] = out.getValue(it.from) + it.amount
            out[it.to] = out.getValue(it.to) - it.amount
        }
        return out
    }

    /** Minimum payments by brute force over every set partition, for small inputs. */
    private fun bruteForceMinimum(values: List<Long>): Int {
        val nonZero = values.filter { it != 0L }
        var bestGroups = 0
        fun search(index: Int, sums: MutableList<Long>) {
            if (index == nonZero.size) {
                if (sums.all { it == 0L }) bestGroups = maxOf(bestGroups, sums.size)
                return
            }
            for (i in sums.indices) {
                sums[i] += nonZero[index]
                search(index + 1, sums)
                sums[i] -= nonZero[index]
            }
            sums += nonZero[index]
            search(index + 1, sums)
            sums.removeAt(sums.lastIndex)
        }
        search(0, mutableListOf())
        return nonZero.size - bestGroups
    }

    @Test fun nothingToSettle() {
        assertEquals(emptyList<Transfer>(), SettleUp.plan(mapOf("a" to 0L, "b" to 0L)))
        assertEquals(emptyList<Transfer>(), SettleUp.plan(emptyMap()))
    }

    @Test fun simpleDebt() {
        assertEquals(listOf(Transfer("b", "a", 500)), SettleUp.plan(mapOf("a" to 500L, "b" to -500L)))
    }

    @Test fun chainCollapsesToOnePayment() {
        // b owes a 100 and c owes b 100 (b nets to zero): c should pay a directly.
        assertEquals(listOf(Transfer("c", "a", 100)), SettleUp.plan(mapOf("a" to 100L, "b" to 0L, "c" to -100L)))
    }

    @Test fun findsIndependentPairs() {
        // Pairing 3 with -3 and 2+5 with -7 takes 3 payments; n - 1 would be 4.
        val balances = mapOf("a" to 300L, "b" to 200L, "c" to 500L, "d" to -300L, "e" to -700L)
        val plan = SettleUp.plan(balances)
        assertEquals(3, plan.size)
        assertTrue(apply(balances, plan).values.all { it == 0L })
    }

    @Test fun planAlwaysSettlesEveryoneWithTheMinimumNumberOfPayments() {
        val random = Random(11)
        repeat(1_500) {
            val n = random.nextInt(2, 9)
            val values = MutableList(n - 1) { random.nextLong(-20, 21) * 100 }
            values += -values.sum()
            val balances = values.mapIndexed { i, v -> "m$i" to v }.toMap()
            val plan = SettleUp.plan(balances)
            assertTrue(apply(balances, plan).values.all { it == 0L })
            assertTrue(plan.all { it.amount > 0 && it.from != it.to })
            assertEquals("for $values", bruteForceMinimum(values), plan.size)
        }
    }

    @Test fun largeGroupsFallBackToAtMostNMinusOnePayments() {
        val random = Random(3)
        repeat(50) {
            val n = SettleUp.EXACT_LIMIT + random.nextInt(1, 10)
            val values = MutableList(n - 1) { random.nextLong(1, 100_000) * if (random.nextBoolean()) 1 else -1 }
            values += -values.sum()
            val balances = values.mapIndexed { i, v -> "m$i" to v }.toMap()
            val plan = SettleUp.plan(balances)
            assertTrue(apply(balances, plan).values.all { it == 0L })
            assertTrue(plan.size <= values.count { it != 0L } - 1)
        }
    }

    @Test fun exactPlanHandlesTheLimitQuickly() {
        val values = MutableList(SettleUp.EXACT_LIMIT - 1) { (it + 1) * 137L * if (it % 2 == 0) 1 else -1 }
        values += -values.sum()
        val balances = values.mapIndexed { i, v -> "m$i" to v }.toMap()
        val plan = SettleUp.plan(balances)
        assertTrue(apply(balances, plan).values.all { it == 0L })
    }
}
