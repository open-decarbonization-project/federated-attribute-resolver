package net.far.resolver.web;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import net.far.resolver.model.*;
import net.far.resolver.model.query.InvalidFilterException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
public class FarExceptionMapper implements ExceptionMapper<FarException> {

  @ConfigProperty(name = "far.identity", defaultValue = "https://far.example.com")
  String identity;

  @Override
  public Response toResponse(final FarException exception) {
    final var status =
        switch (exception) {
          case InvalidFilterException e -> Response.Status.BAD_REQUEST;
          case InvalidUrnException e -> Response.Status.BAD_REQUEST;
          case NamespaceNotFoundException e -> Response.Status.NOT_FOUND;
          case IdentifierNotFoundException e -> Response.Status.NOT_FOUND;
          case UnauthorizedException e -> Response.Status.UNAUTHORIZED;
          case DelegationLoopException e -> Response.Status.CONFLICT;
          case DelegationException e -> Response.Status.BAD_GATEWAY;
          case SignatureException e -> Response.Status.UNAUTHORIZED;
          case UnsupportedFormatException e -> Response.Status.NOT_ACCEPTABLE;
          default -> Response.Status.INTERNAL_SERVER_ERROR;
        };

    final var urn =
        exception instanceof IdentifierNotFoundException identifier
            ? identifier.getMessage()
            : null;
    final var body = new ErrorResponse(exception.code(), exception.getMessage(), urn, identity);

    return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
  }
}
