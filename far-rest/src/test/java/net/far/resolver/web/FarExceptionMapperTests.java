package net.far.resolver.web;

import static org.assertj.core.api.Assertions.assertThat;

import net.far.resolver.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FarExceptionMapperTests {

  private FarExceptionMapper mapper;

  @BeforeEach
  void setup() {
    mapper = new FarExceptionMapper();
  }

  @Test
  void shouldMapInvalidUrnTo400() {
    final var response = mapper.toResponse(new InvalidUrnException("bad urn"));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
    assertThat(((ErrorResponse) response.getEntity()).error().code()).isEqualTo("invalid_urn");
  }

  @Test
  void shouldMapNamespaceNotFoundTo404() {
    final var response = mapper.toResponse(new NamespaceNotFoundException("unknown"));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(((ErrorResponse) response.getEntity()).error().code())
        .isEqualTo("namespace_not_found");
  }

  @Test
  void shouldMapIdentifierNotFoundTo404() {
    final var response = mapper.toResponse(new IdentifierNotFoundException("missing"));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(((ErrorResponse) response.getEntity()).error().code())
        .isEqualTo("identifier_not_found");
  }

  @Test
  void shouldMapUnauthorizedTo401() {
    final var response = mapper.toResponse(new UnauthorizedException("denied"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(((ErrorResponse) response.getEntity()).error().code()).isEqualTo("unauthorized");
  }

  @Test
  void shouldMapSignatureExceptionTo401() {
    final var response = mapper.toResponse(new SignatureException("bad sig"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(((ErrorResponse) response.getEntity()).error().code())
        .isEqualTo("signature_invalid");
  }

  @Test
  void shouldMapDelegationLoopTo409() {
    final var response = mapper.toResponse(new DelegationLoopException("loop"));

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(((ErrorResponse) response.getEntity()).error().code()).isEqualTo("delegation_loop");
  }

  @Test
  void shouldMapDelegationExceptionTo502() {
    final var response = mapper.toResponse(new DelegationException("failed"));

    assertThat(response.getStatus()).isEqualTo(502);
    assertThat(((ErrorResponse) response.getEntity()).error().code())
        .isEqualTo("delegation_failed");
  }

  @Test
  void shouldIncludeTimestampInResponse() {
    final var response = mapper.toResponse(new InvalidUrnException("bad"));

    assertThat(((ErrorResponse) response.getEntity()).error().timestamp()).isNotNull();
  }
}
