package com.atlassian.mcp.plugin.tools;

import static org.junit.Assert.*;

import com.atlassian.mcp.plugin.McpToolException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class DeclarativeToolTest {

  private static final ToolParam<String> KEY =
      ToolParam.string("issue_key", "Jira issue key").required();
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer("limit", "Maximum results").withDefault(10);
  private static final ToolParam<Boolean> EXPAND = ToolParam.bool("expand", "Expand fields");
  private static final ToolParam<String> UNDECLARED = ToolParam.string("nope", "Not declared");

  /** Records what it read so the assertions can inspect it. */
  private static final class Probe extends DeclarativeTool {
    String key;
    Integer limit;
    Boolean expand;

    @Override
    public String name() {
      return "probe";
    }

    @Override
    public String description() {
      return "probe";
    }

    @Override
    public boolean isWriteTool() {
      return false;
    }

    @Override
    public List<ToolParam<?>> params() {
      return List.of(KEY, LIMIT, EXPAND);
    }

    @Override
    public String run(ToolArgs args, String authHeader) throws McpToolException {
      key = args.require(KEY);
      limit = args.get(LIMIT);
      expand = args.get(EXPAND);
      return "ok";
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaIsGeneratedFromDeclaration() {
    Map<String, Object> schema = new Probe().inputSchema();

    assertEquals("object", schema.get("type"));
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");
    assertEquals(Set.of("issue_key", "limit", "expand"), props.keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));

    Map<String, Object> limit = (Map<String, Object>) props.get("limit");
    assertEquals("integer", limit.get("type"));
    assertEquals("Maximum results", limit.get("description"));
    assertEquals(10, limit.get("default"));
  }

  @Test
  public void readsCoerceStringsClientsActuallySend() throws Exception {
    Probe probe = new Probe();
    probe.execute(Map.of("issue_key", "PROJ-1", "limit", "25", "expand", "true"), null);

    assertEquals("PROJ-1", probe.key);
    assertEquals(Integer.valueOf(25), probe.limit);
    assertEquals(Boolean.TRUE, probe.expand);
  }

  @Test
  public void absentValueFallsBackToDeclaredDefault() throws Exception {
    Probe probe = new Probe();
    probe.execute(Map.of("issue_key", "PROJ-1"), null);

    assertEquals(Integer.valueOf(10), probe.limit);
    assertEquals(Boolean.FALSE, probe.expand);
  }

  @Test
  public void uncoercibleValueFallsBackRatherThanThrowing() throws Exception {
    Probe probe = new Probe();
    probe.execute(Map.of("issue_key", "PROJ-1", "limit", "not-a-number"), null);

    assertEquals(Integer.valueOf(10), probe.limit);
  }

  @Test
  public void requiredParamMissingIsRejected() {
    try {
      new Probe().execute(Map.of(), null);
      fail("expected McpToolException for missing issue_key");
    } catch (McpToolException e) {
      assertTrue(e.getMessage().contains("issue_key"));
    }
  }

  @Test
  public void blankRequiredParamIsRejected() {
    try {
      new Probe().execute(Map.of("issue_key", "   "), null);
      fail("expected McpToolException for blank issue_key");
    } catch (McpToolException e) {
      assertTrue(e.getMessage().contains("issue_key"));
    }
  }

  @Test
  public void readingAnUndeclaredParamFailsLoudly() {
    ToolArgs args = new ToolArgs(List.of(KEY), Map.of("nope", "value"));
    try {
      args.get(UNDECLARED);
      fail("expected IllegalArgumentException for undeclared param");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("nope"));
    }
  }

  @Test
  public void everyDeclaredParamIsRecordedAsRead() throws Exception {
    Probe probe = new Probe();
    ToolArgs args = new ToolArgs(probe.params(), Map.of("issue_key", "PROJ-1"));
    probe.run(args, null);

    assertEquals(Set.of("issue_key", "limit", "expand"), args.readParams());
  }
}
