package reveila.spring;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import reveila.Reveila;
import reveila.system.Proxy;
import reveila.system.SystemContext;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Reveila reveila;

    public ApiController(Reveila reveila) {
        this.reveila = reveila;
    }

    private Proxy getEchoServiceProxy() {
        SystemContext systemContext = reveila.getSystemContext();
        return systemContext.getProxy("Echo Service")
                .orElseThrow(() -> new IllegalStateException("EchoService proxy not found. The service may not be available."));
    }

    // GET
    @GetMapping("/echo")
    public ResponseEntity<String> echo(@RequestParam(defaultValue = "World") String name) throws Exception {
        Proxy proxy = getEchoServiceProxy();
        String result = (String) proxy.invoke("echo", new Class<?>[] { String.class }, new String[] { name });
        return ResponseEntity.ok(result);
    }

    // POST
    @PostMapping("/greetings")
    public ResponseEntity<Greeting> createGreeting(@RequestBody Greeting greeting) throws Exception {
        Proxy proxy = getEchoServiceProxy();
        Map<String, Object> greetingMap = Map.of("content", greeting.getContent());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) proxy.invoke(
            "createGreeting", new Class<?>[] { Map.class }, new Object[] { greetingMap });

        String newId = (String) result.get("id");
        Greeting responseGreeting = new Greeting((String) result.get("content"));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(newId)
                .toUri();
        return ResponseEntity.created(location).body(responseGreeting);
    }

    // PUT
    @PutMapping("/greetings/{id}")
    public ResponseEntity<Greeting> updateGreeting(@PathVariable String id, @RequestBody Greeting greeting) throws Exception {
        Proxy proxy = getEchoServiceProxy();
        Map<String, Object> greetingMap = Map.of("content", greeting.getContent());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) proxy.invoke(
            "updateGreeting", new Class<?>[] { String.class, Map.class }, new Object[] { id, greetingMap });

        return ResponseEntity.ok(new Greeting((String) result.get("content")));
    }

    // DELETE
    @DeleteMapping("/greetings/{id}")
    public ResponseEntity<Void> deleteGreeting(@PathVariable String id) throws Exception {
        Proxy proxy = getEchoServiceProxy();
        proxy.invoke("deleteGreeting", new Class<?>[] { String.class }, new Object[] { id });
        return ResponseEntity.noContent().build();
    }

    // PATCH
    @PatchMapping("/greetings/{id}")
    public ResponseEntity<Greeting> patchGreeting(@PathVariable String id, @RequestBody Map<String, Object> updates) throws Exception {
        Proxy proxy = getEchoServiceProxy();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) proxy.invoke(
            "patchGreeting", new Class<?>[] { String.class, Map.class }, new Object[] { id, updates });

        return ResponseEntity.ok(new Greeting((String) result.get("content")));
    }

    @PostMapping("/upload")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }
        Proxy proxy = getEchoServiceProxy();
        String result = (String) proxy.invoke("handleFileUpload", new Class<?>[] { String.class }, new Object[] { file.getOriginalFilename() });
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<String> handleMultipleFileUpload(@RequestParam("files") MultipartFile[] files) throws Exception {
        Proxy proxy = getEchoServiceProxy();
        String[] filenames = Arrays.stream(files).map(MultipartFile::getOriginalFilename).toArray(String[]::new);

        String result = (String) proxy.invoke("handleMultipleFileUpload", new Class<?>[] { String[].class }, new Object[] { filenames });
        return ResponseEntity.ok(result);
    }
}