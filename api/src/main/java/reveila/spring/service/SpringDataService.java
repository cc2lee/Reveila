package reveila.spring.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reveila.service.AbstractDataService;
import reveila.service.Page;
import reveila.spring.SpringPlatformAdapter;

/**
 * A DataService implementation for the Spring environment.
 * This implementation uses Spring Data JPA to interact with a database.
 */
public class SpringDataService extends AbstractDataService {

    private GenericEntityRepository repository;
    private ObjectMapper objectMapper;

    @Override
    public void start() throws Exception {
        systemContext.getLogger(this).info("SpringDataService starting...");
        if (systemContext.getPlatformAdapter() instanceof SpringPlatformAdapter springAdapter) {
            this.repository = springAdapter.getBean(GenericEntityRepository.class);
            this.objectMapper = springAdapter.getBean(ObjectMapper.class);
            systemContext.getLogger(this).info("SpringDataService started and wired to Spring Data JPA.");
        } else {
            throw new IllegalStateException("SpringDataService requires a SpringPlatformAdapter to function.");
        }
    }

    @Override
    public Map<String, Object> save(String entityName, Map<String, Object> data) {
        try {
            GenericEntity entity;
            Object idObject = data.get("id");
 
            // If an ID is provided, treat it as an update. Otherwise, it's a new entity.
            if (idObject instanceof String id && !id.trim().isEmpty()) {
                entity = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found for update."));
                // Optional: Check if the entity type matches
                if (!entity.getEntityType().equals(entityName)) {
                    throw new IllegalArgumentException(
                        String.format("Entity with id %s has type %s, but was expecting %s.",
                            id, entity.getEntityType(), entityName));
                }
            } else {
                entity = new GenericEntity();
                entity.setEntityType(entityName);
            }

            // The ID from the map should not be part of the JSON blob
            Map<String, Object> dataToStore = new HashMap<>(data);
            dataToStore.remove("id");
            entity.setJsonData(objectMapper.writeValueAsString(dataToStore));

            GenericEntity savedEntity = repository.save(entity);
            return toMap(savedEntity);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize entity data to JSON", e);
        }
    }

    @Override
    public Optional<Map<String, Object>> findById(String entityName, String id) {
        // The entityName isn't strictly needed if IDs are globally unique.
        return repository.findById(id).map(this::toMap);
    }

    @Override
    public Page<Map<String, Object>> findAll(String entityName, int pageNumber, int pageSize) {
        try {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            org.springframework.data.domain.Page<GenericEntity> springPage = repository.findByEntityType(entityName, pageable);

            List<Map<String, Object>> content = springPage.getContent().stream()
                    .map(this::toMap)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return new Page<>(content, springPage.getNumber(), springPage.getSize(), springPage.getTotalElements());
        } catch (Exception e) {
            systemContext.getLogger(this).log(Level.SEVERE, "Error finding all entities for: " + entityName, e);
            return new Page<>(Collections.emptyList(), pageNumber, pageSize, 0);
        }
    }

    @Override
    public void deleteById(String entityName, String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Entity with id " + id + " not found for deletion.");
        }
        repository.deleteById(id);
        systemContext.getLogger(this).info(String.format("Deleted entity with ID '%s'.", id));
    }

    private Map<String, Object> toMap(GenericEntity entity) {
        try {
            // The TypeReference is needed to correctly deserialize into a Map.
            Map<String, Object> map = objectMapper.readValue(entity.getJsonData(), new TypeReference<>() {});
            map.put("id", entity.getId()); // Ensure the database-generated/persisted ID is in the map.
            return map;
        } catch (JsonProcessingException e) {
            // This would indicate a data corruption issue if it fails.
            throw new IllegalStateException("Failed to deserialize entity JSON for id: " + entity.getId(), e);
        }
    }
}