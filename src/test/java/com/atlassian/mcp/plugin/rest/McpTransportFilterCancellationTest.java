package com.atlassian.mcp.plugin.rest;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/**
 * The SDK has no notion of {@code notifications/cancelled} at any published version, so a caller
 * pressing stop only reaches a tool if the filter reads the notification itself. These pin that,
 * and that the message still reaches the SDK afterwards.
 */
public class McpTransportFilterCancellationTest {

  private static final String SESSION = "session-1";

  private McpCancellationRegistry registry;
  private McpTransportFilter filter;
  private HttpServlet delegate;

  @Before
  public void setUp() throws Exception {
    registry = new McpCancellationRegistry();
    delegate = mock(HttpServlet.class);
    McpBootstrap bootstrap = mock(McpBootstrap.class);
    when(bootstrap.buildTransport()).thenReturn(delegate);

    filter = new McpTransportFilter(bootstrap, registry);
    FilterConfig config = mock(FilterConfig.class);
    filter.init(config);
  }

  private HttpServletRequest post(String body) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    when(request.getMethod()).thenReturn("POST");
    when(request.getContentLengthLong()).thenReturn((long) bytes.length);
    when(request.getHeader("Mcp-Session-Id")).thenReturn(SESSION);
    when(request.getInputStream()).thenReturn(stream(bytes));

    Map<String, Object> attributes = new HashMap<>();
    doAnswer(call -> attributes.put(call.getArgument(0), call.getArgument(1)))
        .when(request)
        .setAttribute(anyString(), any());
    when(request.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
    return request;
  }

  private static ServletInputStream stream(byte[] bytes) {
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

  private void run(HttpServletRequest request) throws Exception {
    filter.doFilter(request, mock(HttpServletResponse.class), mock(FilterChain.class));
  }

  @Test
  public void aCancellationForARunningCallReachesItsSignal() throws Exception {
    String call = McpCancellationRegistry.key(SESSION, "7");
    registry.begin(call);

    run(
        post(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
                + "\"params\":{\"requestId\":7,\"reason\":\"user pressed stop\"}}"));

    assertEquals(Optional.of("user pressed stop"), registry.signalFor(call).cancellation());
  }

  @Test
  public void aCancellationWithoutAReasonStillStopsTheCall() throws Exception {
    String call = McpCancellationRegistry.key(SESSION, "7");
    registry.begin(call);

    run(
        post(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
                + "\"params\":{\"requestId\":7}}"));

    assertTrue(registry.signalFor(call).cancellation().isPresent());
  }

  /** The filter is the endpoint: whatever it peeked at still has to be handled by the SDK. */
  @Test
  public void theMessageStillReachesTheSdk() throws Exception {
    run(post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{}}"));

    verify(delegate).service(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  public void aToolCallIsTaggedWithItsIdSoACancellationCanFindIt() throws Exception {
    HttpServletRequest request =
        post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{}}");

    run(request);

    assertEquals("7", request.getAttribute(McpTransportFilter.ATTR_REQUEST_ID));
    assertEquals(SESSION, request.getAttribute(McpTransportFilter.ATTR_SESSION_ID));
  }

  @Test
  public void aMalformedBodyIsNotFatal() throws Exception {
    run(post("not json at all"));

    verify(delegate).service(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  public void aCancellationNamingNothingThatRunsChangesNothing() throws Exception {
    run(
        post(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
                + "\"params\":{\"requestId\":404}}"));

    assertTrue(registry.isEmpty());
  }
}
