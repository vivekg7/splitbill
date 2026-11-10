package com.crylo.splitbill.core

/**
 * Wording for [Summary], supplied from string resources so the shared text is
 * translatable. Each template uses positional `%1$s` / `%2$s` placeholders.
 */
data class SummaryStrings(
    /** %1$s group name. */
    val title: String,
    /** %1$s total spent, %2$s number of expenses. */
    val total: String,
    val balancesHeader: String,
    /** %1$s member, %2$s amount. */
    val getsBack: String,
    /** %1$s member, %2$s amount. */
    val owes: String,
    /** %1$s member. */
    val settled: String,
    val planHeader: String,
    /** %1$s from, %2$s to, %3$s amount. */
    val transfer: String,
    val allSettled: String,
    /** %1$s UPI id. */
    val upiLine: String,
    val footer: String,
)

/** A group summary as plain text and lines for an image: balances and who pays whom. */
object Summary {
    /** Lines shared by the text and image renderings, so the two never drift apart. */
    data class Lines(
        val title: String,
        val total: String,
        val balancesHeader: String,
        val balances: List<Pair<String, Long>>, // display line to signed balance
        val planHeader: String,
        val transfers: List<String>,
        val footer: String,
    )

    fun lines(
        snapshot: GroupSnapshot,
        strings: SummaryStrings,
    ): Lines {
        val symbol = snapshot.group.currency
        val names = snapshot.members.associate { it.id to it.name }
        val upi = snapshot.members.associate { it.id to it.upiId }
        val balances = Balances.compute(snapshot.members.map { it.id }, snapshot.expenses, snapshot.settlements)
        val total = snapshot.expenses.sumOf { it.amount }
        val balanceLines = balances.map { (id, value) ->
            val name = names[id] ?: "?"
            val line = when {
                value > 0 -> strings.getsBack.format(name, Money.format(value, symbol))
                value < 0 -> strings.owes.format(name, Money.format(-value, symbol))
                else -> strings.settled.format(name)
            }
            line to value
        }
        val plan = SettleUp.plan(balances)
        val transferLines = if (plan.isEmpty()) {
            listOf(strings.allSettled)
        } else {
            plan.map { t ->
                val line = strings.transfer.format(names[t.from] ?: "?", names[t.to] ?: "?", Money.format(t.amount, symbol))
                val vpa = upi[t.to]
                if (!vpa.isNullOrBlank() && Money.isRupee(symbol)) line + " " + strings.upiLine.format(vpa) else line
            }
        }
        return Lines(
            title = strings.title.format(snapshot.group.name),
            total = strings.total.format(Money.format(total, symbol), snapshot.expenses.size),
            balancesHeader = strings.balancesHeader,
            balances = balanceLines,
            planHeader = strings.planHeader,
            transfers = transferLines,
            footer = strings.footer,
        )
    }

    /** WhatsApp renders `*text*` as bold, which is why the headings are starred. */
    fun text(snapshot: GroupSnapshot, strings: SummaryStrings): String {
        val l = lines(snapshot, strings)
        return buildString {
            appendLine("*${l.title}*")
            appendLine(l.total)
            appendLine()
            appendLine("*${l.balancesHeader}*")
            l.balances.forEach { appendLine("• ${it.first}") }
            appendLine()
            appendLine("*${l.planHeader}*")
            l.transfers.forEach { appendLine("• $it") }
            appendLine()
            append("_${l.footer}_")
        }
    }
}
