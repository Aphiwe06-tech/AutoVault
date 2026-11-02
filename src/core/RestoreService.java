

package core;

import java.io.File;
import model.BackupVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class RestoreService {

    private FileScanner fileScanner;

    public RestoreService() {
        fileScanner = new FileScanner();
    }

    public void restoreBackup(Path backupFolder, Path restoreFolder)
            throws IOException {

        if (!Files.isDirectory(backupFolder)) {
            throw new IOException("Backup folder does not exist.");
        }

        Path backupPath = backupFolder.toAbsolutePath().normalize();
        Path restorePath = restoreFolder.toAbsolutePath().normalize();

        if (backupPath.equals(restorePath)
                || restorePath.startsWith(backupPath)) {
            throw new IOException(
                    "Restore folder cannot be inside the backup folder.");
        }

        Files.createDirectories(restorePath);

        List<Path> files = fileScanner.scanFiles(backupPath);

        for (Path file : files) {

            Path relativePath = backupPath.relativize(file);
            Path destination = restorePath.resolve(relativePath);

            Files.createDirectories(destination.getParent());

            Files.copy(
                    file,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println("Restored: " + relativePath);
        }

        System.out.println("Restore completed.");
    }

    // Matches the UML: copies one saved version into the target folder.
    public void restore(BackupVersion version, File target)
            throws IOException {
        restoreBackup(version.getVersionDir().toPath(), target.toPath());
    }
}