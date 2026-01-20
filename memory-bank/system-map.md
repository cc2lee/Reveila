graph TB
    subgraph Clients ["Client Layer (Monorepo)"]
        AndroidApp[Android App<br/>'Java/Kotlin']
        WebApp[Web App<br/>'React/TS']
    end

    subgraph Backend ["Server Layer (Monorepo)"]
        JavaAPI[Java Backend<br/>'Spring Boot']
        DB[(PostgreSQL)]
    end

    subgraph Plugins ["Extension Layer"]
        PluginA[Plugin A<br/>'DEX JAR']
        PluginB[Plugin B<br/>'DEX JAR']
    end

    %% Relationships
    AndroidApp -- "REST / JSON" --> JavaAPI
    WebApp -- "REST / JSON" --> JavaAPI
    JavaAPI -- "JPA / JDBC" --> DB

    %% Dynamic Loading
    AndroidApp -- "DexClassLoader" --> PluginA
    AndroidApp -- "DexClassLoader" --> PluginB

    %% Styling
    style AndroidApp fill:#3DDC84,stroke:#333,stroke-width:2px
    style WebApp fill:#61DAFB,stroke:#333,stroke-width:2px
    style JavaAPI fill:#6DB33F,stroke:#333,stroke-width:2px
    style DB fill:#336791,stroke:#333,stroke-width:2px