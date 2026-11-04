package com.reveila.spring.service;

import com.reveila.persistence.DefaultVectorStore;
import com.reveila.persistence.VectorMatch;
import com.reveila.persistence.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise-grade VectorStore backed by PostgreSQL and the pgvector HNSW index.
 * Scales to millions of vectors with logarithmic approximate nearest neighbor search (ANN).
 * Gracefully falls back to DefaultVectorStore if pgvector or PostgreSQL is unavailable.
 */
@Service
public class PgVectorStore implements VectorStore {

    private static final Logger logger = LoggerFactory.getLogger(PgVectorStore.class);

    private final DataSource dataSource;
    private final DefaultVectorStore fallbackStore = new DefaultVectorStore();
    private boolean pgVectorAvailable = false;

    @Autowired
    public PgVectorStore(DataSource dataSource) {
        this.dataSource = dataSource;
        initialize();
    }

    private void initialize() {
        if (dataSource == null) {
            logger.warn("No DataSource provided. Operating in fallback in-memory mode.");
            return;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Attempt to enable pgvector extension
            try {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
                pgVectorAvailable = true;
            } catch (SQLException e) {
                logger.warn("pgvector extension not installed or permission denied on PostgreSQL: {}. Using fallback in-memory vector store.", e.getMessage());
                pgVectorAvailable = false;
                return;
            }

            // 2. Create the vector storage table if it does not exist
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS reveila_vector_embeddings (" +
                "    id VARCHAR(255) PRIMARY KEY," +
                "    vector vector," +
                "    payload TEXT," +
                "    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP," +
                "    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 3. Create HNSW index for high-scale cosine similarity search
            try {
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_reveila_vectors_hnsw " +
                    "ON reveila_vector_embeddings USING hnsw (vector vector_cosine_ops)"
                );
            } catch (SQLException e) {
                logger.warn("Could not create HNSW index (version might require IVFFLAT or pgvector >= 0.5): {}", e.getMessage());
            }

            logger.info("PgVectorStore successfully initialized with PostgreSQL pgvector support.");
        } catch (Exception e) {
            logger.warn("Failed to initialize PostgreSQL PgVectorStore: {}. Operating in fallback mode.", e.getMessage());
            pgVectorAvailable = false;
        }
    }

    @Override
    public void insert(String id, float[] vector, String payload) {
        if (!pgVectorAvailable) {
            fallbackStore.insert(id, vector, payload);
            return;
        }

        String vectorStr = toPgVectorString(vector);
        String sql = "INSERT INTO reveila_vector_embeddings (id, vector, payload, updated_at) " +
                     "VALUES (?, ?::vector, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (id) DO UPDATE SET vector = EXCLUDED.vector, payload = EXCLUDED.payload, updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, vectorStr);
            ps.setString(3, payload);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert vector into PostgreSQL for id [{}]: {}", id, e.getMessage());
            fallbackStore.insert(id, vector, payload);
        }
    }

    @Override
    public List<VectorMatch> search(float[] query, int limit) {
        if (!pgVectorAvailable) {
            return fallbackStore.search(query, limit);
        }

        String queryVectorStr = toPgVectorString(query);
        // Uses cosine distance operator (<=>), score = 1 - distance
        String sql = "SELECT id, vector::text as vec_text, payload, (1.0 - (vector <=> ?::vector)) AS score " +
                     "FROM reveila_vector_embeddings " +
                     "ORDER BY vector <=> ?::vector ASC " +
                     "LIMIT ?";

        List<VectorMatch> matches = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queryVectorStr);
            ps.setString(2, queryVectorStr);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String vecText = rs.getString("vec_text");
                    String payload = rs.getString("payload");
                    double score = rs.getDouble("score");
                    float[] parsedVec = parsePgVectorString(vecText);
                    matches.add(new VectorMatch(id, parsedVec, score, payload));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to execute pgvector search: {}. Querying fallback store.", e.getMessage());
            return fallbackStore.search(query, limit);
        }

        return matches;
    }

    @Override
    public boolean delete(String id) {
        if (!pgVectorAvailable) {
            return fallbackStore.delete(id);
        }

        String sql = "DELETE FROM reveila_vector_embeddings WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete vector id [{}] from PostgreSQL: {}", id, e.getMessage());
            return fallbackStore.delete(id);
        }
    }

    @Override
    public int count() {
        if (!pgVectorAvailable) {
            return fallbackStore.count();
        }

        String sql = "SELECT COUNT(*) FROM reveila_vector_embeddings";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count vectors in PostgreSQL: {}", e.getMessage());
        }
        return fallbackStore.count();
    }

    @Override
    public void clear() {
        if (!pgVectorAvailable) {
            fallbackStore.clear();
            return;
        }

        String sql = "TRUNCATE TABLE reveila_vector_embeddings";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            logger.error("Failed to clear reveila_vector_embeddings table: {}", e.getMessage());
            fallbackStore.clear();
        }
    }

    public boolean isPgVectorAvailable() {
        return pgVectorAvailable;
    }

    private static String toPgVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static float[] parsePgVectorString(String vectorStr) {
        if (vectorStr == null || !vectorStr.startsWith("[") || !vectorStr.endsWith("]")) {
            return new float[0];
        }
        String inner = vectorStr.substring(1, vectorStr.length() - 1).trim();
        if (inner.isEmpty()) {
            return new float[0];
        }
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
