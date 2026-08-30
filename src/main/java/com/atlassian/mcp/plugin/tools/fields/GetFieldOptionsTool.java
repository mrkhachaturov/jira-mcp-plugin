package com.atlassian.mcp.plugin.tools.fields;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class GetFieldOptionsTool extends TypedTool<GetFieldOptionsTool.Args> {

  public record Args(
      @ToolArg(
              value =
                  "Field id, e.g. 'customfield_10001', 'priority' or 'components'. Use"
                      + " search_fields to find a custom field's id.",
              required = true)
          String fieldId,
      @ToolArg(value = "Project key, e.g. 'PROJ'", required = true) String projectKey,
      @ToolArg(value = "Issue type name as shown in the project, e.g. 'Bug'", required = true)
          String issueType,
      @ToolArg(
              "(Optional) Case-insensitive substring filter on option values. Also matches child"
                  + " values in cascading selects.")
          String contains,
      @ToolArg(
              value =
                  "(Optional) Maximum number of options to return, applied after filtering. 0"
                      + " returns all.",
              defaultValue = "0")
          int returnLimit,
      @ToolArg(
              value =
                  "If true, return only the option value strings instead of full option objects.",
              defaultValue = "false")
          boolean valuesOnly) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JiraRestClient client;

  public GetFieldOptionsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_field_options";
  }

  @Override
  public String description() {
    return "Get allowed option values for a field on a given project and issue type. Returns the list of valid options for select, multi-select, radio, checkbox, cascading select, and other constrained fields (priority, components, versions). Options are read from the create metadata for the project/issue type combination, so they reflect what Jira will actually accept on that screen.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    String issueTypeId =
        resolveIssueTypeId(args.projectKey(), args.issueType(), context.authHeader());
    ArrayNode options =
        readAllowedValues(args.projectKey(), issueTypeId, args.fieldId(), context.authHeader());

    ArrayNode filtered = filter(options, args.contains(), args.returnLimit());
    return serialize(args, filtered);
  }

  /** Maps an issue type name to its id, which the create-metadata field endpoint requires. */
  private String resolveIssueTypeId(String projectKey, String issueType, String authHeader)
      throws McpToolException {
    JsonNode types =
        read(
            client.get(
                "/rest/api/2/issue/createmeta/" + encode(projectKey) + "/issuetypes", authHeader));

    StringBuilder known = new StringBuilder();
    for (JsonNode type : types.path("values")) {
      String name = type.path("name").asText("");
      if (name.equalsIgnoreCase(issueType)) return type.path("id").asText();
      if (known.length() > 0) known.append(", ");
      known.append(name);
    }
    throw new McpToolException(
        "Issue type '"
            + issueType
            + "' not found in project '"
            + projectKey
            + "'. Available: "
            + known);
  }

  private ArrayNode readAllowedValues(
      String projectKey, String issueTypeId, String fieldId, String authHeader)
      throws McpToolException {
    JsonNode fields =
        read(
            client.get(
                "/rest/api/2/issue/createmeta/"
                    + encode(projectKey)
                    + "/issuetypes/"
                    + encode(issueTypeId),
                authHeader));

    for (JsonNode field : fields.path("values")) {
      if (fieldId.equals(field.path("fieldId").asText())) {
        JsonNode allowed = field.path("allowedValues");
        if (!allowed.isArray()) {
          throw new McpToolException(
              "Field '"
                  + fieldId
                  + "' has no constrained option list on this screen — it accepts free input.");
        }
        return (ArrayNode) allowed;
      }
    }
    throw new McpToolException(
        "Field '"
            + fieldId
            + "' is not on the create screen for issue type "
            + issueTypeId
            + " in project '"
            + projectKey
            + "'.");
  }

  /**
   * Applies the substring filter and the result cap. A cascading option matches when either its own
   * value or any of its children match, and matching children are kept.
   */
  private static ArrayNode filter(ArrayNode options, String contains, int returnLimit) {
    ArrayNode kept = MAPPER.createArrayNode();
    String needle = contains == null ? null : contains.toLowerCase(Locale.ROOT);

    for (JsonNode option : options) {
      if (returnLimit > 0 && kept.size() >= returnLimit) break;
      if (needle == null) {
        kept.add(option);
        continue;
      }
      boolean selfMatches = valueOf(option).toLowerCase(Locale.ROOT).contains(needle);
      ArrayNode children = matchingChildren(option, needle);
      if (selfMatches) {
        kept.add(option);
      } else if (children.size() > 0) {
        ObjectNode narrowed = option.deepCopy();
        narrowed.set("children", children);
        kept.add(narrowed);
      }
    }
    return kept;
  }

  private static ArrayNode matchingChildren(JsonNode option, String needle) {
    ArrayNode matches = MAPPER.createArrayNode();
    for (JsonNode child : option.path("children")) {
      if (valueOf(child).toLowerCase(Locale.ROOT).contains(needle)) matches.add(child);
    }
    return matches;
  }

  /** Jira names an option's label 'value' for custom fields and 'name' for system fields. */
  private static String valueOf(JsonNode option) {
    String value = option.path("value").asText(null);
    return value != null ? value : option.path("name").asText("");
  }

  private static String serialize(Args args, ArrayNode options) throws McpToolException {
    ObjectNode result = MAPPER.createObjectNode();
    result.put("field_id", args.fieldId());
    result.put("project_key", args.projectKey());
    result.put("issue_type", args.issueType());
    result.put("total", options.size());

    if (args.valuesOnly()) {
      ArrayNode values = MAPPER.createArrayNode();
      for (JsonNode option : options) values.add(valueOf(option));
      result.set("values", values);
    } else {
      result.set("options", options);
    }

    try {
      return MAPPER.writeValueAsString(result);
    } catch (IOException e) {
      throw new McpToolException("Failed to serialize field options: " + e.getMessage());
    }
  }

  private static JsonNode read(String json) throws McpToolException {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new McpToolException(
          "Jira returned an unreadable create-metadata response: " + e.getMessage());
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
