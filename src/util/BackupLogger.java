

package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Records AutoVault events and manages saved log entries.
public class BackupLogger {

    private final List<LogEntry> entries = new ArrayList<>();

    private final File logFile;

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Uses the normal log file for the AutoVault application.
    public BackupLogger() {
        this(new File("AutoVault.log"));
    }

    // Allows tests to use a separate log file.
    public BackupLogger(File logFile) {
        this.logFile = logFile;
        loadExistingLogs();
    }

    // Records an event and saves it to the log file.
    public synchronized void log(String level, String message) {

        LogEntry entry = new LogEntry(
                LocalDateTime.now().format(formatter),
                level,
                message
        );

        try (PrintWriter writer = new PrintWriter(
                new FileWriter(logFile, true))) {

            writer.println(entry.toString());

            // Add the entry to memory after writing it.
            entries.add(entry);

        } catch (IOException e) {
            System.err.println(
                    "Could not save log: " + e.getMessage());
        }
    }

    // Records an information event (matches the UML).
    public void info(String msg) {
        log("INFO", msg);
    }

    // Records a warning event (matches the UML).
    public void warn(String msg) {
        log("WARN", msg);
    }

    // Records an error event together with its exception (matches the UML).
    public void error(String msg, Exception ex) {
        log("ERROR", msg + " (" + ex.getMessage() + ")");
    }

    // Returns a copy of the stored entries.
    public synchronized List<LogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    // Loads entries saved during previous runs.
    private void loadExistingLogs() {

        if (!logFile.exists()) {
            return;
        }

        try (BufferedReader reader =
                new BufferedReader(new FileReader(logFile))) {

            String line;

            while ((line = reader.readLine()) != null) {

                int levelStart = line.indexOf(" [");
                int messageStart = line.indexOf("] ", levelStart + 2);

                // Ignore malformed log lines.
                if (levelStart < 0 || messageStart < 0) {
                    continue;
                }

                String timestamp =
                        line.substring(0, levelStart);

                String level =
                        line.substring(levelStart + 2, messageStart);

                String message =
                        line.substring(messageStart + 2);

                entries.add(new LogEntry(
                        timestamp, level, message));
            }

        } catch (IOException e) {
            System.err.println(
                    "Could not load previous logs: " + e.getMessage());
        }
    }

    // Exports all currently loaded entries to a selected file.
    public synchronized void exportTo(File file)
            throws IOException {

        try (PrintWriter writer =
                new PrintWriter(new FileWriter(file))) {

            for (LogEntry entry : entries) {
                writer.println(entry.toString());
            }
        }
    }
}