package com.reveila.spring.service;

import com.reveila.cluster.ClusterPulse;
import com.reveila.cluster.ClusterService;
import com.reveila.system.Constants;
import com.reveila.system.Reveila;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Bridges native Reveila {@link ClusterService} state-synchronization pulses with the Spring host.
 *
 * <p>PostgreSQL NOTIFY/LISTEN is no longer required for cluster synchronization: {@link ClusterService}
 * natively broadcasts config, routing, and vector pulses from the Lead Node to all Worker Nodes.
 * If PostgreSQL is present AND {@code cluster.pg.bridge.enabled=true} is configured (Enterprise Edition
 * hybrid mode), database triggers are bridged into {@link ClusterService#publishPulse}.</p>
 */
@Service
public class ClusterSyncService implements CommandLineRunner {

    private final DataSource dataSource;
    private final Reveila reveila;

    public ClusterSyncService(ObjectProvider<DataSource> dataSourceProvider, Reveila reveila) {
        this.dataSource = dataSourceProvider.getIfAvailable();
        this.reveila = reveila;
    }

    @Override
    public void run(String... args) {
        // 1. Register native ClusterService pulse listener (zero DB dependency)
        ClusterService clusterService = reveila.getClusterService();
        if (clusterService != null) {
            clusterService.addPulseListener(this::handleNativePulse);
            System.out.println("📡 [CLUSTER] Attached native ClusterService pulse listener (role="
                    + clusterService.getRole() + ", nodeId=" + clusterService.getNodeId() + ").");
        }

        // 2. Optional PostgreSQL NOTIFY/LISTEN bridge (only when explicitly enabled & PG is reachable)
        if (shouldStartPostgresBridge()) {
            Thread listenerThread = new Thread(this::listenForPostgresUpdates, "Reveila-PgClusterBridge");
            listenerThread.setDaemon(true);
            listenerThread.start();
        }
    }

    private void handleNativePulse(ClusterPulse pulse) {
        String channel = pulse.getChannel();
        System.out.println("🔔 [CLUSTER] NATIVE PULSE: " + channel + " (v=" + pulse.getConfigVersion()
                + ", term=" + pulse.getTerm() + ") -> " + pulse.getPayload());

        if (Constants.CLUSTER_CHANNEL_CONFIG.equals(channel)) {
            if (reveila.getSystemContext() != null && pulse.getConfigUpdates() != null) {
                pulse.getConfigUpdates().forEach((k, v) ->
                        reveila.getSystemContext().getProperties().setProperty(k, v));
            }
            System.out.println("✅ [CLUSTER] Native config sync complete on this node.");
        } else if (Constants.CLUSTER_CHANNEL_VECTOR.equals(channel)) {
            System.out.println("⚡ [CLUSTER] Vector store synchronized for entity/doc: " + pulse.getPayload());
        } else if (Constants.CLUSTER_CHANNEL_ROUTING.equals(channel)) {
            System.out.println("🧭 [CLUSTER] Active routing table updated (routingVersion="
                    + pulse.getRoutingVersion() + ").");
        }
    }

    private boolean shouldStartPostgresBridge() {
        if (dataSource == null || reveila.getSystemContext() == null) {
            return false;
        }
        String bridgeFlag = reveila.getSystemContext().getProperties()
                .getProperty("cluster.pg.bridge.enabled", "false");
        return "true".equalsIgnoreCase(bridgeFlag);
    }

    private void listenForPostgresUpdates() {
        System.out.println("📡 [CLUSTER] Starting optional PostgreSQL NOTIFY/LISTEN bridge...");

        while (!Thread.currentThread().isInterrupted()) {
            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isWrapperFor(org.postgresql.PGConnection.class)) {
                    System.out.println("ℹ️ [CLUSTER] Active DataSource is not PostgreSQL; disabling PG bridge.");
                    return;
                }
                org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LISTEN " + Constants.CLUSTER_CHANNEL_CONFIG);
                    stmt.execute("LISTEN " + Constants.CLUSTER_CHANNEL_VECTOR);
                }

                while (!Thread.currentThread().isInterrupted()) {
                    org.postgresql.PGNotification[] notifications = pgConn.getNotifications(10000);
                    if (notifications != null) {
                        for (org.postgresql.PGNotification notification : notifications) {
                            String channel = notification.getName();
                            String payload = notification.getParameter();
                            System.out.println("🔔 [CLUSTER-PG] RECEIVED PG PULSE: " + channel + " -> " + payload);

                            if (Constants.CLUSTER_CHANNEL_CONFIG.equals(channel)) {
                                reveila.getSystemContext().getPlatformAdapter().loadProperties(null);
                            }

                            // Forward into native ClusterService so non-PG peers also stay synchronized
                            ClusterService clusterService = reveila.getClusterService();
                            if (clusterService != null && !clusterService.getStandalone()) {
                                clusterService.publishPulse(channel, payload != null ? payload : "", null);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ [CLUSTER-PG] Connection lost or Error: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
