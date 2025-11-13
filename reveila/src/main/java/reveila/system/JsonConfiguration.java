package reveila.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import reveila.util.JsonUtil;

public class JsonConfiguration {
    
    private List<MetaObject> metaObjects;
    private final String jsonContent;
    private final Logger logger;

    public JsonConfiguration(InputStream inputStream, Logger logger) throws IOException, JsonException {
        this.logger = Objects.requireNonNull(logger, "Logger must not be null.");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            this.jsonContent = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        this.metaObjects = parse();
    }

    public List<MetaObject> read() {
        // The read method now simply returns a copy of the already-parsed list.
        return new ArrayList<>(this.metaObjects);
    }

    @SuppressWarnings("unchecked")
    private List<MetaObject> parse() throws JsonException {
        // This assumes a method exists in JsonUtil to parse a string.
        // This is a reasonable assumption for a JSON utility class.
        List<Map<String, Object>> rawObjectList = JsonUtil.parseJsonStringToList(this.jsonContent);
        List<MetaObject> parsedObjects = new ArrayList<>();

        if (rawObjectList != null) {
            for (Map<String, Object> wrapper : rawObjectList) {
                
                Map<String, Object> componentMap = null;

                if (wrapper.containsKey(Constants.COMPONENT)) {
                    componentMap = (Map<String, Object>) wrapper.get(Constants.COMPONENT);
                } else if (wrapper.containsKey(Constants.TASK)) {
                    componentMap = (Map<String, Object>) wrapper.get(Constants.TASK);
                }

                if (componentMap != null) {
                    // We pass "component" as the type to ensure that when written back, it uses the new standard.
                    parsedObjects.add(new MetaObject(componentMap, Constants.COMPONENT));
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

    public synchronized void remove(MetaObject metaObject) {
        if (metaObject == null) {
            return;
        }
        this.metaObjects.remove(metaObject);
    }

    public synchronized void writeToStream(OutputStream outputStream) throws IOException, JsonException {
        List<Map<String, Object>> listToSave = this.metaObjects.stream()
            .map(MetaObject::toWrapperMap)
                .collect(Collectors.toList());
        JsonUtil.writeToStream(listToSave, outputStream);
    }
}