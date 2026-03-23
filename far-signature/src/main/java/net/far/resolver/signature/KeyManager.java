package net.far.resolver.signature;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

/** Manages Ed25519 key pairs for server identity. */
public class KeyManager {

  private final Ed25519PrivateKeyParameters signing;
  private final Ed25519PublicKeyParameters verifying;
  private final String id;

  public KeyManager(final byte[] signing, final byte[] verifying, final String id) {
    this.signing = new Ed25519PrivateKeyParameters(signing, 0);
    this.verifying = new Ed25519PublicKeyParameters(verifying, 0);
    this.id = id;
  }

  /** Generates a new random Ed25519 key pair with the given identifier. */
  public static KeyManager generate(final String id) {
    final var generator = new Ed25519KeyPairGenerator();
    generator.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
    final var pair = generator.generateKeyPair();
    final var signing = (Ed25519PrivateKeyParameters) pair.getPrivate();
    final var verifying = (Ed25519PublicKeyParameters) pair.getPublic();
    return new KeyManager(signing.getEncoded(), verifying.getEncoded(), id);
  }

  /**
   * Loads an Ed25519 key pair from PEM files in the given directory. Expects private.pem (PKCS#8)
   * and public.pem (SPKI).
   */
  public static KeyManager load(final Path directory, final String id) {
    try {
      final var signing = readPrivate(directory.resolve("private.pem"));
      final var verifying = readPublic(directory.resolve("public.pem"));
      return new KeyManager(signing.getEncoded(), verifying.getEncoded(), id);
    } catch (final IOException exception) {
      throw new UncheckedIOException("Failed to load keys from " + directory, exception);
    }
  }

  private static Ed25519PrivateKeyParameters readPrivate(final Path path) throws IOException {
    try (final var reader = new PemReader(Files.newBufferedReader(path))) {
      final var pem = reader.readPemObject();
      final var info = PrivateKeyInfo.getInstance(pem.getContent());
      return (Ed25519PrivateKeyParameters) PrivateKeyFactory.createKey(info);
    }
  }

  private static Ed25519PublicKeyParameters readPublic(final Path path) throws IOException {
    try (final var reader = new PemReader(Files.newBufferedReader(path))) {
      final var pem = reader.readPemObject();
      final var info = SubjectPublicKeyInfo.getInstance(pem.getContent());
      return (Ed25519PublicKeyParameters) PublicKeyFactory.createKey(info);
    }
  }

  /** Parses a public key from either PEM or raw Base64 format. */
  public static Ed25519PublicKeyParameters parse(final String key) {
    if (key.contains("BEGIN PUBLIC KEY")) {
      try {
        final var reader = new PemReader(new java.io.StringReader(key));
        final var pem = reader.readPemObject();
        reader.close();
        final var info = SubjectPublicKeyInfo.getInstance(pem.getContent());
        return (Ed25519PublicKeyParameters) PublicKeyFactory.createKey(info);
      } catch (final IOException exception) {
        throw new UncheckedIOException("Failed to parse PEM public key", exception);
      }
    }
    final var decoded = Base64.getDecoder().decode(key);
    return new Ed25519PublicKeyParameters(decoded, 0);
  }

  /** Writes the key pair as PEM files to the given directory. */
  public void write(final Path directory) {
    try {
      Files.createDirectories(directory);
      final var info = PrivateKeyInfoFactory.createPrivateKeyInfo(signing);
      try (final var writer =
          new PemWriter(Files.newBufferedWriter(directory.resolve("private.pem")))) {
        writer.writeObject(new PemObject("PRIVATE KEY", info.getEncoded()));
      }
      final var spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(verifying);
      try (final var writer =
          new PemWriter(Files.newBufferedWriter(directory.resolve("public.pem")))) {
        writer.writeObject(new PemObject("PUBLIC KEY", spki.getEncoded()));
      }
    } catch (final IOException exception) {
      throw new UncheckedIOException("Failed to write keys to " + directory, exception);
    }
  }

  /** Returns the public key as a PEM-encoded SPKI string. */
  public String pem() {
    try {
      final var info = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(verifying);
      final var buffer = new StringWriter();
      try (final var writer = new PemWriter(buffer)) {
        writer.writeObject(new PemObject("PUBLIC KEY", info.getEncoded()));
      }
      return buffer.toString().trim();
    } catch (final IOException exception) {
      throw new UncheckedIOException("Failed to encode public key as PEM", exception);
    }
  }

  public Ed25519PrivateKeyParameters signing() {
    return signing;
  }

  public Ed25519PublicKeyParameters verifying() {
    return verifying;
  }

  public String id() {
    return id;
  }

  /** Returns the Base64-encoded public key. */
  public String encoded() {
    return Base64.getEncoder().encodeToString(verifying.getEncoded());
  }
}
