# Cordova Advanced Background Mode Plugin (OutSystems Compatible)

![Maintenance](https://img.shields.io/maintenance/yes/2025)
[![npm version](https://badge.fury.io/js/cordova-plugin-advanced-background-mode.svg)](https://badge.fury.io/js/cordova-plugin-advanced-background-mode)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Cordova plugin optimized for **OutSystems MABS 11/12** that enables infinite background execution for mobile applications.

## 🌟 Features

- ✅ **MABS 11/12 Compatible** - Fully tested with OutSystems Mobile Apps Build Service
- ✅ **Android 14+ Support** - Updated for latest Android versions with proper foreground service types
- ✅ **AndroidX Ready** - Modern Android support library integration
- ✅ **iOS 13+ Support** - Background task scheduling for modern iOS
- ✅ **Notification Permissions** - Android 13+ (API 33) permission handling
- ✅ **Battery Optimization** - Tools to request battery optimization exemptions
- ✅ **Customizable Notifications** - Full control over background service notifications

## 📋 Table of Contents

- [What This Plugin Does](#what-this-plugin-does)
- [Store Compliance Warning](#store-compliance-warning)
- [Supported Platforms](#supported-platforms)
- [Installation](#installation)
  - [Standard Cordova](#standard-cordova)
  - [OutSystems MABS](#outsystems-mabs)
- [Usage](#usage)
  - [Basic Setup](#basic-setup)
  - [Request Permissions (Android 13+)](#request-permissions-android-13)
  - [Enable/Disable Background Mode](#enabledisable-background-mode)
  - [Listen for Events](#listen-for-events)
  - [Configure Notifications](#configure-notifications)
- [Android-Specific Features](#android-specific-features)
- [iOS-Specific Features](#ios-specific-features)
- [API Reference](#api-reference)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## 🔍 What This Plugin Does

This plugin prevents your Cordova/OutSystems app from being paused when it enters the background. It achieves this by:

**Android:**
- Creating a foreground service with a persistent notification
- Acquiring wake locks to prevent CPU sleep
- Managing battery optimization settings

**iOS:**
- Using background audio session to keep app alive
- Scheduling background tasks (iOS 13+)
- Managing app lifecycle events

**Browser:**
- No-op implementation for testing

## ⚠️ Store Compliance Warning

**IMPORTANT:** Infinite background tasks are not officially supported by most mobile operating systems and may not be compliant with app store policies.

- ❌ Google Play may reject apps with background services that don't fit specific use cases
- ❌ Apple App Store requires legitimate background task justification
- ✅ Use only if your app has a valid reason (e.g., tracking, monitoring, real-time updates)

**A successful app store submission is not guaranteed. Use at your own risk!**

## 📱 Supported Platforms

| Platform | Minimum Version | Status |
|----------|----------------|---------|
| Android | API 23 (Android 6.0) | ✅ Fully Supported |
| iOS | iOS 13.0+ | ✅ Fully Supported |
| Browser | All versions | ✅ Mock Implementation |

## 📦 Installation

### Standard Cordova

```bash
cordova plugin add cordova-plugin-advanced-background-mode --variable ANDROIDXENABLED=true
```

### OutSystems MABS

Add this to your **Extensibility Configurations**:

```json
{
    "preferences": {
        "global": [
            {
                "name": "AndroidXEnabled",
                "value": "true"
            },
            {
                "name": "GradlePluginKotlinEnabled",
                "value": "true"
            },
            {
                "name": "android-minSdkVersion",
                "value": "23"
            },
            {
                "name": "android-targetSdkVersion",
                "value": "34"
            },
            {
                "name": "deployment-target",
                "value": "13.0"
            }
        ]
    },
    "plugin": {
        "url": "https://github.com/fafvaz/cordova-plugin-advanced-background-mode-OS",
        "variables": [
            {
                "name": "ANDROIDXENABLED",
                "value": "true"
            }
        ]
    }
}
```

**Required Android Permissions** (automatically added):
- `WAKE_LOCK` - Keep CPU awake
- `FOREGROUND_SERVICE` - Run foreground service
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Request battery exemption
- `POST_NOTIFICATIONS` (Android 13+) - Show notifications

**Required iOS Background Modes** (automatically added):
- `processing` - Background processing
- `fetch` - Background fetch
- `audio` - Background audio session

## 🚀 Usage

### Basic Setup

```javascript
document.addEventListener('deviceready', function () {
    // Check if plugin is available
    if (cordova.plugins.backgroundMode) {
        
        // Request notification permissions first (Android 13+)
        cordova.plugins.backgroundMode.requestPermissions(
            function() {
                console.log('Permissions granted');
                
                // Enable background mode
                cordova.plugins.backgroundMode.enable();
            },
            function() {
                console.log('Permissions denied');
            }
        );
    }
}, false);
```

### Request Permissions (Android 13+)

**CRITICAL:** On Android 13+, you must request notification permissions before enabling background mode!

```javascript
cordova.plugins.backgroundMode.requestPermissions(
    function() {
        // Permission granted - safe to enable
        cordova.plugins.backgroundMode.enable();
    },
    function(error) {
        // Permission denied - inform user
        alert('Background notifications are required for this feature');
    }
);
```

### Enable/Disable Background Mode

```javascript
// Enable background mode
cordova.plugins.backgroundMode.enable();

// Disable background mode
cordova.plugins.backgroundMode.disable();

// Toggle
cordova.plugins.backgroundMode.setEnabled(true); // or false

// Check status
if (cordova.plugins.backgroundMode.isEnabled()) {
    console.log('Background mode is enabled');
}

if (cordova.plugins.backgroundMode.isActive()) {
    console.log('App is currently in background');
}
```

### Listen for Events

```javascript
// App entered background
cordova.plugins.backgroundMode.on('activate', function() {
    console.log('App is now in background');
    // Start your background tasks here
});

// App returned to foreground
cordova.plugins.backgroundMode.on('deactivate', function() {
    console.log('App is now in foreground');
    // Stop background tasks
});

// Background mode enabled
cordova.plugins.backgroundMode.on('enable', function() {
    console.log('Background mode enabled');
});

// Background mode disabled
cordova.plugins.backgroundMode.on('disable', function() {
    console.log('Background mode disabled');
});

// Error occurred
cordova.plugins.backgroundMode.on('failure', function(errorMsg) {
    console.error('Background mode failed:', errorMsg);
});

// Remove event listener
cordova.plugins.backgroundMode.un('activate', callbackFunction);
```

### Configure Notifications

Customize the notification shown when app is in background:

```javascript
// Set default notification settings
cordova.plugins.backgroundMode.setDefaults({
    title: 'MyApp Running',
    text: 'Processing data in background...',
    icon: 'ic_launcher', // Name of icon in res/drawable
    color: 'F14F4D', // Hex color (without #)
    resume: true, // Tap notification to resume app
    bigText: false, // Use big text style
    silent: false // Don't show notification (not recommended)
});

// Update notification while running
cordova.plugins.backgroundMode.configure({
    title: 'Updated Title',
    text: 'New status message'
});

// Silent mode (no notification) - NOT RECOMMENDED
// Android may kill your app without notification!
cordova.plugins.backgroundMode.setDefaults({ silent: true });
```

## 📱 Android-Specific Features

### Move to Background/Foreground

```javascript
// Programmatically move app to background
cordova.plugins.backgroundMode.moveToBackground();

// Bring app to foreground
cordova.plugins.backgroundMode.moveToForeground();
```

### Override Back Button

Make back button minimize app instead of closing it:

```javascript
cordova.plugins.backgroundMode.overrideBackButton();
```

### Task List Management

```javascript
// Hide app from recent apps list
cordova.plugins.backgroundMode.excludeFromTaskList();

// Show app in recent apps list
cordova.plugins.backgroundMode.includeToTaskList();
```

### Screen Status Detection

```javascript
cordova.plugins.backgroundMode.isScreenOff(function(isOff) {
    if (isOff) {
        console.log('Screen is off');
    } else {
        console.log('Screen is on');
    }
});
```

### Wake Up and Unlock

```javascript
// Turn screen on
cordova.plugins.backgroundMode.wakeUp();

// Turn screen on and show app (even if locked)
cordova.plugins.backgroundMode.unlock();
```

### Disable Battery Optimizations

Request user to exempt your app from battery optimizations:

```javascript
cordova.plugins.backgroundMode.disableBatteryOptimizations();
// This opens system settings - user must manually approve
```

### Disable WebView Optimizations

Some WebView optimizations can interfere with background execution:

```javascript
cordova.plugins.backgroundMode.on('activate', function() {
    cordova.plugins.backgroundMode.disableWebViewOptimizations(); 
});
```

**⚠️ Warning:** This increases battery consumption!

### Open Auto-Start Settings

Some manufacturers (Xiaomi, Huawei, Oppo, etc.) have auto-start restrictions:

```javascript
// Show dialog first, then open settings
cordova.plugins.backgroundMode.openAppStartSettings({
    title: 'Enable Auto-Start',
    text: 'Please allow this app to start automatically for background features to work.'
});

// Open settings directly without dialog
cordova.plugins.backgroundMode.openAppStartSettings(false);
```

## 🍎 iOS-Specific Features

### Background Audio

iOS keeps apps alive using a silent audio loop. The plugin handles this automatically.

**Required Background Mode:** Already configured in plugin.xml
```xml
<array>
    <string>processing</string>
    <string>fetch</string>
    <string>audio</string>
</array>
```

### Background Task Scheduling (iOS 13+)

The plugin automatically schedules background tasks on iOS 13+. No additional code needed.

### Audio Session Interruptions

The plugin automatically handles interruptions (phone calls, etc.) and restarts the background audio.

## 📚 API Reference

### Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `enable()` | - | void | Enable background mode |
| `disable()` | - | void | Disable background mode |
| `setEnabled(enable)` | boolean | void | Enable or disable |
| `requestPermissions(success, error)` | callbacks | void | Request Android 13+ permissions |
| `isEnabled()` | - | boolean | Check if enabled |
| `isActive()` | - | boolean | Check if app is in background |
| `configure(options)` | object | void | Update notification |
| `setDefaults(options)` | object | void | Set default notification |
| `on(event, callback, scope)` | string, function, object | void | Add event listener |
| `un(event, callback)` | string, function | void | Remove event listener |

**Android Only:**
- `moveToBackground()` - Minimize app
- `moveToForeground()` - Restore app
- `excludeFromTaskList()` - Hide from recents
- `includeToTaskList()` - Show in recents
- `isScreenOff(callback)` - Check screen state
- `wakeUp()` - Turn screen on
- `unlock()` - Turn screen on and unlock
- `overrideBackButton()` - Back button minimizes
- `disableBatteryOptimizations()` - Open settings
- `disableWebViewOptimizations()` - Increase performance
- `openAppStartSettings(options)` - Open manufacturer settings

### Events

| Event | Description |
|-------|-------------|
| `activate` | App entered background |
| `deactivate` | App returned to foreground |
| `enable` | Background mode enabled |
| `disable` | Background mode disabled |
| `failure` | Error occurred |

### Notification Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `title` | string | 'App is running in background' | Notification title |
| `text` | string | 'Doing heavy tasks.' | Notification text |
| `icon` | string | 'icon' | Icon name (from res/drawable) |
| `color` | string | undefined | Notification color (hex, no #) |
| `resume` | boolean | true | Tap to resume app |
| `silent` | boolean | false | Don't show notification (not recommended) |
| `bigText` | boolean | false | Use big text style |
| `silent` | boolean | false | Don't show notification |

## 🔧 Troubleshooting

### Android Issues

**App is killed in background**
- Request battery optimization exemption: `disableBatteryOptimizations()`
- Don't use `silent: true` - Android needs the notification
- Check manufacturer-specific settings: `openAppStartSettings()`

**Notification not showing on Android 13+**
- Call `requestPermissions()` before `enable()`
- Check if user denied permission in system settings

**WebView stops working in background**
- Call `disableWebViewOptimizations()` in the `activate` event
- Note: This increases battery usage

**Build fails with ANDROIDXENABLED error**
- Make sure to pass the variable in plugin installation (see Installation section)

### iOS Issues

**Music stops when app goes to background**
- This is expected - the plugin uses audio session
- Configure audio session in iOS settings if needed

**App doesn't stay alive on simulator**
- iOS Simulator doesn't accurately simulate background behavior
- Always test on real devices

**Background task not working on iOS 13+**
- Ensure `processing` background mode is enabled in Info.plist (automatically added by plugin)
- Check iOS battery settings haven't disabled background refresh for your app

### General Issues

**Plugin not found**
- Verify plugin is installed: `cordova plugin ls`
- Ensure `deviceready` event has fired before accessing plugin

**Background mode not activating**
- Check if enabled: `cordova.plugins.backgroundMode.isEnabled()`
- Listen for `failure` event to catch errors
- Check device logs for system-level errors

## 🤝 Contributing

Contributions are welcome! This is a community-maintained fork optimized for OutSystems MABS.

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -am 'Add some feature'`
4. Push to the branch: `git push origin feature/my-feature`
5. Submit a Pull Request

### Areas We Need Help

- [ ] Testing on various Android manufacturers (Xiaomi, Oppo, Samsung, etc.)
- [ ] iOS 17+ testing and optimization
- [ ] Documentation improvements
- [ ] Example apps and use cases
- [ ] Ionic/Capacitor wrapper

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

**Latest (v2.0.0):**
- ✅ MABS 11/12 compatibility
- ✅ Android 14+ foreground service types
- ✅ Android 13+ notification permissions
- ✅ iOS 13+ background task scheduling
- ✅ AndroidX migration
- ✅ Modern build tools support

## 📄 License

Apache License 2.0

```
Copyright 2013-2025 appPlant GmbH & Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🙏 Credits

This plugin is a fork of [cordova-plugin-background-mode](https://github.com/katzer/cordova-plugin-background-mode) by Katzer, updated and maintained for modern Cordova versions and OutSystems MABS compatibility.

**Maintained by:** Community Contributors  
**Original Author:** Katzer (appPlant GmbH)  
**OutSystems Optimization:** fafvaz

## 💬 Support

- **Issues:** [GitHub Issues](https://github.com/fafvaz/cordova-plugin-advanced-background-mode-OS/issues)
- **Discussions:** [GitHub Discussions](https://github.com/fafvaz/cordova-plugin-advanced-background-mode-OS/discussions)
- **OutSystems Community:** [OutSystems Forums](https://www.outsystems.com/forums/)

---

**⭐ If this plugin helps your project, please consider starring the repository!**
