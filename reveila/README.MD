# Reveila Data Access Layer (DAL) Guide

This document outlines the standardized approach for database persistence, filtering, and pagination within the **Reveila** platform.

## 🏗 Architecture Overview

The DAL is built on a "Generic-Base" pattern to maximize code reuse and ensure high performance across both desktop and mobile platforms. 



### Core Components
| Component | Purpose |
| :--- | :--- |
| **`Page<T>`** | A record for paginated results (supports both total count and the "+1" slice pattern). |
| **`Filter`** | A dynamic query builder supporting AND/OR logic and nested path navigation (e.g., `author.name`). |
| **`QueryRequest`** | A wrapper that bundles filter, sort, fetch, and page parameters. |
| **`BaseRepository`** | The Hibernate/JPA implementation that handles the Criteria API complexity. |

---

## 🚀 How to Add a New Entity

To add a new data-driven feature (e.g., "Audit Logs"), follow these three steps:

### 1. Create the Entity
Define your Hibernate entity using `jakarta.persistence` annotations.
```java
@Entity
public class AuditLog {
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String action;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}