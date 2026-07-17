package de.simiil.liftlog.testing

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import de.simiil.liftlog.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers

// Matches the prod (di/AppModules.kt) and test-override (di/TestOverrideModules.kt) builders: the
// bundled KMP-ready driver instead of framework SQLite, with queries dispatched on Dispatchers.IO.
// allowMainThreadQueries() is dropped along with it — it existed solely to permit tombstoneOf's old
// blocking Cursor read below; that read is now a suspend call via useReaderConnection, and every
// caller (DAO suspend functions, tombstoneOf) already runs inside a `runTest { }` coroutine, so no
// test in this tree performs synchronous main-thread DB access anymore.
fun newInMemoryDb(): AppDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/**
 * Reads (deletedAt, updatedAt) for a row by id directly, bypassing the DAOs' `deletedAt IS NULL`
 * filters — needed to assert cascade soft-deletes actually tombstoned the target rows.
 * Returns null if no row with that id exists. [table] is a trusted test-only table name.
 */
suspend fun AppDatabase.tombstoneOf(
    table: String,
    id: String,
): Pair<Long?, Long>? =
    useReaderConnection { connection ->
        connection.usePrepared("SELECT deletedAt, updatedAt FROM $table WHERE id = ?") { stmt ->
            stmt.bindText(1, id)
            if (!stmt.step()) return@usePrepared null
            val deletedAt = if (stmt.isNull(0)) null else stmt.getLong(0)
            val updatedAt = stmt.getLong(1)
            deletedAt to updatedAt
        }
    }
