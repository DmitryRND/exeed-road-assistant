package com.example.instrumentawdprobe;

public final class InstrumentCameraPolicyTest {
    public static void main(String[] args) {
        assertTrue(InstrumentCameraPolicy.shouldMove(
                false, 0, 0, Float.NaN, 0,
                47.2357, 39.7015, 90f, 1_000));

        assertFalse(InstrumentCameraPolicy.shouldMove(
                true, 47.2357, 39.7015, 90f, 1_000,
                47.235704, 39.701504, 91f, 2_000));

        assertFalse(InstrumentCameraPolicy.shouldMove(
                true, 47.2357, 39.7015, 90f, 1_000,
                47.23572, 39.70152, 94f, 1_100));

        assertTrue(InstrumentCameraPolicy.shouldMove(
                true, 47.2357, 39.7015, 90f, 1_000,
                47.23572, 39.70152, 94f, 1_500));

        assertTrue(InstrumentCameraPolicy.shouldMove(
                true, 47.2357, 39.7015, 359f, 1_000,
                47.2357, 39.7015, 15f, 1_100));

        assertTrue(InstrumentCameraPolicy.shouldUseFallbackPosition(0L, 10_000L));
        assertFalse(InstrumentCameraPolicy.shouldUseFallbackPosition(8_000L, 10_000L));
        assertFalse(InstrumentCameraPolicy.shouldUseFallbackPosition(
                6_000L, 6_000L + InstrumentCameraPolicy.NAVIGATOR_POSITION_FRESH_MS));
        assertTrue(InstrumentCameraPolicy.shouldUseFallbackPosition(
                6_000L, 6_001L + InstrumentCameraPolicy.NAVIGATOR_POSITION_FRESH_MS));
        assertTrue(InstrumentCameraPolicy.shouldUseFallbackPosition(10_000L, 9_000L));

        assertNear(2f, InstrumentCameraPolicy.headingDelta(359f, 1f), 0.001f);
        System.out.println("Instrument camera policy tests passed.");
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void assertNear(float expected, float actual, float tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
