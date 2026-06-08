package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.db.Transactor

class FakeTransactor : Transactor {
    override suspend fun <R> immediate(block: suspend () -> R): R = block()
}
