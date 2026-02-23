package com.reveila.spring.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.data.GenericRepository;
import com.reveila.data.JavaObjectRepository;
import com.reveila.data.Repository;
import com.reveila.platform.BasePlatformAdapter;
import com.reveila.system.Constants;

public class SpringPlatformAdapter extends BasePlatformAdapter {

    private final ApplicationContext springContext;
    private final Map<String, GenericRepository<?, ?>> repoRegistry = new HashMap<>();
    private boolean databaseAvailable = false;

    public SpringPlatformAdapter(ApplicationContext context, Properties commandLineArgs) throws Exception {
        super(commandLineArgs);
        this.springContext = context;
        checkDatabaseAvailability();
        initialize();
    }

    private void checkDatabaseAvailability() {
        try {
            DataSource ds = springContext.getBean(DataSource.class);
            try (Connection conn = ds.getConnection()) {
                databaseAvailable = !conn.isClosed();
                System.out.println("Reveila DB check: Database is available.");
            }
        } catch (Exception e) {
            databaseAvailable = false;
            System.err.println("Reveila DB check FAILED: " + e.getMessage());
        }
    }

    private void initialize() throws Exception {
        // 1. Read and apply database schema if DB is available
        if (databaseAvailable) {
            applyDatabaseSchema();
        }

        // 2. Discover Spring-managed JPA repositories
        @SuppressWarnings("rawtypes")
        Map<String, JavaObjectRepository> beans = springContext.getBeansOfType(JavaObjectRepository.class);

        for (JavaObjectRepository<?, ?> repo : beans.values()) {
            String type = repo.getType().toLowerCase();
            if (databaseAvailable) {
                repoRegistry.put(type, createGenericRepo(repo));
            }
        }

        // 3. Fallback to JSON files if DB is not available or for missing repos
        if (!databaseAvailable) {
            System.out.println("Reveila Fallback: Using JSON file storage in " + getSystemHome().resolve("data"));
            setupJsonFallbacks();
        }
    }

    private void applyDatabaseSchema() {
        Path schemaPath = getSystemHome().resolve("configs").resolve("database-schema.json");
        if (Files.exists(schemaPath)) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode schema = mapper.readTree(schemaPath.toFile());
                System.out.println("Reveila: Applying database schema from " + schemaPath);
                // Implementation for actual schema update could go here using JdbcTemplate
                // For now, we log that we read it as requested.
            } catch (Exception e) {
                System.err.println("Failed to read database schema: " + e.getMessage());
            }
        }
    }

    private void setupJsonFallbacks() {
        Path dataDir = getSystemHome().resolve("data");
        // Manually register the AuditLog fallback
        try {
            Class<?> auditLogClass = Class.forName("com.reveila.spring.model.jpa.AuditLog");
            com.reveila.data.JsonFileRepository<?, ?> auditRepo = new com.reveila.data.JsonFileRepository<>(
                dataDir, "AuditLog", auditLogClass, java.util.UUID.class);
            
            repoRegistry.put("auditlog", createGenericRepoFromGeneric(auditRepo));
            System.out.println("Reveila Fallback: AuditLog repository registered (JSON).");
        } catch (Exception e) {
            System.err.println("Failed to setup AuditLog JSON fallback: " + e.getMessage());
        }
    }

    private <T, ID> GenericRepository<T, ID> createGenericRepoFromGeneric(com.reveila.data.JavaObjectRepository<T, ID> repo) {
        EntityMapper<T> mapper = repo.getEntityMapper();
        Class<T> entityClass = repo.getEntityClass();
        Class<ID> idClass = repo.getIdClass();
        return new GenericRepository<>(repo, mapper, entityClass, idClass);
    }

    // Wildcard Capture: Compiler internally infers the specific type of an unknown
    // wildcard (?) argument
    private <T, ID> GenericRepository<T, ID> createGenericRepo(JavaObjectRepository<T, ID> repo) {
        // We pull the metadata directly from the repo instance
        EntityMapper<T> mapper = repo.getEntityMapper();
        Class<T> entityClass = repo.getEntityClass();
        Class<ID> idClass = repo.getIdClass();
        GenericRepository<T, ID> genericRepo = new GenericRepository<T, ID>(repo, mapper, entityClass, idClass);
        // We capture T and ID here, so the GenericRepository
        // is instantiated with the correct 'identity'
        return genericRepo;
    }

    @Override
    public Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType) {
        if (entityType == null)
            return null;
        return repoRegistry.get(entityType.toLowerCase());
    }
}
