package com.atlassian.mcp.plugin.tools;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.issues.SearchTool;
import org.junit.Before;
import org.junit.Test;
import java.util.Map;

public class SearchToolTest {

    private JiraRestClient client;
    private SearchTool tool;

    @Before
    public void setUp() {
        client = mock(JiraRestClient.class);
        tool = new SearchTool(client);
    }

    @Test
    public void testName() {
        assertEquals("search", tool.name());
    }

    @Test
    public void testIsNotWriteTool() {
        assertFalse(tool.isWriteTool());
    }

    @Test
    public void testExecuteCallsSearchEndpoint() throws Exception {
        when(client.get(anyString(), anyString()))
                .thenReturn("{\"issues\":[],\"total\":0}");

        String result = tool.execute(
                Map.of("jql", "project = TEST"),
                "Bearer mytoken");

        assertEquals("{\"issues\":[],\"total\":0}", result);
        verify(client).get(contains("/rest/api/2/search?jql="), eq("Bearer mytoken"));
    }

    @Test
    public void testExecutePassesLimit() throws Exception {
        when(client.get(anyString(), anyString())).thenReturn("{}");

        tool.execute(Map.of("jql", "test", "limit", 25), "Bearer t");

        verify(client).get(contains("maxResults=25"), anyString());
    }

    @Test(expected = McpToolException.class)
    public void testExecuteRequiresJql() throws Exception {
        tool.execute(Map.of(), "Bearer t");
    }
}
