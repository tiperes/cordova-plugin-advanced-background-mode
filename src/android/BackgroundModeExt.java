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
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

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
import static android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

public class BackgroundModeExt extends CordovaPlugin {

    private PowerManager.WakeLock wakeLock;

	private CallbackContext appStartCallback;
	private boolean appStartLaunched = false;

	private volatile boolean keepAliveRequested = false;

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
                    openAppStart(callback, args.opt(0));
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
        keepAliveRequested = true;
    	ensureKeepAlive();
    }
	
	@Override
	public void onPause(boolean multitasking) {
	    super.onPause(multitasking);
	
	    if (keepAliveRequested) {
	        ensureKeepAlive(); // called every background transition
	    }
	}

    private void ensureKeepAlive() {
	    Activity activity = cordova.getActivity();
	    if (activity == null) return;
	
	    View decorView = activity.getWindow().getDecorView();
	    // Post to UI thread on decorView
	    decorView.post(new Runnable() {
	        @Override
	        public void run() {
	            View webViewView = null;
	            try {
	                webViewView = webView != null ? webView.getEngine().getView() : null;
	            } catch (Exception ignored) {
	                // webView not fully initialized yet
	            }
	
	            if (webViewView != null) {
	                forceVisibility(webViewView, 20, 200);
	            } else if (keepAliveRequested) {
	                // WebView not ready yet, retry after short delay
	                decorView.postDelayed(this, 100);
	            }
	        }
	    });
    }
	
	/**
	 * Recursively attempts to force the WebView visible, bounded by retries
	 */
	private void forceVisibility(View view, int retries, long delayMs) {
	    if (view.isAttachedToWindow()) {
	        try {
	            // Crosswalk-specific hook
	            Class.forName("org.crosswalk.engine.XWalkCordovaView")
	                    .getMethod("onShow")
	                    .invoke(view);
	        } catch (Exception ignore) {
	            // System WebView fallback
	            view.dispatchWindowVisibilityChanged(View.VISIBLE);
	        }
	        return;
	    }
	
	    if (retries <= 0) return;
	
	    // Retry after a short delay
	    view.postDelayed(() -> forceVisibility(view, retries - 1, delayMs), delayMs);
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

    private void openAppStart(CallbackContext callback, Object arg) {
        Activity activity = cordova.getActivity();
        if (activity == null) {
	        callback.error("No activity");
	        return;
	    }
    
        PackageManager pm = activity.getPackageManager();
        Intent intent = null;
    
        for (Intent appStartIntent : getAppStartIntents()) {
            try {
                if (pm.resolveActivity(appStartIntent, MATCH_DEFAULT_ONLY) != null) {
					android.util.Log.d("BackgroundModeExt", "Found auto-start intent: " + appStartIntent);
					
					intent = appStartIntent;
                    break;
                } else {
                    android.util.Log.d("BackgroundModeExt", "Skipped auto-start intent: " + appStartIntent);
                }
            } catch (Exception e) {
                android.util.Log.e("BackgroundModeExt", "Error resolving auto-start intent: " + appStartIntent, e);
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

		// Store callback for later
		appStartCallback = callback;
		appStartLaunched = false;
		
		// Tell Cordova: result will come later
		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		callback.sendPluginResult(result);

		// Open app start settings
		if (arg instanceof Boolean && !((Boolean) arg)) {
			try {
        		launchAppStart(activity, intent);
			} catch (Exception e) {
				sendAppStartResult("Failed to open");
			}
		}
		// Show confirmation popup and then open app start settings
		else {			
			showAppStartDialog(activity, intent, (arg instanceof JSONObject) ? (JSONObject) arg : null);
		}
    }

	private void showAppStartDialog(Activity activity, Intent intent, JSONObject spec) {
	    if (activity == null) return;
	
	    activity.runOnUiThread(() -> {
	        try {
	            AlertDialog.Builder builder = new AlertDialog.Builder(
	                activity,
	                android.R.style.Theme_DeviceDefault_Dialog_Alert // Follows system theme
	            );

				// ---- Compose title + message ----		
				String title = "";
				if (spec != null && spec.has("title")) {
					title = spec.optString("title", "");
				}
				String message = "";
				if (spec != null && spec.has("text")) {
					message = spec.optString("text", "");
				}
				if (message.isEmpty()) {
					message = "To ensure the app works properly in background, " +
							  "please adjust the app start settings.";
				}

				// DO NOT call setTitle()
				// DO NOT call setView()
				SpannableString msg = new SpannableString(
				        (!title.isEmpty() ? title + "\n\n" : "") + message
				);
				if (!title.isEmpty()) {
				    msg.setSpan(
				            new StyleSpan(Typeface.BOLD),
				            0,
				            title.length(),
				            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				    );
				    msg.setSpan(
				            new RelativeSizeSpan(1.15f),
				            0,
				            title.length(),
				            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				    );
				}				
				builder.setMessage(msg);
	
	            // ---- Buttons ----
	            builder.setPositiveButton(android.R.string.ok, (o, d) -> {
	                try {
	                    launchAppStart(activity, intent);
	                } catch (Exception e) {
	                    sendAppStartResult("Failed to open from dialog");
	                }
	            });
	            builder.setNegativeButton(android.R.string.cancel, (o, d) -> {
	                sendAppStartResult("Canceled from dialog");
	            });	
	
	            // ---- Clear focus & hide IME BEFORE dialog ----
	            View focused = activity.getCurrentFocus();
	            if (focused != null) {
	                focused.clearFocus();
	                InputMethodManager imm =
	                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
	                if (imm != null) {
	                    imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
	                }
	            }
	
	            // ---- Create & show dialog ----
	            builder.setCancelable(false); // disable back button dismiss
	            AlertDialog dialog = builder.create();
	            dialog.setCanceledOnTouchOutside(false); // Prevent dismiss on outside touch
	
	            dialog.setOnShowListener(d -> {
	                // ---- Enforce modal behavior & IME isolation ----
	                Window dw = dialog.getWindow();
	                if (dw != null) {
	                    dw.setDimAmount(0.6f);
	                    dw.addFlags(
	                        FLAG_DIM_BEHIND |
	                        FLAG_ALT_FOCUSABLE_IM
	                    );
	                }
	
	                // ---- Final IME suppression ----
	                View dfocused = activity.getCurrentFocus();
	                if (dfocused != null) {
	                    dfocused.clearFocus();
	                    InputMethodManager dimm =
	                        (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
	                    if (dimm != null) {
	                        dimm.hideSoftInputFromWindow(dfocused.getWindowToken(), 0);
	                    }
	                }
	            });
	
	            dialog.show();
	
	        } catch (Exception e) {
	            sendAppStartResult("Failed to show dialog");
	        }
	    });
	}

	private void sendAppStartResult(String error) {
		if (appStartCallback == null) return;

		if (error == null) {
			appStartCallback.success();
		}
		else  {
			appStartCallback.error(error);
		}
		// Clear callback
		appStartCallback = null;
		appStartLaunched = false;
	}

	private void launchAppStart(Activity activity, Intent intent) {
		activity.startActivity(intent);
		appStartLaunched = true;

		// Schedule App Start Timeout
	    activity.getWindow().getDecorView().postDelayed(() -> {
	        if (appStartLaunched) {
	            sendAppStartResult(null);
	        }
	    }, 3000); // 2–3 seconds is ideal
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
