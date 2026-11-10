package com.crylo.splitbill.core

object Balances {
    /**
     * Net balance per member: what they paid (or sent in settlements) minus what they
     * owe (or received). Positive means the group owes them; negative means they owe.
     * Because every expense's payers and shares each sum to its amount, and a settlement
     * adds and subtracts the same figure, the balances always sum to zero.
     *
     * The map keeps [memberIds] order, followed by any id that only appears in entries.
     */
    fun compute(
        memberIds: List<String>,
        expenses: List<Expense>,
        settlements: List<Settlement>,
    ): Map<String, Long> {
        val net = LinkedHashMap<String, Long>()
        memberIds.forEach { net[it] = 0L }
        fun add(id: String, delta: Long) {
            net[id] = (net[id] ?: 0L) + delta
        }
        for (e in expenses) {
            e.payers.forEach { add(it.memberId, it.amount) }
            e.splits.forEach { add(it.memberId, -it.share) }
        }
        for (s in settlements) {
            add(s.fromId, s.amount)
            add(s.toId, -s.amount)
        }
        return net
    }
}
