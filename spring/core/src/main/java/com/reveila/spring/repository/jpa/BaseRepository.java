package com.reveila.spring.repository.jpa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import com.reveila.data.EntityMapper;
import com.reveila.data.Filter;
import com.reveila.data.Page;
import com.reveila.data.Repository;
import com.reveila.data.Sort;
import com.reveila.spring.security.TenantContext;
import com.reveila.spring.system.ApplicationContextProvider;
import com.reveila.spring.system.EntityMapperRegistry;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class BaseRepository<T, ID>
        extends SimpleJpaRepository<T, ID> implements Repository<T, ID> {

    protected EntityManager entityManager;
    protected Class<T> entityClass;
    protected Class<ID> idClass;
    protected boolean isMultiTenant;
    protected EntityMapper<T> entityMapper;

    @Override
    public Optional<T> fetchById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        return super.findById(id);
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public EntityMapper<T> getEntityMapper() {
        if (this.entityMapper == null) {
            // Look up from the Spring Context or a Static Registry
            this.entityMapper = ApplicationContextProvider.getBean(EntityMapperRegistry.class)
                    .getMapper(entityClass);
        }
        return this.entityMapper;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    // MANDATORY CONSTRUCTOR for Spring Data Custom Base Classes
    public BaseRepository(JpaEntityInformation<T, ID> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.entityClass = entityInformation.getJavaType();
        this.idClass = entityInformation.getIdType();
        this.isMultiTenant = Arrays.stream(entityClass.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("org"));
    }

    public Class<ID> getIdClass() {
        return idClass;
    }

    @Override
    public Page<T> fetchPage(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
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

        // 1. Inject Global Tenant Filter (Optimized with isMultiTenant)
        if (isMultiTenant && TenantContext.getTenantId() != null) {
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

                switch (criterion.operator()) {
                    case EQUAL -> predicates.add(cb.equal(path, value));
                    case LIKE -> predicates.add(cb.like(cb.lower(path.as(String.class)),
                            "%" + value.toString().toLowerCase() + "%"));
                    case IN -> predicates.add(path.in((Collection<?>) value));
                    case GREATER_THAN -> predicates.add(cb.greaterThan(
                            (Path<Comparable<Object>>) path, (Comparable<Object>) value));
                    case LESS_THAN -> predicates.add(cb.lessThan(
                            (Path<Comparable<Object>>) path, (Comparable<Object>) value));
                }
            });
        }

        // 3. Combine with Logical Operator
        return (filter != null && filter.getLogicalOp() == Filter.LogicalOp.OR)
                ? cb.or(predicates.toArray(new Predicate[0]))
                : cb.and(predicates.toArray(new Predicate[0]));
    }

    @Override
    public List<T> storeAll(Collection<T> entities) {
        List<T> result = new ArrayList<>();
        for (T entity : entities) {
            result.add(store(entity));
        }
        return result;
    }

    @Override
    public boolean hasId(ID id) {
        if (id == null) {
            return false;
        }
        return findById(id).isPresent();
    }

    @Override
    public void disposeById(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        super.deleteById(id);
    }

    @Override
    public long count() {
        return entityManager.createQuery("SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e", Long.class)
                .getSingleResult();
    }

    @Override
    public void commit() {
        entityManager.flush();
    }

    @Override
    public String getType() {
        return entityClass.getSimpleName().toLowerCase();
    }

    @Override
    public T store(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        return save(entity);
    }

    @Override
    public List<T> fetchAll() {
        return findAll();
    }
}