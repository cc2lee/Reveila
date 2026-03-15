package com.reveila.spring.model.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plugin_registry")
public class PluginRegistry {

    @Id
    @Column(name = "plugin_id", length = 100)
    private String pluginId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String checksum;

    @Column(name = "storage_path", nullable = false, columnDefinition = "TEXT")
    private String storagePath;

    @Column(length = 20)
    private String status = "ACTIVE";

    @Column(name = "target_cluster_role", length = 50)
    private String targetClusterRole;

    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTargetClusterRole() { return targetClusterRole; }
    public void setTargetClusterRole(String targetClusterRole) { this.targetClusterRole = targetClusterRole; }
}
