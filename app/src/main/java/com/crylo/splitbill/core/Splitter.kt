package com.crylo.splitbill.core

/** Why a split cannot be saved yet; drives the live hint under the split editor. */
sealed interface SplitProblem {
    data object NoAmount : SplitProblem
    data object NoParticipants : SplitProblem

    /** EXACT: [remaining] minor units still to assign (negative when over-assigned). */
    data class AmountMismatch(val remaining: Long) : SplitProblem

    /** PERCENT: [remaining] basis points still to assign (negative when over 100%). */
    data class PercentMismatch(val remaining: Long) : SplitProblem
}

sealed interface SplitOutcome {
    data class Valid(val entries: List<SplitEntry>) : SplitOutcome
    data class Invalid(val problem: SplitProblem) : SplitOutcome
}

object Splitter {
    const val FULL_PERCENT = 10_000L // 100.00% in basis points

    /**
     * Divides [total] in proportion to [weights] so the parts always sum to exactly
     * [total]. Each part is first rounded down; the minor units left over go one each to
     * the parts with the largest discarded fraction (largest-remainder method), ties to
     * the earlier position. Position order is the group's member order, so the same
     * input always gives the same result on every phone.
     */
    fun allocate(total: Long, weights: List<Long>): List<Long> {
        require(total >= 0) { "total must not be negative" }
        require(weights.all { it >= 0 }) { "weights must not be negative" }
        val weightSum = weights.sum()
        if (weightSum == 0L) return weights.map { 0L }
        val base = LongArray(weights.size)
        val fraction = LongArray(weights.size)
        weights.forEachIndexed { i, w ->
            val product = Math.multiplyExact(total, w)
            base[i] = product / weightSum
            fraction[i] = product % weightSum
        }
        var leftover = total - base.sum()
        val order = weights.indices.filter { weights[it] > 0 }
            .sortedWith(compareByDescending<Int> { fraction[it] }.thenBy { it })
        for (i in order) {
            if (leftover == 0L) break
            base[i]++
            leftover--
        }
        return base.toList()
    }

    /**
     * Turns the editor's per-member inputs (in member order) into owed shares, or says
     * what is wrong. See [SplitEntry.input] for each mode's unit.
     */
    fun split(total: Long, mode: SplitMode, inputs: List<Pair<String, Long>>): SplitOutcome {
        if (total <= 0) return SplitOutcome.Invalid(SplitProblem.NoAmount)
        val values = inputs.map { it.second.coerceAtLeast(0) }
        val shares: List<Long> = when (mode) {
            SplitMode.EQUAL -> {
                val weights = values.map { if (it > 0) 1L else 0L }
                if (weights.sum() == 0L) return SplitOutcome.Invalid(SplitProblem.NoParticipants)
                allocate(total, weights)
            }
            SplitMode.EXACT -> {
                val remaining = total - values.sum()
                if (remaining != 0L) return SplitOutcome.Invalid(SplitProblem.AmountMismatch(remaining))
                values
            }
            SplitMode.SHARES -> {
                if (values.sum() == 0L) return SplitOutcome.Invalid(SplitProblem.NoParticipants)
                allocate(total, values)
            }
            SplitMode.PERCENT -> {
                val remaining = FULL_PERCENT - values.sum()
                if (remaining != 0L) return SplitOutcome.Invalid(SplitProblem.PercentMismatch(remaining))
                allocate(total, values)
            }
        }
        val normalized = if (mode == SplitMode.EQUAL) values.map { if (it > 0) 1L else 0L } else values
        return SplitOutcome.Valid(
            inputs.indices.map { SplitEntry(inputs[it].first, normalized[it], shares[it]) },
        )
    }

    /** Minor units of [total] not yet covered by [payers]; negative when overpaid. */
    fun unpaid(total: Long, payers: List<Portion>): Long = total - payers.sumOf { it.amount }
}
