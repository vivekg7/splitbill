package com.crylo.splitbill.core

/** Small builders so tests read as scenarios rather than constructor noise. */
object TestData {
    const val GID = "g1"

    fun group(updatedAt: Long = 1, name: String = "Goa trip") =
        Group(GID, name, "₹", createdAt = 1, updatedAt = updatedAt)

    fun member(id: String, name: String = id.uppercase(), upi: String? = null, at: Long = 1) =
        Member(id, GID, name, upi, createdAt = at, updatedAt = at)

    fun expense(
        id: String,
        amount: Long,
        paidBy: String,
        among: List<String>,
        updatedAt: Long = 1,
        description: String = "Dinner",
    ): Expense {
        val split = Splitter.split(amount, SplitMode.EQUAL, among.map { it to 1L }) as SplitOutcome.Valid
        return Expense(
            id, GID, description, amount, date = 20_000, payers = listOf(Portion(paidBy, amount)),
            splitMode = SplitMode.EQUAL, splits = split.entries, createdAt = 1, updatedAt = updatedAt,
        )
    }

    fun settlement(id: String, from: String, to: String, amount: Long, updatedAt: Long = 1) =
        Settlement(id, GID, from, to, amount, date = 20_000, createdAt = 1, updatedAt = updatedAt)
}
