package net.far.resolver.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.far.resolver.core.Resolver;
import net.far.resolver.model.Attribute;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Urn;
import net.far.resolver.model.query.Query;
import net.far.resolver.spi.DriverRegistry;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/v1/resources")
public class ResolveResource {

  private static final String PREFIX = "Bearer ";
  private static final Set<String> RENDITION_TYPES =
      Set.of(MediaType.TEXT_HTML, "application/pdf", "image/png");

  @Inject Resolver resolver;

  @Inject DriverRegistry registry;

  @ConfigProperty(name = "far.identity", defaultValue = "https://far.example.com")
  String identity;

  private static String extractToken(final String authorization) {
    if (authorization != null && authorization.startsWith(PREFIX)) {
      return authorization.substring(PREFIX.length()).trim();
    }
    return null;
  }

  static List<String> delegation(final String chain) {
    if (chain == null || chain.isBlank()) {
      return List.of();
    }
    final var entries = new ArrayList<String>();
    for (final var entry : chain.split(",\\s*")) {
      if (entry.isBlank() || entry.contains("\r") || entry.contains("\n") || entry.length() > 256) {
        throw new BadRequestException("Malformed delegation chain");
      }
      entries.add(entry);
    }
    return entries;
  }

  static String media(final String accept) {
    if (accept == null || accept.isBlank()) {
      return null;
    }
    for (final var part : accept.split(",")) {
      final var type = part.split(";")[0].trim();
      if (RENDITION_TYPES.contains(type)) {
        return type;
      }
      if (MediaType.APPLICATION_JSON.equals(type) || "*/*".equals(type)) {
        return null;
      }
    }
    return null;
  }

  private static Resolution filter(final Resolution resolution, final String attributes) {
    final var names = Set.of(attributes.split(","));
    final var filtered = new LinkedHashMap<String, Attribute>();
    for (final var entry : resolution.attributes().entrySet()) {
      if (names.contains(entry.getKey())) {
        filtered.put(entry.getKey(), entry.getValue());
      }
    }
    return new Resolution(
        resolution.urn(),
        resolution.namespace(),
        resolution.identifier(),
        filtered,
        resolution.integrity(),
        resolution.resolver(),
        resolution.status(),
        resolution.timestamp(),
        resolution.delegated());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response query(
      @QueryParam("$filter") final String filter,
      @QueryParam("$top") @DefaultValue("25") final int top,
      @QueryParam("$skip") @DefaultValue("0") final int skip,
      @QueryParam("$orderby") final String orderby,
      @HeaderParam("Far-Delegation-Chain") final String chain,
      @HeaderParam("Authorization") final String authorization) {
    final var parsed = Filters.parse(filter);
    final var namespaces = Filters.namespaces(parsed);
    final var stripped = Filters.strip(parsed);
    final var query = new Query(namespaces, stripped, top, skip, orderby);
    final var delegation = delegation(chain);
    final var token = extractToken(authorization);
    final var page = resolver.query(query, delegation, token);
    return Response.ok(page).build();
  }

  @GET
  @Path("/{urn: urn:far:.+}")
  @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML, "application/pdf", "image/png"})
  public Response resolve(
      @PathParam("urn") final String raw,
      @HeaderParam("Far-Delegation-Chain") final String chain,
      @HeaderParam("Authorization") final String authorization,
      @HeaderParam("Accept") final String accept,
      @QueryParam("attribute") final String attribute,
      @QueryParam("delegate") @DefaultValue("allow") final String delegate,
      @QueryParam("resolve") @DefaultValue("full") final String resolve) {
    final var urn = Urn.parse(raw);
    final var token = extractToken(authorization);
    final var media = media(accept);
    if (media != null) {
      final var delegates = delegation(chain);
      final var rendition = resolver.rendition(urn, media, delegates, token);
      return Response.ok(rendition.content(), rendition.media())
          .header("Far-Resolver", identity)
          .header("Far-Namespace", urn.namespace())
          .build();
    }
    if ("deny".equals(delegate) && !registry.supported().contains(urn.namespace())) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(
              new ErrorResponse(
                  "namespace_not_found",
                  "Namespace not local and delegation denied: " + urn.namespace()))
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
    final var delegation = delegation(chain);
    var resolution = resolver.resolve(urn, delegation, token);
    if ("exists".equals(resolve)) {
      final var summary = new LinkedHashMap<String, Object>();
      summary.put("urn", resolution.urn().toString());
      summary.put("namespace", resolution.namespace());
      summary.put("identifier", resolution.identifier());
      summary.put("exists", true);
      summary.put("resolved_at", resolution.timestamp().toString());
      summary.put("resolver", resolution.resolver());
      return Response.ok(summary)
          .header("Far-Resolver", identity)
          .header("Far-Namespace", resolution.namespace())
          .build();
    }
    if (attribute != null && !attribute.isBlank()) {
      resolution = filter(resolution, attribute);
    }
    final var builder =
        Response.ok(resolution)
            .header("Far-Resolver", identity)
            .header("Far-Namespace", resolution.namespace());
    if (resolution.integrity() != null) {
      builder.header("Content-Digest", "sha-256=:" + resolution.integrity().digest() + ":");
    }
    return builder.build();
  }

  @GET
  @Path("/{urn: urn:far:.+}/history")
  @Produces(MediaType.APPLICATION_JSON)
  public Response history(
      @PathParam("urn") final String raw,
      @QueryParam("type") final String type,
      @QueryParam("limit") @DefaultValue("100") final int limit,
      @QueryParam("offset") @DefaultValue("0") final int offset,
      @QueryParam("from") final String from,
      @QueryParam("to") final String to,
      @HeaderParam("Far-Delegation-Chain") final String chain,
      @HeaderParam("Authorization") final String authorization) {
    final var urn = Urn.parse(raw);
    final var token = extractToken(authorization);
    final var delegates = delegation(chain);
    final var history = resolver.history(urn, delegates, token);
    var filtered = history.events();
    if (type != null && !type.isBlank()) {
      final var target = net.far.resolver.model.Event.EventType.parse(type);
      filtered = filtered.stream().filter(e -> e.type() == target).toList();
    }
    if (from != null && !from.isBlank()) {
      final var start = Instant.parse(from);
      filtered = filtered.stream().filter(e -> !e.timestamp().isBefore(start)).toList();
    }
    if (to != null && !to.isBlank()) {
      final var end = Instant.parse(to);
      filtered = filtered.stream().filter(e -> !e.timestamp().isAfter(end)).toList();
    }
    final var total = filtered.size();
    final var start = Math.min(offset, total);
    final var end = Math.min(start + limit, total);
    final var page = filtered.subList(start, end);
    final var result = new LinkedHashMap<String, Object>();
    result.put("urn", urn.toString());
    result.put("namespace", urn.namespace());
    result.put("identifier", urn.identifier());
    result.put("history", page);
    result.put("total", total);
    result.put("limit", limit);
    result.put("offset", offset);
    return Response.ok(result)
        .header("Far-Resolver", identity)
        .header("Far-Namespace", urn.namespace())
        .build();
  }

  @HEAD
  @Path("/{urn: urn:far:.+}")
  public Response exists(
      @PathParam("urn") final String raw, @HeaderParam("Far-Delegation-Chain") final String chain) {
    final var urn = Urn.parse(raw);
    final var delegates = delegation(chain);
    final var found = resolver.exists(urn, delegates);
    if (found) {
      return Response.ok()
          .header("Far-Resolver", identity)
          .header("Far-Namespace", urn.namespace())
          .build();
    }
    return Response.status(Response.Status.NOT_FOUND).build();
  }
}
