package de.einfachhans.BackgroundMode;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class BackgroundMode extends CordovaPlugin {

    // Event types for callbacks
    private enum Event { ACTIVATE, DEACTIVATE, FAILURE }

    // Plugin namespace
    private static final String JS_NAMESPACE = "cordova.plugins.backgroundMode";

	// Permission request string. This constant exists only from Android 13 (API 33)
	private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

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

    // Pending enable request
    private boolean isEnablePending = false;

    private CallbackContext permissionCallback;

    // Flag indicates if the foreground services has been started
    private boolean isForegroundStarted = false;
    
	// Flag indicates if the app is in background or foreground
    private boolean inBackground = false;
    
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
			case "background":
				BackgroundModeExt.moveToBackground(cordova.getActivity());
				callback.success();
				break;
			case "foreground":
				moveToForeground();
				callback.success();
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
		if (permissionCallback != null) {
		    callback.error("Permission request already in progress");
		}
	    // Android < 13 → permission not required
	    else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
	        callback.success();
	    }
	    // Already granted → immediate success
	    else if (cordova.hasPermission(POST_NOTIFICATIONS)) {
	        // If there was a pending enable request, start foreground service
	        if (isEnablePending) {
	            isEnablePending = false;
	            startForeground();
	        }
			callback.success();
	    }
		// Not Granted - Request Permissions
		else {
		    // Store callback for later
		    permissionCallback = callback;
	
			// Tell Cordova: result will come later
		    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		    result.setKeepCallback(true);
		    callback.sendPluginResult(result);
		
		    // Request permissions
			cordova.requestPermissions(
				this,
				NOTIFICATION_PERMISSION_REQUEST_CODE,
				new String[] { POST_NOTIFICATIONS }
			);
		}
	}
	
    /**
     * Handle permission request result
     */
    @Override
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
	throws JSONException
	{
		// Not our request or callback already cleared
	    if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE || permissionCallback == null) return;
	
	    // Granted
	    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			// If there was a pending enable request, start foreground service
		    if (isEnablePending) {
		        isEnablePending = false;
			    startForeground();
		    }			
			permissionCallback.success();
		// Denied
		} else {
			isEnablePending = false;
	        if (!ActivityCompat.shouldShowRequestPermissionRationale(
	                cordova.getActivity(), POST_NOTIFICATIONS)) {
	            permissionCallback.error("Notification permission permanently denied. Please enable it in app settings.");
	        } else {
	            permissionCallback.error("Notification permission denied");
	        }
		}
		// Clear callback
		permissionCallback = null;
	}

	/**
	 * Enable background mode
	 */
	private void enableMode(CallbackContext callback)
	{
	    // Android 13+ check
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
	        !cordova.hasPermission(POST_NOTIFICATIONS)) {
	        
	        // Will enable after permission granted
	        isEnablePending = true;
	
	        // Request permission (will callback via onRequestPermissionResult)
	        requestNotificationPermission(callback);
	        return;
	    }
		// Permission already granted or not needed → start foreground service
	    startForeground();

		callback.success();
	}

    /**
     * Disable the background mode.
     */
    private void disableMode()
    {
		stopForeground();
    }

    /**
     * Called when the activity is no longer visible to the user.
     */
    @Override
    public void onStop ()
	{
        BackgroundModeExt.clearKeyguardFlags(cordova.getActivity());
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    public void onPause(boolean multitasking)
    {
		inBackground = true;
		BackgroundModeExt.clearKeyguardFlags(cordova.getActivity());
    }

    /**
     * Called when the activity will start interacting with the user.
     */
    @Override
    public void onResume (boolean multitasking)
    {
		inBackground = false;
    }

	/**
     * Move the application to foreground
     */
    private void moveToForeground()
    {
		if (!inBackground) return;
		
        if (!isForegroundStarted) {
			// Fallback if no Foreground Service
			BackgroundModeExt.moveToForeground(cordova.getActivity());
			return;
		}

		Activity context = cordova.getActivity();
		Intent intent    = new Intent(context, ForegroundService.class);
		intent.setAction(ForegroundService.ACTION_FOREGROUND);
		context.startService(intent);
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
