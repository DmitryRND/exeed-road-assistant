package com.example.instrumentawdprobe;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.Charset;

public final class SpeedCameraIndexTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("primary database path required");
        }
        SpeedCameraIndex index = SpeedCameraIndex.read(new FileInputStream(args[0]));
        require(index.size() == 71765, "unexpected record count: " + index.size());
        require(index.repairedRows() == 0,
                "unexpected repaired row count: " + index.repairedRows());
        require(index.skippedRows() == 0,
                "unexpected skipped row count: " + index.skippedRows());
        require("2026-07-17".equals(index.databaseDate()),
                "unexpected database date: " + index.databaseDate());

        float distance = SpeedCameraIndex.distanceMeters(
                55.7500, 37.6100, 55.7510, 37.6100);
        require(distance > 110f && distance < 112f,
                "haversine distance is wrong: " + distance);
        require(Math.abs(SpeedCameraIndex.angleDifference(350f, 10f) - 20f) < 0.01f,
                "wrapped angle difference is wrong");
        float bearing = SpeedCameraIndex.bearingDegrees(
                55.7500, 37.6100, 55.7510, 37.6100);
        require(bearing < 1f || bearing > 359f, "north bearing is wrong: " + bearing);
        SpeedCameraIndex.Match exact = index.findNearest(
                44.92694, 37.32397, 165f, true, 20);
        require(exact != null, "known camera was not found");
        require(exact.camera.id == 8, "wrong known camera: " + exact.camera.id);
        require(exact.distanceMeters < 1f,
                "known camera distance is wrong: " + exact.distanceMeters);

        // Reported road-test regression: eastbound on Stachki Avenue from
        // house 223 toward the city centre. The verified 40 km/h camera is at
        // 47.212335, 39.631195 (HUD) / 47.212375, 39.631131 (official layer).
        SpeedCameraIndex.Match stachkiEastbound = index.findNearest(
                47.212335, 39.627200, 88f, true, 600);
        require(stachkiEastbound != null,
                "Stachki 217k1 camera was missed on the eastbound approach");
        require(stachkiEastbound.camera.speed == 40,
                "wrong Stachki camera selected: " + stachkiEastbound.camera.id
                        + " speed=" + stachkiEastbound.camera.speed);
        require(stachkiEastbound.distanceMeters < 350f,
                "Stachki camera distance is wrong: "
                        + stachkiEastbound.distanceMeters);
        SpeedCameraIndex.Match stachkiWithoutCourse = index.findNearest(
                47.212100, 39.629900, 0f, false, 180);
        require(stachkiWithoutCourse == null,
                "camera was acquired without a reliable route course");
        require(!SpeedCameraIndex.hasPassedCamera(80f, 95f, false),
                "camera cleared too early");
        require(SpeedCameraIndex.hasPassedCamera(80f, 120f, false),
                "increasing distance did not clear passed camera");
        require(SpeedCameraIndex.hasPassedCamera(140f, 50f, true),
                "camera behind the car was not cleared");
        require(SpeedCameraIndex.areSamePhysicalCamera(
                        new SpeedCamera(201, 39.631195, 47.212335, 1, 40, 1, 88),
                        new SpeedCamera(202, 39.631131, 47.212375, 1, 0, 0, 0),
                        SpeedCameraIndex.DUPLICATE_RADIUS_METERS),
                "nearby records were not recognized as one physical camera");
        require(!SpeedCameraIndex.areSamePhysicalCamera(
                        new SpeedCamera(203, 39.631195, 47.212335, 1, 40, 1, 88),
                        new SpeedCamera(204, 39.631600, 47.212700, 1, 40, 0, 0),
                        SpeedCameraIndex.DUPLICATE_RADIUS_METERS),
                "distinct cameras were incorrectly grouped");

        // Reported road-test regression on Nagibina Avenue: HUD Speed has a
        // speed-camera record followed by two traffic-light control records
        // for the same northbound intersection approach.
        SpeedCamera nagibinaCamera = new SpeedCamera(
                2405765, 39.720624, 47.264684, 1, 60, 1, 21);
        SpeedCamera nagibinaTrafficLight = new SpeedCamera(
                4142502, 39.720979, 47.265369, 3, 60, 1, 22);
        SpeedCamera nagibinaNextTrafficLight = new SpeedCamera(
                4147936, 39.721388, 47.266268, 3, 60, 1, 18);
        require(SpeedCameraIndex.areSameWarningZone(
                        nagibinaCamera, nagibinaTrafficLight),
                "Nagibina intersection records were not grouped");
        require(SpeedCameraIndex.areSameWarningZone(
                        nagibinaCamera, nagibinaNextTrafficLight),
                "Nagibina warning zone was too short");
        require(!SpeedCameraIndex.areSameWarningZone(
                        nagibinaCamera,
                        new SpeedCamera(301, 39.720624, 47.265000, 1, 60, 1, 201)),
                "opposite approaches were incorrectly grouped");
        require(!SpeedCameraIndex.areSameWarningZone(
                        nagibinaCamera,
                        new SpeedCamera(302, 39.720624, 47.265000, 1, 40, 1, 21)),
                "different speed limits were incorrectly grouped");

        SpeedCameraIndex northOnly = singleCameraIndex(
                "101,37.6100000,55.7510000,1,60,1,0");
        require(northOnly.findNearest(55.7500, 37.6100, 0f, true, 200) != null,
                "one-way camera was not found in its control direction");
        require(northOnly.findNearest(55.7520, 37.6100, 180f, true, 200) == null,
                "one-way camera incorrectly matched the opposite direction");
        require(northOnly.findNearest(55.7500, 37.6100, 0f, false, 200) == null,
                "directional camera matched without a reliable course");

        SpeedCameraIndex allDirection = singleCameraIndex(
                "104,37.6100000,55.7510000,1,60,0,0");
        require(allDirection.findNearest(55.7500, 37.6100, 0f, false, 200) == null,
                "all-direction camera matched by radius without a route course");
        require(allDirection.findNearest(55.7500, 37.6120, 0f, true, 300) == null,
                "camera beside the route corridor was accepted");
        require(!SpeedCameraIndex.matchesTravelPath(
                        new SpeedCamera(105, 0, 0, 1, 60, 0, 0),
                        0f, 20f, 600f),
                "long-range camera outside the 70 m road corridor was accepted");
        require(SpeedCameraIndex.matchesTravelPath(
                        new SpeedCamera(106, 0, 0, 1, 60, 0, 0),
                        0f, 6f, 600f),
                "long-range camera inside the 70 m road corridor was rejected");

        SpeedCameraIndex twoWay = singleCameraIndex(
                "102,37.6100000,55.7510000,4,80,2,0");
        require(twoWay.findNearest(55.7500, 37.6100, 0f, true, 200) != null,
                "two-way camera did not match its primary direction");
        require(twoWay.findNearest(55.7520, 37.6100, 180f, true, 200) != null,
                "two-way camera did not match its opposite direction");
        SpeedCameraIndex hudFormat = singleHudCameraIndex(
                "103,37.6100000,55.7510000,11,80,2,0,400,15"
                        + " // radarbase:103 | source_type:13 | source_dir_type:2");
        require("2026-08-09".equals(hudFormat.databaseDate()),
                "HUD header date was not parsed");
        SpeedCameraIndex.Match hudMatch = hudFormat.findNearest(
                55.7500, 37.6100, 0f, true, 600);
        require(hudMatch != null,
                "HUD nine-column row with comment was not parsed");
        require(hudMatch.camera.alertDistanceMeters == 400,
                "HUD DISTANCE was not parsed");
        require(hudMatch.camera.angleToleranceDegrees == 15,
                "HUD ANGLE was not parsed");
        require(hudMatch.camera.effectiveWarningDistance(600) == 600,
                "application warning distance was incorrectly capped by source DISTANCE");
        require(Math.abs(hudMatch.camera.effectiveAngleTolerance() - 18f) < 0.01f,
                "HUD ANGLE tolerance is wrong");
        require(hudFormat.findNearest(55.7468, 37.6100, 0f, true, 600) != null,
                "speed-dependent 600 m radius did not override source DISTANCE");
        require(hudFormat.findNearest(55.7479, 37.6100, 0f, true, 600) != null,
                "HUD DISTANCE rejected a camera inside its own radius");
        require(hudFormat.findNearest(55.7500, 37.6100, 18f, true, 600) != null,
                "HUD ANGLE rejected a course on the calibrated boundary");
        require(hudFormat.findNearest(55.7500, 37.6100, 19f, true, 600) == null,
                "HUD ANGLE accepted a course outside its calibrated boundary");
        require("СРЕДНЯЯ СКОРОСТЬ".equals(new SpeedCamera(
                1, 0, 0, 4, 80, 0, 0).typeLabel()),
                "camera type label is wrong");
        require("Камера 60 км/ч".equals(new SpeedCamera(
                2, 0, 0, 1, 60, 0, 0).hudLabel()),
                "speed camera HUD label is wrong");
        require("Камера на светофоре".equals(new SpeedCamera(
                3, 0, 0, 2, 60, 0, 0).hudLabel()),
                "traffic-light camera HUD label includes a speed limit");
        require("Контроль светофора".equals(new SpeedCamera(
                4, 0, 0, 3, 60, 0, 0).hudLabel()),
                "traffic-light control HUD label includes a speed limit");
        System.out.println("OK records=" + index.size()
                + " repaired=" + index.repairedRows()
                + " distance=" + distance + " bearing=" + bearing
                + " exactCamera=" + exact.camera.id
                + " stachkiCamera=" + stachkiEastbound.camera.id
                + " stachkiDistance=" + stachkiEastbound.distanceMeters);
    }

    private static SpeedCameraIndex singleCameraIndex(String row) throws Exception {
        StringBuilder database = new StringBuilder(SpeedCameraIndex.HEADER).append('\n');
        for (int i = 0; i < 1000; i++) database.append(row).append('\n');
        return SpeedCameraIndex.read(new ByteArrayInputStream(
                database.toString().getBytes(Charset.forName("windows-1251"))));
    }

    private static SpeedCameraIndex singleHudCameraIndex(String row) throws Exception {
        StringBuilder database = new StringBuilder(SpeedCameraIndex.HUD_HEADER)
                .append(" // RadarBase 2026-08-09T00:00:00.000Z\n");
        for (int i = 0; i < 1000; i++) database.append(row).append('\n');
        return SpeedCameraIndex.read(new ByteArrayInputStream(
                database.toString().getBytes(Charset.forName("UTF-8"))));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
