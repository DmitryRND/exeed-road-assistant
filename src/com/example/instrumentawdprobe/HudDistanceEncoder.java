package com.example.instrumentawdprobe;

/** Encodes the five-byte distance payload expected by the TXL2 HUD VHAL properties. */
final class HudDistanceEncoder {
    static final int UNIT_METERS = 1;

    private HudDistanceEncoder() {}

    static byte[] encodeMeters(int distanceMeters) {
        int safeMeters = Math.max(0, distanceMeters);
        long decimetersLong = (long) safeMeters * 10L;
        int decimeters = decimetersLong > Integer.MAX_VALUE
                ? Integer.MAX_VALUE : (int) decimetersLong;
        return new byte[]{
                (byte) (decimeters & 0xff),
                (byte) ((decimeters >>> 8) & 0xff),
                (byte) ((decimeters >>> 16) & 0xff),
                (byte) ((decimeters >>> 24) & 0xff),
                (byte) UNIT_METERS
        };
    }
}
