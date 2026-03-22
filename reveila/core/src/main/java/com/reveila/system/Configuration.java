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

import com.reveila.util.json.JsonException;
import com.reveila.util.json.JsonUtil;

public class Configuration {

    private List<MetaObject> metaObjects;

    public Configuration(InputStream inputStream) throws IOException, JsonException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            this.metaObjects = parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
        }
    }

    public List<MetaObject> getMetaObjects() {
        return metaObjects;
    }

    @SuppressWarnings("unchecked")
    private synchronized List<MetaObject> parse(String jsonContent) throws JsonException {
        List<Map<String, Object>> rawList = null;
        try {
            rawList = JsonUtil.parseJsonStringToList(jsonContent);
        } catch (Exception e) {
            try {
                Map<String, Object> singleObj = JsonUtil.parseJsonStringToMap(jsonContent);
                rawList = new ArrayList<>();
                rawList.add(singleObj);
            } catch (Exception e2) {
                throw new JsonException("Failed to parse configuration.", e2);
            }
        }

        if (rawList == null || rawList.isEmpty()) {
            throw new JsonException("Mulformed configuration.");
        }

        List<MetaObject> mObjList = new ArrayList<>();

        for (Map<String, Object> wrapper : rawList) {
            if (wrapper.containsKey(Constants.COMPONENT)) {
                Map<String, Object> map = (Map<String, Object>) wrapper.get(Constants.COMPONENT);
                if (map != null) {
                    mObjList.add(new MetaObject(map));
                }
            } else if (wrapper.containsKey(Constants.PLUGIN)) {
                Map<String, Object> map = (Map<String, Object>) wrapper.get(Constants.PLUGIN);
                if (map != null) {
                    mObjList.add(new MetaObject(map));
                }
            }
        }

        return mObjList;
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
                .map(MetaObject::getDataMap)
                .collect(Collectors.toList());
        JsonUtil.writeToStream(listToSave, outputStream);
    }
}