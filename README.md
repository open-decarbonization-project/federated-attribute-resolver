# Federated Attribute Resolver

The reference implementation of the FAR resolution protocol — a federated layer for resolving environmental attribute certificates across registries. Analogous to DNS for certificate data: each server is authoritative for one or more namespaces and delegates to peers for the rest.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for integration tests via Quarkus DevServices)

## Build

```bash
# Compile all modules
mvn compile

# Install to local Maven repo (required by the registry project)
mvn install -DskipTests

# Package as runnable JAR
mvn package -DskipTests
java -jar far-server/target/quarkus-app/quarkus-run.jar
```

## Development

```bash
mvn quarkus:dev -pl far-server
```

Starts the resolver on port 8080 with a stub driver for the `stub` namespace. No external dependencies required in dev mode.

### Quarkus Dev UI

Available at `http://localhost:8080/q/dev-ui` with live configuration, OpenAPI spec, and Swagger UI.

## Test

```bash
# Unit tests (no Docker required)
mvn test -pl far-model,far-spi,far-json,far-core,far-signature,far-rest,far-client,far-driver-stub,far-driver-http

# Integration tests (requires Docker for Quarkus test instance)
mvn test -pl far-test-integration

# All tests
mvn test

# Single test class
mvn test -pl far-model -Dtest=UrnTests
```

Unit tests cover the domain model, URN parsing, filter expressions, JSON serialization round-trips, Ed25519 signing/verification, content digest computation, and driver contracts. Integration tests cover local resolution, federated delegation with signing mock peers, signature verification (strict mode), and rejection of unsigned peer responses.

## Architecture

```
far-model           Domain records: Urn, Resolution, Attribute, Value, Integrity, Peer, Event
far-spi             Driver and FarClient SPIs
far-json            Jackson serializers/deserializers (Resolution, Value, Event, History, Peer)
far-core            Resolution engine: Router, Resolver, Delegator, PeerRegistry
far-signature       Ed25519 key management, RFC 9421 signing/verification, content digest
far-client          HTTP-based FarClient with strict signature verification
far-rest            JAX-RS resources, signature filter, exception mappers
far-driver-stub     In-memory stub driver for testing
far-driver-http     HTTP-based driver for out-of-process registry plugins
far-server          Quarkus application: CDI wiring, configuration, key generation
far-test-integration  End-to-end tests with signing mock peers
```

**Dependency flow:** model → spi → json / signature → core → client → rest / drivers → server

## REST API

| Endpoint | Method | Description |
|---|---|---|
| `/v1/resources` | GET | Federated search with `$filter/$top/$skip/$orderby` |
| `/v1/resources/{urn}` | GET | Resolve URN to full attribute data |
| `/v1/resources/{urn}` | HEAD | Existence check |
| `/v1/resources/{urn}/history` | GET | Audit trail for a certificate |
| `/v1/namespaces` | GET | List local and delegated namespaces |
| `/v1/namespaces/{ns}` | GET | Namespace detail |
| `/v1/peers` | GET | List connected peers |
| `/v1/peers/configuration` | GET | This server's identity, namespaces, and public key |
| `/v1/health` | GET | Health check with namespace/peer counts |

All `/v1/resources` responses are signed per RFC 9421 with Ed25519. The `Content-Digest`, `Signature`, and `Signature-Input` headers enable end-to-end integrity verification.

### Resolve Modes

The resolve endpoint supports `?resolve=` parameter:
- **`exists`** — returns a JSON summary: `{urn, namespace, identifier, exists, resolver}`
- **`summary`** — returns metadata without full attributes: `{urn, namespace, identifier, status, integrity, resolver, timestamp}`
- *(default)* — full resolution with all attributes

### Content Negotiation

The resolve endpoint supports `Accept` header negotiation:
- `application/json` — structured attribute data (default)
- `text/html`, `application/pdf`, `image/png` — pre-formatted renditions from the upstream registry

## Delegation

Resolvers form a federated mesh. Each server owns a set of namespaces and delegates queries for unknown namespaces to connected peers.

- **Routing:** URN namespace determines which driver or peer handles the request
- **Loop prevention:** The `Far-Delegation-Chain` header tracks visited servers; loops return 409
- **Depth limit:** Configurable via `far.delegation.limit` (default: 5)
- **Signatures:** Delegated requests are signed; responses are verified strictly — unsigned or tampered responses are rejected with `DelegationException`
- **Identity propagation:** Bearer tokens are forwarded unchanged through the chain

## Signatures

All inter-server communication uses RFC 9421 HTTP Message Signatures with Ed25519 keys.

**Response signing** covers: `@status`, `content-type`, `content-digest`, `far-resolver`, `far-namespace`.

**Request signing** covers: `@method`, `@target-uri`, `@authority`, plus authorization and delegation headers.

**Key rotation:** The `KeyRing` supports a primary key and retired previous keys with expiry timestamps. Peers try all valid keys when verifying.

**Strict verification:** `HttpFarClient` rejects unsigned, tampered, or unverifiable peer responses by default. The `strict` constructor parameter can be relaxed for testing.

## Configuration

Key properties in `far-server/src/main/resources/application.yaml`:

| Property | Default | Description |
|---|---|---|
| `far.identity` | `https://far.example.com` | This server's identity URI |
| `far.namespaces` | `stub` | Comma-separated owned namespaces |
| `far.delegation.limit` | `5` | Maximum delegation chain depth |
| `far.protocol.version` | `0.1.0` | FAR protocol version |
| `far.keys.id` | `key-1` | Ed25519 signing key identifier |
| `far.keys.directory` | *(auto-generate)* | Path to PEM key files |
| `far.keys.previous.directory` | *(none)* | Path to retired key PEM files |
| `far.keys.previous.expires` | *(none)* | ISO-8601 expiry for retired key |
| `far.client.signature.strict` | `true` | Reject unsigned peer responses |

## Drivers

Drivers are loaded via Java SPI (`ServiceLoader`). Each driver implements:

```java
public interface Driver {
    String name();
    Set<String> namespaces();
    Optional<Resolution> resolve(Urn urn, String token);
    Page query(Query query, String token);
    boolean exists(Urn urn);
    Optional<History> history(Urn urn, String token);
    Optional<Rendition> rendition(Urn urn, String media, String token);
}
```

**Built-in drivers:**
- `far-driver-stub` — in-memory stub for testing
- `far-driver-http` — HTTP proxy driver for out-of-process registry plugins

The [federated-attribute-repository](../federated-attribute-repository) project implements `RegistryDriver` which bridges a full certificate registry to the resolver's driver interface.

## Related Projects

- **[federated-attribute-repository](../federated-attribute-repository)** — the write-side certificate registry
- **[far/protocol](../far/protocol)** — the FAR protocol specification and OpenAPI schema
