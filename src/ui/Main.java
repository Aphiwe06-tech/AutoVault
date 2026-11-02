package ui;

import core.BackupManager;

public class Main {

    public static void main(String[] args) {
        BackupManager manager = new BackupManager();
        manager.loadAll();

        // Creates the window (GUI)
        MainWindow window = new MainWindow(manager);
        window.show();

        // Creates the console menu; this runs until the user chooses Exit
        // (Exit also saves the jobs and stops automatic backups)
        CliMenu menu = new CliMenu(manager);
        menu.start();

        // Close the window and end the program
        System.exit(0);
    }
}