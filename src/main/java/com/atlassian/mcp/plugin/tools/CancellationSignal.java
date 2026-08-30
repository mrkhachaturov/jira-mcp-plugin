package com.atlassian.mcp.plugin.tools;

import java.util.Optional;

/**
 * Why work already under way should stop before its next item.
 *
 * <p>Read only between the items of a batch: a request that has reached Jira runs to completion, so
 * there is no checkpoint inside one. A tool that stops early still reports what it wrote, since
 * those writes are already in Jira.
 */
@FunctionalInterface
public interface CancellationSignal {

  /** A signal that never asks for a stop. */
  CancellationSignal NONE = Optional::empty;

  /** Why the work should stop, or empty while it should carry on. */
  Optional<String> cancellation();
}
