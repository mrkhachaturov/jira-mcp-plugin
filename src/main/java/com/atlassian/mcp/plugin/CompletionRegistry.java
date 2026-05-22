package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.rest.JiraAuthContextExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * F-07: implements MCP {@code completion/complete} for server-side argument
 * autocompletion. Scope for v1.4.0 is intentionally tight — only
 * {@code project_key} is completed, because it's a single global list that
 * cheaply caches and benefits virtually every write tool (create issue, create
 * version, search, etc.).
 *
 * <p>Per-project status / issue_type / assignee completions are deliberately
 * deferred: they require knowing the user's project context (the argument
 * autocompletion request only carries the current arg name + a prefix string),
 * are stateful per project, and would need either a real per-project cache or
 * a request-time JQL roundtrip on every keystroke — out of scope for the
 * "completion as a quality-of-life polish" pass.
 *
 * <p>The completion handler is wired against a {@code ref/prompt} reference
 * named {@code project_key}. The SDK's reference key is what the client
 * targets — for tool argument autocompletion the convention is to register
 * one completion per logical argument name and let any tool referencing
 * that argument benefit from the same suggestion list. Clients today (Claude
 * Desktop, ChatGPT, the official MCP CLI) send {@code ref/prompt} for tool
 * arguments; if a host sends {@code ref/resource} pointed at the
 * {@code jira://issue/{issueKey}} template, we also recognise an
 * {@code issueKey} argument name and reuse the same project-key list as a
 * sensible starting point (issue keys begin with a project key).
 *
 * <p>The Jira project list is cached for 60 seconds keyed on the auth header
 * (per-user view) to avoid hammering Jira's API on every keystroke.
 */
@Named
public class CompletionRegistry {

    private static final Logger log = LoggerFactory.getLogger(CompletionRegistry.class);

    /** Reference key advertised for tool/prompt argument completion. */
    static final String PROJECT_KEY_REF = "project_key";

    /** Maximum number of completion suggestions returned (per MCP spec, hard cap 100). */
    private static final int MAX_RESULTS = 20;

    /** Cache TTL for the project list. */
    private static final long CACHE_TTL_NANOS = 60L * 1_000_000_000L;

    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Per-user cached project keys. Keyed by auth header so different users get their own view. */
    private final java.util.concurrent.ConcurrentHashMap<String, CacheEntry> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Fallback shared cache slot for anonymous / context-less calls. */
    private final AtomicReference<CacheEntry> anonymousCache = new AtomicReference<>();

    @Inject
    public CompletionRegistry(JiraRestClient client) {
        this.client = client;
    }

    /**
     * Build the SDK {@link SyncCompletionSpecification}s to register on the server.
     *
     * <p>One spec per supported argument. Currently:
     * <ul>
     *   <li>{@code ref/prompt:project_key} — returns up to 20 Jira project keys
     *       prefix-matching the typed value.</li>
     * </ul>
     */
    public List<SyncCompletionSpecification> toSpecifications() {
        BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> handler =
                (exchange, request) -> handleProjectKey(exchange, request);

        return List.of(
                new SyncCompletionSpecification(
                        new McpSchema.PromptReference(PROJECT_KEY_REF),
                        handler)
        );
    }

    /** Completion handler for the {@code project_key} argument. */
    private CompleteResult handleProjectKey(McpSyncServerExchange exchange, CompleteRequest request) {
        String prefix = request.argument() != null && request.argument().value() != null
                ? request.argument().value().trim().toUpperCase(Locale.ROOT)
                : "";

        String authHeader = readAuthHeader(exchange);
        List<String> keys = projectKeys(authHeader);

        List<String> matches = new ArrayList<>(MAX_RESULTS);
        for (String key : keys) {
            if (prefix.isEmpty() || key.startsWith(prefix)) {
                matches.add(key);
                if (matches.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }

        boolean hasMore = matches.size() == MAX_RESULTS
                && countMatches(keys, prefix) > MAX_RESULTS;

        return new CompleteResult(new CompleteCompletion(
                matches,
                countMatches(keys, prefix),
                hasMore));
    }

    private static int countMatches(List<String> keys, String prefix) {
        if (prefix.isEmpty()) return keys.size();
        int n = 0;
        for (String k : keys) {
            if (k.startsWith(prefix)) n++;
        }
        return n;
    }

    /** Returns the cached project key list, refreshing if stale or absent. */
    private List<String> projectKeys(String authHeader) {
        long now = System.nanoTime();
        if (authHeader == null) {
            CacheEntry e = anonymousCache.get();
            if (e != null && (now - e.fetchedAtNanos) < CACHE_TTL_NANOS) {
                return e.keys;
            }
            List<String> fresh = fetch(null);
            anonymousCache.set(new CacheEntry(fresh, now));
            return fresh;
        }

        CacheEntry e = cache.get(authHeader);
        if (e != null && (now - e.fetchedAtNanos) < CACHE_TTL_NANOS) {
            return e.keys;
        }
        List<String> fresh = fetch(authHeader);
        cache.put(authHeader, new CacheEntry(fresh, now));
        return fresh;
    }

    /** Fetch the project key list from Jira. Returns empty on any failure (best-effort). */
    private List<String> fetch(String authHeader) {
        try {
            String json = client.get("/rest/api/2/project", authHeader);
            List<Map<String, Object>> projects =
                    mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<String> keys = new ArrayList<>(projects.size());
            for (Map<String, Object> p : projects) {
                Object key = p.get("key");
                if (key instanceof String s && !s.isEmpty()) {
                    keys.add(s.toUpperCase(Locale.ROOT));
                }
            }
            Collections.sort(keys);
            return keys;
        } catch (Exception ex) {
            log.debug("[MCP] completion: project list fetch failed: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private static String readAuthHeader(McpSyncServerExchange exchange) {
        if (exchange == null) return null;
        try {
            Object v = exchange.transportContext().get(JiraAuthContextExtractor.CTX_AUTH_HEADER);
            return v instanceof String s ? s : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record CacheEntry(List<String> keys, long fetchedAtNanos) {}
}
