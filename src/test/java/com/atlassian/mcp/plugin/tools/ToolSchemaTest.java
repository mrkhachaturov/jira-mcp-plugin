package com.atlassian.mcp.plugin.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ToolSchemaTest {

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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> propertyOf(Class<?> type, String name) {
    Map<String, Object> properties = (Map<String, Object>) ToolSchema.of(type).get("properties");
    return (Map<String, Object>) properties.get(name);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void componentNamesBecomeSnakeCase() {
    Map<String, Object> properties =
        (Map<String, Object>) ToolSchema.of(Fixture.class).get("properties");

    assertEquals(
        List.of(
            "issue_key",
            "limit",
            "update_history",
            "board_type",
            "components",
            "additional_fields"),
        List.copyOf(properties.keySet()));
  }

  @Test
  public void onlyAnnotatedRequiredComponentsAreRequired() {
    assertEquals(List.of("issue_key"), ToolSchema.of(Fixture.class).get("required"));
  }

  @Test
  public void additionalPropertiesAreRefused() {
    assertEquals(Boolean.FALSE, ToolSchema.of(Fixture.class).get("additionalProperties"));
  }

  @Test
  public void defaultsCarryTheTypeOfTheirComponent() {
    assertEquals(10, propertyOf(Fixture.class, "limit").get("default"));
    assertEquals(true, propertyOf(Fixture.class, "update_history").get("default"));
  }

  @Test
  public void javaTypesMapOntoJsonSchemaTypes() {
    assertEquals("string", propertyOf(Fixture.class, "issue_key").get("type"));
    assertEquals("integer", propertyOf(Fixture.class, "limit").get("type"));
    assertEquals("boolean", propertyOf(Fixture.class, "update_history").get("type"));
    assertEquals("object", propertyOf(Fixture.class, "additional_fields").get("type"));

    Map<String, Object> components = propertyOf(Fixture.class, "components");
    assertEquals("array", components.get("type"));
    assertEquals(Map.of("type", "string"), components.get("items"));
  }

  @Test
  public void allowedValuesBecomeAnEnum() {
    assertEquals(List.of("scrum", "kanban"), propertyOf(Fixture.class, "board_type").get("enum"));
  }

  @Test
  public void anOptionalComponentCarriesNoDefaultKey() {
    assertFalse(propertyOf(Fixture.class, "board_type").containsKey("default"));
  }

  public record MissingAnnotation(String issueKey) {}

  @Test
  public void aComponentWithoutToolArgIsRejected() {
    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> ToolSchema.of(MissingAnnotation.class));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("issueKey"));
  }

  public record UnsatisfiablePrimitive(@ToolArg("How many") int limit) {}

  @Test
  public void aPrimitiveThatCanNeverBeSuppliedIsRejected() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> ToolSchema.of(UnsatisfiablePrimitive.class));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("limit"));
  }

  @Test
  public void aNonRecordIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> ToolSchema.of(String.class));
  }

  public record Child(@ToolArg(value = "Name", required = true) String name) {}

  public record Parent(
      @ToolArg("One child") Child child, @ToolArg("Several children") List<Child> children) {}

  @Test
  @SuppressWarnings("unchecked")
  public void aNestedRecordBecomesAnObjectSchema() {
    Map<String, Object> child = propertyOf(Parent.class, "child");

    assertEquals("object", child.get("type"));
    assertEquals(Boolean.FALSE, child.get("additionalProperties"));
    assertEquals(List.of("name"), child.get("required"));
    assertEquals(Set.of("name"), ((Map<String, Object>) child.get("properties")).keySet());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void aListOfRecordsDescribesItsElement() {
    Map<String, Object> children = propertyOf(Parent.class, "children");
    Map<String, Object> item = (Map<String, Object>) children.get("items");

    assertEquals("array", children.get("type"));
    assertEquals("object", item.get("type"));
    assertEquals(List.of("name"), item.get("required"));
  }

  public record SelfReferencing(@ToolArg("Itself") SelfReferencing inner) {}

  @Test
  public void aRecordThatContainsItselfIsRejected() {
    assertThrows(IllegalStateException.class, () -> ToolSchema.of(SelfReferencing.class));
  }

  public record EnumOverAList(
      @ToolArg(
              value = "Sprint states",
              allowed = {"future", "active"})
          List<String> states) {}

  @Test
  public void anEnumOverListElementsIsRejectedRatherThanSilentlyNeverMatching() {
    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> ToolSchema.of(EnumOverAList.class));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("states"));
  }
}
