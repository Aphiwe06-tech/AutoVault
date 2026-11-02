
package core;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

//detects changes by looking at the file's contents not its date
public class HashDetector implements ChangeDetector { 

    private final Map<String, String> hashCache = new HashMap<>();

    @Override
    public boolean hasChanged(File file, LocalDateTime lastRun) {
        String path = file.getAbsolutePath();
        //calculate files current hash
        String newHash = computeHash(file);

        if (newHash == null) {
            return true; // could not read the file
        }
        String oldHash = hashCache.get(path); // hash is saved from previous check
        
        hashCache.put(path, newHash);//save new hash for the next check

        return oldHash == null || !oldHash.equals(newHash); //file changes if its new or if the old and new hashes are different
    }

    private String computeHash(File file) {
        
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Read the file in blocks of 8 KB and feed each block to the digest
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            
            // Convert the resulting bytes into readable hexadecimal text
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) { // The file could not be read
            return null;
        }
    }
}
