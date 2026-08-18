package com.example.instrumentawdprobe;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.traffic.TrafficLayer;
import com.yandex.mapkit.user_location.UserLocationLayer;

/**
 * Hosts a regular MapKit MapView in the display-specific instrument window.
 * This mirrors the proven Media Bridge rendering path and avoids the black
 * secondary-display buffers observed with OffscreenMapWindow.addSurface().
 */
final class InstrumentMapSurfaceView extends FrameLayout {
    private static final String TAG = "InstrumentMapView";
    private static final Point ROSTOV = new Point(47.2357, 39.7015);
    private static final float INITIAL_ZOOM = 15f;

    private final MapView mapView;
    private UserLocationLayer userLocationLayer;
    private TrafficLayer trafficLayer;
    private boolean mapKitAcquired;
    private boolean mapViewStarted;

    InstrumentMapSurfaceView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(9, 10, 9));

        Application application = (Application) context.getApplicationContext();
        mapKitAcquired = RoadAssistantApplication.acquireMapKit(application);
        if (!mapKitAcquired) {
            mapView = null;
            Log.e(TAG, "MapKit is unavailable for instrument MapView");
            return;
        }

        mapView = new MapView(context);
        mapView.setNoninteractive(true);
        mapView.setBackgroundColor(Color.rgb(9, 10, 9));
        promoteOpaqueSurfaces(mapView);
        addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        configureMap(mapView.getMapWindow());
    }

    private static void promoteOpaqueSurfaces(View view) {
        if (view instanceof SurfaceView) {
            SurfaceView surfaceView = (SurfaceView) view;
            // MapKit uses a SurfaceView. Its default z=-2 places it below the
            // OEM cluster chrome even though our overlay window is on top.
            surfaceView.setZOrderOnTop(true);
            surfaceView.getHolder().setFormat(PixelFormat.OPAQUE);
            surfaceView.setBackgroundColor(Color.rgb(9, 10, 9));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                promoteOpaqueSurfaces(group.getChildAt(i));
            }
        }
    }

    private void configureMap(MapWindow mapWindow) {
        mapWindow.setMaxFps(15);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO;
        mapWindow.getMap().setNightModeEnabled(night);
        mapWindow.getMap().move(new CameraPosition(
                ROSTOV, INITIAL_ZOOM, 0f, 0f),
                new Animation(Animation.Type.SMOOTH, 0.35f), null);

        userLocationLayer = com.yandex.mapkit.MapKitFactory.getInstance()
                .createUserLocationLayer(mapWindow);
        userLocationLayer.setVisible(true);
        userLocationLayer.setHeadingModeActive(true);
        userLocationLayer.setAutoZoomEnabled(true);

        trafficLayer = com.yandex.mapkit.MapKitFactory.getInstance()
                .createTrafficLayer(mapWindow);
        trafficLayer.setTrafficVisible(true);
        Log.i(TAG, "Instrument MapView configured night=" + night + " traffic=true");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mapView != null && !mapViewStarted) {
            promoteOpaqueSurfaces(mapView);
            mapView.onStart();
            mapViewStarted = true;
            mapView.post(new Runnable() {
                @Override public void run() {
                    promoteOpaqueSurfaces(mapView);
                }
            });
            Log.i(TAG, "Instrument MapView started");
        }
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (userLocationLayer != null && width > 0 && height > 0) {
            userLocationLayer.setAnchor(
                    new PointF(width * 0.5f, height * 0.5f),
                    new PointF(width * 0.5f, height * 0.68f));
            Log.i(TAG, "Instrument MapView ready size=" + width + "x" + height);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (mapView != null && mapViewStarted) {
            mapView.onStop();
            mapViewStarted = false;
        }
        trafficLayer = null;
        userLocationLayer = null;
        if (mapKitAcquired) {
            mapKitAcquired = false;
            RoadAssistantApplication.releaseMapKit();
        }
        super.onDetachedFromWindow();
        Log.i(TAG, "Instrument MapView stopped");
    }
}
