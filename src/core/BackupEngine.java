package core;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import model.BackupJob;
import model.BackupVersion;

public class BackupEngine {

    private final FileScanner fileScanner;

    public BackupEngine() {
        fileScanner = new FileScanner();
    }

    // Backup without excluded extensions.
    public void backupFolder(Path source, Path backupFolder)
            throws IOException {

        backupFolder(source, backupFolder, Collections.emptySet());
    }

    // Backup a folder while excluding selected file extensions.
    private void backupFolder(
            Path source,
            Path backupFolder,
            Set<String> excludedExtensions) throws IOException {

        Path sourcePath = source.toAbsolutePath().normalize();
        Path backupPath = backupFolder.toAbsolutePath().normalize();

        if (!Files.isDirectory(sourcePath)) {
            throw new IOException("Source folder does not exist.");
        }

        if (sourcePath.equals(backupPath)
                || backupPath.startsWith(sourcePath)) {

            throw new IOException(
                    "Backup folder cannot be inside the source folder."
            );
        }

        Files.createDirectories(backupPath);

        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes) throws IOException {

                // Preserve the folder structure.
                Path relative = sourcePath.relativize(directory);

                Files.createDirectories(backupPath.resolve(relative));

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes) throws IOException {

                // Ignore files with excluded extensions.
                if (isExcluded(file, excludedExtensions)) {
                    System.out.println("Skipped: " + file.getFileName());
                    return FileVisitResult.CONTINUE;
                }

                if (!attributes.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }

                Path relative = sourcePath.relativize(file);
                Path destination = backupPath.resolve(relative);

                Files.createDirectories(destination.getParent());

                Files.copy(
                        file,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );

                System.out.println("Backed up: " + relative);

                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("Backup completed.");
    }

    // Create a new version for the selected backup job.
    public BackupVersion createBackup(BackupJob job) {

        File versionDir = new File(
                job.getDestinationDir(),
                "ver" + System.currentTimeMillis()
        );

        try {

            backupFolder(
                    job.getSourceDir().toPath(),
                    versionDir.toPath(),
                    job.getExcludedExtensions()
            );

            List<File> files = fileScanner.scan(versionDir);

            long totalSize = 0;

            for (File file : files) {
                totalSize += file.length();
            }

            return new BackupVersion(
                    versionDir,
                    files.size(),
                    totalSize
            );

        } catch (IOException e) {

            System.out.println(
                    "Backup failed: " + e.getMessage()
            );

            return null;
        }
    }

    // Determine whether a file should be excluded.
    private boolean isExcluded(
            Path file,
            Set<String> excludedExtensions) {

        String filename = file.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        for (String extension : excludedExtensions) {

            if (filename.endsWith(
                    extension.toLowerCase(Locale.ROOT))) {

                return true;
            }
        }

        return false;
    }

    // Check whether the source differs from the latest backup.
    public boolean hasChanges(BackupJob job) {

        List<BackupVersion> versions = job.getVersions();

        // First backup always creates a version.
        if (versions.isEmpty()) {
            return true;
        }

        // Find the newest version.
        BackupVersion newest = versions.get(0);

        for (BackupVersion version : versions) {

            if (version.compareTo(newest) > 0) {
                newest = version;
            }
        }

        Path source = job.getSourceDir()
                .toPath()
                .toAbsolutePath()
                .normalize();

        Path previous = newest.getVersionDir()
                .toPath()
                .toAbsolutePath()
                .normalize();

        if (!Files.isDirectory(source)
                || !Files.isDirectory(previous)) {

            return true;
        }

        try {

            List<Path> sourceItems = new ArrayList<>();
            List<Path> previousItems = new ArrayList<>();

            // Scan the source, ignoring excluded files.
            try (Stream<Path> paths = Files.walk(source)) {

                paths.filter(path -> !path.equals(source))
                        .filter(path ->
                                !Files.isRegularFile(path)
                                || !isExcluded(
                                        path,
                                        job.getExcludedExtensions()
                                ))
                        .forEach(sourceItems::add);
            }

            // Scan the most recent backup version.
            try (Stream<Path> paths = Files.walk(previous)) {

                paths.filter(path -> !path.equals(previous))
                        .forEach(previousItems::add);
            }

            // Detect added or deleted files and folders.
            if (sourceItems.size() != previousItems.size()) {
                return true;
            }

            // Compare the source with the previous backup.
            for (Path current : sourceItems) {

                Path relative = source.relativize(current);
                Path oldFile = previous.resolve(relative);

                if (Files.isDirectory(current)) {

                    if (!Files.isDirectory(oldFile)) {
                        return true;
                    }

                } else if (Files.isRegularFile(current)) {

                    if (!Files.isRegularFile(oldFile)) {
                        return true;
                    }

                    // Compare actual file contents.
                    if (Files.mismatch(current, oldFile) != -1) {
                        return true;
                    }
                }
            }

            return false;

        } catch (IOException e) {

            System.out.println(
                    "Change detection failed: " + e.getMessage()
            );

            // Attempt a new backup if comparison fails.
            return true;
        }
    }
}
