package com.reveila.standalone;

import com.reveila.system.Reveila;
import com.reveila.system.DefaultPlatformAdapter;
import com.reveila.system.RuntimeUtil;

public class App {
    public static void main(String[] args) {
        System.out.println("Starting Reveila..."); // Need this printout for attaching debugger.

        // A URL pointing to the Reveila properties file can be passed in as an argument to the Java main class, using the following format:
        // "reveila.properties=file:///C:/IDE/Projects/Reveila-Suite/reveila/runtime-directory/configs/reveila.properties"
        
        Thread serverThread = new Thread(() -> {
            Reveila reveila = new Reveila();
            try {
                reveila.start(new DefaultPlatformAdapter(RuntimeUtil.getArgsAsProperties(args)));
                while(!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                reveila.shutdown();
            }
        });

        serverThread.setDaemon(false); // Ensure JVM stays alive
        serverThread.start();
        System.out.println("Server started. Press Ctrl+C to stop.");

        // Use join() to wait for the server thread to finish naturally or be interrupted
        try {
            serverThread.join();
        }
        catch (InterruptedException e) {
            System.out.println("Main thread interrupted, exiting...");
            // Handle graceful shutdown if needed
        }

        // The JVM will only reach this point after server thread has terminated.
        
        System.out.println("Server stopped.");
    }
}
