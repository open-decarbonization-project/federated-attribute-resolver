package net.far.resolver.web;

import static org.mockito.Mockito.*;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityFilterTests {

  private IdentityFilter filter;
  private ContainerRequestContext context;

  private static String token(final String payload) {
    final var header =
        """
        {"alg":"EdDSA","typ":"JWT"}\
        """;
    return encode(header) + "." + encode(payload) + "." + encode("fake-signature");
  }

  private static String encode(final String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes());
  }

  @BeforeEach
  void setup() {
    filter = new IdentityFilter();
    context = mock(ContainerRequestContext.class);
  }

  @Test
  void shouldExtractRequester() {
    final var payload =
        """
        {"sub":"user@example.com","iss":"auth.example.com","iat":1700000000}\
        """;
    final var token = token(payload);
    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + token);

    filter.filter(context);

    verify(context)
        .setProperty(
            eq("far.requester"),
            argThat(
                arg -> {
                  final var requester = (net.far.resolver.model.Requester) arg;
                  return "user@example.com".equals(requester.subject())
                      && "auth.example.com".equals(requester.issuer());
                }));
  }

  @Test
  void shouldSkipMissing() {
    when(context.getHeaderString("Authorization")).thenReturn(null);

    filter.filter(context);

    verify(context, never()).setProperty(anyString(), any());
  }

  @Test
  void shouldSkipNonBearer() {
    when(context.getHeaderString("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

    filter.filter(context);

    verify(context, never()).setProperty(anyString(), any());
  }

  @Test
  void shouldSkipMalformed() {
    when(context.getHeaderString("Authorization")).thenReturn("Bearer not-a-valid-token");

    filter.filter(context);

    verify(context, never()).setProperty(anyString(), any());
  }
}
