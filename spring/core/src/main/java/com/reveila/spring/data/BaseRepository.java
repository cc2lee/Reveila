package com.reveila.spring.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.reveila.data.Filter;
import com.reveila.data.Page;
import com.reveila.data.Repository;
import com.reveila.data.Sort;
import com.reveila.spring.security.TenantContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class BaseRepository<T, ID> implements Repository<T, ID> {

    protected final EntityManager entityManager;
    protected final Class<T> entityClass;

    protected BaseRepository(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(entityManager.find(entityClass, id));
    }

    @Override
    public T save(T entity) {
        return entityManager.merge(entity);
    }

    @Override
    public Page<T> findAll(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // 1. Data Query
        CriteriaQuery<T> dataQuery = cb.createQuery(entityClass);
        Root<T> root = dataQuery.from(entityClass);

        // Apply Eager Fetches
        if (fetches != null) {
            for (String f : fetches) {
                FetchParent<T, ?> fetch = root;
                for (String part : f.split("\\.")) {
                    fetch = fetch.fetch(part, JoinType.LEFT);
                }
            }
        }

        dataQuery.where(buildPredicate(filter, root, cb));

        // Apply Sorting
        if (sort != null) {
            Path<?> sortPath = root;
            for (String part : sort.field().split("\\.")) {
                sortPath = sortPath.get(part);
            }
            dataQuery.orderBy(sort.ascending() ? cb.asc(sortPath) : cb.desc(sortPath));
        }

        List<T> content = entityManager.createQuery(dataQuery.distinct(true))
                .setFirstResult(page * size)
                .setMaxResults(size + 1)
                .getResultList();

        boolean hasNext = content.size() > size;
        List<T> finalContent = hasNext ? content.subList(0, size) : content;

        // 2. Count Query (Optional)
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

    @SuppressWarnings("unchecked")
    private Predicate buildPredicate(Filter filter, Root<T> root, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        // 1. Inject Global Tenant Filter
        // Check if the entity has an 'org' field
        boolean hasOrgField = Arrays.stream(entityClass.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("org"));

        if (hasOrgField && TenantContext.getTenantId() != null) {
            predicates.add(cb.equal(root.get("org").get("id"), TenantContext.getTenantId()));
        }

        // 2. Existing Filter Logic
        if (filter != null && !filter.getConditions().isEmpty()) {
            filter.getConditions().forEach((field, criterion) -> {
                Path<?> path = root;
                for (String part : field.split("\\.")) {
                    path = path.get(part);
                }
                Object value = criterion.value();
                // Aligned with Filter.SearchOp
                switch (criterion.operator()) {
                    case EQUAL -> predicates.add(cb.equal(path, value));
                    case LIKE -> predicates.add(cb.like(cb.lower(path.as(String.class)),
                            "%" + value.toString().toLowerCase() + "%"));
                    case IN -> predicates.add(path.in((Collection<?>) value));

                    // These two lines are WHY you need the @SuppressWarnings
                    case GREATER_THAN -> predicates.add(cb.greaterThan(
                            (Path<Comparable<Object>>) path, (Comparable<Object>) value));
                    case LESS_THAN -> predicates.add(cb.lessThan(
                            (Path<Comparable<Object>>) path, (Comparable<Object>) value));
                }
            });
        }

        // 3. Combine with Logical Operator
        Predicate filterPredicate = (filter != null && filter.getLogicalOp() == Filter.LogicalOp.OR)
                ? cb.or(predicates.toArray(new Predicate[0]))
                : cb.and(predicates.toArray(new Predicate[0]));

        return filterPredicate;
    }

    // Standard implementations
    @Override
    public List<T> saveAll(Collection<T> entities) {
        List<T> result = new ArrayList<>();
        for (T entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public List<T> findAll() {
        return entityManager.createQuery("SELECT e FROM " + entityClass.getSimpleName() + " e", entityClass)
                .getResultList();
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    // Standard implementations you already had:
    @Override
    public void deleteById(ID id) {
        findById(id).ifPresent(entityManager::remove);
    }

    @Override
    public long count() {
        return entityManager.createQuery("SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e", Long.class)
                .getSingleResult();
    }

    @Override
    public void flush() {
        entityManager.flush();
    }
}