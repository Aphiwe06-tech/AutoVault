
package model;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackupVersion implements Serializable, Comparable<BackupVersion> {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String versionId;
    private final LocalDateTime timestamp;
    private final File versionDir;
    private final int fileCount;
    private final long totalSizeBytes;

    public BackupVersion(File versionDir, int fileCount, long size) {
        if (versionDir == null) {
            throw new IllegalArgumentException("Version folder must not be null.");
        }
        if (fileCount < 0 || size < 0) {
            throw new IllegalArgumentException("File count and size cannot be negative.");
        }
        this.timestamp = LocalDateTime.now();
        this.versionId = timestamp.format(ID_FORMAT);
        this.versionDir = versionDir;
        this.fileCount = fileCount;
        this.totalSizeBytes = size;
    }

    public String getVersionId()         { return versionId; }
    public LocalDateTime getTimestamp()  { return timestamp; }
    public File getVersionDir()          { return versionDir; }
    public int getFileCount()            { return fileCount; }
    public long getTotalSizeBytes()      { return totalSizeBytes; }

    @Override
    public int compareTo(BackupVersion other) {
        return this.timestamp.compareTo(other.timestamp);
    }

    public String formattedSize() {
        if (totalSizeBytes < 1024) return totalSizeBytes + " B";
        double kb = totalSizeBytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    @Override
    public String toString() {
        return versionId + " | " + timestamp.format(DISPLAY_FORMAT)
                + " | " + fileCount + " file(s) | " + formattedSize();
    }
}