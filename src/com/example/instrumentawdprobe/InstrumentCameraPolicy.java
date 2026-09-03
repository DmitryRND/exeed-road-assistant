package com.example.instrumentawdprobe;

/**
 * Rejects GPS/heading noise before it reaches the small instrument map.
 * MapKit's user-location auto camera is deliberately disabled, so this policy
 * is the only owner of camera movement while Navigator guidance is active.
 */
final class InstrumentCameraPolicy {
    static final long NAVIGATOR_POSITION_FRESH_MS = 4_000L;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0d;
    private static final double MIN_POSITION_CHANGE_METERS = 1.5d;
    private static final float MIN_HEADING_CHANGE_DEGREES = 3.0f;
    private static final long MIN_UPDATE_INTERVAL_MS = 350L;
    private static final double FAST_POSITION_CHANGE_METERS = 10.0d;
    private static final float FAST_HEADING_CHANGE_DEGREES = 12.0f;

    private InstrumentCameraPolicy() {}

    /**
     * Navigator owns the camera while it is actively publishing map positions.
     * Before the first Navigator sample, or after that stream goes quiet, the
     * accepted Android location keeps the instrument map centred on the car.
     */
    static boolean shouldUseFallbackPosition(
            long lastNavigatorPositionMs, long nowMs) {
        if (lastNavigatorPositionMs <= 0L || nowMs < lastNavigatorPositionMs) return true;
        return nowMs - lastNavigatorPositionMs > NAVIGATOR_POSITION_FRESH_MS;
    }

    static boolean shouldMove(
            boolean hasPrevious,
            double previousLatitude,
            double previousLongitude,
            float previousHeading,
            long previousUpdateMs,
            double latitude,
            double longitude,
            float heading,
            long nowMs) {
        if (!hasPrevious) return true;

        double distance = distanceMeters(
                previousLatitude, previousLongitude, latitude, longitude);
        float headingDelta = headingDelta(previousHeading, heading);
        if (distance < MIN_POSITION_CHANGE_METERS
                && headingDelta < MIN_HEADING_CHANGE_DEGREES) {
            return false;
        }

        long elapsed = Math.max(0L, nowMs - previousUpdateMs);
        return elapsed >= MIN_UPDATE_INTERVAL_MS
                || distance >= FAST_POSITION_CHANGE_METERS
                || headingDelta >= FAST_HEADING_CHANGE_DEGREES;
    }

    static double distanceMeters(
            double latitudeA, double longitudeA,
            double latitudeB, double longitudeB) {
        double latA = Math.toRadians(latitudeA);
        double latB = Math.toRadians(latitudeB);
        double deltaLat = latB - latA;
        double deltaLon = Math.toRadians(longitudeB - longitudeA);
        double sinLat = Math.sin(deltaLat * 0.5d);
        double sinLon = Math.sin(deltaLon * 0.5d);
        double value = sinLat * sinLat
                + Math.cos(latA) * Math.cos(latB) * sinLon * sinLon;
        return EARTH_RADIUS_METERS * 2.0d
                * Math.atan2(Math.sqrt(value), Math.sqrt(Math.max(0.0d, 1.0d - value)));
    }

    static float headingDelta(float previous, float current) {
        if (Float.isNaN(previous) || Float.isNaN(current)) return 0.0f;
        float delta = Math.abs((current - previous) % 360.0f);
        return delta > 180.0f ? 360.0f - delta : delta;
    }
}
