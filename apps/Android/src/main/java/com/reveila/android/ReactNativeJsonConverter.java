package com.reveila.android;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 * A utility class to convert Java objects to React Native's WritableMap and WritableArray.
 * This uses Gson as an intermediary to handle complex object serialization.
 */
public class ReactNativeJsonConverter {

    private static final Gson gson = new Gson();

    /**
     * Converts a standard Java object into a type that can be passed directly to a React Native Promise.
     * This will be a WritableMap, WritableArray, or a primitive type.
     *
     * @param object The object to convert.
     * @return A React Native compatible object.
     */
    public static Object convertObjectToWritable(Object object) {
        if (object == null) {
            return null;
        }
        // Use Gson to convert the Java object to a JsonElement tree
        JsonElement jsonElement = gson.toJsonTree(object);
        return convertJsonElementToWritable(jsonElement);
    }

    private static Object convertJsonElementToWritable(JsonElement jsonElement) {
        if (jsonElement.isJsonNull()) {
            return null;
        }
        if (jsonElement.isJsonObject()) {
            return convertJsonToMap(jsonElement.getAsJsonObject());
        }
        if (jsonElement.isJsonArray()) {
            return convertJsonToArray(jsonElement.getAsJsonArray());
        }
        if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                // Return as Double, as React Native bridge handles numbers this way.
                return primitive.getAsDouble();
            }
            if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        // Fallback for any other type
        return gson.toJson(jsonElement);
    }

    private static WritableMap convertJsonToMap(JsonObject jsonObject) {
        WritableMap map = Arguments.createMap();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            Object value = convertJsonElementToWritable(entry.getValue());
            putValueInMap(map, key, value);
        }
        return map;
    }

    private static WritableArray convertJsonToArray(JsonArray jsonArray) {
        WritableArray array = Arguments.createArray();
        for (JsonElement jsonElement : jsonArray) {
            Object value = convertJsonElementToWritable(jsonElement);
            pushValueToArray(array, value);
        }
        return array;
    }

    private static void putValueInMap(WritableMap map, String key, Object value) {
        if (value == null) { map.putNull(key); }
        else if (value instanceof Boolean) { map.putBoolean(key, (Boolean) value); }
        else if (value instanceof Double) { map.putDouble(key, (Double) value); }
        else if (value instanceof Integer) { map.putInt(key, (Integer) value); }
        else if (value instanceof String) { map.putString(key, (String) value); }
        else if (value instanceof WritableMap) { map.putMap(key, (WritableMap) value); }
        else if (value instanceof WritableArray) { map.putArray(key, (WritableArray) value); }
        else { map.putString(key, value.toString()); }
    }

    private static void pushValueToArray(WritableArray array, Object value) {
        if (value == null) { array.pushNull(); }
        else if (value instanceof Boolean) { array.pushBoolean((Boolean) value); }
        else if (value instanceof Double) { array.pushDouble((Double) value); }
        else if (value instanceof Integer) { array.pushInt((Integer) value); }
        else if (value instanceof String) { array.pushString((String) value); }
        else if (value instanceof WritableMap) { array.pushMap((WritableMap) value); }
        else if (value instanceof WritableArray) { array.pushArray((WritableArray) value); }
        else { array.pushString(value.toString()); }
    }

    public static Object[] toArray(ReadableArray readableArray) {
        if (readableArray == null) {
            return new Object[0];
        }
        Object[] array = new Object[readableArray.size()];
        for (int i = 0; i < readableArray.size(); i++) {
            switch (readableArray.getType(i)) {
                case Null:
                    array[i] = null;
                    break;
                case Boolean:
                    array[i] = readableArray.getBoolean(i);
                    break;
                case Number:
                    array[i] = readableArray.getDouble(i);
                    break;
                case String:
                    array[i] = readableArray.getString(i);
                    break;
                case Map:
                    array[i] = toMap(readableArray.getMap(i));
                    break;
                case Array:
                    array[i] = toArray(readableArray.getArray(i));
                    break;
            }
        }
        return array;
    }

    public static Map<String, Object> toMap(ReadableMap readableMap) {
        if (readableMap == null) {
            return null;
        }
        Map<String, Object> map = new java.util.HashMap<>();
        com.facebook.react.bridge.ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            switch (readableMap.getType(key)) {
                case Null:
                    map.put(key, null);
                    break;
                case Boolean:
                    map.put(key, readableMap.getBoolean(key));
                    break;
                case Number:
                    map.put(key, readableMap.getDouble(key));
                    break;
                case String:
                    map.put(key, readableMap.getString(key));
                    break;
                case Map:
                    map.put(key, toMap(readableMap.getMap(key)));
                    break;
                case Array:
                    map.put(key, toArray(readableMap.getArray(key)));
                    break;
            }
        }
        return map;
    }
}