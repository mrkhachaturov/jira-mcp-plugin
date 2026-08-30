package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetProformaFormDetailsTool extends TypedTool<GetProformaFormDetailsTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key, e.g. 'PROJ-123'", required = true) String issueKey,
      @ToolArg(
              value = "ProForma form UUID, e.g. '1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a'",
              required = true)
          String formId) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JiraRestClient client;

  public GetProformaFormDetailsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_proforma_form_details";
  }

  @Override
  public String description() {
    return "Get one ProForma form attached to a Jira issue, selected by its UUID. Returns that"
        + " form alone — its design, questions and current answers — rather than every form on"
        + " the issue.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.jira.plugins.jira-proforma-plugin";
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    JsonNode property =
        read(
            client.get(
                "/rest/api/2/issue/" + args.issueKey() + "/properties/proforma.forms",
                context.authHeader()));

    List<String> known = new ArrayList<>();
    for (JsonNode form : formsIn(property, args.issueKey())) {
      String id = idOf(form);
      if (args.formId().equalsIgnoreCase(id)) return form.toString();
      known.add(id);
    }
    throw new McpToolException(
        "Form '"
            + args.formId()
            + "' is not attached to "
            + args.issueKey()
            + ". Attached form ids: "
            + String.join(", ", known));
  }

  /** ProForma keeps the forms either directly in the property value or under a 'forms' array. */
  private static JsonNode formsIn(JsonNode property, String issueKey) throws McpToolException {
    JsonNode value = property.path("value");
    if (value.isArray()) return value;
    if (value.path("forms").isArray()) return value.path("forms");
    throw new McpToolException(
        "The proforma.forms property on " + issueKey + " holds no list of forms.");
  }

  /** ProForma has named a form's identifier both 'id' and 'uuid' across its releases. */
  private static String idOf(JsonNode form) {
    String id = form.path("id").asText("");
    return id.isEmpty() ? form.path("uuid").asText("") : id;
  }

  private static JsonNode read(String json) throws McpToolException {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new McpToolException("Jira returned an unreadable form property: " + e.getMessage());
    }
  }
}
