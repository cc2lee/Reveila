package reveila.system;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reveila.util.json.JsonUtil;

public class JsonConfiguration extends HashMap<String, Object> {
    
    private List<Map<String,Object>> jsonList;
    private String filePath;

    public JsonConfiguration (String configFilePath) {
        File file = new File(configFilePath);
		if (!file.exists()) {
			throw new IllegalArgumentException(
				"File does not exist: " + file.getAbsolutePath());
		} else if (!file.canWrite()) {
            throw new IllegalArgumentException(
				"File is not writable: " + file.getAbsolutePath());
        }
        this.filePath = configFilePath;
    }

    @SuppressWarnings("unchecked")
    public List<MetaObject> read () throws IOException {

        // Parse the JSON into a List<Map<String, Object>>
        jsonList = JsonUtil.parseJsonFileToList(this.filePath);
        List<MetaObject> metaObjects = new ArrayList<MetaObject>();
        if (jsonList != null) {
            for (Map<String, Object> wrapper : jsonList) {
                // Check for "service"
                if (wrapper.containsKey("service")) {
                    Map<String, Object> service = (Map<String, Object>) wrapper.get("service");

                    // TODO: auto create guid if not present!

                    metaObjects.add(new MetaObject(service, this));
                }
                // Check for "object"
                if (wrapper.containsKey("object")) {
                    Map<String, Object> object = (Map<String, Object>) wrapper.get("object");
                    metaObjects.add(new MetaObject(object, this));
                }
                
            }
        }
        return metaObjects;
    }

    public void add(MetaObject objectFactory) {
        if (objectFactory == null) {
            throw new IllegalArgumentException("Argument must not be null");
        }
        jsonList.add(objectFactory);
    }

    public void writeToFile() throws IOException {
        JsonUtil.toJsonFile(this.jsonList, this.filePath);
    }

    public void writeToFile(String file) throws IOException {
        JsonUtil.toJsonFile(this.jsonList, file);
    }
}