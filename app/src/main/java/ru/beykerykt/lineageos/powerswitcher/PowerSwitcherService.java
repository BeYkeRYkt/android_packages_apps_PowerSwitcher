package ru.beykerykt.lineageos.powerswitcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import lineageos.power.PerformanceManager;

import static ru.beykerykt.lineageos.powerswitcher.Constants.APP_PREFERENCES_DEFAULT_POWER_PROFILE;
import static ru.beykerykt.lineageos.powerswitcher.Constants.NOTIFICATION_CHANNEL_ID;
import static ru.beykerykt.lineageos.powerswitcher.Constants.NOTIFICATION_NOTIFY_ID;
import static ru.beykerykt.lineageos.powerswitcher.Constants.getAppPrefs;

public class PowerSwitcherService extends Service {

    public String TAG = "PowerSwitcherService";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_PACKAGE_FULLY_REMOVED)) {
                String packageName = intent.getData().getSchemeSpecificPart();
                mAppPerfManager.removeAppProfile(packageName);
                mAppPerfManager.saveAppProfiles(mContext);
            }
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            boolean updNotif = false;

            if (mAppPerfManager == null) {
                mAppPerfManager = PerfProfilesManager.getInstance();
                if (mAppPerfManager.getAppProfiles().isEmpty()) {
                    mAppPerfManager.restoreAppProfiles(getApplicationContext());
                }
            }

            // power save mode
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean powerSaveMode = powerManager.isPowerSaveMode();
            if (!powerSaveMode) {
                if (mPowerSaveMode) {
                    // restore default profile
                    mPerf.setPowerProfile(mDefaultProfileId);
                }
            }
            mPowerSaveMode = powerSaveMode;

            // application
            String currentAppPkg = getForegroundAppPackage();
            if (currentAppPkg != null) {
                updateCurrentAppProfile(currentAppPkg);
            }

            // profiles
            if (!mPowerSaveMode && (mCurrentProfileId != mPerf.getActivePowerProfile().getId())) {
                mCurrentProfileId = mPerf.getActivePowerProfile().getId();
                PerfProfilesQSTile.updateTile();
                if (!isAppProfileApplied()) {
                    setDefaultPowerProfile(mPerf.getActivePowerProfile().getId());
                }
                updNotif = true;
            }

            // Notification
            if (updNotif) {
                Log.i(TAG, "Updating notification");
                updateNotification();
            }

            mHandler.postDelayed(this, repeat_time);
        }
    };

    private PerfProfilesManager mAppPerfManager;

    // Lineage power profiles
    private PerformanceManager mPerf;
    private int mDefaultProfileId = -1;
    private int mCurrentProfileId = -1;
    private String mCurrentAppPkg = "";
    private boolean mProfileApplied = false;
    private boolean mPowerSaveMode = false;

    private Handler mHandler = null;
    private static final int repeat_time = 250;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Starting service.");

        // register intent
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intentFilter.addDataScheme("package");
        registerReceiver(mReceiver, intentFilter);

        HandlerThread handlerThread = new HandlerThread("PowerSwitcher-Backend");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mPerf = PerformanceManager.getInstance(this);

        // check lineage power service
        if (!checkLineagePowerService()) {
            Toast.makeText(this, "PerformanceManager is not available!", Toast.LENGTH_SHORT);
            stopSelf();
            return;
        }

        mAppPerfManager = PerfProfilesManager.getInstance();
        if (mAppPerfManager.getAppProfiles().isEmpty()) {
            mAppPerfManager.restoreAppProfiles(getApplicationContext());
        }

        // set default profile first
        int profileId = getAppPrefs(this).getInt(APP_PREFERENCES_DEFAULT_POWER_PROFILE, mPerf.getActivePowerProfile().getId());
        if (mPerf.getPowerProfile(profileId) == null) {
            profileId = mPerf.getActivePowerProfile().getId();
        }
        setDefaultPowerProfile(profileId);

        // apply default profile if for example the device was rebooted
        mPerf.setPowerProfile(mDefaultProfileId);
        mCurrentProfileId = mPerf.getActivePowerProfile().getId();

        // add 'repeater'
        mHandler.postDelayed(mRunnable, repeat_time);

        // notification
        createNotificationChannel();
        Notification notification = getNotification();
        startForeground(NOTIFICATION_NOTIFY_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        mHandler.removeCallbacks(mRunnable);
        mHandler.getLooper().quitSafely();
        mHandler.getLooper().getThread().interrupt();
        mPerf.setPowerProfile(mDefaultProfileId); // restore to default profile
        stopForeground(true);
        Log.i(TAG, "Service is down.");
    }

    private boolean checkLineagePowerService() {
        if (mPerf.getPowerProfile() == -1) {
            Log.e(TAG, "PerformanceManager is not available!");
            return false;
        }
        return true;
    }

    public boolean isAppProfileApplied() {
        return mProfileApplied;
    }

    public void setDefaultPowerProfile(int profileId) {
        if (profileId != mDefaultProfileId) {
            SharedPreferences.Editor e = getAppPrefs(this).edit();
            e.putInt(APP_PREFERENCES_DEFAULT_POWER_PROFILE, profileId);
            e.apply();
        }
        mDefaultProfileId = profileId;
    }

    public void updateCurrentAppProfile(String currentAppPkg) {
        if (!isAppProfileApplied()) {
            if (!mPowerSaveMode && mAppPerfManager.availableAppProfile(currentAppPkg)) {
                mCurrentAppPkg = currentAppPkg;
                Log.i(TAG, "Target foreground app has been detected: " + mCurrentAppPkg);
                Log.i(TAG, "Applying power profile for " + mCurrentAppPkg);
                int profileId = mAppPerfManager.getProfileFromAppPackage(mCurrentAppPkg);
                if (mPerf.getPowerProfile(profileId) == null) {
                    profileId = mDefaultProfileId;
                }
                mPerf.setPowerProfile(profileId);
                mProfileApplied = true;
            }
        } else {
            if (!mCurrentAppPkg.equals(currentAppPkg)) {
                if (!mPowerSaveMode && mAppPerfManager.availableAppProfile(currentAppPkg)) {
                    mCurrentAppPkg = currentAppPkg;
                    Log.i(TAG, "Target foreground app has been changed: " + mCurrentAppPkg);
                    Log.i(TAG, "Switching power profile for " + mCurrentAppPkg);
                    int profileId = mAppPerfManager.getProfileFromAppPackage(mCurrentAppPkg);
                    if (mPerf.getPowerProfile(profileId) == null) {
                        profileId = mDefaultProfileId;
                    }
                    mPerf.setPowerProfile(profileId);
                    mProfileApplied = true;
                } else {
                    Log.i(TAG, "Return to default power profile for " + currentAppPkg);
                    mCurrentAppPkg = "";
                    if (!mPowerSaveMode) {
                        mPerf.setPowerProfile(mDefaultProfileId);
                    }
                    mProfileApplied = false;
                }
            }
        }
    }

    /**
     * Get foreground app package name
     */
    private String getForegroundAppPackage() {
        String foregroundApp = null;

        UsageStatsManager mUsageStatsManager = (UsageStatsManager) getSystemService(Service.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();

        UsageEvents usageEvents = mUsageStatsManager.queryEvents(time - 1000 * 2, time);
        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.getPackageName();
            }
        }
        return foregroundApp;
    }

    /**
     * Notification channel
     */
    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PowerSwitcher",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    /**
     * Main body for notifications
     */
    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, SplashActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_current_profile_text) + ": " + mPerf.getPowerProfile(mCurrentProfileId).getName())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_current_profile_text) + ": " + mPerf.getPowerProfile(mCurrentProfileId).getName()))
                .setSmallIcon(R.drawable.ic_profile_balanced)
                .setContentIntent(pendingIntent)
                .build();
        return notification;
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_NOTIFY_ID, getNotification());
    }
}
