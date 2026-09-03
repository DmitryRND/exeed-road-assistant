package com.example.instrumentawdprobe;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/** Keeps camera location updates alive while the control Activity is in the background. */
public final class CameraLocationService extends Service {
    static final String ACTION_LOCATION_UPDATE =
            "com.example.instrumentawdprobe.action.LOCATION_UPDATE";
    static final String ACTION_REFRESH_PHONE_GPS =
            "com.example.instrumentawdprobe.action.REFRESH_PHONE_GPS";
    static final String EXTRA_LOCATION = "location";
    static final String PREFS = "awd_display";
    static final String PREF_PHONE_GPS_ENABLED = "phone_gps_enabled";
    static final String PREF_PHONE_GPS_TOKEN = "phone_gps_token";

    private static final String TAG = "CameraLocationService";
    private static final String CHANNEL_ID = "camera_location";
    private static final int NOTIFICATION_ID = 2501;

    private LocationManager locationManager;
    private boolean updatesStarted;
    private PhoneLocationReceiver phoneLocationReceiver;
    private String activePhoneToken = "";

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location == null) return;
            Intent update = new Intent(ACTION_LOCATION_UPDATE);
            update.setPackage(getPackageName());
            update.putExtra(EXTRA_LOCATION, location);
            sendBroadcast(update);
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
    };

    @Override public void onCreate() {
        super.onCreate();
        startAsForeground();
        startLocationUpdates();
        refreshPhoneGpsReceiver();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startLocationUpdates();
        refreshPhoneGpsReceiver();
        return START_STICKY;
    }

    private void refreshPhoneGpsReceiver() {
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_PHONE_GPS_ENABLED, false);
        String token = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(PREF_PHONE_GPS_TOKEN, "");
        if (!enabled || token == null || token.length() < 12) {
            if (phoneLocationReceiver != null) phoneLocationReceiver.stop();
            phoneLocationReceiver = null;
            activePhoneToken = "";
            return;
        }
        if (phoneLocationReceiver != null && token.equals(activePhoneToken)) return;
        if (phoneLocationReceiver != null) phoneLocationReceiver.stop();
        phoneLocationReceiver = new PhoneLocationReceiver(new PhoneLocationReceiver.Listener() {
            @Override public void onPhoneLocation(Location location) {
                Intent update = new Intent(ACTION_LOCATION_UPDATE);
                update.setPackage(getPackageName());
                update.putExtra(EXTRA_LOCATION, location);
                sendBroadcast(update);
            }

            @Override public void onPhoneLocationError(String detail) {
                Log.w(TAG, "Phone GPS receiver error: " + detail);
            }
        });
        phoneLocationReceiver.start(token);
        activePhoneToken = token;
    }

    private void startAsForeground() {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Camera monitoring", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps road-camera location monitoring active");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        int icon = getResources().getIdentifier(
                "ic_launcher", "drawable", getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_menu_mylocation;
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(icon)
                .setContentTitle("Road assistant")
                .setContentText("Camera monitoring is active")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startLocationUpdates() {
        if (updatesStarted) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission is not granted");
            stopSelf();
            return;
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            Log.w(TAG, "LocationManager is unavailable");
            stopSelf();
            return;
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1000L, 2f, listener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener);
            }
            updatesStarted = true;
            Log.i(TAG, "Foreground camera location monitoring started");
        } catch (SecurityException error) {
            Log.e(TAG, "Unable to request location updates", error);
            stopSelf();
        }
    }

    @Override public void onDestroy() {
        if (phoneLocationReceiver != null) {
            phoneLocationReceiver.stop();
            phoneLocationReceiver = null;
        }
        activePhoneToken = "";
        if (locationManager != null) {
            try { locationManager.removeUpdates(listener); }
            catch (Throwable error) { Log.w(TAG, "Unable to remove location updates", error); }
        }
        updatesStarted = false;
        locationManager = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
