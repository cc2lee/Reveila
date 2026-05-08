package com.reveila.android;

/**
 * Result of a vault scan operation.
 * Java implementation of the original Kotlin data class.
 */
public class ScanResult {
    private final int newFilesCount;
    private final int entitiesDiscoveredCount;

    public ScanResult(int newFilesCount, int entitiesDiscoveredCount) {
        this.newFilesCount = newFilesCount;
        this.entitiesDiscoveredCount = entitiesDiscoveredCount;
    }

    public int getNewFilesCount() {
        return newFilesCount;
    }

    public int getEntitiesDiscoveredCount() {
        return entitiesDiscoveredCount;
    }
}