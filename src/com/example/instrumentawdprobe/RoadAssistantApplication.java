package com.example.instrumentawdprobe;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/** Initializes process-local access to the vendor RPC API before activities are loaded. */
public final class RoadAssistantApplication extends Application {
    private static final String TAG = "InstrumentAwdProbe";

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
}
