# Authoring an MCP tool

Every tool extends `TypedTool<A>` and declares its parameters as a record. The
advertised JSON Schema is derived from that record, and the same record is what
`run` receives, so the schema and the code that reads arguments are one
declaration.

## The shape

```java
public class CreateIssueTool extends TypedTool<CreateIssueTool.Args> {

  public record Args(
      @ToolArg(value = "The project key, e.g. 'PROJ'", required = true) String projectKey,
      @ToolArg(value = "Maximum number of results", defaultValue = "10") int limit,
      @ToolArg(value = "Board kind", allowed = {"scrum", "kanban"}) String boardType,
      @ToolArg("Component names") List<String> components,
      @ToolArg("Fields to set verbatim") Map<String, Object> additionalFields) {}

  public CreateIssueTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.post("/rest/api/2/issue", body, context.authHeader());
  }
}
```

`inputSchema()`, `execute(...)`, `executeWithProgress(...)` and
`executeWithSdkProgress(...)` are final on `TypedTool`. `run` is the only entry
point a tool implements.

## Rules

**Names.** A component's name becomes the wire name through Jackson's snake-case
strategy: `projectKey` is advertised as `project_key`. Never write the wire name
as a string literal.

**Types.** Declare the type the parameter actually is.

| Parameter                                                  | Java type               |
| ---------------------------------------------------------- | ----------------------- |
| Text, or an expression Jira parses (JQL, a field selector) | `String`                |
| A list of values                                           | `List<String>`          |
| A structured object forwarded to Jira                      | `Map<String, Object>`   |
| A repeated structured object                               | `List<SomeRecord>`      |
| Whole numbers, flags                                       | `int`/`long`, `boolean` |

A parameter that is a list of values is a JSON array, not a comma-separated
string. A parameter that is an object is a JSON object, not a string holding
JSON. Keep `String` only where the value is an opaque expression forwarded to
Jira verbatim.

A selector expression stays a `String` even though it looks like a list. `fields`
and `expand` are the cases in this plugin: they support wildcards such as `*all`,
Jira parses them as one expression, and `search` and `get_issue` already declare
them that way. A list of identifiers — issue keys, project keys, component names,
sprint states — is an array.

**Identifiers.** A Jira id that the REST API numbers is `long`: board, sprint,
link, comment, transition, service desk, queue, version. An identifier that is
not a number stays `String`: issue and project keys, custom field ids such as
`customfield_10010`, usernames, and the UUIDs ProForma uses for forms. Jackson
coerces a quoted `"10001"` either way, so `long` costs a caller nothing and makes
the advertised type honest.

**Values the schema cannot type.** A field whose JSON type depends on a sibling
field — a ProForma answer, where `value` follows `type` — has no expressible
record, because a component typed `Object` is refused. `List<Map<String, Object>>`
is the honest declaration; note in the description what the entries must contain.

**Nested records.** A repeated structured argument is a record, not prose
describing the shape of a map. The nested schema is generated, its required
fields are enforced, and a complaint names the offending path — for example
`issues[2].issue_type`.

**Required and defaults.** `required = true` for a parameter with no sensible
default. `defaultValue` is written as it would arrive on the wire, so `"10"` is
a valid default for an `int`. A primitive component must be one or the other; a
declaration that is neither fails when the tool is constructed.

**Enums.** `allowed = {...}` both advertises the values and rejects anything
else before the tool runs. It applies to a `String` component only: the binder
compares whole values, so an enum over the elements of a list is not
expressible, and declaring one fails when the tool is constructed. Check such a
value in `run` and name the permitted ones in the description. Close an enum
only where the endpoint defines a fixed set; leave it open where Jira accepts
anything a deployment has registered.

**Cardinality.** The schema layer has no `minItems`. A tool that needs a
non-empty list checks it in `run` and throws `McpToolException`.

**Constants and shared types.** Annotation members must be compile-time
constants, so a description or `defaultValue` shared between tools lives in
whichever tool owns it and is referenced from the other; the same goes for a
nested argument record two tools accept. Do not copy the literal or the type.
`defaultValue` is a single wire value, so a list can only default to one
element — a longer default lives in `run` and is stated in the description,
where a client reading the schema will not see it.

**Renaming and removing.** Both are allowed and sometimes required — a name that
means one thing in one tool and something else next door, or a parameter that is
declared and never read. Both are breaking for a caller with the old name
memorised: the parameter is now refused as unknown rather than ignored. Say so
in the release notes.

**Context.** Anything the caller did not declare as a parameter comes from
`McpContext`: `context.authHeader()` for the Jira call, and
`context.reportProgress(current, total, message)` for progress. A tool never
observes whether the caller asked for progress notifications, so one `run` body
serves both cases.

## Questions to answer before converting a tool

Do not port the existing parameters across. Ask what the tool is for, then write
the declaration it should have had.

1. **What is each parameter really?** A `string` carrying a comma-separated list
   is a list. A `string` carrying JSON is an object. A prose paragraph describing
   the keys of a map is a record.
2. **Does the description promise anything the code does not do?** A field named
   in the description but never read is a defect, not a parameter. Implement it
   or delete it from the description.
3. **Does the description promise anything the code does differently?** Two tools
   that declare the same input format must handle it the same way.
4. **What belongs in the schema and what belongs in the tool?** Whether a request
   is well-formed is the schema's job, and a malformed one is rejected whole.
   Whether Jira will accept it is the tool's job, and that is reported per item.
5. **Which parameters are inert?** Declared, parsed and then dropped. Remove them
   or make them work; the record makes the second reading impossible to fake.
6. **Is the result shape still honest?** `outputSchema()` and
   `structuredContent()` are still written by hand and are not derived from the
   record. Check they match what `run` returns.

## Verifying

```bash
mise run test       # unit tests
mise run lint:fix   # formatters and linters
mise run build      # compile and package
```

A tool's own test should assert, at minimum, that every declared parameter
reaches the request, that defaults apply when a parameter is absent, that a
required parameter is refused when missing, and that the advertised schema lists
exactly the declared parameters.

## Known gaps

`outputSchema()` and `structuredContent()` are declared on `McpTool` and written
by hand, so they can still drift from what a tool returns. Deriving them from a
result record is the next piece of work on this layer.

Three constraints can be enforced but not advertised, so a client reading the
schema learns them only by being refused: a non-empty list, an enum over list
elements, and a default of more than one value.
