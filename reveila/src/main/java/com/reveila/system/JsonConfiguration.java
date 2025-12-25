package com.reveila.system;

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
import java.util.stream.Collectors;

import com.reveila.util.JsonUtil;

public class JsonConfiguration {
    
    private List<MetaObject> metaObjects;
    private final String jsonContent;
    
    public JsonConfiguration(InputStream inputStream) throws IOException, JsonException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            this.jsonContent = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        this.metaObjects = parse();
    }

    public synchronized List<MetaObject> getMetaObjects() {
        // The read method now simply returns a copy of the already-parsed list.
        return new ArrayList<>(this.metaObjects);
    }

    @SuppressWarnings("unchecked")
    private synchronized List<MetaObject> parse() throws JsonException {
        List<Map<String, Object>> rawObjectList = JsonUtil.parseJsonStringToList(this.jsonContent);
        List<MetaObject> parsedObjects = new ArrayList<>();

        if (rawObjectList != null) {
            for (Map<String, Object> wrapper : rawObjectList) {
                if (wrapper.containsKey(Constants.COMPONENT)) {
                    Map<String, Object> map = (Map<String, Object>) wrapper.get(Constants.COMPONENT);
                    if (map != null) {
                        parsedObjects.add(new MetaObject(map, Constants.COMPONENT));
                    }
                }
                else if (wrapper.containsKey(Constants.TASK)) {
                    Map<String, Object> map = (Map<String, Object>) wrapper.get(Constants.TASK);
                    if (map != null) {
                        parsedObjects.add(new MetaObject(map, Constants.TASK));
                    }
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