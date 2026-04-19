package com.reveila.system;

import java.io.Serializable;
import java.security.Principal;

public final class UserPrincipal implements Principal, Serializable {

    private String name;

    public UserPrincipal(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Argument 'name' cannot be null or empty.");
        }
        this.name = name;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserPrincipal)) {
            return false;
        }
        return name.equalsIgnoreCase(((UserPrincipal)obj).getName());
    }

}
