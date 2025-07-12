package reveila.util.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A utility class for JSON serialization and deserialization using Jackson.
 * <p>
 * This class provides static methods to convert between JSON strings/files and Java objects.
 * It reuses a single {@link ObjectMapper} instance for performance, as it is thread-safe.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter();

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE_REFERENCE = new TypeReference<>() {};

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private JsonUtil() {
    }

    public static Map<String, Object> parseJsonFileToMap(String filePath) throws IOException {
        return MAPPER.readValue(new File(filePath), MAP_TYPE_REFERENCE);
    }

    public static Map<String, Object> parseJsonStringToMap(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, MAP_TYPE_REFERENCE);
    }

    public static List<Map<String, Object>> parseJsonFileToList(String filePath) throws IOException {
        return MAPPER.readValue(new File(filePath), LIST_OF_MAPS_TYPE_REFERENCE);
    }

    public static List<Map<String, Object>> parseJsonStringToList(String json) throws IOException {
        return MAPPER.readValue(json, LIST_OF_MAPS_TYPE_REFERENCE);
    }

    /**
     * Serializes an object to a JSON string.
     *
     * @param data The object to serialize.
     * @return A JSON string representation of the object.
     * @throws JsonProcessingException if a problem occurs during serialization.
     */
    public static String toJsonString(Object data) throws JsonProcessingException {
        return MAPPER.writeValueAsString(data);
    }

    /**
     * Serializes an object to a JSON file with pretty printing.
     *
     * @param data     The object to serialize.
     * @param filePath The path to the output file.
     * @throws IOException if a problem occurs during file writing or serialization.
     */
    public static void toJsonFile(Object data, String filePath) throws IOException {
        PRETTY_WRITER.writeValue(new File(filePath), data);
    }
}
