package reveila.service;

import java.util.Map;

import reveila.system.MetaObject;
import reveila.system.Proxy;
import reveila.util.IdGenerator;

/**
 * A service class providing business logic for the REST endpoints.
 * This class is designed to be instantiated and invoked via the Reveila proxy system.
 */
public class EchoService extends Proxy {

    private boolean reverse = false;
    private int repeat = 0;

    public EchoService(MetaObject objectDescriptor) {
        super(objectDescriptor);
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    public String echo(String name) {
        String textToEcho = name;
        if (this.reverse) {
            // Reverse the string using StringBuilder
            textToEcho = new StringBuilder(name).reverse().toString();
        }
        if (this.repeat > 0) {
            StringBuilder repeated = new StringBuilder();
            for (int i = 0; i < this.repeat; i++) {
                repeated.append(textToEcho);
                if (i < this.repeat - 1) {
                    repeated.append(", ");
                }
            }
            textToEcho = repeated.toString();
        }
        return "Echo: " + textToEcho;
    }

    /**
     * Creates a greeting.
     * Note: We use a Map instead of the Greeting DTO because the DTO is in the main
     * application and is not visible to the 'reveila' subproject.
     */
    public Map<String, Object> createGreeting(Map<String, Object> greeting) {
        // In a real app, you would save the greeting. Here, we just echo it back.
        String newId = IdGenerator.createId();
        return Map.of(
            "id", newId,
            "content", greeting.get("content")
        );
    }

    public Map<String, Object> updateGreeting(String id, Map<String, Object> greeting) {
        String content = (String) greeting.get("content");
        return Map.of("content", "Updated greeting " + id + " with: " + content);
    }

    public Map<String, Object> patchGreeting(String id, Map<String, Object> updates) {
        return Map.of("content", "Patched greeting " + id + " with: " + updates.toString());
    }

    public void deleteGreeting(String id) {
        System.out.println("Deleted greeting " + id + " from service.");
    }

    /**
     * Handles a file upload.
     * Note: We pass the filename as a String, as the Spring-specific MultipartFile
     * is not visible to this subproject and cannot be passed via 'invoke'.
     */
    public String handleFileUpload(String filename) {
        return "File uploaded via service: " + filename;
    }

    public String handleMultipleFileUpload(String[] filenames) {
        return "Uploaded via service: " + String.join(", ", filenames);
    }
}