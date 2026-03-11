package com.reveila.android;

import com.reveila.system.ReveilaExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java 17 / Android compatible implementation of ReveilaExecutor.
 */
public class AndroidReveilaExecutor implements ReveilaExecutor {
    
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
