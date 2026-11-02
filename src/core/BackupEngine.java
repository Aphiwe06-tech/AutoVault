package core;

import java.io.File;
import model.BackupJob;
import model.BackupVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class BackupEngine {

    private FileScanner fileScanner;

    public BackupEngine() {
        fileScanner = new FileScanner();
    }

    public void backupFolder(Path source, Path backupFolder)
            throws IOException {

        if (!Files.isDirectory(source)) {
            throw new IOException("Source folder does not exist.");
        }

        Path sourcePath = source.toAbsolutePath().normalize();
        Path backupPath = backupFolder.toAbsolutePath().normalize();

        if (sourcePath.equals(backupPath)
                || backupPath.startsWith(sourcePath)) {
            throw new IOException(
                    "Backup folder cannot be inside the source folder.");
        }

        Files.createDirectories(backupPath);

        List<Path> files = fileScanner.scanFiles(sourcePath);

        for (Path file : files) {

            Path relativePath = sourcePath.relativize(file);
            Path destination = backupPath.resolve(relativePath);

            Files.createDirectories(destination.getParent());

            Files.copy(
                    file,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println("Backed up: " + relativePath);
        }

        System.out.println("Backup completed.");
    }

    // Matches the UML: backs up one job into a new version folder.
    // Returns the new version, or null if the backup failed.
    public BackupVersion createBackup(BackupJob job) {
        File versionDir = new File(job.getDestinationDir(),
                "ver" + System.currentTimeMillis());
        try {
            backupFolder(job.getSourceDir().toPath(), versionDir.toPath());

            // Count the files and their total size for the version record.
            List<File> files = fileScanner.scan(versionDir);
            long size = 0;
            for (File f : files) {
                size += f.length();
            }
            return new BackupVersion(versionDir, files.size(), size);

        } catch (IOException e) {
            System.out.println("Backup failed: " + e.getMessage());
            return null;
        }
    }

    // Used to decide which files changed since the last backup.
    private ChangeDetector detector = new TimestampDetector();

    // Returns true if the job's source folder has changed since the last backup:
    // it is the first backup, a file was added or deleted, or a file was edited.
    public boolean hasChanges(BackupJob job) {
        List<BackupVersion> versions = job.getVersions();
        if (versions.isEmpty()) {
            return true;
        }

        // Find the newest version
        BackupVersion newest = versions.get(0);
        for (BackupVersion v : versions) {
            if (v.compareTo(newest) > 0) {
                newest = v;
            }
        }

        // A different number of files means a file was added or deleted
        List<File> files = fileScanner.scan(job.getSourceDir());
        if (files.size() != newest.getFileCount()) {
            return true;
        }

        // Otherwise check each file with the change detector
        for (File f : files) {
            if (detector.hasChanged(f, job.getLastRun())) {
                return true;
            }
        }
        return false;
    }
}