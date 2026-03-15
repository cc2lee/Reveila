package com.reveila.spring.service;

import com.reveila.system.Reveila;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Service to handle cluster-wide synchronization using PostgreSQL NOTIFY/LISTEN.
 * Implements the "Pulse" pattern for Reactive Updates.
 */
@Service
public class ClusterSyncService implements CommandLineRunner {

    private final DataSource dataSource;
    private final Reveila reveila;

    public ClusterSyncService(DataSource dataSource, Reveila reveila) {
        this.dataSource = dataSource;
        this.reveila = reveila;
    }

    @Override
    public void run(String... args) {
        // Start the background listener thread
        Thread listenerThread = new Thread(this::listenForUpdates, "Reveila-ClusterSync");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenForUpdates() {
        System.out.println("📡 [CLUSTER] Starting Config Pulse listener...");
        
        while (!Thread.currentThread().isInterrupted()) {
            try (Connection conn = dataSource.getConnection()) {
                // 1. Establish the LISTEN channel
                // We use unwrap to access PostgreSQL specific features (PGConnection)
                org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LISTEN reveila_config_updates");
                }

                // 2. Continuous Polling for Notifications
                while (!Thread.currentThread().isInterrupted()) {
                    // getNotifications(0) would block, but we use a timeout to allow for graceful shutdown check
                    org.postgresql.PGNotification[] notifications = pgConn.getNotifications(10000);
                    
                    if (notifications != null) {
                        for (org.postgresql.PGNotification notification : notifications) {
                            System.out.println("🔔 [CLUSTER] RECEIVED PULSE: " + notification.getName() + " -> " + notification.getParameter());
                            
                            // Trigger the hot-reload logic implemented in the platform adapter
                            reveila.getSystemContext().getPlatformAdapter().reloadProperties();
                            System.out.println("✅ [CLUSTER] Hot Reload complete on this node.");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ [CLUSTER] Connection lost or Error: " + e.getMessage());
                try {
                    // Wait before retrying connection
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
