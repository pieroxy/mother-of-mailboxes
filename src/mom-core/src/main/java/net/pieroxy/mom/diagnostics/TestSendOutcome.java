package net.pieroxy.mom.diagnostics;

/** Where an account's test send stands, as seen by a check that wants its message — see {@link TestSends}. */
public record TestSendOutcome(Kind kind, String message, ReceivedTestMessage received) {
  public enum Kind {
    /** No SMTP sink configured (General Settings): no test send. */
    NOT_CONFIGURED,
    /** Sent or being sent, not received yet: {@link #message} says since when. */
    PENDING,
    /** Couldn't be sent, or never arrived: {@link #message} says why. */
    FAILED,
    /** Arrived: see {@link #received}. */
    RECEIVED,
  }

  public static TestSendOutcome notConfigured() {
    return new TestSendOutcome(Kind.NOT_CONFIGURED, null, null);
  }

  public static TestSendOutcome pending(String message) {
    return new TestSendOutcome(Kind.PENDING, message, null);
  }

  public static TestSendOutcome failed(String message) {
    return new TestSendOutcome(Kind.FAILED, message, null);
  }

  public static TestSendOutcome received(ReceivedTestMessage received) {
    return new TestSendOutcome(Kind.RECEIVED, null, received);
  }
}
