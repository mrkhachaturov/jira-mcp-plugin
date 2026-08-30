package com.atlassian.mcp.plugin.rest.oauth;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class RedirectUriMatcherTest {

  /** What Claude Code's CIMD declares. */
  private static final List<String> LOOPBACK =
      List.of("http://localhost/callback", "http://127.0.0.1/callback");

  @Test
  public void matchesExactUri() {
    assertTrue(
        RedirectUriMatcher.matches("https://app.example/cb", List.of("https://app.example/cb")));
  }

  @Test
  public void matchesLoopbackOnEphemeralPort() {
    assertTrue(RedirectUriMatcher.matches("http://localhost:62127/callback", LOOPBACK));
    assertTrue(RedirectUriMatcher.matches("http://127.0.0.1:8080/callback", LOOPBACK));
  }

  @Test
  public void rejectsDifferentLoopbackHost() {
    assertFalse(
        RedirectUriMatcher.matches(
            "http://127.0.0.1:62127/callback", List.of("http://localhost/callback")));
  }

  @Test
  public void rejectsDifferentPath() {
    assertFalse(RedirectUriMatcher.matches("http://localhost:62127/steal", LOOPBACK));
  }

  @Test
  public void rejectsDifferentQuery() {
    assertFalse(RedirectUriMatcher.matches("http://localhost:62127/callback?x=1", LOOPBACK));
  }

  @Test
  public void doesNotRelaxPortForNonLoopbackHosts() {
    assertFalse(
        RedirectUriMatcher.matches(
            "https://app.example:8443/cb", List.of("https://app.example/cb")));
  }

  @Test
  public void rejectsLookalikeHost() {
    assertFalse(RedirectUriMatcher.matches("http://localhost.evil.example:80/callback", LOOPBACK));
  }

  @Test
  public void rejectsEmbeddedCredentials() {
    assertFalse(RedirectUriMatcher.matches("http://evil@localhost:62127/callback", LOOPBACK));
  }

  @Test
  public void rejectsFragment() {
    assertFalse(RedirectUriMatcher.matches("http://localhost:62127/callback#x", LOOPBACK));
  }

  @Test
  public void rejectsMissingOrEmptyInput() {
    assertFalse(RedirectUriMatcher.matches(null, LOOPBACK));
    assertFalse(RedirectUriMatcher.matches("", LOOPBACK));
    assertFalse(RedirectUriMatcher.matches("http://localhost:62127/callback", List.of()));
    assertFalse(RedirectUriMatcher.matches("http://localhost:62127/callback", null));
  }
}
