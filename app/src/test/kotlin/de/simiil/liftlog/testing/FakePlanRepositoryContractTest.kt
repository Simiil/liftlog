package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.repository.PlanRepository

/** Runs [PlanRepositoryContract] against [FakePlanRepository]. */
class FakePlanRepositoryContractTest : PlanRepositoryContract() {
    override fun createRepository(): PlanRepository = FakePlanRepository()
}
