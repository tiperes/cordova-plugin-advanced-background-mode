/*
    Copyright 2013-2017 appPlant GmbH

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
*/

var exec    = require('cordova/exec'),
    channel = require('cordova/channel');

/**
 * @private
 *
 * Initialize the plugin.
 *
 * Method should be called after the 'deviceready' event
 * but before the event listeners will be called.
 *
 * @return [ Void ]
 */
exports._pluginInitialize = function()
{
    this._isAndroid = device.platform.match(/^android|amazon/i) !== null;	
	this._isEnabled = false;
	this._isActive = false;
		
	if (this._isAndroid) {		
		this.on('activate', function() {
			exports._isActive = true;
			// reset runtime settings to defaults
			exports._settings = exports._mergeObjects({}, exports._defaults);
		});
		this.on('deactivate', function() {
			exports._isActive = false;
			// reset runtime settings to unset
			exports._settings = {};
		});
	}
	else if (device.platform == 'browser') {
        this._isEnabled = true;
        this._isActive = true;
    }
};

/**
 * If the mode is enabled or disabled.
 *
 * @return [ Boolean ]
 */
exports.isEnabled = function()
{
    return this._isEnabled !== false;
};

/**
 * If the mode is active.
 *
 * @return [ Boolean ]
 */
exports.isActive = function()
{
    return this._isActive !== false;
};

/**
 * @private
 *
 * Default values of all available options.
 */
exports._defaults =
{
    title:   undefined,
    text:    undefined,
    bigText: false,
    resume:  true,
    color:   undefined,
    icon:    undefined
};

/**
 * List of all available options with their default value.
 *
 * @return [ Object ]
 */
exports.getDefaults = function()
{
    return this._defaults;
};

/**
 * Overwrite the default settings.
 *
 * @param [ Object ] overrides Dict of options to be overridden.
 *
 * @return [ Void ]
 */
exports.setDefaults = function (overrides)
{
	for (var key in this._defaults) {
		if (overrides.hasOwnProperty(key)) {
			this._defaults[key] = overrides[key];
		}
	}
	
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundMode', 'configure', [this._defaults, false]);
    }
};

/**
 * List of current runtime settings
 *
 * @return [ Object ]
 */
exports.getSettings = function()
{
	return this._settings || {};
};

/**
 * Configures the notification settings for Android.
 * Will be merged with the defaults.
 *
 * @param [ Object ] options Dict of options to be overridden.
 *
 * @return [ Void ]
 */
exports.configure = function (options)
{
    if (this._isAndroid) {
		if (!this._isActive) {
            console.log('Background Mode is not active, configuration skipped...');
            return;
        }
		
		this._mergeObjects(options, this._settings);
        this._mergeObjects(options, this._defaults);
        this._settings = options;
		
        cordova.exec(null, null, 'BackgroundMode', 'configure', [options, true]);
    }
};

/**
 * Activates the background mode. When activated the application
 * will be prevented from going to sleep while in background
 * for the next time.
 *
 * @param [ Function ] success Callback on success
 * @param [ Function ] error Callback on error
 *
 * @return [ Void ]
 */
exports.enable = function(success, error)
{
    if (this._isEnabled) {
        success();
        return;
    }

    var onSuccess = function() {
        this._isEnabled = true;
        this.fireEvent('enable');
        success();
    };
    var onError = function(errorMsg) {
        error(errorMsg);
    };

    cordova.exec(onSuccess, onError, 'BackgroundMode', 'enable', []);
};

/**
 * Deactivates the background mode. When deactivated the application
 * will not stay awake while in background.
 *
 * @param [ Function ] success Callback on success
 * @param [ Function ] error Callback on error
 *
 * @return [ Void ]
 */
exports.disable = function(success, error)
{
    if (!this._isEnabled) {
        success();
        return;
    }

    var onSuccess = function() {
        this._isEnabled = false;
        this.fireEvent('disable');
        success();
    };
    var onError = function(errorMsg) {
        error(errorMsg);
    };

    cordova.exec(onSuccess, onError, 'BackgroundMode', 'disable', []);
};

/**
 * Enable or disable the background mode.
 *
 * @param [ Bool ] enable The status to set for.
 * @param [ Function ] success Callback on success
 * @param [ Function ] error Callback on error
 *
 * @return [ Void ]
 */
exports.setEnabled = function (enable, success, error)
{
    if (enable) {
        this.enable(success, error);
    } else {
        this.disable(success, error);
    }
};

/**
 * Enable GPS-tracking in background (Android).
 *
 * @return [ Void ]
 */
exports.disableWebViewOptimizations = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'webview', []);
    }
};

/**
 * Disables battery optimazation mode for the app.
 *
 * @return [ Void ]
 */
exports.disableBatteryOptimizations = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'battery', []);
    }
};

/**
 * Opens the system settings dialog where the user can tweak or turn off any
 * custom app start settings added by the manufacturer if available.
 *
 * @param [ Object|Bool ] options Set to false if you dont want to display an
 *                                alert dialog first.
 *
 * @return [ Void ]
 */
exports.openAppStartSettings = function (options)
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'appstart', [options]);
    }
};

/**
 * Move app to background (Android only).
 *
 * @return [ Void ]
 */
exports.moveToBackground = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'background', []);
    }
};

/**
 * Move app to foreground when in background (Android only).
 *
 * @return [ Void ]
 */
exports.moveToForeground = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'foreground', []);
    }
};

/**
 * Exclude the app from the recent tasks list (Android only).
 *
 * @return [ Void ]
 */
exports.excludeFromTaskList = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'tasklistExclude', []);
    }
};

/**
 * Include the app back to the recent tasks list (Android only).
 *
 * @return [ Void ]
 */
exports.includeToTaskList = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'tasklistInclude', []);
    }
};

/**
 * Override the back button on Android to go to background
 * instead of closing the app.
 *
 * @return [ Void ]
 */
exports.overrideBackButton = function()
{
    if (this._isAndroid) {
		document.addEventListener('backbutton', exports.moveToBackground, false);
    }
};

/**
 * If the screen is off.
 *
 * @param [ Function ] fn Callback function to invoke with boolean arg.
 *
 * @return [ Void ]
 */
exports.isScreenOff = function (fn)
{
    if (this._isAndroid) {
        cordova.exec(fn, null, 'BackgroundModeExt', 'dimmed', []);
    }
    else {
        fn(undefined);
    }
};

/**
 * Wake up the device.
 *
 * @return [ Void ]
 */
exports.wakeUp = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'wakeup', []);
    }
};

/**
 * Wake up and unlock the device.
 *
 * @return [ Void ]
 */
exports.unlock = function()
{
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'unlock', []);
    }
};

/**
 * Request notification permissions (Android 13+)
 *
 * @param [ Function ] success Callback on success
 * @param [ Function ] error Callback on error
 * 
 * @return [ Void ]
 */
exports.requestPermissions = function(success, error)
{
    if (this._isAndroid) {
        cordova.exec(success, error, 'BackgroundMode', 'requestPermissions', []);
    } else {
        // others dont need this
        if (success) success();
    }
};

/**
 * @private
 *
 * Merge settings with default values.
 *
 * @param [ Object ] options The custom options.
 * @param [ Object ] toMergeIn The options to merge in.
 *
 * @return [ Object ] Default values merged with custom values.
 */
exports._mergeObjects = function (options, toMergeIn)
{
    for (var key in toMergeIn) {
        if (!options.hasOwnProperty(key)) {
            options[key] = toMergeIn[key];
        }
    }

    return options;
};

exports._listener = {};

/**
 * Fire event with given arguments.
 *
 * @param [ String ] event The event's name.
 * @param [ Array<Object> ] The callback's arguments.
 *
 * @return [ Void ]
 */
exports.fireEvent = function (event)
{
    var args     = Array.apply(null, arguments).slice(1),
        listener = this._listener[event];

    if (!listener)
        return;

    for (var i = 0; i < listener.length; i++) {
        var fn    = listener[i][0],
            scope = listener[i][1];

        fn.apply(scope, args);
    }
};

/**
 * Register callback for given event.
 *
 * @param [ String ] event The event's name.
 * @param [ Function ] callback The function to be exec as callback.
 * @param [ Object ] scope The callback function's scope.
 *
 * @return [ Void ]
 */
exports.on = function (event, callback, scope)
{
    if (typeof callback !== "function") return;

    if (!this._listener[event]) {
        this._listener[event] = [];
    }

    var item = [callback, scope || window];

    this._listener[event].push(item);
};

/**
 * Unregister callback for given event.
 *
 * @param [ String ] event The event's name.
 * @param [ Function ] callback The function to be exec as callback.
 *
 * @return [ Void ]
 */
exports.off = function (event, callback)
{
    var listener = this._listener[event];

    if (!listener) return;

    for (var i = 0; i < listener.length; i++) {
        var fn = listener[i][0];

        if (fn == callback) {
            listener.splice(i, 1);
            break;
        }
    }
};

// Called before 'deviceready' listener will be called
channel.onCordovaReady.subscribe(function()
{
    channel.onCordovaInfoReady.subscribe(function() {
        exports._pluginInitialize();
    });
});

// Called after 'deviceready' event
channel.deviceready.subscribe(function()
{
    if (exports._isEnabled) {
        exports.fireEvent('enable');
    }

    if (exports._isActive) {
        exports.fireEvent('activate');
    }
});
