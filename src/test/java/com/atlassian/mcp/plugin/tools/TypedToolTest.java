package com.atlassian.mcp.plugin.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.ResourceContextBuilder;
import com.atlassian.mcp.plugin.ResourceRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.Test;

public class TypedToolTest {

  public record Args(@ToolArg(value = "The key", required = true) String issueKey) {}

  private static final class Tool extends TypedTool<Args> {
    Tool(UiBinding ui) {
      super(Args.class, ui);
    }

    @Override
    public String name() {
      return "probe";
    }

    @Override
    public String description() {
      return "Probe";
    }

    @Override
    public boolean isWriteTool() {
      return false;
    }

    @Override
    protected String run(Args args, McpContext context) throws McpToolException {
      return "{\"key\":\"" + args.issueKey() + "\"}";
    }
  }

  @Test
  public void aToolWithNoBindingAdvertisesNoWidget() {
    Tool tool = new Tool(null);

    assertNull(tool.uiResourceUri());
    assertNull(tool.iconUri());
    assertNull(tool.outputSchema());
    assertNull(tool.structuredContent(Map.of(), "{}", "jdoe", "J Doe"));
  }

  @Test
  public void aBoundToolAdvertisesTheWidgetWithoutDeclaringAnythingItself() {
    ResourceRegistry registry = mock(ResourceRegistry.class);
    when(registry.getResourceUri()).thenReturn("ui://jira/issue-card");
    ResourceContextBuilder builder = mock(ResourceContextBuilder.class);
    ObjectNode built = new ObjectMapper().createObjectNode();
    when(builder.build(anyString(), anyString(), anyString(), anyString())).thenReturn(built);

    Tool tool = new Tool(new UiBinding(registry, builder));

    assertEquals("ui://jira/issue-card", tool.uiResourceUri());
    assertNotNull(tool.iconUri());
    assertSame(UiToolDefaults.ISSUE_LIST_OUTPUT_SCHEMA, tool.outputSchema());
    assertSame(built, tool.structuredContent(Map.of(), "{}", "jdoe", "J Doe"));
  }

  @Test
  public void aBindingWithoutAContextBuilderEmitsNoStructuredContent() {
    ResourceRegistry registry = mock(ResourceRegistry.class);
    when(registry.getResourceUri()).thenReturn("ui://jira/issue-card");

    Tool tool = new Tool(new UiBinding(registry, null));

    assertEquals("ui://jira/issue-card", tool.uiResourceUri());
    assertNull(tool.structuredContent(Map.of(), "{}", "jdoe", "J Doe"));
  }

  @Test
  public void noResultMeansNoStructuredContent() {
    ResourceContextBuilder builder = mock(ResourceContextBuilder.class);
    Tool tool = new Tool(new UiBinding(mock(ResourceRegistry.class), builder));

    assertNull(tool.structuredContent(Map.of(), null, "jdoe", "J Doe"));
  }
}
