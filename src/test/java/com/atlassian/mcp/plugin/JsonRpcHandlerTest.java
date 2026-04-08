package com.atlassian.mcp.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.ToolRegistry;
import com.atlassian.sal.api.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

public class JsonRpcHandlerTest {

    private JsonRpcHandler handler;
    private ToolRegistry toolRegistry;
    private McpPluginConfig config;
    private ResourceRegistry resourceRegistry;
    private ApplicationProperties applicationProperties;
    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        toolRegistry = mock(ToolRegistry.class);
        config = mock(McpPluginConfig.class);
        resourceRegistry = mock(ResourceRegistry.class);
        applicationProperties = mock(ApplicationProperties.class);
        handler = new JsonRpcHandler(toolRegistry, config, resourceRegistry, applicationProperties);
    }

    @Test
    public void testInitialize() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        String response = handler.handle(request, "user1", "user1", "User One", "Bearer token");

        JsonNode json = mapper.readTree(response);
        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(1, json.get("id").asInt());
        assertNotNull(json.get("result"));
        assertEquals("jira-mcp", json.get("result").get("serverInfo").get("name").asText());
        assertNotNull(json.get("result").get("capabilities").get("tools"));
    }

    @Test
    public void testPing() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}";
        String response = handler.handle(request, "user1", "user1", "User One", "Bearer token");

        JsonNode json = mapper.readTree(response);
        assertEquals(2, json.get("id").asInt());
        assertNotNull(json.get("result"));
    }

    @Test
    public void testNotificationReturnsNull() {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        String response = handler.handle(request, "user1", "user1", "User One", "Bearer token");
        assertNull(response);
    }

    @Test
    public void testUnknownMethodReturnsError() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"unknown/method\"}";
        String response = handler.handle(request, "user1", "user1", "User One", "Bearer token");

        JsonNode json = mapper.readTree(response);
        assertEquals(-32601, json.get("error").get("code").asInt());
    }

    @Test
    public void testMalformedJsonReturnsParseError() throws Exception {
        String response = handler.handle("not json at all", "user1", "user1", "User One", "Bearer token");

        JsonNode json = mapper.readTree(response);
        assertEquals(-32700, json.get("error").get("code").asInt());
    }

    @Test
    public void testBatchRequestRejected() throws Exception {
        String response = handler.handle("[{},{}]", "user1", "user1", "User One", "Bearer token");

        JsonNode json = mapper.readTree(response);
        assertEquals(-32600, json.get("error").get("code").asInt());
    }
}
