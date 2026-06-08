package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.data.mapper.toDomain
import de.simiil.liftlog.domain.model.WorkoutPlan
import de.simiil.liftlog.domain.repository.PlanRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val dao: PlanDao,
    private val transactor: Transactor,
    private val clock: Clock,
) : PlanRepository {
    override fun observePlans() = dao.observePlans().map { it.map(WorkoutPlanEntity::toDomain) }

    override suspend fun createPlan(name: String): WorkoutPlan {
        val now = clock.millis()
        val entity = WorkoutPlanEntity(UUID.randomUUID().toString(), name.trim(), position = 0,
            createdAt = now, updatedAt = now, deletedAt = null)
        dao.insertPlan(entity)
        return entity.toDomain()
    }

    override suspend fun softDeletePlan(id: String) = transactor.immediate {
        val now = clock.millis()
        dao.softDeleteTemplateExercisesForPlan(id, now)
        dao.softDeleteDayTemplatesForPlan(id, now)
        dao.softDeletePlan(id, now)
    }
}
