package com.example.android.prayerNotifier.Database;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public class PrayerData {
    @PrimaryKey
    private int pid;

    @ColumnInfo(name = "fajr")
    private String fajr;

    @ColumnInfo(name = "dhuhr")
    private String dhuhr;

    @ColumnInfo(name = "asr")
    private String asr;

    @ColumnInfo(name = "mghrib")
    private String mghrib;

    @ColumnInfo(name = "isha")
    private String isha;

    @ColumnInfo(name = "hijri_date")
    private String hijriDate;

    @ColumnInfo(name = "date")
    private String date;

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public String getFajr() {
        return fajr;
    }

    public void setFajr(String fajr) {
        this.fajr = fajr;
    }

    public String getDhuhr() {
        return dhuhr;
    }

    public void setDhuhr(String dhuhr) {
        this.dhuhr = dhuhr;
    }

    public String getAsr() {
        return asr;
    }

    public void setAsr(String asr) {
        this.asr = asr;
    }

    public String getMghrib() {
        return mghrib;
    }

    public void setMghrib(String mghrib) {
        this.mghrib = mghrib;
    }

    public String getIsha() {
        return isha;
    }

    public void setIsha(String isha) {
        this.isha = isha;
    }

    public String getHijriDate() {
        return hijriDate;
    }

    public void setHijriDate(String hijriDate) {
        this.hijriDate = hijriDate;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}