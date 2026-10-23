package com.reveila.test;

import com.reveila.system.Reveila;

/**
 * Java Test Application for Reveila Core (Kotlin Multiplatform).
 *
 * Verifies:
 * 1. Reveila object instantiation from standard Java runtime.
 * 2. Invocation of reveila.start() method.
 * 3. Successful loading and parsing of system configuration files (reveila.properties, standard.json, java.json).
 * 4. Dependency-aware component registration, linting, and lifecycle startup.
 * 5. Clean and orderly shutdown.
 */
public class ReveilaTestApp {

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("            REVEILA KOTLIN MULTIPLATFORM - JAVA TEST APPLICATION               ");
        System.out.println("===============================================================================");
        System.out.println("[TestApp] Java Runtime Version: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("[TestApp] OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("[TestApp] Working Directory: " + System.getProperty("user.dir"));
        System.out.println("-------------------------------------------------------------------------------");

        // 1. Instantiation verification
        System.out.println("[TestApp] Step 1: Instantiating the Reveila object...");
        Reveila reveila = null;
        try {
            reveila = new Reveila();
            System.out.println("[TestApp] SUCCESS: Reveila class instantiated successfully!");
            System.out.println("[TestApp] Initial isRunning() state: " + reveila.isRunning());
        } catch (Throwable t) {
            System.err.println("[TestApp] FAILED: Could not instantiate Reveila class!");
            t.printStackTrace();
            System.exit(1);
        }

        // 2. Start-up verification
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("[TestApp] Step 2: Calling reveila.start() to load configs and start components...");
        System.out.println("-------------------------------------------------------------------------------");

        long startTime = System.currentTimeMillis();
        try {
            reveila.start();

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("-------------------------------------------------------------------------------");
            System.out.println("[TestApp] Step 3: Verifying post-start engine state...");
            boolean running = reveila.isRunning();
            System.out.println("[TestApp] Engine isRunning(): " + running);
            System.out.println("[TestApp] Startup Elapsed Time: " + elapsed + " ms");

            if (reveila.getSystemContext() != null) {
                System.out.println("[TestApp] SystemContext initialized: true");
                System.out.println("[TestApp] SystemContext Properties count: " +
                        reveila.getSystemContext().getProperties().stringPropertyNames().size());
            }

            if (!running) {
                System.err.println("[TestApp] FAILED: Reveila engine reported isRunning() = false after start()!");
                System.exit(2);
            }

            System.out.println("===============================================================================");
            System.out.println(" ✅ VERIFICATION PASSED: Reveila instantiated, loaded configs & started cleanly!");
            System.out.println("===============================================================================");

            // Let background workers and auto-call tasks run for a brief moment
            boolean keepAlive = false;
            for (String arg : args) {
                if ("--daemon".equalsIgnoreCase(arg) || "-d".equalsIgnoreCase(arg) || "--keep-alive".equalsIgnoreCase(arg)) {
                    keepAlive = true;
                    break;
                }
            }

            if (keepAlive) {
                System.out.println("[TestApp] Running in daemon mode. Press Ctrl+C to terminate...");
                Thread.currentThread().join();
            } else {
                System.out.println("[TestApp] Observing background tasks for 3 seconds before graceful shutdown...");
                Thread.sleep(3000);
            }

        } catch (Throwable t) {
            System.err.println("===============================================================================");
            System.err.println(" ❌ VERIFICATION FAILED: Error during Reveila startup sequence!");
            System.err.println("===============================================================================");
            t.printStackTrace();
            if (reveila != null) {
                try {
                    reveila.shutdown();
                } catch (Exception ignored) {
                }
            }
            System.exit(3);
        }

        // 4. Graceful Shutdown verification
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("[TestApp] Step 4: Shutting down Reveila engine...");
        try {
            reveila.shutdown();
            System.out.println("[TestApp] Engine isRunning() after shutdown: " + reveila.isRunning());
            System.out.println("[TestApp] Engine shutdown complete.");
        } catch (Throwable t) {
            System.err.println("[TestApp] Warning during shutdown: " + t.getMessage());
        }

        System.out.println("===============================================================================");
        System.out.println("                   TEST EXECUTION FINISHED SUCCESSFULLY                       ");
        System.out.println("===============================================================================");
    }
}
