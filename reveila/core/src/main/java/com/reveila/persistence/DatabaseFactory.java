package com.reveila.persistence;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

public class DatabaseFactory {

    /**
     * Resolves the appropriate DatabaseEngine based on configuration or environment heuristics.
     * On first run, it initializes the schema dynamically using SchemaInitializer.
     */
    public static DatabaseEngine getEngine() {
        Properties props = new Properties();
        
        try (InputStream is = DatabaseFactory.class.getResourceAsStream("/reveila.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            // Ignore
        }

        String dbType = props.getProperty("REVEILA_DB_TYPE");
        if (dbType == null || dbType.trim().isEmpty()) {
            dbType = System.getenv("REVEILA_DB_TYPE");
        }

        // [ ] 1. Persistence & Memory: DatabaseFactory defaults to reveila_memory.db
        // In Android, System.getProperty("reveila.internal.dir") should be set to context.getFilesDir().getAbsolutePath()
        String internalDir = System.getProperty("reveila.internal.dir", ".");
        
        // Ensure the database is strictly mapped to the formal ${system.home}/data directory
        File dbDir = new File(internalDir, "system-home/standard/data");
        if (!dbDir.exists()) {
            dbDir.mkdirs(); // SQLite requires the parent directory to exist before connection
        }
        String sqlitePath = new File(dbDir, "reveila_memory.db").getAbsolutePath();

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
