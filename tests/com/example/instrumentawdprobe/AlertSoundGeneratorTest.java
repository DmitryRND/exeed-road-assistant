package com.example.instrumentawdprobe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class AlertSoundGeneratorTest {
    private static final int SAMPLE_RATE = 48000;

    public static void main(String[] args) throws Exception {
        File outputDirectory = args.length > 0 ? new File(args[0]) : null;
        if (outputDirectory != null && !outputDirectory.isDirectory()
                && !outputDirectory.mkdirs()) {
            throw new IOException("Cannot create preview directory: " + outputDirectory);
        }

        String[] names = {"soft-chord.wav", "smooth-navigation.wav", "soft-short.wav"};
        for (int style = 0; style < AlertSoundGenerator.STYLE_COUNT; style++) {
            byte[] pcm = AlertSoundGenerator.createPcm(SAMPLE_RATE, style);
            require(pcm.length == SAMPLE_RATE * AlertSoundGenerator.TOTAL_MS / 1000 * 4,
                    "wrong PCM length for style " + style);

            double peak = 0.0;
            double squareSum = 0.0;
            int frames = pcm.length / 4;
            for (int frame = 0; frame < frames; frame++) {
                int offset = frame * 4;
                short left = decodeShort(pcm, offset);
                short right = decodeShort(pcm, offset + 2);
                require(left == right, "stereo channels differ at frame " + frame);
                double normalized = left / 32768.0;
                peak = Math.max(peak, Math.abs(normalized));
                squareSum += normalized * normalized;
            }
            double rms = Math.sqrt(squareSum / frames);
            require(peak >= 0.48 && peak <= 0.88,
                    "unexpected peak for style " + style + ": " + peak);
            require(rms >= 0.10 && rms <= 0.28,
                    "unexpected RMS for style " + style + ": " + rms);
            require(maximumInRange(pcm, 0, SAMPLE_RATE * 50 / 1000) == 0,
                    "style " + style + " does not begin with silence");
            require(maximumInRange(pcm, SAMPLE_RATE * 1250 / 1000, frames) == 0,
                    "style " + style + " does not end with silence");

            if (outputDirectory != null) {
                writeWav(new File(outputDirectory, names[style]), pcm, SAMPLE_RATE);
            }
            System.out.printf("audio style=%d peak=%.3f rms=%.3f%n", style, peak, rms);
        }
    }

    private static int maximumInRange(byte[] pcm, int fromFrame, int toFrame) {
        int maximum = 0;
        for (int frame = fromFrame; frame < toFrame; frame++) {
            maximum = Math.max(maximum, Math.abs((int) decodeShort(pcm, frame * 4)));
        }
        return maximum;
    }

    private static short decodeShort(byte[] bytes, int offset) {
        return (short) ((bytes[offset] & 0xff) | (bytes[offset + 1] << 8));
    }

    private static void writeWav(File file, byte[] pcm, int sampleRate) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            writeAscii(output, "RIFF");
            writeLittleEndian(output, 36 + pcm.length, 4);
            writeAscii(output, "WAVEfmt ");
            writeLittleEndian(output, 16, 4);
            writeLittleEndian(output, 1, 2);
            writeLittleEndian(output, 2, 2);
            writeLittleEndian(output, sampleRate, 4);
            writeLittleEndian(output, sampleRate * 4, 4);
            writeLittleEndian(output, 4, 2);
            writeLittleEndian(output, 16, 2);
            writeAscii(output, "data");
            writeLittleEndian(output, pcm.length, 4);
            output.write(pcm);
        }
    }

    private static void writeAscii(FileOutputStream output, String value) throws IOException {
        output.write(value.getBytes("US-ASCII"));
    }

    private static void writeLittleEndian(FileOutputStream output, int value, int bytes)
            throws IOException {
        for (int index = 0; index < bytes; index++) {
            output.write((value >> (index * 8)) & 0xff);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
