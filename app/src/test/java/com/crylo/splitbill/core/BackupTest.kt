package com.crylo.splitbill.core

import com.crylo.splitbill.core.TestData.GID
import com.crylo.splitbill.core.TestData.expense
import com.crylo.splitbill.core.TestData.group
import com.crylo.splitbill.core.TestData.member
import com.crylo.splitbill.core.TestData.settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    private val base = GroupSnapshot(
        group = group(),
        members = listOf(member("a", upi = "a@ybl"), member("b"), member("c")),
        expenses = listOf(expense("e1", 900, "a", listOf("a", "b", "c"))),
        settlements = listOf(settlement("s1", "b", "a", 300)),
    )

    private fun roundTrip(s: GroupSnapshot) = Backup.decode(Backup.encode(s, exportedAt = 99))

    private fun assertRejected(text: String, reason: BackupException.Reason) {
        try {
            Backup.decode(text)
            fail("expected $reason")
        } catch (e: BackupException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test fun roundTripKeepsEverything() {
        assertEquals(base, roundTrip(base))
    }

    @Test fun importIntoEmptyPhoneIsTheFile() {
        assertEquals(base, Backup.merge(null, roundTrip(base)))
    }

    @Test fun reimportingTheSameFileChangesNothing() {
        val once = Backup.merge(base, roundTrip(base))
        assertEquals(base, once)
        assertEquals(once, Backup.merge(once, roundTrip(base)))
    }

    @Test fun reimportingAnUpdatedFileUpdatesInsteadOfDuplicating() {
        val edited = base.copy(
            expenses = listOf(base.expenses[0].copy(description = "Dinner + drinks", updatedAt = 5)),
            settlements = base.settlements + settlement("s2", "c", "a", 300, updatedAt = 5),
        )
        val merged = Backup.merge(base, roundTrip(edited))
        assertEquals(1, merged.expenses.size)
        assertEquals("Dinner + drinks", merged.expenses[0].description)
        assertEquals(listOf("s1", "s2"), merged.settlements.map { it.id })
    }

    @Test fun newerLocalEditIsNotOverwrittenByOlderFile() {
        val local = base.copy(expenses = listOf(base.expenses[0].copy(description = "Local edit", updatedAt = 10)))
        val merged = Backup.merge(local, roundTrip(base))
        assertEquals("Local edit", merged.expenses[0].description)
    }

    @Test fun bothSidesAddingDifferentEntriesKeepsBoth() {
        val mine = base.copy(expenses = base.expenses + expense("e2", 100, "b", listOf("a", "b"), updatedAt = 3))
        val theirs = base.copy(expenses = base.expenses + expense("e3", 200, "c", listOf("b", "c"), updatedAt = 4))
        assertEquals(listOf("e1", "e2", "e3"), Backup.merge(mine, roundTrip(theirs)).expenses.map { it.id })
    }

    @Test fun deletionTravelsWithTheFile() {
        val theirs = base.copy(expenses = emptyList(), tombstones = listOf(Tombstone("e1", GID, deletedAt = 5)))
        val merged = Backup.merge(base, roundTrip(theirs))
        assertTrue(merged.expenses.isEmpty())
        assertEquals(listOf("e1"), merged.tombstones.map { it.id })
        // ...and an old file that still has the expense does not bring it back.
        assertTrue(Backup.merge(merged, roundTrip(base)).expenses.isEmpty())
    }

    @Test fun editMadeAfterTheDeletionWins() {
        val local = base.copy(expenses = listOf(base.expenses[0].copy(updatedAt = 9)))
        val theirs = base.copy(expenses = emptyList(), tombstones = listOf(Tombstone("e1", GID, deletedAt = 5)))
        val merged = Backup.merge(local, roundTrip(theirs))
        assertEquals(listOf("e1"), merged.expenses.map { it.id })
        assertTrue(merged.tombstones.isEmpty())
    }

    @Test fun deletedMemberStillReferencedIsKept() {
        val local = base.copy(tombstones = listOf(Tombstone("c", GID, deletedAt = 5)), members = base.members.take(2))
        val merged = Backup.merge(local, roundTrip(base))
        assertEquals(listOf("a", "b", "c"), merged.members.map { it.id })
    }

    @Test fun newerGroupNameWins() {
        val theirs = base.copy(group = group(updatedAt = 7, name = "Goa 2026"))
        assertEquals("Goa 2026", Backup.merge(base, roundTrip(theirs)).group.name)
    }

    @Test fun mergedBalancesStillNetToZero() {
        val theirs = base.copy(expenses = base.expenses + expense("e9", 1_001, "c", listOf("a", "b", "c"), updatedAt = 3))
        val merged = Backup.merge(base, roundTrip(theirs))
        val net = Balances.compute(merged.members.map { it.id }, merged.expenses, merged.settlements)
        assertEquals(0L, net.values.sum())
    }

    @Test fun rejectsFilesThatAreNotBackups() {
        assertRejected("not json", BackupException.Reason.NOT_A_BACKUP)
        assertRejected("""{"hello": 1}""", BackupException.Reason.NOT_A_BACKUP)
        val other = Backup.encode(base, 1).replace(Backup.FORMAT, "something-else")
        assertRejected(other, BackupException.Reason.NOT_A_BACKUP)
    }

    @Test fun rejectsNewerFormatVersion() {
        val newer = Backup.encode(base, 1).replace("\"version\": 1", "\"version\": 2")
        assertRejected(newer, BackupException.Reason.NEWER_VERSION)
    }

    @Test fun rejectsInconsistentData() {
        val badSum = base.copy(expenses = listOf(base.expenses[0].copy(amount = 1_000)))
        assertRejected(Backup.encode(badSum, 1), BackupException.Reason.CORRUPT)
        val unknownMember = base.copy(settlements = listOf(settlement("s1", "b", "zz", 300)))
        assertRejected(Backup.encode(unknownMember, 1), BackupException.Reason.CORRUPT)
        assertFalse(Backup.isConsistent(base.copy(settlements = listOf(settlement("s1", "a", "a", 300)))))
    }

    @Test fun ignoresUnknownFieldsFromLaterMinorRevisions() {
        val withExtra = Backup.encode(base, 1).replaceFirst("{", "{\n  \"futureField\": true,")
        assertEquals(base, Backup.decode(withExtra))
    }
}
