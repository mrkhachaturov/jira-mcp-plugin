package com.atlassian.mcp.plugin.tools;

import java.util.Optional;

/**
 * Per-call context, carrying what a tool needs from the request but never declares as a parameter.
 *
 * <p>A tool reports progress against this context and does not observe how it is delivered, so the
 * same {@code run} body serves a call that asked for progress notifications and one that did not.
 */
public interface McpContext {

  /** The caller's Authorization header, forwarded to the Jira REST API. */
  String authHeader();

  /**
   * Reports progress through an operation.
   *
   * @param current units completed so far
   * @param total total units, or a value of 0 or less when unknown
   * @param message human-readable status
   */
  default void reportProgress(int current, int total, String message) {}

  /**
   * Why the work should stop before its next item, or empty while it should carry on.
   *
   * <p>A tool honours this by leaving its loop, never by abandoning a call already in flight.
   */
  default Optional<String> cancellation() {
    return Optional.empty();
  }

  static McpContext of(String authHeader) {
    return () -> authHeader;
  }

  static McpContext of(String authHeader, McpTool.ProgressCallback progress) {
    return of(authHeader, progress, CancellationSignal.NONE);
  }

  static McpContext of(
      String authHeader, McpTool.ProgressCallback progress, CancellationSignal cancellation) {
    return new McpContext() {
      @Override
      public String authHeader() {
        return authHeader;
      }

      @Override
      public void reportProgress(int current, int total, String message) {
        progress.report(current, total, message);
      }

      @Override
      public Optional<String> cancellation() {
        return cancellation.cancellation();
      }
    };
  }
}
