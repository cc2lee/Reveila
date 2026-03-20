package com.reveila.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Handles the first-run database schema initialization dynamically.
 */
public class SchemaInitializer {

    /**
     * Reads the schema.sql and applies engine-specific transformations before execution.
     */
    public static void initialize(Connection connection, boolean isSqlite) {
        try {
            String sql = "";
            // 1. Try resolving from the physical workspace/runtime directory first
            File file = new File("system-home/standard/resources/db/scripts/schema.sql");
            if (file.exists()) {
                try (InputStream is = new FileInputStream(file)) {
                    sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                // 2. Fallback to classpath if packaged in a JAR
                try (InputStream is = SchemaInitializer.class.getResourceAsStream("/db/scripts/schema.sql")) {
                    if (is != null) {
                        sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    } else {
                        System.err.println("SchemaInitializer: Could not locate schema.sql");
                        return;
                    }
                }
            }

            // Apply SQLite specific dialect rules dynamically
            if (isSqlite) {
                sql = sql.replaceAll("(?i)\\bJSONB\\b", "TEXT");
                sql = sql.replaceAll("(?i)\\bBYTEA\\b", "BLOB");
                sql = sql.replaceAll("(?i)\\bTIMESTAMPTZ\\b", "TEXT");
                sql = sql.replaceAll("(?i)\\bSERIAL\\s+PRIMARY\\s+KEY\\b", "INTEGER PRIMARY KEY AUTOINCREMENT");
                sql = sql.replaceAll("(?i)\\bSERIAL\\b", "INTEGER PRIMARY KEY AUTOINCREMENT");
                
                // UUID adjustments for SQLite
                sql = sql.replaceAll("(?i)UUID\\s+PRIMARY\\s+KEY\\s+DEFAULT\\s+gen_random_uuid\\(\\)", "TEXT PRIMARY KEY");
                sql = sql.replaceAll("(?i)\\bUUID\\b", "TEXT");

                // Strip Postgres-only commands
                sql = sql.replaceAll("(?i)CREATE\\s+EXTENSION\\s+IF\\s+NOT\\s+EXISTS\\s+vector;", "");

                // [ ] 1. Persistence & Memory: Ensure vector schema uses vec0 virtual table format for fast KNN searches
                // E.g. converting CREATE TABLE semantic_vectors ( id TEXT, embedding vector(1536) ... ) to vec0 format
                // This is a naive regex that captures standard table creations for entity_graph / semantic_vectors
                // and forces the sqlite-vec virtual table construct: CREATE VIRTUAL TABLE name USING vec0(...)
                sql = sql.replaceAll(
                    "(?i)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+(entity_graph|semantic_vectors)\\s*\\((.*?)\\);", 
                    "CREATE VIRTUAL TABLE IF NOT EXISTS $1 USING vec0(id TEXT, embedding float[1536]);"
                );
            }

            try (Statement stmt = connection.createStatement()) {
                if (isSqlite) {
                    // SQLite requires splitting by semicolon and omitting PL/pgSQL constructs
                    for (String statement : sql.split(";")) {
                        String s = statement.trim();
                        if (!s.isEmpty() && 
                            !s.toUpperCase().startsWith("CREATE OR REPLACE FUNCTION") && 
                            !s.toUpperCase().startsWith("CREATE TRIGGER") && 
                            !s.toUpperCase().startsWith("DROP TRIGGER")) {
                            stmt.executeUpdate(s);
                        }
                    }
                } else {
                    // PostgreSQL handles the entire script natively as-is
                    stmt.executeUpdate(sql);
                }
            }
        } catch (Exception e) {
            System.err.println("Schema Initialization failed: " + e.getMessage());
        }
    }
}
