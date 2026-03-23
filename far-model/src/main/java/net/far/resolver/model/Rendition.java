package net.far.resolver.model;

import java.time.Instant;
import java.util.Arrays;

/**
 * A rendered view of a resolution in a specific format (e.g. text/html, application/pdf,
 * image/png).
 */
public record Rendition(byte[] content, String media, Urn urn, Instant timestamp) {

  public Rendition {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("Rendition content must not be empty");
    }
    if (media == null || media.isBlank()) {
      throw new IllegalArgumentException("Rendition media type must not be blank");
    }
    if (urn == null) {
      throw new IllegalArgumentException("Rendition URN must not be null");
    }
    if (timestamp == null) {
      timestamp = Instant.now();
    }
    content = Arrays.copyOf(content, content.length);
  }

  @Override
  public byte[] content() {
    return Arrays.copyOf(content, content.length);
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) return true;
    if (!(other instanceof Rendition that)) return false;
    return Arrays.equals(content, that.content)
        && media.equals(that.media)
        && urn.equals(that.urn)
        && timestamp.equals(that.timestamp);
  }

  @Override
  public int hashCode() {
    var result = Arrays.hashCode(content);
    result = 31 * result + media.hashCode();
    result = 31 * result + urn.hashCode();
    result = 31 * result + timestamp.hashCode();
    return result;
  }
}
