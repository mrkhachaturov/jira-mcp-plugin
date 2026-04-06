package com.atlassian.mcp.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Transforms Jira REST API JSON responses to match the upstream mcp-atlassian
 * Python project's to_simplified_dict() output format.
 *
 * The upstream uses Pydantic models with whitelist-based serialization
 * (model_dump(exclude_none=True)). Since our plugin receives raw Jira JSON
 * and doesn't have Pydantic models, we approximate by:
 *   1. Recursively stripping fields the upstream models never include
 *      (self links, avatar URLs, icon URLs, expand metadata)
 *   2. Stripping top-level response metadata the upstream never returns
 *      (renderedFields, editmeta, changelog, operations, names, schema)
 *   3. Simplifying nested objects the way upstream does (e.g., extracting
 *      "name" from {id, name, self, description, iconUrl} objects)
 *
 * This mirrors upstream behavior while working with raw JSON.
 */
public final class ResponseTrimmer {

    private ResponseTrimmer() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Fields to remove recursively from ALL JSON objects.
     * Upstream Pydantic models never serialize these.
     */
    private static final Set<String> STRIP_RECURSIVE = Set.of(
            "avatarUrls",
            "self",
            "iconUrl",
            "expand",
            "48x48", "32x32", "24x24", "16x16",
            // Empty container fields — upstream models never include these
            "applicationRoles",
            "groups"
    );

    /**
     * Top-level fields to remove from issue/search responses.
     * Upstream's to_simplified_dict() never includes these.
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
     * Fields in the upstream JiraIssue model that get renamed.
     * Upstream: issuetype → issue_type, fixVersions → fix_versions, etc.
     */
    private static final Map<String, String> RENAME_FIELDS = Map.of(
            "issuetype", "issue_type",
            "fixVersions", "fix_versions"
    );

    /**
     * Trim a JSON response string to match upstream's simplified output.
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

            // Rename fields to match upstream model naming
            for (var entry : RENAME_FIELDS.entrySet()) {
                JsonNode val = obj.remove(entry.getKey());
                if (val != null) {
                    obj.set(entry.getValue(), val);
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
