package reveila.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import reveila.error.ConfigurationException;
import reveila.util.json.JsonUtil;

public class JsonConfiguration {
    
    private final List<MetaObject> metaObjects;
    private final Path configPath;

    public JsonConfiguration (String configFilePath) throws ConfigurationException {
        Objects.requireNonNull(configFilePath, "Configuration file path must not be null.");
        this.configPath = Paths.get(configFilePath);

        if (!Files.exists(this.configPath)) {
            throw new ConfigurationException("File does not exist: " + this.configPath.toAbsolutePath());
        } else if (!Files.isWritable(this.configPath)) {
            throw new ConfigurationException("File is not writable: " + this.configPath.toAbsolutePath());
        }
        this.metaObjects = new ArrayList<>();
    }

    public List<MetaObject> read () throws IOException, ConfigurationException {
        // Parse the JSON into a List<Map<String, Object>>
        List<Map<String, Object>> rawObjectList = JsonUtil.parseJsonFileToList(this.configPath.toString());
        this.metaObjects.clear();

        if (rawObjectList != null) {
            for (Map<String, Object> wrapper : rawObjectList) {
                if (wrapper.containsKey("service")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> service = (Map<String, Object>) wrapper.get("service");
                    // TODO: auto create guid if not present!
                    this.metaObjects.add(new MetaObject(service, "service"));
                } else if (wrapper.containsKey("object")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> object = (Map<String, Object>) wrapper.get("object");
                    this.metaObjects.add(new MetaObject(object, "object"));
                } else {
                    // Handle cases where the wrapper is missing or unknown
                }
            }
        }
        return new ArrayList<>(this.metaObjects);
    }

    public void add(MetaObject metaObject) {
        Objects.requireNonNull(metaObject, "MetaObject to add must not be null");
        this.metaObjects.add(metaObject);
    }

    public void writeToFile() throws IOException {
        writeToFile(this.configPath.toString());
    }

    public void writeToFile(String file) throws IOException {
        List<Map<String, Object>> listToSave = this.metaObjects.stream()
                .map(MetaObject::toWrapperMap)
                .collect(Collectors.toList());
        JsonUtil.toJsonFile(listToSave, file);
    }
}