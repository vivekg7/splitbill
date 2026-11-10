package com.crylo.splitbill.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Room rows mirror the domain types in core/ one to one. There are deliberately no
// foreign keys: an import replaces a whole group in one transaction, and the merge in
// Backup already guarantees every reference resolves.

@Entity(tableName = "expense_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Local-only: last time anything in the group changed, for list ordering. */
    val touchedAt: Long,
)

@Entity(tableName = "members", indices = [Index("groupId")])
data class MemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val upiId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "expenses", indices = [Index("groupId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val description: String,
    val amount: Long,
    val date: Long,
    val category: String,
    /** JSON list of Portion; always read and written whole with the expense. */
    val payers: String,
    val splitMode: String,
    /** JSON list of SplitEntry. */
    val splits: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "settlements", indices = [Index("groupId")])
data class SettlementEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val fromId: String,
    val toId: String,
    val amount: Long,
    val date: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "tombstones", indices = [Index("groupId")])
data class TombstoneEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val deletedAt: Long,
)

/** One row of the group list. */
data class GroupRow(
    val id: String,
    val name: String,
    val currency: String,
    val memberCount: Int,
    val total: Long,
)
