package net.far.resolver.model.query;

import java.util.List;
import net.far.resolver.model.Resolution;

public record Page(List<Resolution> value, long count, int skip, int top) {

  public Page {
    if (value == null) {
      value = List.of();
    } else {
      value = List.copyOf(value);
    }
    if (skip < 0) {
      skip = 0;
    }
    if (top <= 0) {
      top = Query.DEFAULT_TOP;
    }
  }
}
