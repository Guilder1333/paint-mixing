package com.paintmixer.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// PaletteColorDao and TargetShotDao stay empty until Phase 3/4 give them
// something to do -- see PLAN.md "Phase 0 -- Skeleton". PaletteDao got its
// first real methods in Phase 2, which needs a palette's CaptureSettings to
// survive a restart so they can be replayed (PLAN.md section 4.1/4.2's
// repeatability test).

@Dao
interface PaletteDao {
    @Insert
    suspend fun insert(palette: Palette)

    @Query("SELECT * FROM Palette ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Palette>>

    @Query("SELECT * FROM Palette ORDER BY createdAt DESC LIMIT 1")
    suspend fun mostRecent(): Palette?

    @Query("SELECT * FROM Palette WHERE id = :id")
    suspend fun getById(id: String): Palette?
}

@Dao
interface PaletteColorDao

@Dao
interface TargetShotDao
