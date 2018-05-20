package com.example.android.prayerNotifier;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
    private TextView mDhuhrTimeTextView;
    private TextView mAsrTimeTextView;
    private TextView mMghribTimeTextView;
    private TextView mIshaTimeTextView;

    private ProgressBar mProgressBar;

    private AppDatabase mDatabase;
    private DatabaseAsyncTask mDatabaseAsyncTask;

    private VolleyOnResponseAsyncTask mVolleyOnResponseAsyncTask;
    private VolleyOnErrorAsyncTask mVolleyOnErrorAsyncTask;

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
        mDhuhrTimeTextView = findViewById(R.id.dhuhr_time);
        mAsrTimeTextView = findViewById(R.id.asr_time);
        mMghribTimeTextView = findViewById(R.id.mghrib_time);
        mIshaTimeTextView = findViewById(R.id.isha_time);

        mProgressBar = findViewById(R.id.progressBar);

        mDatabase = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "prayer_notifier").build();

        mDatabaseAsyncTask = new DatabaseAsyncTask();
        mDatabaseAsyncTask.execute();

        SharedPreferences mPreferences = getPreferences(MODE_PRIVATE);
        if (!mPreferences.getBoolean("run", false)) {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putBoolean("run", true).apply();

            ScheduleRepeatingAlarmToFireNotificationServiceEveryDay();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.menu_main, menu);
        return true;
    }

    @SuppressLint({"StaticFieldLeak", "BatteryLife"})
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reset:
                RefreshAsyncTask refreshAsyncTask = new RefreshAsyncTask();
                refreshAsyncTask.execute();
                return true;

            case R.id.action_fix_notifications:
                //Ask for user permission to make the app not battery optimized
                //Cause making it battery optimized will affect the alarm manager.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent();
                    String packageName = getPackageName();
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    if (pm != null) {
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + packageName));
                            startActivity(intent);
                        }else{
                            Toast.makeText(this, "All required permissions already granted", Toast.LENGTH_LONG).show();
                        }
                    }
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateFromTheInternet(final boolean refresh) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://api.aladhan.com/v1/calendarByCity?city=Cairo&country=Egypt&method=5";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @SuppressLint("StaticFieldLeak")
            @Override
            public void onResponse(final String response) {
                mVolleyOnResponseAsyncTask = new VolleyOnResponseAsyncTask();
                mVolleyOnResponseAsyncTask.execute(response, (refresh) ? "true" : "false");
            }
        }, new Response.ErrorListener() {
            @SuppressLint("StaticFieldLeak")
            @Override
            public void onErrorResponse(VolleyError error) {
                mVolleyOnErrorAsyncTask = new VolleyOnErrorAsyncTask();
                mVolleyOnErrorAsyncTask.execute();
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
                    data.setDhuhr(timings.getString("Dhuhr").substring(0, 5));
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
            mDhuhrTimeTextView.setText(mPrayTodayData.getDhuhr());
            mAsrTimeTextView.setText(mPrayTodayData.getAsr());
            mMghribTimeTextView.setText(mPrayTodayData.getMghrib());
            mIshaTimeTextView.setText(mPrayTodayData.getIsha());

            mProgressBar.setVisibility(View.GONE);
        } else {
            Toast.makeText(this, "Check your device date then restart the app", Toast.LENGTH_LONG).show();
        }
    }

    private void stopAsyncTasks() {
        if (mDatabaseAsyncTask.getStatus() == AsyncTask.Status.RUNNING || mDatabaseAsyncTask.getStatus() == AsyncTask.Status.PENDING) {
            mDatabaseAsyncTask.cancel(true);
        }

        if (mVolleyOnResponseAsyncTask != null) {
            if (mVolleyOnResponseAsyncTask.getStatus() == AsyncTask.Status.RUNNING || mVolleyOnResponseAsyncTask.getStatus() == AsyncTask.Status.PENDING) {
                mVolleyOnResponseAsyncTask.cancel(true);
            }
        }
    }

    private void ScheduleRepeatingAlarmToFireNotificationServiceEveryDay() {
        //Schedule to start the NotificationService every day
        //NotificationService will schedule each day's notification(s)
        //This code runs only one time.

        Intent serviceIntent = new Intent(MainActivity.this, NotificationService.class);
        serviceIntent.setAction("scheduling");

        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);

        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);

        }
    }

    @SuppressLint("StaticFieldLeak")
    private class DatabaseAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            //if the app was force closed, reschedule to start the NotificationService every day
            Intent serviceIntent = new Intent(MainActivity.this, NotificationService.class);
            serviceIntent.setAction("scheduling");
            PendingIntent pendingIntent = PendingIntent.getService(
                    getApplicationContext(),
                    0,
                    serviceIntent,
                    PendingIntent.FLAG_NO_CREATE
            );
            if (pendingIntent == null) {
                ScheduleRepeatingAlarmToFireNotificationServiceEveryDay();

                //reschedule today's notification(s)
                startService(serviceIntent);
            }


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
                updateFromTheInternet(false);
                return false;
            } else {
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

    @SuppressLint("StaticFieldLeak")
    private class VolleyOnResponseAsyncTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... strings) {
            parseJsonResponseToTheDataBase(strings[0]);//add the new month data

            mPrayTodayData = mDatabase.prayDao().loadByDate(mCurrentDate);

            //Schedule today's notification(s)
            Intent serviceIntent = new Intent(MainActivity.this, NotificationService.class);
            serviceIntent.setAction("scheduling");
            startService(serviceIntent);

            return strings[1].equals("true");
        }

        @Override
        protected void onPostExecute(Boolean refresh) {
            updateLocally();

            if (refresh) {
                Toast.makeText(MainActivity.this, "Refreshed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class VolleyOnErrorAsyncTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            mPrayTodayData = mDatabase.prayDao().loadByDate(mCurrentDate);
            int i = 31;
            while (mPrayTodayData == null) {
                mPrayTodayData = mDatabase.prayDao().loadById(i--);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            updateLocally();

            Toast.makeText(MainActivity.this, "Error updating prayer timing", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class RefreshAsyncTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            stopAsyncTasks();

            //cancel all scheduled notification(s)
            for (int i = 0; i < 5; ++i) {
                Intent intent = new Intent(getApplicationContext(), NotificationService.class);
                intent.setAction("push_notification");
                intent.putExtra("prayer", String.valueOf(i));

                PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), i, intent, PendingIntent.FLAG_NO_CREATE);

                AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (alarmManager != null && pendingIntent != null) {
                    alarmManager.cancel(pendingIntent);
                }
            }

            updateFromTheInternet(true);
            return null;
        }
    }
}
