package com.atlassian.mcp.plugin.rest.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

/**
 * Matches an authorization request's {@code redirect_uri} against a client's declared URIs.
 *
 * <p>Exact string match, except for loopback URIs, where the port is ignored per RFC 8252 §7.3 — a
 * native client on an ephemeral port declares {@code http://localhost/callback} and sends {@code
 * http://localhost:54321/callback}. Scheme, host, path and query must still match exactly ({@code
 * localhost} is not {@code 127.0.0.1}). Safe because a loopback address is only reachable from the
 * user's own machine.
 */
public final class RedirectUriMatcher {

  private RedirectUriMatcher() {}

  public static boolean matches(String requested, List<String> registered) {
    if (requested == null || requested.isEmpty() || registered == null || registered.isEmpty()) {
      return false;
    }
    if (registered.contains(requested)) {
      return true;
    }
    URI req = parseLoopback(requested);
    if (req == null) {
      return false;
    }
    for (String candidate : registered) {
      URI reg = parseLoopback(candidate);
      if (reg != null && equalIgnoringPort(req, reg)) {
        return true;
      }
    }
    return false;
  }

  /** An http loopback URI with no credentials and no fragment (RFC 6749 §3.1.2), else null. */
  private static URI parseLoopback(String value) {
    URI uri;
    try {
      uri = new URI(value);
    } catch (URISyntaxException e) {
      return null;
    }
    if (uri.getUserInfo() != null
        || uri.getRawFragment() != null
        || !"http".equalsIgnoreCase(uri.getScheme())) {
      return null;
    }
    String host = uri.getHost();
    if (host == null) {
      return null;
    }
    boolean loopback =
        host.equalsIgnoreCase("localhost")
            || host.equals("127.0.0.1")
            || host.equals("[::1]")
            || host.equals("::1");
    return loopback ? uri : null;
  }

  private static boolean equalIgnoringPort(URI a, URI b) {
    return a.getHost().equalsIgnoreCase(b.getHost())
        && path(a).equals(path(b))
        && Objects.equals(a.getRawQuery(), b.getRawQuery());
  }

  private static String path(URI uri) {
    return uri.getRawPath() == null ? "" : uri.getRawPath();
  }
}
