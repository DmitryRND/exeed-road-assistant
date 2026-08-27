package com.example.instrumentawdprobe;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.geometry.Polyline;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.IconStyle;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.RotationType;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.traffic.TrafficLayer;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.runtime.image.ImageProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts a regular MapKit MapView in the display-specific instrument window.
 * This mirrors the proven Media Bridge rendering path and avoids the black
 * secondary-display buffers observed with OffscreenMapWindow.addSurface().
 */
final class InstrumentMapSurfaceView extends FrameLayout {
    private static final String TAG = "InstrumentMapView";
    private static final Point ROSTOV = new Point(47.2357, 39.7015);
    private static final float INITIAL_ZOOM = 15f;
    private static final int CAMERA_MARKER_WIDTH = 96;
    private static final int CAMERA_MARKER_HEIGHT = 112;

    private final MapView mapView;
    private MapWindow mapWindow;
    private UserLocationLayer userLocationLayer;
    private TrafficLayer trafficLayer;
    private MapObjectCollection navigatorRouteCollection;
    private PlacemarkMapObject cameraPlacemark;
    private int displayedCameraId = Integer.MIN_VALUE;
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
        mapWindow = mapView.getMapWindow();
        configureMap(mapWindow);
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
        navigatorRouteCollection = mapWindow.getMap().getMapObjects().addCollection();
        Log.i(TAG, "Instrument MapView configured night=" + night + " traffic=true");
    }

    /** Applies the active polyline exported by the patched Yandex Navigator. */
    void updateNavigatorRoute(String encodedPoints) {
        if (navigatorRouteCollection == null) return;
        List<Point> points = parseNavigatorPoints(encodedPoints);
        navigatorRouteCollection.clear();
        if (points.size() < 2) {
            Log.i(TAG, "Navigator route cleared: no valid geometry");
            return;
        }
        com.yandex.mapkit.map.PolylineMapObject route =
                navigatorRouteCollection.addPolyline(new Polyline(points));
        route.setZIndex(20f);
        route.setStrokeColor(Color.rgb(66, 115, 255));
        route.setStrokeWidth(9f);
        route.setOutlineColor(Color.argb(210, 30, 30, 34));
        route.setOutlineWidth(3f);
        Log.i(TAG, "Navigator route shown points=" + points.size());
    }

    void clearNavigatorRoute() {
        if (navigatorRouteCollection != null) navigatorRouteCollection.clear();
    }

    void updateNavigatorPosition(double latitude, double longitude, float heading) {
        if (mapWindow == null || latitude < -90d || latitude > 90d
                || longitude < -180d || longitude > 180d) return;
        CameraPosition current = mapWindow.getMap().getCameraPosition();
        mapWindow.getMap().move(new CameraPosition(
                new Point(latitude, longitude),
                Math.max(16f, current.getZoom()),
                Float.isNaN(heading) ? current.getAzimuth() : heading,
                Math.max(30f, current.getTilt())));
    }

    private static List<Point> parseNavigatorPoints(String encodedPoints) {
        List<Point> points = new ArrayList<>();
        if (encodedPoints == null || encodedPoints.trim().isEmpty()) return points;
        String[] encoded = encodedPoints.split(";");
        for (String item : encoded) {
            String[] fields = item.split(",");
            if (fields.length != 2) continue;
            try {
                double latitude = Double.parseDouble(fields[0].trim());
                double longitude = Double.parseDouble(fields[1].trim());
                if (latitude >= -90d && latitude <= 90d
                        && longitude >= -180d && longitude <= 180d) {
                    points.add(new Point(latitude, longitude));
                }
            } catch (NumberFormatException ignored) {
                // Ignore one malformed point while preserving the valid route tail.
            }
        }
        return points;
    }

    void showCamera(SpeedCamera camera) {
        if (mapWindow == null || camera == null
                || camera.latitude < -90.0 || camera.latitude > 90.0
                || camera.longitude < -180.0 || camera.longitude > 180.0) {
            clearCamera();
            return;
        }
        Point cameraPoint = new Point(camera.latitude, camera.longitude);
        if (cameraPlacemark != null && cameraPlacemark.isValid()
                && displayedCameraId == camera.id) {
            cameraPlacemark.setGeometry(cameraPoint);
            return;
        }
        clearCamera();
        MapObjectCollection objects = mapWindow.getMap().getMapObjects();
        IconStyle style = new IconStyle()
                .setAnchor(new PointF(0.5f, 1.0f))
                .setRotationType(RotationType.NO_ROTATION)
                .setFlat(false)
                .setScale(1.0f)
                .setZIndex(100f)
                .setOpacity(1.0f);
        cameraPlacemark = objects.addPlacemark(
                cameraPoint,
                ImageProvider.fromBitmap(createCameraMarker(camera)),
                style);
        cameraPlacemark.setZIndex(100f);
        displayedCameraId = camera.id;
        Log.i(TAG, "Camera marker shown id=" + camera.id
                + " speed=" + camera.speed
                + " lat=" + camera.latitude + " lon=" + camera.longitude);
    }

    void clearCamera() {
        if (cameraPlacemark != null) {
            try {
                if (cameraPlacemark.isValid()) {
                    cameraPlacemark.getParent().remove(cameraPlacemark);
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to remove camera marker", error);
            }
        }
        cameraPlacemark = null;
        displayedCameraId = Integer.MIN_VALUE;
    }

    private static Bitmap createCameraMarker(SpeedCamera camera) {
        Bitmap bitmap = Bitmap.createBitmap(
                CAMERA_MARKER_WIDTH, CAMERA_MARKER_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

        float centerX = CAMERA_MARKER_WIDTH * 0.5f;
        float centerY = 44f;
        float radius = 36f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(105, 0, 0, 0));
        canvas.drawCircle(centerX + 2f, centerY + 4f, radius + 5f, paint);
        Path shadowPointer = new Path();
        shadowPointer.moveTo(centerX - 12f, 72f);
        shadowPointer.lineTo(centerX + 3f, 108f);
        shadowPointer.lineTo(centerX + 15f, 72f);
        shadowPointer.close();
        canvas.drawPath(shadowPointer, paint);

        paint.setColor(Color.rgb(248, 245, 237));
        Path pointer = new Path();
        pointer.moveTo(centerX - 12f, 70f);
        pointer.lineTo(centerX, 105f);
        pointer.lineTo(centerX + 12f, 70f);
        pointer.close();
        canvas.drawPath(pointer, paint);
        canvas.drawCircle(centerX, centerY, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7f);
        paint.setColor(Color.rgb(213, 91, 70));
        canvas.drawCircle(centerX, centerY, radius - 3.5f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(22, 22, 21));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        if (camera.speed > 0) {
            String speed = String.valueOf(camera.speed);
            paint.setTextSize(speed.length() >= 3 ? 25f : 30f);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) * 0.5f;
            canvas.drawText(speed, centerX, baseline, paint);
        } else {
            RectF body = new RectF(centerX - 22f, centerY - 13f,
                    centerX + 16f, centerY + 14f);
            canvas.drawRoundRect(body, 5f, 5f, paint);
            Path lens = new Path();
            lens.moveTo(centerX + 16f, centerY - 8f);
            lens.lineTo(centerX + 29f, centerY - 15f);
            lens.lineTo(centerX + 29f, centerY + 15f);
            lens.lineTo(centerX + 16f, centerY + 8f);
            lens.close();
            canvas.drawPath(lens, paint);
            paint.setColor(Color.rgb(248, 245, 237));
            canvas.drawCircle(centerX - 4f, centerY, 7f, paint);
        }
        return bitmap;
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
        clearCamera();
        if (mapView != null && mapViewStarted) {
            mapView.onStop();
            mapViewStarted = false;
        }
        trafficLayer = null;
        userLocationLayer = null;
        navigatorRouteCollection = null;
        mapWindow = null;
        if (mapKitAcquired) {
            mapKitAcquired = false;
            RoadAssistantApplication.releaseMapKit();
        }
        super.onDetachedFromWindow();
        Log.i(TAG, "Instrument MapView stopped");
    }
}
