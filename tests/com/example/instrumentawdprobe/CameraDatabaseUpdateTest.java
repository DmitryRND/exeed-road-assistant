package com.example.instrumentawdprobe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CameraDatabaseUpdateTest {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("mkdir failed");
        testValidArchive(root);
        testMissingEntry(root);
        testTraversalRejected(root);
        testDuplicateRejected(root);
        testExpandedLimit(root);
        testArchiveLimit(root);
        testReplacementAndBackup(root);
        if (args.length > 1) testLiveArchive(root, args[1]);
        System.out.println("Camera database update tests passed.");
    }

    private static void testValidArchive(File root) throws Exception {
        byte[] body = "one\ntwo\n".getBytes(StandardCharsets.UTF_8);
        byte[] archive = zip(new String[]{"PocketGisPlus.txt"}, new byte[][]{body});
        File output = fresh(root, "valid.txt");
        CameraDatabaseUpdate.extractExactEntry(new ByteArrayInputStream(archive), output,
                "PocketGisPlus.txt", archive.length + 10L, body.length + 10L);
        require(Arrays.equals(body, readAll(output)), "valid archive contents");
    }

    private static void testMissingEntry(File root) throws Exception {
        expectFailure(root, "missing.txt", zip(new String[]{"other.txt"},
                new byte[][]{{1}}), 1024L, 1024L, "отсутствует");
    }

    private static void testTraversalRejected(File root) throws Exception {
        expectFailure(root, "traversal.txt", zip(new String[]{"../PocketGisPlus.txt"},
                new byte[][]{{1}}), 1024L, 1024L, "Небезопасное");
    }

    private static void testDuplicateRejected(File root) throws Exception {
        expectFailure(root, "duplicate.txt", zip(
                new String[]{"PocketGisPlus.txt", "./PocketGisPlus.txt"},
                new byte[][]{{1}, {2}}), 2048L, 2048L, "Повторяющийся");
    }

    private static void testExpandedLimit(File root) throws Exception {
        byte[] body = new byte[2048];
        expectFailure(root, "expanded.txt", zip(new String[]{"PocketGisPlus.txt"},
                new byte[][]{body}), 4096L, 1024L, "Распакованная");
    }

    private static void testArchiveLimit(File root) throws Exception {
        byte[] body = new byte[2048];
        for (int i = 0; i < body.length; i++) body[i] = (byte) i;
        byte[] archive = zip(new String[]{"PocketGisPlus.txt"}, new byte[][]{body});
        expectFailure(root, "archive.txt", archive, Math.max(1L, archive.length - 1L),
                4096L, "Архив базы");
    }

    private static void testReplacementAndBackup(File root) throws Exception {
        File target = fresh(root, "target.txt");
        File temporary = fresh(root, "target.tmp");
        File backup = fresh(root, "target.bak");
        write(target, "old");
        write(temporary, "new");
        CameraDatabaseUpdate.replaceKeepingBackup(temporary, target, backup);
        require("new".equals(new String(readAll(target), StandardCharsets.UTF_8)),
                "new database installed");
        require("old".equals(new String(readAll(backup), StandardCharsets.UTF_8)),
                "old database retained as backup");
        require(!temporary.exists(), "temporary file consumed");
    }

    private static void testLiveArchive(File root, String sourceUrl) throws Exception {
        File output = fresh(root, "live-PocketGisPlus.txt");
        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(90000);
        connection.setRequestProperty("User-Agent", "EXEED-Road-Assistant/update-test");
        try {
            require(connection.getResponseCode() == HttpURLConnection.HTTP_OK,
                    "live update HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                CameraDatabaseUpdate.extractExactEntry(input, output,
                        "PocketGisPlus.txt", 10L * 1024L * 1024L,
                        25L * 1024L * 1024L);
            }
            SpeedCameraIndex index;
            try (InputStream input = new FileInputStream(output)) {
                index = SpeedCameraIndex.read(input);
            }
            require(index.size() >= 50000, "live database record count " + index.size());
            System.out.println("Live camera database OK: records=" + index.size()
                    + " date=" + index.databaseDate()
                    + " etag=" + connection.getHeaderField("ETag")
                    + " lastModified=" + connection.getHeaderField("Last-Modified"));
        } finally {
            connection.disconnect();
        }
    }

    private static void expectFailure(File root, String name, byte[] archive,
                                      long archiveLimit, long extractedLimit,
                                      String messagePart) throws Exception {
        File output = fresh(root, name);
        try {
            CameraDatabaseUpdate.extractExactEntry(new ByteArrayInputStream(archive), output,
                    "PocketGisPlus.txt", archiveLimit, extractedLimit);
            throw new AssertionError("Expected failure containing: " + messagePart);
        } catch (IOException expected) {
            require(expected.getMessage().contains(messagePart),
                    "failure message: " + expected.getMessage());
            require(!output.exists(), "partial output removed");
        }
    }

    private static byte[] zip(String[] names, byte[][] contents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < names.length; i++) {
                zip.putNextEntry(new ZipEntry(names[i]));
                zip.write(contents[i]);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static File fresh(File root, String name) {
        File file = new File(root, name);
        if (file.exists() && !file.delete()) throw new AssertionError("cleanup failed");
        return file;
    }

    private static void write(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] readAll(File file) throws IOException {
        byte[] result = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < result.length) {
                int read = input.read(result, offset, result.length - offset);
                if (read < 0) break;
                offset += read;
            }
            require(offset == result.length, "complete file read");
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
