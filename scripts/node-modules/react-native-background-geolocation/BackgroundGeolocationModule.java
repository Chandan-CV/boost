package com.marianhello.bgloc.react;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.marianhello.bgloc.BackgroundGeolocationFacade;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.PluginDelegate;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.react.data.LocationMapper;
import com.marianhello.bgloc.react.headless.HeadlessTaskRunner;
import com.marianhello.logging.LogEntry;
import com.marianhello.logging.LoggerManager;

import org.json.JSONException;

import java.util.Collection;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

public class BackgroundGeolocationModule extends ReactContextBaseJavaModule implements LifecycleEventListener, PluginDelegate {

    public static final String LOCATION_EVENT = "location";
    public static final String STATIONARY_EVENT = "stationary";
    public static final String ACTIVITY_EVENT = "activity";

    public static final String FOREGROUND_EVENT = "foreground";
    public static final String BACKGROUND_EVENT = "background";
    public static final String AUTHORIZATION_EVENT = "authorization";

    public static final String START_EVENT = "start";
    public static final String STOP_EVENT = "stop";
    public static final String ABORT_REQUESTED_EVENT = "abort_requested";
    public static final String HTTP_AUTHORIZATION_EVENT = "http_authorization";
    public static final String ERROR_EVENT = "error";

    public static final String MY_PREFS_NAME = "MyPrefsFile";
    public static final String IS_AUTO_START_ENABLED = "isAutoStartEnabled";

    private static final int PERMISSIONS_REQUEST_CODE = 1;

    private BackgroundGeolocationFacade facade;
    private org.slf4j.Logger logger;
    ReactApplicationContext mContext;

    public static class ErrorMap {
        public static ReadableMap from(String message, int code) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", code);
            out.putString("message", message);
            return out;
        }

        public static ReadableMap from(String message, Throwable cause, int code) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", code);
            out.putString("message", message);
            out.putMap("cause", from(cause));
            return out;
        }

        public static ReadableMap from(PluginException e) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", e.getCode());
            out.putString("message", e.getMessage());
            if (e.getCause() != null) {
                out.putMap("cause", from(e.getCause()));
            }

            return out;
        }

        private static WritableMap from(Throwable e) {
            WritableMap out = Arguments.createMap();
            out.putString("message", e.getMessage());
            return out;
        }
    }

    public BackgroundGeolocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addLifecycleEventListener(this);
        mContext = reactContext;
        facade = new BackgroundGeolocationFacade(getContext(), this);
        logger = LoggerManager.getLogger(BackgroundGeolocationModule.class);
    }

    @Override
    public String getName() {
        return "BackgroundGeolocation";
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     */
    @Override
    public void onHostResume() {
        logger.info("App will be resumed");
        facade.resume();
        sendEvent(FOREGROUND_EVENT, null);
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    public void onHostPause() {
        logger.info("App will be paused");
        facade.pause();
        sendEvent(BACKGROUND_EVENT, null);
    }

    /**
     * The final call you receive before your activity is destroyed.
     * Checks to see if it should turn off
     */
    @Override
    public void onHostDestroy() {
        logger.info("Destroying plugin");
        facade.destroy();
//        facade = null;
    }

    private void runOnBackgroundThread(Runnable runnable) {
        // currently react-native has no other thread we can run on
        new Thread(runnable).start();
    }


    @ReactMethod
    public void start() {
        facade.start();
    }

    @ReactMethod
    public void stop() {
        facade.stop();
    }

    @ReactMethod
    public void switchMode(Integer mode, Callback success, Callback error) {
        facade.switchMode(mode);
    }

    @ReactMethod
    public void configure(final ReadableMap options, final Callback success, final Callback error) {
        String autoStartTitle = options.getString("enableAutoStartDialogueTitle");
        String autoStartText = options.getString("enableAutoStartDialogueText");
        String locationTitle = options.getString("enableLocationDialogueTitle");
        String locationText = options.getString("enableLocationDialogueText");

        checkGPSPermission(autoStartTitle, autoStartText, locationTitle, locationText);
        runOnBackgroundThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Config config = ConfigMapper.fromMap(options);
                    facade.configure(config);
                    success.invoke(true);
                } catch (JSONException e) {
                    logger.error("Configuration error: {}", e.getMessage());
                    error.invoke(ErrorMap.from("Configuration error", e, PluginException.CONFIGURE_ERROR));
                } catch (PluginException e) {
                    logger.error("Configuration error: {}", e.getMessage());
                    error.invoke(ErrorMap.from(e));
                }
            }
        });
    }

    @ReactMethod
    public void showLocationSettings() {
        BackgroundGeolocationFacade.showLocationSettings(getContext());
    }

    @ReactMethod
    public void showAppSettings() {
        BackgroundGeolocationFacade.showAppSettings(getContext());
    }

    @ReactMethod
    public void getStationaryLocation(Callback success, Callback error) {
        BackgroundLocation stationaryLocation = facade.getStationaryLocation();
        if (stationaryLocation != null) {
            success.invoke(LocationMapper.toWriteableMap(stationaryLocation));
        } else {
            success.invoke();
        }
    }

    @ReactMethod
    public void getLocations(final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                WritableArray locationsArray = Arguments.createArray();
                Collection<BackgroundLocation> locations = facade.getLocations();
                for (BackgroundLocation location : locations) {
                    locationsArray.pushMap(LocationMapper.toWriteableMapWithId(location));
                }
                success.invoke(locationsArray);
            }
        });
    }

    @ReactMethod
    public void getValidLocations(final Callback success, Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                WritableArray locationsArray = Arguments.createArray();
                Collection<BackgroundLocation> locations = facade.getValidLocations();
                for (BackgroundLocation location : locations) {
                    locationsArray.pushMap(LocationMapper.toWriteableMapWithId(location));
                }
                success.invoke(locationsArray);
            }
        });
    }

    @ReactMethod
    public void deleteLocation(final Integer locationId, final Callback success, Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                facade.deleteLocation(locationId.longValue());
                success.invoke(true);
            }
        });
    }

    @ReactMethod
    public void deleteAllLocations(final Callback success, Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                facade.deleteAllLocations();
                success.invoke(true);
            }
        });
    }

    @ReactMethod
    public void getCurrentLocation(final ReadableMap options, final Callback success, final Callback error) {
      runOnBackgroundThread(new Runnable() {
        public void run() {
          try {
              int timeout = options.hasKey("timeout") ? options.getInt("timeout") : Integer.MAX_VALUE;
              long maximumAge = options.hasKey("maximumAge") ? options.getInt("maximumAge") : Long.MAX_VALUE;
              boolean enableHighAccuracy = options.hasKey("enableHighAccuracy") && options.getBoolean("enableHighAccuracy");

              BackgroundLocation location = facade.getCurrentLocation(timeout, maximumAge, enableHighAccuracy);
              success.invoke(LocationMapper.toWriteableMap(location));
          } catch (PluginException e) {
              error.invoke(ErrorMap.from(e));
          }
        }
      });
    }

    @ReactMethod
    public void getConfig(final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                Config config = facade.getConfig();
                ReadableMap out = ConfigMapper.toMap(config);
                success.invoke(out);
            }
        });
    }

    @ReactMethod
    public void getLogEntries(final Integer limit, final Integer offset, final String minLevel, final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                WritableArray logEntriesArray = Arguments.createArray();
                Collection<LogEntry> logEntries = facade.getLogEntries(limit, offset, minLevel);
                for (LogEntry logEntry : logEntries) {
                    WritableMap out = Arguments.createMap();
                    out.putInt("id", logEntry.getId());
                    out.putInt("context", logEntry.getContext());
                    out.putString("level", logEntry.getLevel());
                    out.putString("message", logEntry.getMessage());
                    out.putString("timestamp", new Long(logEntry.getTimestamp()).toString());
                    out.putString("logger", logEntry.getLoggerName());
                    if (logEntry.hasStackTrace()) {
                        out.putString("stackTrace", logEntry.getStackTrace());
                    }

                    logEntriesArray.pushMap(out);
                }
                success.invoke(logEntriesArray);
            }
        });
    }

    @ReactMethod
    public void checkStatus(final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                try {
                    WritableMap out = Arguments.createMap();
                    out.putBoolean("isRunning", facade.isRunning());
                    out.putBoolean("hasPermissions", facade.hasPermissions()); //@Deprecated
                    out.putBoolean("locationServicesEnabled", facade.locationServicesEnabled());
                    out.putInt("authorization", getAuthorizationStatus());
                    success.invoke(out);
                } catch (PluginException e) {
                    logger.error("Location service checked failed: {}", e.getMessage());
                    error.invoke(ErrorMap.from(e));
                }
            }
        });
    }

    @ReactMethod
    public void registerHeadlessTask(Callback success, Callback error) {
        logger.debug("Registering headless task");
        facade.registerHeadlessTask(HeadlessTaskRunner.class.getName());
        success.invoke();
    }

    @ReactMethod
    public void forceSync(Callback success, Callback error) {
        facade.forceSync();
        success.invoke();
    }

    private void sendEvent(String eventName, Object params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendError(PluginException error) {
        WritableMap out = Arguments.createMap();
        out.putInt("code", error.getCode());
        out.putString("message", error.getMessage());

        sendEvent(ERROR_EVENT, out);
    }

    private void sendError(int code, String message) {
        WritableMap out = Arguments.createMap();
        out.putInt("code", code);
        out.putString("message", message);

        sendEvent(ERROR_EVENT, out);
    }

    public int getAuthorizationStatus() {
        return facade.getAuthorizationStatus();
    }

    public Context getContext() {
        return getReactApplicationContext().getBaseContext();
    }

    @Override
    public void onAuthorizationChanged(int authStatus) {
        sendEvent(AUTHORIZATION_EVENT, authStatus);
    }

    @Override
    public void onLocationChanged(BackgroundLocation location) {
        sendEvent(LOCATION_EVENT, LocationMapper.toWriteableMapWithId(location));
    }

    @Override
    public void onStationaryChanged(BackgroundLocation location) {
        sendEvent(STATIONARY_EVENT, LocationMapper.toWriteableMapWithId(location));
    }

    @Override
    public void onActivityChanged(BackgroundActivity activity) {
        WritableMap out = Arguments.createMap();
        out.putInt("confidence", activity.getConfidence());
        out.putString("type", BackgroundActivity.getActivityString(activity.getType()));
        sendEvent(ACTIVITY_EVENT, out);
    }

    @Override
    public void onServiceStatusChanged(int status) {
        switch (status) {
            case BackgroundGeolocationFacade.SERVICE_STARTED:
                sendEvent(START_EVENT, null);
                return;
            case BackgroundGeolocationFacade.SERVICE_STOPPED:
                sendEvent(STOP_EVENT, null);
                return;
        }
    }

    @Override
    public void onError(PluginException error) {
        sendError(error);
    }

    @Override
    public void onAbortRequested() {
        sendEvent(ABORT_REQUESTED_EVENT, null);
    }

    @Override
    public void onHttpAuthorization() {
        sendEvent(HTTP_AUTHORIZATION_EVENT, null);
    }

    public void showSettingsAlert(final String locationDialogueTitle, final String locationDialogueText) {


        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                AlertDialog.Builder alertDialog = new AlertDialog.Builder(getCurrentActivity());

                // Setting Dialog Title
                alertDialog.setTitle(locationDialogueTitle);
                // Setting Dialog Message
                alertDialog.setMessage(locationDialogueText);
                alertDialog.setCancelable(false);

                // On pressing Settings button
                alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        getCurrentActivity().startActivity(intent);
                    }
                });

                // Showing Alert Message
                alertDialog.show();
            }
        });
    }

    private void checkGPSPermission(String autoStartDialogueTitle, String autoStartDialogueText, String locationDialogueTitle, String locationDialogueText) {

        android.location.LocationManager locationManager = (android.location.LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsStatus = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        Log.e("gpsStatus", gpsStatus + "");

        if (!gpsStatus) {
            showSettingsAlert(locationDialogueTitle, locationDialogueText);
        }

        SharedPreferences pref = mContext.getSharedPreferences("MyPrefsFile", MODE_PRIVATE);
        boolean isAutoStartEnabled=pref.getBoolean(IS_AUTO_START_ENABLED, false);

        Log.e("isAutoStartEnabled===", IS_AUTO_START_ENABLED + "");

        if(true) {
            addAutoStartup(autoStartDialogueTitle, autoStartDialogueText);
        }
    }

    private void addAutoStartup(final String autoStartDialogueTitle, final String autoStartDialogueText) {

        try {
            final Intent intent = new Intent();
            String manufacturer = android.os.Build.MANUFACTURER;
            if ("xiaomi".equalsIgnoreCase(manufacturer)) {
                intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } else if ("oppo".equalsIgnoreCase(manufacturer)) {
                intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            } else if ("vivo".equalsIgnoreCase(manufacturer)) {
                intent.setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            } else if ("Letv".equalsIgnoreCase(manufacturer)) {
                intent.setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"));
            } else if ("Honor".equalsIgnoreCase(manufacturer)) {
                intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
            }

            List<ResolveInfo> list = getCurrentActivity().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if  (true) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {

                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getCurrentActivity());

                        // Setting Dialog Title
                        alertDialog.setTitle(autoStartDialogueTitle);
                        // Setting Dialog Message
                        alertDialog.setMessage(autoStartDialogueText);
                        alertDialog.setCancelable(false);

                        // On pressing Settings button
                        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                                SharedPreferences.Editor editor = mContext.getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
                                editor.putBoolean(IS_AUTO_START_ENABLED, true);
                                editor.apply();

                                getCurrentActivity().startActivity(intent);
                            }
                        });

                        alertDialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });

                        // Showing Alert Message
                        alertDialog.show();
                    }
                });
            }
        } catch (Exception e) {
            Log.e("exc" , String.valueOf(e));
        }
    }

}
