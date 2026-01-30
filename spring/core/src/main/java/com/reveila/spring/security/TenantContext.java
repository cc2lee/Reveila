package com.reveila.spring.security;

import java.util.UUID;

// A simple thread-local holder for the current user's Org ID
public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) { currentTenant.set(tenantId); }
    public static UUID getTenantId() { return currentTenant.get(); }
    public static void clear() { currentTenant.remove(); }
}