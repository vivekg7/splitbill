package com.crylo.splitbill.data

import com.crylo.splitbill.core.Backup
import com.crylo.splitbill.core.Category
import com.crylo.splitbill.core.Expense
import com.crylo.splitbill.core.Group
import com.crylo.splitbill.core.GroupSnapshot
import com.crylo.splitbill.core.Member
import com.crylo.splitbill.core.Portion
import com.crylo.splitbill.core.Settlement
import com.crylo.splitbill.core.SplitEntry
import com.crylo.splitbill.core.SplitMode
import com.crylo.splitbill.core.Tombstone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class Repository(private val dao: SplitDao, private val clock: () -> Long = System::currentTimeMillis) {
    enum class ImportResult { CREATED, UPDATED }

    fun now(): Long = clock()

    fun groupRows(): Flow<List<GroupRow>> = dao.groupRows()

    fun snapshot(groupId: String): Flow<GroupSnapshot?> = combine(
        dao.group(groupId),
        dao.members(groupId),
        dao.expenses(groupId),
        dao.settlements(groupId),
        dao.tombstones(groupId),
    ) { g, members, expenses, settlements, tombstones ->
        g?.let {
            GroupSnapshot(
                group = it.toDomain(),
                members = members.map { m -> m.toDomain() },
                expenses = expenses.map { e -> e.toDomain() },
                settlements = settlements.map { s -> s.toDomain() },
                tombstones = tombstones.map { t -> Tombstone(t.id, t.groupId, t.deletedAt) },
            )
        }
    }

    suspend fun snapshotOnce(groupId: String): GroupSnapshot? = snapshot(groupId).first()

    suspend fun saveGroup(group: Group, members: List<Member>, removedMemberIds: List<String>) {
        val at = now()
        dao.saveGroup(
            group.toEntity(touchedAt = at),
            members.map { it.toEntity() },
            removedMemberIds.map { TombstoneEntity(it, group.id, at) },
        )
    }

    suspend fun deleteGroup(groupId: String) = dao.deleteGroup(groupId)

    suspend fun saveExpense(expense: Expense) = dao.saveExpense(expense.toEntity(), now())

    suspend fun deleteExpense(expense: Expense) =
        dao.removeExpense(TombstoneEntity(expense.id, expense.groupId, now()))

    suspend fun saveSettlement(settlement: Settlement) = dao.saveSettlement(settlement.toEntity(), now())

    suspend fun deleteSettlement(settlement: Settlement) =
        dao.removeSettlement(TombstoneEntity(settlement.id, settlement.groupId, now()))

    /** Merges an imported group into whatever this phone already has under its id. */
    suspend fun import(incoming: GroupSnapshot): ImportResult {
        val local = snapshotOnce(incoming.group.id)
        val merged = Backup.merge(local, incoming)
        dao.replaceGroup(
            merged.group.toEntity(touchedAt = now()),
            merged.members.map { it.toEntity() },
            merged.expenses.map { it.toEntity() },
            merged.settlements.map { it.toEntity() },
            merged.tombstones.map { TombstoneEntity(it.id, it.groupId, it.deletedAt) },
        )
        return if (local == null) ImportResult.CREATED else ImportResult.UPDATED
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val portions = ListSerializer(Portion.serializer())
        val entries = ListSerializer(SplitEntry.serializer())

        fun GroupEntity.toDomain() = Group(id, name, currency, createdAt, updatedAt)
        fun Group.toEntity(touchedAt: Long) = GroupEntity(id, name, currency, createdAt, updatedAt, touchedAt)
        fun MemberEntity.toDomain() = Member(id, groupId, name, upiId, createdAt, updatedAt)
        fun Member.toEntity() = MemberEntity(id, groupId, name, upiId, createdAt, updatedAt)

        fun ExpenseEntity.toDomain() = Expense(
            id = id,
            groupId = groupId,
            description = description,
            amount = amount,
            date = date,
            category = Category.entries.firstOrNull { it.name == category } ?: Category.GENERAL,
            payers = json.decodeFromString(portions, payers),
            splitMode = SplitMode.valueOf(splitMode),
            splits = json.decodeFromString(entries, splits),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        fun Expense.toEntity() = ExpenseEntity(
            id, groupId, description, amount, date, category.name,
            json.encodeToString(portions, payers), splitMode.name, json.encodeToString(entries, splits),
            createdAt, updatedAt,
        )

        fun SettlementEntity.toDomain() = Settlement(id, groupId, fromId, toId, amount, date, note, createdAt, updatedAt)
        fun Settlement.toEntity() = SettlementEntity(id, groupId, fromId, toId, amount, date, note, createdAt, updatedAt)
    }
}
