
package model;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BackupJob implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String jobId;
    private String name;
    private File sourceDir;
    private File destinationDir;
    private int intervalMinutes;
    private boolean enabled = true;
    private LocalDateTime lastRun;                      
    private final Set<String> excludedExtensions = new HashSet<>();
    private final List<BackupVersion> versions = new ArrayList<>();

    public BackupJob(String name, File sourceDir, File destinationDir, int intervalMinutes) {
        validate(name, sourceDir, destinationDir, intervalMinutes);
        this.jobId = UUID.randomUUID().toString();
        this.name = name.trim();
        this.sourceDir = sourceDir;
        this.destinationDir = destinationDir;
        this.intervalMinutes = intervalMinutes;
    }

    private static void validate(String name, File src, File dest, int interval) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Job name must not be empty.");
        }
        if (src == null || dest == null) {
            throw new IllegalArgumentException("Source and destination folders are required.");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("Interval must be at least 1 minute.");
        }
        if (src.getAbsoluteFile().equals(dest.getAbsoluteFile())) {
            throw new IllegalArgumentException("Source and destination must be different folders.");
        }
        if (dest.getAbsoluteFile().toPath().startsWith(src.getAbsoluteFile().toPath())) {
            throw new IllegalArgumentException("Destination cannot be inside the source folder.");
        }
    }

    public synchronized void addVersion(BackupVersion v) {
        if (v == null) {
            throw new IllegalArgumentException("Version must not be null.");
        }
        versions.add(v);
        lastRun = v.getTimestamp();
    }

    public synchronized boolean removeVersion(BackupVersion v) {
        return versions.remove(v);
    }

    public synchronized List<BackupVersion> getVersions() {
        return new ArrayList<>(versions);
    }

    public synchronized boolean isDue() {
        if (!enabled) return false;
        if (lastRun == null) return true;
        return !LocalDateTime.now().isBefore(lastRun.plusMinutes(intervalMinutes));
    }

    public String getJobId()               { return jobId; }
    public String getName()                { return name; }
    public File getSourceDir()             { return sourceDir; }
    public File getDestinationDir()        { return destinationDir; }
    public int getIntervalMinutes()        { return intervalMinutes; }
    public boolean isEnabled()             { return enabled; }
    public synchronized LocalDateTime getLastRun() { return lastRun; }

    public Set<String> getExcludedExtensions() {
        return Collections.unmodifiableSet(excludedExtensions);
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Job name must not be empty.");
        }
        this.name = name.trim();
    }

    public void setSourceDir(File sourceDir) {
        validate(name, sourceDir, destinationDir, intervalMinutes);
        this.sourceDir = sourceDir;
    }

    public void setDestinationDir(File destinationDir) {
        validate(name, sourceDir, destinationDir, intervalMinutes);
        this.destinationDir = destinationDir;
    }

    public void setIntervalMinutes(int intervalMinutes) {
        if (intervalMinutes <= 0) {
            throw new IllegalArgumentException("Interval must be at least 1 minute.");
        }
        this.intervalMinutes = intervalMinutes;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void addExcludedExtension(String ext) {
        if (ext == null || ext.trim().isEmpty()) return;
        String e = ext.trim().toLowerCase();
        excludedExtensions.add(e.startsWith(".") ? e : "." + e);
    }

    @Override
    public String toString() {
        return name + " [" + sourceDir + " -> " + destinationDir + ", every "
                + intervalMinutes + " min]";
    }
}