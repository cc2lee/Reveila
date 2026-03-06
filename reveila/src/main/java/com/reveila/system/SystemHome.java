package com.reveila.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class SystemHome {

    public static final String[] DIRS = {
        "bin",
        "configs",
        "configs/components",
        "data",
        "libs",
        "logs",
        "plugins",
        "resources",
        "temp"
    };

    private final Path systemHome;

    public SystemHome(String systemHome) {
        this.systemHome = Path.of(systemHome).toAbsolutePath().normalize();
        try {
            if (Files.exists(this.systemHome)) {
                if (!Files.isDirectory(this.systemHome)) {
                    throw new RuntimeException("System home path exists but is not a directory: " + this.systemHome);
                }
            } else {
                Files.createDirectories(this.systemHome);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize system home: " + this.systemHome, e);
        }
    }

    public void createDirectoryStructure(boolean createNew) {
        try {
            if (createNew) {
                // Delete old files and create a new set of folders
                if (Files.exists(systemHome)) {
                    try (Stream<Path> walk = Files.walk(systemHome)) {
                        walk.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(systemHome)) // Don't delete systemHome itself
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to delete: " + path, e);
                                }
                            });
                    }
                }
            }

            // Create folders from DIRS
            for (String dir : DIRS) {
                Path dirPath = systemHome.resolve(dir);
                if (!Files.exists(dirPath)) {
                    Files.createDirectories(dirPath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory structure in: " + systemHome, e);
        }
    }

    public Path getSystemHome() {
        return systemHome;
    }

    public Path resolve(String path) {
        return systemHome.resolve(path);
    }

}
