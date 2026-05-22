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
 * autocompletion.
 *
 * <p>Per the MCP 2025-11-25 spec (server/utilities/completion.mdx) and the
 * SDK's dispatcher (McpAsyncServer line 1003-1067), completion targets MUST
 * be one of:
 * <ul>
 *   <li>{@code ref/prompt}: a registered prompt's argument, OR
 *   <li>{@code ref/resource}: a registered resource template's URI variable.
 * </ul>
 * We have no prompts, so we wire completion against the
 * {@code jira://issue/{issueKey}} resource template (registered by
 * {@link ResourceRegistry#toResourceTemplateSpecifications()} for F-10).
 * The {@code issueKey} URI variable accepts prefix-completion to a Jira
 * project key — typing "PR" yields {@code PROJ-}, {@code PROD-}, etc.,
 * which is the high-LLM-accuracy win the audit's F-07 was after (every
 * Jira issue key starts with a project key).
 *
 * <p>Per-project status / issue_type / assignee completions remain deferred:
 * they require per-project context that the spec's completion request shape
 * doesn't carry, and would need stateful per-project caches or per-keystroke
 * JQL roundtrips.
 *
 * <p>The Jira project list is cached for 60 seconds keyed on the auth header
 * (per-user view) to avoid hammering Jira's API on every keystroke.
 */
@Named
public class CompletionRegistry {

    private static final Logger log = LoggerFactory.getLogger(CompletionRegistry.class);

    /** URI of the resource template that completion is wired to (must match
     *  {@link ResourceRegistry#toResourceTemplateSpecifications()}). */
    static final String ISSUE_TEMPLATE_URI = "jira://issue/{issueKey}";

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
     * <p>One spec, targeting the {@code issueKey} URI variable of the
     * {@code jira://issue/{issueKey}} resource template. Returns up to 20
     * project-key prefixes that match the typed value — issue keys always
     * begin with a project key, so suggesting {@code PROJ-} for prefix
     * {@code PR} is the right completion.
     */
    public List<SyncCompletionSpecification> toSpecifications() {
        BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> handler =
                (exchange, request) -> handleIssueKey(exchange, request);

        return List.of(
                new SyncCompletionSpecification(
                        new McpSchema.ResourceReference(ISSUE_TEMPLATE_URI),
                        handler)
        );
    }

    /**
     * Completion handler for the {@code issueKey} URI variable of
     * {@code jira://issue/{issueKey}}. Returns up to {@value #MAX_RESULTS}
     * project-key prefixes (e.g. {@code PROJ-}) that match the typed value.
     */
    private CompleteResult handleIssueKey(McpSyncServerExchange exchange, CompleteRequest request) {
        String prefix = request.argument() != null && request.argument().value() != null
                ? request.argument().value().trim().toUpperCase(Locale.ROOT)
                : "";

        // Strip everything from the first '-' onward — completion targets the
        // project-key prefix of an issue key, not the numeric tail.
        int dash = prefix.indexOf('-');
        if (dash >= 0) {
            prefix = prefix.substring(0, dash);
        }

        String authHeader = readAuthHeader(exchange);
        List<String> keys = projectKeys(authHeader);

        List<String> matches = new ArrayList<>(MAX_RESULTS);
        for (String key : keys) {
            if (prefix.isEmpty() || key.startsWith(prefix)) {
                matches.add(key + "-");
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
