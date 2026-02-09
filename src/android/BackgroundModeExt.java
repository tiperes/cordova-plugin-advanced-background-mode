package de.einfachhans.BackgroundMode;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.POWER_SERVICE;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
import static android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

public class BackgroundModeExt extends CordovaPlugin {

    private PowerManager.WakeLock wakeLock;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) {
        boolean validAction = true;

        try {
            switch (action) {
                case "battery":
                    disableBatteryOptimizations();
                    callback.success();
                    break;
                case "webview":
                    disableWebViewOptimizations();
                    callback.success();
                    break;
                case "appstart":
                    openAppStart(args.opt(0));
                    callback.success();
                    break;
                case "background":
                    moveToBackground();
                    callback.success();
                    break;
                case "foreground":
                    moveToForeground();
                    callback.success();
                    break;
                case "tasklistExclude":
                    setExcludeFromRecents(true);
                    callback.success();
                    break;
                case "tasklistInclude":
                    setExcludeFromRecents(false);
                    callback.success();
                    break;
                case "dimmed":
                    isDimmed(callback);
                    break;
                case "wakeup":
                    wakeup();
                    callback.success();
                    break;
                case "unlock":
                    wakeup();
                    unlock();
                    callback.success();
                    break;
                default:
                    validAction = false;
            }
        } catch (Exception e) {
            callback.error("Error executing " + action + ": " + e.getMessage());
            return false;
        }

        if (!validAction) {
            callback.error("Invalid action: " + action);
        }

        return validAction;
    }

    private void moveToBackground() {
        moveToBackground(cordova.getActivity());
    }

    public static void moveToBackground(Context context) {
        if (context == null) return;

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void moveToForeground() {
        Activity activity = cordova.getActivity();
        if (activity == null) return;
        
        moveToForeground(activity.getApplicationContext(), activity);
    }

    public static void moveToForeground(Activity activity) {
        if (activity == null) return;
        
        moveToForeground(activity.getApplicationContext(), activity);
    }

    public static void moveToForeground(Context context, Activity activity) {
        if (context == null) return;

        Intent intent = getLaunchIntent(context);
        if (intent == null) return;

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
            Intent.FLAG_ACTIVITY_SINGLE_TOP |
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        context.startActivity(intent);

        // Apply window flags only if Activity is available
        if (activity != null) {
            clearScreenAndKeyguardFlags(activity);
        }
    }

    private void disableWebViewOptimizations() {
        Activity activity = cordova.getActivity();
        if (activity == null) return;
    
        int maxRetries = 20;      // total attempts
        long retryDelay = 200;    // ms between attempts
    
        new Thread(() -> {
            int attempts = 0;    
            while (attempts < maxRetries) {
                if (webView != null) break;
    
                attempts++;
                try { Thread.sleep(retryDelay); } catch (InterruptedException ignored) {}
            }
    
            // WebView still not available
            if (webView == null) return;
    
            activity.runOnUiThread(() -> {
                View view;
                try {
                    view = webView.getEngine().getView();
                } catch (Exception e) { return; }
    
                if (view == null) return;
    
                view.post(() -> {
                    if (!view.isAttachedToWindow()) return;
    
                    try {
                        // Crosswalk-specific hook
                        Class.forName("org.crosswalk.engine.XWalkCordovaView")
                            .getMethod("onShow")
                            .invoke(view);
                    } catch (Exception ignore) {
                        // fallback for normal WebView
                        view.dispatchWindowVisibilityChanged(View.VISIBLE);
                    }
                });
            });
        }).start();
    }

    @SuppressLint("BatteryLife")
    private void disableBatteryOptimizations() {
        Activity activity = cordova.getActivity();
        if (activity == null) return;

        if (SDK_INT < M) return;

        String pkgName = activity.getPackageName();
        PowerManager pm = (PowerManager) getService(POWER_SERVICE);
        
        if (pm == null) return;
        if (pm.isIgnoringBatteryOptimizations(pkgName)) return;

        try {
            Intent intent = new Intent();
            intent.setAction(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + pkgName));
            activity.startActivity(intent);
        } catch (Exception e) {
            // Handle case where intent can't be resolved
            android.util.Log.e("BackgroundModeExt", "Cannot open battery optimization settings", e);
        }
    }

    private void openAppStart(Object arg) {
        Activity activity = cordova.getActivity();
        if (activity == null) return;
    
        PackageManager pm = activity.getPackageManager();
        Intent intent = null;
    
        for (Intent appStartIntent : getAppStartIntents()) {
            try {
                if (pm.resolveActivity(appStartIntent, MATCH_DEFAULT_ONLY) != null) {
					android.util.Log.d("BackgroundModeExt", "Found auto-start intent: " + intent);
					
					intent = appStartIntent;
                    break;
                } else {
                    android.util.Log.d("BackgroundModeExt", "Skipped intent: " + intent);
                }
            } catch (Exception e) {
                android.util.Log.e("BackgroundModeExt", "Error resolving intent: " + intent, e);
            }
        }
    
        // Fallback to app settings if no intent resolved
        if (intent == null) {
            intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
					.setData(Uri.parse("package:" + activity.getPackageName()));
			
			android.util.Log.d("BackgroundModeExt", "Fallback to app settings");
        }
		
		// Open settings
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		if (arg instanceof Boolean && !((Boolean) arg)) {
			try {
				activity.startActivity(intent);
			} catch (Exception e) {
				android.util.Log.e("BackgroundModeExt", "Failed to open app start", e);
			}
		}
		else {
			showAppStartDialog(activity, intent, (arg instanceof JSONObject) ? (JSONObject) arg : null);
		}
    }

    private void showAppStartDialog(Activity activity, Intent intent, JSONObject spec) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity, 
            android.R.style.Theme_DeviceDefault_Light_Dialog);

        dialog.setPositiveButton(android.R.string.ok, (o, d) -> {
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("BackgroundModeExt", "Failed to open app start from popup", e);
            }
        });
        
        dialog.setNegativeButton(android.R.string.cancel, (o, d) -> {});
        dialog.setCancelable(true);

        if (spec != null && spec.has("title")) {
            dialog.setTitle(spec.optString("title"));
        }

        if (spec != null && spec.has("text")) {
            dialog.setMessage(spec.optString("text"));
        } else {
            dialog.setMessage("To ensure the app works properly in background, " +
                "please adjust the app start settings.");
        }

        activity.runOnUiThread(dialog::show);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setExcludeFromRecents(boolean value) {
        ActivityManager am = (ActivityManager) getService(ACTIVITY_SERVICE);

        if (am == null || SDK_INT < 21) return;

        try {
            List<AppTask> tasks = am.getAppTasks();
            if (tasks != null && !tasks.isEmpty()) {
                tasks.get(0).setExcludeFromRecents(value);
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    private void isDimmed(CallbackContext callback) {
        boolean status = isDimmed();
        PluginResult res = new PluginResult(Status.OK, status);
        callback.sendPluginResult(res);
    }

    private boolean isDimmed() {
        PowerManager pm = (PowerManager) getService(POWER_SERVICE);
        if (pm == null) return false;

        if (SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return !pm.isInteractive();
        } else {
            return !pm.isScreenOn();
        }
    }

    private void wakeup() {
        try {
            acquireWakeLock();
        } catch (Exception e) {
            releaseWakeLock();
        }
    }

    private void unlock() {
        Activity activity = cordova.getActivity();
        addScreenAndKeyguardFlags(activity);
        Intent intent = getLaunchIntent();
        if (intent != null) {
            activity.startActivity(intent);
        }
    }

    @SuppressWarnings("deprecation")
    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getService(POWER_SERVICE);
        if (pm == null) return;

        releaseWakeLock();

        if (!isDimmed()) return;

        // Use SCREEN_BRIGHT_WAKE_LOCK for better compatibility
        int level = PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                   PowerManager.ACQUIRE_CAUSES_WAKEUP;

        wakeLock = pm.newWakeLock(level, "backgroundmode:wakelock");
        wakeLock.setReferenceCounted(false);
        
        // Acquire with timeout (3 seconds) for safety
        wakeLock.acquire(3000);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                // Already released
            }
            wakeLock = null;
        }
    }

    private static void addScreenAndKeyguardFlags(Activity activity) {
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    window.addFlags(
                        FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                        FLAG_SHOW_WHEN_LOCKED |
                        FLAG_TURN_SCREEN_ON |
                        FLAG_DISMISS_KEYGUARD
                    );
                }
            } catch (Exception e) {
                // Silently fail
            }
        });
    }

    private static void clearScreenAndKeyguardFlags(Activity activity) {
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    window.clearFlags(
                        FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                        FLAG_SHOW_WHEN_LOCKED |
                        FLAG_TURN_SCREEN_ON |
                        FLAG_DISMISS_KEYGUARD
                    );
                }
            } catch (Exception e) {
                // Silently fail
            }
        });
    }

    public static void clearKeyguardFlags(Activity activity) {
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    window.clearFlags(FLAG_DISMISS_KEYGUARD);
                }
            } catch (Exception e) {
                // Silently fail
            }
        });
    }

    private Intent getLaunchIntent() {
        Activity activity = cordova.getActivity();
        if (activity == null) return null;

        return getLaunchIntent(activity.getApplicationContext());
    }

    private static Intent getLaunchIntent(Context context) {
        if (context == null) return null;

        return context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    }

    private Object getService(String name) {
        Activity activity = cordova.getActivity();
        if (activity == null) return null;
        
        return activity.getSystemService(name);
    }

    private List<Intent> getAppStartIntents() {
        return Arrays.asList(
            // Xiaomi
            new Intent().setComponent(new ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )),
            // Letv
            new Intent().setComponent(new ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"
            )),
            // Huawei
            new Intent().setComponent(new ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )),
            new Intent().setComponent(new ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )),
            // Oppo
            new Intent().setComponent(new ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )),
            new Intent().setComponent(new ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )),
            new Intent().setComponent(new ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )),
            // Vivo
            new Intent().setComponent(new ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )),
            new Intent().setComponent(new ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            )),
            new Intent().setComponent(new ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )),
            // Asus
            new Intent().setComponent(new ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.autostart.AutoStartActivity"
            )),
            new Intent().setComponent(new ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.entry.FunctionActivity"
            )).setData(Uri.parse("mobilemanager://function/entry/AutoStart")),
            // Samsung Global & China
            new Intent().setComponent(new ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.ram.AutoRunActivity"
            )),
            new Intent().setComponent(new ComponentName(
                "com.samsung.android.sm_cn",
                "com.samsung.android.sm.ui.ram.AutoRunActivity"
            )),
            // Meizu
            new Intent().setComponent(ComponentName.unflattenFromString(
                "com.meizu.safe/.permission.SmartBGActivity"
            )),
            // Lenovo / ZUI
            new Intent().setComponent(new ComponentName(
                "com.zui.safecenter",
                "com.lenovo.safecenter.MainTab.LeSafeMainActivity"
            )),
            // Nubia
            new Intent().setComponent(ComponentName.unflattenFromString(
                "cn.nubia.security2/.selfstart.ui.SelfStartActivity"
            )),
            // Zebra
            new Intent().setComponent(new ComponentName(
                "com.symbol.deviceenterprise",
                "com.symbol.deviceenterprise.DeviceAdminActivity"
            )),
            // Other manufacturers
            new Intent().setAction("com.letv.android.permissionautoboot"),
            new Intent().setComponent(ComponentName.unflattenFromString(
                "com.iqoo.secure/.MainActivity"
            )),
            new Intent().setComponent(new ComponentName(
                "com.yulong.android.coolsafe",
                ".ui.activity.autorun.AutoRunListActivity"
            ))
        );
    }
}
