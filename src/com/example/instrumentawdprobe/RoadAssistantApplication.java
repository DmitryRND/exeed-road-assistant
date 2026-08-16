package com.example.instrumentawdprobe;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.yandex.mapkit.MapKitFactory;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/** Initializes process-local access to the vendor RPC API before activities are loaded. */
public final class RoadAssistantApplication extends Application {
    private static final String TAG = "InstrumentAwdProbe";
    private static boolean mapKitInitialized;
    private static int mapKitClients;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            boolean enabled = HiddenApiBypass.addHiddenApiExemptions("Lalfusos/rpc/");
            Log.i(TAG, "BATTERY_RPC hidden API exemption=" + enabled);
        } catch (Throwable error) {
            Log.e(TAG, "BATTERY_RPC hidden API exemption failed", error);
        }
    }

    static synchronized boolean acquireMapKit(Application application) {
        if (BuildConfig.MAPKIT_API_KEY == null
                || BuildConfig.MAPKIT_API_KEY.trim().length() == 0) {
            Log.e(TAG, "MapKit API key is not configured");
            return false;
        }
        try {
            if (!mapKitInitialized) {
                MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY);
                MapKitFactory.initialize(application);
                mapKitInitialized = true;
                Log.i(TAG, "Yandex MapKit initialized for instrument display");
            }
            if (mapKitClients == 0) MapKitFactory.getInstance().onStart();
            mapKitClients++;
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Yandex MapKit initialization failed", error);
            return false;
        }
    }

    static synchronized void releaseMapKit() {
        if (!mapKitInitialized || mapKitClients <= 0) return;
        mapKitClients--;
        if (mapKitClients == 0) {
            try {
                MapKitFactory.getInstance().onStop();
            } catch (Throwable error) {
                Log.w(TAG, "Yandex MapKit stop failed", error);
            }
        }
    }

    static boolean hasMapKitApiKey() {
        return BuildConfig.MAPKIT_API_KEY != null
                && BuildConfig.MAPKIT_API_KEY.trim().length() > 0;
    }
}
