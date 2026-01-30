package com.reveila.spring.data;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Set;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    // Optional: Bi-directional mapping if you need to find all users from an org
    @OneToMany(mappedBy = "org", cascade = CascadeType.ALL)
    private Set<User> users;

    // Mandatory No-args constructor for Hibernate
    public Organization() {}

    public Organization(String name) {
        this.name = name;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}