package com.atlassian.mcp.plugin.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.atlassian.mcp.plugin.McpToolException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ToolArgsBinderTest {

  public record Fixture(
      @ToolArg(value = "The key", required = true) String issueKey,
      @ToolArg(value = "How many", defaultValue = "10") int limit,
      @ToolArg(value = "Whether to update", defaultValue = "true") boolean updateHistory,
      @ToolArg(
              value = "The kind",
              allowed = {"scrum", "kanban"})
          String boardType,
      @ToolArg("Component names") List<String> components,
      @ToolArg("Extra fields") Map<String, Object> additionalFields) {}

  private static Fixture bind(Map<String, Object> args) throws McpToolException {
    return ToolArgsBinder.bind(Fixture.class, args);
  }

  @Test
  public void declaredDefaultsApplyWhenAParameterIsAbsent() throws Exception {
    Fixture bound = bind(Map.of("issue_key", "PROJ-1"));

    assertEquals(10, bound.limit());
    assertEquals(true, bound.updateHistory());
    assertNull(bound.boardType());
    assertNull(bound.components());
  }

  @Test
  public void aMissingRequiredParameterIsReported() {
    McpToolException thrown = assertThrows(McpToolException.class, () -> bind(Map.of()));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("issue_key"));
  }

  @Test
  public void anUnknownParameterIsReportedWithTheAcceptedNames() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class,
            () -> bind(Map.of("issue_key", "PROJ-1", "issueKey", "PROJ-2")));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("issueKey"));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("issue_key"));
  }

  @Test
  public void aValueOutsideTheDeclaredEnumIsReported() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class,
            () -> bind(Map.of("issue_key", "PROJ-1", "board_type", "waterfall")));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("board_type"));
  }

  @Test
  public void numbersAndBooleansArrivingAsStringsAreAccepted() throws Exception {
    Fixture bound = bind(Map.of("issue_key", "PROJ-1", "limit", "25", "update_history", "false"));

    assertEquals(25, bound.limit());
    assertEquals(false, bound.updateHistory());
  }

  @Test
  public void aValueThatCannotBecomeItsDeclaredTypeIsReported() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class, () -> bind(Map.of("issue_key", "PROJ-1", "limit", "many")));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("limit"));
  }

  @Test
  public void aBlankStringIsTreatedAsAbsentSoTheDefaultStillApplies() throws Exception {
    assertEquals(10, bind(Map.of("issue_key", "PROJ-1", "limit", "  ")).limit());
  }

  @Test
  public void anExplicitNullIsTreatedAsAbsent() throws Exception {
    Map<String, Object> args = new HashMap<>();
    args.put("issue_key", "PROJ-1");
    args.put("board_type", null);

    assertNull(bind(args).boardType());
  }

  @Test
  public void aListArrivesAsAnArray() throws Exception {
    Fixture bound = bind(Map.of("issue_key", "PROJ-1", "components", List.of("Frontend", "API")));

    assertEquals(List.of("Frontend", "API"), bound.components());
  }

  @Test
  public void aSingleValueIsAcceptedWhereAListIsDeclared() throws Exception {
    assertEquals(
        List.of("Frontend"),
        bind(Map.of("issue_key", "PROJ-1", "components", "Frontend")).components());
  }

  @Test
  public void anObjectArrivesAsAMap() throws Exception {
    Fixture bound =
        bind(
            Map.of(
                "issue_key", "PROJ-1", "additional_fields", Map.of("labels", List.of("urgent"))));

    assertEquals(Map.of("labels", List.of("urgent")), bound.additionalFields());
  }

  @Test
  public void aNullArgumentMapLeavesOnlyTheRequiredComplaint() {
    McpToolException thrown = assertThrows(McpToolException.class, () -> bind(null));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("issue_key"));
  }
}
