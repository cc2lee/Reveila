package spring;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import reveila.Reveila;
import reveila.examples.EchoService;
import reveila.system.Proxy;
import reveila.system.SystemContext;

@SpringBootApplication
@RestController
public class Application {

    private static Reveila reveila;
    
    /**
     * Main method to start the Spring Boot application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {

        System.out.println("Launch with JVM arguments:");
        for (String arg : args) {
            System.out.println("" + arg);
        }
        
        try {
            reveila = new Reveila();
            reveila.start(args);
            SpringApplication.run(Application.class, args);
            /* For debug only:
            String f = "C:\\IDE\\Projects\\spring-boot-gradle-project\\dir-structure\\configs\\jobs\\jobs.json";
            JsonConfiguration j = new JsonConfiguration(f);
            List<MetaObject> l = j.read();
            File file = new File(f);
            File dir = file.getParentFile();
            String fn = file.getName();
            file = new File(dir, "copy of " + fn);
            j.writeToFile(file.getAbsolutePath());
            */
		} catch (Exception e) {
            System.err.println("Failed to start the application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // GET
    @GetMapping("/reveila")
    public String get(@RequestParam Map<String, String> params) {
        
        String name = params.get("name");
        
        if (reveila == null) {
            return String.format("Hello %s!", name);
        }
        
        SystemContext systemContext = reveila.getSystemContext();
        Proxy proxy = systemContext.getProxy("Echo Service");
        if (proxy == null) {
           return "Internal error: object not found - " + EchoService.class.getName();
        }
        try {
            return (String) proxy.invoke("echo", new Class<?>[] { String.class }, new String[] { name });
        } catch (Exception e) {
                e.printStackTrace();
            return "Failed to invoke method on object: " + e.getMessage();
        }
    }

    // POST
    @PostMapping("/reveila")
    public String createHello(@RequestBody(required = false) String name) {
        if (name == null || name.isBlank()) {
            name = "World";
        }
        return String.format("Created Hello %s!", name);
    }

    // PUT
    @PutMapping("/reveila")
    public String updateHello(@RequestBody String name) {
        return String.format("Updated Hello %s!", name);
    }

    // DELETE
    @DeleteMapping("/reveila")
    public String deleteHello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Deleted Hello %s!", name);
    }

    // PATCH
    @PatchMapping("/reveila")
    public String patchHello(@RequestBody String name) {
        return String.format("Patched Hello %s!", name);
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty";
        }
        // TODO: You can process the file here (e.g., save to disk)
        return "File uploaded: " + file.getOriginalFilename();
    }

    @PostMapping("/upload-multiple")
    public String handleMultipleFileUpload(@RequestParam("files") MultipartFile[] files) {
        StringBuilder result = new StringBuilder();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                result.append("Uploaded: ").append(file.getOriginalFilename()).append("\n");
                // TODO: Process each file as needed
            }
        }
        return result.toString();
    }
}