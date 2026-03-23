package net.far.resolver.signature;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.bouncycastle.crypto.signers.Ed25519Signer;

/** Signs HTTP messages per RFC 9421 using Ed25519. */
public class Signer {

  private final KeyManager keys;

  public Signer(final KeyManager keys) {
    this.keys = keys;
  }

  /** Builds the covered component lines (without the signature-params line). */
  static String lines(final MessageComponents components) {
    final var builder = new StringBuilder();
    if (components.status() > 0) {
      builder.append("\"@status\": ").append(components.status()).append("\n");
    } else {
      builder.append("\"@method\": ").append(components.method()).append("\n");
      builder.append("\"@target-uri\": ").append(components.target()).append("\n");
      builder.append("\"@authority\": ").append(components.authority()).append("\n");
    }
    if (components.type() != null) {
      builder.append("\"content-type\": ").append(components.type()).append("\n");
    }
    if (components.digest() != null) {
      builder.append("\"content-digest\": ").append(components.digest()).append("\n");
    }
    if (components.headers() != null) {
      for (final var entry : components.headers().entrySet()) {
        builder
            .append("\"")
            .append(entry.getKey())
            .append("\": ")
            .append(entry.getValue())
            .append("\n");
      }
    }
    return builder.toString();
  }

  /**
   * Creates a signature base string from message components and signature parameters per RFC 9421.
   */
  public String base(final MessageComponents components, final String parameters) {
    return lines(components) + "\"@signature-params\": " + parameters;
  }

  /**
   * Signs the message components and returns a {@link Signature} containing the Base64-encoded
   * Ed25519 signature and the corresponding Signature-Input header value.
   */
  public Signature sign(final MessageComponents components) {
    final var parameters = params(components);
    final var base = base(components, parameters);
    final var signer = new Ed25519Signer();
    signer.init(true, keys.signing());
    final var bytes = base.getBytes(StandardCharsets.UTF_8);
    signer.update(bytes, 0, bytes.length);
    final var value = signer.generateSignature();
    final var encoded = Base64.getEncoder().encodeToString(value);
    final var input = "sig1=" + parameters;
    return new Signature(encoded, input);
  }

  private String params(final MessageComponents components) {
    final var covered = new StringBuilder("(");
    if (components.status() > 0) {
      covered.append("\"@status\" ");
    } else {
      if (components.method() != null) covered.append("\"@method\" ");
      if (components.target() != null) covered.append("\"@target-uri\" ");
      if (components.authority() != null) covered.append("\"@authority\" ");
    }
    if (components.type() != null) covered.append("\"content-type\" ");
    if (components.digest() != null) covered.append("\"content-digest\" ");
    if (components.headers() != null) {
      for (final var name : components.headers().keySet()) {
        covered.append("\"").append(name).append("\" ");
      }
    }
    final var list = covered.toString().trim() + ")";
    return list
        + ";keyid=\""
        + keys.id()
        + "\";alg=\"ed25519\";created="
        + Instant.now().getEpochSecond();
  }
}
