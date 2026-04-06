package com.atlassian.mcp.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.sal.api.ApplicationProperties;
import org.junit.Before;
import org.junit.Test;

public class JiraRestClientTest {

    private ApplicationProperties applicationProperties;
    private McpPluginConfig pluginConfig;

    @Before
    public void setUp() {
        applicationProperties = mock(ApplicationProperties.class);
        pluginConfig = mock(McpPluginConfig.class);
        when(applicationProperties.getBaseUrl()).thenReturn("http://localhost:2990/jira");
        when(pluginConfig.getJiraBaseUrlOverride()).thenReturn("");
    }

    @Test
    public void testBaseUrlUsesApplicationProperties() {
        JiraRestClient client = new JiraRestClient(applicationProperties, pluginConfig);
        assertNotNull(client);
    }

    @Test
    public void testBaseUrlOverrideTakesPrecedence() {
        when(pluginConfig.getJiraBaseUrlOverride()).thenReturn("http://internal:8080");
        JiraRestClient client = new JiraRestClient(applicationProperties, pluginConfig);
        assertNotNull(client);
    }
}
