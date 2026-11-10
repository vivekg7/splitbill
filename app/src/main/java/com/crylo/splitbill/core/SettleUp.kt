package com.crylo.splitbill.core

/** "[from] pays [to] [amount]": one step of a settle-up plan. */
data class Transfer(val from: String, val to: String, val amount: Long)

/**
 * Debt simplification: given net balances that sum to zero, find the fewest payments
 * that bring everyone to zero.
 *
 * A group of k people whose balances sum to zero can always be settled in k - 1
 * payments, and never fewer if no smaller subgroup also sums to zero. So the minimum for
 * the whole group is n minus the largest number of disjoint zero-sum subgroups the
 * balances can be split into. Finding that partition is NP-hard in general, but with n
 * people who are owed or owe something it is a 2^n subset DP: instant for any real
 * group. Above [EXACT_LIMIT] people with non-zero balances the plan falls back to the
 * classic greedy (largest debtor pays largest creditor), which is never worse than
 * n - 1 payments but not always minimal.
 */
object SettleUp {
    const val EXACT_LIMIT = 16

    fun plan(balances: Map<String, Long>): List<Transfer> {
        require(balances.values.sum() == 0L) { "balances must sum to zero" }
        val people = balances.entries.filter { it.value != 0L }.map { it.key to it.value }
        if (people.isEmpty()) return emptyList()
        if (people.size > EXACT_LIMIT) return greedy(people)
        return zeroSumGroups(people).flatMap { group -> greedy(group.map { people[it] }) }
    }

    /**
     * Splits [people] into the largest possible number of disjoint zero-sum groups.
     * dp[mask] is the most zero-sum prefixes any ordering of the people in mask can have;
     * the groups are the stretches between those prefixes in the best ordering.
     */
    private fun zeroSumGroups(people: List<Pair<String, Long>>): List<List<Int>> {
        val n = people.size
        val full = (1 shl n) - 1
        val sum = LongArray(1 shl n)
        val dp = IntArray(1 shl n)
        for (mask in 1..full) {
            val low = Integer.numberOfTrailingZeros(mask)
            sum[mask] = sum[mask and (mask - 1)] + people[low].second
            var best = 0
            var bits = mask
            while (bits != 0) {
                val bit = bits and -bits
                best = maxOf(best, dp[mask xor bit])
                bits = bits xor bit
            }
            dp[mask] = best + if (sum[mask] == 0L) 1 else 0
        }
        // Walk back from the full set, removing one person at a time along an optimal
        // path; reversed, that is an ordering whose zero-sum prefixes delimit the groups.
        val removed = ArrayList<Int>(n)
        var mask = full
        while (mask != 0) {
            val gain = if (sum[mask] == 0L) 1 else 0
            var bits = mask
            var pick = -1
            while (bits != 0) {
                val bit = bits and -bits
                if (dp[mask xor bit] + gain == dp[mask]) {
                    pick = Integer.numberOfTrailingZeros(bit)
                    break
                }
                bits = bits xor bit
            }
            removed += pick
            mask = mask xor (1 shl pick)
        }
        val groups = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var running = 0L
        for (i in removed.asReversed()) {
            current += i
            running += people[i].second
            if (running == 0L) {
                groups += current.sorted()
                current = mutableListOf()
            }
        }
        return groups
    }

    /**
     * Repeatedly has the biggest debtor pay the biggest creditor as much as settles one
     * of them. Each payment zeroes at least one person and the last zeroes two, so k
     * people take at most k - 1 payments. Ties go to the earlier person for determinism.
     */
    private fun greedy(people: List<Pair<String, Long>>): List<Transfer> {
        val ids = people.map { it.first }
        val bal = people.map { it.second }.toLongArray()
        val out = mutableListOf<Transfer>()
        while (true) {
            var creditor = -1
            var debtor = -1
            for (i in bal.indices) {
                if (bal[i] > 0 && (creditor < 0 || bal[i] > bal[creditor])) creditor = i
                if (bal[i] < 0 && (debtor < 0 || bal[i] < bal[debtor])) debtor = i
            }
            if (creditor < 0 || debtor < 0) break
            val amount = minOf(bal[creditor], -bal[debtor])
            out += Transfer(ids[debtor], ids[creditor], amount)
            bal[creditor] -= amount
            bal[debtor] += amount
        }
        return out
    }
}
