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
    
    public static final int NOTIFICATION_ID = 101;
    public static final String ACTION_UPDATE = "ForegroundService.UPDATE";
    public static final String ACTION_RECOVER = "ForegroundService.RECOVER";
    public static final String ACTION_GOTO_FOREGROUND = "ForegroundService.GOTO_FOREGROUND";
    
    private static final String CHANNEL_ID = "background_mode_channel";
    private static final String CHANNEL_NAME = "Background Mode";
    private static final String NOTIFICATION_TITLE = "App is running in background";
    private static final String NOTIFICATION_TEXT = "Doing heavy tasks.";
    private static final String NOTIFICATION_ICON = "ic_launcher";
    
    private JSONObject lastSettings = null;
    private JSONObject getSettings() {
        if (lastSettings == null) {
            lastSettings = BackgroundMode.getDefaultSettings();
        }
        return lastSettings;
    }
    
    private JSONObject getSettings(JSONObject newSettings) {
        if (newSettings == null) {
            lastSettings = BackgroundMode.getDefaultSettings();
        }
        else {
            lastSettings = newSettings;
        }
        return lastSettings;
    }

    @Override
    public IBinder onBind (Intent intent) {
        // This is a started-only service, not bindable
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();        
        startForegroundSafe(getSettings());
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            
            if (ACTION_UPDATE.equals(action)) {
                try {
                    JSONObject newSettings = new JSONObject(intent.getStringExtra("settings"));
                    updateNotification(getSettings(newSettings));
                } catch (Exception ignored) {}
            }
            else if (ACTION_RECOVER.equals(action)) {
                startForegroundSafe(getSettings());
            }
            else if (ACTION_GOTO_FOREGROUND.equals(action)) {
                // Move app to foreground
                BackgroundModeExt.moveToForeground(
                    getApplicationContext(), // service context
                    null                     // no activity reference available
                );
            }
        }
        // allows recovery if system kills service, ignored if user stopped
        return START_STICKY;
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

    private void startForegroundSafe(JSONObject settings) {
        ensureNotificationChannel();
        
        Notification notification = makeNotification(settings);
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
    
    /**
     * Update existing notification
     */
    private void updateNotification(JSONObject settings) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        
        nm.notify(NOTIFICATION_ID, makeNotification(settings));
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

        int intentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        if (resume) {
            Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());

            if (intent != null) {
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                builder.setContentIntent(
                        PendingIntent.getActivity(
                                context,
                                NOTIFICATION_ID,
                                intent,
                                intentFlags
                        )
                );
            }
        }

        // Add deleteIntent to handle user swipes
        Intent deleteIntent = new Intent(context, ForegroundService.class);
        deleteIntent.setAction(ACTION_RECOVER);
        builder.setDeleteIntent(
            PendingIntent.getService(
                context,
                NOTIFICATION_ID,
                deleteIntent,
                intentFlags
            )
        );

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
