package com.reveila.spring.data;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    // We use @JsonIgnore to prevent the infinite recursion loop 
    // when mapping User -> Org -> Users -> Org...
    @JsonIgnore
    @OneToMany(mappedBy = "org") 
    private Set<User> users = new HashSet<>();

    public Organization() {}

    public Organization(String name) {
        this.name = name;
    }

    // Standard Getters/Setters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<User> getUsers() { return users; }
}