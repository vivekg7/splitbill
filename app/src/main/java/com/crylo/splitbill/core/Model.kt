package com.crylo.splitbill.core

import kotlinx.serialization.Serializable

/*
 * Domain types. They double as the export file format (see Backup), so renaming a field
 * is a file-format change: bump Backup.VERSION and keep reading the old name.
 *
 * Every entity has a random UUID string id and an `updatedAt` wall-clock timestamp. The
 * ids are what make import a merge rather than a copy (the same expense on two phones
 * has the same id); `updatedAt` decides which side's edit wins.
 */

@Serializable
data class Group(
    val id: String,
    val name: String,
    val currency: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    val upiId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
enum class SplitMode { EQUAL, EXACT, SHARES, PERCENT }

@Serializable
enum class Category { GENERAL, FOOD, GROCERIES, TRAVEL, STAY, FUEL, SHOPPING, ENTERTAINMENT, RENT, UTILITIES }

/** How much of an amount belongs to one member: a payer's contribution, for instance. */
@Serializable
data class Portion(val memberId: String, val amount: Long)

/**
 * One member's line in a split. [input] is what the user typed, in the mode's unit
 * (1/0 for EQUAL, minor units for EXACT, hundredths of a share for SHARES, basis points
 * for PERCENT); [share] is the resulting amount owed. Both are stored so editing reopens
 * exactly what was entered and balances never depend on recomputation.
 */
@Serializable
data class SplitEntry(val memberId: String, val input: Long, val share: Long)

@Serializable
data class Expense(
    val id: String,
    val groupId: String,
    val description: String,
    val amount: Long,
    /** Days since 1970-01-01 (LocalDate.toEpochDay); expenses have a date, not a time. */
    val date: Long,
    val category: Category = Category.GENERAL,
    val payers: List<Portion>,
    val splitMode: SplitMode,
    val splits: List<SplitEntry>,
    val createdAt: Long,
    val updatedAt: Long,
)

/** "[fromId] paid [toId] [amount]": money moving between members to settle a debt. */
@Serializable
data class Settlement(
    val id: String,
    val groupId: String,
    val fromId: String,
    val toId: String,
    val amount: Long,
    val date: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Records that an expense, settlement or member was deleted, so that importing an older
 * file on another phone deletes it there too instead of bringing it back.
 */
@Serializable
data class Tombstone(val id: String, val groupId: String, val deletedAt: Long)

/** Everything belonging to one group: the unit of export, import and merge. */
data class GroupSnapshot(
    val group: Group,
    val members: List<Member>,
    val expenses: List<Expense>,
    val settlements: List<Settlement>,
    val tombstones: List<Tombstone> = emptyList(),
)
