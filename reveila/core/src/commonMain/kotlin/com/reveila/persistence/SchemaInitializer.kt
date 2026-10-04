package com.reveila.persistence

import com.reveila.system.io.PlatformFileSystem

/**
 * Pure Kotlin Multiplatform utility for database schema transformation.
 */
class SchemaInitializer private constructor() {

    companion object {

        fun transformSql(rawSql: String, isSqlite: Boolean): String {
            if (!isSqlite) return rawSql
            var sql = rawSql
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
            return sql
        }

        fun getSqlStatements(sql: String, isSqlite: Boolean): List<String> {
            val transformed = transformSql(sql, isSqlite)
            if (!isSqlite) return listOf(transformed)

            return transformed.split(";")
                .map { it.trim() }
                .filter { s ->
                    s.isNotEmpty() &&
                    !s.uppercase().startsWith("CREATE OR REPLACE FUNCTION") &&
                    !s.uppercase().startsWith("CREATE TRIGGER") &&
                    !s.uppercase().startsWith("DROP TRIGGER")
                }
        }

        fun loadSchemaSql(schemaPath: String = "system-home/standard/bin/sql/schema.sql"): String? {
            val fs = PlatformFileSystem()
            return if (fs.exists(schemaPath)) {
                fs.readText(schemaPath)
            } else {
                null
            }
        }
    }
}
