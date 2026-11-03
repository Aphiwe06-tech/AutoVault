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

    // Stores all backup jobs.
    private Map<String, BackupJob> jobs = new HashMap<>();

    // Controls automatic backups.
    private BackupScheduler scheduler;
    private Thread schedulerThread;

    // Records application activities.
    private final BackupLogger logger = new BackupLogger();

    // Add a backup job.
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

    // Return all registered jobs.
    public List<BackupJob> getJobs() {

        return new ArrayList<>(jobs.values());
    }

    // Perform a backup.
    public BackupVersion runBackup(String jobId) {

        BackupJob job = jobs.get(jobId);

        if (job == null) {

            logger.warn("Unknown backup job: " + jobId);

            System.out.println("Job not found: " + jobId);

            return null;
        }

        try {

            // Check that the source folder exists.
            if (!job.getSourceDir().isDirectory()) {

                IOException exception = new IOException(
                        "Source folder does not exist: "
                        + job.getSourceDir().getAbsolutePath()
                );

                logger.error(
                        "Backup failed for job: " + job.getName(),
                        exception
                );

                System.out.println(exception.getMessage());

                return null;
            }

            BackupEngine engine = new BackupEngine();

            // Skip unchanged folders.
            if (!engine.hasChanges(job)) {

                logger.info(
                        "Backup skipped for "
                        + job.getName()
                        + ": no changes detected"
                );

                System.out.println(
                        "No changes since the last backup."
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

                return null;
            }

            // Add the new version to the job.
            job.addVersion(version);

            // Keep the five most recent versions.
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

            // Save the updated version history.
            saveAll();

            return version;

        } catch (Exception exception) {

            logger.error(
                    "Unexpected backup failure: " + job.getName(),
                    exception
            );

            System.out.println(
                    "Backup failed: " + exception.getMessage()
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

        return VersionSorter.mergeSort(job.getVersions());
    }

    // Restore a selected backup version.
    // Returns true on success and false on failure.
    public boolean restore(
            String jobId,
            String versionId,
            File target) {

        BackupJob job = jobs.get(jobId);

        if (job == null) {

            logger.warn("Restore requested for unknown job: " + jobId);

            System.out.println("Job not found: " + jobId);

            return false;
        }

        if (target == null) {

            logger.warn("Restore destination was not selected.");

            System.out.println("Restore destination is required.");

            return false;
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

                    return true;

                } catch (Exception exception) {

                    logger.error(
                            "Restore failed for job: " + job.getName(),
                            exception
                    );

                    System.out.println(
                            "Restore failed: "
                            + exception.getMessage()
                    );

                    return false;
                }
            }
        }

        logger.warn(
                "Restore failed: version not found: " + versionId
        );

        System.out.println("Version not found: " + versionId);

        return false;
    }

    // Start automatic backups for the selected job.
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

        // Start the scheduler thread only once.
        if (schedulerThread == null) {

            schedulerThread = new Thread(
                    scheduler,
                    "AutoVault-Scheduler"
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

    // Stop automatic backups for one job.
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

    // Save all jobs and their backup versions.
    public void saveAll() {

        try (ObjectOutputStream output =
                new ObjectOutputStream(
                        new FileOutputStream("jobs.dat"))) {

            output.writeObject(jobs);

        } catch (Exception exception) {

            logger.error(
                    "Could not save backup jobs",
                    exception
            );

            System.out.println(
                    "Could not save: "
                    + exception.getMessage()
            );
        }
    }

    // Load previously saved jobs.
    @SuppressWarnings("unchecked")
    public void loadAll() {

        File file = new File("jobs.dat");

        if (!file.exists()) {

            return;
        }

        try (ObjectInputStream input =
                new ObjectInputStream(
                        new FileInputStream(file))) {

            jobs = (Map<String, BackupJob>) input.readObject();

            logger.info(
                    "Saved backup jobs loaded successfully"
            );

        } catch (Exception exception) {

            logger.error(
                    "Could not load saved backup jobs",
                    exception
            );

            System.out.println(
                    "Could not load: "
                    + exception.getMessage()
            );
        }
    }
}
