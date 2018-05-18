package com.example.android.prayerNotifier.Database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;


@Database(entities = {PrayerData.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PrayerDao prayDao();
}
