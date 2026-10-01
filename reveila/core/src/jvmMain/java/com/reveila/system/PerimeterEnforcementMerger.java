package com.reveila.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Reads system-wide perimeter settings from global-perimeter.json 
 * and merges them into plugin definition JSON files.
 */
public class PerimeterEnforcementMerger {

    private static Path getProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("system-home"))) {
            current = current.getParent();
        }
        return current != null ? current : Paths.get("");
    }

    private static final Path CONFIG_FILE_PATH = getProjectRoot().resolve("system-home/standard/configs/global-perimeter.json");
    private static final Path PLUGINS_DIR = getProjectRoot().resolve("system-home/standard/configs/plugins");


    public static void main(String[] args) {
        PerimeterEnforcementMerger merger = new PerimeterEnforcementMerger();
        try {
            merger.mergePerimeterSettings();
            System.out.println("Successfully merged perimeter settings into plugin configurations.");
        } catch (Exception e) {
            System.err.println("Failed to merge perimeter settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static JsonNode getAgencyPerimeterNode(Path configFilePath) throws IOException {
        if (!Files.exists(configFilePath)) return null;
        
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(configFilePath.toFile());
    }

    public void mergePerimeterSettings() throws IOException {
        JsonNode agencyPerimeterNode = getAgencyPerimeterNode(CONFIG_FILE_PATH);

        if (agencyPerimeterNode == null) {
            throw new IllegalStateException("Global perimeter configuration not found at " + CONFIG_FILE_PATH);
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Process all plugin JSON files
        try (Stream<Path> paths = Files.list(PLUGINS_DIR)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                 .forEach(p -> {
                     try {
                         updatePluginJson(p, agencyPerimeterNode, mapper);
                     } catch (IOException e) {
                         System.err.println("Error processing file " + p + ": " + e.getMessage());
                     }
                 });
        }
    }

    public static void updatePluginJson(Path jsonFile, JsonNode agencyPerimeterNode, ObjectMapper mapper) throws IOException {
        JsonNode root = mapper.readTree(jsonFile.toFile());

        // Check if there is a "plugin" wrapper node
        if (root.has("plugin") && root.get("plugin").isObject()) {
            ObjectNode pluginNode = (ObjectNode) root.get("plugin");
            pluginNode.set("agency_perimeter", agencyPerimeterNode);
        } else if (root.isObject()) {
            // Alternatively, append to the root if no "plugin" wrapper
            ((ObjectNode) root).set("agency_perimeter", agencyPerimeterNode);
        }

        // Write the merged JSON back to the file
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile.toFile(), root);
        System.out.println("Updated " + jsonFile.getFileName());
    }
}
