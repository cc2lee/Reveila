package com.reveila.spring.system;

import com.reveila.ai.InvocationResult;
import com.reveila.spring.service.WebhookIngestionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for receiving external task management webhooks.
 * 
 * @author CL
 */
@RestController
@RequestMapping("/api/v1/ingestion")
public class WebhookIngestionController {

    private final WebhookIngestionService ingestionService;

    public WebhookIngestionController(WebhookIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Accepts webhook signals from external AI tools.
     *
     * @param payload The raw JSON payload from the external tool.
     * @return The result of the ingestion and governance audit.
     */
    @PostMapping("/webhook")
    public InvocationResult handleWebhook(@RequestBody Map<String, Object> payload) {
        return ingestionService.ingest(payload);
    }
}
