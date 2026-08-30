package com.atlassian.mcp.plugin.tools.attachments;

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
import java.util.Set;

public class GetIssueImagesTool extends TypedTool<GetIssueImagesTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key, e.g. 'PROJ-123'", required = true) String issueKey) {}

  private static final Set<String> IMAGE_EXTENSIONS =
      Set.of("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JiraRestClient client;

  public GetIssueImagesTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_images";
  }

  @Override
  public String description() {
    return "List the image attachments on a Jira issue — PNG, JPEG, GIF, WebP, SVG and BMP —"
        + " dropping every other file. An attachment Jira typed as application/octet-stream is"
        + " judged by its filename extension. Each entry keeps the Jira URL the image is served"
        + " from; the bytes are not inlined.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    JsonNode issue =
        read(
            client.get(
                "/rest/api/2/issue/" + args.issueKey() + "?fields=attachment",
                context.authHeader()));

    ArrayNode images = MAPPER.createArrayNode();
    for (JsonNode attachment : issue.path("fields").path("attachment")) {
      if (isImage(attachment)) images.add(attachment);
    }

    ObjectNode result = MAPPER.createObjectNode();
    result.put("issue_key", args.issueKey());
    result.put("total", images.size());
    result.set("images", images);

    try {
      return MAPPER.writeValueAsString(result);
    } catch (IOException e) {
      throw new McpToolException("Failed to serialize the image list: " + e.getMessage());
    }
  }

  private static boolean isImage(JsonNode attachment) {
    if (attachment.path("mimeType").asText("").toLowerCase(Locale.ROOT).startsWith("image/")) {
      return true;
    }
    String filename = attachment.path("filename").asText("");
    int dot = filename.lastIndexOf('.');
    return dot >= 0
        && IMAGE_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
  }

  private static JsonNode read(String json) throws McpToolException {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new McpToolException("Jira returned an unreadable issue: " + e.getMessage());
    }
  }
}
