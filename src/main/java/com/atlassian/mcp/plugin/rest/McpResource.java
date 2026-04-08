package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.mcp.plugin.JsonRpcHandler;
import com.atlassian.mcp.plugin.McpToolException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP Streamable HTTP endpoint per MCP spec 2025-06-18.
 *
 * POST  → JSON-RPC request → JSON response (or SSE for batch with progressToken)
 * GET   → SSE stream for server-initiated notifications
 * DELETE → close session
 */
@Path("/")
@UnrestrictedAccess
public class McpResource {

    private static final Logger log = LoggerFactory.getLogger(McpResource.class);

    private static final String SUPPORTED_PROTOCOL_VERSION = "2025-06-18";
    private static final String SSE_CONTENT_TYPE = "text/event-stream";
    private static final int HEARTBEAT_INTERVAL_MS = 30_000;

    // ── Security limits ─────────────────────────────────────────────
    private static final int MAX_SESSIONS = 200;
    private static final long SESSION_TTL_MS = 4 * 60 * 60 * 1000; // 4 hours
    private static final int MAX_BODY_BYTES = 1_048_576; // 1 MB
    private static final int MCP_RATE_PER_MIN = 120; // per user

    private final JsonRpcHandler handler;
    private final McpPluginConfig config;
    private final UserManager userManager;
    private final GroupManager groupManager;
    private final ApplicationProperties applicationProperties;
    private final RateLimiter rateLimiter;

    /** Active sessions — static because JAX-RS creates a new resource instance per request. */
    private static final Map<String, SseSession> sessions = new ConcurrentHashMap<>();

    /** Global event ID counter — monotonically increasing across all streams. */
    private static final AtomicLong eventIdCounter = new AtomicLong(0);

    // ── SSE lifecycle metrics ────────────────────────────────────────
    private static final AtomicInteger activeStreams = new AtomicInteger(0);
    private static final AtomicLong totalEventsSent = new AtomicLong(0);
    private static final AtomicLong totalStreamsOpened = new AtomicLong(0);
    private static final AtomicLong totalReconnects = new AtomicLong(0);

    @Inject
    public McpResource(
            JsonRpcHandler handler,
            McpPluginConfig config,
            RateLimiter rateLimiter,
            @ComponentImport UserManager userManager,
            @ComponentImport GroupManager groupManager,
            @ComponentImport ApplicationProperties applicationProperties) {
        this.handler = handler;
        this.config = config;
        this.rateLimiter = rateLimiter;
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
        Response originError = validateOrigin(request);
        if (originError != null) return originError;

        // Body size limit
        if (body != null && body.length() > MAX_BODY_BYTES) {
            log.warn("[MCP-SEC] Oversized request body ({} bytes) from {}", body.length(), getClientIp(request));
            return Response.status(413)
                    .entity("{\"error\":\"Request body too large\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        Response authError = checkAuth(request);
        if (authError != null) return authError;

        UserProfile user = userManager.getRemoteUser(request);
        String userKey = user.getUserKey().getStringValue();
        String username = user.getUsername();

        if (!isAccessAllowed(username, userKey)) {
            log.warn("[MCP-SEC] Access denied for user '{}' from {}", username, getClientIp(request));
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"User not authorized for MCP access\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        // Rate limit per user
        if (!rateLimiter.isAllowed(userKey, "mcp", MCP_RATE_PER_MIN)) {
            log.warn("[MCP-SEC] Rate limit exceeded for user '{}' from {}", username, getClientIp(request));
            return Response.status(429)
                    .header("Retry-After", "60")
                    .entity("{\"error\":\"Rate limit exceeded\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        String protocolVersion = request.getHeader("MCP-Protocol-Version");
        boolean isInitialize = body != null && body.contains("\"initialize\"");
        if (!isInitialize && protocolVersion != null
                && !SUPPORTED_PROTOCOL_VERSION.equals(protocolVersion)) {
            return Response.status(400)
                    .entity("{\"error\":\"Unsupported MCP-Protocol-Version: " + sanitizeLog(protocolVersion)
                            + ". Supported: " + SUPPORTED_PROTOCOL_VERSION + "\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        String authHeader = request.getHeader("Authorization");
        String sessionId = request.getHeader("MCP-Session-Id");

        if (sessionId != null && !isInitialize) {
            SseSession session = sessions.get(sessionId);
            if (session == null || session.isExpired()) {
                if (session != null) sessions.remove(sessionId);
                return Response.status(404)
                        .entity("{\"error\":\"Unknown or expired session. Send initialize first.\"}")
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
            }
            // Session-user binding: verify the session belongs to this user
            if (!session.userKey.equals(userKey)) {
                log.warn("[MCP-SEC] Session user mismatch: session owner '{}', request user '{}'",
                        session.userKey, userKey);
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\":\"Session does not belong to this user\"}")
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
            }
        }

        // Streaming path: tools/call with progressToken on a streaming-capable tool
        JsonRpcHandler.ToolCallInfo toolCall = handler.resolveToolCall(body, userKey);
        if (toolCall != null) {
            return handleStreamingToolCall(toolCall, authHeader, sessionId);
        }

        // Standard path: single JSON response
        String result = handler.handle(body, userKey, username, authHeader);

        if (result == null) {
            return Response.status(202).build();
        }

        if (isInitialize) {
            cleanupSessions();
            if (sessions.size() >= MAX_SESSIONS) {
                log.warn("[MCP-SEC] Session capacity reached ({}), rejecting new session", MAX_SESSIONS);
                return Response.status(503)
                        .entity("{\"error\":\"Server at session capacity\"}")
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
            }
            String newSessionId = UUID.randomUUID().toString();
            sessions.put(newSessionId, new SseSession(newSessionId, userKey, authHeader));
            log.info("MCP session created: {} for user '{}'", newSessionId.substring(0, 8), username);

            return addSecurityHeaders(Response.ok(result, MediaType.APPLICATION_JSON_TYPE))
                    .header("MCP-Session-Id", newSessionId)
                    .build();
        }

        Response.ResponseBuilder rb = addSecurityHeaders(Response.ok(result, MediaType.APPLICATION_JSON_TYPE));
        if (sessionId != null) {
            rb.header("MCP-Session-Id", sessionId);
        }
        return rb.build();
    }

    // ── GET — SSE stream for server-initiated notifications ──────────

    @GET
    public Response handleGet(@Context HttpServletRequest request) {
        Response originError = validateOrigin(request);
        if (originError != null) return originError;

        // Auth required on GET (was missing — security fix)
        Response authError = checkAuth(request);
        if (authError != null) return authError;

        UserProfile user = userManager.getRemoteUser(request);
        String userKey = user.getUserKey().getStringValue();
        String username = user.getUsername();

        if (!isAccessAllowed(username, userKey)) {
            log.warn("[MCP-SEC] SSE access denied for user '{}' from {}", username, getClientIp(request));
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"User not authorized for MCP access\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        String sessionId = request.getHeader("MCP-Session-Id");
        SseSession session = sessionId != null ? sessions.get(sessionId) : null;
        if (session == null || session.isExpired()) {
            if (session != null) sessions.remove(sessionId);
            return Response.status(405)
                    .entity("{\"error\":\"SSE requires a valid MCP-Session-Id. POST initialize first.\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        // Session-user binding
        if (!session.userKey.equals(userKey)) {
            log.warn("[MCP-SEC] SSE session user mismatch: session owner '{}', request user '{}'",
                    session.userKey, userKey);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Session does not belong to this user\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        // Check for Last-Event-ID (client reconnecting after disconnect)
        String lastEventId = request.getHeader("Last-Event-ID");
        if (lastEventId != null) {
            totalReconnects.incrementAndGet();
            log.info("SSE reconnect for session {} from Last-Event-ID: {}",
                    sessionId.substring(0, 8), lastEventId);
        }

        StreamingOutput stream = output -> {
            totalStreamsOpened.incrementAndGet();
            activeStreams.incrementAndGet();
            log.info("SSE stream opened for session {} (active: {})",
                    sessionId.substring(0, 8), activeStreams.get());

            try {
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(output, StandardCharsets.UTF_8), true);

                // Priming event with ID for reconnection
                writeSseEvent(writer, null, "heartbeat", "");

                // Heartbeat loop (30s interval keeps proxies from killing the connection)
                while (!writer.checkError()) {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    writeSseEvent(writer, null, "heartbeat", "");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                int remaining = activeStreams.decrementAndGet();
                log.info("SSE stream closed for session {} (active: {})",
                        sessionId.substring(0, 8), remaining);
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
        Response originError = validateOrigin(request);
        if (originError != null) return originError;

        Response authError = checkAuth(request);
        if (authError != null) return authError;

        UserProfile user = userManager.getRemoteUser(request);
        String userKey = user.getUserKey().getStringValue();

        String sessionId = request.getHeader("MCP-Session-Id");
        if (sessionId != null) {
            SseSession session = sessions.get(sessionId);
            if (session != null) {
                // Session-user binding
                if (!session.userKey.equals(userKey)) {
                    log.warn("[MCP-SEC] DELETE session user mismatch from {}", getClientIp(request));
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("{\"error\":\"Session does not belong to this user\"}")
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build();
                }
                sessions.remove(sessionId);
                log.info("MCP session closed: {}", sessionId.substring(0, 8));
                return Response.ok("{\"status\":\"session closed\"}").build();
            }
        }
        return Response.status(404)
                .entity("{\"error\":\"No active session\"}")
                .build();
    }

    // ── SSE streaming for batch tools ─────────────────────────────────

    private Response handleStreamingToolCall(JsonRpcHandler.ToolCallInfo toolCall,
                                             String authHeader, String sessionId) {
        StreamingOutput stream = output -> {
            totalStreamsOpened.incrementAndGet();
            activeStreams.incrementAndGet();
            String streamId = UUID.randomUUID().toString().substring(0, 8);
            log.info("SSE tool stream opened: {} for tool {} (active: {})",
                    streamId, toolCall.tool().name(), activeStreams.get());

            try {
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(output, StandardCharsets.UTF_8), true);

                // 1. Priming event
                writeSseEvent(writer, streamId, "heartbeat", "");

                // 2. Execute tool with progress callback
                String resultText;
                boolean isError;
                try {
                    resultText = toolCall.tool().executeWithProgress(
                            toolCall.args(), authHeader,
                            (current, total, message) -> {
                                String notification = handler.buildProgressNotification(
                                        toolCall.progressToken(), current, total, message);
                                if (notification != null) {
                                    writeSseEvent(writer, streamId, "progress", notification);
                                }
                            });
                    isError = false;
                } catch (McpToolException e) {
                    writeSseEvent(writer, streamId, "error",
                            "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
                    resultText = e.getMessage();
                    isError = true;
                }

                // 3. Final response
                String response = handler.buildToolResult(toolCall.id(), resultText, isError);
                writeSseEvent(writer, streamId, "message", response);

            } finally {
                int remaining = activeStreams.decrementAndGet();
                log.info("SSE tool stream closed: {} (active: {})", streamId, remaining);
            }
        };

        Response.ResponseBuilder rb = Response.ok(stream)
                .type(SSE_CONTENT_TYPE)
                .header("Cache-Control", "no-cache");
        if (sessionId != null) {
            rb.header("MCP-Session-Id", sessionId);
        }
        return rb.build();
    }

    // ── SSE event writer ─────────────────────────────────────────────

    private void writeSseEvent(PrintWriter writer, String streamPrefix, String eventType, String data) {
        long id = eventIdCounter.incrementAndGet();
        totalEventsSent.incrementAndGet();

        String eventId = streamPrefix != null ? streamPrefix + "-" + id : String.valueOf(id);

        writer.write("id: " + eventId + "\n");
        if (!"heartbeat".equals(eventType)) {
            writer.write("event: " + eventType + "\n");
        }
        if (data != null && !data.isEmpty()) {
            writer.write("data: " + data + "\n");
        } else {
            writer.write("data: \n");
        }
        writer.write("\n");
        writer.flush();
    }

    // ── Origin validation (MUST per spec) ────────────────────────────

    /** Hosts allowed as Origin besides the Jira base URL itself. */
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "claude.ai", "www.claude.ai", "claude.com", "www.claude.com",
            "chatgpt.com", "www.chatgpt.com", "openai.com", "www.openai.com");

    private Response validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return null;
        }

        String baseUrl = getJiraBaseUrl();
        try {
            URI baseUri = URI.create(baseUrl);
            URI originUri = URI.create(origin);

            String baseHost = baseUri.getHost();
            String originHost = originUri.getHost();

            if (baseHost != null && baseHost.equalsIgnoreCase(originHost)) {
                return null;
            }
            if ("localhost".equals(originHost) || "127.0.0.1".equals(originHost)) {
                return null;
            }
            if (originHost != null && ALLOWED_ORIGINS.contains(originHost.toLowerCase())) {
                return null;
            }
        } catch (IllegalArgumentException e) {
            // Malformed Origin — reject
        }

        log.warn("[MCP-SEC] Rejected Origin '{}' from {}", sanitizeLog(origin), getClientIp(request));
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
            log.warn("[MCP-SEC] Unauthenticated request from {}", getClientIp(request));
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

    // ── Security helpers ─────────────────────────────────────────────

    private static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Strip control characters to prevent log injection. */
    private static String sanitizeLog(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\\r\\n\\t]", "_");
    }

    private static Response.ResponseBuilder addSecurityHeaders(Response.ResponseBuilder rb) {
        return rb.header("X-Content-Type-Options", "nosniff")
                 .header("Cache-Control", "no-store")
                 .header("X-Frame-Options", "DENY");
    }

    // ── Session tracking ─────────────────────────────────────────────

    private static final int MAX_SESSION_ID_LOG_LEN = 8;

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

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SESSION_TTL_MS;
        }
    }

    private static void cleanupSessions() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    // ── Metrics (accessible for monitoring) ──────────────────────────

    /** Returns current SSE lifecycle metrics. */
    public static Map<String, Long> getSseMetrics() {
        return Map.of(
                "activeStreams", (long) activeStreams.get(),
                "totalStreamsOpened", totalStreamsOpened.get(),
                "totalEventsSent", totalEventsSent.get(),
                "totalReconnects", totalReconnects.get(),
                "activeSessions", (long) sessions.size()
        );
    }
}
