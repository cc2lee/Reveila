# Reveila Plugin Security & Perimeters

Reveila uses a layered security model to enforce "Least Privilege" for all plugin executions. This is governed by **Security Perimeters** (previously known as Agency Perimeters).

## 1. The Global Perimeter
A base security policy is defined globally in `system-home/standard/configs/global-perimeter.json`. This policy sets the default resource limits and access restrictions for any plugin that does not define its own.

```json
{
    "accessScopes": [],
    "allowedDomains": [],
    "internetAccessBlocked": false,
    "maxMemoryMb": 512,
    "maxCpuCores": 1,
    "maxExecutionSec": 30,
    "delegationAllowed": false
}
```

## 2. Plugin-Specific Perimeters
Plugins can define their own `agency_perimeter` block within their manifest file (located in `configs/plugins/*.json`). This allows developers to request specific capabilities (like network access to a specific domain) or restrict resources further.

### Manifest Structure
The `agency_perimeter` should be a top-level property within the `plugin` object:

```json
{
  "plugin": {
    "name": "MySecurePlugin",
    "displayName": "My Secure Plugin",
    "version": "1.0.0",
    "class": "com.reveila.service.MySecurePlugin",
    "agency_perimeter": {
      "accessScopes": ["db.read", "fs.write:/tmp"],
      "allowedDomains": ["api.trusted-partner.com"],
      "internetAccessBlocked": false,
      "maxMemoryMb": 1024,
      "maxCpuCores": 2,
      "maxExecutionSec": 60,
      "delegationAllowed": true
    }
  }
}
```

## 3. Enforcement Logic (Intersection)
When a plugin is invoked, Reveila performs a **Perimeter Intersection**. The actual enforced security policy is the *most restrictive* combination of the Global Perimeter and the Plugin-Specific Perimeter:

- **Scopes/Domains:** Only those present in *both* are allowed (unless one is empty, in which case the more specific one usually applies depending on implementation, but typically the most restrictive is chosen).
- **Boolean Flags:** If either perimeter blocks internet access or forbids delegation, it is forbidden.
- **Resource Limits:** The *minimum* value (lowest memory, shortest time, fewest cores) is always selected.

This ensures that even if a plugin requests high resource limits, the system administrator can cap them globally, and vice-versa.
