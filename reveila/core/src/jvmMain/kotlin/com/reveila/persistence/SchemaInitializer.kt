package com.reveila.persistence

import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.sql.Connection

/**
 * Handles the first-run database schema initialization dynamically.
 */
class SchemaInitializer private constructor() {

    companion object {
        /**
         * Reads the schema.sql and applies engine-specific transformations before execution.
         */
        @JvmStatic
        fun initialize(connection: Connection, isSqlite: Boolean) {
            try {
                var sql: String
                val file = File("system-home/standard/bin/sql/schema.sql")
                if (file.exists()) {
                    FileInputStream(file).use { `is` ->
                        sql = String(`is`.readAllBytes(), StandardCharsets.UTF_8)
                    }
                } else {
                    var `is` = SchemaInitializer::class.java.getResourceAsStream("/bin/sql/schema.sql")
                    if (`is` == null) {
                        `is` = SchemaInitializer::class.java.getResourceAsStream("/db/scripts/schema.sql")
                    }

                    if (`is` != null) {
                        try {
                            sql = String(`is`.readAllBytes(), StandardCharsets.UTF_8)
                            `is`.close()
                        } catch (e: Exception) {
                            System.err.println("SchemaInitializer: Failed to read schema.sql from classpath")
                            return
                        }
                    } else {
                        System.err.println("SchemaInitializer: Could not locate schema.sql")
                        return
                    }
                }

                if (isSqlite) {
                    sql = sql.replace(Regex("(?i)\\bJSONB\\b"), "TEXT")
                    sql = sql.replace(Regex("(?i)\\bBYTEA\\b"), "BLOB")
                    sql = sql.replace(Regex("(?i)\\bTIMESTAMPTZ\\b"), "TEXT")
                    sql = sql.replace(Regex("(?i)\\bSERIAL\\s+PRIMARY\\s+KEY\\b"), "INTEGER PRIMARY KEY AUTOINCREMENT")
                    sql = sql.replace(Regex("(?i)\\bSERIAL\\b"), "INTEGER PRIMARY KEY AUTOINCREMENT")
                    sql = sql.replace(Regex("(?i)UUID\\s+PRIMARY\\s+KEY\\s+DEFAULT\\s+gen_random_uuid\\(\\)"), "TEXT PRIMARY KEY")
                    sql = sql.replace(Regex("(?i)\\bUUID\\b"), "TEXT")
                    sql = sql.replace(Regex("(?i)CREATE\\s+EXTENSION\\s+IF\\s+NOT\\s+EXISTS\\s+vector;"), "")
                    sql = sql.replace(
                        Regex("(?i)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+(entity_graph|semantic_vectors)\\s*\\((.*?)\\);"),
                        "CREATE VIRTUAL TABLE IF NOT EXISTS $1 USING vec0(id TEXT, embedding float[1536]);"
                    )
                }

                connection.createStatement().use { stmt ->
                    if (isSqlite) {
                        for (statement in sql.split(";")) {
                            val s = statement.trim()
                            if (s.isNotEmpty() &&
                                !s.uppercase().startsWith("CREATE OR REPLACE FUNCTION") &&
                                !s.uppercase().startsWith("CREATE TRIGGER") &&
                                !s.uppercase().startsWith("DROP TRIGGER")
                            ) {
                                stmt.executeUpdate(s)
                            }
                        }
                    } else {
                        stmt.executeUpdate(sql)
                    }
                }
            } catch (e: Exception) {
                System.err.println("Schema Initialization failed: ${e.message}")
            }
        }
    }
}
