package com.atlassian.mcp.plugin.rest;

import static org.junit.Assert.*;

import com.atlassian.mcp.plugin.tools.CancellationSignal;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class McpCancellationRegistryTest {

  private static final String SESSION = "session-1";
  private static final String CALL = McpCancellationRegistry.key(SESSION, "7");

  private McpCancellationRegistry registry;

  @Before
  public void setUp() {
    registry = new McpCancellationRegistry();
  }

  @Test
  public void aRunningCallIsNotCancelledUntilSomethingAsks() {
    registry.begin(CALL);

    assertEquals(Optional.empty(), registry.signalFor(CALL).cancellation());
  }

  @Test
  public void aCancelledCallReportsTheReasonItWasGiven() {
    registry.begin(CALL);
    registry.cancel(CALL, "user pressed stop");

    assertEquals(Optional.of("user pressed stop"), registry.signalFor(CALL).cancellation());
  }

  /** The signal is read repeatedly, once per item, and has to keep saying stop. */
  @Test
  public void aCancelledCallKeepsSayingStop() {
    registry.begin(CALL);
    CancellationSignal signal = registry.signalFor(CALL);
    registry.cancel(CALL, "user pressed stop");

    assertTrue(signal.cancellation().isPresent());
    assertTrue(signal.cancellation().isPresent());
    assertTrue(signal.cancellation().isPresent());
  }

  /**
   * Spec: a receiver MAY ignore a cancellation whose request is unknown. Dropping it is also what
   * stops a caller from growing the map by naming ids that were never running.
   */
  @Test
  public void cancellingSomethingThatIsNotRunningIsDroppedRatherThanRemembered() {
    registry.cancel(CALL, "user pressed stop");

    assertTrue(registry.isEmpty());
    assertEquals(Optional.empty(), registry.signalFor(CALL).cancellation());
  }

  @Test
  public void aCancellationDoesNotOutliveTheCallItNamed() {
    registry.begin(CALL);
    registry.cancel(CALL, "user pressed stop");
    registry.end(CALL);

    registry.begin(CALL);

    assertEquals(Optional.empty(), registry.signalFor(CALL).cancellation());
  }

  @Test
  public void aFinishedCallLeavesNothingBehind() {
    registry.begin(CALL);
    registry.end(CALL);

    assertTrue(registry.isEmpty());
  }

  /** Request ids are unique only within a session, so the session has to be part of the key. */
  @Test
  public void oneSessionCannotStopAnotherSessionsCall() {
    String mine = McpCancellationRegistry.key("session-1", "7");
    String theirs = McpCancellationRegistry.key("session-2", "7");
    registry.begin(mine);
    registry.begin(theirs);

    registry.cancel(theirs, "user pressed stop");

    assertEquals(Optional.empty(), registry.signalFor(mine).cancellation());
    assertTrue(registry.signalFor(theirs).cancellation().isPresent());
  }

  @Test
  public void theFirstReasonWins() {
    registry.begin(CALL);
    registry.cancel(CALL, "first");
    registry.cancel(CALL, "second");

    assertEquals(Optional.of("first"), registry.signalFor(CALL).cancellation());
  }
}
