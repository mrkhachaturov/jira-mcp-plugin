package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.mcp.plugin.JsonRpcHandler;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.atlassian.mcp.plugin.McpToolException;

/**
 * MCP Streamable HTTP endpoint per MCP spec 2025-06-18.
 *
 * POST → JSON-RPC request  → JSON response (single message)
 * GET  → SSE stream for server-initiated notifications
 * DELETE → close session
 *
 * Session management via MCP-Session-Id header.
 * Origin validation per spec security requirements.
 */
@Path("/")
@UnrestrictedAccess
public class McpResource {

    private static final String SUPPORTED_PROTOCOL_VERSION = "2025-06-18";
    private static final String SSE_CONTENT_TYPE = "text/event-stream";

    private final JsonRpcHandler handler;
    private final McpPluginConfig config;
    private final UserManager userManager;
    private final GroupManager groupManager;
    private final ApplicationProperties applicationProperties;

    /** Active sessions — static because JAX-RS creates a new resource instance per request. */
    private static final Map<String, SseSession> sessions = new ConcurrentHashMap<>();

    /** Global event ID counter for SSE priming events. */
    private static final AtomicLong eventIdCounter = new AtomicLong(0);

    @Inject
    public McpResource(
            JsonRpcHandler handler,
            McpPluginConfig config,
            @ComponentImport UserManager userManager,
            @ComponentImport GroupManager groupManager,
            @ComponentImport ApplicationProperties applicationProperties) {
        this.handler = handler;
        this.config = config;
        this.userManager = userManager;
        this.groupManager = groupManager;
        this.applicationProperties = applicationProperties;
    }

    private String getJiraBaseUrl() {
        String override = config.getJiraBaseUrlOverride();
        if (override != null && !override.isEmpty()) return override;
        return applicationProperties.getBaseUrl().toString();
    }

    // ── POST — client sends JSON-RPC messages ────────────────────────

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handlePost(String body, @Context HttpServletRequest request) {
        // Origin validation (MUST per spec — prevents DNS rebinding)
        Response originError = validateOrigin(request);
        if (originError != null) return originError;

        // Auth checks
        Response authError = checkAuth(request);
        if (authError != null) return authError;

        UserProfile user = userManager.getRemoteUser(request);
        String userKey = user.getUserKey().getStringValue();
        String username = user.getUsername();

        if (!isAccessAllowed(username, userKey)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"User not authorized for MCP access\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        // Protocol version check
        String protocolVersion = request.getHeader("MCP-Protocol-Version");
        boolean isInitialize = body != null && body.contains("\"initialize\"");
        if (!isInitialize && protocolVersion != null
                && !SUPPORTED_PROTOCOL_VERSION.equals(protocolVersion)) {
            return Response.status(400)
                    .entity("{\"error\":\"Unsupported MCP-Protocol-Version: " + protocolVersion
                            + ". Supported: " + SUPPORTED_PROTOCOL_VERSION + "\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        String authHeader = request.getHeader("Authorization");
        String sessionId = request.getHeader("MCP-Session-Id");

        // Validate session if provided
        if (sessionId != null && !isInitialize && !sessions.containsKey(sessionId)) {
            return Response.status(404)
                    .entity("{\"error\":\"Unknown session. Send initialize first.\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        // Check if this is a tools/call with progressToken on a streaming-capable tool
        JsonRpcHandler.ToolCallInfo toolCall = handler.resolveToolCall(body, userKey);
        if (toolCall != null) {
            // SSE streaming: progress notifications + final result
            return handleStreamingToolCall(toolCall, authHeader, sessionId);
        }

        // Standard path: single JSON response
        String result = handler.handle(body, userKey, authHeader);

        // Notifications return 202 Accepted with no body (per spec)
        if (result == null) {
            return Response.status(202).build();
        }

        // Session management for initialize
        if (isInitialize) {
            String newSessionId = UUID.randomUUID().toString();
            sessions.put(newSessionId, new SseSession(newSessionId, userKey, authHeader));

            return Response.ok(result, MediaType.APPLICATION_JSON_TYPE)
                    .header("MCP-Session-Id", newSessionId)
                    .build();
        }

        Response.ResponseBuilder rb = Response.ok(result, MediaType.APPLICATION_JSON_TYPE);
        if (sessionId != null) {
            rb.header("MCP-Session-Id", sessionId);
        }
        return rb.build();
    }

    // ── GET — SSE stream for server-initiated notifications ──────────

    @GET
    public Response handleGet(@Context HttpServletRequest request) {
        // Origin validation
        Response originError = validateOrigin(request);
        if (originError != null) return originError;

        if (!config.isEnabled()) {
            return Response.status(503)
                    .entity("{\"error\":\"MCP server is disabled\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        String sessionId = request.getHeader("MCP-Session-Id");
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            // Per spec: return 405 if server does not offer SSE at this endpoint
            return Response.status(405)
                    .entity("{\"error\":\"SSE requires a valid MCP-Session-Id. POST initialize first.\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        // Return SSE stream with priming event (per spec: SHOULD send event ID + empty data)
        StreamingOutput stream = output -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);

            // Priming event: event ID + empty data so client can reconnect with Last-Event-ID
            long eventId = eventIdCounter.incrementAndGet();
            writer.write("id: " + eventId + "\n");
            writer.write("data: \n\n");
            writer.flush();

            // Hold connection for server-initiated events
            // Currently we have no server-push use cases, so just keep-alive
            try {
                while (!writer.checkError()) {
                    Thread.sleep(30000);
                    writer.write(": keep-alive\n\n");
                    writer.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        return Response.ok(stream)
                .type(SSE_CONTENT_TYPE)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("MCP-Session-Id", sessionId)
                .build();
    }

    // ── DELETE — close session ───────────────────────────────────────

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleDelete(@Context HttpServletRequest request) {
        String sessionId = request.getHeader("MCP-Session-Id");
        if (sessionId != null) {
            SseSession removed = sessions.remove(sessionId);
            if (removed != null) {
                return Response.ok("{\"status\":\"session closed\"}").build();
            }
        }
        return Response.status(404)
                .entity("{\"error\":\"No active session\"}")
                .build();
    }

    // ── SSE streaming for batch tools ─────────────────────────────────

    /**
     * Handles a tools/call with progressToken — returns SSE stream with
     * progress notifications followed by the final CallToolResult.
     */
    private Response handleStreamingToolCall(JsonRpcHandler.ToolCallInfo toolCall,
                                             String authHeader, String sessionId) {
        StreamingOutput stream = output -> {
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
            AtomicLong eventSeq = new AtomicLong(0);
            String streamId = UUID.randomUUID().toString().substring(0, 8);

            // 1. Priming event (per spec: SHOULD send event ID + empty data for reconnection)
            writer.write("id: " + streamId + "-" + eventSeq.incrementAndGet() + "\n");
            writer.write("data: \n\n");
            writer.flush();

            // 2. Execute tool with progress callback that writes SSE events
            String resultText;
            boolean isError;
            try {
                resultText = toolCall.tool().executeWithProgress(
                        toolCall.args(), authHeader,
                        (current, total, message) -> {
                            String notification = handler.buildProgressNotification(
                                    toolCall.progressToken(), current, total, message);
                            if (notification != null) {
                                writer.write("id: " + streamId + "-" + eventSeq.incrementAndGet() + "\n");
                                writer.write("event: message\n");
                                writer.write("data: " + notification + "\n\n");
                                writer.flush();
                            }
                        });
                isError = false;
            } catch (McpToolException e) {
                resultText = e.getMessage();
                isError = true;
            }

            // 3. Final response — the complete CallToolResult
            String response = handler.buildToolResult(toolCall.id(), resultText, isError);
            writer.write("id: " + streamId + "-" + eventSeq.incrementAndGet() + "\n");
            writer.write("event: message\n");
            writer.write("data: " + response + "\n\n");
            writer.flush();
        };

        Response.ResponseBuilder rb = Response.ok(stream)
                .type(SSE_CONTENT_TYPE)
                .header("Cache-Control", "no-cache");
        if (sessionId != null) {
            rb.header("MCP-Session-Id", sessionId);
        }
        return rb.build();
    }

    // ── Origin validation (MUST per spec) ────────────────────────────

    /**
     * Validates the Origin header to prevent DNS rebinding attacks.
     * Per MCP spec: if Origin is present and invalid, MUST return 403.
     */
    private Response validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            // No Origin header — allow (server-to-server calls, curl, etc. don't send Origin)
            return null;
        }

        // Validate: Origin must match our Jira base URL's scheme+host+port
        String baseUrl = getJiraBaseUrl();
        try {
            URI baseUri = URI.create(baseUrl);
            URI originUri = URI.create(origin);

            String baseHost = baseUri.getHost();
            String originHost = originUri.getHost();

            if (baseHost != null && baseHost.equalsIgnoreCase(originHost)) {
                return null; // Same host — allowed
            }

            // Also allow localhost for local development
            if ("localhost".equals(originHost) || "127.0.0.1".equals(originHost)) {
                return null;
            }
        } catch (IllegalArgumentException e) {
            // Malformed Origin — reject
        }

        return Response.status(Response.Status.FORBIDDEN)
                .entity("{\"error\":\"Invalid Origin header\"}")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    // ── Auth helpers ─────────────────────────────────────────────────

    private Response checkAuth(HttpServletRequest request) {
        if (!config.isEnabled()) {
            return Response.status(503)
                    .entity("{\"error\":\"MCP server is disabled\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        UserProfile user = userManager.getRemoteUser(request);
        if (user == null) {
            if (config.isOAuthEnabled()) {
                String resourceMetadata = getJiraBaseUrl()
                        + "/plugins/servlet/mcp-oauth/protected-resource";
                return Response.status(Response.Status.UNAUTHORIZED)
                        .header("WWW-Authenticate",
                                "Bearer resource_metadata=\"" + resourceMetadata + "\"")
                        .entity("{\"error\":\"Authentication required\"}")
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
            }
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Authentication required. Provide a Bearer token (PAT or OAuth).\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }
        return null;
    }

    private boolean isAccessAllowed(String username, String userKey) {
        if (config.isUserAllowed(userKey) || config.isUserAllowed(username)) {
            return true;
        }
        Set<String> allowedGroups = config.getAllowedGroups();
        if (!allowedGroups.isEmpty()) {
            Collection<Group> userGroups = groupManager.getGroupsForUser(username);
            for (Group group : userGroups) {
                if (allowedGroups.contains(group.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Session tracking ─────────────────────────────────────────────

    /** Tracks an active MCP session. */
    private static class SseSession {
        final String id;
        final String userKey;
        final String authHeader;
        final long createdAt;

        SseSession(String id, String userKey, String authHeader) {
            this.id = id;
            this.userKey = userKey;
            this.authHeader = authHeader;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
