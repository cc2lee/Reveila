package reveila.spring;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import reveila.Reveila;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Reveila reveila;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiController(Reveila reveila) {
        this.reveila = reveila;
    }

    @PostMapping("/components/{componentName}/invoke")
    public ResponseEntity<?> invokeComponent(
            @PathVariable("componentName") String componentName,
            @RequestBody MethodDTO request,
            HttpServletRequest httpRequest) throws Exception {
        
        Object[] args = request.getArgs();
        String callerIp = httpRequest.getRemoteAddr();

        Object result = reveila.invoke(componentName, request.getMethodName(), args, callerIp);
        String jsonResponse = objectMapper.writeValueAsString(result);
        return ResponseEntity.ok(jsonResponse);
    }
}