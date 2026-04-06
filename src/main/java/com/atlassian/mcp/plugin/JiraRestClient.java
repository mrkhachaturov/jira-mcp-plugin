package com.atlassian.mcp.plugin;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Named
public class JiraRestClient {

    private final ApplicationProperties applicationProperties;
    private final com.atlassian.mcp.plugin.config.McpPluginConfig pluginConfig;
    private final HttpClient httpClient;

    @Inject
    public JiraRestClient(
            @ComponentImport ApplicationProperties applicationProperties,
            com.atlassian.mcp.plugin.config.McpPluginConfig pluginConfig) {
        this.applicationProperties = applicationProperties;
        this.pluginConfig = pluginConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String get(String path, String authHeader) throws McpToolException {
        return execute(buildRequest(path, authHeader, "GET", null));
    }

    public String post(String path, String body, String authHeader) throws McpToolException {
        return execute(buildRequest(path, authHeader, "POST", body));
    }

    public String put(String path, String body, String authHeader) throws McpToolException {
        return execute(buildRequest(path, authHeader, "PUT", body));
    }

    public String delete(String path, String authHeader) throws McpToolException {
        return execute(buildRequest(path, authHeader, "DELETE", null));
    }

    private String getBaseUrl() {
        String override = pluginConfig.getJiraBaseUrlOverride();
        if (override != null && !override.isBlank()) {
            return override.replaceAll("/+$", "");
        }
        return applicationProperties.getBaseUrl().replaceAll("/+$", "");
    }

    private HttpRequest buildRequest(String path, String authHeader, String method, String body) {
        String url = getBaseUrl() + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        if (authHeader != null && !authHeader.isBlank()) {
            builder.header("Authorization", authHeader);
        }

        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }

    private String execute(HttpRequest request) throws McpToolException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new McpToolException(
                        "Jira API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            // Trim response to match upstream mcp-atlassian's to_simplified_dict() output
            return ResponseTrimmer.trim(response.body());
        } catch (IOException e) {
            throw new McpToolException("Failed to connect to Jira REST API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolException("Jira REST API call interrupted", e);
        }
    }
}
