package com.crylo.splitbill.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, MemberEntity::class, ExpenseEntity::class, SettlementEntity::class, TombstoneEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SplitDatabase : RoomDatabase() {
    abstract fun dao(): SplitDao

    companion object {
        fun create(context: Context): SplitDatabase =
            Room.databaseBuilder(context, SplitDatabase::class.java, "splitbill.db").build()
    }
}
