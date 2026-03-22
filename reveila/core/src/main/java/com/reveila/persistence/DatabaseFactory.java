package com.reveila.persistence;

import java.util.Properties;

import com.reveila.system.PlatformAdapter;

public class DatabaseFactory {

    /**
     * Resolves the appropriate DatabaseEngine based on configuration or environment heuristics.
     * On first run, it initializes the schema dynamically using SchemaInitializer.
     */
    public static DatabaseEngine getEngine(PlatformAdapter platformAdapter) {
        Properties props = platformAdapter.getProperties();

        String dbType = props.getProperty("REVEILA_DB_TYPE");
        if (dbType == null || dbType.trim().isEmpty()) {
            dbType = System.getenv("REVEILA_DB_TYPE");
        }

        // [ ] 1. Persistence & Memory: DatabaseFactory defaults to reveila_memory.db
        // Ensure the database is strictly mapped to the formal ${system.home}/data directory
        String dbDir = "system-home/standard/data";
        String sqlitePath = "";
        try {
            // Check if directory exists, if not, create it by writing a dummy file and deleting it
            platformAdapter.getFileOutputStream(dbDir + "/.dummy", false).close();
            sqlitePath = platformAdapter.getProperties().getProperty("system.home", ".") + "/" + dbDir + "/reveila_memory.db";
        } catch (Exception e) {
            System.err.println("Could not initialize DB dir: " + e.getMessage());
        }

        DatabaseEngine engine;
        boolean isSqlite = false;

        if ("sqlite".equalsIgnoreCase(dbType)) {
            engine = new SqliteEngine(sqlitePath);
            isSqlite = true;
        } else if ("postgres".equalsIgnoreCase(dbType)) {
            engine = new PostgresEngine();
        } else {
            // Fallbacks
            String osName = System.getProperty("os.name", "").toLowerCase();
            String javaVendor = System.getProperty("java.vendor", "").toLowerCase();
            boolean isLinux = osName.contains("nix") || osName.contains("nux") || osName.contains("aix");
            boolean isAndroid = javaVendor.contains("android");

            if (isLinux && !isAndroid) {
                String dbUrl = System.getenv("DB_URL");
                if (dbUrl == null || dbUrl.trim().isEmpty()) {
                    dbUrl = props.getProperty("DB_URL");
                }
                if (dbUrl != null && !dbUrl.trim().isEmpty()) {
                    engine = new PostgresEngine();
                } else {
                    engine = new SqliteEngine(sqlitePath);
                    isSqlite = true;
                }
            } else {
                // Default to SQLite (Android / Windows / Mac)
                // Note: The REVEILA_DB_URL environment variable is correctly ignored on Android per requirements
                engine = new SqliteEngine(sqlitePath);
                isSqlite = true;
            }
        }

        // Initialize Schema automatically using the factory logic
        if (engine instanceof SqliteEngine sqliteEngine) {
            SchemaInitializer.initialize(sqliteEngine.getConnection(), true);
        } else if (engine instanceof PostgresEngine postgresEngine) {
            SchemaInitializer.initialize(postgresEngine.getConnection(), false);
        }

        return engine;
    }
}
