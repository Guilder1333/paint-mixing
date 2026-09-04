package com.paintmixer.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Palette::class, PaletteColor::class, TargetShot::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paletteDao(): PaletteDao
    abstract fun paletteColorDao(): PaletteColorDao
    abstract fun targetShotDao(): TargetShotDao

    companion object {
        const val NAME = "paint-mixer.db"
    }
}
