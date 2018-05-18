package com.example.android.prayerNotifier;

import android.annotation.SuppressLint;
import android.arch.persistence.room.Room;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.android.prayerNotifier.Database.AppDatabase;
import com.example.android.prayerNotifier.Database.PrayerData;
import com.example.android.prayerNotifier.Service.NotificationService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView mHijriDateTextView;

    private TextView mFajrTimeTextView;
    private TextView mDhurTimeTextView;
    private TextView mAsrTimeTextView;
    private TextView mMghribTimeTextView;
    private TextView mIshaTimeTextView;

    private ProgressBar mProgressBar;

    private AppDatabase mDatabase;
    private DatabaseAsyncTask mAsyncTask;
    private AsyncTask mVolleyListenerAsyncTask;

    private String mCurrentDate;
    private PrayerData mPrayTodayData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);

        mHijriDateTextView = findViewById(R.id.hijri_date);

        mFajrTimeTextView = findViewById(R.id.fajr_time);
        mDhurTimeTextView = findViewById(R.id.dhur_time);
        mAsrTimeTextView = findViewById(R.id.asr_time);
        mMghribTimeTextView = findViewById(R.id.mghrib_time);
        mIshaTimeTextView = findViewById(R.id.isha_time);

        mProgressBar = findViewById(R.id.progressBar);

        mDatabase = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "prayer_notifier").build();

        mAsyncTask = new DatabaseAsyncTask();
        mAsyncTask.execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAsyncTask.getStatus() == AsyncTask.Status.RUNNING || mAsyncTask.getStatus() == AsyncTask.Status.PENDING) {
            mAsyncTask.cancel(true);
        }

        if (mVolleyListenerAsyncTask != null) {
            if (mVolleyListenerAsyncTask.getStatus() == AsyncTask.Status.RUNNING || mVolleyListenerAsyncTask.getStatus() == AsyncTask.Status.PENDING) {
                mVolleyListenerAsyncTask.cancel(true);
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class DatabaseAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            Calendar calendar = Calendar.getInstance();
            String currentMonth = String.valueOf(calendar.get(Calendar.MONTH) + 1);
            if (currentMonth.charAt(0) != '1') {
                currentMonth = "0" + currentMonth;
            }
            mCurrentDate = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)) + "-" +
                    currentMonth + "-" +
                    String.valueOf(calendar.get(Calendar.YEAR));

            mPrayTodayData = mDatabase.prayDao().loadByDate(mCurrentDate);

            if (mPrayTodayData == null) {
                updateFromTheInternet();
                return false;
            } else {
                mPrayTodayData = mDatabase.prayDao().loadByDate(mCurrentDate);
                return true;
            }
        }

        @Override
        protected void onPostExecute(Boolean updateLocally) {
            if (updateLocally) {
                updateLocally();
            }
        }
    }

    private void updateFromTheInternet() {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://api.aladhan.com/v1/calendarByCity?city=Cairo&country=Egypt&method=5";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @SuppressLint("StaticFieldLeak")
            @Override
            public void onResponse(final String response) {
                mVolleyListenerAsyncTask = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        parseJsonResponseToTheDataBase(response);//add the new month data

                        mPrayTodayData = mDatabase.prayDao().loadByDate(mCurrentDate);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        updateLocally();

                        Intent intent = new Intent(MainActivity.this, NotificationService.class);
                        intent.setAction("scheduling");
                        intent.putExtra("i", 0);
                        startService(intent);
                    }
                }.execute();
            }
        }, new Response.ErrorListener() {
            @SuppressLint("StaticFieldLeak")
            @Override
            public void onErrorResponse(VolleyError error) {
                mVolleyListenerAsyncTask = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        int i = 31;
                        do {
                            mPrayTodayData = mDatabase.prayDao().loadById(i--);
                        }
                        while (mPrayTodayData == null);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        updateLocally();

                        Toast.makeText(MainActivity.this, "Error updating prayer timing", Toast.LENGTH_LONG).show();
                    }
                }.execute();
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    private void parseJsonResponseToTheDataBase(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);

            if (jsonObject.getInt("code") == 200) {
                mDatabase.prayDao().delete();//delete the old month data

                JSONArray jsonArray = jsonObject.getJSONArray("data");

                for (int i = 0; i < jsonArray.length(); ++i) {
                    JSONObject object = jsonArray.getJSONObject(i);
                    PrayerData data = new PrayerData();
                    data.setPid(i);

                    JSONObject date = object.getJSONObject("date");
                    JSONObject gregorian = date.getJSONObject("gregorian");
                    String dateString = gregorian.getString("date");
                    if (dateString.charAt(0) == '0') {
                        dateString = dateString.substring(1, dateString.length());
                    }
                    data.setDate(dateString);

                    JSONObject hijri = date.getJSONObject("hijri");
                    String hijriDate = hijri.getString("day") + ", ";
                    JSONObject hijri_month = hijri.getJSONObject("month");
                    hijriDate += hijri_month.getString("en") + " " + hijri.getString("year");
                    data.setHijriDate(hijriDate);

                    JSONObject timings = object.getJSONObject("timings");
                    data.setFajr(timings.getString("Fajr").substring(0, 5));
                    data.setDhur(timings.getString("Dhuhr").substring(0, 5));
                    data.setAsr(timings.getString("Asr").substring(0, 5));
                    data.setMghrib(timings.getString("Maghrib").substring(0, 5));
                    data.setIsha(timings.getString("Isha").substring(0, 5));

                    try {
                        mDatabase.prayDao().insert(data);
                    } catch (Exception e) {
                        Log.v(TAG, e.getMessage());
                    }
                }
            } else {
                Toast.makeText(MainActivity.this, "Error Connecting to the server", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            Log.v(TAG, e.getMessage());
        }
    }

    private void updateLocally() {
        if (mPrayTodayData != null) {
            mHijriDateTextView.setText(mPrayTodayData.getHijriDate());

            mFajrTimeTextView.setText(mPrayTodayData.getFajr());
            mDhurTimeTextView.setText(mPrayTodayData.getDhur());
            mAsrTimeTextView.setText(mPrayTodayData.getAsr());
            mMghribTimeTextView.setText(mPrayTodayData.getMghrib());
            mIshaTimeTextView.setText(mPrayTodayData.getIsha());

            mProgressBar.setVisibility(View.GONE);
        } else {
            Toast.makeText(this, "Check your device date and restart the app", Toast.LENGTH_LONG).show();
        }
    }
}
