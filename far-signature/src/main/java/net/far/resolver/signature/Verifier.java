package net.far.resolver.signature;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;
import net.far.resolver.model.SignatureException;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

/** Verifies HTTP message signatures per RFC 9421. */
public class Verifier {

  private static final Pattern KEYID = Pattern.compile("keyid=\"([^\"]+)\"");
  private static final Pattern CREATED = Pattern.compile("created=(\\d+)");
  private static final Pattern EXPIRES = Pattern.compile("expires=(\\d+)");
  private static final long SKEW = 60;

  private static void temporal(final String parameters) {
    final var now = Instant.now().getEpochSecond();
    final var created = CREATED.matcher(parameters);
    if (created.find()) {
      final var value = Long.parseLong(created.group(1));
      if (value > now + SKEW) {
        throw new SignatureException("Signature created in the future");
      }
    }
    final var expires = EXPIRES.matcher(parameters);
    if (expires.find()) {
      final var value = Long.parseLong(expires.group(1));
      if (value < now - SKEW) {
        throw new SignatureException("Signature has expired");
      }
    }
  }

  /**
   * Verifies a signature against message components using the provided public key and the original
   * signature-input parameters.
   *
   * @throws SignatureException if the signature is invalid
   */
  public boolean verify(
      final MessageComponents components,
      final String signature,
      final String input,
      final Ed25519PublicKeyParameters key) {
    try {
      final var parameters = input.startsWith("sig1=") ? input.substring(5) : input;
      temporal(parameters);
      final var base = Signer.lines(components) + "\"@signature-params\": " + parameters;
      final var verifier = new Ed25519Signer();
      verifier.init(false, key);
      final var bytes = base.getBytes(StandardCharsets.UTF_8);
      verifier.update(bytes, 0, bytes.length);
      final var decoded = Base64.getDecoder().decode(signature);
      final var valid = verifier.verifySignature(decoded);
      if (!valid) {
        throw new SignatureException("Signature verification failed");
      }
      return true;
    } catch (final SignatureException exception) {
      throw exception;
    } catch (final Exception exception) {
      throw new SignatureException("Signature verification error", exception);
    }
  }

  /** Extracts the key ID from a Signature-Input header value. */
  public String keyid(final String input) {
    final var matcher = KEYID.matcher(input);
    if (matcher.find()) {
      return matcher.group(1);
    }
    throw new SignatureException("No keyid found in signature input: " + input);
  }
}
