package de.einfachhans.BackgroundMode;

import android.annotation.SuppressLint;
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
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

public class ForegroundService extends Service {

    public static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "cordova_adv_bg_mode_channel_v1";
    private static final String NOTIFICATION_TITLE = "App is running in background";
    private static final String NOTIFICATION_TEXT = "Doing heavy tasks.";
    private static final String NOTIFICATION_ICON = "ic_launcher";

    private final IBinder binder = new ForegroundBinder();
    private PowerManager.WakeLock wakeLock;

    @Override
    public IBinder onBind (Intent intent) {
        return binder;
    }

    class ForegroundBinder extends Binder {
        ForegroundService getService() {
            return ForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        keepAwake();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sleepWell();
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Background Service";
            String description = "Keeps app running in background";
            int importance = NotificationManager.IMPORTANCE_LOW;
            
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private void keepAwake() {
        JSONObject settings = BackgroundMode.getSettings();
        boolean isSilent = settings.optBoolean("silent", false);

        if (!isSilent) {
            Notification notification = makeNotification();

            // Foreground Service Types (Android 12+)
            int fgsTypes =
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC |
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                startForeground(NOTIFICATION_ID, notification, fgsTypes);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12–13
                startForeground(NOTIFICATION_ID, notification, fgsTypes);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10–11
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                // Older Android
                startForeground(NOTIFICATION_ID, notification);
            }
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PARTIAL_WAKE_LOCK, "backgroundmode:wakelock");
            wakeLock.acquire();
        }
    }

    private void sleepWell() {
        stopForeground(true);
        getNotificationManager().cancel(NOTIFICATION_ID);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private Notification makeNotification() {
        return makeNotification(BackgroundMode.getSettings());
    }

    private Notification makeNotification(JSONObject settings) {
        String title = settings.optString("title", NOTIFICATION_TITLE);
        String text = settings.optString("text", NOTIFICATION_TEXT);
        String icon = settings.optString("icon", NOTIFICATION_ICON);
        boolean bigText = settings.optBoolean("bigText", false);

        Context context = getApplicationContext();
        String pkgName = context.getPackageName();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);

        Notification.Builder notification;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(context, CHANNEL_ID);
        } else {
            notification = new Notification.Builder(context);
        }

        notification
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSmallIcon(getIconResId(context, icon));

        if (settings.optBoolean("hidden", true)) {
            notification.setPriority(Notification.PRIORITY_MIN);
        }

        if (bigText || text.contains("\n")) {
            notification.setStyle(new Notification.BigTextStyle().bigText(text));
        }

        setColor(notification, settings);

        if (intent != null && settings.optBoolean("resume")) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            // PendingIntent flags for Android 12+
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            
            PendingIntent contentIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags);
            notification.setContentIntent(contentIntent);
        }

        return notification.build();
    }

    protected void updateNotification(JSONObject settings) {
        boolean isSilent = settings.optBoolean("silent", false);

        if (isSilent) {
            stopForeground(true);
            return;
        }

        Notification notification = makeNotification(settings);
        getNotificationManager().notify(NOTIFICATION_ID, notification);
    }

    /**
     * Gets the resource ID for a small notification icon, with fallbacks.
     *
     * @param context     The application context.
     * @param iconName    The name of the icon you want (e.g., "power").
     * @return A valid drawable resource ID (int).
     */
    private int getIconResId(Context context, String iconName) {
        Resources res = context.getResources();
        String pkgName = context.getPackageName();
        
        // 1. Try to find the named icon (e.g., "ic_notify_power")
        int iconId = findIconResourceId(res, pkgName, iconName);
        // 2. If not found, fall back to the default notification (app) icon
        if (iconId == 0) {
            iconId = findIconResourceId(res, pkgName, NOTIFICATION_ICON);
        }
        // 4. As a final, last-resort safety net, use a built-in system icon.
        //    This prevents setSmallIcon(0), which causes the "stop" icon.
        if (iconId == 0) {
            iconId = android.R.drawable.ic_dialog_info;
        }
        return iconId;
    }

    /**
     * Helper method to search for an icon resource in both mipmap and drawable.
     * The plugin code you shared checks mipmap first, so we do the same.
     */
    private int findIconResourceId(Resources res, String pkgName, String iconName) {
        if (iconName == null || iconName.isEmpty()) {
            return 0;
        }
        
        // 1. Check mipmap folders
        int resId = res.getIdentifier(iconName, "mipmap", pkgName);        
        // 2. If not in mipmap, check drawable folders
        if (resId == 0) {
            resId = res.getIdentifier(iconName, "drawable", pkgName);
        }        
        return resId;
    }

    private void setColor(Notification.Builder notification, JSONObject settings) {
        String hex = settings.optString("color", null);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || hex == null)
            return;

        try {
            int aRGB = Color.parseColor("#" + hex.replaceAll("#", ""));
            notification.setColor(aRGB);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
}
