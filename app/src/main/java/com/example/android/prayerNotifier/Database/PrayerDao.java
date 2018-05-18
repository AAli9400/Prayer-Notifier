package com.example.android.prayerNotifier.Database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

@Dao
public interface PrayerDao {

    @Query("SELECT * FROM PrayerData WHERE date = :date")
    PrayerData loadByDate(String date);

    @Query("SELECT * FROM PrayerData Where pid = :pid ")
    PrayerData loadById(int pid);

    @Insert
    void insert(PrayerData PraysData);

    @Query("DELETE FROM PrayerData")
    void delete();

}
