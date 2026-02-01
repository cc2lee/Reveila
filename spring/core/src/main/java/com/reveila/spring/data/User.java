package com.reveila.spring.data;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.persistence.*;
import java.util.Collection;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    private boolean enabled = true;

    private String s3FolderId;

    // @ManyToOne: Many users belong to one organization.

    // By default, @ManyToOne is optional (optional = true). This means a user can
    // exist in the database without an organization.
    // If every user MUST belong to an org: Change it to @ManyToOne(fetch =
    // FetchType.LAZY, optional = false). This helps the Hibernate query optimizer
    // and adds a NOT NULL constraint to the database schema.

    // fetch = FetchType.LAZY: Load organization data only when accessed.
    // @JoinColumn(name = "org_id"): Specify the column to join on (the physical
    // column in the users table is named org_id).

    // By default, @ManyToOne is Eager in JPA, setting it to LAZY (fetch = FetchType.LAZY) 
    // can help performance in the User class to prevent unnecessary database hits.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private Organization org;

    public Organization getOrg() {
        return org;
    }

    public void setOrg(Organization org) {
        this.org = org;
    }

    // Transient or Persistent authorities (depending on your Role strategy)
    @Transient
    private Collection<? extends GrantedAuthority> authorities;

    // 1. Mandatory No-Args Constructor for Hibernate
    protected User() {
    }

    // 2. Business Constructor
    public User(String username, String password, Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    // --- UserDetails Interface Implementation ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // Standard defaults for security (can be backed by DB columns if needed)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // --- Standard Getters/Setters ---

    public UUID getId() {
        return id;
    }

    public String getS3FolderId() {
        return s3FolderId;
    }
}