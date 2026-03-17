package com.reveila.system;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.reveila.data.Entity;
import com.reveila.data.Repository;

import java.util.Map;

/**
 * Java 21 implementation of PlatformAdapter for Server environment.
 */
public class ServerPlatformAdapter extends BasePlatformAdapter {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ServerPlatformAdapter(Properties commandLineArgs) throws Exception {
        super(commandLineArgs);
        System.err.println("[CRITICAL_LOG] ServerPlatformAdapter Initialized");
    }

    @Override
    public ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType) {
        // TODO: Implement server-specific repository (e.g. JPA/Postgres)
        return null;
    }
}
