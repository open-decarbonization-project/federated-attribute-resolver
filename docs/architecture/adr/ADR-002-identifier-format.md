# ADR-002: URN Identifier Format per RFC 8141

## Status

Accepted

## Context

The Federated Attribute Resolver must identify certificates across multiple, heterogeneous registries using a single,
universal identifier format. Each registry has its own native identifier scheme:

- Verra uses numeric project IDs (e.g., `VCS-1234`).
- MiQ uses alphanumeric certificate identifiers.
- REC registries use tracking system IDs that vary by region.
- Context Labs NGC uses its own certification identifiers.
- Carbon credit registries use scheme-specific serial numbers.

FAR needs an identifier format that:

1. Is globally unique across all registries.
2. Encodes which registry (namespace) is authoritative — enabling namespace-based routing (a core design principle).
3. Is standards-compliant and well-understood.
4. Is extensible to accommodate new registries without format changes.
5. Is compatible with emerging decentralized identity patterns (DID resolution).

Candidate formats considered:

- **Custom format** (e.g., `far://miq/cert-123`): simple but non-standard, no RFC backing, potential parsing ambiguity.
- **DID format** (e.g., `did:far:miq:cert-123`): aligned with W3C DID spec, but DID resolution has heavier
  infrastructure requirements and FAR is not a full DID system.
- **URN format** (e.g., `urn:far:miq:cert-123`): RFC 8141 compliant, well-established, used in IETF protocols, naturally
  supports hierarchical namespaces.

## Decision

We will use **URN (Uniform Resource Name) format per RFC 8141** with the following structure:

```
urn:far:{namespace}:{identifier}
```

Where:

- `urn` is the URI scheme (fixed).
- `far` is the Namespace Identifier (NID), identifying this as a FAR URN.
- `{namespace}` identifies the authoritative registry (e.g., `verra`, `miq`, `rec`, `ngc`, `carbon`).
- `{identifier}` is the registry-native certificate identifier, opaque to FAR's routing layer.

Examples:

| Certificate              | URN                      |
|--------------------------|--------------------------|
| Verra project 1234       | `urn:far:verra:VCS-1234` |
| MiQ certificate A7X      | `urn:far:miq:A7X`        |
| REC tracking ID 98765    | `urn:far:rec:98765`      |
| NGC certification C-001  | `urn:far:ngc:C-001`      |
| Carbon credit serial ABC | `urn:far:carbon:ABC`     |

The namespace component is the routing key. The resolution engine extracts the namespace, looks up the authoritative
server or driver, and dispatches accordingly.

## Consequences

### Positive

- **Standards-compliant.** RFC 8141 is a mature IETF standard. URNs are well-understood by systems integrators and
  standards bodies, which matters for EU regulatory adoption.
- **Namespace-based routing.** The `{namespace}` component maps directly to FAR's core routing mechanism. Parsing a URN
  to extract the routing key is trivial and unambiguous.
- **Extensible.** Adding a new registry requires only defining a new namespace string. No format changes, no schema
  migrations.
- **DID-compatible.** The URN structure is conceptually compatible with DID resolution patterns. If FAR later needs to
  bridge to a DID ecosystem, the mapping is straightforward (`did:far:miq:A7X` mirrors `urn:far:miq:A7X`).
- **Persistent and location-independent.** URNs by definition identify resources independent of their location or access
  mechanism, which aligns with FAR's delegation-over-replication principle.

### Negative

- **NID registration.** Formally registering `far` as a URN Namespace Identifier with IANA requires an RFC or IANA
  registration process. In the interim, FAR operates as an informal NID.
- **Identifier opacity.** The `{identifier}` portion is opaque to FAR, meaning FAR cannot validate whether a given
  identifier is well-formed for its namespace without invoking the driver. Validation is deferred to the registry.
- **No built-in versioning.** The URN format does not inherently support versioned references to a certificate. If
  version-specific resolution is needed, it must be handled via query parameters or a separate mechanism.
