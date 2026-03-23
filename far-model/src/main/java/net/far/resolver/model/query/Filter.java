package net.far.resolver.model.query;

import java.util.List;

public sealed interface Filter {

  record Comparison(String field, Operator operator, Object operand) implements Filter {
    public Comparison {
      if (field == null || field.isBlank()) {
        throw new IllegalArgumentException("Field must not be blank");
      }
      if (operator == null) {
        throw new IllegalArgumentException("Operator must not be null");
      }
      if (operand == null) {
        throw new IllegalArgumentException("Operand must not be null");
      }
    }
  }

  record And(List<Filter> operands) implements Filter {
    public And {
      if (operands == null || operands.size() < 2) {
        throw new IllegalArgumentException("And requires at least two operands");
      }
      operands = List.copyOf(operands);
    }
  }

  record Or(List<Filter> operands) implements Filter {
    public Or {
      if (operands == null || operands.size() < 2) {
        throw new IllegalArgumentException("Or requires at least two operands");
      }
      operands = List.copyOf(operands);
    }
  }
}
