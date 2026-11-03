package ui;


import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import util.BackupLogger;
import util.LogEntry;

public class AutoVaultRole6Prototype extends JFrame {

    // Uses the separate BackupLogger class
    private final BackupLogger logger = new BackupLogger();

    // Displays logs, QA results and documentation
    private JTextArea displayArea;

    // Creates the main application window
    public AutoVaultRole6Prototype() {
        setTitle("AutoVault - Role 6: QA and Logging");
        setSize(800, 550);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Application heading
        JLabel heading = new JLabel(
                "AutoVault - Quality Assurance and Logging",
                SwingConstants.CENTER
        );

        heading.setFont(new Font("Arial", Font.BOLD, 20));
        heading.setBorder(
                BorderFactory.createEmptyBorder(15, 10, 15, 10)
        );

        add(heading, BorderLayout.NORTH);

        // Create the navigation buttons
        JPanel buttonPanel = new JPanel(
                new GridLayout(0, 1, 5, 5)
        );

        JButton dashboardButton = new JButton("Dashboard");
        JButton logsButton = new JButton("Activity Logs");
        JButton testButton = new JButton("Run QA Tests");
        JButton exportButton = new JButton("Export Logs");
        JButton clearButton = new JButton("Clear Display");

        buttonPanel.add(dashboardButton);
        buttonPanel.add(logsButton);
        buttonPanel.add(testButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(clearButton);

        buttonPanel.setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );

        add(buttonPanel, BorderLayout.WEST);

        // Create the display area
        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setFont(
                new Font("Monospaced", Font.PLAIN, 13)
        );
        displayArea.setMargin(new Insets(10, 10, 10, 10));

        add(new JScrollPane(displayArea), BorderLayout.CENTER);

        // Connect buttons to their functions
        dashboardButton.addActionListener(e -> showDashboard());
        logsButton.addActionListener(e -> showLogs());
        testButton.addActionListener(e -> runQATests());
        exportButton.addActionListener(e -> exportLogs());
        clearButton.addActionListener(e -> displayArea.setText(""));

        // Record application startup
        logger.log("INFO", "AutoVault prototype started");

        showDashboard();
    }

    // Displays the main dashboard
    private void showDashboard() {
        displayArea.setText(
                "====================================\n"
                + "          AUTOVAULT DASHBOARD\n"
                + "====================================\n\n"
                + "Welcome to AutoVault!\n\n"
                + "Available functions:\n"
                + "1. View activity logs\n"
                + "2. Run quality assurance tests\n"
                + "3. Export activity logs\n\n"
        );

        logger.log("INFO", "Dashboard opened");
    }

    // Displays recorded activities
    private void showLogs() {
        displayArea.setText(
                "========== ACTIVITY LOGS ==========\n\n"
        );

        List<LogEntry> entries = logger.getEntries();

        if (entries.isEmpty()) {
            displayArea.append("No activities recorded.\n");
        } else {
            for (LogEntry entry : entries) {
                displayArea.append(entry.toString() + "\n");
            }
        }

        logger.log("INFO", "Activity logs viewed");
    }

    // Runs basic tests on the logging system
    private void runQATests() {
        displayArea.setText("===== AUTOVAULT QA TESTS =====\n\n");

        int passed = 0;
        int failed = 0;

        // TEST 1: Check whether the logger records a new event.
        int before = logger.getEntries().size();

        logger.log("TEST", "QA logger test");

        int after = logger.getEntries().size();

        if (after == before + 1) {
            displayArea.append("PASS: Logger records events.\n");
            passed++;
        } else {
            displayArea.append("FAIL: Logger did not record an event.\n");
            failed++;
        }

        // TEST 2: Check whether log entries can be retrieved.
        if (!logger.getEntries().isEmpty()) {
            displayArea.append("PASS: Log entries can be retrieved.\n");
            passed++;
        } else {
            displayArea.append("FAIL: No log entries were retrieved.\n");
            failed++;
        }

        // TEST 3: Check whether the latest log entry has all required fields.
        LogEntry latest = logger.getEntries()
                .get(logger.getEntries().size() - 1);

        if (latest.getTimestamp() != null
                && !latest.getTimestamp().isEmpty()
                && latest.getLevel() != null
                && !latest.getLevel().isEmpty()
                && latest.getMessage() != null
                && !latest.getMessage().isEmpty()) {

            displayArea.append(
                    "PASS: Log entry has timestamp, level and message.\n");
            passed++;
        } else {
            displayArea.append(
                    "FAIL: Log entry is missing required information.\n");
            failed++;
        }

        // TEST 4: Check whether log entries can be exported.
        try {
            File exportFile = File.createTempFile(
                    "autovault-test-", ".log");

            exportFile.deleteOnExit();

            logger.exportTo(exportFile);

           String exportedContent = new String(
        java.nio.file.Files.readAllBytes(exportFile.toPath()),
        java.nio.charset.StandardCharsets.UTF_8);

            if (exportedContent.contains("QA logger test")) {
                displayArea.append(
                        "PASS: Logs export correctly to a file.\n");
                passed++;
            } else {
                displayArea.append(
                        "FAIL: Exported file does not contain the test log.\n");
                failed++;
            }

        } catch (IOException e) {
            displayArea.append(
                    "FAIL: Log export failed: " + e.getMessage() + "\n");
            failed++;
        }

        // TEST 5: Check whether log entries survive a restart.
        File persistenceFile = null;

        try {
            persistenceFile = File.createTempFile(
                    "autovault-persistence-", ".log");

            persistenceFile.deleteOnExit();

            BackupLogger firstLogger =
                    new BackupLogger(persistenceFile);

            firstLogger.log(
                    "TEST", "Persistence check entry");

            BackupLogger restartedLogger =
                    new BackupLogger(persistenceFile);

            boolean found = false;

            for (LogEntry entry : restartedLogger.getEntries()) {
                if ("Persistence check entry".equals(
                        entry.getMessage())) {
                    found = true;
                    break;
                }
            }

            if (found) {
                displayArea.append(
                        "PASS: Log entries survive a logger restart.\n");
                passed++;
            } else {
                displayArea.append(
                        "FAIL: Previous log entry was not reloaded.\n");
                failed++;
            }

        } catch (IOException e) {
            displayArea.append(
                    "FAIL: Persistence test error: "
                    + e.getMessage() + "\n");
            failed++;
        }

        // TEST 6: Check how the logger handles a write failure.
        File blockedPath = null;

        try {
            blockedPath = File.createTempFile(
                    "autovault-error-test-", ".tmp");

            if (!blockedPath.delete() || !blockedPath.mkdir()) {
                throw new IOException(
                        "Could not prepare the error test location.");
            }

            blockedPath.deleteOnExit();

            BackupLogger errorLogger =
                    new BackupLogger(blockedPath);

            errorLogger.log(
                    "TEST", "This entry should not be saved");

            if (errorLogger.getEntries().isEmpty()) {
                displayArea.append(
                        "PASS: Logger handles write failures safely.\n");
                passed++;
            } else {
                displayArea.append(
                        "FAIL: Logger stored an entry despite write failure.\n");
                failed++;
            }

        } catch (IOException e) {
            displayArea.append(
                    "FAIL: Could not prepare error test: "
                    + e.getMessage() + "\n");
            failed++;
        }

        // Display the test results.
        displayArea.append("\n===== TEST SUMMARY =====\n");
        displayArea.append("Passed: " + passed + "\n");
        displayArea.append("Failed: " + failed + "\n");

        displayArea.append(
                "Pending integration: backup, restore, change detection "
                + "and retention tests.\n");
    }

    // Exports the logs to a file selected by the user
    private void exportLogs() {
        JFileChooser fileChooser = new JFileChooser();

        fileChooser.setDialogTitle("Export AutoVault Logs");
        fileChooser.setSelectedFile(
                new File("AutoVaultExport.txt")
        );

        int result = fileChooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            try {
                logger.exportTo(selectedFile);

                logger.log(
                        "INFO",
                        "Logs exported to "
                        + selectedFile.getAbsolutePath()
                );

                JOptionPane.showMessageDialog(
                        this,
                        "Logs exported successfully!"
                );

            } catch (IOException ex) {
                logger.log("ERROR", "Log export failed");

                JOptionPane.showMessageDialog(
                        this,
                        "Could not export logs: " + ex.getMessage(),
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    // Starts the application
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AutoVaultRole6Prototype application =
                    new AutoVaultRole6Prototype();

            application.setVisible(true);
        });
    }
}

