package reveila.service;

import java.util.Map;
import java.util.Optional;

/**
 * Defines the contract for a generic data access service.
 * Implementations will handle the specific storage mechanism (e.g., Spring Data, Android SQLite).
 */
public interface DataService {

    Map<String, Object> save(String entityName, Map<String, Object> data);

    Optional<Map<String, Object>> findById(String entityName, String id);

    /**
     * Finds a paginated list of entities, where client can request a specific page of data.
     * 
     * For example, to get the first page of 20 "products", a client would make the following request:
     *
     * POST /api/components/DataService/invoke
     *
     * Body:
     *
     * json
     * {
     *   "methodName": "findAll",
     *   "args": ["products", 0, 20]
     * }
     * 
     * The response will be a JSON object representing the Page, 
     * containing the list of products for that page and the total number of products available.
     *
     * @param entityName The name of the entity to find.
     * @param pageNumber The page number, 0-based.
     * @param pageSize The size of the page.
     * @return A {@link Page} of entities.
     */
    Page<Map<String, Object>> findAll(String entityName, int pageNumber, int pageSize);

    void deleteById(String entityName, String id);
}