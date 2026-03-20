# 🧠 Reveila-Suite Memory Bank

This folder contains the architectural brain of the project. It tracks decisions, system maps, and context to ensure both the human and AI developers stay aligned.

> **Note on Confidential Architectural Artifacts:**
> Additional confidential architectural artifacts (such as GTM strategy and personal editions) are stored outside the main workspace at `c:\IDE\Projects\Misc\Reveila`. AI assistants should reference both this `memory-bank` and the external `Misc/Reveila` directory when gathering context.

## 🗺️ System Maps
* [System Context Map](./system-map.md) - High-level overview of Java, Android, and Web interaction.

## 📝 Architectural Decision Records (ADR)
| ID | Title | Status | Date |
| :--- | :--- | :--- | :--- |
| [0001](./adr/0001-use-monorepo-structure.md) | Use Monorepo Structure | ✅ Accepted | 2026-01-17 |
| 0002 | [Plugin Strategy (DEX Loading)](#) | 💡 Proposed | TBD |

## 🏗️ Standards & Rules
* [Global Standards](../.roo/rules/global-standards.md)
* [Android Standards](../android/.roo/rules/android-standards.md)
* [Configuration Requirements](./configuration-requirements.md)