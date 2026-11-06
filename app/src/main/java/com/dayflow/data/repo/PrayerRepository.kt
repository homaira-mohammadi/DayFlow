package com.dayflow.data.repo

import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.PrayerLogEntity
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class PrayerRepository(db: DayFlowDatabase) {
    private val dao = db.prayerDao()

    /** Every recorded prayer, grouped by day. */
    val logs: Flow<Map<LocalDate, Map<Prayer, PrayerStatus>>> = dao.observeAll().map { list ->
        list.groupBy { LocalDate.ofEpochDay(it.date) }.mapValues { (_, day) -> day.associate { it.prayer to it.status } }
    }

    /** Records how [prayer] was kept on [date]; null clears the record. */
    suspend fun set(date: LocalDate, prayer: Prayer, status: PrayerStatus?) {
        if (status == null) dao.delete(date.toEpochDay(), prayer)
        else dao.upsert(PrayerLogEntity(date.toEpochDay(), prayer, status))
    }
}
