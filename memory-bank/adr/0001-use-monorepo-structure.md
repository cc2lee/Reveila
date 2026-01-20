# [ADR-0001]: Use Monorepo Structure for Reveila-Suite

* **Status:** Accepted
* **Deciders:** Charles Lee
* **Date:** 2026-01-17
* **Tags:** #architecture #monorepo #java #android #web

## Context and Problem Statement
The Reveila-Suite consists of three primary projects: a Java/Spring Boot backend, an Android mobile application, and a React-based web app. Traditionally, these would be in separate repositories. However, maintaining API consistency and ensuring AI-assisted coding tools (like Roo Code) have full context across the entire stack is difficult with fragmented repositories.

## Decision Drivers
* **AI Context:** AI tools perform better when they can "see" the relationship between the API definition and its consumption.
* **Atomic Changes:** The ability to refactor an API and update its clients in a single commit.
* **Tooling Consistency:** Standardizing rules and "Architect Pro" personas across sub-projects.

## Considered Options
* **Option 1: Multi-Repo (Polyrepo):** Each project (Java, Android, Web) has its own Git repository.
* **Option 2: Monorepo:** All projects reside in one Git repository under the `Reveila-Suite/` root.

## Decision Outcome
Chosen option: **Option 2: Monorepo**, because it provides the unified context required for "vibe coding" and atomic refactoring.

### Positive Consequences
* **Shared Context:** Roo Code can cross-reference Java DTOs when writing Android network code.
* **Simplified Rule Management:** We can use hierarchical `.roo/rules/` folders to enforce standards.

### Negative Consequences
* **Token Usage:** Large codebase scans may consume more tokens if not filtered by `.rooignore`.
* **Build Complexity:** Requires a unified build strategy (e.g., Gradle composite builds).

## Pros and Cons of the Options

### Option 1: Multi-Repo
* **Pros:** Strict access control; smaller, faster individual builds.
* **Cons:** "Blind spots" for AI assistants; difficult to coordinate breaking API changes.

### Option 2: Monorepo
* **Pros:** Single source of truth; high visibility; simplified onboarding for AI.
* **Cons:** Can become unwieldy without proper `.rooignore` and sparse checkouts.

## Implementation Notes
* The root `Reveila-Suite/` will contain a `.roomodes` file to define the "Architect Pro" persona.
* Each sub-project folder will have its own `.roo/rules/` for domain-specific instructions.