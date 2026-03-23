# ADR-004: SPI-Based Plugin Model for Registry Drivers

## Status

Accepted

## Context

The Federated Attribute Resolver must communicate with multiple, heterogeneous certificate registries (Verra, MiQ, REC,
NGC, Carbon Credits, and future registries not yet known). Each registry has its own API, authentication scheme, data
model, and protocol. FAR needs an extensible mechanism to add support for new registries without modifying the core
resolution engine.

The requirements for the driver/plugin model are:

1. **Runtime extensibility.** New registry drivers should be deployable without rebuilding or redeploying the FAR core.
2. **Isolation.** A buggy or slow driver should not compromise the core resolution engine or other drivers.
3. **Standardized interface.** All drivers must implement a common contract so the resolution engine can invoke them
   uniformly.
4. **Multiple deployment modes.** Some drivers may run in-process (for performance), while others may run as separate
   services (for isolation or when implemented in other languages).
5. **Compatibility with native compilation.** The driver model must work with GraalVM native images (see ADR-001).

Candidate approaches:

- **Hardcoded adapters**: simple but not extensible. Adding a registry requires modifying and redeploying core.
- **OSGi**: mature plugin model but heavyweight, complex, and poorly suited to cloud-native deployment.
- **Java SPI (ServiceLoader)**: standard Java mechanism for service discovery. Lightweight, well-understood, supported
  by GraalVM with configuration.
- **HTTP-based drivers**: drivers run as external services invoked via HTTP. Maximum isolation and language
  independence, but higher latency.

## Decision

We will use a **hybrid model combining Java SPI (ServiceLoader) for in-process drivers and HTTP for out-of-process
drivers**.

### In-Process Drivers (Java SPI)

- Drivers implement a standard `RegistryDriver` SPI interface.
- The FAR core discovers drivers at startup via `java.util.ServiceLoader`.
- Each driver declares the namespace(s) it handles.
- Drivers are packaged as JAR files and placed on the classpath.

```java
public interface RegistryDriver {
    Set<String> namespaces();

    Resolution resolve(Identifier identifier, Context context);

    Health check();
}
```

### Out-of-Process Drivers (HTTP)

- Drivers run as standalone HTTP services.
- The FAR core invokes them via a standardized HTTP API contract.
- Configuration maps namespaces to driver HTTP endpoints.
- An `HttpRegistryDriver` SPI implementation acts as a bridge, translating the SPI interface to HTTP calls.

### Driver Resolution Order

1. The resolution engine extracts the namespace from the URN.
2. It looks up the registered driver for that namespace (SPI drivers first, then HTTP driver configuration).
3. If no driver is found and delegation is configured, the request is delegated to a peer (see ADR-005).
4. If no driver and no delegation target exists, the server returns 404.

## Consequences

### Positive

- **Runtime extensibility.** New in-process drivers can be added by dropping a JAR on the classpath and restarting. New
  HTTP drivers can be added by updating configuration — no restart needed if hot-reload is supported.
- **Standardized contract.** The `RegistryDriver` SPI interface ensures all drivers are invoked uniformly. The
  resolution engine has no registry-specific code.
- **Deployment flexibility.** Operators can choose in-process drivers for low latency or HTTP drivers for isolation,
  language independence, and independent scaling.
- **GraalVM compatibility.** Java SPI works with GraalVM native images when drivers are registered in the native image
  configuration at build time. HTTP drivers work without any native image constraints.
- **Testability.** The SPI interface can be mocked in tests. HTTP drivers can be tested independently as standalone
  services.

### Negative

- **SPI discovery limitations.** `ServiceLoader` discovers all implementations on the classpath. If two drivers claim
  the same namespace, conflict resolution logic is needed.
- **Native image registration.** In-process SPI drivers must be registered in GraalVM reflection/service configuration
  at build time. Truly dynamic "drop a JAR" deployment is not possible in native mode.
- **HTTP driver latency.** Out-of-process drivers add network round-trip latency to every resolution. This is acceptable
  for registries with inherently slow APIs but may be problematic for latency-sensitive use cases.
- **Two models to maintain.** Supporting both SPI and HTTP drivers means maintaining two codepaths (the SPI contract and
  the HTTP API contract), though the `HttpRegistryDriver` bridge minimizes duplication.
