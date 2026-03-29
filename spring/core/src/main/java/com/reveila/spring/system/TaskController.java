package com.reveila.spring.system;

import com.reveila.system.Reveila;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for managing autonomous recurring tasks.
 * Provides APIs to list, read, save, and delete task definitions.
 * 
 * @author Charles Lee
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final Reveila reveila;

    public TaskController(Reveila reveila) {
        this.reveila = reveila;
    }

    private String getTasksDir() {
        String home = reveila.getSystemContext().getProperties().getProperty("system.home");
        if (home == null) home = "../../system-home/standard";
        return home + "/tasks";
    }

    /**
     * Lists all autonomous tasks currently defined in the tasks directory.
     * 
     * @return A list of task objects containing filename and raw JSON content.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listTasks() {
        File dir = new File(getTasksDir());
        if (!dir.exists() || !dir.isDirectory()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Map<String, Object>> tasks = Arrays.stream(files).map(file -> {
            Map<String, Object> map = new HashMap<>();
            map.put("filename", file.getName());
            try {
                map.put("content", Files.readString(file.toPath()));
            } catch (Exception e) {
                map.put("error", "Error reading file: " + e.getMessage());
            }
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(tasks);
    }

    /**
     * Saves or updates a task definition file.
     * 
     * @param filename The name of the file (e.g., daily-news.json).
     * @param content  The raw JSON content of the task.
     * @return Success or error message.
     */
    @PostMapping("/{filename}")
    public ResponseEntity<String> saveTask(@PathVariable String filename, @RequestBody String content) {
        try {
            if (filename == null || filename.isBlank()) {
                return ResponseEntity.badRequest().body("Filename is required.");
            }
            
            if (!filename.toLowerCase().endsWith(".json")) {
                filename += ".json";
            }

            File dir = new File(getTasksDir());
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, filename);
            Files.writeString(file.toPath(), content);
            return ResponseEntity.ok("Task saved successfully: " + filename);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to save task: " + e.getMessage());
        }
    }

    /**
     * Deletes a task definition file.
     * 
     * @param filename The name of the file to delete.
     * @return Success or error message.
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<String> deleteTask(@PathVariable String filename) {
        try {
            File file = new File(getTasksDir(), filename);
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    return ResponseEntity.ok("Task deleted successfully.");
                } else {
                    return ResponseEntity.status(500).body("Failed to delete file.");
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to delete task: " + e.getMessage());
        }
    }
}
