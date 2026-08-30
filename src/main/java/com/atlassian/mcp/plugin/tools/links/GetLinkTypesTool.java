package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Locale;

public class GetLinkTypesTool extends TypedTool<GetLinkTypesTool.Args> {

  public record Args(
      @ToolArg("(Optional) Keep only link types whose name contains this text, case-insensitively")
          String nameFilter) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JiraRestClient client;

  public GetLinkTypesTool(JiraRestClient client) {
    super(Args.class);
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
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    String response = client.get("/rest/api/2/issueLinkType", context.authHeader());

    return args.nameFilter() == null ? response : filterByName(response, args.nameFilter());
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
