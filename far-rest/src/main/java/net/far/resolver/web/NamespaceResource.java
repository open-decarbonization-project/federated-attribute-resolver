package net.far.resolver.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.model.Namespace;
import net.far.resolver.spi.DriverRegistry;

@Path("/v1/namespaces")
@Produces(MediaType.APPLICATION_JSON)
public class NamespaceResource {

  @Inject DriverRegistry registry;

  @Inject PeerRegistry peers;

  @GET
  public Map<String, Object> list(@QueryParam("type") @DefaultValue("all") final String type) {
    final var result = new ArrayList<Namespace>();
    if (!"delegated".equals(type)) {
      for (final var driver : registry.all()) {
        for (final var namespace : driver.namespaces()) {
          result.add(
              new Namespace(
                  namespace, driver.name() + " driver", driver.name(), driver.name(), true));
        }
      }
    }
    if (!"local".equals(type)) {
      for (final var peer : peers.all()) {
        for (final var namespace : peer.namespaces()) {
          if (!registry.supported().contains(namespace)) {
            result.add(
                new Namespace(
                    namespace, "Delegated via " + peer.identity(), peer.identity(), null, false));
          }
        }
      }
    }
    return Map.of("namespaces", result);
  }

  @GET
  @Path("/{namespace}")
  public Response get(@PathParam("namespace") final String name) {
    final var driver = registry.find(name);
    if (driver.isPresent()) {
      final var d = driver.get();
      final var namespace = new Namespace(name, d.name() + " driver", d.name(), d.name(), true);
      return response(namespace);
    }
    for (final var peer : peers.all()) {
      if (peer.namespaces().contains(name)) {
        final var namespace =
            new Namespace(name, "Delegated via " + peer.identity(), peer.identity(), null, false);
        return response(namespace);
      }
    }
    return Response.status(Response.Status.NOT_FOUND).build();
  }

  private Response response(Namespace namespace) {
    final var detail = new LinkedHashMap<String, Object>();
    detail.put("namespace", namespace);
    detail.put("supported_attributes", List.of());
    detail.put("capabilities", Map.of());
    detail.put("identifier_format", null);
    detail.put("example_urns", List.of());
    return Response.ok(detail).build();
  }
}
