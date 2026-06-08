package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.SessionDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.mapper.toDomain
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.domain.repository.SessionRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val transactor: Transactor,
    private val clock: Clock,
) : SessionRepository {
    override fun observeActiveSession() = dao.observeActiveSession().map { it?.toDomain() }
    override fun observeHistory() = dao.observeHistory().map { it.map(SessionEntity::toDomain) }
    override fun observeSessionDetails(id: String) = dao.observeSessionWithDetails(id).map { it?.toDomain() }

    override suspend fun startEmptySession(): Session = transactor.immediate {
        check(dao.activeSessionId() == null) { "A session is already in progress" }
        val now = clock.millis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(), templateId = null, templateNameSnapshot = null,
            startedAt = now, endedAt = null, note = null,
            createdAt = now, updatedAt = now, deletedAt = null,
        )
        dao.insertSession(session)
        session.toDomain()
    }

    override suspend fun finishSession(id: String) {
        val current = dao.findSession(id) ?: return
        val now = clock.millis()
        dao.updateSession(current.copy(endedAt = now, updatedAt = now))
    }

    override suspend fun softDeleteSession(id: String) = transactor.immediate {
        val now = clock.millis()
        dao.softDeleteLoggedSetsForSession(id, now)
        dao.softDeleteSessionExercisesFor(id, now)
        dao.softDeleteSession(id, now)
    }
}
