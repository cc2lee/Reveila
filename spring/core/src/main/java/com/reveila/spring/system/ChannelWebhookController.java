package com.reveila.spring.system;

import com.reveila.system.Reveila;
import com.reveila.system.RolePrincipal;
import com.reveila.system.Subject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * Unified inbound webhook controller for connected communication apps
 * (Microsoft Teams, WhatsApp Cloud API, Slack, and custom chat channels).
 *
 * Routes all natural-language messages into Reveila's `InboundChannelGateway` -> `AgenticFabric`.
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelWebhookController {

    private final Reveila reveila;

    public ChannelWebhookController(Reveila reveila) {
        this.reveila = reveila;
    }

    /**
     * Webhook verification handshake (e.g. Meta WhatsApp `hub.verify_token` & `hub.challenge`).
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/{channel}/webhook")
    public ResponseEntity<String> verifyWebhook(
            @PathVariable("channel") String channel,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge,
            HttpServletRequest request
    ) {
        try {
            Subject subject = buildChannelSubject();
            String remoteIp = request != null && request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
            Object res = reveila.invoke(
                    "InboundChannelGateway",
                    "verifyWebhookChallenge",
                    new Object[]{channel, verifyToken, challenge},
                    remoteIp,
                    subject
            );
            if (res instanceof Map<?, ?> map) {
                boolean ok = Boolean.TRUE.equals(map.get("ok"));
                if (!ok) {
                    return ResponseEntity.status(403).body(String.valueOf(map.getOrDefault("error", "Forbidden")));
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(String.valueOf(map.getOrDefault("challenge", "")));
            }
            return ResponseEntity.ok(challenge != null ? challenge : "");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage() != null ? e.getMessage() : "Verification failed");
        }
    }

    /**
     * Inbound natural-language chat webhook for Teams, WhatsApp, Slack, or custom channels.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/{channel}/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @PathVariable("channel") String channel,
            @RequestParam(name = "autoReply", defaultValue = "false") boolean autoReply,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request
    ) {
        try {
            Map<String, Object> body = payload != null ? payload : Collections.emptyMap();
            Subject subject = buildChannelSubject();
            String remoteIp = request != null && request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
            Object result = reveila.invoke(
                    "InboundChannelGateway",
                    "handleInboundWebhook",
                    new Object[]{channel, body, autoReply},
                    remoteIp,
                    subject
            );
            if (result instanceof Map<?, ?> map) {
                return ResponseEntity.ok((Map<String, Object>) map);
            }
            return ResponseEntity.ok(Map.of("ok", true, "answer", result != null ? result.toString() : ""));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "error", e.getMessage() != null ? e.getMessage() : "Inbound channel processing failed"
            ));
        }
    }

    private Subject buildChannelSubject() {
        Subject s = new Subject();
        s.getPrincipals().add(new RolePrincipal("ui-client"));
        return s;
    }
}
