package com.reveila.spring.system;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.context.ApplicationContext;

import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.data.GenericRepository;
import com.reveila.data.JavaObjectRepository;
import com.reveila.data.Repository;
import com.reveila.platform.BasePlatformAdapter;

public class SpringPlatformAdapter extends BasePlatformAdapter {

    private final ApplicationContext springContext;

    // The "Registry" of all discovered repositories
    private final Map<String, GenericRepository<?, ?>> repoRegistry = new HashMap<>();

    public SpringPlatformAdapter(ApplicationContext context, Properties commandLineArgs) throws Exception {
        super(commandLineArgs);
        this.springContext = context;

        // Automatically discover and map repositories during initialization
        initialize();
    }

    private void initialize() {
        @SuppressWarnings("rawtypes")
        Map<String, JavaObjectRepository> beans = springContext.getBeansOfType(JavaObjectRepository.class);

        for (JavaObjectRepository<?, ?> repo : beans.values()) {
            String type = repo.getType().toLowerCase();

            // Pass the 'unknown' repo into our capture method
            // This effectively 'unwraps' the Repository<?, ?> into Repository<T, ID>
            @SuppressWarnings("unchecked")
            JavaObjectRepository<?, ?> typedRepo = (JavaObjectRepository<Object, Object>) repo;

            GenericRepository<?, ?> genericRepo = createGenericRepo(typedRepo);

            // Store it as the platform-standard Repository<Entity, Map<String, Object>>
            repoRegistry.put(type, genericRepo);
        }
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