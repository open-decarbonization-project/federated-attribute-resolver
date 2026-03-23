# Sequence Diagrams

**Status:** Draft
**Version:** 0.1.0
**Date:** 2026-02-22

## Overview

This document contains Mermaid sequence diagrams illustrating the key interaction patterns in the Federated Attribute
Resolver (FAR) protocol. Each diagram shows the message flow between participants for a specific scenario.

---

## 1. Direct Resolution

A client resolves a URN for which the FAR server has a local driver. The request is handled entirely within a single
server without delegation.

```mermaid
sequenceDiagram
    participant Client
    participant FAR as FAR Server
    participant Router
    participant Driver
    participant Registry as External Registry
    Client ->> FAR: GET /v1/resources/urn:far:verra:981:2023
    Note over Client, FAR: Headers: Accept, Authorization: Bearer JWS,<br/>Signature-Input, Signature
    FAR ->> FAR: Parse URN, extract namespace "verra"
    FAR ->> Router: route(namespace="verra")
    Router ->> Router: Look up local drivers
    Router -->> FAR: VerraDriver (local)
    FAR ->> Driver: resolve(identifier="981:2023", attributes=[*])
    Driver ->> Registry: HTTPS request to Verra API
    Note over Driver, Registry: Fetch project 981, vintage 2023
    Registry -->> Driver: Raw registry data (JSON/XML)
    Driver ->> Driver: Transform to FAR attribute schema
    Driver -->> FAR: Resolution {project_name, volume, vintage, ...}
    FAR ->> FAR: Compute Content-Digest (SHA-256)
    FAR ->> FAR: Sign response (Ed25519, RFC 9421)
    FAR -->> Client: 200 OK
    Note over FAR, Client: Headers: Content-Type, Content-Digest,<br/>Far-Resolver, Far-Namespace,<br/>Signature-Input, Signature<br/><br/>Body: {urn, attributes, signature}
```

---

## 2. Delegated Resolution

A client sends a request to FAR-A, which does not have a local driver for the namespace. FAR-A delegates to FAR-B, which
has the appropriate driver.

```mermaid
sequenceDiagram
    participant Client
    participant FARA as FAR-A<br/>(no REC driver)
    participant FARB as FAR-B<br/>(REC driver)
    participant Driver as REC Driver
    participant Registry as WREGIS
    Client ->> FARA: GET /v1/resources/urn:far:rec:wregis:12345678
    Note over Client, FARA: Authorization: Bearer JWS
    FARA ->> FARA: Parse URN, namespace = "rec"
    FARA ->> FARA: No local driver for "rec"
    FARA ->> FARA: Check delegation: allowed
    FARA ->> FARA: Check loop: no Far-Delegation-Chain, OK
    FARA ->> FARA: Look up routing table for "rec"
    Note over FARA: Peer: https://rec-resolver.example.com (FAR-B)
    FARA ->> FARB: GET /v1/resources/urn:far:rec:wregis:12345678
    Note over FARA, FARB: Far-Delegation-Chain: https://far-a.example.com<br/>Authorization: Bearer JWS<br/>Signature-Input, Signature (FAR-A's key)
    FARB ->> FARB: Parse URN, namespace = "rec"
    FARB ->> FARB: Verify FAR-A's request signature
    FARB ->> FARB: Check loop: FAR-B not in chain, OK
    FARB ->> FARB: Local driver found for "rec"
    FARB ->> Driver: resolve(identifier="wregis:12345678")
    Driver ->> Registry: HTTPS request to WREGIS API
    Registry -->> Driver: Certificate data
    Driver ->> Driver: Transform to FAR attribute schema
    Driver -->> FARB: Resolution {technology, state, volume, ...}
    FARB ->> FARB: Sign response (FAR-B's key)
    FARB -->> FARA: 200 OK
    Note over FARB, FARA: Far-Resolver: https://rec-resolver.example.com<br/>Signature (FAR-B's key)
    FARA ->> FARA: Verify FAR-B's response signature
    FARA ->> FARA: Add delegation metadata
    FARA ->> FARA: Re-sign response (FAR-A's key)
    FARA -->> Client: 200 OK
    Note over FARA, Client: Body includes:<br/>delegated: true<br/>delegation.chain: [FAR-A, FAR-B]<br/>Outer signature: FAR-A<br/>Inner signature: FAR-B (in body)
```

---

## 3. Identity Propagation and Partial Data

A request carries the end-user's identity through the delegation chain. The resolving server applies authorization rules
and masks fields the requester is not permitted to see.

```mermaid
sequenceDiagram
    participant Client as Client<br/>(Limited Access)
    participant FARA as FAR-A
    participant FARB as FAR-B<br/>(Authoritative)
    participant Driver
    participant Registry
    Client ->> FARA: GET /v1/resources/urn:far:miq:A:7f3a2b1c:2024Q1
    Note over Client, FARA: Authorization: Bearer JWS<br/>Far-Attributes: grade,facility,period,volume
    FARA ->> FARA: No local driver for "miq"
    FARA ->> FARA: Route to FAR-B for "miq"
    FARA ->> FARB: GET /v1/resources/urn:far:miq:A:7f3a2b1c:2024Q1
    Note over FARA, FARB: Authorization: Bearer JWS<br/>(original bearer token forwarded unchanged)<br/>Far-Delegation-Chain: https://far-a.example.com<br/>Far-Attributes: grade,facility,period,volume
    FARB ->> FARB: Verify FAR-A's signature
    FARB ->> FARB: Extract requester identity
    FARB ->> FARB: Authorization check:<br/>limited-client.example.org<br/>may access: grade, period, volume<br/>may NOT access: facility (confidential)
    FARB ->> Driver: resolve(identifier="A:7f3a2b1c:2024Q1")
    Driver ->> Registry: Fetch MiQ certificate
    Registry -->> Driver: Full certificate data
    Driver -->> FARB: Resolution (all fields)
    FARB ->> FARB: Apply field masking per authorization
    Note over FARB: facility field masked:<br/>value replaced with null,<br/>masked: true flag set
    FARB ->> FARB: Sign response (covers masked result)
    FARB -->> FARA: 200 OK
    Note over FARB, FARA: Signed response with masked fields
    FARA ->> FARA: Verify signature, re-sign
    FARA -->> Client: 200 OK
    Note over Client: Response body:
    Note over Client: grade: "A"<br/>facility: null (masked: true)<br/>period: "2024Q1"<br/>volume: {value: 84200, unit: "MMBtu"}
```

---

## 4. Certificate History

A client requests the audit history of a certificate. The history endpoint retrieves the chronological record of
events (issuance, transfers, retirements) from the underlying registry.

```mermaid
sequenceDiagram
    participant Client
    participant FAR as FAR Server
    participant Router
    participant Driver as Verra Driver
    participant Registry as Verra Registry
    Client ->> FAR: GET /v1/resources/urn:far:verra:981:2023/history?type=all&limit=50
    Note over Client, FAR: Authorization: Bearer JWS
    FAR ->> FAR: Parse URN, extract namespace "verra"
    FAR ->> FAR: Identify history request
    FAR ->> Router: route(namespace="verra")
    Router -->> FAR: VerraDriver (local)
    FAR ->> Driver: history(identifier="981:2023", type=ALL, limit=50)
    Driver ->> Registry: GET /api/projects/981/history
    Note over Driver, Registry: Fetch audit trail for project 981
    Registry -->> Driver: Raw history records
    Driver ->> Driver: Transform each event to FAR history schema
    Driver ->> Driver: Filter by vintage 2023
    Driver ->> Driver: Sort chronologically
    Driver -->> FAR: HistoryResult [issuance, transfer, retirement, ...]
    FAR ->> FAR: Build paginated response
    FAR ->> FAR: Compute Content-Digest
    FAR ->> FAR: Sign response
    FAR -->> Client: 200 OK
    Note over FAR, Client: Body: {<br/> urn, namespace, identifier,<br/> history: [<br/> {timestamp, type: "issuance", volume, actor},<br/> {timestamp, type: "transfer", volume, actor},<br/> {timestamp, type: "retirement", volume, actor, beneficiary}<br/> ],<br/> total: 3, limit: 50, offset: 0<br/>}
```

---

## 5. Peer Discovery

At startup, a FAR server probes all configured peers to build its routing table. It fetches each peer's
`/v1/peers/configuration`, extracts namespace advertisements and public keys, and constructs the namespace-to-peer
routing map.

```mermaid
sequenceDiagram
    participant FAR as FAR Server<br/>(starting up)
    participant PeerA as Peer A<br/>(rec-resolver.example.com)
    participant PeerB as Peer B<br/>(ngc.contextlabs.com)
    participant PeerC as Peer C<br/>(carbon-far.example.org)
    Note over FAR: Server startup initiated
    FAR ->> FAR: Load static peer configuration
    Note over FAR: Peers: A (rec), B (ngc, miq), C (carbon, verra)

    par Probe all peers in parallel
        FAR ->> PeerA: GET /v1/peers/configuration
        FAR ->> PeerB: GET /v1/peers/configuration
        FAR ->> PeerC: GET /v1/peers/configuration
    end

    PeerA -->> FAR: 200 OK
    Note over PeerA, FAR: identity: https://rec-resolver.example.com<br/>namespaces: ["rec"]<br/>public_key: {key_id: "...", pem: "..."}
    PeerB -->> FAR: 200 OK
    Note over PeerB, FAR: identity: https://ngc.contextlabs.com/far<br/>namespaces: ["ngc", "miq"]<br/>public_key: {key_id: "...", pem: "..."}
    PeerC -->> FAR: Connection timeout
    Note over FAR, PeerC: Peer C unreachable
    FAR ->> FAR: Process Peer A response
    Note over FAR: Cache public key for Peer A<br/>Namespaces confirmed: ["rec"]<br/>Record latency: 42ms
    FAR ->> FAR: Process Peer B response
    Note over FAR: Cache public key for Peer B<br/>Namespaces confirmed: ["ngc", "miq"]<br/>Record latency: 88ms
    FAR ->> FAR: Handle Peer C failure
    Note over FAR: Mark Peer C as "unverified"<br/>Retain static config: ["carbon", "verra"]<br/>Schedule retry with backoff (10s)
    FAR ->> FAR: Build routing table
    Note over FAR: Routing table:<br/>rec -> Peer A (verified, 42ms)<br/>ngc -> Peer B (verified, 88ms)<br/>miq -> Peer B (verified, 88ms)<br/>carbon -> Peer C (unverified)<br/>verra -> local + Peer C (unverified)
    FAR ->> FAR: Start periodic probe timer (5 min)
    Note over FAR: Server ready to accept requests
    Note over FAR: ... 10 seconds later (backoff retry) ...
    FAR ->> PeerC: GET /v1/peers/configuration
    PeerC -->> FAR: 200 OK
    Note over PeerC, FAR: identity: https://carbon-far.example.org<br/>namespaces: ["carbon", "verra"]<br/>public_key: {key_id: "...", pem: "..."}
    FAR ->> FAR: Update routing table
    Note over FAR: carbon -> Peer C (verified, 120ms)<br/>verra -> local + Peer C (verified, 120ms)
```
