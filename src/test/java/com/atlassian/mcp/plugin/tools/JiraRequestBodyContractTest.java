package com.atlassian.mcp.plugin.tools;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Validates the JSON every write tool builds against Jira's own OpenAPI description.
 *
 * <p>The plugin talks to Jira over REST rather than the in-process Java API, so nothing stops a
 * tool from serialising its MCP parameter names straight into the request body. Six tools did
 * exactly that — {@code create_sprint} sent {@code board_id}/{@code start_date} where Jira accepts
 * only {@code originBoardId}/{@code startDate} — and every such call died on "Unrecognized field
 * ... not marked as ignorable" with nothing in the build noticing.
 */
public class JiraRequestBodyContractTest {

  private static final String SPEC = "/jira-openapi.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static OpenApiInteractionValidator validator;

  @BeforeClass
  public static void loadSpec() throws Exception {
    JsonNode spec;
    try (InputStream in = JiraRequestBodyContractTest.class.getResourceAsStream(SPEC)) {
      assertNotNull("Jira OpenAPI spec missing from test resources", in);
      spec = MAPPER.readTree(in);
    }
    forbidUnknownProperties(spec);
    relaxHeterogeneousMaps(spec);
    relaxRawJsonBodies(spec);
    makeOperationIdsUnique(spec);
    validator =
        OpenApiInteractionValidator.createForInlineApiSpecification(MAPPER.writeValueAsString(spec))
            .build();
  }

  /**
   * Only 4 of the spec's 332 schemas set {@code additionalProperties}, and JSON Schema permits
   * unknown properties unless a schema forbids them — so a faithful reading of the document accepts
   * bodies the server rejects outright. Jira answers "Unrecognized field ... not marked as
   * ignorable", verified against a live Data Center 11 instance, so the document is tightened here
   * to describe how the server actually behaves.
   */
  private static void forbidUnknownProperties(JsonNode node) {
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      // Only a node typed "object" is a schema; a bare properties map is not.
      if ("object".equals(object.path("type").asText())
          && object.path("properties").isObject()
          && !object.has("additionalProperties")) {
        object.put("additionalProperties", false);
      }
      object.properties().forEach(field -> forbidUnknownProperties(field.getValue()));
    } else if (node.isArray()) {
      node.forEach(JiraRequestBodyContractTest::forbidUnknownProperties);
    }
  }

  /**
   * The document types every value in {@code fields} and {@code update} as an object, but Jira
   * takes a string for {@code summary}, an array for {@code components}, and {@code add}/{@code
   * remove} operations under {@code update} — all accepted by a live Data Center 11 instance. Both
   * maps are heterogeneous by nature, so their value schemas are dropped.
   */
  private static void relaxHeterogeneousMaps(JsonNode spec) {
    JsonNode issueUpdate = spec.path("components").path("schemas").path("IssueUpdateBean");
    for (String map : List.of("fields", "update")) {
      JsonNode node = issueUpdate.path("properties").path(map);
      if (node.isObject()) {
        ((ObjectNode) node).set("additionalProperties", MAPPER.createObjectNode());
      }
    }
  }

  /**
   * An issue property holds any JSON value the caller chooses, but the document types the body of
   * {@code PUT .../properties/{propertyKey}} as {@code "type": "string", "format": "json"} — the
   * shape of the resource method's parameter, not of the payload. Read literally that rejects every
   * object body, so such a schema is left unconstrained; it names no properties, so nothing this
   * test checks is lost.
   */
  private static void relaxRawJsonBodies(JsonNode node) {
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      if ("string".equals(object.path("type").asText())
          && "json".equals(object.path("format").asText())) {
        object.removeAll();
        return;
      }
      object.properties().forEach(field -> relaxRawJsonBodies(field.getValue()));
    } else if (node.isArray()) {
      node.forEach(JiraRequestBodyContractTest::relaxRawJsonBodies);
    }
  }

  /** The published document repeats operationIds, which the loader treats as fatal. */
  private static void makeOperationIdsUnique(JsonNode spec) {
    java.util.Set<String> seen = new java.util.HashSet<>();
    JsonNode paths = spec.path("paths");
    paths
        .fieldNames()
        .forEachRemaining(
            path ->
                paths
                    .path(path)
                    .properties()
                    .forEach(
                        operation -> {
                          JsonNode node = operation.getValue();
                          if (!node.isObject() || !node.has("operationId")) return;
                          String id = node.get("operationId").asText();
                          for (int n = 2; !seen.add(id); n++) {
                            id = node.get("operationId").asText() + "_" + n;
                          }
                          ((ObjectNode) node).put("operationId", id);
                        }));
  }

  private static final List<String> AWAITING_MIGRATION = List.of("batch_create_versions");

  @Test
  public void everyWriteToolSendsABodyJiraAccepts() throws Exception {
    List<String> violations = new ArrayList<>();
    List<String> silent = new ArrayList<>();

    for (Class<?> type : ToolScan.mcpToolClasses()) {
      JiraRestClient client = mockClient();
      McpTool tool = instantiate(type, client);
      if (!tool.isWriteTool()) continue;

      List<Sent> sent = capture(tool, client);
      if (mockingDetails(client).getInvocations().isEmpty()
          && !AWAITING_MIGRATION.contains(tool.name())) {
        silent.add(tool.name());
      }
      for (Sent one : sent) {
        for (String problem : validate(one)) {
          violations.add(tool.name() + ": " + one.method + " " + one.path + " — " + problem);
        }
      }
    }

    assertEquals(
        "these write tools never reached Jira, so this check covers none of them",
        List.of(),
        silent);
    assertEquals(
        "these tools build a body Jira rejects, so the call does nothing", List.of(), violations);
  }

  private static List<String> validate(Sent sent) {
    SimpleRequest.Builder builder =
        "PUT".equals(sent.method)
            ? SimpleRequest.Builder.put(sent.path)
            : SimpleRequest.Builder.post(sent.path);
    ValidationReport report =
        validator.validateRequest(
            builder.withContentType("application/json").withBody(sent.body).build());

    List<String> problems = new ArrayList<>();
    for (ValidationReport.Message message : report.getMessages()) {
      // Path, query and header findings belong to the caller, not to the body under test; an
      // unknown operation means the endpoint lives in a spec Atlassian ships separately
      // (ServiceDesk, Proforma), which this document cannot judge.
      if (!message.getKey().startsWith("validation.request.body")) continue;
      problems.add(message.getMessage().replaceAll("\\s+", " ").trim());
    }
    return problems;
  }

  private record Sent(String method, String path, String body) {}

  /** Drives a tool with synthetic arguments and returns each POST/PUT body it produced. */
  private static List<Sent> capture(McpTool tool, JiraRestClient client) throws Exception {
    try {
      tool.execute(sampleObject(tool.inputSchema()), "Bearer test");
    } catch (McpToolException | RuntimeException e) {
      // Synthetic arguments may be rejected; whatever was sent before that still counts.
    }

    List<Sent> sent = new ArrayList<>();
    collect(client, "POST", sent);
    collect(client, "PUT", sent);
    return sent;
  }

  private static void collect(JiraRestClient client, String method, List<Sent> out)
      throws Exception {
    ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    if ("POST".equals(method)) {
      verify(client, atLeast(0)).post(path.capture(), body.capture(), any());
    } else {
      verify(client, atLeast(0)).put(path.capture(), body.capture(), any());
    }
    for (int i = 0; i < path.getAllValues().size(); i++) {
      String raw = body.getAllValues().get(i);
      if (raw == null || raw.isBlank()) continue;
      out.add(new Sent(method, path.getAllValues().get(i).split("\\?")[0], raw));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> sampleObject(Map<String, Object> schema) {
    Map<String, Object> properties =
        (Map<String, Object>) schema.getOrDefault("properties", Map.of());
    Map<String, Object> args = new LinkedHashMap<>();
    properties.forEach(
        (name, property) -> args.put(name, sampleFor(name, (Map<String, Object>) property)));
    return args;
  }

  @SuppressWarnings("unchecked")
  private static Object sampleFor(String name, Map<String, Object> property) {
    List<String> allowed = (List<String>) property.get("enum");
    if (allowed != null && !allowed.isEmpty()) return allowed.get(0);

    switch (String.valueOf(property.get("type"))) {
      case "integer":
      case "number":
        return 1;
      case "boolean":
        return Boolean.FALSE;
      case "array":
        return List.of(sampleFor(name, (Map<String, Object>) property.get("items")));
      case "object":
        return property.containsKey("properties") ? sampleObject(property) : Map.of();
      default:
        // Several tools parse a parameter as embedded JSON; an object literal survives both paths.
        if (name.contains("field") || name.contains("visibility")) return "{}";
        // A date parameter has to arrive as a date, or the finding is about this sample value
        // rather than about the property name the tool chose.
        if (name.contains("date")) return "2025-01-01T00:00:00.000+0000";
        return "TEST-1";
    }
  }

  private static JiraRestClient mockClient() throws Exception {
    JiraRestClient client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{}");
    when(client.post(anyString(), anyString(), any())).thenReturn("{\"id\":\"1\",\"key\":\"T-1\"}");
    when(client.put(anyString(), anyString(), any())).thenReturn("{}");
    return client;
  }

  private static McpTool instantiate(Class<?> type, JiraRestClient client) throws Exception {
    Constructor<?> best = null;
    for (Constructor<?> candidate : type.getConstructors()) {
      if (best == null || candidate.getParameterCount() < best.getParameterCount()) {
        best = candidate;
      }
    }
    Object[] arguments = new Object[best.getParameterCount()];
    Class<?>[] types = best.getParameterTypes();
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = types[i] == JiraRestClient.class ? client : mock(types[i]);
    }
    return (McpTool) best.newInstance(arguments);
  }
}
