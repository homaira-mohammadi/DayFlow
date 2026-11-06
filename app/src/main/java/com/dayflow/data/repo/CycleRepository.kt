package com.dayflow.data.repo

import androidx.room.withTransaction
import com.dayflow.data.db.CycleSymptomEntity
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.PeriodDayEntity
import com.dayflow.domain.PeriodFlow
import com.dayflow.domain.Symptom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class SymptomDay(val date: LocalDate, val symptoms: Set<Symptom>, val note: String)

class CycleRepository(private val db: DayFlowDatabase) {
    private val dao = db.cycleDao()

    val periodDays: Flow<Map<LocalDate, PeriodFlow>> =
        dao.observePeriodDays().map { list -> list.associate { LocalDate.ofEpochDay(it.date) to it.flow } }

    val symptoms: Flow<Map<LocalDate, SymptomDay>> = dao.observeSymptoms().map { list ->
        list.associate { e ->
            val date = LocalDate.ofEpochDay(e.date)
            date to SymptomDay(date, parse(e.symptoms), e.note)
        }
    }

    suspend fun periodDaysNow(): List<LocalDate> = dao.periodDays().map { LocalDate.ofEpochDay(it.date) }

    suspend fun setPeriodDay(date: LocalDate, flow: PeriodFlow?) {
        if (flow == null) dao.deletePeriodDay(date.toEpochDay())
        else dao.upsertPeriodDays(listOf(PeriodDayEntity(date.toEpochDay(), flow)))
    }

    /** Logs a whole period at once, e.g. "my last period started on the 3rd and lasted 5 days". */
    suspend fun logPeriod(start: LocalDate, days: Int) = db.withTransaction {
        dao.upsertPeriodDays((0 until days).map { PeriodDayEntity(start.plusDays(it.toLong()).toEpochDay()) })
    }

    suspend fun setSymptoms(date: LocalDate, symptoms: Set<Symptom>, note: String) {
        if (symptoms.isEmpty() && note.isBlank()) dao.deleteSymptoms(date.toEpochDay())
        else dao.upsertSymptoms(CycleSymptomEntity(date.toEpochDay(), symptoms.joinToString(",") { it.name }, note.trim()))
    }

    /** Removes every period and symptom record; other app data is untouched. */
    suspend fun deleteAll() = db.withTransaction {
        dao.deleteAllPeriodDays()
        dao.deleteAllSymptoms()
    }

    private fun parse(csv: String): Set<Symptom> =
        csv.split(',').mapNotNull { name -> Symptom.entries.firstOrNull { it.name == name } }.toSet()
}
