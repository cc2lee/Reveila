package com.reveila.system;

import java.security.Principal;

public class RolePrincipal implements Principal {

    private String name;

    public RolePrincipal(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Argument 'name' cannot be null or empty.");
        }
        this.name = roleName;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RolePrincipal)) {
            return false;
        }
        return name.equalsIgnoreCase(((RolePrincipal)obj).getName());
    }

}
