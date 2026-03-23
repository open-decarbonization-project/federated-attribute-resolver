package net.far.resolver.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.ArrayList;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.model.Peer;
import net.far.resolver.signature.KeyManager;
import net.far.resolver.signature.MessageComponents;
import net.far.resolver.signature.Verifier;

/**
 * Verifies incoming HTTP request signatures per RFC 9421. Signature verification is optional —
 * unsigned requests pass through. Falls back to previous keys during rotation grace periods.
 */
@Provider
public class VerificationFilter implements ContainerRequestFilter {

  @Inject Verifier verifier;

  @Inject PeerRegistry peers;

  @Override
  public void filter(final ContainerRequestContext context) {
    final var input = context.getHeaderString("Signature-Input");
    final var signature = context.getHeaderString("Signature");

    if (input == null || signature == null) {
      return;
    }

    try {
      final var id = verifier.keyid(input);
      final var signer = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
      final var peer = peers.get(signer).or(() -> peers.match(signer));

      if (peer.isEmpty() || peer.get().key() == null) {
        abort(context, "Unknown signer: " + signer);
        return;
      }

      final var value = extract(signature);
      final var method = context.getMethod();
      final var target = context.getUriInfo().getRequestUri().toString();
      final var authority = context.getUriInfo().getRequestUri().getAuthority();
      final var type = context.getHeaderString("Content-Type");
      final var digest = context.getHeaderString("Content-Digest");
      final var headers = new java.util.LinkedHashMap<String, String>();
      final var authorization = context.getHeaderString("Authorization");
      if (authorization != null) {
        headers.put("authorization", authorization);
      }
      final var delegation = context.getHeaderString("Far-Delegation-Chain");
      if (delegation != null) {
        headers.put("far-delegation-chain", delegation);
      }
      final var components =
          new MessageComponents(method, target, authority, type, digest, headers);

      final var candidates = candidates(peer.get());
      for (final var candidate : candidates) {
        try {
          final var key = KeyManager.parse(candidate);
          verifier.verify(components, value, input, key);
          context.setProperty("far.peer", peer.get().identity());
          return;
        } catch (final Exception ignored) {
          // try next key
        }
      }
      abort(context, "signature_invalid");
    } catch (final Exception exception) {
      abort(context, "signature_invalid");
    }
  }

  private ArrayList<String> candidates(final Peer peer) {
    final var result = new ArrayList<String>();
    result.add(peer.key());
    final var now = Instant.now();
    for (final var previous : peer.previous()) {
      if (previous.key() != null && previous.expires().isAfter(now)) {
        result.add(previous.key());
      }
    }
    return result;
  }

  private String extract(final String header) {
    var value = header;
    if (value.startsWith("sig1=:")) {
      value = value.substring(6);
    }
    if (value.endsWith(":")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  private void abort(final ContainerRequestContext context, final String message) {
    final var body = new ErrorResponse("signature_invalid", message);
    context.abortWith(
        Response.status(Response.Status.UNAUTHORIZED)
            .type(MediaType.APPLICATION_JSON)
            .entity(body)
            .build());
  }
}
