package com.reveila.system;

import java.util.ArrayList;
import java.util.List;

public class Manifest {
    
    private String name;
    private String displayName;
    private String version;
    private String description;
    private String author;
    private String org;
    private List<String> roles = new ArrayList<>();
    private List<String> requiredRoles = new ArrayList<>();
    private String implementationClass;
    private String componentType; // "system" ("component") or "plugin"
    private List<ExposedMethod> exposedMethods = new ArrayList<>();
    private boolean isolate = false;

    public String getOrg() {
        return org;
    }
    public void setOrg(String org) {
        this.org = org;
    }
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }
    public boolean isIsolate() {
        return isolate;
    }
    public void setIsolate(boolean isolate) {
        this.isolate = isolate;
    }
    public List<String> getRoles() { return roles; }
    public List<String> getRequiredRoles() { return requiredRoles; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getImplementationClass() { return implementationClass; }
    public void setImplementationClass(String implementationClass) { this.implementationClass = implementationClass; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public List<ExposedMethod> getExposedMethods() { return exposedMethods; }
    public void setExposedMethods(List<ExposedMethod> exposedMethods) { this.exposedMethods = exposedMethods; }
    
    public static class ExposedMethod {
        public String name;
        public String description;
        public List<Parameter> parameters = new ArrayList<>();
        public String returnType;
        public List<String> requiredRoles = new ArrayList<>();
    }
    
    public static class Parameter {
        public String name;
        public String description;
        public String type;
        public boolean isRequired;
        public boolean isSecret;
    }
}