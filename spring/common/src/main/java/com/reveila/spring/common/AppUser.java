package com.reveila.spring.common;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class AppUser extends User {

    private final String s3FolderId;

    public AppUser(String username, String password, Collection<? extends GrantedAuthority> authorities, String s3FolderId) {
        super(username, password, authorities);
        this.s3FolderId = s3FolderId;
    }

    public String getS3FolderId() {
        return s3FolderId;
    }
}
