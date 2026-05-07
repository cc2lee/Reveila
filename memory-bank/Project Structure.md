

Reveila-Suite/
├── android/            # The Native Android Library (The Bridge)
├── reveila/            # The Core Java Engine (Headless)
├── spring/             # Spring Boot Server (Drive the Engine)
├── ts/js/              # SOVEREIGN JS CORE (The Brain)
│   ├── reveila-core.js # Universal Client (Unified Fetch/Bridge)
│   └── bridge-adapter/ # Native-specific adapters (Expo vs. Web)
├── apps/
│   ├── expo/
│   │   └── Reveila/    # DISPOSABLE EXPO SHELL (The View)
│   └── web/            # Vue Client (The View)