package reveila.util.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reveila.util.json.JsonUtil;

public class IOHelper {

    public static String readToString(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024]; // Buffer size
        while ((nRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        byte[] byteArray = buffer.toByteArray();
        return new String(byteArray);
    }

    public static void writeAsJsonArray(OutputStream output, Map<?, ?>[] mapArray) throws Exception {
        try {
            ArrayNode jsonArray = JsonUtil.MAPPER.createArrayNode();
            for (Map<?, ?> map : mapArray) {
                ObjectNode jsonObject = JsonUtil.MAPPER.convertValue(map, ObjectNode.class);
                jsonArray.add(jsonObject);
            }
            JsonUtil.MAPPER.writerWithDefaultPrettyPrinter().writeValue(output, jsonArray);
        } catch (Exception e) {
            throw new Exception("Failed to write JSON array to output stream: " + e.getMessage(), e);
        } finally {
            try {
                output.flush();
            } catch (IOException ioe) {
                // Ignore
            }
        }
    }

    public static Map<?, ?>[] readJsonArray(InputStream jsonArrayIS) throws Exception {
        try {
            // Read the JSON array from the input stream
            JsonNode rootNode = JsonUtil.MAPPER.readTree(jsonArrayIS);

            // restore tracker from the first JsonNode
            if (rootNode.isArray()) {
                Iterator<JsonNode> elements = rootNode.elements();
                if (elements == null) {
                    return new Map[0];
                }
                Map<?, ?>[] maps = new Map[((ArrayNode) rootNode).size()];
                int index = 0;
                while (elements.hasNext()) {
                    JsonNode node = elements.next();
                    maps[index++] = JsonUtil.MAPPER.convertValue(node, new TypeReference<Map<?, ?>>() {});
                }
                return maps;
            } else {
                throw new Exception("Malformed JSON array in the input stream");
            }
        } catch (Exception e) {
            throw new Exception("Failed to read JSON array from input stream: " + e.getMessage(), e);
        }
    }
}