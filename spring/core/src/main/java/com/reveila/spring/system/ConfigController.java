package com.reveila.spring.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.system.Reveila;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;

/**
 * Controller to serve UI and system configurations.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final Reveila reveila;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConfigController(Reveila reveila) {
        this.reveila = reveila;
    }

    @GetMapping("/ui")
    public ResponseEntity<JsonNode> getUiConfig(@org.springframework.web.bind.annotation.RequestParam(required = false) String tableId) {
        try {
            String systemHome = reveila.getSystemContext().getProperties().getProperty("system.home");
            if (systemHome == null) systemHome = System.getenv("REVEILA_HOME");
            if (systemHome == null) systemHome = "../../system-home/standard";
            
            if (tableId == null || tableId.isBlank()) {
                tableId = "ui-config"; // Default fallback
            }

            File configFile = new File(systemHome, "configs/ui/" + tableId + ".json");
            System.err.println("[DEBUG] UI Config lookup: " + configFile.getAbsolutePath());
            
            // Second fallback to the old root config if it exists
            if (!configFile.exists()) {
                configFile = new File(systemHome, "configs/" + tableId + ".json");
                System.err.println("[DEBUG] UI Config fallback lookup: " + configFile.getAbsolutePath());
            }

            if (!configFile.exists()) {
                System.err.println("[DEBUG] UI Config NOT FOUND, returning default fallback.");
                // Return default if file missing
                return ResponseEntity.ok(mapper.readTree("{\"table\": {\"displayColumns\": [\"riskScore\", \"timestamp\"]}}"));
            }
            return ResponseEntity.ok(mapper.readTree(configFile));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
