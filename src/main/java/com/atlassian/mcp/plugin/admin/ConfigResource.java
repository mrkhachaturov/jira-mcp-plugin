package com.atlassian.mcp.plugin.admin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
public class ConfigResource {

    private final UserManager userManager;
    private final McpPluginConfig config;

    @Inject
    public ConfigResource(
            @ComponentImport UserManager userManager,
            McpPluginConfig config) {
        this.userManager = userManager;
        this.config = config;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig(@Context HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null || !userManager.isSystemAdmin(user.getUserKey())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", config.isEnabled());
        result.put("allowedUsers", String.join(",", config.getAllowedUserKeys()));
        result.put("disabledTools", String.join(",", config.getDisabledTools()));
        result.put("readOnlyMode", config.isReadOnlyMode());
        result.put("jiraBaseUrl", config.getJiraBaseUrlOverride());
        return Response.ok(result).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putConfig(Map<String, Object> body, @Context HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null || !userManager.isSystemAdmin(user.getUserKey())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (body.containsKey("enabled")) {
            config.setEnabled(Boolean.parseBoolean(body.get("enabled").toString()));
        }
        if (body.containsKey("allowedUsers")) {
            config.setAllowedUserKeys(body.get("allowedUsers").toString());
        }
        if (body.containsKey("disabledTools")) {
            config.setDisabledTools(body.get("disabledTools").toString());
        }
        if (body.containsKey("readOnlyMode")) {
            config.setReadOnlyMode(Boolean.parseBoolean(body.get("readOnlyMode").toString()));
        }
        if (body.containsKey("jiraBaseUrl")) {
            config.setJiraBaseUrlOverride(body.get("jiraBaseUrl").toString());
        }

        return Response.noContent().build();
    }
}
