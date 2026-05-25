package com.atlassian.mcp.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Trims Jira REST API JSON responses to reduce payload size for AI agents:
 *   1. Recursively strips fields agents don't need (avatar URLs, icon URLs,
 *      expand metadata, group/role containers).
 *   2. Strips top-level response metadata (renderedFields, editmeta,
 *      changelog, operations, names, schema).
 *   3. Simplifies nested objects (e.g. extracts "name" from
 *      {id, name, self, description, iconUrl} objects).
 */
public final class ResponseTrimmer {

    private ResponseTrimmer() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Fields to remove recursively from ALL JSON objects.
     *
     * Note: "self" is kept — JiraIssueLinkType responses need it, and the
     * 48x48 avatar URL is converted to "avatar_url" for user objects. Removing
     * "self" globally would break link type and user responses. Individual
     * avatar dimension keys (48x48 etc.) inside "avatarUrls" are stripped along
     * with the "avatarUrls" container itself.
     */
    private static final Set<String> STRIP_RECURSIVE = Set.of(
            "avatarUrls",
            "iconUrl",
            "expand",
            "48x48", "32x32", "24x24", "16x16",
            // Empty container fields agents never need
            "applicationRoles",
            "groups"
    );

    /**
     * Top-level fields to remove from issue/search responses.
     */
    private static final Set<String> STRIP_TOP_LEVEL = Set.of(
            "renderedFields",
            "names",
            "schema",
            "editmeta",
            "versionedRepresentations",
            "operations"
    );

    /**
     * Field renames applied to issue payloads for naming consistency.
     */
    private static final Map<String, String> RENAME_FIELDS = Map.of(
            "issuetype", "issue_type",
            "fixVersions", "fix_versions"
    );

    /**
     * Text fields that contain Jira wiki markup and should be converted
     * to Markdown for AI agent consumption.
     */
    private static final Set<String> WIKI_MARKUP_FIELDS = Set.of(
            "description",
            "body",
            "environment"
    );

    /**
     * Trim a JSON response string to the simplified output shape.
     * Returns the original string unchanged if it's not valid JSON.
     */
    public static String trim(String json) {
        if (json == null || json.isBlank()) return json;
        try {
            JsonNode root = MAPPER.readTree(json);
            trimNode(root, true);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return json;
        }
    }

    private static void trimNode(JsonNode node, boolean isTopLevel) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;

            // Remove blacklisted fields
            Iterator<String> names = obj.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (STRIP_RECURSIVE.contains(name)) {
                    names.remove();
                } else if (isTopLevel && STRIP_TOP_LEVEL.contains(name)) {
                    names.remove();
                }
            }

            // Apply field renames
            for (var entry : RENAME_FIELDS.entrySet()) {
                JsonNode val = obj.remove(entry.getKey());
                if (val != null) {
                    obj.set(entry.getValue(), val);
                }
            }

            // Convert wiki markup text fields to Markdown
            for (String field : WIKI_MARKUP_FIELDS) {
                JsonNode val = obj.get(field);
                if (val != null && val.isTextual()) {
                    String converted = JiraMarkupConverter.jiraToMarkdown(val.asText());
                    obj.put(field, converted);
                }
            }

            // Recurse into remaining children
            obj.fields().forEachRemaining(e -> trimNode(e.getValue(), false));

            // Simplify nested "fields" object in issue responses
            if (isTopLevel && obj.has("fields") && obj.get("fields").isObject()) {
                ObjectNode fields = (ObjectNode) obj.get("fields");
                // Apply renames inside fields too
                for (var entry : RENAME_FIELDS.entrySet()) {
                    JsonNode val = fields.remove(entry.getKey());
                    if (val != null) {
                        fields.set(entry.getValue(), val);
                    }
                }
            }

        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                trimNode(arr.get(i), false);
            }
        }
    }
}
