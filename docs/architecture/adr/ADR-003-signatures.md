# ADR-003: RFC 9421 HTTP Message Signatures with Ed25519

## Status

Accepted

## Context

FAR servers communicate with each other (delegation) and with external consumers (resolution requests) over HTTPS. While
TLS provides transport-level encryption and server authentication, it does not provide:

1. **Message-level integrity.** TLS terminates at load balancers and reverse proxies. Once decrypted, the message
   content can be modified by intermediaries. FAR needs end-to-end integrity guarantees for resolution responses.
2. **Server-to-server authentication.** When FAR server A delegates to FAR server B, server B must verify that the
   request genuinely originated from server A (not just from any client with a valid TLS connection). This is server
   identity at the application layer.
3. **Non-repudiation.** Auditors and regulatory bodies may need to verify that a specific FAR server produced a specific
   resolution response. Signed messages provide this guarantee.
4. **Request authenticity.** Downstream registries that enforce authorization policies need to verify the identity of
   the requesting FAR server, not just the TLS client certificate.

The two leading approaches are:

- **JWT/JWS-based signatures**: widely used, but designed for token-based authentication rather than HTTP message
  signing. Signing arbitrary HTTP messages with JWS requires non-standard envelope formats.
- **RFC 9421 HTTP Message Signatures**: an IETF standard specifically designed for signing HTTP messages (requests and
  responses). Supports signing specific headers and the request body. Uses standard HTTP headers (`Signature` and
  `Signature-Input`).

For the signing algorithm, the candidates are:

- **RSA (RS256/RS512)**: widely supported but slower, larger keys, and larger signatures.
- **ECDSA (ES256)**: good performance, standard curves, but complex implementation with nonce requirements.
- **Ed25519**: fast, compact 32-byte keys and 64-byte signatures, deterministic (no nonce), resistant to side-channel
  attacks, increasingly adopted (SSH, TLS 1.3, DNSSEC).

## Decision

We will use **RFC 9421 HTTP Message Signatures** with **Ed25519** as the signing algorithm.

Specifically:

- All inter-server (delegation) requests and responses are signed using RFC 9421.
- Resolution responses returned to external consumers are signed.
- The signature covers at minimum: the HTTP method, request target, `Content-Type` header, `Content-Digest` header (body
  hash), the `Authorization` header, and any FAR-specific headers (`Far-Delegation-Chain`).
- Each FAR server maintains an Ed25519 key pair. The public key is published at the peer configuration endpoint (
  `/v1/peers/configuration`) for peer verification.
- Key identifiers follow the format `{server-id}#{key-id}` to support multiple keys and rotation.

Signature headers example:

```http
Signature-Input: sig1=("@method" "@target-uri" "content-type" "content-digest" "far-delegation-chain");created=1704067200;keyid="far.example.org#key-2024"
Signature: sig1=:BASE64_ED25519_SIGNATURE:
Content-Digest: sha-256=:BASE64_SHA256_HASH:
```

## Consequences

### Positive

- **Standards-compliant.** RFC 9421 is the IETF standard for HTTP message signatures, purpose-built for this use case.
  It avoids the impedance mismatch of repurposing JWS for HTTP message signing.
- **End-to-end integrity.** Signatures survive TLS termination, load balancers, and reverse proxies. A consumer can
  verify that a resolution response was not tampered with after leaving the originating FAR server.
- **Strong cryptography.** Ed25519 provides 128-bit security with 32-byte public keys and 64-byte signatures. It is
  deterministic (no nonce-related vulnerabilities), fast to sign and verify, and resistant to timing attacks.
- **Key rotation support.** The `keyid` parameter in `Signature-Input` allows servers to reference specific keys,
  enabling graceful rotation where old and new keys coexist during a transition period.
- **Non-repudiation.** Signed responses provide cryptographic proof that a specific server produced a specific response,
  supporting audit and compliance requirements.
- **Selective signing.** RFC 9421 allows signing specific components of the HTTP message, avoiding the need to
  canonicalize the entire message.

### Negative

- **Implementation complexity.** RFC 9421 is newer and has fewer off-the-shelf library implementations compared to
  JWT/JWS. The signing and verification logic may require custom implementation or use of early-stage libraries.
- **Key distribution.** Each FAR server must publish its public key and peers must discover and cache these keys. The
  `/v1/peers/configuration` endpoint must be reliably available.
- **Performance overhead.** Every request and response requires signature computation and verification. Ed25519 is
  fast (tens of microseconds), but in high-throughput scenarios the cumulative cost is non-zero.
- **Clock sensitivity.** The `created` parameter in `Signature-Input` depends on reasonably synchronized clocks across
  FAR servers. Clock skew beyond a configurable tolerance will cause signature validation failures.
