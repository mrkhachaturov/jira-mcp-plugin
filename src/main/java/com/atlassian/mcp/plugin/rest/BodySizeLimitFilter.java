package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caps the MCP POST body at 1 MiB, enforced on the ACTUAL bytes read — not on a trusted {@code
 * Content-Length} header. A fixed-length header over the cap is rejected as a fast path; otherwise
 * the stream is drained up to the cap + 1 byte and, if more remains, the request is rejected with
 * 413. This closes the bypass where a client omits Content-Length (chunked transfer encoding) or
 * under-declares it to slip an oversized body past the gate. The already-read bytes are re-wrapped
 * in a {@link BufferedRequestWrapper} so the SDK transport downstream still reads the body intact.
 */
@UnrestrictedAccess
@Named("mcpBodySizeLimitFilter")
public class BodySizeLimitFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(BodySizeLimitFilter.class);
  private static final long MAX_BYTES = 1024L * 1024L;

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpReq = (HttpServletRequest) req;
    HttpServletResponse httpResp = (HttpServletResponse) resp;

    long declared = httpReq.getContentLengthLong();
    if (declared > MAX_BYTES) {
      reject(httpResp, declared);
      return;
    }

    // Only POST carries a body worth buffering; let other methods pass untouched.
    if (!"POST".equalsIgnoreCase(httpReq.getMethod())) {
      chain.doFilter(req, resp);
      return;
    }

    byte[] body = readBounded(httpReq, MAX_BYTES);
    if (body == null) {
      reject(httpResp, MAX_BYTES + 1);
      return;
    }
    chain.doFilter(new BufferedRequestWrapper(httpReq, body), resp);
  }

  /** Drains up to {@code max} bytes; returns null if the stream holds more than the cap. */
  private static byte[] readBounded(HttpServletRequest req, long max) throws IOException {
    ServletInputStream in = req.getInputStream();
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    long total = 0;
    int n;
    while ((n = in.read(chunk)) != -1) {
      total += n;
      if (total > max) {
        return null;
      }
      buf.write(chunk, 0, n);
    }
    return buf.toByteArray();
  }

  private static void reject(HttpServletResponse resp, long size) throws IOException {
    log.warn("[MCP-SEC] body too large (declared/actual {} bytes, max {})", size, MAX_BYTES);
    resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
  }
}
