package net.far.resolver.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.signature.KeyRing;
import net.far.resolver.spi.DriverRegistry;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/v1/peers")
@Produces(MediaType.APPLICATION_JSON)
public class PeerResource {

  @ConfigProperty(name = "far.identity", defaultValue = "https://far.example.com")
  String identity;

  @ConfigProperty(name = "far.protocol.version", defaultValue = "0.1.0")
  String version;

  @ConfigProperty(name = "far.delegation.depth", defaultValue = "5")
  int depth;

  @Inject PeerRegistry peers;

  @Inject DriverRegistry registry;

  @Inject KeyRing ring;

  @GET
  public Map<String, Object> list(@QueryParam("status") final String status) {
    final var all = List.copyOf(peers.all());
    if ("connected".equals(status)) {
      return Map.of("peers", all);
    }
    if ("disconnected".equals(status)) {
      return Map.of("peers", List.of());
    }
    return Map.of("peers", all);
  }

  @GET
  @Path("/configuration")
  public Response configuration() {
    final var result = new LinkedHashMap<String, Object>();
    result.put("identity", identity);
    result.put("protocol_version", "far/1.0");
    result.put("api_base", identity + "/v1");
    result.put("namespaces", List.copyOf(registry.supported()));
    result.put(
        "public_key",
        Map.of(
            "algorithm", "Ed25519",
            "key_id", ring.current().id(),
            "public_key_pem", ring.current().pem()));

    final var retired = new ArrayList<Map<String, Object>>();
    for (final var previous : ring.previous()) {
      retired.add(
          Map.of(
              "key_id", previous.keys().id(),
              "public_key_pem", previous.keys().pem(),
              "expires", previous.expires().toString()));
    }
    result.put("previous_keys", retired);

    result.put("delegation", Map.of("accepts_delegation", true, "max_depth", depth));
    result.put("version", version);
    result.put(
        "endpoints",
        Map.of(
            "resources", "/v1/resources",
            "namespaces", "/v1/namespaces",
            "peers", "/v1/peers",
            "health", "/v1/health"));
    return Response.ok(result).header("Cache-Control", "public, max-age=3600").build();
  }
}
