# ADR-001: Quarkus as Application Framework

## Status

Accepted

## Context

The Federated Attribute Resolver requires a Java application framework to build its REST API, manage dependency
injection, and support deployment in cloud-native environments (containers, Kubernetes, serverless). The framework must
support:

- JAX-RS natively for the REST API layer.
- Reactive and non-blocking I/O for efficient delegation to peer servers and registry drivers.
- Fast startup time, since FAR servers may be scaled horizontally and restarted frequently.
- Low memory footprint to reduce infrastructure cost across a mesh of servers.
- Ahead-of-time (native) compilation as a deployment option for latency-sensitive environments.
- A mature CDI (Contexts and Dependency Injection) implementation for the plugin/driver architecture.

The two leading candidates are **Spring Boot** and **Quarkus**.

Spring Boot is the dominant Java framework with a vast ecosystem. However, it relies on runtime reflection and classpath
scanning, leading to slower startup and higher memory consumption. Native compilation via Spring Native / GraalVM is
supported but remains less mature than Quarkus's native story.

Quarkus is designed from the ground up for cloud-native Java. It performs build-time initialization, supports JAX-RS
natively via RESTEasy, and has first-class GraalVM native image support. Its CDI implementation (ArC) is compile-time
optimized.

## Decision

We will use **Quarkus with RESTEasy Reactive** as the application framework for FAR.

Specifically:

- **RESTEasy Reactive** for the JAX-RS REST API layer, providing non-blocking request handling on the Vert.x event loop.
- **ArC CDI** for dependency injection and the driver plugin lifecycle.
- **Quarkus native build** as a supported (but not required) deployment option.
- **Quarkus Dev Services** for local development and testing with external dependencies.

## Consequences

### Positive

- **Fast startup.** Quarkus JVM mode starts in under one second; native mode in tens of milliseconds. This supports
  rapid horizontal scaling of FAR mesh nodes.
- **Low memory.** Build-time class analysis and dead code elimination reduce the runtime memory footprint, which matters
  when deploying many FAR servers across a mesh.
- **Native JAX-RS.** RESTEasy Reactive is a first-class citizen in Quarkus, not a bolted-on adapter. This simplifies the
  REST API layer and content negotiation implementation.
- **Native compilation.** GraalVM native images are a production-supported deployment target, enabling deployment in
  latency-sensitive or resource-constrained environments.
- **Strong CDI support.** ArC provides compile-time CDI that works well with the SPI-based driver model (ADR-004).
- **Reactive foundation.** The Vert.x underpinning supports efficient non-blocking delegation to peer servers without
  thread-per-request overhead.

### Negative

- **Smaller ecosystem.** Quarkus has fewer third-party libraries and integrations compared to Spring Boot. Some
  libraries may require manual Quarkus extensions or compatibility work.
- **Team familiarity.** Developers with Spring Boot experience will need to learn Quarkus conventions, particularly
  around build-time CDI and the reactive programming model.
- **Native compilation constraints.** GraalVM native images impose restrictions on reflection, dynamic proxies, and
  classpath resources. The SPI-based driver model (ADR-004) must be designed with these constraints in mind.
