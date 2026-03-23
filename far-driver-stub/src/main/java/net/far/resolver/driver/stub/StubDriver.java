package net.far.resolver.driver.stub;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import net.far.resolver.model.*;
import net.far.resolver.model.query.Filter;
import net.far.resolver.model.query.Page;
import net.far.resolver.model.query.Query;
import net.far.resolver.spi.Driver;

/** In-memory stub driver with hardcoded certificate data for testing. */
public class StubDriver implements Driver {

  private final Map<String, Resolution> data = new HashMap<>();
  private final Map<String, History> histories = new HashMap<>();

  public StubDriver() {
    seed();
  }

  private static Object raw(final Value value) {
    return switch (value) {
      case Value.Text text -> text.raw();
      case Value.Numeric numeric -> numeric.raw().longValue();
      case Value.Quantity quantity -> quantity.amount().longValue();
      case Value.Bool bool -> bool.raw();
      case Value.Temporal temporal -> temporal.raw().toString();
      case Value.Arr arr -> null;
      case Value.Record record -> null;
    };
  }

  private static double number(final Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return 0;
  }

  private void seed() {
    // TEST-001: A basic verified carbon credit
    final var urn1 = new Urn("stub", "TEST-001");
    final var attributes1 =
        Map.of(
            "volume",
                new Attribute(
                    "volume",
                    Value.of(1000, "tCO2e"),
                    "stub-registry",
                    true,
                    Instant.parse("2024-01-15T00:00:00Z")),
            "vintage",
                new Attribute(
                    "vintage",
                    Value.of(2023),
                    "stub-registry",
                    true,
                    Instant.parse("2024-01-15T00:00:00Z")),
            "methodology",
                new Attribute(
                    "methodology",
                    Value.of("VM0007"),
                    "stub-registry",
                    true,
                    Instant.parse("2024-01-15T00:00:00Z")),
            "status",
                new Attribute(
                    "status",
                    Value.of("active"),
                    "stub-registry",
                    true,
                    Instant.parse("2024-01-15T00:00:00Z")));
    data.put(
        urn1.toString(),
        new Resolution(
            urn1,
            "stub",
            "TEST-001",
            attributes1,
            null,
            "stub-driver",
            Instant.parse("2024-01-15T00:00:00Z")));

    // TEST-002: A transferred certificate
    final var urn2 = new Urn("stub", "TEST-002");
    final var attributes2 =
        Map.of(
            "volume",
                new Attribute(
                    "volume",
                    Value.of(500, "MWh"),
                    "stub-registry",
                    true,
                    Instant.parse("2024-03-01T00:00:00Z")),
            "source",
                new Attribute(
                    "source",
                    Value.of("wind"),
                    "stub-registry",
                    true,
                    Instant.parse("2024-03-01T00:00:00Z")),
            "status",
                new Attribute(
                    "status",
                    Value.of("retired"),
                    "stub-registry",
                    true,
                    Instant.parse("2024-06-15T00:00:00Z")));
    data.put(
        urn2.toString(),
        new Resolution(
            urn2,
            "stub",
            "TEST-002",
            attributes2,
            null,
            "stub-driver",
            Instant.parse("2024-03-01T00:00:00Z")));

    // Histories
    histories.put(
        urn1.toString(),
        new History(
            urn1,
            List.of(
                new Event(
                    Event.EventType.ISSUANCE,
                    Instant.parse("2024-01-15T00:00:00Z"),
                    "stub-registry",
                    Map.of("project", "Test Project Alpha")),
                new Event(
                    Event.EventType.STATUS_CHANGE,
                    Instant.parse("2024-02-01T00:00:00Z"),
                    "auditor-1",
                    Map.of()))));

    histories.put(
        urn2.toString(),
        new History(
            urn2,
            List.of(
                new Event(
                    Event.EventType.ISSUANCE,
                    Instant.parse("2024-03-01T00:00:00Z"),
                    "stub-registry",
                    Map.of()),
                new Event(
                    Event.EventType.TRANSFER,
                    Instant.parse("2024-04-15T00:00:00Z"),
                    "trader-1",
                    Map.of("to", "buyer-1")),
                new Event(
                    Event.EventType.RETIREMENT,
                    Instant.parse("2024-06-15T00:00:00Z"),
                    "buyer-1",
                    Map.of()))));
  }

  @Override
  public String name() {
    return "stub";
  }

  @Override
  public Set<String> namespaces() {
    return Set.of("stub");
  }

  @Override
  public Optional<Resolution> resolve(final Urn urn) {
    return Optional.ofNullable(data.get(urn.toString()));
  }

  @Override
  public Optional<History> history(final Urn urn) {
    return Optional.ofNullable(histories.get(urn.toString()));
  }

  @Override
  public boolean exists(final Urn urn) {
    return data.containsKey(urn.toString());
  }

  @Override
  public Page query(final Query query) {
    final var matched =
        data.values().stream()
            .filter(resolution -> query.filter() == null || matches(resolution, query.filter()))
            .collect(Collectors.toList());
    final var total = matched.size();
    final var skip = Math.min(query.skip(), total);
    final var end = Math.min(skip + query.top(), total);
    return new Page(matched.subList(skip, end), total, query.skip(), query.top());
  }

  private boolean matches(final Resolution resolution, final Filter filter) {
    return switch (filter) {
      case Filter.Comparison comparison -> evaluate(resolution, comparison);
      case Filter.And and ->
          and.operands().stream().allMatch(operand -> matches(resolution, operand));
      case Filter.Or or -> or.operands().stream().anyMatch(operand -> matches(resolution, operand));
    };
  }

  private boolean evaluate(final Resolution resolution, final Filter.Comparison comparison) {
    final var attribute = resolution.attributes().get(comparison.field());
    if (attribute == null || attribute.value() == null) {
      return false;
    }
    final var raw = raw(attribute.value());
    return switch (comparison.operator()) {
      case EQ -> Objects.equals(raw, comparison.operand());
      case NE -> !Objects.equals(raw, comparison.operand());
      case GT -> number(raw) > number(comparison.operand());
      case GE -> number(raw) >= number(comparison.operand());
      case LT -> number(raw) < number(comparison.operand());
      case LE -> number(raw) <= number(comparison.operand());
      case IN -> comparison.operand() instanceof List<?> values && values.contains(raw);
      case CONTAINS -> raw != null && raw.toString().contains(comparison.operand().toString());
    };
  }

  @Override
  public Optional<Rendition> rendition(final Urn urn, final String media) {
    if (!exists(urn) || !"text/html".equals(media)) {
      return Optional.empty();
    }
    final var resolution = data.get(urn.toString());
    final var html =
        "<html><body><h1>"
            + urn
            + "</h1>"
            + "<table>"
            + resolution.attributes().entrySet().stream()
                .map(
                    e ->
                        "<tr><td>" + e.getKey() + "</td><td>" + e.getValue().value() + "</td></tr>")
                .reduce("", String::concat)
            + "</table></body></html>";
    return Optional.of(
        new Rendition(html.getBytes(StandardCharsets.UTF_8), media, urn, Instant.now()));
  }
}
