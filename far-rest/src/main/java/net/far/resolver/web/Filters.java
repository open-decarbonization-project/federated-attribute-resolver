package net.far.resolver.web;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.far.resolver.model.query.Filter;
import net.far.resolver.model.query.InvalidFilterException;
import net.far.resolver.model.query.Operator;

public final class Filters {

  private Filters() {}

  public static Filter parse(final String expression) {
    if (expression == null || expression.isBlank()) {
      throw new InvalidFilterException("Filter expression must not be blank");
    }
    final var parser = new Parser(expression.trim());
    final var result = parser.expression();
    parser.end();
    return result;
  }

  public static Set<String> namespaces(final Filter filter) {
    final var result = new LinkedHashSet<String>();
    collect(filter, result);
    return Set.copyOf(result);
  }

  public static Filter strip(final Filter filter) {
    return remove(filter);
  }

  private static void collect(final Filter filter, final Set<String> target) {
    switch (filter) {
      case Filter.Comparison comparison -> namespace(comparison, target);
      case Filter.And and -> {
        for (final var operand : and.operands()) {
          if (operand instanceof Filter.Comparison comparison) {
            namespace(comparison, target);
          }
        }
      }
      case Filter.Or ignored -> {
        // Never extract namespaces from OR — the semantics would change
      }
    }
  }

  private static void namespace(final Filter.Comparison comparison, final Set<String> target) {
    if ("namespace".equals(comparison.field())) {
      if (comparison.operator() == Operator.EQ) {
        target.add(comparison.operand().toString());
      } else if (comparison.operator() == Operator.IN
          && comparison.operand() instanceof List<?> values) {
        for (final var value : values) {
          target.add(value.toString());
        }
      }
    }
  }

  private static Filter remove(final Filter filter) {
    switch (filter) {
      case Filter.Comparison comparison -> {
        if ("namespace".equals(comparison.field())) {
          return null;
        }
        return comparison;
      }
      case Filter.And and -> {
        final var remaining = new ArrayList<Filter>();
        for (final var operand : and.operands()) {
          final var stripped = remove(operand);
          if (stripped != null) {
            remaining.add(stripped);
          }
        }
        if (remaining.isEmpty()) {
          return null;
        }
        if (remaining.size() == 1) {
          return remaining.getFirst();
        }
        return new Filter.And(remaining);
      }
      case Filter.Or or -> {
        // Preserve OR nodes intact — namespace inside OR has different semantics
        return or;
      }
    }
  }

  private static final class Parser {

    private final String input;
    private int position;

    Parser(final String input) {
      this.input = input;
      this.position = 0;
    }

    Filter expression() {
      return or();
    }

    void end() {
      whitespace();
      if (position < input.length()) {
        throw new InvalidFilterException(
            "Unexpected token at position " + position + ": '" + input.substring(position) + "'");
      }
    }

    private Filter or() {
      final var operands = new ArrayList<Filter>();
      operands.add(and());
      while (keyword("or")) {
        operands.add(and());
      }
      if (operands.size() == 1) {
        return operands.getFirst();
      }
      return new Filter.Or(operands);
    }

    private Filter and() {
      final var operands = new ArrayList<Filter>();
      operands.add(primary());
      while (keyword("and")) {
        operands.add(primary());
      }
      if (operands.size() == 1) {
        return operands.getFirst();
      }
      return new Filter.And(operands);
    }

    private Filter primary() {
      whitespace();
      if (position < input.length() && input.charAt(position) == '(') {
        position++;
        final var result = expression();
        whitespace();
        expect(')');
        return result;
      }
      if (lookahead("contains")) {
        return function();
      }
      return comparison();
    }

    private Filter function() {
      consume("contains");
      whitespace();
      expect('(');
      whitespace();
      final var field = identifier();
      whitespace();
      expect(',');
      whitespace();
      final var literal = string();
      whitespace();
      expect(')');
      return new Filter.Comparison(field, Operator.CONTAINS, literal);
    }

    private Filter comparison() {
      whitespace();
      final var field = identifier();
      whitespace();
      final var operator = operator();
      whitespace();
      final var operand = value(operator);
      return new Filter.Comparison(field, operator, operand);
    }

    private String identifier() {
      whitespace();
      final var start = position;
      while (position < input.length()
          && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '_')) {
        position++;
      }
      if (position == start) {
        throw new InvalidFilterException("Expected identifier at position " + position);
      }
      return input.substring(start, position);
    }

    private Operator operator() {
      whitespace();
      for (final var operator : Operator.values()) {
        if (operator == Operator.CONTAINS) {
          continue;
        }
        final var token = operator.name().toLowerCase();
        if (lookaheadKeyword(token)) {
          consume(token);
          return operator;
        }
      }
      throw new InvalidFilterException("Expected operator at position " + position);
    }

    private Object value(final Operator operator) {
      whitespace();
      if (operator == Operator.IN) {
        return list();
      }
      return scalar();
    }

    private Object scalar() {
      whitespace();
      if (position < input.length() && input.charAt(position) == '\'') {
        return string();
      }
      return number();
    }

    private String string() {
      expect('\'');
      final var builder = new StringBuilder();
      while (position < input.length() && input.charAt(position) != '\'') {
        if (input.charAt(position) == '\\' && position + 1 < input.length()) {
          position++;
        }
        builder.append(input.charAt(position));
        position++;
      }
      expect('\'');
      return builder.toString();
    }

    private Number number() {
      whitespace();
      final var start = position;
      if (position < input.length() && input.charAt(position) == '-') {
        position++;
      }
      while (position < input.length() && Character.isDigit(input.charAt(position))) {
        position++;
      }
      var decimal = false;
      if (position < input.length() && input.charAt(position) == '.') {
        decimal = true;
        position++;
        while (position < input.length() && Character.isDigit(input.charAt(position))) {
          position++;
        }
      }
      if (position == start) {
        throw new InvalidFilterException("Expected value at position " + position);
      }
      final var raw = input.substring(start, position);
      if (decimal) {
        return Double.parseDouble(raw);
      }
      return Long.parseLong(raw);
    }

    private List<Object> list() {
      whitespace();
      expect('(');
      final var items = new ArrayList<>();
      items.add(scalar());
      while (comma()) {
        items.add(scalar());
      }
      whitespace();
      expect(')');
      return items;
    }

    private boolean keyword(final String word) {
      whitespace();
      if (lookaheadKeyword(word)) {
        position += word.length();
        return true;
      }
      return false;
    }

    private boolean lookaheadKeyword(final String word) {
      if (position + word.length() > input.length()) {
        return false;
      }
      final var end = position + word.length();
      if (!input.substring(position, end).equalsIgnoreCase(word)) {
        return false;
      }
      return end >= input.length() || !Character.isLetterOrDigit(input.charAt(end));
    }

    private boolean lookahead(final String word) {
      whitespace();
      return lookaheadKeyword(word);
    }

    private void consume(final String token) {
      position += token.length();
    }

    private boolean comma() {
      whitespace();
      if (position < input.length() && input.charAt(position) == ',') {
        position++;
        return true;
      }
      return false;
    }

    private void expect(final char character) {
      if (position >= input.length() || input.charAt(position) != character) {
        throw new InvalidFilterException("Expected '" + character + "' at position " + position);
      }
      position++;
    }

    private void whitespace() {
      while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
        position++;
      }
    }
  }
}
