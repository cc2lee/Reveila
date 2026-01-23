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

public abstract class BaseRepository<T, ID> implements Repository<T, ID> {

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
        if (filter == null || filter.getConditions().isEmpty()) {
            return cb.conjunction();
        }

        List<Predicate> predicates = new ArrayList<>();
        filter.getConditions().forEach((field, criterion) -> {
            Path<?> path = root;
            for (String part : field.split("\\.")) {
                path = path.get(part);
            }

            Object value = criterion.value();
            switch (criterion.operator()) {
                case EQUAL -> predicates.add(cb.equal(path, value));
                case LIKE -> predicates.add(cb.like(cb.lower(path.as(String.class)), "%" + value.toString().toLowerCase() + "%"));
                case IN -> predicates.add(path.in((Collection<?>) value));
                case GREATER_THAN -> predicates.add(cb.greaterThan((Path<Comparable<Object>>) path, (Comparable<Object>) value));
                case LESS_THAN -> predicates.add(cb.lessThan((Path<Comparable<Object>>) path, (Comparable<Object>) value));
            }
        });

        return filter.getLogicalOp() == Filter.LogicalOp.OR 
                ? cb.or(predicates.toArray(new Predicate[0])) 
                : cb.and(predicates.toArray(new Predicate[0]));
    }

    // Standard implementations
    @Override public void deleteById(ID id) { findById(id).ifPresent(entityManager::remove); }
    @Override public long count() { return entityManager.createQuery("SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e", Long.class).getSingleResult(); }
    @Override public void flush() { entityManager.flush(); }
}