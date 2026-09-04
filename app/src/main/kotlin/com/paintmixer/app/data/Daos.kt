package com.paintmixer.app.data

import androidx.room.Dao

// Empty DAOs for Phase 0 -- see PLAN.md "Phase 0 -- Skeleton". Queries and
// inserts are added as each screen needs them (Phase 3 for Palette /
// PaletteColor, Phase 4 for TargetShot).

@Dao
interface PaletteDao

@Dao
interface PaletteColorDao

@Dao
interface TargetShotDao
