# ADR-009: Parametric Search with OData $filter

**Status:** Accepted
**Date:** 2026-03-05

## Context

FAR resolves individual URNs to attribute data but provides no mechanism to **discover** certificates matching a set of
criteria across registries. Users need to find certificates by attributes (e.g. certification level, volume thresholds,
producer name) without knowing specific URNs in advance.

## Decision

Add a parametric search feature using an OData-inspired `$filter` syntax on the existing `/v1/resources` collection
endpoint.

### Query Language: OData $filter Subset

We adopt a subset of the OData `$filter` query language:

- **Comparison operators:** `eq`, `ne`, `gt`, `ge`, `lt`, `le`
- **Set membership:** `in`
- **Text matching:** `contains()`
- **Composition:** `and`, `or` with parenthesized grouping
- **No NOT/negation** — the `ne` operator covers the common case

### Alternatives Considered

| Alternative    | Reason for rejection                                                          |
|----------------|-------------------------------------------------------------------------------|
| FIQL/RSQL      | Less readable, no broad tooling support, unfamiliar syntax                    |
| JSON body POST | Breaks REST convention for search (GET is cacheable), harder to share as URLs |
| GraphQL        | Heavyweight for this use case, requires separate schema, not REST-native      |
| Custom DSL     | No standards backing, documentation burden, learning curve                    |

OData was chosen because it is human-readable, standards-backed (OASIS), widely supported by tooling, and the subset we
need is small enough to implement as a simple recursive descent parser.

### Namespace in the Filter

The `namespace` field is a first-class filterable field **inside** `$filter` rather than a separate query parameter.
This means:

- `namespace eq 'ngg'` targets a single namespace
- `namespace in ('ngg','naesb')` targets multiple namespaces
- Omitting namespace fans out across all locally known namespaces and delegates to all peers

This design keeps the filter language self-contained and avoids a separate routing parameter.

### Federated Fan-Out Strategy

When a query targets multiple namespaces or omits namespace entirely:

1. Local drivers are queried in parallel for each target namespace
2. Remote namespaces are delegated to peers (reusing existing delegation infrastructure)
3. Results are merged, sorted by `$orderby`, and paginated with `$skip`/`$top`

The same loop detection and depth limiting from single-URN delegation applies to search delegation.

### Model Classes

Query-related model classes live in `net.far.resolver.model.query`:

- `Filter` — sealed interface with `Comparison`, `And`, `Or` variants
- `Operator` — enum of comparison operators
- `Query` — search request record (namespaces, filter, top, skip, orderby)
- `Page` — search result record (value, count, skip, top)
- `InvalidFilterException` — thrown on malformed filter expressions

### Driver SPI

A default `query(Query)` method on the `Driver` interface returns empty results. Drivers that support search override
this method. This maintains backward compatibility with existing drivers.

## Consequences

- Clients can discover certificates without knowing URNs in advance
- The OData subset is small enough that the parser is ~200 lines with no external dependencies
- Fan-out queries may be expensive across many namespaces; `$top` clamping (max 500) bounds this
- Drivers must opt in to search support; unsupported drivers return empty results gracefully
- The filter parser lives in the web layer (`Filters` utility), keeping model classes clean
