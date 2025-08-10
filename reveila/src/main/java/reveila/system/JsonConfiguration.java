package reveila.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.UUID;

import reveila.error.ConfigurationException;
import reveila.util.json.JsonUtil;

public class JsonConfiguration {
    
    private List<MetaObject> metaObjects;
    private Path configPath; // Can be null if created from a stream
    private final String jsonContent;
    private final Logger logger;

    public JsonConfiguration (String configFilePath, Logger logger) throws ConfigurationException, IOException {
        Objects.requireNonNull(configFilePath, "Configuration file path must not be null.");
        this.logger = Objects.requireNonNull(logger, "Logger must not be null.");
        this.configPath = Paths.get(configFilePath);

        if (!Files.exists(this.configPath)) {
            throw new ConfigurationException("File does not exist: " + this.configPath.toAbsolutePath());
        }
        this.jsonContent = Files.readString(this.configPath);
        this.metaObjects = parse();
    }

    public JsonConfiguration(InputStream inputStream, Logger logger) throws IOException, ConfigurationException {
        this.logger = Objects.requireNonNull(logger, "Logger must not be null.");
        this.configPath = null; // No original file path when reading from a stream
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            this.jsonContent = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        this.metaObjects = parse();
    }

    private void ensureIdExists(Map<String, Object> componentMap) {
        // If an 'id' is not present in the configuration, generate a unique one.
        Object id = componentMap.get(Constants.C_ID);
        if (id == null || (id instanceof String && ((String) id).isBlank())) {
            componentMap.put(Constants.C_ID, UUID.randomUUID().toString());
        }
    }

    public List<MetaObject> read() {
        // The read method now simply returns a copy of the already-parsed list.
        return new ArrayList<>(this.metaObjects);
    }

    @SuppressWarnings("unchecked")
    private List<MetaObject> parse() throws IOException, ConfigurationException {
        // This assumes a method exists in JsonUtil to parse a string.
        // This is a reasonable assumption for a JSON utility class.
        List<Map<String, Object>> rawObjectList = JsonUtil.parseJsonStringToList(this.jsonContent);
        List<MetaObject> parsedObjects = new ArrayList<>();

        if (rawObjectList != null) {
            for (Map<String, Object> wrapper : rawObjectList) {
                
                Map<String, Object> componentMap = null;

                if (wrapper.containsKey(Constants.C_COMPONENT)) {
                    componentMap = (Map<String, Object>) wrapper.get(Constants.C_COMPONENT);
                } else if (wrapper.containsKey(Constants.C_SERVICE)) {
                    componentMap = (Map<String, Object>) wrapper.get(Constants.C_SERVICE);
                    this.logger.warning("The 'service' key is deprecated and will be removed in a future version. Please use 'component' instead.");
                } else if (wrapper.containsKey(Constants.C_OBJECT)) {
                    componentMap = (Map<String, Object>) wrapper.get(Constants.C_OBJECT);
                    this.logger.warning("The 'object' key is deprecated and will be removed in a future version. Please use 'component' instead.");
                } else if (wrapper.containsKey(Constants.TASK)) {
                    componentMap = (Map<String, Object>) wrapper.get(Constants.TASK);
                }

                if (componentMap != null) {
                    ensureIdExists(componentMap);
                    // We pass "component" as the type to ensure that when written back, it uses the new standard.
                    parsedObjects.add(new MetaObject(componentMap, Constants.C_COMPONENT));
                } else {
                    this.logger.warning("Found an unknown or malformed entry in component configuration: " + wrapper);
                }
            }
        }
        return parsedObjects;
    }

    public synchronized void add(MetaObject metaObject) {
        Objects.requireNonNull(metaObject, "MetaObject to add must not be null");
        this.metaObjects.add(metaObject);
    }

    public void writeToFile() throws IOException {
        if (this.configPath == null) {
            throw new IOException("Cannot write to file because the configuration was not loaded from a file path.");
        }
        writeToFile(this.configPath.toString());
    }

    public synchronized void writeToFile(String file) throws IOException {
        Path outputPath = Paths.get(file);
        Path parentDir = outputPath.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        List<Map<String, Object>> listToSave = this.metaObjects.stream()
                .map(MetaObject::toWrapperMap)
                .collect(Collectors.toList());
        JsonUtil.toJsonFile(listToSave, file);
    }
}