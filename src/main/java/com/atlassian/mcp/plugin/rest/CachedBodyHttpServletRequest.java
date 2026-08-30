package com.atlassian.mcp.plugin.rest;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body can be read more than once.
 *
 * <p>The transport filter reads the JSON-RPC envelope to route {@code notifications/cancelled}
 * itself, and the SDK transport then reads the same body to handle the message. A servlet body is a
 * one-shot stream, so it is held here and replayed.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  private CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  /**
   * Holds the body when its declared length says it is safe to, and hands back the request
   * untouched otherwise — an unbounded or oversized body is left for the SDK to reject rather than
   * buffered here. A request that is not wrapped simply carries no cancellation routing; the
   * notification that matters is a few dozen bytes.
   */
  static HttpServletRequest caching(HttpServletRequest request, int maxBytes) throws IOException {
    long declared = request.getContentLengthLong();
    if (declared < 0 || declared > maxBytes) {
      return request;
    }
    return new CachedBodyHttpServletRequest(request, request.getInputStream().readAllBytes());
  }

  /** The held body as text, or null when this request was not wrapped. */
  static String bodyOf(HttpServletRequest request) {
    return request instanceof CachedBodyHttpServletRequest cached
        ? new String(cached.body, StandardCharsets.UTF_8)
        : null;
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream replay = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public int read() {
        return replay.read();
      }

      @Override
      public int read(byte[] into, int off, int len) {
        return replay.read(into, off, len);
      }

      @Override
      public boolean isFinished() {
        return replay.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("a cached body is read synchronously");
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
