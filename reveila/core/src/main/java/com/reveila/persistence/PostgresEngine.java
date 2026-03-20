package com.reveila.persistence;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An implementation of DatabaseEngine for the Enterprise Edition using PostgreSQL.
 * It uses the standard PostgreSQL JDBC driver and implements the VectorStore 
 * interface using the pgvector extension syntax.
 */
public class PostgresEngine implements DatabaseEngine, VectorStore {

    private Connection connection;

    public PostgresEngine() {
        try {
            // Resolve connection parameters from environment with defaults
            String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/reveila");
            String user = System.getenv().getOrDefault("DB_USER", "postgres");
            String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");

            // Initialize PostgreSQL connection
            connection = DriverManager.getConnection(url, user, password);
            // Note: Schema initialization is now orchestrated by DatabaseFactory via SchemaInitializer
        } catch (Exception e) {
            System.err.println("Failed to connect to PostgreSQL: " + e.getMessage());
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
        // TODO: Implement PostgreSQL save logic
        return entity;
    }

    @Override
    public <T, ID> Optional<T> findById(ID id, Class<T> entityClass) {
        // TODO: Implement PostgreSQL findById logic
        return Optional.empty();
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        // TODO: Implement PostgreSQL findAll logic
        return List.of();
    }

    @Override
    public <T> void delete(T entity) {
        // TODO: Implement PostgreSQL delete logic
    }

    @Override
    public <T, ID> void deleteById(ID id, Class<T> entityClass) {
        // TODO: Implement PostgreSQL deleteById logic
    }

    // ==========================================
    // VectorStore Implementation using pgvector
    // ==========================================

    @Override
    public void insert(String id, float[] vector, String payload) {
        // Cast the string representation directly to pgvector's custom type
        String sql = "INSERT INTO memory (id, embedding, payload) VALUES (?, ?::vector, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, formatVector(vector)); // pgvector uses "[1.1, 2.2]" format
            pstmt.setString(3, payload);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to insert vector to PostgreSQL: " + e.getMessage());
        }
    }

    @Override
    public List<VectorMatch> search(float[] query, int limit) {
        List<VectorMatch> matches = new ArrayList<>();
        // Using pgvector's cosine distance operator (<=>) exactly as requested
        // Note: standard JDBC parameters use '?' rather than '?1' or '?2'
        String sql = "SELECT id, embedding, payload, (embedding <=> ?::vector) AS distance " +
                     "FROM memory ORDER BY embedding <=> ?::vector LIMIT ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            String vectorStr = formatVector(query);
            pstmt.setString(1, vectorStr); // parameter for the SELECT distance
            pstmt.setString(2, vectorStr); // parameter for the ORDER BY
            pstmt.setInt(3, limit);        // parameter for the LIMIT
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    float[] vec = parseVector(rs.getString("embedding"));
                    String payload = rs.getString("payload");
                    double distance = rs.getDouble("distance");
                    
                    matches.add(new VectorMatch(id, vec, distance, payload));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to execute PostgreSQL vector search: " + e.getMessage());
        }
        return matches;
    }

    /**
     * Helper to format float[] to pgvector's required string format: "[1.1, 2.2, 3.3]"
     */
    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Helper to parse pgvector's string output back into float[]
     */
    private float[] parseVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty()) return new float[0];
        String[] parts = vectorStr.replace("[", "").replace("]", "").split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }
}
