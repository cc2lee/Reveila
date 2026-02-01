# 🚀 How the database access flow behaves when upon a search request

## Request Flow

JSON Packet: You send a POST to /api/components/DataService/invoke.

ApiController: Jackson uses MethodDTO to parse the JSON. It sees methodName="search" and args[0] as a Map containing your filter and sort.

Reveila Engine: The engine locates the DataService plugin and invokes its search method, passing that args[0] map.

DataService Proxy: Your DataService uses EntityMapper to turn that map into a typed SearchRequest.

Persistence: The SearchRequest is passed to the JpaRepository, where the TenantContext (set by your TenantInterceptor) automatically secures the query.

## Key Components

ApiController	The single entry point for all REST-to-Reveila traffic.
MethodDTO	Standardizes how parameters are passed to components.
DataService	The "reveila-aware" proxy for your database.
SearchRequest	A high-level DTO that encapsulates complex query logic.
SecurityConfig	Protects the API and wires the UserService into the lifecycle.