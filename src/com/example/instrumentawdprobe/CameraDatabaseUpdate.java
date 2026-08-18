package com.example.instrumentawdprobe;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Safe extraction and replacement primitives for downloaded camera databases. */
final class CameraDatabaseUpdate {
    private CameraDatabaseUpdate() { }

    static void extractExactEntry(InputStream networkInput, File target,
                                  String expectedEntry, long maxArchiveBytes,
                                  long maxExtractedBytes) throws IOException {
        if (networkInput == null) throw new IOException("Пустой ответ сервера");
        boolean completed = false;
        boolean found = false;
        long extractedBytes = 0L;
        LimitedInputStream limited = new LimitedInputStream(networkInput, maxArchiveBytes);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(limited))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizedSafeName(entry.getName());
                if (!entry.isDirectory() && expectedEntry.equals(name)) {
                    if (found) throw new IOException("Повторяющийся файл базы в архиве");
                    found = true;
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            if (read == 0) continue;
                            extractedBytes += read;
                            if (extractedBytes > maxExtractedBytes) {
                                throw new IOException("Распакованная база слишком большая");
                            }
                            output.write(buffer, 0, read);
                        }
                        output.getFD().sync();
                    }
                }
                zip.closeEntry();
            }
            if (!found || extractedBytes == 0L || !target.isFile()) {
                throw new IOException("В архиве отсутствует " + expectedEntry);
            }
            completed = true;
        } finally {
            if (!completed && target.exists() && !target.delete()) {
                target.deleteOnExit();
            }
        }
    }

    static void replaceKeepingBackup(File temporary, File target, File backup)
            throws IOException {
        if (!temporary.isFile() || temporary.length() == 0L) {
            throw new IOException("Временный файл базы отсутствует или пуст");
        }
        if (backup.exists() && !backup.delete()) {
            throw new IOException("Не удалось очистить резервную копию базы");
        }
        boolean backedUp = target.exists();
        if (backedUp && !target.renameTo(backup)) {
            throw new IOException("Не удалось создать резервную копию базы");
        }
        if (!temporary.renameTo(target)) {
            if (backedUp && backup.exists() && !backup.renameTo(target)) {
                throw new IOException("Не удалось сохранить новую базу и выполнить откат");
            }
            throw new IOException("Не удалось сохранить новую базу");
        }
    }

    private static String normalizedSafeName(String rawName) throws IOException {
        if (rawName == null) throw new IOException("Пустое имя файла в архиве");
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.indexOf(':') >= 0) {
            throw new IOException("Небезопасное имя файла в архиве");
        }
        String[] segments = name.split("/", -1);
        StringBuilder normalized = new StringBuilder();
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new IOException("Небезопасное имя файла в архиве");
            }
            if (segment.length() == 0 || ".".equals(segment)) continue;
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximum;
        private long count;

        LimitedInputStream(InputStream input, long maximum) {
            super(input);
            this.maximum = maximum;
        }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) add(1L);
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) add(read);
            return read;
        }

        private void add(long bytes) throws IOException {
            count += bytes;
            if (count > maximum) {
                throw new IOException("Архив базы слишком большой");
            }
        }
    }
}
