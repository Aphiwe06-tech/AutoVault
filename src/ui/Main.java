
package ui;

import core.BackupManager;
import model.BackupJob;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            // Create the backup manager.
            BackupManager manager = new BackupManager();

            // Load previously saved backup jobs.
            manager.loadAll();

            // Automatically restart enabled backup jobs.
            for (BackupJob job : manager.getJobs()) {
                if (job.isEnabled()) {
                    manager.startAuto(job.getJobId());
                }
            }

            // Open the main AutoVault application.
            MainWindow window = new MainWindow(manager);
            window.show();
        });
    }
}
