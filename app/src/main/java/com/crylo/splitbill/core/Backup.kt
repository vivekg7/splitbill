package com.crylo.splitbill.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION, CORRUPT }
}

@Serializable
private data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val group: Group,
    val members: List<Member> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    @SerialName("deleted") val tombstones: List<Tombstone> = emptyList(),
)

/**
 * Group export/import: the v1 stand-in for sync. One group per `.json` file, moved
 * between phones by whatever the user already has (WhatsApp, Bluetooth, a USB cable).
 * Importing merges by stable ids, so re-importing an updated file updates the group in
 * place instead of duplicating it.
 */
object Backup {
    const val FORMAT = "splitbill-group"
    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // a newer minor revision may add fields we can skip
        encodeDefaults = true
    }

    fun encode(snapshot: GroupSnapshot, exportedAt: Long): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            format = FORMAT,
            version = VERSION,
            exportedAt = exportedAt,
            group = snapshot.group,
            members = snapshot.members,
            expenses = snapshot.expenses,
            settlements = snapshot.settlements,
            tombstones = snapshot.tombstones,
        ),
    )

    /** Parses and validates a file. Throws [BackupException] rather than importing junk. */
    fun decode(text: String): GroupSnapshot {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        }
        if (file.format != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (file.version > VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        val snapshot = GroupSnapshot(file.group, file.members, file.expenses, file.settlements, file.tombstones)
        if (!isConsistent(snapshot)) throw BackupException(BackupException.Reason.CORRUPT)
        return snapshot
    }

    /**
     * Checks the invariants the rest of the app relies on: everything belongs to the
     * group, every referenced member exists, and every expense's payers and shares add
     * up to its amount (otherwise balances would stop summing to zero).
     */
    fun isConsistent(s: GroupSnapshot): Boolean {
        val gid = s.group.id
        if (gid.isBlank() || s.group.name.isBlank()) return false
        val memberIds = s.members.map { it.id }.toSet()
        if (memberIds.size != s.members.size || s.members.any { it.groupId != gid || it.id.isBlank() }) return false
        if (s.expenses.map { it.id }.toSet().size != s.expenses.size) return false
        if (s.settlements.map { it.id }.toSet().size != s.settlements.size) return false
        val expensesOk = s.expenses.all { e ->
            e.groupId == gid && e.amount in 1..Money.MAX_AMOUNT &&
                e.payers.isNotEmpty() && e.payers.all { it.memberId in memberIds && it.amount >= 0 } &&
                e.payers.sumOf { it.amount } == e.amount &&
                e.splits.isNotEmpty() && e.splits.all { it.memberId in memberIds && it.share >= 0 } &&
                e.splits.sumOf { it.share } == e.amount
        }
        val settlementsOk = s.settlements.all {
            it.groupId == gid && it.amount in 1..Money.MAX_AMOUNT &&
                it.fromId != it.toId && it.fromId in memberIds && it.toId in memberIds
        }
        return expensesOk && settlementsOk && s.tombstones.all { it.groupId == gid }
    }

    /**
     * Merges an imported group into the local copy (null when the group is new here).
     *
     * - Entities match by id. When both sides have one, the later `updatedAt` wins; on a
     *   tie the local copy is kept, so importing the same file twice changes nothing.
     * - Deletions travel as tombstones. An entity is dropped when a tombstone for it is
     *   at least as new as its last edit, so a deletion beats older edits and a newer
     *   edit (made on another phone after the delete) brings the entity back.
     * - A member is never dropped while a surviving expense or settlement refers to them.
     */
    fun merge(local: GroupSnapshot?, incoming: GroupSnapshot): GroupSnapshot {
        if (local == null) return incoming
        require(local.group.id == incoming.group.id) { "different groups" }

        val tombstones = (local.tombstones + incoming.tombstones)
            .groupBy { it.id }
            .map { (_, list) -> list.maxBy { it.deletedAt } }
        val deletedAt = tombstones.associate { it.id to it.deletedAt }

        fun <T> pick(a: List<T>, b: List<T>, id: (T) -> String, updated: (T) -> Long): List<T> {
            val result = LinkedHashMap<String, T>()
            a.forEach { result[id(it)] = it }
            b.forEach { other ->
                val mine = result[id(other)]
                if (mine == null || updated(other) > updated(mine)) result[id(other)] = other
            }
            return result.values.filter { (deletedAt[id(it)] ?: Long.MIN_VALUE) < updated(it) }
        }

        val group = if (incoming.group.updatedAt > local.group.updatedAt) {
            incoming.group.copy(createdAt = minOf(local.group.createdAt, incoming.group.createdAt))
        } else {
            local.group
        }
        val expenses = pick(local.expenses, incoming.expenses, { it.id }, { it.updatedAt })
        val settlements = pick(local.settlements, incoming.settlements, { it.id }, { it.updatedAt })

        val allMembers = LinkedHashMap<String, Member>()
        (local.members + incoming.members).forEach { m ->
            val mine = allMembers[m.id]
            if (mine == null || m.updatedAt > mine.updatedAt) allMembers[m.id] = m
        }
        val referenced = buildSet {
            expenses.forEach { e -> e.payers.forEach { add(it.memberId) }; e.splits.forEach { add(it.memberId) } }
            settlements.forEach { add(it.fromId); add(it.toId) }
        }
        val members = allMembers.values
            .filter { it.id in referenced || (deletedAt[it.id] ?: Long.MIN_VALUE) < it.updatedAt }
            .sortedWith(compareBy<Member> { it.createdAt }.thenBy { it.id })
        val survivors = (members.map { it.id } + expenses.map { it.id } + settlements.map { it.id }).toSet()

        return GroupSnapshot(
            group = group,
            members = members,
            expenses = expenses,
            settlements = settlements,
            tombstones = tombstones.filter { it.id !in survivors },
        )
    }
}
