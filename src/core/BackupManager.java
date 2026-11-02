package core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.BackupJob;
import model.BackupVersion;

public class BackupManager {

    // All backup jobs, stored by their job id
    private Map<String, BackupJob> jobs = new HashMap<>();

    // Runs backups automatically at each job's interval.
    // It is only created when automatic backups are first started (see startAuto).
    private BackupScheduler scheduler;
    private Thread schedulerThread;

    // Add a new job to the list
    public void addJob(BackupJob job) {
        jobs.put(job.getJobId(), job);
    }

    // Remove a job from the list
    public void removeJob(String jobId) {
        jobs.remove(jobId);
    }

    // Get all jobs (the menu uses this to show a numbered list)
    public List<BackupJob> getJobs() {
        return new ArrayList<>(jobs.values());
    }

    // Run a backup for one job and return the new version
    public BackupVersion runBackup(String jobId) {
        BackupJob job = jobs.get(jobId);
        if (job == null) {
            System.out.println("Job not found: " + jobId);
            return null;
        }

        BackupEngine engine = new BackupEngine();
        if (!engine.hasChanges(job)) {
            System.out.println("No changes in " + job.getName() + " since the last backup. Nothing to do.");
            return null;
        }
        BackupVersion version = engine.createBackup(job);
        if (version == null) {
            System.out.println("Backup failed for job: " + jobId);
            return null;
        }
        job.addVersion(version);

        // Delete old versions if there are too many
        RetentionPolicy policy = new RetentionPolicy(5);
        policy.enforce(job);

        return version;
    }

    // Get all versions of a job, sorted
    public List<BackupVersion> listVersions(String jobId) {
        BackupJob job = jobs.get(jobId);
        if (job == null) {
            System.out.println("Job not found: " + jobId);
            return null;
        }

        VersionSorter sorter = new VersionSorter();
        return sorter.mergeSort(job.getVersions());
    }

    // Copy one version back into the target folder
    public void restore(String jobId, String versionId, File target) {
        BackupJob job = jobs.get(jobId);
        if (job == null) {
            System.out.println("Job not found: " + jobId);
            return;
        }

        for (BackupVersion version : job.getVersions()) {
            if (version.getVersionId().equals(versionId)) {
                try {
                    RestoreService restoreService = new RestoreService();
                    restoreService.restore(version, target);
                    System.out.println("Restore finished.");
                } catch (Exception e) {
                    System.out.println("Restore failed: " + e.getMessage());
                }
                return;
            }
        }
        System.out.println("Version not found: " + versionId);
    }

    // Start automatic backups for one job (uses the job's interval)
    public void startAuto(String jobId) {
        BackupJob job = jobs.get(jobId);
        if (job == null) {
            System.out.println("Job not found: " + jobId);
            return;
        }
        if (scheduler == null) {
            scheduler = new BackupScheduler(this);
        }
        scheduler.schedule(job);
        if (schedulerThread == null) {            // start the scheduler loop only once
            schedulerThread = new Thread(scheduler, "scheduler");
            schedulerThread.setDaemon(true);      // so the program can still exit
            schedulerThread.start();
        }
    }

    // Stop automatic backups for one job
    public void stopAuto(String jobId) {
        if (scheduler != null) {
            scheduler.cancel(jobId);
        }
    }

    // Stop the scheduler and save the jobs; call this when the program exits
    public void shutdown() {
        if (scheduler != null) {
            scheduler.stop();
        }
        saveAll();
    }

    // Save all jobs to a file
    public void saveAll() {
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("jobs.dat"));
            out.writeObject(jobs);
            out.close();
        } catch (Exception e) {
            System.out.println("Could not save: " + e.getMessage());
        }
    }

    // Load all jobs from the file (if it exists)
    @SuppressWarnings("unchecked")
    public void loadAll() {
        File file = new File("jobs.dat");
        if (!file.exists()) {
            return;
        }
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
            jobs = (Map<String, BackupJob>) in.readObject();
            in.close();
        } catch (Exception e) {
            System.out.println("Could not load: " + e.getMessage());
        }
    }
}