package ui;

import core.BackupManager;
import java.io.File;
import java.util.List;
import java.util.Scanner;
import model.BackupJob;
import model.BackupVersion;

/**
 * The console menu of AutoVault.
 * The user types choices here instead of clicking a window.
 */
public class CliMenu {
    private BackupManager manager;
    private Scanner in = new Scanner(System.in);

    /** Creates the menu and gives it the manager to use. */
    public CliMenu(BackupManager manager) {
        this.manager = manager;
    }

    /** Starts the menu: shows it again and again until the user chooses 0 (Exit). */
    public void start() {
        boolean running = true;
        while (running) {
            printMenu();
            String choice = readLine("Choose an option");
            if (choice == null) {
                break; // input was closed
            }
            switch (choice.trim()) {
                case "1": addJob(); break;
                case "2": listJobs(); break;
                case "3": backupNow(); break;
                case "4": listVersions(); break;
                case "5": restore(); break;
                case "6": startAuto(); break;
                case "7": stopAuto(); break;
                case "8": removeJob(); break;
                case "0": running = false; break;
                default: System.out.println("Invalid option, try again.");
            }
        }
        manager.shutdown(); // stops automatic backups and saves the jobs
        System.out.println("Goodbye.");
    }

    /** Prints the list of choices. */
    private void printMenu() {
        System.out.println();
        System.out.println("===== AutoVault =====");
        System.out.println("1) Add a backup job");
        System.out.println("2) List jobs");
        System.out.println("3) Back up now");
        System.out.println("4) List versions of a job");
        System.out.println("5) Restore a version");
        System.out.println("6) Start automatic backups");
        System.out.println("7) Stop automatic backups");
        System.out.println("8) Remove a job");
        System.out.println("0) Exit");
    }

    /** Asks for the job details and adds the new job to the manager. */
    private void addJob() {
        String name = readLine("Job name");
        String source = readLine("Folder to back up (full path)");
        String destination = readLine("Folder to store backups in (full path)");
        String minutesText = readLine("Backup interval in minutes");
        if (name == null || source == null || destination == null || minutesText == null) {
            return;
        }

        File sourceDir = new File(source.trim());
        if (!sourceDir.isDirectory()) {
            System.out.println("That source folder does not exist.");
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(minutesText.trim());
        } catch (NumberFormatException e) {
            System.out.println("The interval must be a whole number.");
            return;
        }

        try {
            // BackupJob checks its own values and throws if something is wrong
            BackupJob job = new BackupJob(name, sourceDir, new File(destination.trim()), minutes);
            manager.addJob(job);
            manager.saveAll();
            System.out.println("Job added: " + job);
        } catch (IllegalArgumentException e) {
            System.out.println("Could not add job: " + e.getMessage());
        }
    }

    /** Prints all jobs with a number the user can pick. */
    private void listJobs() {
        List<BackupJob> jobs = manager.getJobs();
        if (jobs.isEmpty()) {
            System.out.println("No jobs yet. Use option 1 to add one.");
            return;
        }
        for (int i = 0; i < jobs.size(); i++) {
            System.out.println((i + 1) + ") " + jobs.get(i));
        }
    }

    /** Runs a backup for the chosen job. */
    private void backupNow() {
        BackupJob job = pickJob();
        if (job == null) {
            return;
        }
        BackupVersion version = manager.runBackup(job.getJobId());
        if (version != null) {
            System.out.println("Backup created: " + version.getVersionId());
        }
    }

    /** Prints every version of the chosen job. */
    private void listVersions() {
        BackupJob job = pickJob();
        if (job != null) {
            printVersions(job);
        }
    }

    /** Lets the user pick a version and copies it into a target folder. */
    private void restore() {
        BackupJob job = pickJob();
        if (job == null || !printVersions(job)) {
            return;
        }
        List<BackupVersion> versions = manager.listVersions(job.getJobId());
        int number = readNumber("Version number to restore", versions.size());
        if (number < 1) {
            return;
        }
        String folder = readLine("Folder to restore into (a new folder is safest)");
        if (folder == null) {
            return;
        }
        manager.restore(job.getJobId(), versions.get(number - 1).getVersionId(), new File(folder.trim()));
    }

    /** Starts automatic backups for the chosen job. */
    private void startAuto() {
        BackupJob job = pickJob();
        if (job != null) {
            manager.startAuto(job.getJobId());
            System.out.println("Automatic backups started: every " + job.getIntervalMinutes() + " minute(s).");
        }
    }

    /** Stops automatic backups for the chosen job. */
    private void stopAuto() {
        BackupJob job = pickJob();
        if (job != null) {
            manager.stopAuto(job.getJobId());
            System.out.println("Automatic backups stopped.");
        }
    }

    /** Removes the chosen job from the manager. */
    private void removeJob() {
        BackupJob job = pickJob();
        if (job != null) {
            manager.stopAuto(job.getJobId());
            manager.removeJob(job.getJobId());
            manager.saveAll();
            System.out.println("Job removed.");
        }
    }

    /** Prints the versions (newest last); returns false if there is nothing to show. */
    private boolean printVersions(BackupJob job) {
        List<BackupVersion> versions = manager.listVersions(job.getJobId());
        if (versions == null || versions.isEmpty()) {
            System.out.println("No versions yet.");
            return false;
        }
        for (int i = 0; i < versions.size(); i++) {
            BackupVersion v = versions.get(i);
            System.out.println((i + 1) + ") " + v.getVersionId() + "  " + v.getTimestamp());
        }
        return true;
    }

    /** Shows the jobs and returns the one the user picks (null if none or invalid). */
    private BackupJob pickJob() {
        List<BackupJob> jobs = manager.getJobs();
        if (jobs.isEmpty()) {
            System.out.println("No jobs yet. Use option 1 to add one.");
            return null;
        }
        listJobs();
        int number = readNumber("Job number", jobs.size());
        return number < 1 ? null : jobs.get(number - 1);
    }

    /** Reads a number from 1 to max; prints a message and returns 0 if it is not valid. */
    private int readNumber(String prompt, int max) {
        String text = readLine(prompt);
        if (text == null) {
            return 0;
        }
        try {
            int n = Integer.parseInt(text.trim());
            if (n >= 1 && n <= max) {
                return n;
            }
        } catch (NumberFormatException e) {
            // fall through to the message below
        }
        System.out.println("Please type a number from 1 to " + max + ".");
        return 0;
    }

    /** Asks a question and returns the typed line (null if input is closed). */
    private String readLine(String prompt) {
        System.out.print(prompt + ": ");
        return in.hasNextLine() ? in.nextLine() : null;
    }
}
