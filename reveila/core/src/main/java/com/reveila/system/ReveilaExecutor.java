package com.reveila.system;

import java.util.concurrent.ExecutorService;

/**
 * Interface for platform-specific execution strategies.
 * Allows core to remain Java 17 compatible while leveraging Java 21 features on server.
 */
public interface ReveilaExecutor {
    ExecutorService getExecutor();
    void shutdown();
}
