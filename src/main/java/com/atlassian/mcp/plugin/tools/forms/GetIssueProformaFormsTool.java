package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class GetIssueProformaFormsTool extends TypedTool<GetIssueProformaFormsTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key, e.g. 'PROJ-123'", required = true) String issueKey) {}

  private final JiraRestClient client;

  public GetIssueProformaFormsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_proforma_forms";
  }

  @Override
  public String description() {
    return "List the ProForma forms attached to a Jira issue. Each form carries the UUID that"
        + " get_proforma_form_details and update_proforma_form_answers take.";
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
    return client.get(
        "/rest/api/2/issue/" + args.issueKey() + "/properties/proforma.forms",
        context.authHeader());
  }
}
