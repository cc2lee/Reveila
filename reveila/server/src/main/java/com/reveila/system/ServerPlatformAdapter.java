package com.reveila.system;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public com.reveila.crypto.Cryptographer getCryptographer() {
        return super.getCryptographer();
    }
}
