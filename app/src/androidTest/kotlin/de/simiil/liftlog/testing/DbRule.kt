package de.simiil.liftlog.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.simiil.liftlog.data.db.AppDatabase

fun newInMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
