# ADR-008: Pre-Formatted Renditions via Driver SPI

## Status

Accepted

## Context

The Federated Attribute Resolver currently resolves URNs to structured attribute data (JSON, XML, CSV). However, many
upstream registries also provide pre-formatted views of certificate data — HTML certificate pages, PDF documents, badge
images, and other visual representations. Consumers such as auditors, regulatory bodies, and compliance dashboards
frequently need these formatted views alongside or instead of raw attribute data.

Candidate approaches:

- **Server-side rendering.** FAR renders attribute data into HTML/PDF/PNG using templates or rendering libraries. This
  couples FAR to presentation logic, requires maintaining templates per namespace, and cannot reproduce the
  authoritative
  formatting of the upstream registry.
- **Separate rendering service.** A dedicated service consumes FAR's structured data and produces formatted views. This
  adds operational complexity and still cannot reproduce upstream formatting.
- **Driver-fetched renditions.** The driver fetches pre-formatted content directly from the upstream registry, just as
  it
  fetches attribute data. FAR passes the bytes through to the client without interpretation.

## Decision

We will extend the **Driver SPI** with an optional `rendition(Urn, String media)` method that fetches pre-formatted
content from the upstream registry. FAR acts as a pass-through — it does not render, transform, or interpret the
content.

### Driver SPI Extension

```java
default Optional<Rendition> rendition(final Urn urn, final String media) {
    return Optional.empty();
}
```

The method returns `Optional.empty()` by default, so existing drivers are unaffected. Drivers that support renditions
override this method to fetch content from their upstream registry for the requested media type.

### Rendition Model

```java
public record Rendition(byte[] content, String media, Urn urn, Instant timestamp)
```

The `Rendition` record holds the raw bytes fetched from the upstream registry, the media type (e.g. `text/html`,
`application/pdf`, `image/png`), the URN that was rendered, and a timestamp.

### Content Negotiation

Renditions are served via the same `GET /v1/resources/{urn}` endpoint using standard HTTP content negotiation. When the
client sends an `Accept` header requesting `text/html`, `application/pdf`, or `image/png`, JAX-RS routes to the
rendition method. When the client requests `application/json` (or omits the header), the existing attribute resolution
path is used.

### Supported Rendition Media Types

| Media Type        | Use Case                                      |
|-------------------|-----------------------------------------------|
| `text/html`       | Certificate detail pages, human-readable view |
| `application/pdf` | Official certificate documents, audit records |
| `image/png`       | Certificate badges, visual summaries          |

### Error Handling

If a driver does not support the requested media type, or the upstream registry does not provide a rendition for the
given URN, the server returns `406 Not Acceptable` with error code `unsupported_format`.

### Example

Request:

```http
GET /v1/resources/urn:far:verra:981:2023 HTTP/1.1
Accept: application/pdf
```

Response:

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Far-Namespace: verra

%PDF-1.4 ...
```

## Consequences

### Positive

- **Authentic formatting.** Renditions come directly from the upstream registry, preserving the authoritative
  presentation of certificate data — branding, layout, legal text, and all.
- **Zero rendering logic in FAR.** FAR acts purely as a pass-through. No templates, rendering libraries, or
  presentation logic to maintain.
- **Backward-compatible.** The `rendition` method has a default implementation returning `Optional.empty()`. Existing
  drivers compile and function without changes.
- **Standard content negotiation.** Uses the same `Accept` header mechanism as ADR-006. Clients already know how to
  request different media types.
- **Driver-controlled.** Each driver decides which rendition formats it supports based on what the upstream registry
  offers. No assumptions about universal format availability.

### Negative

- **Upstream dependency.** Rendition availability depends entirely on the upstream registry. If the registry does not
  provide a PDF or HTML view, the driver cannot fabricate one.
- **No customisation.** Since FAR passes bytes through without interpretation, there is no opportunity to add FAR
  branding, inject verification metadata, or normalise formatting across namespaces.
- **Caching complexity.** Renditions are typically larger than attribute data (especially PDFs and images) and may
  change
  independently of attribute values. Caching strategies must account for this.
- **Signature scope.** RFC 9421 signatures cover the rendition bytes. Different renditions of the same URN produce
  different signatures, consistent with the behaviour described in ADR-006.
