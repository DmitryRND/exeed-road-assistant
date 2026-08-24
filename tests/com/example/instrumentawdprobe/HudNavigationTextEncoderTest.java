package com.example.instrumentawdprobe;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class HudNavigationTextEncoderTest {
    public static void main(String[] args) {
        assertRoundTrip("Камера 60 км/ч");
        assertRoundTrip("2-я Краснодарская ул.");
        assertRoundTrip("Очень длинное название улицы, которое должно быть аккуратно сокращено");
        assertSpaceForEmpty();
        System.out.println("HUD navigation text encoder OK");
    }

    private static void assertRoundTrip(String expectedPrefix) {
        byte[][] frames = HudNavigationTextEncoder.encode(expectedPrefix);
        if (frames.length < 1 || frames.length > 3) {
            throw new AssertionError("Unexpected frame count: " + frames.length);
        }
        int payloadLength = frames[0][1] & 0xff;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int remaining = payloadLength;
        for (int i = 0; i < frames.length; i++) {
            if ((frames[i][0] & 0xff) != i) {
                throw new AssertionError("Bad sequence at frame " + i);
            }
            int sourceOffset = i == 0 ? 2 : 1;
            int capacity = i == 0 ? 22 : 23;
            int count = Math.min(capacity, remaining);
            output.write(frames[i], sourceOffset, count);
            remaining -= count;
        }
        String actual = new String(output.toByteArray(), StandardCharsets.UTF_8);
        if (!expectedPrefix.startsWith(actual) || actual.isEmpty()) {
            throw new AssertionError("Bad round trip: '" + actual + "'");
        }
    }

    private static void assertSpaceForEmpty() {
        byte[][] frames = HudNavigationTextEncoder.encode("");
        if (frames.length != 1 || frames[0][1] != 1 || frames[0][2] != 0x20) {
            throw new AssertionError("Empty text was not encoded as a clearing space");
        }
    }
}
