package net.far.resolver.model.query;

import java.util.Set;

public record Query(Set<String> namespaces, Filter filter, int top, int skip, String orderby) {

  public static final int DEFAULT_TOP = 25;
  public static final int MAX_TOP = 500;

  public Query {
    if (namespaces == null) {
      namespaces = Set.of();
    } else {
      namespaces = Set.copyOf(namespaces);
    }
    if (top <= 0) {
      top = DEFAULT_TOP;
    } else if (top > MAX_TOP) {
      top = MAX_TOP;
    }
    if (skip < 0) {
      skip = 0;
    }
  }
}
