package com.paintmixer.app.di

import android.content.Context
import androidx.room.Room
import com.paintmixer.app.data.AppDatabase

/**
 * Manual DI: the app is too small to justify Hilt's build overhead
 * (see PLAN.md section 1). One instance is built in [com.paintmixer.app.PaintMixerApp]
 * and handed to composables that need it.
 */
class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.NAME
    ).build()
}
