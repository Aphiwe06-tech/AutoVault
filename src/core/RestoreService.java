
package core;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import model.BackupVersion;

public class RestoreService {

    public RestoreService() {
    }

    // Restore all files and folders from a saved backup.
    public void restoreBackup(Path backupFolder, Path restoreFolder)
            throws IOException {

        Path backupPath = backupFolder.toAbsolutePath().normalize();
        Path restorePath = restoreFolder.toAbsolutePath().normalize();

        // Check whether the selected backup exists.
        if (!Files.isDirectory(backupPath)) {
            throw new IOException("Backup folder does not exist.");
        }

        // Prevent restoring a backup into itself.
        if (backupPath.equals(restorePath)
                || restorePath.startsWith(backupPath)) {

            throw new IOException(
                    "Restore folder cannot be inside the backup folder."
            );
        }

        // Create the destination folder if necessary.
        Files.createDirectories(restorePath);

        // Visit every folder and file in the backup.
        Files.walkFileTree(backupPath, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes) throws IOException {

                // Recreate the original folder structure.
                Path relativePath = backupPath.relativize(directory);

                Path destination = restorePath.resolve(relativePath);

                Files.createDirectories(destination);

                System.out.println("Restored folder: " + relativePath);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes) throws IOException {

                if (!attributes.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }

                // Find the file's original relative location.
                Path relativePath = backupPath.relativize(file);

                Path destination = restorePath.resolve(relativePath);

                Files.createDirectories(destination.getParent());

                // Restore the file to its original relative location.
                Files.copy(
                        file,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );

                System.out.println("Restored file: " + relativePath);

                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("Restore completed successfully.");
    }

    // Restore the selected backup version.
    public void restore(BackupVersion version, File target)
            throws IOException {

        if (version == null || target == null) {
            throw new IllegalArgumentException(
                    "Backup version and restore destination are required."
            );
        }

        restoreBackup(
                version.getVersionDir().toPath(),
                target.toPath()
        );
    }
}
