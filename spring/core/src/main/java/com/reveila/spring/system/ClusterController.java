package com.reveila.spring.system;

import com.reveila.cluster.ClusterService;
import com.reveila.system.Reveila;
import com.reveila.system.RolePrincipal;
import com.reveila.system.Subject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * Observability and administrative endpoints for the native Reveila Runtime Cluster.
 */
@RestController
@RequestMapping("/api/cluster")
public class ClusterController {

    private final Reveila reveila;

    public ClusterController(Reveila reveila) {
        this.reveila = reveila;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        ClusterService clusterService = reveila.getClusterService();
        if (clusterService == null) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "ClusterService is not registered in this runtime."
            ));
        }
        return ResponseEntity.ok(clusterService.getClusterStatus());
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyLeadership() {
        ClusterService clusterService = reveila.getClusterService();
        if (clusterService == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "ClusterService is not registered."));
        }
        boolean stillLeader = clusterService.verifyLeadership();
        return ResponseEntity.ok(Map.of(
                "stillLeader", stillLeader,
                "role", clusterService.getRole().name(),
                "term", clusterService.getCurrentTerm(),
                "leaderId", clusterService.getLeaderId() != null ? clusterService.getLeaderId() : ""
        ));
    }

    /**
     * Bidirectional standing-instruction delta sync endpoint for paired Desktop/Mobile Edge nodes.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/instructions/sync")
    public ResponseEntity<Map<String, Object>> syncInstructions(
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request
    ) {
        try {
            Map<String, Object> body = payload != null ? payload : Collections.emptyMap();
            Subject clusterSubject = new Subject();
            clusterSubject.getPrincipals().add(new RolePrincipal("system"));
            String remoteIp = request != null && request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
            Object result = reveila.invoke(
                    "AgentInstructionStore",
                    "handleSyncRequest",
                    new Object[]{body},
                    remoteIp,
                    clusterSubject
            );
            if (result instanceof Map<?, ?> map) {
                return ResponseEntity.ok((Map<String, Object>) map);
            }
            return ResponseEntity.ok(Map.of("ok", true, "result", result != null ? result : ""));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "error", e.getMessage() != null ? e.getMessage() : "Instruction sync failed"
            ));
        }
    }
}
