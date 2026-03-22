package com.reveila.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An implementation of DatabaseEngine for the Personal Edition using SQLite.
 * It uses the sqlite-jdbc driver and implements the VectorStore interface 
 * using the sqlite-vec extension syntax.
 */
public class SqliteEngine implements DatabaseEngine, VectorStore {

    private Connection connection;

    public SqliteEngine(String dbPath) {
        try {
            // [ ] 1. Persistence & Memory: Load sqlite-vec extension natively via JNI
            try {
                System.loadLibrary("sqlite_vec");
            } catch (UnsatisfiedLinkError e) {
                System.err.println("sqlite_vec library not bundled in jniLibs natively, skipping JNI load. " + e.getMessage());
            }

            // Initialize SQLite connection using the sqlite-jdbc driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            // [ ] 1. Persistence & Memory: Verify extension loading via SELECT vec_version()
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT vec_version()")) {
                if (rs.next()) {
                    System.out.println("sqlite-vec extension loaded. Version: " + rs.getString(1));
                }
            } catch (Exception e) {
                System.err.println("Failed to verify vec_version(). Extension might not be fully linked. " + e.getMessage());
            }
            
            // Note: Schema initialization is now orchestrated by DatabaseFactory via SchemaInitializer
        } catch (Exception e) {
            System.err.println("Failed to connect to SQLite: " + e.getMessage());
        }
    }

    /**
     * Exposes the connection specifically for the Factory's SchemaInitializer.
     */
    public Connection getConnection() {
        return connection;
    }

    // ==========================================
    // DatabaseEngine Implementation (Stubs for CRUD)
    // ==========================================
    
    @Override
    public <T> T save(T entity) {
        return entity;
    }

    @Override
    public <T, ID> Optional<T> findById(ID id, Class<T> entityClass) {
        return Optional.empty();
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        return List.of();
    }

    @Override
    public <T> void delete(T entity) {
    }

    @Override
    public <T, ID> void deleteById(ID id, Class<T> entityClass) {
    }

    // ==========================================
    // VectorStore Implementation using sqlite-vec
    // ==========================================

    @Override
    public void insert(String id, float[] vector, String payload) {
        // Updated to target semantic_vectors virtual table
        String sql = "INSERT INTO semantic_vectors (id, embedding, payload) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setBytes(2, serializeVector(vector)); // sqlite-vec handles BLOB format
            pstmt.setString(3, payload);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to insert vector: " + e.getMessage());
        }
    }

    @Override
    public List<VectorMatch> search(float[] query, int limit) {
        List<VectorMatch> matches = new ArrayList<>();
        // Using the sqlite-vec extension syntax exactly as requested
        String sql = "SELECT * FROM semantic_vectors WHERE embedding MATCH ?1 ORDER BY distance LIMIT ?2";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setBytes(1, serializeVector(query)); // ?1
            pstmt.setInt(2, limit);                    // ?2
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    float[] vec = deserializeVector(rs.getBytes("embedding"));
                    String payload = rs.getString("payload");
                    double distance = rs.getDouble("distance");
                    
                    matches.add(new VectorMatch(id, vec, distance, payload));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to execute vector search: " + e.getMessage());
        }
        return matches;
    }

    private byte[] serializeVector(float[] vector) {
        return new byte[0]; 
    }

    private float[] deserializeVector(byte[] bytes) {
        return new float[0];
    }
}
