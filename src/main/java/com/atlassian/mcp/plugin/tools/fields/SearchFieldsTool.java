package com.atlassian.mcp.plugin.tools.fields;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mirrors upstream mcp-atlassian search_fields: fuzzy keyword matching + limit.
 * Upstream uses fuzzywuzzy partial_ratio; we approximate with substring + prefix scoring.
 */
public class SearchFieldsTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public SearchFieldsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "search_fields"; }

    @Override
    public String description() {
        return "Search Jira fields by keyword with fuzzy match.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "Keyword for fuzzy search. If left empty, lists the first 'limit' available fields in their default order.", "default", ""),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results", "default", 10),
                        "refresh", Map.of("type", "boolean", "description", "Whether to force refresh the field list", "default", false)
                ),
                "required", List.of()
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String keyword = (String) args.getOrDefault("keyword", "");
        int limit = getInt(args, "limit", 10);

        // Fetch all fields from Jira
        String raw = client.get("/rest/api/2/field", authHeader);

        try {
            List<Map<String, Object>> fields = mapper.readValue(raw,
                    new TypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> result;

            if (keyword == null || keyword.isBlank()) {
                // No keyword — return first `limit` fields
                result = fields.stream().limit(limit).collect(Collectors.toList());
            } else {
                // Fuzzy search: score each field by keyword relevance, sort descending
                String needle = keyword.toLowerCase();
                result = fields.stream()
                        .map(f -> Map.entry(f, similarity(needle, f)))
                        .filter(e -> e.getValue() > 0)
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .limit(limit)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }

            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new McpToolException("Failed to process field search: " + e.getMessage());
        }
    }

    /**
     * Score a field against a keyword. Higher = better match.
     * Checks id, key, name, and clauseNames — same candidates as upstream.
     */
    private static int similarity(String needle, Map<String, Object> field) {
        int best = 0;
        best = Math.max(best, score(needle, str(field.get("id"))));
        best = Math.max(best, score(needle, str(field.get("key"))));
        best = Math.max(best, score(needle, str(field.get("name"))));

        Object clauses = field.get("clauseNames");
        if (clauses instanceof List<?> list) {
            for (Object c : list) {
                best = Math.max(best, score(needle, str(c)));
            }
        }
        return best;
    }

    /**
     * Simple fuzzy score: exact match > starts with > contains > no match.
     * Approximates fuzzywuzzy partial_ratio behavior.
     */
    private static int score(String needle, String candidate) {
        if (candidate.isEmpty()) return 0;
        String lower = candidate.toLowerCase();
        if (lower.equals(needle)) return 100;
        if (lower.startsWith(needle)) return 80;
        if (lower.contains(needle)) return 60;
        // Check if needle words appear in candidate
        if (needle.length() > 2) {
            String[] words = needle.split("[_\\s-]+");
            boolean allFound = true;
            for (String w : words) {
                if (!lower.contains(w)) { allFound = false; break; }
            }
            if (allFound) return 40;
        }
        return 0;
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private static int getInt(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}
