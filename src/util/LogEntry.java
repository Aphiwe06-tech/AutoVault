

package util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Represents one event recorded by AutoVault.
public class LogEntry {

    private final String timestamp;
    private final String level;
    private final String message;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Creates a new log entry using the current time.
    public LogEntry(String level, String message) {
        this(LocalDateTime.now().format(FORMATTER), level, message);
    }

    // Creates a log entry with an existing timestamp.
    public LogEntry(String timestamp, String level, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    // Controls how an entry appears when displayed.
    @Override
    public String toString() {
        return timestamp + " [" + level + "] " + message;
    }
}