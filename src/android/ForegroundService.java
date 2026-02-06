package de.einfachhans.BackgroundMode;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

public class ForegroundService extends Service {
    
    public static final String ACTION_NOTIFICATION = "ForegroundService.NOTIFICATION";

    public static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "background_mode_channel";
    private static final String CHANNEL_NAME = "Background Mode";
    private static final String NOTIFICATION_TITLE = "App is running in background";
    private static final String NOTIFICATION_TEXT = "Doing heavy tasks.";
    private static final String NOTIFICATION_ICON = "ic_launcher";

    @Override
    public IBinder onBind (Intent intent) {
        // This is a started-only service, not bindable
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        ensureNotificationChannel();
        Notification notification = makeNotification(BackgroundMode.getDefaultSettings());

        // Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int fgsTypes =
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC |
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
            startForeground(NOTIFICATION_ID, notification, fgsTypes);
        // Android 10+
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        // Older than Android 10
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_NOTIFICATION.equals(intent.getAction())) {
            try {
                String raw = intent.getStringExtra("settings");
                if (raw != null) {
                    updateNotification(new JSONObject(raw));
                }
            } catch (Exception ignored) {}
        }
        // allows recovery if system kills service, ignored if user stopped
        // onCreate is execute and then onStartCommand will also with the last intent received.
        return START_REDELIVER_INTENT;
    }

    /**
     * Update existing notification
     */
    public void updateNotification(JSONObject settings) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        
        nm.notify(NOTIFICATION_ID, makeNotification(settings));
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification makeNotification(JSONObject settings) {
        Context context = getApplicationContext();

        String title = settings.optString("title", NOTIFICATION_TITLE);
        String text = settings.optString("text", NOTIFICATION_TEXT);
        String icon = settings.optString("icon", NOTIFICATION_ICON);
        boolean bigText = settings.optBoolean("bigText", false);
        boolean resume = settings.optBoolean("resume", true);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setOngoing(true)
                        .setSilent(true)
                        .setSmallIcon(getIconResId(context, icon))
                        .setPriority(NotificationCompat.PRIORITY_LOW);

        if (bigText || text.contains("\n")) {
            builder.setStyle(
                    new NotificationCompat.BigTextStyle().bigText(text)
            );
        }

        setColor(builder, settings);

        if (resume) {
            Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());

            if (intent != null) {
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                builder.setContentIntent(
                        PendingIntent.getActivity(
                                context,
                                NOTIFICATION_ID,
                                intent,
                                flags
                        )
                );
            }
        }

        return builder.build();
    }

    private void setColor(NotificationCompat.Builder builder, JSONObject settings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        String hex = settings.optString("color", null);
        if (hex == null) return;

        try {
            int color = Color.parseColor("#" + hex.replace("#", ""));
            builder.setColor(color);
        } catch (Exception ignored) {}
    }

    private int getIconResId(Context context, String iconName) {
        Resources res = context.getResources();
        String pkgName = context.getPackageName();

        int iconId = findIconResourceId(res, pkgName, iconName);
        if (iconId == 0) {
            iconId = findIconResourceId(res, pkgName, NOTIFICATION_ICON);
        }
        if (iconId == 0) {
            iconId = android.R.drawable.ic_dialog_info;
        }
        return iconId;
    }

    private int findIconResourceId(Resources res, String pkgName, String iconName) {
        if (iconName == null || iconName.isEmpty()) return 0;

        int resId = res.getIdentifier(iconName, "mipmap", pkgName);
        if (resId == 0) {
            resId = res.getIdentifier(iconName, "drawable", pkgName);
        }
        return resId;
    }
}
