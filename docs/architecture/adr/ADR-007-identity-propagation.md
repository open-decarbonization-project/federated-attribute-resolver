# ADR-007: JWS Bearer Tokens for Requester Identity

## Status

Accepted

## Context

When a resolution request traverses multiple FAR servers via delegation (ADR-005), the downstream server or leaf-node
registry may need to make authorization decisions based on the identity of the **original requester**, not the
intermediate server that is proxying the request.

Consider this scenario:

1. An EU auditor sends a resolution request to FAR server A.
2. FAR server A delegates to FAR server B (which owns the namespace).
3. FAR server B invokes a registry driver that calls the MiQ API.
4. MiQ's API enforces access control: only authorized regulatory bodies may query certificate details.

In this chain, MiQ needs to know that the original requester is an EU auditor — not FAR server A or FAR server B.

We considered two approaches:

- **Custom `Far-Requester` header** with signed identity tokens — functional but non-standard, mixes requester identity
  with the `Far-*` headers used for peer federation.
- **OAuth 1.0a-style request signing** — strong guarantees but too complex for this use case; every request must be
  signed, requiring key management at the client level.

Requirements:

1. **End-to-end identity.** The original requester's identity must be available to every server and registry in the
   delegation chain.
2. **Standard format.** Use an established token format rather than inventing a custom header.
3. **Separation of concerns.** Keep `Far-*` headers exclusively for peer federation (delegation chain, resolver
   identity, namespace routing).

## Decision

We will use **JWS (JSON Web Signature, RFC 7515) compact tokens** carried in the standard
`Authorization: Bearer <token>` header for requester identity.

### Token Format

The JWS token uses compact serialization with the following structure:

**Header:**

```json
{
  "alg": "EdDSA",
  "kid": "auditor.eu.example.org#key-1"
}
```

**Payload (claims):**

| Claim | Description                                                  |
|-------|--------------------------------------------------------------|
| `sub` | Subject identifier — the requester's identity URI.           |
| `iss` | Issuer — typically the same as `sub` for self-issued tokens. |
| `iat` | Issued-at timestamp (Unix epoch seconds).                    |
| `exp` | Expiration timestamp. Recommended lifetime: 5 minutes.       |
| `aud` | Audience — the target FAR server's identity URI (optional).  |

**Example request:**

```http
GET /v1/resources/urn:far:miq:A:7f3a2b1c:2024Q1 HTTP/1.1
Host: far-a.example.org
Accept: application/json
Authorization: Bearer eyJhbGciOiJFZERTQSIsImtpZCI6ImF1ZGl0b3IuZXUuZXhhbXBsZS5vcmcja2V5LTEifQ.eyJzdWIiOiJhdWRpdG9yLmV1LmV4YW1wbGUub3JnIiwiaXNzIjoiYXVkaXRvci5ldS5leGFtcGxlLm9yZyIsImlhdCI6MTcwNDA2NzIwMCwiZXhwIjoxNzA0MDY3NTAwfQ.SIGNATURE
Far-Delegation-Chain: https://far-a.example.org
```

### Propagation Through Delegation

When a FAR server delegates a request to a peer:

1. The original `Authorization: Bearer <token>` header is **forwarded unchanged** to the downstream peer.
2. The downstream peer verifies the JWS token by fetching the requester's public key (identified by the `kid` header
   parameter).
3. The `Far-Delegation-Chain` header (ADR-005) independently tracks which servers have handled the request.

This cleanly separates concerns:

- `Authorization` — who is making the request (requester identity)
- `Far-Delegation-Chain` — which servers have processed the request (federation routing)

### Verification

Any server in the chain verifies the bearer token by:

1. Decoding the JWS compact serialization.
2. Extracting the `kid` from the JOSE header.
3. Retrieving the requester's public key (from a key registry or well-known endpoint).
4. Verifying the EdDSA signature.
5. Checking `exp` has not passed (with clock skew tolerance).
6. Optionally checking `aud` matches the server's identity.

### Header Taxonomy

After this decision, the header responsibilities are:

| Header                          | Scope           | Purpose                                           |
|---------------------------------|-----------------|---------------------------------------------------|
| `Authorization: Bearer <JWS>`   | Requester       | Carries requester identity as a signed JWS token  |
| `Far-Delegation-Chain`          | Peer federation | Tracks server delegation chain for loop detection |
| `Far-Resolver`                  | Peer federation | Identifies the server that produced the response  |
| `Far-Namespace`                 | Peer federation | Identifies the resolved namespace                 |
| `Signature-Input` / `Signature` | Peer federation | RFC 9421 server-to-server message signatures      |

## Consequences

### Positive

- **Standard format.** JWS (RFC 7515) is widely supported with mature libraries in every language. No custom token
  format to document or implement.
- **Clean separation.** `Far-*` headers are exclusively for peer federation. Requester identity uses the standard
  `Authorization` header.
- **Simple propagation.** The bearer token is forwarded as-is through the delegation chain — no chain construction or
  extension required.
- **Stateless verification.** Each server can independently verify the token without contacting the issuer at request
  time (given cached public keys).
- **Familiar pattern.** Developers already understand `Authorization: Bearer` from OAuth 2.0 and JWT-based systems.

### Negative

- **Key distribution.** Verifying tokens requires access to the requester's public key. A key registry or discovery
  mechanism is needed for non-peer requesters.
- **Token expiry management.** Short-lived tokens (5 minutes) require clients to refresh tokens, adding complexity for
  long-running batch operations.
- **No chain attestation.** Unlike a signed chain, this approach does not cryptographically prove which servers handled
  the request — that responsibility falls to `Far-Delegation-Chain` and the RFC 9421 server signatures.
