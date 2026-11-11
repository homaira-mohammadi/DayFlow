package com.dayflow.data.repo

import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.SleepLogEntity
import com.dayflow.domain.SleepNight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class SleepRepository(db: DayFlowDatabase) {
    private val dao = db.sleepDao()

    /** Every logged night, keyed by the morning of waking up. */
    val nights: Flow<Map<LocalDate, SleepNight>> = dao.observeAll().map { list ->
        list.associate { LocalDate.ofEpochDay(it.date) to SleepNight(LocalDate.ofEpochDay(it.date), it.bedMinutes, it.wakeMinutes) }
    }

    suspend fun latest(): SleepNight? = dao.latest()?.let { SleepNight(LocalDate.ofEpochDay(it.date), it.bedMinutes, it.wakeMinutes) }

    suspend fun save(date: LocalDate, bedMinutes: Int, wakeMinutes: Int) =
        dao.upsert(SleepLogEntity(date.toEpochDay(), bedMinutes, wakeMinutes))

    suspend fun delete(date: LocalDate) = dao.delete(date.toEpochDay())
}
