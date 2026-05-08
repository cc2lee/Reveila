package com.reveila.android.data;

import java.util.Map;

public interface VaultRepository {
    Map<String, Long> getIndexedFiles();
    void insertFact(String subject, String type, String predicate, String object, String objectType, String metadata);
    void markFileAsIndexed(String uri, long timestamp);
    // Added this to satisfy the ReveilaModule call
    void saveSecret(String data); 
}