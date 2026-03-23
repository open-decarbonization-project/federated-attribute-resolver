package net.far.resolver.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Set;
import net.far.resolver.signature.DigestCalculator;
import net.far.resolver.signature.KeyManager;
import net.far.resolver.signature.MessageComponents;
import net.far.resolver.signature.Signer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Signs outgoing HTTP responses per RFC 9421 using Ed25519. */
@Provider
public class SignatureFilter implements ContainerResponseFilter {

  private static final Set<String> SKIP = Set.of("/v1/health", "/v1/peers/configuration");

  @Inject ObjectMapper mapper;
  @Inject Signer signer;
  @Inject KeyManager keys;

  @ConfigProperty(name = "far.identity", defaultValue = "https://far.example.com")
  String identity;

  @Override
  public void filter(
      final ContainerRequestContext request, final ContainerResponseContext response) {
    final var path = request.getUriInfo().getPath();
    if (SKIP.stream().anyMatch(path::startsWith)) {
      return;
    }

    if (!response.hasEntity()) {
      return;
    }

    if (response.getEntity() instanceof byte[]) {
      return;
    }

    try {
      final var bytes = mapper.writeValueAsBytes(response.getEntity());
      final var digest = DigestCalculator.compute(bytes);

      final var headers = new LinkedHashMap<String, String>();
      final var resolver = response.getHeaderString("Far-Resolver");
      if (resolver != null) {
        headers.put("far-resolver", resolver);
      }
      final var namespace = response.getHeaderString("Far-Namespace");
      if (namespace != null) {
        headers.put("far-namespace", namespace);
      }

      final var type = "application/json;charset=UTF-8";
      response.getHeaders().putSingle("Content-Type", type);

      final var components =
          new MessageComponents(response.getStatus(), null, null, null, type, digest, headers);
      final var signature = signer.sign(components);

      response.getHeaders().putSingle("Content-Digest", digest);
      response.getHeaders().putSingle("Signature", "sig1=:" + signature.value() + ":");
      response.getHeaders().putSingle("Signature-Input", signature.input());
    } catch (final Exception exception) {
      // Signing failure should not break the response
    }
  }
}
