package de.simiil.liftlog.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/** Atomic multi-DAO unit of work. Fakes run [block] inline so cascade logic is JVM-testable. */
interface Transactor {
    suspend fun <R> immediate(block: suspend () -> R): R
}

class RoomTransactor(
    private val db: AppDatabase,
) : Transactor {
    override suspend fun <R> immediate(block: suspend () -> R): R =
        db.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
}
