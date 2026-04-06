package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Path("/")
public class McpResource {

    private static final String SUPPORTED_PROTOCOL_VERSION = "2025-06-18";

    private final JsonRpcHandler handler;
    private final McpPluginConfig config;
    private final UserManager userManager;

    @Inject
    public McpResource(
            JsonRpcHandler handler,
            McpPluginConfig config,
            @ComponentImport UserManager userManager) {
        this.handler = handler;
        this.config = config;
        this.userManager = userManager;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handlePost(String body, @Context HttpServletRequest request) {
        // Check if MCP is enabled
        if (!config.isEnabled()) {
            return Response.status(503)
                    .entity("{\"error\":\"MCP server is disabled\"}")
                    .build();
        }

        // Get authenticated user
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Authentication required\"}")
                    .build();
        }

        String userKey = user.getUserKey().getStringValue();

        // Check allowlist
        if (!config.isUserAllowed(userKey)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"User not authorized for MCP access\"}")
                    .build();
        }

        // Validate MCP-Protocol-Version header (required after initialize)
        String protocolVersion = request.getHeader("MCP-Protocol-Version");
        boolean isInitialize = body != null && body.contains("\"initialize\"");
        if (!isInitialize && protocolVersion != null
                && !SUPPORTED_PROTOCOL_VERSION.equals(protocolVersion)) {
            return Response.status(400)
                    .entity("{\"error\":\"Unsupported MCP-Protocol-Version: " + protocolVersion
                            + ". Supported: " + SUPPORTED_PROTOCOL_VERSION + "\"}")
                    .build();
        }

        String authHeader = request.getHeader("Authorization");
        String result = handler.handle(body, userKey, authHeader);

        if (result == null) {
            // Notification — return 202 Accepted
            return Response.status(202).build();
        }

        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleGet() {
        return Response.status(405)
                .entity("{\"error\":\"SSE streaming not supported in this version. Use POST.\"}")
                .build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleDelete() {
        return Response.status(405)
                .entity("{\"error\":\"Sessions not supported in this version.\"}")
                .build();
    }
}
