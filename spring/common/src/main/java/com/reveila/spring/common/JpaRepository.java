package com.reveila.spring.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.reveila.data.Filter;
import com.reveila.data.Page;
import com.reveila.data.Repository;
import com.reveila.data.Sort;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public abstract class JpaRepository<T, ID> implements Repository<T, ID> {

    private final EntityManager entityManager;
    private final Class<T> entityClass;

    protected JpaRepository(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(entityManager.find(entityClass, id));
    }

    @Override
    public T save(T entity) {
        // In JPA, we use merge for both new and existing entities
        // to handle the "detached" state common in plugin-based systems.
        return entityManager.merge(entity);
    }

    @Override
    public List<T> findAll() {
        CriteriaQuery<T> cq = entityManager.getCriteriaBuilder().createQuery(entityClass);
        cq.select(cq.from(entityClass));
        return entityManager.createQuery(cq).getResultList();
    }

    /*
     * Example usage: Request the first page (0) with 10 plugins
     * Page<Plugin> pluginPage = pluginRepository.findAll(0, 10);
     * 
     * System.out.println("Total Plugins: " + pluginPage.totalElements());
     * System.out.println("Is last page? " + pluginPage.isLast());
     * 
     * for (Plugin p : pluginPage.content()) {
     * System.out.println("Loaded: " + p.getName());
     * }
     * 
     * Example: Find plugins from Reveila Corp, newest first, page 1
     * Filter filter = new Filter().add("author.company.name", "Reveila Corp");
     * Sort sort = Sort.desc("createdAt");
     * Page<Plugin> page = pluginRepository.findAll(filter, sort, 0, 10, true);
     */
    /**
     * Retrieves a paginated list of all entities of type T.
     *
     * @param page the zero-based page number to retrieve
     * @param size the number of entities per page
     * @return a {@link Page} object containing the requested page of entities,
     *         along with pagination metadata (page number, size, and total element
     *         count)
     */
    @Override
    public Page<T> findAll(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> dataQuery = cb.createQuery(entityClass);
    Root<T> root = dataQuery.from(entityClass);

    // Apply Eager Fetches (The N+1 Fix)
    if (fetches != null) {
        for (String fetchPath : fetches) {
            FetchParent<T, ?> currentFetch = root;
            for (String part : fetchPath.split("\\.")) {
                // JoinType.LEFT is safest to ensure you don't hide entities 
                // that might have a null relationship.
                currentFetch = currentFetch.fetch(part, JoinType.LEFT);
            }
        }
    }
        // Apply Filtering
        dataQuery.where(buildPredicate(filter, root, cb));

        // Apply Sorting (New Logic)
        if (sort != null) {
            Path<?> sortPath = root;
            // Use the same dot-notation logic for sorting!
            for (String part : sort.field().split("\\.")) {
                sortPath = sortPath.get(part);
            }

            dataQuery.orderBy(sort.ascending()
                    ? cb.asc(sortPath)
                    : cb.desc(sortPath));
        }

        // Execution (Pagination)
        // Note: Use DISTINCT if fetching Collections to avoid duplicate rows
        dataQuery.distinct(true);
        List<T> content = entityManager.createQuery(dataQuery)
                .setFirstResult(page * size)
                .setMaxResults(size + 1)
                .getResultList();

        boolean hasNext = content.size() > size;
        List<T> finalContent = hasNext ? content.subList(0, size) : content;

        // Setup the Count Query (Only if requested)
        Long totalElements = null;
        if (includeCount) {
            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            Root<T> countRoot = countQuery.from(entityClass);
            countQuery.select(cb.count(countRoot));
            countQuery.where(buildPredicate(filter, countRoot, cb));
            totalElements = entityManager.createQuery(countQuery).getSingleResult();
        }

        return new Page<>(finalContent, page, size, hasNext, totalElements);
    }

    /**
     * Helper to convert our Filter object into a JPA Predicate.
     */
    @SuppressWarnings("unchecked")
    private Predicate buildPredicate(Filter filter, Root<T> root, CriteriaBuilder cb) {
        if (filter == null || filter.getConditions().isEmpty()) {
            return cb.conjunction();
        }

        List<Predicate> predicates = new ArrayList<>();

        filter.getConditions().forEach((field, criterion) -> {
            // --- START NESTED PATH LOGIC ---
            Path<?> path = root;
            for (String part : field.split("\\.")) {
                path = path.get(part);
            }
            // --- END NESTED PATH LOGIC ---

            Object value = criterion.value();

            // Cast to Comparable for range queries
            Path<Comparable<Object>> compPath = (Path<Comparable<Object>>) path;
            Comparable<Object> compValue = (Comparable<Object>) value;

            switch (criterion.operator()) {
                case EQUAL -> predicates.add(cb.equal(path, value));
                case LIKE -> predicates.add(cb.like(cb.lower(path.as(String.class)),
                        "%" + value.toString().toLowerCase() + "%"));
                case IN -> predicates.add(path.in((Collection<?>) value));
                case GREATER_THAN -> predicates.add(cb.greaterThan(compPath, compValue));
                case LESS_THAN -> predicates.add(cb.lessThan(compPath, compValue));
            }
        });

        return (filter.getLogicalOp() == Filter.LogicalOp.OR)
                ? cb.or(predicates.toArray(new Predicate[0]))
                : cb.and(predicates.toArray(new Predicate[0]));
    }

    @Override
    public void deleteById(ID id) {
        findById(id).ifPresent(entityManager::remove);
    }

    @Override
    public void flush() {
        entityManager.flush();
    }

    @Override
    public long count() {
        var q = entityManager.createQuery("SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e", Long.class);
        return q.getSingleResult();
    }
}