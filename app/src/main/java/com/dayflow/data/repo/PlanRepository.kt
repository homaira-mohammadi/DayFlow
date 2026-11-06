package com.dayflow.data.repo

import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.domain.History
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * Exposes the whole local history as one stream. A personal tracker holds a few thousand rows
 * at most, so every screen derives its numbers from the same snapshot and they always agree.
 */
class PlanRepository(db: DayFlowDatabase) {
    val history: Flow<History> = combine(
        db.habitDao().observeAll(),
        db.taskDao().observeAll(),
        db.habitDao().observeLogs(),
        db.restDayDao().observeAll(),
        db.taskDao().observeReschedules(),
    ) { habits, tasks, logs, rest, reschedules ->
        History(habits, tasks, logs, rest.map { it.date }.toSet(), reschedules)
    }.flowOn(Dispatchers.Default)
}
