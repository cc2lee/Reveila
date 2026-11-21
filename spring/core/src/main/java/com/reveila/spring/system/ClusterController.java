package com.reveila.spring.system;

import com.reveila.cluster.ClusterService;
import com.reveila.system.Reveila;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
