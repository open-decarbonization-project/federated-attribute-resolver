# ADR-005: Namespace-Based Delegation with Loop Detection

## Status

Accepted

## Context

No single FAR server can be authoritative for every certificate namespace. The federated nature of the system means that
certificate data is distributed across multiple servers, each responsible for different registries. When a FAR server
receives a resolution request for a namespace it does not own, it must forward (delegate) that request to a peer server
that is authoritative.

This delegation mechanism must address several concerns:

1. **Routing.** How does a server determine which peer to delegate to for a given namespace?
2. **Loop detection.** In a mesh topology, delegation chains can form cycles (A delegates to B, B delegates to C, C
   delegates back to A). Without loop detection, these cycles cause infinite request loops.
3. **Depth limiting.** Even without loops, excessively long delegation chains increase latency and reduce reliability.
   There must be a configurable maximum depth.
4. **Transparency.** The original requester and intermediate servers should be able to inspect the delegation path for
   debugging and audit purposes.
5. **Resilience.** If a peer is unavailable, the delegating server should handle the failure gracefully rather than
   hanging or crashing.

Candidate approaches for loop detection:

- **TTL (Time-to-Live) counter**: simple but does not reveal the actual path; only limits depth.
- **Visited-set header**: each server appends its identity to a header; recipients check for their own identity before
  processing. Reveals the full path.
- **Hybrid**: visited-set header (for loop detection and transparency) combined with a configurable maximum depth (for
  safety).

## Decision

We will implement **namespace-based delegation with loop detection via the `Far-Delegation-Chain` header and a
configurable maximum delegation depth**.

### Namespace Routing

Each FAR server maintains a delegation table mapping namespaces to peer server endpoints:

```yaml
delegation:
  peers:
    - url: https://far-eu.example.org
      namespaces: [ verra, carbon ]
    - url: https://far-us.example.org
      namespaces: [ rec, ngc ]
    - url: https://far-miq.example.org
      namespaces: [ miq ]
  depth: 5
```

When the resolution engine encounters a namespace it does not own and has no local driver for, it looks up the
delegation table. If a peer is configured for that namespace, the request is forwarded.

### Loop Detection — `Far-Delegation-Chain` Header

Each FAR server that handles a delegation request appends its own server identifier to the `Far-Delegation-Chain` header
before forwarding:

```http
Far-Delegation-Chain: far-eu.example.org, far-us.example.org
```

Before processing an incoming request, a server checks whether its own identifier already appears in the chain. If it
does, the request is rejected with `508 Loop Detected`.

### Depth Limiting

The number of entries in `Far-Delegation-Chain` is checked against the configurable `depth` limit. If the chain length
exceeds the limit, the request is rejected with `508 Loop Detected` and a descriptive error body.

### Delegation Request Flow

1. Server receives resolution request for `urn:far:miq:A7X`.
2. Resolution engine determines `miq` is not a local namespace.
3. Delegation layer looks up `miq` in the delegation table and finds `https://far-miq.example.org`.
4. Delegation layer checks the incoming `Far-Delegation-Chain` (if any) for loop and depth violations.
5. Delegation layer appends its own identity to `Far-Delegation-Chain`.
6. Delegation layer forwards the request to the peer, including `Far-Delegation-Chain`, `Authorization: Bearer <token>`,
   and the RFC 9421 signature (ADR-003).
7. The peer resolves the request (locally or via further delegation) and returns a signed response.
8. The delegating server returns the response to the original caller.

## Consequences

### Positive

- **Mesh topology support.** Any FAR server can delegate to any other, enabling flexible deployment topologies without a
  central coordinator.
- **Loop safety.** The `Far-Delegation-Chain` header provides deterministic loop detection. A server will never process
  a request that has already passed through it.
- **Configurable depth.** Operators can tune the maximum delegation depth based on their deployment topology. A small
  private mesh might use depth 2; a global mesh might allow depth 5 or more.
- **Audit trail.** The `Far-Delegation-Chain` header provides a complete record of every server that participated in
  resolving a request, supporting debugging and regulatory audit.
- **Graceful degradation.** If a peer is unreachable, the delegating server returns an appropriate error (502 or 504) to
  the caller rather than silently failing.

### Negative

- **Configuration burden.** Each FAR server must maintain a delegation table mapping namespaces to peers. In a large
  mesh, keeping these tables consistent requires operational tooling or a discovery mechanism.
- **Latency accumulation.** Each delegation hop adds network latency. A depth-5 delegation chain means five sequential
  HTTP round trips before the original caller receives a response.
- **Header size.** In deep delegation chains, the `Far-Delegation-Chain` header can grow large. HTTP header size
  limits (typically 8KB) impose a practical upper bound on chain depth.
- **Partial failure complexity.** When a delegation chain fails at an intermediate node, the error must propagate back
  through all preceding nodes. Error attribution (which node failed?) requires careful error response design.
