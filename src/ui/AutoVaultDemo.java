package ui;

import core.BackupManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import model.BackupJob;
import model.BackupVersion;

/**
 * Scripted console demo of AutoVault (no typing needed).
 * It uses the real BackupManager: add a job, back up, change a file,
 * back up again, list the versions, then restore the oldest one.
 * Run it with: right-click this file in NetBeans -> Run File (Shift+F6).
 */
public class AutoVaultDemo {

    public static void main(String[] args) {
        try {
            runDemo();
        } catch (Exception e) {
            System.out.println("\nDemo stopped: " + e);
        }
    }

    private static void runDemo() throws Exception {
        System.out.println("=== AutoVault Prototype Demo ===");

        // Use a fresh folder in the computer's temporary folder every run.
        // (A folder inside the project can be locked by OneDrive or the IDE.)
        File root = Files.createTempDirectory("autovault-demo-").toFile();
        System.out.println("Demo folder: " + root.getAbsolutePath());
        File source = new File(root, "source");
        File backup = new File(root, "backup");
        File restored = new File(root, "restored");
        source.mkdirs();
        backup.mkdirs();

        // STEP 1: make a small folder to protect
        System.out.println("\n1. Creating a test folder with two files...");
        write(new File(source, "report.txt"), "Report - first draft");
        write(new File(source, "notes.txt"), "Meeting notes");
        System.out.println("   " + source.getAbsolutePath());

        // STEP 2: add a backup job
        System.out.println("\n2. Adding a backup job...");
        BackupManager manager = new BackupManager();
        BackupJob job = new BackupJob("DemoJob", source, backup, 1);
        manager.addJob(job);
        System.out.println("   Job added: " + job);

        // STEP 3: first backup
        System.out.println("\n3. Running the first backup...");
        BackupVersion v1 = manager.runBackup(job.getJobId());
        System.out.println("   Version created: " + (v1 == null ? "none" : v1.getVersionId()));
        System.out.println("   Files in the backup folder:");
        printTree(backup.toPath());

        // STEP 4: change a file and back up again
        System.out.println("\n4. Editing report.txt and backing up again...");
        Thread.sleep(1100); // so the second version gets a different time
        write(new File(source, "report.txt"), "Report - second draft (edited)");
        BackupVersion v2 = manager.runBackup(job.getJobId());
        System.out.println("   Version created: " + (v2 == null ? "none" : v2.getVersionId()));

        // STEP 5: list the versions
        System.out.println("\n5. Listing all versions of the job...");
        List<BackupVersion> versions = manager.listVersions(job.getJobId());
        if (versions == null || versions.isEmpty()) {
            System.out.println("   No versions found.");
            return;
        }
        for (int i = 0; i < versions.size(); i++) {
            System.out.println("   " + (i + 1) + ") " + versions.get(i).getVersionId()
                    + "  " + versions.get(i).getTimestamp());
        }

        // STEP 6: restore the oldest version
        System.out.println("\n6. Restoring the oldest version...");
        BackupVersion oldest = versions.get(0);
        manager.restore(job.getJobId(), oldest.getVersionId(), restored);
        File restoredReport = find(restored.toPath(), "report.txt");
        System.out.println("   report.txt now in the source folder : "
                + read(new File(source, "report.txt")));
        System.out.println("   report.txt in the restored version  : "
                + (restoredReport == null ? "NOT FOUND" : read(restoredReport)));

        System.out.println("\n=== Demo Complete ===");
    }

    // ---------- small helpers ----------

    private static void write(File file, String text) throws IOException {
        Files.writeString(file.toPath(), text);
    }

    private static String read(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    /** Prints every file under a folder, indented. */
    private static void printTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .forEach(p -> System.out.println("     " + dir.relativize(p)));
        }
    }

    /** Finds a file by name anywhere under a folder (null if missing). */
    private static File find(Path dir, String name) throws IOException {
        if (!Files.exists(dir)) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().equals(name))
                       .map(Path::toFile).findFirst().orElse(null);
        }
    }
}