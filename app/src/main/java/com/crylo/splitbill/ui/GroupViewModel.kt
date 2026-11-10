package com.crylo.splitbill.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crylo.splitbill.core.Backup
import com.crylo.splitbill.core.Balances
import com.crylo.splitbill.core.Expense
import com.crylo.splitbill.core.GroupSnapshot
import com.crylo.splitbill.core.Member
import com.crylo.splitbill.core.Money
import com.crylo.splitbill.core.SettleUp
import com.crylo.splitbill.core.Settlement
import com.crylo.splitbill.core.Transfer
import com.crylo.splitbill.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ActivityItem {
    val id: String

    data class Spent(val expense: Expense) : ActivityItem {
        override val id get() = expense.id
    }

    data class Paid(val settlement: Settlement) : ActivityItem {
        override val id get() = settlement.id
    }
}

data class GroupUi(
    val snapshot: GroupSnapshot,
    val balances: Map<String, Long>,
    val plan: List<Transfer>,
    val activity: List<ActivityItem>,
    val total: Long,
) {
    val members: Map<String, Member> = snapshot.members.associateBy { it.id }
    val currency: String get() = snapshot.group.currency
    fun name(id: String): String = members[id]?.name ?: "?"
}

sealed interface GroupState {
    data object Loading : GroupState
    data object Gone : GroupState
    data class Ready(val ui: GroupUi) : GroupState
}

/** Editor state for the record/edit payment dialog. */
data class SettlementDraft(
    val existing: Settlement? = null,
    val fromId: String? = null,
    val toId: String? = null,
    val amount: String = "",
    val note: String = "",
    val date: Long = today(),
    val showErrors: Boolean = false,
) {
    val parsedAmount: Long? get() = Money.parse(amount)?.takeIf { it > 0 }
    val valid: Boolean get() = fromId != null && toId != null && fromId != toId && parsedAmount != null
}

class GroupViewModel(private val repo: Repository, private val groupId: String) : ViewModel() {
    val state = repo.snapshot(groupId)
        .map { s -> if (s == null) GroupState.Gone else GroupState.Ready(build(s)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupState.Loading)

    var settlement by mutableStateOf<SettlementDraft?>(null)
        private set

    private fun build(s: GroupSnapshot): GroupUi {
        val balances = Balances.compute(s.members.map { it.id }, s.expenses, s.settlements)
        val activity = (s.expenses.map { ActivityItem.Spent(it) } + s.settlements.map { ActivityItem.Paid(it) })
            .sortedWith(
                compareByDescending<ActivityItem> {
                    when (it) {
                        is ActivityItem.Spent -> it.expense.date
                        is ActivityItem.Paid -> it.settlement.date
                    }
                }.thenByDescending {
                    when (it) {
                        is ActivityItem.Spent -> it.expense.createdAt
                        is ActivityItem.Paid -> it.settlement.createdAt
                    }
                },
            )
        return GroupUi(s, balances, SettleUp.plan(balances), activity, s.expenses.sumOf { it.amount })
    }

    fun startSettlement(transfer: Transfer? = null) {
        settlement = SettlementDraft(
            fromId = transfer?.from,
            toId = transfer?.to,
            amount = transfer?.let { Money.plain(it.amount) }.orEmpty(),
        )
    }

    fun editSettlement(s: Settlement) {
        settlement = SettlementDraft(s, s.fromId, s.toId, Money.plain(s.amount), s.note, s.date)
    }

    fun updateSettlement(draft: SettlementDraft) {
        settlement = draft
    }

    fun dismissSettlement() {
        settlement = null
    }

    fun saveSettlement() {
        val d = settlement ?: return
        if (!d.valid) {
            settlement = d.copy(showErrors = true)
            return
        }
        val now = repo.now()
        val s = d.existing?.copy(
            fromId = d.fromId!!, toId = d.toId!!, amount = d.parsedAmount!!, date = d.date, note = d.note.trim(), updatedAt = now,
        ) ?: Settlement(
            UUID.randomUUID().toString(), groupId, d.fromId!!, d.toId!!, d.parsedAmount!!, d.date, d.note.trim(), now, now,
        )
        settlement = null
        viewModelScope.launch { repo.saveSettlement(s) }
    }

    fun deleteSettlement() {
        val s = settlement?.existing ?: return
        settlement = null
        viewModelScope.launch { repo.deleteSettlement(s) }
    }

    fun deleteGroup() {
        viewModelScope.launch { repo.deleteGroup(groupId) }
    }

    fun exportJson(): String? =
        (state.value as? GroupState.Ready)?.ui?.snapshot?.let { Backup.encode(it, repo.now()) }

    fun exportFileName(): String {
        val name = (state.value as? GroupState.Ready)?.ui?.snapshot?.group?.name ?: "group"
        val safe = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "").trim().ifEmpty { "group" }
        return "$safe.splitbill.json"
    }
}
