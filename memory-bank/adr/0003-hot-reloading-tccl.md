# [ADR-0003]: Hot-Reloading and Thread Context Management

* **Status:** Accepted
* **Deciders:** Charles Lee
* **Date:** 2026-01-18
* **Tags:** #java #hot-reload #concurrency #TCCL

## 1. Context and Problem Statement
In the Reveila-Suite, we require the ability to reload plugin code without restarting the backend process. Traditional class loading creates two major issues:
1. **Thread Safety:** Swapping a loader while a thread is executing code from that loader causes `LinkageErrors`.
2. **Context Blindness:** Many third-party libraries (Jackson, Logback) use the Thread Context ClassLoader (TCCL). If the TCCL isn't set to the plugin's loader, these libraries fail to find classes.

## 2. Decision Drivers
* **Zero Downtime:** Reloads must not interrupt active user requests.
* **Isolation:** Plugins must operate in their own class "universe" without leaking into the host system.
* **Stability:** Prevent memory leaks caused by "dangling" ClassLoaders in thread pools.

## 3. Considered Options
* **Option 1: Standard Synchronized Blocks:** Simple but causes performance bottlenecks as reloads block all requests.
* **Option 2: ReentrantReadWriteLock + TCCL Sandwich:** Uses a Read-Write lock for high concurrency and a "wrap-execute-restore" pattern for TCCL.

## 4. Decision Outcome
Chosen option: **Option 2: ReentrantReadWriteLock + TCCL Sandwich**. 

### 4.1 Implementation Logic
* **Read-Write Locking:** Multiple threads can `invoke()` simultaneously (Read Lock). The `setClassLoader()` method takes a Write Lock, ensuring no code is executing during the swap.
* **TCCL Sandwich:** Every invocation must capture the original TCCL, set it to the Plugin Loader, and restore the original in a `finally` block.
* **State Invalidation:** During a swap, all cached `singletonInstances` and `implementationClasses` must be nullified.



## 5. Positive Consequences
* **High Throughput:** Simultaneous users don't block each other.
* **Async Safety:** `invokeAsync` correctly propagates context to the `ForkJoinPool`.
* **Clean Cleanup:** Old `URLClassLoaders` are explicitly closed, releasing file handles on the OS.

## 6. Negative Consequences (Trade-offs)
* **Complexity:** Requires strict adherence to the "finally" block pattern to avoid polluting thread pools.
* **ClassCast Risk:** Objects shared between the Host and Plugin must come from the Parent ClassLoader (Common-API) to avoid identity mismatches.

## 7. Code Standards
All Proxy-based calls must follow this template:
1. Capture current TCCL.
2. Set TCCL to `Proxy.getClassLoader()`.
3. Try { execute } finally { restore TCCL }.