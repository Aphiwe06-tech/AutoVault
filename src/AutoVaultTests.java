package demo;

import core.BackupManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import model.BackupJob;
import model.BackupVersion;
import util.LogEntry;

/**
 * Test runner for AutoVault (console, no typing needed).
 * It runs normal-input tests (N), edge-input tests (E) and an optional
 * automatic-backup test (A) against the real BackupManager (any subclass works too) and prints
 * PASS or FAIL with what was observed. The full output is also saved to
 * test-results.txt so it can be pasted into the testing evidence.
 *
 * Run it with: right-click this file in NetBeans -> Run File (Shift+F6).
 * It uses its own folder in the temporary folder and keeps your real jobs.dat safe.
 */
public class AutoVaultTests {

    // Set to true to also test automatic backups (adds about 70 seconds)
    private static final boolean RUN_AUTOMATIC_TEST = false;

    // A fresh folder in the temporary folder each run, outside OneDrive and the project
    private static final File ROOT = new File(System.getProperty("java.io.tmpdir"),
            "autovault-tests-" + System.currentTimeMillis());
    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> report = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        // Keep the user's real jobs.dat safe: the tests save jobs too
        File realJobs = new File("jobs.dat");
        File safeCopy = new File("jobs.dat.before-tests");
        boolean hadJobs = realJobs.exists();
        if (hadJobs) {
            Files.move(realJobs.toPath(), safeCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            deleteTree(ROOT.toPath());
            ROOT.mkdirs();
            print("=== AutoVault Test Run ===");
            // Each group runs on its own, so one broken test cannot hide the others
            try {
                normalTests();
            } catch (Throwable t) {
                check("N-STOP", "Normal tests ran to the end", false, "stopped early: " + t);
            }
            try {
                edgeTests();
            } catch (Throwable t) {
                check("E-STOP", "Edge tests ran to the end", false, "stopped early: " + t);
            }
            if (RUN_AUTOMATIC_TEST) {
                automaticTest();
            }
            print("\n=== SUMMARY ===");
            print("Passed: " + passed);
            print("Failed: " + failed);
            Files.write(new File("test-results.txt").toPath(), report);
            print("Results saved to " + new File("test-results.txt").getAbsolutePath());
        } finally {
            Files.deleteIfExists(realJobs.toPath());
            if (hadJobs) {
                Files.move(safeCopy.toPath(), realJobs.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ================= NORMAL INPUT TESTS =================

    private static void normalTests() throws Exception {
        print("\n--- Normal input ---");
        File src = makeSource("normal");
        File dst = new File(ROOT, "normal/backup");
        File restored = new File(ROOT, "normal/restored");
        dst.mkdirs();

        BackupManager manager = new BackupManager();
        BackupJob job = new BackupJob("TestJob", src, dst, 1);
        manager.addJob(job);
        check("N1", "Add a valid job",
                manager.getJobs().size() == 1,
                "jobs in manager: " + manager.getJobs().size());

        BackupVersion v1 = manager.runBackup(job.getJobId());
        check("N2", "First backup creates a version",
                v1 != null && versions(manager, job).size() == 1,
                "versions: " + versions(manager, job).size());

        Thread.sleep(1100); // so the next version gets a different time
        write(new File(src, "report.txt"), "Report - second draft (edited)");
        manager.runBackup(job.getJobId());
        check("N3", "Backup after editing a file creates a second version",
                versions(manager, job).size() == 2,
                "versions: " + versions(manager, job).size());

        List<BackupVersion> list = versions(manager, job);
        boolean sorted = true;
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).getTimestamp().isBefore(list.get(i - 1).getTimestamp())) {
                sorted = false;
            }
        }
        check("N4", "Versions are listed oldest to newest", sorted && list.size() >= 2,
                "first=" + list.get(0).getVersionId() + ", last=" + list.get(list.size() - 1).getVersionId());

        manager.restore(job.getJobId(), list.get(0).getVersionId(), restored);
        File restoredReport = find(restored, "report.txt");
        String restoredText = restoredReport == null ? "(file not found)" : read(restoredReport);
        check("N5", "Restoring the oldest version brings back the old text",
                "Report - first draft".equals(restoredText),
                "restored report.txt says: " + restoredText);

        for (int i = 3; i <= 8; i++) { // eight versions in total
            Thread.sleep(1100);
            write(new File(src, "report.txt"), "Report - draft " + i);
            manager.runBackup(job.getJobId());
        }
        check("N6", "Retention keeps only the newest 5 versions after 8 backups",
                versions(manager, job).size() == 5,
                "versions kept: " + versions(manager, job).size());

        // Version folders on disk must match the versions kept (old folders are deleted)
        int folders = countVersionFolders(dst);
        check("N7", "Deleted versions' folders are removed from the disk",
                folders == versions(manager, job).size(),
                "version folders on disk: " + folders + ", versions in the job: " + versions(manager, job).size());

        manager.saveAll();
        BackupManager reloaded = new BackupManager();
        reloaded.loadAll();
        check("N8", "Jobs and versions survive a restart (save and load)",
                reloaded.getJobs().size() == 1
                        && reloaded.getJobs().get(0).getVersions().size() == versions(manager, job).size(),
                "after reload: " + reloaded.getJobs().size() + " job(s), "
                        + (reloaded.getJobs().isEmpty() ? 0 : reloaded.getJobs().get(0).getVersions().size())
                        + " version(s)");

        // A new backup must add lines to AutoVault.log
        long logBefore = logSize();
        Thread.sleep(1100);
        write(new File(src, "report.txt"), "Report - logged change");
        manager.runBackup(job.getJobId());
        check("N9", "A backup is recorded in AutoVault.log",
                logSize() > logBefore,
                "AutoVault.log size: " + logBefore + " bytes before, " + logSize() + " bytes after");

        // Folder and file names with spaces and brackets
        File spaced = new File(ROOT, "special folder/source folder");
        spaced.mkdirs();
        write(new File(spaced, "my report (final).txt"), "special name");
        File spacedDst = new File(ROOT, "special folder/backup folder");
        spacedDst.mkdirs();
        BackupJob spacedJob = new BackupJob("Spaced Job", spaced, spacedDst, 1);
        manager.addJob(spacedJob);
        BackupVersion sv = manager.runBackup(spacedJob.getJobId());
        File spacedRestore = new File(ROOT, "special folder/restored");
        if (sv != null) {
            manager.restore(spacedJob.getJobId(), sv.getVersionId(), spacedRestore);
        }
        check("N10", "Names with spaces and brackets are backed up and restored",
                find(spacedRestore, "my report (final).txt") != null,
                "restored file found: " + (find(spacedRestore, "my report (final).txt") != null));
    }

    // ================= EDGE INPUT TESTS =================

    private static void edgeTests() throws Exception {
        print("\n--- Edge input ---");
        File src = makeSource("edge");
        File dst = new File(ROOT, "edge/backup");
        dst.mkdirs();

        // Job validation (BackupJob rejects bad values)
        expectRejected("E1", "Empty job name is rejected", "name",
                () -> new BackupJob("   ", src, dst, 1));
        expectRejected("E2", "Missing folder (null) is rejected", "required",
                () -> new BackupJob("Job", null, dst, 1));
        expectRejected("E3", "Interval of 0 minutes is rejected", "at least 1 minute",
                () -> new BackupJob("Job", src, dst, 0));
        expectRejected("E4", "Negative interval is rejected", "at least 1 minute",
                () -> new BackupJob("Job", src, dst, -5));
        expectRejected("E5", "Source and destination the same folder is rejected", "different folders",
                () -> new BackupJob("Job", src, src, 1));
        expectRejected("E6", "Destination inside the source folder is rejected", "inside the source",
                () -> new BackupJob("Job", src, new File(src, "backups"), 1));

        BackupManager manager = new BackupManager();

        // Unknown ids must not crash the program
        boolean noCrash = true;
        Object result = "x";
        try {
            result = manager.runBackup("no-such-job");
        } catch (Throwable t) {
            noCrash = false;
        }
        check("E7", "Backup of an unknown job returns null without crashing",
                noCrash && result == null, "result: " + result);

        noCrash = true;
        result = "x";
        try {
            result = manager.listVersions("no-such-job");
        } catch (Throwable t) {
            noCrash = false;
        }
        check("E8", "Listing versions of an unknown job returns null without crashing",
                noCrash && result == null, "result: " + result);

        BackupJob job = new BackupJob("EdgeJob", src, dst, 1);
        manager.addJob(job);
        manager.runBackup(job.getJobId());
        noCrash = true;
        try {
            manager.restore(job.getJobId(), "no-such-version", new File(ROOT, "edge/restored"));
        } catch (Throwable t) {
            noCrash = false;
        }
        check("E9", "Restoring an unknown version does not crash", noCrash,
                "the program printed 'Version not found' and carried on");

        // Source folder deleted after the job was created
        File vanishing = makeSource("vanishing");
        File vanishingDst = new File(ROOT, "vanishing/backup");
        vanishingDst.mkdirs();
        BackupJob vanishingJob = new BackupJob("Vanishing", vanishing, vanishingDst, 1);
        manager.addJob(vanishingJob);
        deleteTree(vanishing.toPath());
        long logBeforeError = logSize();
        noCrash = true;
        result = "x";
        try {
            result = manager.runBackup(vanishingJob.getJobId());
        } catch (Throwable t) {
            noCrash = false;
        }
        check("E10", "Backup when the source folder was deleted fails safely",
                noCrash && result == null, "result: " + result);
        check("E15", "That failed backup is recorded in AutoVault.log",
                logSize() > logBeforeError,
                "AutoVault.log size: " + logBeforeError + " bytes before, " + logSize() + " bytes after");

        // Empty source folder
        File emptySrc = new File(ROOT, "empty/source");
        emptySrc.mkdirs();
        File emptyDst = new File(ROOT, "empty/backup");
        emptyDst.mkdirs();
        BackupJob emptyJob = new BackupJob("Empty", emptySrc, emptyDst, 1);
        manager.addJob(emptyJob);
        noCrash = true;
        result = "x";
        try {
            result = manager.runBackup(emptyJob.getJobId());
        } catch (Throwable t) {
            noCrash = false;
        }
        check("E11", "Backup of an empty folder does not crash", noCrash,
                "result: " + (result == null ? "no version created" : "version created"));

        // Two backups in a row with no changes
        File sameSrc = makeSource("nochange");
        File sameDst = new File(ROOT, "nochange/backup");
        sameDst.mkdirs();
        BackupJob sameJob = new BackupJob("NoChange", sameSrc, sameDst, 1);
        manager.addJob(sameJob);
        manager.runBackup(sameJob.getJobId());
        Thread.sleep(1100);
        manager.runBackup(sameJob.getJobId());
        int count = versions(manager, sameJob).size();
        check("E12", "A second backup with no changes is skipped", count == 1,
                count + " version(s) after two backups with no edit in between");

        // Restore into a folder that does not exist yet
        File deepTarget = new File(ROOT, "edge/new/folders/that/do/not/exist");
        List<BackupVersion> edgeVersions = versions(manager, job);
        if (!edgeVersions.isEmpty()) {
            manager.restore(job.getJobId(), edgeVersions.get(0).getVersionId(), deepTarget);
        }
        check("E13", "Restoring into a folder that does not exist yet works",
                find(deepTarget, "report.txt") != null,
                "restored file found: " + (find(deepTarget, "report.txt") != null));

        // Removed jobs cannot be backed up
        String removedId = job.getJobId();
        manager.removeJob(removedId);
        check("E14", "A removed job cannot be backed up",
                manager.runBackup(removedId) == null,
                "jobs left: " + manager.getJobs().size());
    }

    // ================= AUTOMATIC BACKUP TEST =================

    private static void automaticTest() {
        print("\n--- Automatic backups (takes about 70 seconds) ---");
        try {
            File src = makeSource("auto");
            File dst = new File(ROOT, "auto/backup");
            dst.mkdirs();
            BackupManager manager = new BackupManager();
            BackupJob job = new BackupJob("AutoJob", src, dst, 1);
            manager.addJob(job);
            manager.runBackup(job.getJobId());
            int before = versions(manager, job).size();
            manager.startAuto(job.getJobId());
            write(new File(src, "report.txt"), "changed while automatic backups run");
            Thread.sleep(70_000);
            manager.stopAuto(job.getJobId());
            int after = versions(manager, job).size();
            manager.shutdown();
            check("A1", "Automatic backup runs by itself after the interval",
                    after > before, "versions before: " + before + ", after: " + after);
        } catch (Throwable t) {
            check("A1", "Automatic backup runs by itself after the interval",
                    false, "error: " + t);
        }
    }

    // ================= HELPERS =================

    /** Records one result and prints it. */
    private static void check(String id, String description, boolean ok, String observed) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        print((ok ? "PASS  " : "FAIL  ") + id + "  " + description);
        print("        observed: " + observed);
    }

    /** Expects the action to throw IllegalArgumentException with the given text in its message. */
    private static void expectRejected(String id, String description, String expectedText, Runnable action) {
        try {
            action.run();
            check(id, description, false, "no error was thrown");
        } catch (IllegalArgumentException e) {
            boolean match = e.getMessage() != null && e.getMessage().toLowerCase().contains(expectedText);
            check(id, description, match, "message: \"" + e.getMessage() + "\"");
        } catch (Throwable t) {
            check(id, description, false, "unexpected error: " + t);
        }
    }

    private static void print(String line) {
        System.out.println(line);
        report.add(line);
    }

    private static List<BackupVersion> versions(BackupManager manager, BackupJob job) {
        List<BackupVersion> list = manager.listVersions(job.getJobId());
        return list == null ? new ArrayList<>() : list;
    }

    /** Creates a source folder with two small files. */
    private static File makeSource(String name) throws IOException {
        File source = new File(ROOT, name + "/source");
        source.mkdirs();
        write(new File(source, "report.txt"), "Report - first draft");
        write(new File(source, "notes.txt"), "Meeting notes");
        return source;
    }

    /** Size of AutoVault.log in bytes (0 if it does not exist yet). */
    private static long logSize() {
        File log = new File("AutoVault.log");
        return log.exists() ? log.length() : 0;
    }

    /** Counts the version folders (named ver...) under a backup folder. */
    private static int countVersionFolders(File dir) throws IOException {
        if (!dir.exists()) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return (int) walk.filter(Files::isDirectory)
                             .filter(p -> p.getFileName().toString().startsWith("ver"))
                             .count();
        }
    }

    private static void write(File file, String text) throws IOException {
        Files.writeString(file.toPath(), text);
    }

    private static String read(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    /** Finds a file by name anywhere under a folder (null if missing). */
    private static File find(File dir, String name) throws IOException {
        if (!dir.exists()) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(p -> p.getFileName().toString().equals(name))
                       .map(Path::toFile).findFirst().orElse(null);
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        }
    }
}