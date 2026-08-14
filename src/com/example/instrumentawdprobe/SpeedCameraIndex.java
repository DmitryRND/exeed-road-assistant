package com.example.instrumentawdprobe;

import android.location.Location;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SpeedCameraIndex {
    static final String HEADER = "IDX,X,Y,TYPE,SPEED,DIRTYPE,DIRECTION";
    static final String HUD_HEADER = HEADER + ",DISTANCE,ANGLE";
    private static final double CELL_DEGREES = 0.02;
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    static final class Match {
        final SpeedCamera camera;
        final float distanceMeters;
        final float bearingToCamera;

        Match(SpeedCamera camera, float distanceMeters, float bearingToCamera) {
            this.camera = camera;
            this.distanceMeters = distanceMeters;
            this.bearingToCamera = bearingToCamera;
        }
    }

    private final Map<Long, List<SpeedCamera>> cells = new HashMap<>();
    private int count;
    private int repairedRows;
    private int skippedRows;
    private String databaseDate;

    static SpeedCameraIndex read(InputStream input) throws IOException {
        SpeedCameraIndex index = new SpeedCameraIndex();
        index.readInto(input);
        index.validateSize();
        return index;
    }

    static SpeedCameraIndex read(InputStream primary, InputStream supplemental)
            throws IOException {
        SpeedCameraIndex index = new SpeedCameraIndex();
        index.readInto(primary);
        index.validateSize();
        index.readInto(supplemental);
        index.validateSize();
        return index;
    }

    private void readInto(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, Charset.forName("UTF-8")), 64 * 1024);
        String header = reader.readLine();
        if (header != null && header.length() > 0 && header.charAt(0) == '\ufeff') {
            header = header.substring(1);
        }
        String headerData = stripComment(header).trim();
        if (!HEADER.equals(headerData) && !HUD_HEADER.equals(headerData)) {
            throw new IOException("Unexpected speed camera header: " + header);
        }
        if (databaseDate == null && HUD_HEADER.equals(headerData)) {
            databaseDate = parseDatabaseDate(header);
        }
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.length() == 0) continue;
            String[] fields = stripComment(line).trim().split(",", -1);
            if (fields.length != 7 && fields.length != 9) {
                skippedRows++;
                continue;
            }
            try {
                boolean repaired = fields[3].length() == 0
                        || fields[4].length() == 0
                        || fields[5].length() == 0
                        || fields[6].length() == 0;
                SpeedCamera camera = new SpeedCamera(
                        Integer.parseInt(fields[0]),
                        Double.parseDouble(fields[1]),
                        Double.parseDouble(fields[2]),
                        parseInt(fields[3], 1),
                        parseInt(fields[4], 0),
                        parseInt(fields[5], 0),
                        parseInt(fields[6], 0),
                        fields.length == 9 ? parseInt(fields[7], 0) : 0,
                        fields.length == 9 ? parseInt(fields[8], 0) : 0);
                if (camera.latitude < -90.0 || camera.latitude > 90.0
                        || camera.longitude < -180.0 || camera.longitude > 180.0) {
                    skippedRows++;
                    continue;
                }
                add(camera);
                if (repaired) repairedRows++;
            } catch (NumberFormatException error) {
                skippedRows++;
            }
        }
    }

    private void validateSize() throws IOException {
        if (count < 1000) {
            throw new IOException("Camera database is unexpectedly small: " + count);
        }
    }

    int size() {
        return count;
    }

    int repairedRows() {
        return repairedRows;
    }

    int skippedRows() {
        return skippedRows;
    }

    String databaseDate() {
        return databaseDate;
    }

    Match findNearest(Location location, float courseDegrees, boolean hasCourse,
                      int maximumDistanceMeters) {
        if (location == null || cells.isEmpty()) return null;
        return findNearest(location.getLatitude(), location.getLongitude(),
                courseDegrees, hasCourse, maximumDistanceMeters);
    }

    Match findNearest(double latitude, double longitude,
                      float courseDegrees, boolean hasCourse,
                      int maximumDistanceMeters) {
        if (cells.isEmpty()) return null;
        int latCell = cell(latitude);
        int lonCell = cell(longitude);
        int latRadius = Math.max(1, (int) Math.ceil(
                maximumDistanceMeters / (111320.0 * CELL_DEGREES)) + 1);
        double longitudeMeters = Math.max(20000.0,
                111320.0 * Math.cos(Math.toRadians(latitude)));
        int lonRadius = Math.max(1, (int) Math.ceil(
                maximumDistanceMeters / (longitudeMeters * CELL_DEGREES)) + 1);

        Match best = null;
        for (int latOffset = -latRadius; latOffset <= latRadius; latOffset++) {
            for (int lonOffset = -lonRadius; lonOffset <= lonRadius; lonOffset++) {
                List<SpeedCamera> bucket = cells.get(key(
                        latCell + latOffset, lonCell + lonOffset));
                if (bucket == null) continue;
                for (SpeedCamera camera : bucket) {
                    // A route corridor cannot be established without a course.
                    // Suppress acquisition instead of warning for every point
                    // in a circle around the vehicle.
                    if (!hasCourse) continue;
                    float distance = distanceMeters(latitude, longitude,
                            camera.latitude, camera.longitude);
                    if (distance > camera.effectiveWarningDistance(
                            maximumDistanceMeters)) continue;
                    float bearing = bearingDegrees(latitude, longitude,
                            camera.latitude, camera.longitude);
                    if (!matchesTravelPath(camera, courseDegrees, bearing, distance)) {
                        continue;
                    }
                    if (best == null || distance < best.distanceMeters) {
                        best = new Match(camera, distance, bearing);
                    }
                }
            }
        }
        return best;
    }

    private void add(SpeedCamera camera) {
        long key = key(cell(camera.latitude), cell(camera.longitude));
        List<SpeedCamera> bucket = cells.get(key);
        if (bucket == null) {
            bucket = new ArrayList<>();
            cells.put(key, bucket);
        }
        bucket.add(camera);
        count++;
    }

    private static int parseInt(String value, int fallback) {
        return value.length() == 0 ? fallback : Integer.parseInt(value);
    }

    private static String stripComment(String value) {
        if (value == null) return "";
        int comment = value.indexOf("//");
        return comment < 0 ? value : value.substring(0, comment);
    }

    private static String parseDatabaseDate(String header) {
        int marker = header.indexOf("RadarBase ");
        if (marker < 0) return null;
        int start = marker + "RadarBase ".length();
        if (header.length() < start + 10) return null;
        String candidate = header.substring(start, start + 10);
        return candidate.matches("\\d{4}-\\d{2}-\\d{2}") ? candidate : null;
    }

    private static boolean matchesCameraDirection(SpeedCamera camera, float course) {
        if (camera.directionType == 0) return true;
        float primary = angleDifference(course, camera.direction);
        float tolerance = camera.effectiveAngleTolerance();
        if (camera.directionType == 1) return primary <= tolerance;
        if (camera.directionType == 2) {
            return Math.min(primary,
                    angleDifference(course, (camera.direction + 180f) % 360f)) <= tolerance;
        }
        return true;
    }

    static boolean matchesTravelPath(SpeedCamera camera, float course,
                                     float bearingToCamera, float distanceMeters) {
        if (camera == null || !matchesCameraDirection(camera, course)) return false;
        if (distanceMeters <= 25f) return true;
        float bearingDifference = angleDifference(course, bearingToCamera);
        if (bearingDifference >= 90f) return false;
        // Without a route polyline, lateral distance is a much safer proxy for
        // "on our road" than a wide angular cone. At 600 m this is roughly a
        // 6.7-degree corridor; near the camera it remains tolerant of GPS noise.
        double lateralDistance = distanceMeters
                * Math.sin(Math.toRadians(bearingDifference));
        return lateralDistance <= 70.0;
    }

    static float angleDifference(float first, float second) {
        float difference = Math.abs(first - second) % 360f;
        return difference > 180f ? 360f - difference : difference;
    }

    static float distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (float) (EARTH_RADIUS_METERS * 2.0
                * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    static float bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double lambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(lambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0);
    }

    static boolean hasPassedCamera(float minimumDistance, float currentDistance,
                                   boolean cameraBehind) {
        return (minimumDistance < 130f && currentDistance > minimumDistance + 35f)
                || (cameraBehind && minimumDistance < 170f && currentDistance > 30f);
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_DEGREES);
    }

    private static long key(int latCell, int lonCell) {
        return ((long) latCell << 32) ^ (lonCell & 0xffffffffL);
    }
}
