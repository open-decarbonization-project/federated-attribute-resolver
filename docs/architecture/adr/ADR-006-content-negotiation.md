# ADR-006: Accept Header Based Multi-Format Support

## Status

Accepted

## Context

The Federated Attribute Resolver serves a diverse set of consumers:

- **EU regulatory systems** may expect XML (common in government data exchange, e.g., XBRL, UN/CEFACT).
- **Modern web applications and APIs** expect JSON.
- **Data analysts and bulk processing pipelines** may prefer CSV or tabular formats.
- **Future consumers** may need formats not yet anticipated (e.g., Protocol Buffers, JSON-LD).

FAR's resolution engine produces an internal attribute model that is format-independent. The question is how to expose
this model in multiple serialization formats without coupling the core to any specific format.

Candidate approaches:

- **Fixed format (JSON only)**: simplest but excludes consumers that need XML or CSV. Adding formats later requires API
  versioning or separate endpoints.
- **Format parameter in URL** (e.g., `/resolve?format=xml`): works but is non-standard and requires custom parsing.
- **File extension in URL** (e.g., `/resolve/urn:far:miq:A7X.xml`): conflicts with the URN structure and is not RESTful.
- **HTTP Accept header**: the standard HTTP mechanism for content negotiation (RFC 7231, Section 5.3.2). The client
  specifies preferred formats; the server selects the best match.

## Decision

We will use **standard HTTP content negotiation via the `Accept` header** to support multiple response formats.

### Supported Formats (Initial)

| Media Type         | Format | Use Case                                        |
|--------------------|--------|-------------------------------------------------|
| `application/json` | JSON   | Default. Web applications, modern APIs.         |
| `application/xml`  | XML    | EU regulatory systems, XBRL-adjacent workflows. |
| `text/csv`         | CSV    | Bulk data export, analytics pipelines.          |

### Negotiation Rules

1. The client sends an `Accept` header with one or more media types, optionally with quality factors:
   ```http
   Accept: application/json, application/xml;q=0.9, text/csv;q=0.5
   ```
2. The server selects the highest-quality format it supports.
3. If no acceptable format is found, the server returns `406 Not Acceptable`.
4. If no `Accept` header is present, the server defaults to `application/json`.

### Implementation

- The resolution engine returns an internal `Resolution` object that is format-agnostic.
- JAX-RS `MessageBodyWriter` implementations handle serialization for each supported media type.
- Adding a new format requires implementing a new `MessageBodyWriter` and registering it — no changes to the resolution
  engine, drivers, or delegation layer.
- RESTEasy Reactive (ADR-001) provides built-in content negotiation support via `@Produces` annotations and the JAX-RS
  `Variant` mechanism.

### Example

Request:

```http
GET /resolve/urn:far:miq:A7X HTTP/1.1
Accept: application/xml
```

Response:

```http
HTTP/1.1 200 OK
Content-Type: application/xml
Signature-Input: sig1=(...)
Signature: sig1=:...:

<?xml version="1.0" encoding="UTF-8"?>
<resolution xmlns="urn:far:schema:resolution:1.0">
  <urn>urn:far:miq:A7X</urn>
  <attribute name="grade" value="A"/>
  <attribute name="vintage" value="2024"/>
  <attribute name="status" value="active"/>
</resolution>
```

### Rendition Media Types

In addition to the data serialization formats above, the same content negotiation mechanism is used for pre-formatted
renditions (`text/html`, `application/pdf`, `image/png`). These are fetched directly from the upstream registry by the
driver rather than serialized from the internal model. See [ADR-008](ADR-008-renditions.md) for details.

## Consequences

### Positive

- **Standards-compliant.** HTTP content negotiation is a well-established mechanism understood by all HTTP clients,
  proxies, and load balancers. No custom protocol needed.
- **Format-agnostic core.** The resolution engine, drivers, and delegation layer have zero knowledge of serialization
  formats. They operate entirely on the internal attribute model.
- **Extensible.** Adding a new format (e.g., JSON-LD, Protocol Buffers) requires only a new `MessageBodyWriter`.
  No changes to existing code.
- **Client-driven.** Each consumer selects the format that best suits its needs. A single API serves all consumer types.
- **Framework support.** RESTEasy Reactive and JAX-RS provide built-in content negotiation, minimizing custom code.

### Negative

- **Testing surface.** Every supported format must be tested for every response type. The number of test cases grows as
  `formats x response-types`.
- **Inconsistent fidelity.** Not all formats can represent the same data with equal fidelity. CSV cannot represent
  nested structures; XML requires schema decisions that JSON avoids. Format-specific edge cases may arise.
- **Accept header complexity.** Content negotiation with quality factors, wildcards (`*/*`), and vendor media types can
  become complex. The implementation must handle edge cases correctly.
- **Signature interaction.** The `Content-Digest` and RFC 9421 signature (ADR-003) depend on the serialized body. The
  same logical response signed in JSON and XML produces different signatures. Caching and signature verification must
  account for this.
