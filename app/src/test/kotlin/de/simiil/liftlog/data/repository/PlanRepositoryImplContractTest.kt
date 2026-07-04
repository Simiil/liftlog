package de.simiil.liftlog.data.repository

import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.testing.InMemoryPreferencesDataStore
import de.simiil.liftlog.testing.PlanRepositoryContract
import de.simiil.liftlog.testing.fakes.FakePlanDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Runs [PlanRepositoryContract] against the real [PlanRepositoryImpl] (backed by fakes for the DAO, transactor, and DataStore). */
class PlanRepositoryImplContractTest : PlanRepositoryContract() {
    private val clock = Clock.fixed(Instant.ofEpochMilli(5000L), ZoneOffset.UTC)

    override fun createRepository(): PlanRepository =
        PlanRepositoryImpl(FakePlanDao(), FakeTransactor(), clock, InMemoryPreferencesDataStore())
}
