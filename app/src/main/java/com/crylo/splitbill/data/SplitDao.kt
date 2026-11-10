package com.crylo.splitbill.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SplitDao {
    @Query(
        """
        SELECT g.id, g.name, g.currency,
            (SELECT COUNT(*) FROM members m WHERE m.groupId = g.id) AS memberCount,
            (SELECT COALESCE(SUM(e.amount), 0) FROM expenses e WHERE e.groupId = g.id) AS total
        FROM expense_groups g ORDER BY g.touchedAt DESC
        """,
    )
    abstract fun groupRows(): Flow<List<GroupRow>>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    abstract fun group(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY createdAt, id")
    abstract fun members(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY date DESC, createdAt DESC")
    abstract fun expenses(groupId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY date DESC, createdAt DESC")
    abstract fun settlements(groupId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM tombstones WHERE groupId = :groupId")
    abstract fun tombstones(groupId: String): Flow<List<TombstoneEntity>>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    abstract suspend fun groupOnce(id: String): GroupEntity?

    @Upsert abstract suspend fun upsertGroup(group: GroupEntity)
    @Upsert abstract suspend fun upsertMembers(members: List<MemberEntity>)
    @Upsert abstract suspend fun upsertExpense(expense: ExpenseEntity)
    @Upsert abstract suspend fun upsertExpenses(expenses: List<ExpenseEntity>)
    @Upsert abstract suspend fun upsertSettlement(settlement: SettlementEntity)
    @Upsert abstract suspend fun upsertSettlements(settlements: List<SettlementEntity>)
    @Upsert abstract suspend fun upsertTombstones(tombstones: List<TombstoneEntity>)

    @Query("UPDATE expense_groups SET touchedAt = :at WHERE id = :id")
    abstract suspend fun touch(id: String, at: Long)

    @Query("DELETE FROM members WHERE id IN (:ids)")
    abstract suspend fun deleteMembers(ids: List<String>)

    @Query("DELETE FROM expenses WHERE id = :id")
    abstract suspend fun deleteExpense(id: String)

    @Query("DELETE FROM settlements WHERE id = :id")
    abstract suspend fun deleteSettlement(id: String)

    @Query("DELETE FROM tombstones WHERE id IN (:ids)")
    abstract suspend fun deleteTombstones(ids: List<String>)

    @Query("DELETE FROM expense_groups WHERE id = :id") abstract suspend fun deleteGroupRow(id: String)
    @Query("DELETE FROM members WHERE groupId = :id") abstract suspend fun deleteGroupMembers(id: String)
    @Query("DELETE FROM expenses WHERE groupId = :id") abstract suspend fun deleteGroupExpenses(id: String)
    @Query("DELETE FROM settlements WHERE groupId = :id") abstract suspend fun deleteGroupSettlements(id: String)
    @Query("DELETE FROM tombstones WHERE groupId = :id") abstract suspend fun deleteGroupTombstones(id: String)

    @Transaction
    open suspend fun deleteGroup(id: String) {
        deleteGroupMembers(id)
        deleteGroupExpenses(id)
        deleteGroupSettlements(id)
        deleteGroupTombstones(id)
        deleteGroupRow(id)
    }

    /** Swaps a group's contents for [group]'s merged state, atomically. */
    @Transaction
    open suspend fun replaceGroup(
        group: GroupEntity,
        members: List<MemberEntity>,
        expenses: List<ExpenseEntity>,
        settlements: List<SettlementEntity>,
        tombstones: List<TombstoneEntity>,
    ) {
        deleteGroup(group.id)
        upsertGroup(group)
        upsertMembers(members)
        upsertExpenses(expenses)
        upsertSettlements(settlements)
        upsertTombstones(tombstones)
    }

    @Transaction
    open suspend fun saveGroup(group: GroupEntity, members: List<MemberEntity>, removed: List<TombstoneEntity>) {
        upsertGroup(group)
        upsertMembers(members)
        if (removed.isNotEmpty()) {
            deleteMembers(removed.map { it.id })
            upsertTombstones(removed)
        }
    }

    @Transaction
    open suspend fun saveExpense(expense: ExpenseEntity, at: Long) {
        upsertExpense(expense)
        deleteTombstones(listOf(expense.id))
        touch(expense.groupId, at)
    }

    @Transaction
    open suspend fun removeExpense(tombstone: TombstoneEntity) {
        deleteExpense(tombstone.id)
        upsertTombstones(listOf(tombstone))
        touch(tombstone.groupId, tombstone.deletedAt)
    }

    @Transaction
    open suspend fun saveSettlement(settlement: SettlementEntity, at: Long) {
        upsertSettlement(settlement)
        deleteTombstones(listOf(settlement.id))
        touch(settlement.groupId, at)
    }

    @Transaction
    open suspend fun removeSettlement(tombstone: TombstoneEntity) {
        deleteSettlement(tombstone.id)
        upsertTombstones(listOf(tombstone))
        touch(tombstone.groupId, tombstone.deletedAt)
    }
}
