package com.example.instrumentawdprobe;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PointF;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.map.OffscreenMapWindow;
import com.yandex.mapkit.traffic.TrafficLayer;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.runtime.view.SurfaceFactory;

/** Renders MapKit directly into the instrument overlay's Android Surface. */
final class InstrumentMapSurfaceView extends SurfaceView
        implements SurfaceHolder.Callback {
    private static final String TAG = "InstrumentMapSurface";
    private static final Point ROSTOV = new Point(47.2357, 39.7015);
    private static final float INITIAL_ZOOM = 15f;

    private OffscreenMapWindow offscreenMapWindow;
    private com.yandex.runtime.view.Surface yandexSurface;
    private UserLocationLayer userLocationLayer;
    private TrafficLayer trafficLayer;
    private boolean mapKitAcquired;
    private int surfaceWidth;
    private int surfaceHeight;

    InstrumentMapSurfaceView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(9, 10, 9));
        setZOrderOnTop(false);
        getHolder().addCallback(this);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        createRenderer(holder, getWidth(), getHeight());
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format,
                                         int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (offscreenMapWindow == null
                || width != surfaceWidth || height != surfaceHeight) {
            destroyRenderer();
            createRenderer(holder, width, height);
        }
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        destroyRenderer();
    }

    @Override protected void onDetachedFromWindow() {
        destroyRenderer();
        super.onDetachedFromWindow();
    }

    private void createRenderer(SurfaceHolder holder, int width, int height) {
        if (offscreenMapWindow != null || !holder.getSurface().isValid()
                || width <= 0 || height <= 0) return;
        Application application = (Application) getContext()
                .getApplicationContext();
        if (!RoadAssistantApplication.acquireMapKit(application)) return;
        mapKitAcquired = true;
        try {
            surfaceWidth = width;
            surfaceHeight = height;
            offscreenMapWindow = com.yandex.mapkit.MapKitFactory.getInstance()
                    .createOffscreenMapWindow(width, height);
            MapWindow mapWindow = offscreenMapWindow.getMapWindow();
            yandexSurface = SurfaceFactory.from(holder.getSurface());
            yandexSurface.setAnchorPoint(new PointF(0.5f, 0.5f));
            mapWindow.setMaxFps(15);
            mapWindow.addSurface(yandexSurface);

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
            userLocationLayer.setAnchor(
                    new PointF(width * 0.5f, height * 0.5f),
                    new PointF(width * 0.5f, height * 0.68f));

            trafficLayer = com.yandex.mapkit.MapKitFactory.getInstance()
                    .createTrafficLayer(mapWindow);
            trafficLayer.setTrafficVisible(true);
            Log.i(TAG, "Instrument map ready size=" + width + "x" + height
                    + " night=" + night + " traffic=true");
        } catch (Throwable error) {
            Log.e(TAG, "Unable to create instrument MapKit surface", error);
            destroyRenderer();
        }
    }

    private void destroyRenderer() {
        if (offscreenMapWindow != null && yandexSurface != null) {
            try {
                offscreenMapWindow.getMapWindow().removeSurface(yandexSurface);
            } catch (Throwable error) {
                Log.w(TAG, "Unable to detach instrument map surface", error);
            }
        }
        trafficLayer = null;
        userLocationLayer = null;
        yandexSurface = null;
        offscreenMapWindow = null;
        surfaceWidth = 0;
        surfaceHeight = 0;
        if (mapKitAcquired) {
            mapKitAcquired = false;
            RoadAssistantApplication.releaseMapKit();
        }
    }
}
