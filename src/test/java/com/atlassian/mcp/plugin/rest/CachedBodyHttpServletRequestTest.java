package com.atlassian.mcp.plugin.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * The SDK transport reads the body after the filter has already read it. If the replay were wrong,
 * every MCP request would reach the SDK empty, so these pin the replay rather than the peek.
 */
public class CachedBodyHttpServletRequestTest {

  private static final String BODY = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\"}";

  private static HttpServletRequest requestWith(String body, long declaredLength) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    when(request.getContentLengthLong()).thenReturn(declaredLength);
    when(request.getInputStream()).thenReturn(servletStream(bytes));
    return request;
  }

  private static ServletInputStream servletStream(byte[] bytes) {
    ByteArrayInputStream source = new ByteArrayInputStream(bytes);
    return new ServletInputStream() {
      @Override
      public int read() {
        return source.read();
      }

      @Override
      public boolean isFinished() {
        return source.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener listener) {}
    };
  }

  @Test
  public void theBodyCanBeReadAfterTheFilterHasAlreadyReadIt() throws Exception {
    HttpServletRequest wrapped =
        CachedBodyHttpServletRequest.caching(requestWith(BODY, BODY.length()), 1024);

    assertEquals(BODY, CachedBodyHttpServletRequest.bodyOf(wrapped));
    assertEquals(BODY, new String(wrapped.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    assertEquals(BODY, new String(wrapped.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  @Test
  public void theReaderSeesTheSameBodyAsTheStream() throws Exception {
    HttpServletRequest wrapped =
        CachedBodyHttpServletRequest.caching(requestWith(BODY, BODY.length()), 1024);

    assertEquals(BODY, wrapped.getReader().readLine());
  }

  /** A body larger than the cap is left for the SDK to read and reject; nothing is buffered. */
  @Test
  public void anOversizedBodyIsNotHeld() throws Exception {
    HttpServletRequest original = requestWith(BODY, 8 * 1024);

    HttpServletRequest result = CachedBodyHttpServletRequest.caching(original, 1024);

    assertSame(original, result);
    assertNull(CachedBodyHttpServletRequest.bodyOf(result));
  }

  /** A chunked request declares -1, and reading it to find out how big it is defeats the cap. */
  @Test
  public void aBodyOfUnknownLengthIsNotHeld() throws Exception {
    HttpServletRequest original = requestWith(BODY, -1);

    HttpServletRequest result = CachedBodyHttpServletRequest.caching(original, 1024);

    assertSame(original, result);
    assertNull(CachedBodyHttpServletRequest.bodyOf(result));
  }

  @Test
  public void anEmptyBodyIsHeldWithoutComplaint() throws Exception {
    HttpServletRequest wrapped = CachedBodyHttpServletRequest.caching(requestWith("", 0), 1024);

    assertEquals("", CachedBodyHttpServletRequest.bodyOf(wrapped));
  }
}
