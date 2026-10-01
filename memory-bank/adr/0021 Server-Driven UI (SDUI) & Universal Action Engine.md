# Server-Driven UI (SDUI) & Universal Action Engine
## Status

* **Status:** Accepted
* **Context:** The Reveila Suite ecosystem requires multi-platform support across Web, iOS, Android, and React Native. To minimize duplicate code, decouple view rendering from business logic, and eliminate the risks of runtime LLM-generated UI, we are adopting a Server-Driven UI (SDUI) architecture combined with a Universal Action Dispatcher.
* **Decision Drivers:**
* Zero platform-specific view wiring for business logic changes.
* Deterministic, high-performance production rendering without runtime LLM latency or hallucination risks.
* Centralized shared UI schemas with platform-specific layout specialization.



---

## Architecture Overview

1. **Shared Core Schemas:** Stored centrally in `system-home/standard/resources/ui/schemas/`, defining the strict JSON validation contracts and component catalogs allowed across all platforms.
2. **Platform-Specific Layouts:** Specific screen layouts (Web, iOS, Android, React Native) are maintained as static, version-controlled JSON assets served by the backend API (Spring Boot) or local service (Angroid Service).
3. **Universal Action Dispatcher:** Client interactions map abstract `actionId` strings and data bindings to a single backend endpoint (`/api/v1/actions/execute`), routing requests to registered Java business logic handlers.

---

## Directory Structure Strategy

```text
    system-home/
    └── standard/
        └── resources/
            └── ui/
                ├── schemas/
                │   └── master-ui-schema.json          # Master validation schema
                └── layouts/
                    ├── web/
                    │   └── onboarding_v1.json         # Web-specific layout composition
                    ├── ios/
                    │   └── onboarding_v1.json         # iOS SwiftUI layout specialization
                    ├── android/
                    │   └── onboarding_v1.json         # Android Jetpack Compose layout
                    └── react-native/
                        └── onboarding_v1.json         # Cross-platform RN layout

```

---

## Core Artifact Specifications

### 1. Master Component Catalog Schema (`system-home/standard/resources/ui/schemas/component-catalog-schema.json`)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "ReveilaSuiteUIScreen",
  "type": "object",
  "required": ["screenId", "version", "layout"],
  "properties": {
    "screenId": { "type": "string" },
    "version": { "type": "string" },
    "layout": {
      "type": "array",
      "items": { "$ref": "#/$defs/UIComponent" }
    }
  },
  "$defs": {
    "UIComponent": {
      "type": "object",
      "required": ["id", "type", "props"],
      "properties": {
        "id": { "type": "string" },
        "type": { 
          "type": "string",
          "enum": ["Container", "TextField", "Button", "Dropdown", "Text"]
        },
        "props": { "type": "object" },
        "binding": { "type": "string" }
      },
      "additionalProperties": false
    }
  }
}

```

---

## Code Agent Prompts for Implementation

You can copy and paste the following prompts directly into your local coding agent (such as **Cline** or **Antigravity**) to scaffold this architecture into your project.

### Prompt 1: Generate the Spring Boot SDUI Registry & Action Dispatcher

> **Prompt for Agent:**
> *"Please create a Spring Boot REST controller and service layer for the Reveila Suite SDUI framework.
> 1. Create a `SDUIController` that exposes an endpoint `GET /api/v1/ui/layouts/{platform}/{screenId}` which reads static JSON layout files from `system-home/standard/resources/ui/layouts/{platform}/`.
> 2. Create a `UniversalActionController` exposing a POST endpoint `/api/v1/actions/execute` that accepts an `ActionRequest` payload (`actionId` and map of `payload` data), maps the `actionId` using a Spring bean command pattern registry (`Map<String, BusinessActionHandler>`), executes the business logic, and returns an `ActionExecutionResponse`."*
> 
> 

### Prompt 2: Generate the React Native SDUI Renderer & Component Registry

> **Prompt for Agent:**
> *"Please write a React Native component renderer framework in TypeScript for Reveila Suite.
> 1. Build a `ComponentRegistry` mapping `TextField`, `Button`, `Text`, and `Container` to native React Native components.
> 2. Build a `DynamicScreenRenderer` component that accepts a JSON layout specification, manages local form state via JSON pointer data bindings, and triggers an `onExecuteAction(actionId, formState)` callback when action buttons are pressed."*
> 
>