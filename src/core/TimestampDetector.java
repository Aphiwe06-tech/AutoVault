
package core;




import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TimestampDetector implements ChangeDetector { //Detects changes by comparing the file's "last modified" time with the time of the last backup

    @Override
    public boolean hasChanged(File file, LocalDateTime lastRun) {
        if (lastRun == null) { //no previous backup exists, so the file counts as new
            return true; 
        }
        LocalDateTime modified = LocalDateTime.ofInstant( //change the file's last-modified time into a LocalDateTime so its compared with lastRun
                Instant.ofEpochMilli(file.lastModified()),
                ZoneId.systemDefault());
        return modified.isAfter(lastRun); //file changed if it was modified after the last backup
    }
}
