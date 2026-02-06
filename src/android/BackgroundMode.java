package de.einfachhans.BackgroundMode;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

import de.einfachhans.BackgroundMode.ForegroundService;

import static de.einfachhans.BackgroundMode.BackgroundModeExt.clearKeyguardFlags;

public class BackgroundMode extends CordovaPlugin {

    // Event types for callbacks
    private enum Event { ACTIVATE, DEACTIVATE, FAILURE }

    // Plugin namespace
    private static final String JS_NAMESPACE = "cordova.plugins.backgroundMode";

    // Permission request codes
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    // Default settings for the notification
    private static JSONObject defaultSettings = new JSONObject();
    
    /**
     * Returns the settings for the new/updated notification.
     */
    public static JSONObject getDefaultSettings () {
        return defaultSettings;
    }
    
	// Flag indicates if the app is in background or foreground
    private boolean inBackground = false;
	
	// Flag indicates if the plugin is enabled or disabled
    private boolean isEnabled = false;

    // Pending enable request
    private boolean isEnablePending = false;

    private CallbackContext permissionCallback;

    // Flag indicates if the foreground services has been started
    private boolean isForegroundStarted = false;
    
    @Override
    public void onDestroy()
    {
        stopForeground();
        // Older then Android 8
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    /**
     * Executes the request.
     */
    @Override
    public boolean execute (String action, JSONArray args, CallbackContext callback)
    {
        boolean validAction = true;

        switch (action)
        {
            case "configure":
                configure(args.optJSONObject(0), args.optBoolean(1));
                callback.success();
                break;
            case "enable":
                enableMode(callback);
                break;
            case "disable":
                disableMode();
                callback.success();
                break;
            case "requestPermissions":
                requestNotificationPermission(callback);
                break;
            default:
                validAction = false;
        }

        if (!validAction) {
            callback.error("Invalid action: " + action);
        }

        return validAction;
    }
	
	/**
	 * Request notification permission for Android 13+
	 */
	private void requestNotificationPermission(CallbackContext callback)
	{
	    // Android 13+ requires POST_NOTIFICATIONS permission
	    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
	        ContextCompat.checkSelfPermission(cordova.getActivity(),
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
	        callback.success();
	        return;
	    }
	
	    // Store callback for later
	    permissionCallback = callback;

		// Tell Cordova: result will come later
	    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
	    result.setKeepCallback(true);
	    callback.sendPluginResult(result);
	
	    // Request permissions
		ActivityCompat.requestPermissions(
			activity,
			new String[]{Manifest.permission.POST_NOTIFICATIONS},
			NOTIFICATION_PERMISSION_REQUEST_CODE
		);
	}
	
    /**
     * Handle permission request result
     */
    @Override
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
	{
		// ignore unexpected requests
	    if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE &&
			permissionCallback != null) return;
	
	    boolean granted = grantResults.length > 0 && 
						  grantResults[0] == PackageManager.PERMISSION_GRANTED;
	
	    if (granted) {
			permissionCallback.success();
		} else {
			permissionCallback.error("Notification permission denied");
		}
		permissionCallback = null;
	
	    // If there was a pending enable request
	    if (isEnablePending && granted) {
	        isEnablePending = false;
			
			// Permission granted → start foreground service
		    isEnabled = true;
		    startForeground();
	    }
	}

	/**
	 * Enable background mode
	 */
	private void enableMode(CallbackContext callback)
	{
	    // Android 13+ check
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
	        ContextCompat.checkSelfPermission(cordova.getActivity(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
	        
	        // Will enable after permission granted
	        isEnablePending = true;
	
	        // Request permission (will callback via onRequestPermissionResult)
	        requestNotificationPermission(callback);
	        return;
	    }
	
	    // Permission already granted or not needed → start foreground service
	    isEnabled = true;
	    startForeground();
		
		callback.success();
	}

    /**
     * Disable the background mode.
     */
    private void disableMode()
    {
		isEnabled = false;
		stopForeground();
    }

    /**
     * Called when the activity is no longer visible to the user.
     */
    @Override
    public void onStop () {
        clearKeyguardFlags(cordova.getActivity());
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    public void onPause(boolean multitasking)
    {
        try {
			inBackground = true;
			startForeground();
        } finally {
            clearKeyguardFlags(cordova.getActivity());
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     */
    @Override
    public void onResume (boolean multitasking)
    {
		inBackground = false;
        stopForeground();
    }

    /**
     * Update the default settings and configure the notification.
     */
    private void configure(JSONObject settings, boolean update)
    {
        if (update) {
			if (!isForegroundStarted) return;

            Activity context = cordova.getActivity();
			Intent intent    = new Intent(context, ForegroundService.class);
            intent.setAction(ForegroundService.ACTION_UPDATE);
            intent.putExtra("settings", settings.toString());
			context.startService(intent);
        } else {
            defaultSettings = settings;
        }
    }

    /**
     * Bind the activity to a background service and put them into foreground state.
     */
    private void startForeground()
    {
		if (isForegroundStarted) return;
		
		// Android 12+:
		// - Start once when enabled
		// - Never stop on pause/resume
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {			
			if (!isEnabled) return;
		//}
		// Android ≤11:
	    // - MAY start/stop on background transitions
	    // - BUT do NOT rely on this for recovery logic
		//else {
		//	if (!inBackground && !isEnabled) return;
		//}

        try {
			Activity context = cordova.getActivity();
			Intent intent    = new Intent(context, ForegroundService.class);			
			// Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
             // Older Androids
            } else {
                context.startService(intent);
            }
            
            isForegroundStarted = true;            
            fireEvent(Event.ACTIVATE, null);
		} catch (Exception e) {
			fireEvent(Event.FAILURE, String.format("'%s'", e.getMessage()));
		}
    }

    /**
     * Stop the foreground service.
     */
    private void stopForeground()
    {
		if (!isForegroundStarted) return;
		
		// Android 12+:
		// - Start once when enabled
		// - Never stop on pause/resume
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			if (isEnabled) return;
		//}
		// Android ≤11:
	    // - MAY start/stop on background transitions
	    // - BUT do NOT rely on this for recovery logic
		//else {
		//	if (inBackground || isEnabled) return;
		//}

        Activity context = cordova.getActivity();
        Intent intent    = new Intent(context, ForegroundService.class);
        context.stopService(intent);
        
        isForegroundStarted = false;
        fireEvent(Event.DEACTIVATE, null);
    }

    /**
     * Fire event with some parameters inside the web view.
     */
    private void fireEvent (Event event, String params)
    {
        String eventName = event.name().toLowerCase();
        String js 		 = String.format("%s.fireEvent('%s',%s);", JS_NAMESPACE, eventName, params);

        cordova.getActivity().runOnUiThread(() -> webView.loadUrl("javascript:" + js));
    }
}
