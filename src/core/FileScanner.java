

package core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class FileScanner {

    public List<Path> scanFiles(Path folder) throws IOException {

        List<Path> files = new ArrayList<>();

        if (!Files.isDirectory(folder)) {
            throw new IOException("Folder does not exist: " + folder);
        }

        try (Stream<Path> paths = Files.walk(folder)) {
            paths.filter(Files::isRegularFile)
                 .forEach(files::add);
        }

        return files;
    }

    // Matches the UML: lists every file under a folder as File objects.
    public List<File> scan(File root) {
        List<File> result = new ArrayList<>();
        try {
            for (Path path : scanFiles(root.toPath())) {
                result.add(path.toFile());
            }
        } catch (IOException e) {
            System.out.println("Scan failed: " + e.getMessage());
        }
        return result;
    }
}