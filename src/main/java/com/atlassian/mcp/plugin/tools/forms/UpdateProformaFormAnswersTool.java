package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class UpdateProformaFormAnswersTool extends TypedTool<UpdateProformaFormAnswersTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key, e.g. 'PROJ-123'", required = true) String issueKey,
      @ToolArg(
              value = "ProForma form UUID, e.g. '1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a'",
              required = true)
          String formId,
      @ToolArg(
              value =
                  "The answers to write, one object per question. Each carries 'questionId' (the"
                      + " question's id on the form), 'type' (TEXT, NUMBER, DATE, DATETIME,"
                      + " SELECT, MULTI_SELECT or CHECKBOX) and 'value', whose JSON type follows"
                      + " the answer type — a string, a number, a boolean, or an array for"
                      + " MULTI_SELECT.",
              required = true)
          List<Map<String, Object>> answers) {}

  /** ProForma stores DATE and DATETIME answers as epoch milliseconds. */
  private static final List<Function<String, Instant>> ISO_8601_FORMS =
      List.of(
          Instant::parse,
          text -> OffsetDateTime.parse(text).toInstant(),
          text -> LocalDateTime.parse(text).toInstant(ZoneOffset.UTC),
          text -> LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant());

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public UpdateProformaFormAnswersTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "update_proforma_form_answers";
  }

  @Override
  public String description() {
    return "Write answers to a ProForma form on a Jira issue. A DATE or DATETIME answer may be"
        + " given either as epoch milliseconds or as an ISO 8601 string such as '2024-12-17' or"
        + " '2024-12-17T19:00:00Z', which is converted before the call. The Forms API keeps a"
        + " DATETIME to the day and resets the time to midnight, so when the time of day matters"
        + " set the question's underlying custom field with update_issue instead — the form"
        + " details name that field on each question.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.jira.plugins.jira-proforma-plugin";
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    if (args.answers().isEmpty()) {
      throw new McpToolException("'answers' must carry at least one answer");
    }

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("form_id", args.formId());
    requestBody.put("answers", withEpochDates(args.answers()));

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.put(
        "/rest/api/2/issue/" + args.issueKey() + "/properties/proforma.forms",
        body,
        context.authHeader());
  }

  private static List<Map<String, Object>> withEpochDates(List<Map<String, Object>> answers) {
    List<Map<String, Object>> converted = new ArrayList<>();
    for (Map<String, Object> answer : answers) {
      Map<String, Object> copy = new LinkedHashMap<>(answer);
      if (isDate(copy.get("type")) && copy.get("value") instanceof String text) {
        copy.put("value", epochMillis(text));
      }
      converted.add(copy);
    }
    return converted;
  }

  private static boolean isDate(Object type) {
    String name = String.valueOf(type).toUpperCase(Locale.ROOT);
    return "DATE".equals(name) || "DATETIME".equals(name);
  }

  /** Leaves a value it cannot read alone, so Jira reports what is wrong with it. */
  private static Object epochMillis(String text) {
    for (Function<String, Instant> form : ISO_8601_FORMS) {
      try {
        return form.apply(text).toEpochMilli();
      } catch (DateTimeParseException e) {
        // Not this ISO 8601 form; try the next.
      }
    }
    return text;
  }
}
