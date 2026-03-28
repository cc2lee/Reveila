package com.reveila.spring.system;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.data.GenericRepository;
import com.reveila.data.JavaObjectRepository;
import com.reveila.data.Repository;
import com.reveila.system.BasePlatformAdapter;
import com.reveila.system.Constants;

public class SpringPlatformAdapter extends BasePlatformAdapter {

    private final ApplicationContext springContext;
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
            if ("true".equalsIgnoreCase(getProperties().getProperty(Constants.DB_CREATE_SCHEMA))) {
                executeSqlScript("bin/sql/schema.sql");
            }
        }

        // 2. Discover Spring-managed JPA repositories
        @SuppressWarnings("rawtypes")
        Map<String, JavaObjectRepository> beans = springContext.getBeansOfType(JavaObjectRepository.class);

        for (JavaObjectRepository<?, ?> repo : beans.values()) {
            String type = repo.getType().toLowerCase();
            if (databaseAvailable) {
                @SuppressWarnings("unchecked")
                Repository<Entity, Map<String, Map<String, Object>>> repository = (Repository<Entity, Map<String, Map<String, Object>>>)(Object) createGenericRepo(repo);
                registerRepository(type, repository);
            }
        }

        // 3. Fallback to JSON files if DB is not available or for missing repos
        if (!databaseAvailable) {
            System.out.println("Reveila Fallback: Using JSON file storage in " + getSystemHome().resolve("data"));
            setupJsonFallbacks();
        }
    }

    private void executeSqlScript(String relativePath) throws Exception {
        Path scriptPath = getSystemHome().resolve(relativePath);
        if (!Files.exists(scriptPath)) {
            System.err.println("Reveila SQL Executor: Script not found at " + scriptPath);
            return;
        }

        String content = Files.readString(scriptPath, StandardCharsets.UTF_8);
        if (content == null || content.trim().isEmpty()) {
            System.err.println("Reveila SQL Executor: Failed to read " + scriptPath);
            return;
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(springContext.getBean(DataSource.class));
        jdbcTemplate.execute(content);

        System.out.println("Reveila SQL Executor: Successfully applied " + relativePath);
    }

    private void setupJsonFallbacks() {
        // Manually register the AuditLog fallback
        try {
            Class<?> auditLogClass = Class.forName("com.reveila.spring.model.jpa.AuditLog");
            com.reveila.data.JsonFileRepository<?, ?> auditRepo = new com.reveila.data.JsonFileRepository<>(
                    "system-home/standard/data", "AuditLog", auditLogClass, java.util.UUID.class, this);

            @SuppressWarnings("unchecked")
            Repository<Entity, Map<String, Map<String, Object>>> repository = (Repository<Entity, Map<String, Map<String, Object>>>)(Object) createGenericRepoFromGeneric(auditRepo);
            registerRepository("auditlog", repository);
            System.out.println("Reveila Fallback: AuditLog repository registered (JSON).");
        } catch (Exception e) {
            System.err.println("Failed to setup AuditLog JSON fallback: " + e.getMessage());
        }
    }

    private <T, ID> GenericRepository<T, ID> createGenericRepoFromGeneric(
            com.reveila.data.JavaObjectRepository<T, ID> repo) {
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
    public void reloadProperties() throws Exception {
        // 1. Reload from local file first (standard base behavior)
        super.reloadProperties();

        // 2. Override with values from Global Settings table (Sovereign Authority)
        if (databaseAvailable) {
            System.out.println("Reveila: Syncing properties with Global Settings table...");
            try {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(springContext.getBean(DataSource.class));
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT key, value FROM global_settings");

                for (Map<String, Object> row : rows) {
                    String key = (String) row.get("key");
                    String value = (String) row.get("value");
                    if (key != null && value != null) {
                        this.properties.setProperty(key, value);
                    }
                }
                System.out.println("Reveila: Sync complete. " + rows.size() + " properties updated from Database.");
            } catch (Exception e) {
                System.err.println("Reveila: Failed to sync with Global Settings table: " + e.getMessage());
            }
        }
    }

}
