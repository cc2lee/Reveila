package com.reveila.util;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.reveila.system.JsonException;

/**
 * A utility class for JSON serialization and deserialization.
 * <p>
 * This class provides static methods to convert between JSON strings/files and Java objects.
 */
public final class JsonUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectWriter PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter();

    public static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    public static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE_REFERENCE = new TypeReference<>() {};

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private JsonUtil() {
    }

    /**
     * Method to deserialize JSON content from given JSON content String.
     */
    public static <T> T toObject(String content, Class<T> valueType) throws JsonException {
        try {
            return MAPPER.readValue(content, valueType);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    public static Map<String, Object> parseJsonFileToMap(String filePath) throws JsonException {
        try {
            return MAPPER.readValue(new File(filePath), MAP_TYPE_REFERENCE);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    public static Map<String, Object> parseJsonStringToMap(String json) throws JsonException {
        try {
            return MAPPER.readValue(json, MAP_TYPE_REFERENCE);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    public static List<Map<String, Object>> parseJsonFileToList(String filePath) throws JsonException {
        try {
            return MAPPER.readValue(new File(filePath), LIST_OF_MAPS_TYPE_REFERENCE);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    public static List<Map<String, Object>> parseJsonStringToList(String json) throws JsonException {
        try {
            return MAPPER.readValue(json, LIST_OF_MAPS_TYPE_REFERENCE);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    /**
     * Serializes an object to a JSON string.
     *
     * @param data The object to serialize.
     * @return A JSON string representation of the object.
     * @throws JsonException if a problem occurs during serialization.
     */
    public static String toJsonString(Object data) throws JsonException {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    /**
     * Serializes an object to a JSON file with pretty printing.
     *
     * @param data     The object to serialize.
     * @param filePath The path to the output file.
     * @throws JsonException if a problem occurs during file writing or serialization.
     */
    public static void toJsonFile(Object data, String filePath) throws JsonException {
        try {
            PRETTY_WRITER.writeValue(new File(filePath), data);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    public static void writeToStream(Object data, OutputStream outputStream) throws JsonException {
        try {
            PRETTY_WRITER.writeValue(outputStream, data);
        } catch (Exception e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    // Recursively find all values for a given key in a (possibly nested) Map
	public static List<Object> findValuesByKey(Map<String, Object> map, String key) {
		List<Object> results = new ArrayList<>();
		findValuesByKeyHelper(map, key, results);
		return results;
	}

	private static void findValuesByKeyHelper(Map<?, ?> map, String key, List<Object> results) {
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey().equals(key)) {
				results.add(entry.getValue());
			}
			Object value = entry.getValue();
			if (value instanceof Map<?, ?>) {
				findValuesByKeyHelper((Map<?, ?>) value, key, results);
			} else if (value instanceof List) {
				for (Object item : (List<?>) value) {
					if (item instanceof Map<?, ?>) {
						findValuesByKeyHelper((Map<?, ?>) item, key, results);
					}
				}
			}
		}
	}
}
