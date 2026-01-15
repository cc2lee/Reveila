package com.reveila.system.data;

import java.util.Map;
import java.util.Optional;

public interface ObjectDataAdapter {

    void deleteByKey(String entityType, Map<String, Object> key);

    Page<Map<String, Object>> getPage(String entityType, int pageNumber, int pageSize);

    Optional<Map<String, Object>> getByKey(String entityType, Map<String, Object> key);

    Map<String, Object> save(String entityType, Map<String, Object> key, Map<String, Object> data);

}