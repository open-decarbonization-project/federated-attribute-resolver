package net.far.resolver.model;

public class DelegationLoopException extends FarException {

  public DelegationLoopException(final String server) {
    super("delegation_loop", "Delegation loop detected at: " + server);
  }
}
