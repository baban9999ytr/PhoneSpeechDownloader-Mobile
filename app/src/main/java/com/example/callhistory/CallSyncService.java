package com.example.callhistory;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class CallSyncService extends Service {

    private static final String CHANNEL_ID = "CallSyncServiceChannel";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private int countdown = 5;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = createNotification("Starting sync...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

        startTrackingLoop();
        return START_STICKY;
    }

    private void startTrackingLoop() {
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (countdown > 0) {
                    updateNotification(countdown + " minutes left until update");
                    countdown--;
                } else {
                    updateNotification("Updating...");

                    new Thread(() -> {
                        CallSyncHelper.syncHistoricalCalls(getApplicationContext(), 5, false);
                    }).start();

                    countdown = 5;
                }

                handler.postDelayed(this, 60000);
            }
        };
        handler.post(countdownRunnable);
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, createNotification(text));
        }
    }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Call Tracker Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Background Sync Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
}