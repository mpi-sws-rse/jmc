package org.mpisws.jmc.util.files;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtil {
    public static void unsafeStoreToFile(String path, String content) {
        try {
            Files.write(Paths.get(path), content.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static FileOutputStream unsafeCreateFile(String path) {
        try {
            Path pPath = Paths.get(path);
            if (Files.exists(pPath)) {
                Files.delete(pPath);
            }
            return new FileOutputStream(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Ensure the path exists, creating it if it does not.
     *
     * <p>Deletes the path if it already exists.
     *
     * @param path the path to ensure
     */
    public static void unsafeEnsurePath(String path) {
        try {
            Path pPath = Paths.get(path);
            if (Files.exists(pPath) && Files.isDirectory(pPath)) {
                Files.list(pPath)
                        .forEach(
                                (p) -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
            }
            Files.deleteIfExists(pPath);
            Files.createDirectories(pPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
