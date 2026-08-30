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
 * Caches the request body so it can be read more than once. {@link BodySizeLimitFilter} constructs
 * it from an already-bounded {@code byte[]}; {@link SessionBindingFilter} uses the
 * read-from-request constructor to inspect {@code "method":"initialize"} before the SDK servlet
 * reads the body downstream.
 */
public final class BufferedRequestWrapper extends HttpServletRequestWrapper {
  private final byte[] body;

  public BufferedRequestWrapper(HttpServletRequest request) throws IOException {
    super(request);
    this.body = request.getInputStream().readAllBytes();
  }

  /** Construct from a body the caller already read (bounded), avoiding a second read. */
  public BufferedRequestWrapper(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  public byte[] body() {
    return body;
  }

  @Override
  public ServletInputStream getInputStream() {
    final ByteArrayInputStream bais = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return bais.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener listener) {}

      @Override
      public int read() {
        return bais.read();
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }

  @Override
  public int getContentLength() {
    return body.length;
  }

  @Override
  public long getContentLengthLong() {
    return body.length;
  }
}
