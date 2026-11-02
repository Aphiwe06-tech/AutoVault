
package core;


import java.io.File;
import java.time.LocalDateTime;

public interface ChangeDetector {
    boolean hasChanged(File file, LocalDateTime lastRun);//Checks whether a file has changed since the last backup
    
}