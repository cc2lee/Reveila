package com.reveila.system;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java 21 implementation of ReveilaExecutor using Virtual Threads.
 */
public class ServerReveilaExecutor implements ReveilaExecutor {
    
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
