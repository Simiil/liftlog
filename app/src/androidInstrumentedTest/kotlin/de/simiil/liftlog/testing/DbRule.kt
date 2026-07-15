package de.simiil.liftlog.testing

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import de.simiil.liftlog.data.db.AppDatabase

fun newInMemoryDb(): AppDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/**
 * Reads (deletedAt, updatedAt) for a row by id directly, bypassing the DAOs' `deletedAt IS NULL`
 * filters — needed to assert cascade soft-deletes actually tombstoned the target rows.
 * Returns null if no row with that id exists. [table] is a trusted test-only table name.
 */
fun AppDatabase.tombstoneOf(
    table: String,
    id: String,
): Pair<Long?, Long>? {
    query(SimpleSQLiteQuery("SELECT deletedAt, updatedAt FROM $table WHERE id = ?", arrayOf(id))).use { c ->
        if (!c.moveToFirst()) return null
        val deletedAt = if (c.isNull(0)) null else c.getLong(0)
        val updatedAt = c.getLong(1)
        return deletedAt to updatedAt
    }
}
