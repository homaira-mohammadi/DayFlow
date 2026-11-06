package com.dayflow.data.repo

import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.ReflectionEntity
import java.time.LocalDate

class ReflectionRepository(db: DayFlowDatabase, val photos: PhotoStore) {
    private val dao = db.reflectionDao()

    val all = dao.observeAll()

    suspend fun get(date: LocalDate) = dao.get(date.toEpochDay())

    suspend fun save(reflection: ReflectionEntity) = dao.upsert(reflection.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(date: LocalDate) {
        photos.delete(dao.get(date.toEpochDay())?.photoPath)
        dao.delete(date.toEpochDay())
    }
}
