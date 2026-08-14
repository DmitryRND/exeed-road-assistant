package com.example.instrumentawdprobe;

import java.util.Arrays;

public final class HudDistanceEncoderTest {
    public static void main(String[] args) {
        assertFrame(350, new int[]{0xac, 0x0d, 0x00, 0x00, 0x01});
        assertFrame(600, new int[]{0x70, 0x17, 0x00, 0x00, 0x01});
        assertFrame(0, new int[]{0x00, 0x00, 0x00, 0x00, 0x01});
        assertFrame(-10, new int[]{0x00, 0x00, 0x00, 0x00, 0x01});
        System.out.println("HUD distance encoder OK");
    }

    private static void assertFrame(int meters, int[] expected) {
        byte[] actual = HudDistanceEncoder.encodeMeters(meters);
        int[] unsigned = new int[actual.length];
        for (int i = 0; i < actual.length; i++) unsigned[i] = actual[i] & 0xff;
        if (!Arrays.equals(expected, unsigned)) {
            throw new AssertionError(meters + "m: expected " + Arrays.toString(expected)
                    + ", actual " + Arrays.toString(unsigned));
        }
    }
}
