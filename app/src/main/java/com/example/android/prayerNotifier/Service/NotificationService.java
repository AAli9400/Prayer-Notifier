package com.example.android.prayerNotifier.Service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import com.example.android.prayerNotifier.Database.AppDatabase;
import com.example.android.prayerNotifier.Database.PrayerData;
import com.example.android.prayerNotifier.MainActivity;
import com.example.android.prayerNotifier.R;

import java.util.ArrayList;
import java.util.Calendar;

public class NotificationService extends IntentService {

    public NotificationService() {
        super("NotificationService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals("scheduling")) {
            scheduleNotifications(intent.getIntExtra("i", 0));
        } else if (intent.getAction().equals("push_notification")) {
            pushNotification(intent.getStringExtra("prayer"));
        }
    }

    private void scheduleNotifications(int i) {
        AppDatabase database = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "prayer_notifier").build();

        boolean scheduleForTheNextDay = (i == 5);

        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
        if (scheduleForTheNextDay) {
            currentDay++;
            i = 0;
        }
        String currentMonth = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        if (currentMonth.charAt(0) != '1') {
            currentMonth = "0" + currentMonth;
        }
        String prayTodayData = String.valueOf(currentDay) + "-" +
                currentMonth + "-" +
                String.valueOf(calendar.get(Calendar.YEAR));

        PrayerData prayerData = database.prayDao().loadByDate(prayTodayData);

        ArrayList<Integer> times = new ArrayList<>();
        times.add(Integer.valueOf(prayerData.getFajr().substring(0, 2)));
        times.add(Integer.valueOf(prayerData.getFajr().substring(3, 5)));

        if (!scheduleForTheNextDay) {
            times.add(Integer.valueOf(prayerData.getDhur().substring(0, 2)));
            times.add(Integer.valueOf(prayerData.getDhur().substring(3, 5)));

            times.add(Integer.valueOf(prayerData.getAsr().substring(0, 2)));
            times.add(Integer.valueOf(prayerData.getAsr().substring(3, 5)));

            times.add(Integer.valueOf(prayerData.getMghrib().substring(0, 2)));
            times.add(Integer.valueOf(prayerData.getMghrib().substring(3, 5)));

            times.add(Integer.valueOf(prayerData.getIsha().substring(0, 2)));
            times.add(Integer.valueOf(prayerData.getIsha().substring(3, 5)));
        }

        /*
         * i | -> | j
         * ----------
         * 0 | -> | 0
         * 1 | -> | 2
         * 2 | -> | 4
         * 3 | -> | 6
         *
         * and so on
         * */

        int j = i + i;

        for (; i < 5; ++i) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(System.currentTimeMillis());
            c.set(Calendar.HOUR_OF_DAY, times.get(j++));
            c.set(Calendar.MINUTE, times.get(j++));
            if(scheduleForTheNextDay){
                c.add(Calendar.DAY_OF_YEAR, 1);
            }

            if (!(c.getTimeInMillis() < System.currentTimeMillis())) {

                Intent intent = new Intent(getApplicationContext(), NotificationService.class);
                intent.setAction("push_notification");
                intent.putExtra("prayer", String.valueOf(i));//value of i is important here
                PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

                AlarmManager alarmManager = (AlarmManager) this.getSystemService(ALARM_SERVICE);
                if (alarmManager != null) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                            c.getTimeInMillis(), pendingIntent);
                }
                break;
            }
        }
    }

    private void pushNotification(String prayer) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "1")
                .setContentTitle("Prayer Time")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentIntent(PendingIntent.getActivity(
                                getApplicationContext(),
                                1,
                                new Intent(getApplicationContext(), MainActivity.class),
                                PendingIntent.FLAG_CANCEL_CURRENT))
                .setAutoCancel(true);

        switch (prayer) {
            case "0":
                mBuilder.setContentText("It's alfajr prayer time");
                break;
            case "1":
                mBuilder.setContentText("It's aldhur prayer time");
                break;
            case "2":
                mBuilder.setContentText("It's alasr prayer time");
                break;
            case "3":
                mBuilder.setContentText("It's almaghrib prayer time");
                break;
            case "4":
                mBuilder.setContentText("It's alisha prayer time");
                break;
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String CHANNEL_ID = "1";
            String name = "PrayNotifier";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            mChannel.enableVibration(true);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }
        }

        // notificationId is a unique int for each notification that you must define
        if (notificationManager != null) {
            notificationManager.notify(0, mBuilder.build());
        }

        Intent intent = new Intent(getApplicationContext(), NotificationService.class);
        intent.setAction("scheduling");
        intent.putExtra("i", Integer.valueOf(prayer) + 1);
        startService(intent);
    }
}
