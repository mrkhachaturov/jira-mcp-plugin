package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GetLinkTypesTool implements McpTool {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JiraRestClient client;

  public GetLinkTypesTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_link_types";
  }

  @Override
  public String description() {
    return "Get all available issue link types.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "name_filter",
                Map.of(
                    "type",
                    "string",
                    "description",
                    "(Optional) Filter link types by name substring (case-insensitive)")),
        "required", List.of());
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
    String nameFilter = (String) args.get("name_filter");
    String response = client.get("/rest/api/2/issueLinkType", authHeader);

    return nameFilter == null || nameFilter.isBlank()
        ? response
        : filterByName(response, nameFilter);
  }

  /**
   * Keeps only the link types whose name contains {@code nameFilter}. Jira's issueLinkType endpoint
   * has no filter parameter and always returns every type, so the narrowing happens here. A
   * response that does not have the expected shape is passed through untouched.
   */
  static String filterByName(String response, String nameFilter) throws McpToolException {
    JsonNode root;
    try {
      root = MAPPER.readTree(response);
    } catch (IOException e) {
      return response;
    }
    if (!root.isObject() || !root.path("issueLinkTypes").isArray()) {
      return response;
    }

    String needle = nameFilter.toLowerCase(Locale.ROOT);
    ArrayNode kept = MAPPER.createArrayNode();
    for (JsonNode type : root.get("issueLinkTypes")) {
      if (type.path("name").asText("").toLowerCase(Locale.ROOT).contains(needle)) {
        kept.add(type);
      }
    }

    ObjectNode filtered = (ObjectNode) root;
    filtered.set("issueLinkTypes", kept);
    try {
      return MAPPER.writeValueAsString(filtered);
    } catch (IOException e) {
      throw new McpToolException("Failed to serialize filtered link types: " + e.getMessage());
    }
  }
}
