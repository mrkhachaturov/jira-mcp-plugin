package com.atlassian.mcp.plugin.tools;

/**
 * The fields a batch tool adds to its result when a {@link CancellationSignal} stopped it early.
 *
 * <p>Shared so the four batch tools report a short run the same way. Without {@link #PROCESSED} and
 * {@link #TOTAL} a caller cannot tell a stopped run from one where the rest of the items failed,
 * and would retry work that is already in Jira.
 */
public final class BatchResult {

  /** Present, and true, only when the batch stopped before reaching its last item. */
  public static final String CANCELLED = "cancelled";

  public static final String CANCELLED_REASON = "cancelled_reason";
  public static final String PROCESSED = "processed";
  public static final String TOTAL = "total";

  private BatchResult() {}
}
