package net.far.resolver.web;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.far.resolver.signature.TokenParser;

/**
 * Extracts requester identity from Bearer tokens per ADR-007. Malformed tokens are logged but not
 * rejected — the token is forwarded as-is during delegation.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class IdentityFilter implements ContainerRequestFilter {

  private static final Logger LOGGER = Logger.getLogger(IdentityFilter.class.getName());
  private static final String PREFIX = "Bearer ";

  @Override
  public void filter(final ContainerRequestContext context) {
    final var header = context.getHeaderString("Authorization");
    if (header == null || !header.startsWith(PREFIX)) {
      return;
    }
    final var token = header.substring(PREFIX.length()).trim();
    final var requester = TokenParser.parse(token);
    if (requester.isPresent()) {
      context.setProperty("far.requester", requester.get());
    } else {
      LOGGER.log(Level.WARNING, "Malformed bearer token in Authorization header");
    }
  }
}
