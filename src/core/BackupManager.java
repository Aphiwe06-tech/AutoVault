
package core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.BackupJob;
import model.BackupVersion;
import util.BackupLogger;

public class BackupManager {

    private Map<String, BackupJob> jobs = new HashMap<>();

    private BackupScheduler scheduler;
    private Thread schedulerThread;

    // Records backup, restore, scheduling and error events.
    private final BackupLogger logger = new BackupLogger();

    // Add a new backup job.
    public void addJob(BackupJob job) {
        jobs.put(job.getJobId(), job);

        logger.info("Backup job added: " + job.getName());
        saveAll();
    }

    // Remove a backup job.
    public void removeJob(String jobId) {

        BackupJob job = jobs.get(jobId);

        if (job != null) {
            stopAuto(jobId);
            jobs.remove(jobId);

            logger.info("Backup job removed: " + job.getName());
            saveAll();
        }
    }

    // Return all registered backup jobs.
    public List<BackupJob> getJobs() {
        return new ArrayList<>(jobs.values());
    }

    // Perform a backup and record its result.
    public BackupVersion runBackup(String jobId) {

        BackupJob job = jobs.get(jobId);

        if (job == null) {
            logger.warn("Backup requested for unknown job: " + jobId);
            System.out.println("Job not found: " + jobId);
            return null;
        }

        try {

            // Check that the source folder still exists.
            if (!job.getSourceDir().isDirectory()) {

                IOException error = new IOException(
                        "Source folder does not exist: "
                        + job.getSourceDir().getAbsolutePath()
                );

                logger.error(
                        "Backup failed for job: " + job.getName(),
                        error
                );

                System.out.println(error.getMessage());
                return null;
            }

            BackupEngine engine = new BackupEngine();

            // Skip the backup if nothing has changed.
            if (!engine.hasChanges(job)) {

                logger.info(
                        "Backup skipped for "
                        + job.getName()
                        + ": no changes detected"
                );

                System.out.println(
                        "No changes in " + job.getName()
                        + " since the last backup. Nothing to do."
                );

                return null;
            }

            // Create a new backup version.
            BackupVersion version = engine.createBackup(job);

            if (version == null) {

                logger.error(
                        "Backup failed for job: " + job.getName(),
                        new IOException("Could not create backup version")
                );

                System.out.println(
                        "Backup failed for job: " + jobId
                );

                return null;
            }

            job.addVersion(version);

            // Keep the five most recent backup versions.
            RetentionPolicy policy = new RetentionPolicy(5);
            int removed = policy.enforce(job);

            logger.info(
                    "Backup successful: "
                    + job.getName()
                    + " | Version: "
                    + version.getVersionId()
            );

            if (removed > 0) {
                logger.info(
                        "Retention removed "
                        + removed
                        + " old version(s) for "
                        + job.getName()
                );
            }

            // Preserve updated version information.
            saveAll();

            return version;

        } catch (Exception e) {

            logger.error(
                    "Unexpected backup failure: " + job.getName(),
                    e
            );

            System.out.println(
                    "Backup failed: " + e.getMessage()
            );

            return null;
        }
    }

    // Return backup versions in chronological order.
    public List<BackupVersion> listVersions(String jobId) {

        BackupJob job = jobs.get(jobId);

        if (job == null) {
            logger.warn("Versions requested for unknown job: " + jobId);
            System.out.println("Job not found: " + jobId);
            return null;
        }

        VersionSorter sorter = new VersionSorter();

        return sorter.mergeSort(job.getVersions());
    }

    // Restore a selected backup version.
    public void restore(String jobId, String versionId, File target) {

        BackupJob job = jobs.get(jobId);

        if (job == null) {
            logger.warn("Restore requested for unknown job: " + jobId);
            System.out.println("Job not found: " + jobId);
            return;
        }

        for (BackupVersion version : job.getVersions()) {

            if (version.getVersionId().equals(versionId)) {

                try {

                    RestoreService restoreService =
                            new RestoreService();

                    restoreService.restore(version, target);

                    logger.info(
                            "Restore successful: "
                            + job.getName()
                            + " | Version: "
                            + versionId
                            + " | Destination: "
                            + target.getAbsolutePath()
                    );

                    System.out.println("Restore finished.");

                } catch (Exception e) {

                    logger.error(
                            "Restore failed for job: " + job.getName(),
                            e
                    );

                    System.out.println(
                            "Restore failed: " + e.getMessage()
                    );
                }

                return;
            }
        }

        logger.warn(
                "Restore failed: version not found: " + versionId
        );

        System.out.println("Version not found: " + versionId);
    }

    // Start scheduled backups for a job.
    public void startAuto(String jobId) {

        BackupJob job = jobs.get(jobId);

        if (job == null) {
            logger.warn("Cannot schedule unknown job: " + jobId);
            System.out.println("Job not found: " + jobId);
            return;
        }

        if (scheduler == null) {
            scheduler = new BackupScheduler(this);
        }

        scheduler.schedule(job);

        if (schedulerThread == null) {

            schedulerThread = new Thread(
                    scheduler, "AutoVault-Scheduler"
            );

            schedulerThread.setDaemon(true);
            schedulerThread.start();
        }

        logger.info(
                "Automatic backup started: "
                + job.getName()
                + " | Interval: "
                + job.getIntervalMinutes()
                + " minute(s)"
        );
    }

    // Stop scheduled backups for a job.
    public void stopAuto(String jobId) {

        if (scheduler != null) {
            scheduler.cancel(jobId);

            logger.info(
                    "Automatic backup stopped for job: " + jobId
            );
        }
    }

    // Stop scheduling and save all jobs.
    public void shutdown() {

        if (scheduler != null) {
            scheduler.stop();
        }

        saveAll();

        logger.info("AutoVault shutdown completed");
    }

    // Save backup jobs and their version history.
    public void saveAll() {

        try (ObjectOutputStream out =
                new ObjectOutputStream(
                        new FileOutputStream("jobs.dat"))) {

            out.writeObject(jobs);

        } catch (Exception e) {

            logger.error("Could not save backup jobs", e);

            System.out.println(
                    "Could not save: " + e.getMessage()
            );
        }
    }

    // Load saved backup jobs.
    @SuppressWarnings("unchecked")
    public void loadAll() {

        File file = new File("jobs.dat");

        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream in =
                new ObjectInputStream(
                        new FileInputStream(file))) {

            jobs = (Map<String, BackupJob>) in.readObject();

            logger.info("Saved backup jobs loaded successfully");

        } catch (Exception e) {

            logger.error("Could not load saved backup jobs", e);

            System.out.println(
                    "Could not load: " + e.getMessage()
            );
        }
    }
}
