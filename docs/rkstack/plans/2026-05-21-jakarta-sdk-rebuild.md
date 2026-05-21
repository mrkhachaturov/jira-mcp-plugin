# Jakarta + MCP Java SDK rebuild — implementation plan

> **For agentic workers:** Use subagent-driven-development (recommended) or executing-plans skill to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Spec:** [`../specs/2026-05-21-jakarta-sdk-rebuild-design.md`](../specs/2026-05-21-jakarta-sdk-rebuild-design.md)

**Goal:** Make the `jira-mcp-plugin` load and pass its 54 e2e tests on the user's already-upgraded Jira 11.x DC instance, using the official MCP Java SDK as the transport foundation.

**Architecture:** Five atomic commits on `feature/jakarta-jira-11`. Commit 1 bumps every dep to the jakarta stack (branch stops compiling — expected). Commit 2 replaces the hand-rolled JSON-RPC transport with the SDK's `HttpServletStreamableServerTransportProvider`, registered programmatically through `ServletModuleManager`, and stands up six servlet filters in front of it. Commits 3 and 4 adapt the 49 tools and the MCP Apps resource registry to the SDK's `Sync*Specification` shape. Commit 5 flips the docs and bumps the plugin version.

**Tech stack:** Java 21, AMPS 9.1.9, Atlassian platform BOM 8.1.13, Spring 6.2.15, Tomcat 10.1, Jakarta EE 10 (`jakarta.servlet 6.0.0`, `jakarta.ws.rs 3.1.0`, `jakarta.inject 2.0.1`), MCP Java SDK 2.0.0-M2 (`mcp-core` + `mcp-json-jackson2`), Atlassian Spring Scanner 6.0.2, Jackson 2.19.4.

**Working directory:** `/Volumes/Devops/Git/Github/mrkhachaturov/atlassian-mcp-plugin/.worktrees/jakarta-migration`. All paths in this plan are relative to it unless absolute.

**Commands (from project `CLAUDE.md` and `justfile`):**

| Task | Command | Notes |
|---|---|---|
| Build (unit tests excluded) | `atlas-mvn -DskipTests package` | Use `atlas-mvn`, never `mvn` |
| Build everything (widget + JAR) | `just build` | wraps `build-app` + `atlas-mvn package` |
| Unit tests | `atlas-mvn test` | excludes e2e suite via surefire excludes |
| E2E tests (live Jira) | `just e2e` | needs `.credentials/jira.env`; 54 tests |
| Deploy to live Jira UPM | `just deploy` | upload JAR, verify enabled |
| Build + deploy + e2e | `just deploy-and-test` | one-shot verification |
| Clean | `just clean` | `atlas-clean` |
| Verify dep tree | `atlas-mvn dependency:tree -Dverbose` | for commit 1 verification |

**Verification gate:** `just deploy-and-test` must return green before merging the branch to `main`. Each commit from 3 onward is expected to leave that command green.

**Risks called out in the spec (refer to it; not duplicated here):** transport registration (Atlassian `ServletModuleManager` vs SDK servlet), `mcp-json-jackson2` provider loading inside an OSGi plugin bundle, session-user binding correctness, SDK Accept-header strictness, MCP Apps `_meta` shape.

---

## Task 1: Commit 1 — Dependency bump + Jakarta EE 10 + Java 21 + MCP SDK pins

**Goal:** Edit `pom.xml` only. The branch will not compile after this commit — that is intentional. Every source-code change happens in later commits.

**Files:**
- Modify: `pom.xml`
- Modify: `.mise.toml` (Java 21 pin)

### Step 1.1 — Read the current pom.xml fully

- [ ] Read `pom.xml` from line 1 to end. Note every existing property, every dependency, every OSGi instruction. The diff in this task replaces sections wholesale; if anything else is in the file, preserve it.

### Step 1.2 — Rewrite `<properties>` section

Locate `<properties>` (currently lines 16–24). Replace its contents with:

```xml
<properties>
    <jira.version>11.3.6</jira.version>
    <amps.version>9.1.9</amps.version>
    <atlassian.spring.scanner.version>6.0.2</atlassian.spring.scanner.version>
    <atlassian.platform.version>8.1.13</atlassian.platform.version>
    <mcp.sdk.version>2.0.0-M2</mcp.sdk.version>

    <atlassian.plugin.key>${project.groupId}.${project.artifactId}</atlassian.plugin.key>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>
```

- [ ] Apply the edit.

### Step 1.3 — Add Atlassian platform BOM import

After `</properties>` and before `<dependencies>`, insert:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.atlassian.platform.dependencies</groupId>
            <artifactId>platform-public-api</artifactId>
            <version>${atlassian.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- [ ] Apply the edit.

### Step 1.4 — Replace `<dependencies>` block

Replace the entire `<dependencies>...</dependencies>` block (currently lines 26–108) with:

```xml
<dependencies>
    <!-- Jira platform — versions managed by platform BOM -->
    <dependency>
        <groupId>com.atlassian.jira</groupId>
        <artifactId>jira-api</artifactId>
        <version>${jira.version}</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.atlassian.plugin</groupId>
        <artifactId>atlassian-spring-scanner-annotation</artifactId>
        <version>${atlassian.spring.scanner.version}</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.atlassian.annotations</groupId>
        <artifactId>atlassian-annotations</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.atlassian.sal</groupId>
        <artifactId>sal-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.atlassian.templaterenderer</groupId>
        <artifactId>atlassian-template-renderer-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.atlassian.plugins.rest</groupId>
        <artifactId>atlassian-rest-common</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.atlassian.plugins</groupId>
        <artifactId>atlassian-plugins-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Jakarta EE 10 spec jars — versions managed by platform BOM -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.ws.rs</groupId>
        <artifactId>jakarta.ws.rs-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.inject</groupId>
        <artifactId>jakarta.inject-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.annotation</groupId>
        <artifactId>jakarta.annotation-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Jackson — provided by Jira, version managed by platform BOM -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- MCP Java SDK -->
    <dependency>
        <groupId>io.modelcontextprotocol.sdk</groupId>
        <artifactId>mcp-core</artifactId>
        <version>${mcp.sdk.version}</version>
    </dependency>
    <dependency>
        <groupId>io.modelcontextprotocol.sdk</groupId>
        <artifactId>mcp-json-jackson2</artifactId>
        <version>${mcp.sdk.version}</version>
    </dependency>

    <!-- Test scope -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.12.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Notes embedded in the diff:
- `mcp-core` and `mcp-json-jackson2` have **no** `<scope>` (default `compile`) — they are embedded into the plugin jar by AMPS.
- All Jakarta + Atlassian + Jackson deps lose explicit versions because the platform BOM now manages them.
- `javax.*` artifacts are gone (`javax.servlet-api`, `jsr311-api`, `javax.inject:1`).

- [ ] Apply the edit.

### Step 1.5 — Flip the OSGi `Import-Package` directive + embed SDK + schema-validator privately

Inside the `<jira-maven-plugin>` instructions block (currently around line 122–134), replace:

```xml
<Import-Package>
    javax.inject*;resolution:="optional",
    *
</Import-Package>
```

with:

```xml
<Import-Package>
    jakarta.inject*;resolution:="optional",
    *
</Import-Package>
<DynamicImport-Package>*</DynamicImport-Package>

<!-- Embed the MCP SDK + json-schema-validator inside the plugin jar so they
     don't need to resolve as separate OSGi bundles. The spec requires this
     because Atlassian plugin bundles embed third-party jars privately, and
     Jira may not export com.networknt.schema. -->
<Private-Package>
    io.modelcontextprotocol.*,
    com.networknt.schema.*
</Private-Package>
<Export-Package>
    com.atlassian.mcp.plugin.api
</Export-Package>
```

- [ ] Apply the edit. After committing the pom, verify the built jar privately contains both packages:

```bash
atlas-mvn -DskipTests package
unzip -l target/jira-mcp-plugin-*.jar | grep -E "io/modelcontextprotocol|com/networknt/schema" | head
```

Expected: dozens of class entries from `io/modelcontextprotocol/...` and `com/networknt/schema/...` — both packages embedded inside the plugin jar. If empty, the bnd-driven AMPS build did not pick up Private-Package — check that AMPS version 9.1.9 honors the directive (it does on the 9.x line; if older AMPS is somehow resolved, force-update).

### Step 1.6 — Bump `.mise.toml` to Java 21

Read `.mise.toml`. If it pins Java 17 (or `temurin-17` etc.), change to 21:

```toml
[tools]
java = "temurin-21"
```

(Preserve any other pinned tools — only the Java version changes.)

- [ ] Apply the edit.

### Step 1.7 — Verify the published M2 SDK artifacts match the API the spec depends on

The on-disk `.upstream/java-sdk` is `2.0.0-SNAPSHOT`. The pinned artifact is the released `2.0.0-M2` from Maven Central. The spec calls out four API claims that must hold on M2 (Dependencies → "Dependency verification" section). Verify each:

- [ ] Step 1.7a — Download the M2 jars to a temp dir:

```bash
mkdir -p /tmp/mcp-verify && cd /tmp/mcp-verify
for a in mcp-core mcp-json-jackson2; do
  curl -fSL -o "$a-2.0.0-M2.jar" \
    "https://repo1.maven.org/maven2/io/modelcontextprotocol/sdk/$a/2.0.0-M2/$a-2.0.0-M2.jar"
done
ls -lh *.jar
```

Expected: two jars downloaded, each non-empty.

- [ ] Step 1.7b — Verify `HttpServletStreamableServerTransportProvider` has a private constructor:

```bash
unzip -p mcp-core-2.0.0-M2.jar \
  io/modelcontextprotocol/server/transport/HttpServletStreamableServerTransportProvider.class \
  | javap -p /dev/stdin 2>/dev/null | grep -E "^\s+(public|private|protected) HttpServletStreamableServerTransportProvider"
```

Expected: at least one line, all with `private` access. If a public constructor appears, the M2 API has diverged from SNAPSHOT — escalate before continuing.

- [ ] Step 1.7c — Verify `McpTransportContextExtractor` has a single `extract` method, no response access:

```bash
unzip -p mcp-core-2.0.0-M2.jar io/modelcontextprotocol/server/McpTransportContextExtractor.class \
  | javap -p /dev/stdin
```

Expected output contains a single method line: `public abstract io.modelcontextprotocol.common.McpTransportContext extract(java.lang.Object);` (or with the actual `T` erasure). No method takes a response or returns void.

- [ ] Step 1.7d — Verify `McpSchema$Resource$Builder` has `meta(java.util.Map)`:

```bash
unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/spec/McpSchema$Resource$Builder.class' \
  | javap -p /dev/stdin | grep -E "meta\("
```

Expected: a line containing `public io.modelcontextprotocol.spec.McpSchema$Resource$Builder meta(java.util.Map);`.

- [ ] Step 1.7e — Verify `mcp-json-jackson2` exposes a `JacksonMcpJsonMapper` (or equivalent) factory class:

```bash
unzip -l mcp-json-jackson2-2.0.0-M2.jar | grep -E "Jackson(Mcp)?JsonMapper|JsonSchemaValidator" | head
```

Expected: at least one class containing `JacksonMcpJsonMapper` or `JacksonMcpJsonMapperSupplier`, and one for the schema validator. Note the exact class names.

- [ ] Step 1.7f — **Verify the exact method signatures** the plan's code samples depend on. The samples in Task 2 reference specific constructors and builder methods; if M2 differs, the samples won't compile. Record the actual signatures into a `/tmp/sdk-signatures.txt` cheatsheet for the executing engineer:

```bash
{
  echo "=== HttpServletStreamableServerTransportProvider ==="
  unzip -p mcp-core-2.0.0-M2.jar io/modelcontextprotocol/server/transport/HttpServletStreamableServerTransportProvider.class | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== HttpServletStreamableServerTransportProvider\$Builder ==="
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/server/transport/HttpServletStreamableServerTransportProvider$Builder.class' | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== McpServer ==="
  unzip -p mcp-core-2.0.0-M2.jar io/modelcontextprotocol/server/McpServer.class | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== McpServer\$SyncSpec ==="
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/server/McpServer$SyncSpec.class' | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== McpTransportContext ==="
  unzip -p mcp-core-2.0.0-M2.jar io/modelcontextprotocol/common/McpTransportContext.class | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== JacksonMcpJsonMapperSupplier (or whatever class 1.7e found) ==="
  # adjust class path to what 1.7e revealed
  unzip -p mcp-json-jackson2-2.0.0-M2.jar io/modelcontextprotocol/json/jackson2/JacksonMcpJsonMapperSupplier.class | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== McpSchema\$Resource\$Builder ==="
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/spec/McpSchema$Resource$Builder.class' | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== McpSchema\$Tool\$Builder ==="
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/spec/McpSchema$Tool$Builder.class' | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== McpSchema\$ToolAnnotations + Builder ==="
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/spec/McpSchema$ToolAnnotations.class' | javap -p /dev/stdin 2>/dev/null
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/spec/McpSchema$ToolAnnotations$Builder.class' | javap -p /dev/stdin 2>/dev/null
  echo
  echo "=== McpSchema\$ProgressNotification + Builder ==="
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/spec/McpSchema$ProgressNotification.class' | javap -p /dev/stdin 2>/dev/null
  unzip -p mcp-core-2.0.0-M2.jar 'io/modelcontextprotocol/spec/McpSchema$ProgressNotification$Builder.class' | javap -p /dev/stdin 2>/dev/null
} > /tmp/sdk-signatures.txt
wc -l /tmp/sdk-signatures.txt
```

Expected: a multi-hundred-line file containing every method signature the plan depends on. Skim it and confirm:

  1. `HttpServletStreamableServerTransportProvider$Builder.jsonMapper(McpJsonMapper)` exists
  2. Whether `schemaValidator(...)` is on the **transport builder** or on the **`McpServer.SyncSpec` builder** — the plan samples may need adjustment depending on where it lives
  3. Whether `McpServer.SyncSpec.jsonSchemaValidator(...)` or `.schemaValidator(...)` is the correct method name
  4. The Jackson mapper supplier exposes either a `get()` instance method (current sample assumption) or a static `getInstance()` factory
  5. `McpTransportContext.create(Map)` vs `McpTransportContext.from(Map)` vs `new DefaultMcpTransportContext(...)`
  6. `transportContext().get(String, Class)` vs `transportContext().get(String)` returning `Object`

**If any of the plan's code samples reference a method that does NOT appear in `/tmp/sdk-signatures.txt`, treat the samples as pseudocode and adjust them to the verified signatures before pasting.** Record the adjustments in the Task-2 commit message so future readers understand the deltas.

If any of 1.7b–1.7f fail outright (the class doesn't exist at all), **stop** and update the spec before continuing. The implementation cannot proceed without the API surface matching.

### Step 1.8 — Resolve the dependency tree (must succeed even though the source won't compile)

- [ ] Run:

```bash
atlas-mvn -DskipTests -Dmaven.test.skip=true help:effective-pom -Doutput=/tmp/effective-pom.xml
```

Expected: build succeeds. If it fails on missing transitive deps, inspect `/tmp/effective-pom.xml` and adjust BOM scope.

- [ ] Run:

```bash
atlas-mvn dependency:tree | tee /tmp/dep-tree.txt | grep -E "mcp-core|mcp-json-jackson2|json-schema-validator|jakarta\.|jackson-databind" | head -20
```

Expected: `io.modelcontextprotocol.sdk:mcp-core:jar:2.0.0-M2:compile` and `:mcp-json-jackson2:` present. `jakarta.servlet-api` resolves to 6.0.0. `jackson-databind` resolves to 2.19.x (BOM-managed).

- [ ] Verify no `javax.servlet`, `javax.ws.rs`, or `javax.inject` artifacts appear in the resolved tree (they must be fully replaced):

```bash
grep -E "javax\.(servlet|ws\.rs|inject)" /tmp/dep-tree.txt && echo "FAIL: javax deps still present" || echo "OK: no javax deps"
```

Expected: `OK: no javax deps`. If any remain, find which Atlassian transitive pulls them and add an `<exclusion>`.

### Step 1.9 — Compile is expected to FAIL — confirm the failure mode is "javax not found"

- [ ] Run:

```bash
atlas-mvn -DskipTests compile 2>&1 | tee /tmp/compile.log | tail -40
```

Expected: compile failure. The errors should be of the form `package javax.servlet does not exist`, `cannot find symbol class HttpServletRequest`, etc. **Confirm at least 3 distinct `javax.*` import errors appear.** This proves the jakarta switch is in effect and the source-code sweep in Tasks 2–4 will be the right scope.

- [ ] If compile errors mention anything **other** than `javax.*` packages (e.g. missing Atlassian classes, version conflicts), stop and investigate before committing.

### Step 1.10 — Commit

- [ ] Run:

```bash
git add pom.xml .mise.toml
git status   # verify only those two files are staged
git commit -m "$(cat <<'EOF'
chore(deps): bump to Jira 11 + Jakarta EE 10 + Java 21 + MCP SDK 2.0.0-M2

- jira.version 10.7.4 -> 11.3.6
- maven.compiler source/target 17 -> 21
- amps.version 9.9.1 -> 9.1.9 (the post-jakarta line)
- atlassian.spring.scanner.version 3.0.4 -> 6.0.2
- Add atlassian.platform.version 8.1.13 (platform BOM) — manages jakarta,
  spring, jackson, atlassian-rest, atlassian-template-renderer, sal-api
  transitively
- Drop javax.servlet-api 4.0.1, jsr311-api 1.1.1, javax.inject:1
- Add jakarta.servlet-api, jakarta.ws.rs-api, jakarta.inject-api,
  jakarta.annotation-api (versions BOM-managed)
- Add io.modelcontextprotocol.sdk:mcp-core:2.0.0-M2 and
  :mcp-json-jackson2:2.0.0-M2 (default compile scope — embedded in plugin jar)
- OSGi Import-Package: javax.inject* -> jakarta.inject*

The branch does not compile after this commit. Sources still use javax.*
imports; the jakarta + SDK rebuild happens in subsequent commits per
docs/rkstack/specs/2026-05-21-jakarta-sdk-rebuild-design.md.

Dependency verification (from /tmp/dep-tree.txt and javap -p checks):
- mcp-core / mcp-json-jackson2 at 2.0.0-M2 from Maven Central
- HttpServletStreamableServerTransportProvider constructor is private
  (confirms we must use the builder)
- McpTransportContextExtractor.extract(T) is the only method
- Resource.builder().meta(Map) is present
- No javax.* deps in the resolved tree
EOF
)"
```

Expected: commit succeeds (no pre-commit hooks to worry about — this repo doesn't have them on Java).

---

## Task 2: Commit 2 — Replace transport with SDK servlet + filters + transport spike

**Goal:** Delete `McpResource` + `JsonRpcHandler`. Build `McpBootstrap` + `McpPluginLifecycle` + `McpToolAdapter` + `JiraAuthContextExtractor` + six filters. Register the SDK transport programmatically via `ServletModuleManager`. Wire 1–2 tools into the bootstrap as a smoke-test before mass adapter rollout in Task 3.

This task is the highest-risk commit. Order of operations inside it is precise:

1. **Step 2.0** — sweep `javax.*` → `jakarta.*` across **every** file under `src/main/java`. The branch can't compile after Task 1 because the javax deps are gone; this is the recovery step. ALL sweeps happen here, not in Tasks 3 or 4.
2. **Step 2.1** — transport spike on the now-compiling tree (proves `ServletModuleManager` registration works).
3. **Steps 2.2–2.7** — build new transport + filter classes.
4. **Step 2.8** — preserve UI-linked-tool list + extract `ResourceContextBuilder` BEFORE deleting `JsonRpcHandler`.
5. **Step 2.9** — update `atlassian-plugin.xml`.
6. **Step 2.10** — delete old transport.
7. **Steps 2.11–2.13** — compile, smoke-test, commit.

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/McpPluginLifecycle.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/JiraAuthContextExtractor.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/OriginValidationFilter.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/BodySizeLimitFilter.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/RateLimitFilter.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/AccessControlFilter.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/SessionBindingFilter.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/SecurityHeadersFilter.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/ResourceContextBuilder.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/BufferedRequestWrapper.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/CapturingResponseWrapper.java`
- Delete: `src/main/java/com/atlassian/mcp/plugin/rest/McpResource.java`
- Delete: `src/main/java/com/atlassian/mcp/plugin/JsonRpcHandler.java`
- Delete: `src/test/java/com/atlassian/mcp/plugin/JsonRpcHandlerTest.java`
- Modify: `src/main/java/com/atlassian/mcp/plugin/rest/OAuthAnonymousFilter.java` (javax → jakarta, paths unchanged)
- Modify: `src/main/java/com/atlassian/mcp/plugin/rest/RateLimiter.java` (javax → jakarta if any imports, else just stay put)
- Modify: `src/main/resources/atlassian-plugin.xml` (remove `<rest key="mcp-rest">`, add `<servlet-filter>` declarations)

### Step 2.0 — Bulk javax → jakarta sweep across `src/main/java`

Pre-condition: Task 1 committed, branch does not compile. Goal: every `.java` under `src/main/java` switches from `javax.servlet`, `javax.ws.rs`, `javax.inject`, `javax.annotation` to the matching `jakarta.*` package. After this step, the tree compiles **except** for the new transport classes we haven't written yet (which is fine — `JsonRpcHandler`, `McpResource`, `OAuthServlet`, `AdminServlet`, `ConfigResource`, `OAuthAnonymousFilter`, all 49 tools — all of those switch to jakarta in this step).

- [ ] Step 2.0a — Run the sweep:

```bash
cd /Volumes/Devops/Git/Github/mrkhachaturov/atlassian-mcp-plugin/.worktrees/jakarta-migration
find src/main/java -name "*.java" -print0 \
    | xargs -0 grep -lE "javax\.(servlet|ws\.rs|inject|annotation)" \
    | while read -r f; do
        sed -i.bak -E 's|javax\.(servlet|ws\.rs|inject|annotation)\.|jakarta.\1.|g' "$f" && rm "${f}.bak"
        echo "Swept: $f"
      done
```

- [ ] Step 2.0b — Verify the sweep covered everything:

```bash
grep -rnE "javax\.(servlet|ws\.rs|inject|annotation)" src/main/java/ | head
```

Expected: no output. **Do not** sweep other `javax.*` packages — `javax.crypto`, `javax.naming`, `javax.security.auth` etc. are Java SE, not Jakarta EE, and they don't change.

- [ ] Step 2.0c — Compile:

```bash
atlas-mvn -DskipTests compile 2>&1 | tail -25
```

Expected: success. **If it fails**, the failures will tell you which `javax.*` packages still need sweeping (a Maven-time exception will name them). Adjust the sed expression and re-run. Common culprits we may have missed: `javax.crypto` (do NOT sweep — Java SE), `javax.servlet.Filter` inside string literals (rare; sweep manually if found in `.properties` or `.xml`).

### Step 2.1 — Spike: prove `ServletModuleManager` registration works with the SDK transport

Before touching the existing `McpResource`, we prove the new registration path works end-to-end with a throwaway minimal setup.

- [ ] Step 2.1a — Create a minimal scratch class at `src/main/java/com/atlassian/mcp/plugin/rest/SpikeBootstrap.java`:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.plugin.osgi.bridge.external.PluginLifecycleListener;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
// NOTE: exact class names for the json mapper come from Step 1.7e — replace below
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import javax.servlet.http.HttpServlet;  // intentionally javax — the IDE compile will catch it once 2.b lands

@Named("spikeBootstrap")
public class SpikeBootstrap implements LifecycleAware {
    private final ObjectMapper mapper = new ObjectMapper();
    @Override public void onStart() {
        var jsonMapper = JacksonMcpJsonMapper.from(mapper);
        var validator  = JacksonJsonSchemaValidator.from(mapper);
        HttpServletStreamableServerTransportProvider transport =
            HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .schemaValidator(validator)
                .mcpEndpoint("/plugins/servlet/mcp-spike")
                .build();
        McpSyncServer server = McpServer.sync(transport)
            .serverInfo("jira-mcp-plugin-spike", "spike")
            .build();
        // TODO: register `transport` via ServletModuleManager — that's the spike question
    }
    @Override public void onStop() {}
}
```

This file deliberately fails to compile (mixed javax/jakarta + missing `ServletModuleManager` wiring + wrong class names from Step 1.7e). It is **scaffolding for the spike**, not the final code.

- [ ] Step 2.1b — Run `atlas-mvn -DskipTests compile 2>&1 | head -40` and verify the compile errors are limited to:
  1. The mixed `javax.servlet.http.HttpServlet` import
  2. The class names from `mcp-json-jackson2` we noted in Step 1.7e (adjust the imports to the real names — `JacksonMcpJsonMapper` may actually be `JacksonMcpJsonMapperSupplier.getInstance()` or similar)
  3. The unresolved `ServletModuleManager`

If unexpected errors appear (e.g. SDK class not found), the SDK artifact didn't resolve — go back to Task 1 and check `atlas-mvn dependency:get`.

- [ ] Step 2.1c — Look up `ServletModuleManager` shape in the Jira API. In a separate terminal:

```bash
cd .. && find ~/.m2/repository/com/atlassian/jira/jira-api/11.3.6 -name "*.jar" 2>/dev/null | head -1 | xargs -I{} unzip -l {} | grep -i "servletmodulemanager\|servletmoduledescriptor" | head
```

Record the exact package and method signature for adding a servlet at runtime. **If `ServletModuleManager` is not in `jira-api`,** check `atlassian-plugins-api`:

```bash
find ~/.m2/repository -name "atlassian-plugins*.jar" 2>/dev/null | head -3 | xargs -I{} unzip -l {} | grep -i "servletmodulemanager" | head
```

If still not found, the registration mechanism on Jira 11 may have shifted — escalate before continuing.

- [ ] Step 2.1d — Fix the spike file with the real class names + real `ServletModuleManager` calls. The result should look like this (adjust class names per actual SDK):

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.plugin.servlet.ServletModuleManager;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidatorSupplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("spikeBootstrap")
public class SpikeBootstrap implements LifecycleAware {
    private final ServletModuleManager servletModuleManager;
    private HttpServletStreamableServerTransportProvider transport;

    @Inject
    public SpikeBootstrap(@ComponentImport ServletModuleManager servletModuleManager) {
        this.servletModuleManager = servletModuleManager;
    }

    @Override
    public void onStart() {
        ObjectMapper mapper = new ObjectMapper();
        var jsonMapper = new JacksonMcpJsonMapperSupplier(mapper).get();
        var validator  = new JacksonJsonSchemaValidatorSupplier(mapper).get();

        transport = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .schemaValidator(validator)
            .mcpEndpoint("/plugins/servlet/mcp-spike")
            .build();

        McpSyncServer server = McpServer.sync(transport)
            .serverInfo("jira-mcp-plugin-spike", "spike")
            .build();

        // The exact registration API depends on what 2.1c uncovered. Common shape:
        //   ServletContext or ServletModuleManager.registerServlet(...)
        // Replace the line below with the real call.
        // servletModuleManager.registerServlet(...);
    }

    @Override
    public void onStop() {
        // mirror the registration
    }
}
```

- [ ] Step 2.1e — Compile and deploy the spike to confirm it loads:

```bash
just build && just deploy
```

Expected: plugin enables in UPM without errors. If it fails, the Jira log (in `~/.atlassian/jira/log/atlassian-jira.log` on a local instance, or via Jira UI on the remote one) will show the OSGi resolution failure. The most likely failures are:
  1. `Unable to resolve service ServletModuleManager` — wrong injection annotation (`@ComponentImport` may need `@Lazy` or `@Internal`)
  2. `NoClassDefFoundError: io/modelcontextprotocol/...` — SDK packages not exported by our plugin manifest — add `Export-Package: !io.modelcontextprotocol.*` to ensure they're private to the plugin and not exported

- [ ] Step 2.1f — Smoke-test the spike endpoint:

```bash
source .credentials/jira.env
curl -i -X POST "$JIRA_URL/plugins/servlet/mcp-spike" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"spike","version":"1"}}}'
```

Expected: HTTP 200, `MCP-Session-Id` header in response, JSON-RPC `result` body containing `protocolVersion` and `serverInfo`. If the response is 400 with a body-validation error, check the request body shape matches the SDK's `InitializeRequest` schema.

- [ ] Step 2.1g — Record the spike outcome. The commit message for Task 2 must include a one-line summary: "Spike confirmed: `ServletModuleManager.registerServlet(...)` registers the SDK transport at `/plugins/servlet/mcp-spike`; initialize returns 200 with session header."

**If the spike fails after a reasonable effort (≤2 hours of CC time):** stop and escalate. The transport-registration mechanism is the load-bearing assumption of the whole rebuild — do not build the rest of Task 2 on a broken foundation.

### Step 2.2 — Create `McpBootstrap.java` (replaces `JsonRpcHandler` + the McpResource init logic)

`McpBootstrap` builds the SDK server + transport once, holds the configured `HttpServlet` for `McpPluginLifecycle` to register.

> **Code samples in Task 2 are INDICATIVE, not verbatim.** Step 1.7f's `/tmp/sdk-signatures.txt` is the source of truth for exact M2 API shapes. Before pasting any sample below, cross-check:
> - `JacksonMcpJsonMapperSupplier` / `JacksonJsonSchemaValidatorSupplier` constructor — may be `getInstance()` (static factory) instead of `new(ObjectMapper)`
> - `HttpServletStreamableServerTransportProvider.Builder` — `.schemaValidator(...)` is **probably not** on the transport builder; schema validation lives on `McpServer.SyncSpec.jsonSchemaValidator(...)` per the M2 source
> - `McpTransportContext.create(Map)` vs `from(Map)` vs constructor
> - `transportContext().get(String, Class)` vs `get(String)` returning `Object` (cast)
>
> If a sample method doesn't appear in `/tmp/sdk-signatures.txt`, **adjust the sample to the verified signature before continuing.** Record adjustments in the Task-2 commit message.

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java`:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.ResourceRegistry;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.ToolRegistry;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServlet;

@Named("mcpBootstrap")
public class McpBootstrap {

    private static final String SERVER_NAME = "jira-mcp-plugin";
    private static final String SERVER_VERSION_FALLBACK = "1.3.0-SNAPSHOT";  // bumped in commit 5

    private final ToolRegistry toolRegistry;
    private final ResourceRegistry resourceRegistry;
    private final McpPluginConfig config;
    private final JiraAuthContextExtractor authExtractor;

    private volatile HttpServletStreamableServerTransportProvider transport;
    private volatile McpSyncServer server;

    @Inject
    public McpBootstrap(ToolRegistry toolRegistry,
                        ResourceRegistry resourceRegistry,
                        McpPluginConfig config,
                        JiraAuthContextExtractor authExtractor) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.config = config;
        this.authExtractor = authExtractor;
    }

    /** Build (idempotent). Returns the configured servlet for ServletModuleManager. */
    public synchronized HttpServlet buildTransport() {
        if (transport != null) return transport;

        ObjectMapper mapper = new ObjectMapper();
        var jsonMapper = new JacksonMcpJsonMapperSupplier(mapper).get();
        var schemaValidator = new JacksonJsonSchemaValidatorSupplier(mapper).get();

        // INDICATIVE — schemaValidator is on McpServer.sync(...).jsonSchemaValidator(...),
        // NOT on the transport provider builder. Verify against /tmp/sdk-signatures.txt.
        this.transport = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint("/plugins/servlet/mcp")
            .contextExtractor(authExtractor)
            .build();

        this.server = McpServer.sync(transport)
            .jsonMapper(jsonMapper)
            .jsonSchemaValidator(schemaValidator)              // schema validation belongs here
            .serverInfo(SERVER_NAME, SERVER_VERSION_FALLBACK)
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .resources(false, true)
                .build())
            .tools(toolRegistry.toSpecifications())            // 2 tools wired in Task 2 Step 2.2a; full set in Task 3
            .resources(resourceRegistry.toSpecifications())    // populated in Task 4
            .build();

        return transport;
    }

    public synchronized void close() {
        if (server != null) { server.close(); server = null; }
        transport = null;
    }
}
```

- [ ] Apply the create. The references to `toolRegistry.toSpecifications()` and `resourceRegistry.toSpecifications()` need partial implementations to make Task 2's smoke test meaningful — Task 3 expands them to the full 49-tool list, but the transport spike needs to actually call into a tool to prove authHeader forwarding and adapter wiring work.

  - [ ] Step 2.2a — Open `src/main/java/com/atlassian/mcp/plugin/tools/ToolRegistry.java`. Add a **non-empty** stub that wires two read-only tools through `McpToolAdapter` for smoke-testing. Use `GetUserProfileTool` and `GetAllProjectsTool` (both already exist, both are read-only, both have known-good e2e tests):

    ```java
    public java.util.List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> toSpecifications() {
        // TASK-2 SMOKE WIRING — only 2 tools so the transport spike exercises the full
        // adapter path (authHeader extraction, JiraRestClient call, CallToolResult shape).
        // Task 3 replaces this with the full visible-tool stream.
        return java.util.List.of(
            com.atlassian.mcp.plugin.rest.McpToolAdapter.adapt(getToolByName("get_user_profile")),
            com.atlassian.mcp.plugin.rest.McpToolAdapter.adapt(getToolByName("get_all_projects"))
        );
    }
    ```

    If the registry's internal lookup method has a different name (e.g. `findTool`, `lookupByName`, or a public `Map<String, McpTool>` field), use it. Grep first: `grep -nE "private.*Map<String|public.*McpTool[ <]" src/main/java/com/atlassian/mcp/plugin/tools/ToolRegistry.java`.

  - [ ] Step 2.2b — Open `src/main/java/com/atlassian/mcp/plugin/ResourceRegistry.java`. Add an empty stub for now — Task 4 fills it in:

    ```java
    public java.util.List<io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification> toSpecifications() {
        return java.util.List.of();   // populated in Task 4
    }
    ```

    (Resources aren't on the smoke-test critical path — they only affect MCP Apps widget rendering, which has its own gate in Task 4.)

### Step 2.3 — Create `McpPluginLifecycle.java`

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/McpPluginLifecycle.java`:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.plugin.servlet.ServletModuleManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named("mcpPluginLifecycle")
public class McpPluginLifecycle implements LifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(McpPluginLifecycle.class);
    private static final String SERVLET_KEY  = "mcp-transport";
    private static final String SERVLET_PATH = "/plugins/servlet/mcp";

    private final McpBootstrap bootstrap;
    private final ServletModuleManager servletModuleManager;

    @Inject
    public McpPluginLifecycle(McpBootstrap bootstrap,
                              @ComponentImport ServletModuleManager servletModuleManager) {
        this.bootstrap = bootstrap;
        this.servletModuleManager = servletModuleManager;
    }

    @Override
    public void onStart() {
        HttpServlet sdkTransport = bootstrap.buildTransport();
        // The exact ServletModuleManager API call is finalized during the spike (Step 2.1).
        // Likely shape (verify against Jira 11 API):
        servletModuleManager.registerServlet(SERVLET_KEY, SERVLET_PATH, sdkTransport);
        log.info("[MCP] Registered SDK transport at {} (key={})", SERVLET_PATH, SERVLET_KEY);
    }

    public void onStop() {
        servletModuleManager.unregisterServlet(SERVLET_KEY);
        bootstrap.close();
        log.info("[MCP] Unregistered SDK transport (key={})", SERVLET_KEY);
    }
}
```

**Critical:** The exact `ServletModuleManager.registerServlet(...)` signature was confirmed in the spike (Step 2.1c–2.1d). If the spike used a different method (e.g. `addServlet(ServletModuleDescriptor)` taking a descriptor rather than a path), match it here verbatim.

- [ ] Apply the create.

### Step 2.4 — Create `JiraAuthContextExtractor.java`

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/JiraAuthContextExtractor.java`:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserKey;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Pulls the Authorization header off each request and stashes both the raw header
 * (for forwarding to Jira REST) and the resolved Jira username into the per-request
 * McpTransportContext. Tool handlers read these via
 *   exchange.transportContext().get("authHeader", String.class)
 *   exchange.transportContext().get("jiraUser", String.class)
 *
 * This class is the principal extraction point. Session-user binding enforcement
 * lives in SessionBindingFilter, not here — see the spec.
 */
@Named("jiraAuthContextExtractor")
public class JiraAuthContextExtractor implements McpTransportContextExtractor<HttpServletRequest> {

    static final String CTX_AUTH_HEADER = "authHeader";
    static final String CTX_JIRA_USER   = "jiraUser";

    private final UserManager userManager;

    @Inject
    public JiraAuthContextExtractor(@ComponentImport UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public McpTransportContext extract(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String username = resolveUsername(request);   // null if anonymous
        Map<String, Object> ctx = new HashMap<>(4);
        if (authHeader != null) ctx.put(CTX_AUTH_HEADER, authHeader);
        if (username   != null) ctx.put(CTX_JIRA_USER,   username);
        return McpTransportContext.create(ctx);
    }

    private String resolveUsername(HttpServletRequest request) {
        UserKey key = userManager.getRemoteUserKey(request);
        if (key == null) return null;
        return userManager.getUserProfile(key).getUsername();
    }
}
```

Note: `McpTransportContext.create(Map)` is the assumed factory. **In the spike (Step 2.1)** the actual factory was uncovered — if it's `McpTransportContext.from(Map)` or `new DefaultMcpTransportContext(...)`, adjust.

- [ ] Apply the create.

### Step 2.5 — Create `BufferedRequestWrapper.java` and `CapturingResponseWrapper.java`

These wrappers support `SessionBindingFilter` reading the POST body twice (to inspect `"method":"initialize"`) and capturing the `MCP-Session-Id` header the SDK writes.

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/BufferedRequestWrapper.java`:

```java
package com.atlassian.mcp.plugin.rest;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Caches the request body so it can be read more than once. */
public final class BufferedRequestWrapper extends HttpServletRequestWrapper {
    private final byte[] body;

    public BufferedRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    public byte[] body() { return body; }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady()    { return true; }
            @Override public void setReadListener(ReadListener listener) {}
            @Override public int read() { return bais.read(); }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
```

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/CapturingResponseWrapper.java`:

```java
package com.atlassian.mcp.plugin.rest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/** Captures specific response headers as the SDK writes them. */
public final class CapturingResponseWrapper extends HttpServletResponseWrapper {
    private String mcpSessionId;
    private int statusCode = HttpServletResponse.SC_OK;

    public CapturingResponseWrapper(HttpServletResponse response) { super(response); }

    @Override public void setHeader(String name, String value) {
        if ("MCP-Session-Id".equalsIgnoreCase(name)) this.mcpSessionId = value;
        super.setHeader(name, value);
    }
    @Override public void addHeader(String name, String value) {
        if ("MCP-Session-Id".equalsIgnoreCase(name)) this.mcpSessionId = value;
        super.addHeader(name, value);
    }
    @Override public void setStatus(int sc) { this.statusCode = sc; super.setStatus(sc); }
    @Override public void sendError(int sc) throws java.io.IOException { this.statusCode = sc; super.sendError(sc); }
    @Override public void sendError(int sc, String msg) throws java.io.IOException { this.statusCode = sc; super.sendError(sc, msg); }
    @Override public int  getStatus() { return statusCode; }

    public String capturedSessionId() { return mcpSessionId; }
}
```

- [ ] Apply both creates.

### Step 2.6 — Create `SessionBindingFilter.java` (the security boundary)

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/SessionBindingFilter.java`:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the security invariant: an MCP-Session-Id issued to one Jira user
 * may not be used by another. Cross-user replay → 403. Unknown / expired session
 * on a non-initialize POST → 401.
 *
 * Architecture per docs/rkstack/specs/2026-05-21-jakarta-sdk-rebuild-design.md
 * (Session-user binding section).
 */
@Named("mcpSessionBindingFilter")
public class SessionBindingFilter implements Filter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BINDINGS = 200;
    private static final long TTL_MILLIS = 4L * 60 * 60 * 1000;   // 4 hours

    private static final ConcurrentHashMap<String, SessionBinding> bindings = new ConcurrentHashMap<>();

    private final UserManager userManager;

    @Inject
    public SessionBindingFilter(@ComponentImport UserManager userManager) {
        this.userManager = userManager;
    }

    private record SessionBinding(String username, long createdAtMillis) {}

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq  = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) resp;

        String currentUser = resolveUsername(httpReq);
        if (currentUser == null) {
            httpResp.sendError(401, "Unauthorized");
            return;
        }

        String incomingSid = httpReq.getHeader("MCP-Session-Id");
        boolean isPost = "POST".equalsIgnoreCase(httpReq.getMethod());

        // Body-buffered wrapper so we can inspect "method":"initialize" without consuming for the SDK
        BufferedRequestWrapper buffered = isPost ? new BufferedRequestWrapper(httpReq) : null;
        boolean isInitialize = isPost && looksLikeInitialize(buffered);

        if (!isInitialize && incomingSid != null) {
            SessionBinding b = bindings.get(incomingSid);
            if (b == null || expired(b)) {
                httpResp.sendError(401, "session unknown or expired");
                return;
            }
            if (!b.username().equals(currentUser)) {
                httpResp.sendError(403, "session bound to a different user");
                return;
            }
        }

        if (isInitialize) {
            CapturingResponseWrapper wrapped = new CapturingResponseWrapper(httpResp);
            chain.doFilter(buffered, wrapped);
            String issuedSid = wrapped.capturedSessionId();
            if (issuedSid != null && wrapped.getStatus() < 400) {
                evictIfFull();
                bindings.put(issuedSid, new SessionBinding(currentUser, System.currentTimeMillis()));
            }
            return;
        }

        if ("DELETE".equalsIgnoreCase(httpReq.getMethod()) && incomingSid != null) {
            chain.doFilter(req, resp);
            if (httpResp.getStatus() < 400) bindings.remove(incomingSid);
            return;
        }

        chain.doFilter(buffered != null ? buffered : req, resp);
    }

    private boolean looksLikeInitialize(BufferedRequestWrapper buffered) {
        if (buffered == null) return false;
        try {
            JsonNode node = JSON.readTree(buffered.body());
            return node != null && "initialize".equals(node.path("method").asText(null));
        } catch (IOException e) {
            return false;   // malformed body — let SDK return 400
        }
    }

    private boolean expired(SessionBinding b) {
        return System.currentTimeMillis() - b.createdAtMillis() > TTL_MILLIS;
    }

    private void evictIfFull() {
        if (bindings.size() < MAX_BINDINGS) return;
        bindings.entrySet().removeIf(e -> expired(e.getValue()));
        // If still full, drop the oldest
        if (bindings.size() >= MAX_BINDINGS) {
            bindings.entrySet().stream()
                .min((a, b) -> Long.compare(a.getValue().createdAtMillis(), b.getValue().createdAtMillis()))
                .ifPresent(e -> bindings.remove(e.getKey()));
        }
    }

    private String resolveUsername(HttpServletRequest request) {
        UserKey key = userManager.getRemoteUserKey(request);
        if (key == null) return null;
        return userManager.getUserProfile(key).getUsername();
    }
}
```

- [ ] Apply the create.

### Step 2.7 — Create the four remaining filters

Each filter is a focused, single-purpose `jakarta.servlet.Filter`. They will be wired in `atlassian-plugin.xml` in Step 2.9.

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/OriginValidationFilter.java`:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Origin allowlist per MCP spec. Rejects with 403. */
@Named("mcpOriginValidationFilter")
public class OriginValidationFilter implements Filter {
    private static final Set<String> ALWAYS_ALLOWED = Set.of(
        "claude.ai", "claude.com", "chatgpt.com", "chat.openai.com");

    private final McpPluginConfig config;
    @Inject public OriginValidationFilter(McpPluginConfig config) { this.config = config; }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) resp;
        String origin = httpReq.getHeader("Origin");
        if (origin == null) { chain.doFilter(req, resp); return; }   // non-browser, allow
        try {
            URI u = URI.create(origin);
            String host = u.getHost();
            String jiraHost = URI.create(config.getJiraBaseUrl()).getHost();
            if ("localhost".equals(host) || "127.0.0.1".equals(host)
                || (jiraHost != null && jiraHost.equalsIgnoreCase(host))
                || ALWAYS_ALLOWED.contains(host)) {
                chain.doFilter(req, resp);
                return;
            }
        } catch (Exception ignore) { /* fall through to 403 */ }
        httpResp.sendError(403, "Origin not allowed");
    }
}
```

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/BodySizeLimitFilter.java`:

```java
package com.atlassian.mcp.plugin.rest;

import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Caps the MCP POST body at 1 MB. Returns 413. */
@Named("mcpBodySizeLimitFilter")
public class BodySizeLimitFilter implements Filter {
    private static final int MAX_BYTES = 1024 * 1024;
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        int contentLength = httpReq.getContentLength();
        if (contentLength > MAX_BYTES) {
            ((HttpServletResponse) resp).sendError(413, "Request body too large");
            return;
        }
        chain.doFilter(req, resp);
    }
}
```

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/RateLimitFilter.java`:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.RateLimiter;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserKey;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Per-user rate limit on the MCP endpoint. Returns 429. */
@Named("mcpRateLimitFilter")
public class RateLimitFilter implements Filter {
    private static final int LIMIT_PER_MIN = 120;
    private final RateLimiter rateLimiter = new RateLimiter(LIMIT_PER_MIN);

    private final UserManager userManager;
    @Inject public RateLimitFilter(@ComponentImport UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        UserKey key = userManager.getRemoteUserKey(httpReq);
        String bucket = (key != null) ? "u:" + key.getStringValue() : "ip:" + httpReq.getRemoteAddr();
        if (!rateLimiter.tryAcquire(bucket)) {
            ((HttpServletResponse) resp).sendError(429, "Rate limit exceeded");
            return;
        }
        chain.doFilter(req, resp);
    }
}
```

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/AccessControlFilter.java` — **this ports `McpResource.checkAuth(...)` and `McpResource.isAccessAllowed(...)` (currently at `src/main/java/com/atlassian/mcp/plugin/rest/McpResource.java:455-505` on `main`).** Without this filter, any authenticated Jira user can hit the MCP endpoint even when MCP is admin-disabled or the user is not in the allowed-users/allowed-groups list — that is a regression. Verify the exact logic against the pre-deletion file:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.jira.security.groups.GroupManager;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Enforces the admin-config access policy:
 *   - 403 if MCP is globally disabled (config.isEnabled() == false)
 *   - 403 if the authenticated user is not in allowedUsers AND not in any allowedGroups
 *     (when either list is non-empty)
 *   - 401 if no authenticated user (anonymous request to the MCP endpoint)
 *
 * Ports the logic from McpResource.checkAuth + McpResource.isAccessAllowed —
 * see src/main/java/com/atlassian/mcp/plugin/rest/McpResource.java:455-505
 * on the pre-Task-2 branch (commit b75f2e4 or earlier).
 */
@Named("mcpAccessControlFilter")
public class AccessControlFilter implements Filter {
    private final McpPluginConfig config;
    private final UserManager userManager;
    private final GroupManager groupManager;

    @Inject
    public AccessControlFilter(McpPluginConfig config,
                               @ComponentImport UserManager userManager,
                               @ComponentImport GroupManager groupManager) {
        this.config = config;
        this.userManager = userManager;
        this.groupManager = groupManager;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) resp;

        if (!config.isEnabled()) {
            httpResp.sendError(403, "MCP server disabled");
            return;
        }

        UserKey key = userManager.getRemoteUserKey(httpReq);
        if (key == null) {
            httpResp.sendError(401, "Unauthorized");
            return;
        }
        String username = userManager.getUserProfile(key).getUsername();

        if (!isAccessAllowed(username, key.getStringValue())) {
            httpResp.sendError(403, "User not allowed");
            return;
        }
        chain.doFilter(req, resp);
    }

    private boolean isAccessAllowed(String username, String userKey) {
        // Port verbatim from McpResource.isAccessAllowed — lines ~484-505 on main.
        // Logic: if both allowedUsers and allowedGroups are empty -> ALLOW (open access)
        //        else: ALLOW if username in allowedUsers, OR user is in any of allowedGroups.
        Set<String> allowedUsers  = config.getAllowedUsers();
        Set<String> allowedGroups = config.getAllowedGroups();
        if (allowedUsers.isEmpty() && allowedGroups.isEmpty()) return true;
        if (allowedUsers.contains(username)) return true;
        if (!allowedGroups.isEmpty()) {
            for (Group group : groupManager.getGroupsForUser(username)) {
                if (allowedGroups.contains(group.getName())) return true;
            }
        }
        return false;
    }
}
```

- [ ] Create `src/main/java/com/atlassian/mcp/plugin/rest/SecurityHeadersFilter.java`:

```java
package com.atlassian.mcp.plugin.rest;

import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Adds security headers to every MCP response. */
@Named("mcpSecurityHeadersFilter")
public class SecurityHeadersFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) resp;
        httpResp.setHeader("X-Content-Type-Options", "nosniff");
        httpResp.setHeader("Cache-Control", "no-store");
        httpResp.setHeader("X-Frame-Options", "DENY");
        chain.doFilter(req, resp);
    }
}
```

- [ ] Apply all four creates.

### Step 2.8 — Preserve UI-linked tool list + extract `ResourceContextBuilder` BEFORE deleting `JsonRpcHandler`

The 5 UI-linked tool names and the `buildStructuredContent`/`normalizeIssue` logic currently live inside `JsonRpcHandler.java`. Task 2 Step 2.10 deletes that file. The knowledge must be lifted out first.

- [ ] Step 2.8.0 — **Capture baseline structuredContent shapes from the live `JsonRpcHandler` before it's deleted.** For each UI-linked tool, capture the exact JSON that today's transport emits. These baselines become the acceptance fixtures for `ResourceContextBuilder` — every key, every nesting level, every type must match.

```bash
source .credentials/jira.env
# Initialize first to get a session
SID=$(curl -s -D - -X POST "$JIRA_URL/rest/mcp/1.0/" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"baseline","version":"1"}}}' \
  | grep -i "^mcp-session-id:" | awk '{print $2}' | tr -d '\r')

mkdir -p /tmp/structured-baselines

# For each UI-linked tool, capture the structuredContent shape:
for spec in \
    'get_user_profile|{"user_identifier":"admin"}' \
    'get_issue|{"issue_key":"DEMO-1"}' \
    'search|{"jql":"project=DEMO","limit":3}' \
  ; do
  name="${spec%%|*}"
  args="${spec#*|}"
  curl -s -X POST "$JIRA_URL/rest/mcp/1.0/" \
    -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
    -H "Content-Type: application/json" \
    -H "MCP-Session-Id: $SID" \
    -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"${name}\",\"arguments\":${args}}}" \
    | jq '.result.structuredContent' > "/tmp/structured-baselines/${name}.json"
  echo "Captured: ${name} -> /tmp/structured-baselines/${name}.json"
done
```

Open each `/tmp/structured-baselines/*.json`. Record the top-level keys and shape — these are what `ResourceContextBuilder` must reproduce. **Likely shape (confirm from the actual capture):** every UI-linked tool's structuredContent has `currentUser`, `baseUrl`, **plus `issues[]` and `totalCount` even for single-issue tools** (the widget reuses one component for both single + list rendering). If single-issue tools emit `issue` (singular) instead of `issues[]` (array), record that too and add the right method.

(Codex's R2-F4 specifically called out that `buildForSingleIssue` may need to emit `issues[]` + `totalCount` rather than `issue`. Trust the baseline capture over the spec text — the baseline IS the contract.)

- [ ] Step 2.8a — Record the exact UI-linked tool list. Find the lookup in `JsonRpcHandler`:

```bash
grep -nE "uiResource|ui_resource|isUiLinked|UI_LINKED" src/main/java/com/atlassian/mcp/plugin/JsonRpcHandler.java
```

Copy the tool names into the plan's commit message and into a comment at the top of `ResourceContextBuilder` (created in Step 2.8b). Likely set: `get_issue`, `search`, `get_project_issues`, `get_board_issues`, `get_sprint_issues` — but **verify by reading the current file**, don't assume.

- [ ] Step 2.8b — Create `src/main/java/com/atlassian/mcp/plugin/ResourceContextBuilder.java` (Spring-injectable). Port the **entire** logic from `JsonRpcHandler.buildStructuredContent(...)` and `JsonRpcHandler.normalizeIssue(...)`:

```java
package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the structuredContent payload for MCP Apps UI-linked tools.
 *
 * Extracted verbatim from JsonRpcHandler.buildStructuredContent and
 * JsonRpcHandler.normalizeIssue (deleted in Task 2 Step 2.10).
 *
 * UI-linked tools (verified from JsonRpcHandler before deletion):
 *   - get_issue           — single-issue shape
 *   - search              — issue-list shape (issues[], totalCount)
 *   - get_project_issues  — issue-list shape
 *   - get_board_issues    — issue-list shape
 *   - get_sprint_issues   — issue-list shape
 *
 * The shape varies by tool category — single vs list — so this builder
 * exposes two methods.
 */
@Named("resourceContextBuilder")
public class ResourceContextBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final McpPluginConfig config;
    private final UserManager userManager;

    @Inject
    public ResourceContextBuilder(McpPluginConfig config,
                                  @ComponentImport UserManager userManager) {
        this.config = config;
        this.userManager = userManager;
    }

    /** For tools whose execute() returns a single Jira issue JSON. */
    public Map<String, Object> buildForSingleIssue(String executeResultJson, String jiraUsername) {
        try {
            JsonNode issue = MAPPER.readTree(executeResultJson);
            Map<String, Object> sc = new HashMap<>();
            sc.put("currentUser", buildCurrentUser(jiraUsername));
            sc.put("baseUrl",     config.getJiraBaseUrl());
            sc.put("issue",       normalizeIssue(issue));
            return sc;
        } catch (Exception e) { return null; }
    }

    /** For tools whose execute() returns {issues: [...], total: N}. */
    public Map<String, Object> buildForIssueList(String executeResultJson, String jiraUsername) {
        try {
            JsonNode root = MAPPER.readTree(executeResultJson);
            JsonNode issuesNode = root.path("issues");
            List<Object> issues = new java.util.ArrayList<>(issuesNode.size());
            issuesNode.forEach(n -> issues.add(normalizeIssue(n)));
            Map<String, Object> sc = new HashMap<>();
            sc.put("currentUser", buildCurrentUser(jiraUsername));
            sc.put("baseUrl",     config.getJiraBaseUrl());
            sc.put("issues",      issues);
            sc.put("totalCount",  root.path("total").asInt(issues.size()));
            return sc;
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> buildCurrentUser(String username) {
        Map<String, Object> u = new HashMap<>();
        if (username == null) { u.put("name", ""); u.put("displayName", ""); return u; }
        u.put("name", username);
        try {
            String full = userManager.getUserProfile(username).getFullName();
            u.put("displayName", full == null ? username : full);
        } catch (Exception ignored) {
            u.put("displayName", username);
        }
        return u;
    }

    /**
     * Normalize a single Jira issue JsonNode into the shape the widget expects.
     * PORT THIS BODY VERBATIM from JsonRpcHandler.java (the pre-Task-2 version,
     * around line 316 onward). The normalization includes:
     *   - priority: ensure object with {id, name, iconUrl} (was sometimes a string)
     *   - status: nest .category {id, key, name, colorName}
     *   - issuetype -> issue_type (snake_case rename)
     *   - Preserve fields: key, summary, description, assignee, reporter,
     *     created, updated, fixVersions, components, labels, priority, status,
     *     issue_type, project, parent
     */
    private Object normalizeIssue(JsonNode node) {
        // PASTE the existing JsonRpcHandler.normalizeIssue body here.
        // It's ~80 lines of conversion logic. Do NOT rewrite from scratch —
        // the widget depends on the exact shape produced by the current code.
        return MAPPER.convertValue(node, Map.class);   // PLACEHOLDER — replace
    }
}
```

The `normalizeIssue` body is the one piece you cannot guess — port it byte-for-byte from `JsonRpcHandler.java` (which is still on disk until Step 2.10).

- [ ] Step 2.8c — Verify by grepping the old file and counting the lines you're about to port:

```bash
sed -n '/private.*normalizeIssue/,/^    }$/p' src/main/java/com/atlassian/mcp/plugin/JsonRpcHandler.java | wc -l
```

Expected: 60–100 lines. Paste them into `ResourceContextBuilder.normalizeIssue(...)`.

- [ ] Step 2.8d — `OAuthAnonymousFilter` and `RateLimiter` were swept in Step 2.0. Verify:

```bash
grep -n "javax\." src/main/java/com/atlassian/mcp/plugin/rest/OAuthAnonymousFilter.java \
                  src/main/java/com/atlassian/mcp/plugin/rest/RateLimiter.java
```

Expected: no output.

### Step 2.9 — Update `atlassian-plugin.xml`

- [ ] Open `src/main/resources/atlassian-plugin.xml`. Make four changes:

  - Remove the `<rest key="mcp-rest" path="/mcp" version="1.0">` block (the JAX-RS MCP endpoint that's being replaced)
  - Keep `<rest key="mcp-admin-rest" path="/mcp-admin" version="1.0">` (the admin REST stays)
  - **Do not** add a `<servlet>` for the MCP transport — it's registered programmatically by `McpPluginLifecycle`
  - **Add five `<servlet-filter>` declarations** for the new filters, all targeting `/plugins/servlet/mcp` with the `before-dispatch` location

```xml
<!-- MCP transport filter chain — runs in front of the SDK servlet registered programmatically -->
<servlet-filter key="mcp-origin-filter" name="MCP Origin Validation Filter"
                class="com.atlassian.mcp.plugin.rest.OriginValidationFilter"
                location="before-dispatch" weight="100">
    <url-pattern>/plugins/servlet/mcp</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</servlet-filter>

<servlet-filter key="mcp-body-size-filter" name="MCP Body Size Limit Filter"
                class="com.atlassian.mcp.plugin.rest.BodySizeLimitFilter"
                location="before-dispatch" weight="200">
    <url-pattern>/plugins/servlet/mcp</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</servlet-filter>

<servlet-filter key="mcp-rate-limit-filter" name="MCP Rate Limit Filter"
                class="com.atlassian.mcp.plugin.rest.RateLimitFilter"
                location="before-dispatch" weight="300">
    <url-pattern>/plugins/servlet/mcp</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</servlet-filter>

<servlet-filter key="mcp-access-control-filter" name="MCP Access Control Filter"
                class="com.atlassian.mcp.plugin.rest.AccessControlFilter"
                location="before-dispatch" weight="350">
    <url-pattern>/plugins/servlet/mcp</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</servlet-filter>

<servlet-filter key="mcp-session-binding-filter" name="MCP Session Binding Filter"
                class="com.atlassian.mcp.plugin.rest.SessionBindingFilter"
                location="before-dispatch" weight="400">
    <url-pattern>/plugins/servlet/mcp</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</servlet-filter>

<servlet-filter key="mcp-security-headers-filter" name="MCP Security Headers Filter"
                class="com.atlassian.mcp.plugin.rest.SecurityHeadersFilter"
                location="before-dispatch" weight="500">
    <url-pattern>/plugins/servlet/mcp</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</servlet-filter>
```

Weights ascend (filters with lower weight execute first) so the chain runs: Origin → BodySize → RateLimit → **AccessControl** → SessionBinding → SecurityHeaders → SDK transport. The existing `OAuthAnonymousFilter` (weight=1) stays at the very front.

- [ ] Verify no other `<rest>` or `<servlet>` block was disturbed:

```bash
grep -n "<rest\|<servlet" src/main/resources/atlassian-plugin.xml
```

Expected: `<rest key="mcp-admin-rest"`, `<servlet key="mcp-admin-servlet"`, `<servlet key="mcp-oauth-servlet"`, `<servlet-filter key="mcp-oauth-anon-filter"`, and the five new MCP filters.

### Step 2.10 — Delete old transport sources

- [ ] Run:

```bash
git rm src/main/java/com/atlassian/mcp/plugin/rest/McpResource.java
git rm src/main/java/com/atlassian/mcp/plugin/JsonRpcHandler.java
git rm src/test/java/com/atlassian/mcp/plugin/JsonRpcHandlerTest.java
```

Expected: three files removed.

- [ ] Also delete the spike file:

```bash
git rm src/main/java/com/atlassian/mcp/plugin/rest/SpikeBootstrap.java
```

### Step 2.11 — Compile

- [ ] Run:

```bash
atlas-mvn -DskipTests compile 2>&1 | tail -30
```

Expected: compile succeeds. If there are import errors, they will be in two categories — fix both:

  1. Missing imports for new types (`McpServerFeatures.SyncToolSpecification`, etc.) — add to the top of `ToolRegistry`/`ResourceRegistry`
  2. References to deleted classes from anywhere else (`JsonRpcHandler` callers) — those should be the e2e tests + admin code; for now, only the e2e tests should still reference them, which we let break (Task 3 fixes the test suite)

### Step 2.12 — Deploy and smoke-test

- [ ] Run:

```bash
just deploy
```

Expected: JAR uploads, plugin enables in UPM.

- [ ] Smoke-test `initialize`:

```bash
source .credentials/jira.env
curl -i -X POST "$JIRA_URL/plugins/servlet/mcp" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}'
```

Expected: 200, `MCP-Session-Id` header, JSON-RPC `result` body with `serverInfo.name = "jira-mcp-plugin"`.

- [ ] Smoke-test `tools/list`:

```bash
SID=$(curl -s -D - -X POST "$JIRA_URL/plugins/servlet/mcp" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"s","version":"1"}}}' \
  | grep -i "^mcp-session-id:" | awk '{print $2}' | tr -d '\r')

curl -i -X POST "$JIRA_URL/plugins/servlet/mcp" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -H "MCP-Session-Id: $SID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

Expected: 200; **`tools` array contains exactly two entries: `get_user_profile` and `get_all_projects`** (the smoke-wired tools from Step 2.2a). The full 49-tool list lands in Task 3.

- [ ] Smoke-test `tools/call` — exercises the full adapter path (authHeader extraction → JiraRestClient call → CallToolResult shape):

```bash
curl -i -X POST "$JIRA_URL/plugins/servlet/mcp" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -H "MCP-Session-Id: $SID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_user_profile","arguments":{"user_identifier":"admin"}}}'
```

Expected: 200, response body (parsed from SSE envelope) contains `result.content[0].text` with a JSON-string of the admin user profile. **If this fails**, the adapter path is broken — authHeader is not reaching `JiraRestClient`, or the SDK's `CallToolResult` serialization differs from what we built. Debug before continuing — this is the load-bearing acceptance check for the transport commit.

- [ ] Smoke-test cross-user session binding rejection (use a second PAT):

```bash
# Reuse $SID with $JIRA_PAT_OTHER_USER → expect 403
curl -i -X POST "$JIRA_URL/plugins/servlet/mcp" \
  -H "Authorization: Bearer $JIRA_PAT_OTHER_USER" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -H "MCP-Session-Id: $SID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}'
```

Expected: 403, response body contains "session bound to a different user". If a second PAT isn't available, skip this check now; it's covered by the e2e test `session_user_binding` in Task 3.

### Step 2.13 — Commit

- [ ] Run:

```bash
git status --short    # confirm what's staged
git add src/ src/main/resources/atlassian-plugin.xml
git status --short
git commit -m "$(cat <<'EOF'
feat(transport): replace hand-rolled JSON-RPC with MCP SDK servlet transport

Delete McpResource (JAX-RS endpoint) and JsonRpcHandler (hand-rolled
JSON-RPC dispatch). The SDK's HttpServletStreamableServerTransportProvider
is registered programmatically via ServletModuleManager — the SDK provider
IS the servlet; no wrapper class.

Spike confirmed: ServletModuleManager.registerServlet(key, path, servlet)
registers the SDK transport at /plugins/servlet/mcp; initialize returns
200 with MCP-Session-Id header; tools/list returns the two smoke-wired
tools (get_user_profile, get_all_projects); tools/call get_user_profile
returns the admin profile via JiraRestClient.

The full 49-tool registration follows in Task 3.

New classes (all in com.atlassian.mcp.plugin.rest):
- McpBootstrap        — builds the SDK transport + McpSyncServer (singleton,
                        idempotent). Explicit Jackson mapper construction
                        instead of ServiceLoader/SCR discovery (Atlassian
                        plugin bundles embed third-party jars privately).
- McpPluginLifecycle  — LifecycleAware that registers the transport with
                        ServletModuleManager on plugin enable.
- McpToolAdapter      — stub for Task 3 (adapts McpTool -> SyncToolSpecification).
- JiraAuthContextExtractor — implements McpTransportContextExtractor;
                             surfaces {authHeader, jiraUser} to tool handlers.
- SessionBindingFilter — security boundary. Captures MCP-Session-Id from
                         initialize response, binds it to the authenticated
                         Jira user, rejects cross-user replay with 403 and
                         unknown/expired sessions with 401.
- OriginValidationFilter, BodySizeLimitFilter, RateLimitFilter,
  SecurityHeadersFilter — extracted from McpResource into reusable
                          jakarta.servlet.Filters.
- BufferedRequestWrapper, CapturingResponseWrapper — servlet wrappers
  supporting SessionBindingFilter (body re-read + header capture).

atlassian-plugin.xml: removes <rest key="mcp-rest">, adds five
<servlet-filter> declarations for the MCP filter chain (weights 100–500,
location=before-dispatch). The MCP servlet itself is registered
programmatically — no <servlet> descriptor for it.

OAuthAnonymousFilter and RateLimiter swept from javax to jakarta.

The branch compiles after this commit; tools/list returns the 2
smoke-wired tools; Task 3 expands the registration to the full 49.
EOF
)"
```

---

## Task 3: Commit 3 — Adapt 49 tools to `SyncToolSpecification` + complete jakarta sweep on tools

**Goal:** Extend `McpTool` with `uiResourceUri()` and `structuredContent()` optional defaults. Implement `McpToolAdapter` properly. Have `ToolRegistry.toSpecifications()` build the full 49-tool list. Sweep `javax.inject` → `jakarta.inject` across all 51 files under `tools/`. Wire batch tools' progress callbacks to the SDK's `exchange.progressNotification(...)`. Run the full e2e suite — expect green.

**Files (high level — exact list resolved by the sweep):**
- Modify: `src/main/java/com/atlassian/mcp/plugin/tools/McpTool.java` (add two default methods + ToolResponse record)
- Modify: `src/main/java/com/atlassian/mcp/plugin/tools/ToolRegistry.java` (implement `toSpecifications()`)
- Modify: `src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java` (full implementation, was stub in Task 2)
- Modify: all 49 tool files under `src/main/java/com/atlassian/mcp/plugin/tools/{issues,comments,...}/*.java` (javax → jakarta on `@Named`/`@Inject` imports only; bodies untouched)
- Modify: 5 UI-linked tool files (override `uiResourceUri()` and `structuredContent()`)
- Modify: 4 batch tool files (`batch_create_issues`, `batch_create_versions`, `batch_get_changelogs`, `get_issues_development_info`) to use new SDK progress callback
- Modify: `src/test/java/com/atlassian/mcp/plugin/e2e/McpEndpointE2ETest.java` (response-shape adjustments — `Accept` header, SSE envelope parsing)

### Step 3.1 — Extend the `McpTool` interface

- [ ] Open `src/main/java/com/atlassian/mcp/plugin/tools/McpTool.java`. Add three new members:

```java
import io.modelcontextprotocol.server.McpSyncServerExchange;

// ... existing interface members ...

    /**
     * For UI-linked tools (MCP Apps): the resource URI of the widget HTML.
     * Returns null for tools that don't render a widget.
     */
    default String uiResourceUri() { return null; }

    /**
     * For UI-linked tools (MCP Apps): the structured payload the widget renders.
     * Returns null for tools that don't render a widget.
     *
     * Called by McpToolAdapter AFTER execute() succeeds. The adapter passes:
     *   - the original args
     *   - the JSON string returned by execute()
     *   - the resolved Jira username from the transport context (may be null)
     */
    default Map<String, Object> structuredContent(Map<String, Object> args,
                                                  String executeResult,
                                                  String jiraUsername) {
        return null;
    }

    /**
     * Replace ProgressCallback with the SDK's exchange-based notification.
     * Batch tools override executeWithProgress and call
     *   exchange.progressNotification(...)
     * directly. The McpToolAdapter passes the exchange through.
     */
    default String executeWithSdkProgress(Map<String, Object> args, String authHeader,
                                          McpSyncServerExchange exchange)
            throws McpToolException {
        return execute(args, authHeader);
    }
```

The legacy `ProgressCallback` and `executeWithProgress` stay in place for one more commit (Task 4 deletes them after the 4 batch tools migrate to `executeWithSdkProgress`).

- [ ] Apply the edit.

### Step 3.2 — Implement `McpToolAdapter` fully (was a stub in Task 2)

- [ ] Open `src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java`. Replace its contents with:

```java
package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;

/** Converts each McpTool into a SyncToolSpecification the SDK can register. */
public final class McpToolAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpToolAdapter() {}

    public static SyncToolSpecification adapt(McpTool tool) {
        Tool.Builder t = Tool.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.inputSchema());

        // Annotations: destructive hint. Tool.Builder.annotations() takes
        // McpSchema.ToolAnnotations, NOT a Map. Verify the exact field name
        // (destructiveHint vs isDestructive) in /tmp/sdk-signatures.txt.
        if (tool.isDestructiveTool()) {
            t.annotations(io.modelcontextprotocol.spec.McpSchema.ToolAnnotations.builder()
                .destructiveHint(true)
                .build());
        }
        // UI-linked tools attach _meta.ui.resourceUri
        if (tool.uiResourceUri() != null) {
            t.meta(Map.of("ui", Map.of("resourceUri", tool.uiResourceUri())));
        }

        return SyncToolSpecification.builder()
            .tool(t.build())
            .callHandler((exchange, request) -> invoke(tool, exchange, request))
            .build();
    }

    private static CallToolResult invoke(McpTool tool,
                                         McpSyncServerExchange exchange,
                                         CallToolRequest request) {
        // Adjust .get(...) signature per Step 1.7f findings — may be get(String) returning Object
        String authHeader   = exchange.transportContext().get(JiraAuthContextExtractor.CTX_AUTH_HEADER, String.class);
        String jiraUsername = exchange.transportContext().get(JiraAuthContextExtractor.CTX_JIRA_USER,   String.class);

        try {
            String json = tool.supportsProgress()
                ? tool.executeWithSdkProgress(request.arguments(), authHeader, exchange)
                : tool.execute(request.arguments(), authHeader);

            CallToolResult.Builder b = CallToolResult.builder()
                .content(List.of(new TextContent(json)));

            Map<String, Object> structured = tool.structuredContent(request.arguments(), json, jiraUsername);
            if (structured != null) b.structuredContent(structured);

            return b.build();
        } catch (McpToolException e) {
            return CallToolResult.builder()
                .content(List.of(new TextContent(e.getMessage())))
                .isError(true)
                .build();
        }
    }
}
```

- [ ] Apply the edit.

### Step 3.3 — Implement `ToolRegistry.toSpecifications()` for real

- [ ] Open `src/main/java/com/atlassian/mcp/plugin/tools/ToolRegistry.java`. Locate the stub method added in Step 2.2a. Replace it with the real implementation that adapts every visible tool. The existing `ToolRegistry` already knows how to filter by capability (`requiredPluginKey`), config (disabled tools, read-only mode), and the existing public `getVisibleTools()` method returns the filtered set. Use it:

```java
public java.util.List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> toSpecifications() {
    return getVisibleTools().stream()
        .map(com.atlassian.mcp.plugin.rest.McpToolAdapter::adapt)
        .toList();
}
```

If `getVisibleTools()` does not exist in the current `ToolRegistry`, locate the method that produces the filtered list (likely called by the deleted `JsonRpcHandler`) — read the current `ToolRegistry.java` and reuse the same filter logic.

- [ ] Apply the edit. Also remove the temporary stub return from Step 2.2a.

### Step 3.4 — Sanity-check that the Task-2 sweep covered `tools/`

The mass `javax → jakarta` sweep was already done in Task 2 Step 2.0. Re-verify here:

```bash
grep -rnE "javax\.(servlet|ws\.rs|inject|annotation)" src/main/java/com/atlassian/mcp/plugin/tools/ | head
```

Expected: no output. If anything appears, sweep it the same way as Step 2.0 and commit the fix as part of Task 3.

### Step 3.5 — Wire UI-linked tools to `ResourceContextBuilder`

The UI-linked tool list was recorded in Task 2 Step 2.8 and the normalization logic ported to `ResourceContextBuilder`. This step wires each UI-linked tool to it.

The complication Codex caught: the existing `ToolRegistry` constructs tools manually (e.g. `new GetIssueTool(jiraRestClient)`), so `@Inject` on the tool class won't have `ResourceContextBuilder` available. We pass it through the constructor.

- [ ] Step 3.5a — Modify `ToolRegistry.java`'s constructor to inject `ResourceContextBuilder`:

```java
@Inject
public ToolRegistry(JiraRestClient client, ResourceContextBuilder contextBuilder,
                    /* existing deps */) {
    this.client = client;
    this.contextBuilder = contextBuilder;
    // ...
}
```

- [ ] Step 3.5b — Update the manual instantiations of UI-linked tools in `ToolRegistry` (likely in a `register()` or `init()` method) to pass `contextBuilder` through:

```java
register(new GetIssueTool(client, contextBuilder));
register(new SearchTool(client, contextBuilder));
register(new GetProjectIssuesTool(client, contextBuilder));
register(new GetBoardIssuesTool(client, contextBuilder));
register(new GetSprintIssuesTool(client, contextBuilder));
// All other tools still use `new XxxTool(client)` — no change
```

(Exact tool names come from the UI-linked list recorded in Step 2.8a. Adjust as needed.)

- [ ] Step 3.5c — For each of the 5 UI-linked tools, edit the `.java` file:

  1. Add a `private final ResourceContextBuilder contextBuilder` field.
  2. Add a second constructor parameter to accept it (keep the old single-arg constructor for backwards compatibility OR delete it — recommend deleting since `ToolRegistry` is the only caller and we updated it in 3.5b).
  3. Override `uiResourceUri()` and `structuredContent(args, executeResult, jiraUsername)`.

**Important:** the resource URI **must not** be hardcoded — `ResourceRegistry` appends a content hash for cache busting (e.g. `ui://jira/issue-card@a3f9b2`). The tool must ask the registry for the live URI. Inject `ResourceRegistry` into the tool alongside `ResourceContextBuilder`:

Example — single-issue shape (`GetIssueTool`):

```java
public final class GetIssueTool implements McpTool {
    private final JiraRestClient client;
    private final ResourceContextBuilder contextBuilder;
    private final ResourceRegistry resourceRegistry;   // for hash-suffixed URI

    public GetIssueTool(JiraRestClient client,
                        ResourceContextBuilder contextBuilder,
                        ResourceRegistry resourceRegistry) {
        this.client = client;
        this.contextBuilder = contextBuilder;
        this.resourceRegistry = resourceRegistry;
    }

    @Override
    public String uiResourceUri() {
        // Look up by logical name; registry returns the hash-suffixed URI it actually
        // exposes in resources/list. If the lookup is null (e.g. resource not registered),
        // return null so the adapter omits _meta.ui.
        return resourceRegistry.getResourceUri("issue-card");
    }

    @Override
    public Map<String, Object> structuredContent(Map<String, Object> args,
                                                 String executeResult,
                                                 String jiraUsername) {
        return contextBuilder.buildForSingleIssue(executeResult, jiraUsername);
    }

    // ... existing name(), description(), inputSchema(), execute() unchanged ...
}
```

The `ResourceRegistry.getResourceUri(String logicalName)` accessor needs to exist — if it doesn't on `main`, add it as part of Step 2.8 (when the registry is touched anyway).

**Acceptance gate for this step:** for each UI-linked tool, the structuredContent emitted by `tools/call` (parsed from the SSE envelope) must `diff` clean against the corresponding baseline at `/tmp/structured-baselines/<tool>.json` captured in Step 2.8.0. Run the `diff` explicitly:

```bash
# After Task 3 deploy, capture the new shape:
mkdir -p /tmp/structured-after
SID=$(curl -s -D - -X POST "$JIRA_URL/plugins/servlet/mcp" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"verify","version":"1"}}}' \
  | grep -i "^mcp-session-id:" | awk '{print $2}' | tr -d '\r')

for name in get_user_profile get_issue search; do
  curl -s -X POST "$JIRA_URL/plugins/servlet/mcp" \
    -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
    -H "Accept: application/json, text/event-stream" \
    -H "MCP-Protocol-Version: 2025-06-18" \
    -H "MCP-Session-Id: $SID" \
    -H "Content-Type: application/json" \
    -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"${name}\",\"arguments\":{}}}" \
    | grep '^data:' | sed 's/^data: //' \
    | jq '.result.structuredContent' > "/tmp/structured-after/${name}.json"
  diff "/tmp/structured-baselines/${name}.json" "/tmp/structured-after/${name}.json" \
    || { echo "FAIL: $name shape regressed"; exit 1; }
done
```

Expected: every `diff` exits 0. If any tool's structuredContent regressed, the widget will break — fix `ResourceContextBuilder` and re-run before committing Task 3.

Example — issue-list shape (`SearchTool`, `GetProjectIssuesTool`, `GetBoardIssuesTool`, `GetSprintIssuesTool`):

```java
@Override
public Map<String, Object> structuredContent(Map<String, Object> args,
                                             String executeResult,
                                             String jiraUsername) {
    return contextBuilder.buildForIssueList(executeResult, jiraUsername);
}
```

- [ ] Step 3.5d — Update the `McpTool` interface (committed in Step 3.1) so the default `structuredContent` signature **takes the username**:

```java
default Map<String, Object> structuredContent(Map<String, Object> args,
                                              String executeResult,
                                              String jiraUsername) {
    return null;
}
```

Adjust the call in `McpToolAdapter.invoke(...)`:

```java
String jiraUsername = exchange.transportContext()
    .get(JiraAuthContextExtractor.CTX_JIRA_USER, String.class);
Map<String, Object> structured = tool.structuredContent(request.arguments(), json, jiraUsername);
```

(If Step 1.7f revealed `transportContext().get` only takes a String — drop the `.class` arg and cast.)

- [ ] Step 3.5e — Verify no UI-linked tool's old constructor is referenced from tests:

```bash
grep -rn "new GetIssueTool\|new SearchTool\|new GetProjectIssuesTool\|new GetBoardIssuesTool\|new GetSprintIssuesTool" src/test/ | head
```

If any tests call the single-arg constructor, update them to pass a mock `ResourceContextBuilder`.

### Step 3.6 — Migrate the 4 batch tools to SDK progress

The 4 tools are: `BatchCreateIssuesTool`, `BatchCreateVersionsTool`, `BatchGetChangelogsTool`, `GetIssuesDevelopmentInfoTool`.

For each:

- [ ] Override `executeWithSdkProgress(args, authHeader, exchange)`. The body is the same as the current `executeWithProgress(args, authHeader, callback)` — replace each `callback.report(current, total, message)` with `exchange.progressNotification(...)`.

The SDK's `ProgressNotification.builder(...)` requires a **progress token** (the same one the client sent in `params._meta.progressToken`). The token is on the request; threading it through the adapter requires extending `McpTool.executeWithSdkProgress` signature OR exposing it on the exchange. Verify the actual SDK shape in `/tmp/sdk-signatures.txt` (look for `ProgressNotification.builder` — it may take `(Object progressToken, double progress)` or expose the token via `exchange.getProgressToken()`).

Example for `BatchCreateIssuesTool` (assumes the token is reachable from `exchange`; adjust per the verified signature):

```java
@Override
public String executeWithSdkProgress(Map<String, Object> args, String authHeader,
                                     io.modelcontextprotocol.server.McpSyncServerExchange exchange)
        throws McpToolException {
    // ... existing logic ...
    Object progressToken = exchange.getProgressToken();   // verify API — may be on request
    for (int i = 0; i < items.size(); i++) {
        // create issue ...
        exchange.progressNotification(
            io.modelcontextprotocol.spec.McpSchema.ProgressNotification.builder(
                progressToken, (double) (i + 1) / items.size())
                .total((double) items.size())
                .message("Created " + (i + 1) + " of " + items.size())
                .build());
    }
    // ... return result ...
}
```

If `exchange` does not expose the progress token, the adapter (`McpToolAdapter.invoke`) must read it from `request.meta()` or equivalent and pass it as an additional argument to `executeWithSdkProgress`. Update the `McpTool` interface accordingly. **Confirm the exact shape from Step 1.7f output before pasting.**

- [ ] Confirm `supportsProgress()` still returns `true` on each of the 4 tools.

### Step 3.7 — Update the e2e test for SSE envelope + Accept header

The 54 e2e tests currently send `Accept: application/json` only and assert `Content-Type: application/json` on tool responses. Both change with the SDK.

- [ ] Open `src/test/java/com/atlassian/mcp/plugin/e2e/McpEndpointE2ETest.java`. Find the helper that sends MCP requests (it's the most-called method, likely `postMcp(...)` or similar). Update:

  - Set `Accept: application/json, text/event-stream` on every POST
  - For responses with `Content-Type: text/event-stream`, parse the body as SSE: skip event-name lines, take the first `data:` line, parse that as JSON-RPC

A minimal SSE-aware parser:

```java
private static JsonNode parseResponse(HttpResponse<String> response) throws Exception {
    String contentType = response.headers().firstValue("Content-Type").orElse("");
    String body = response.body();
    if (contentType.startsWith("text/event-stream")) {
        // Find the first "data: ..." line
        for (String line : body.split("\\r?\\n")) {
            if (line.startsWith("data:")) {
                return MAPPER.readTree(line.substring(5).trim());
            }
        }
        throw new IllegalStateException("SSE body has no data: line");
    }
    return MAPPER.readTree(body);   // initialize response is still application/json
}
```

- [ ] Apply the edit. Adapt every existing test that called `response.body()` and parsed JSON directly to call `parseResponse(response)` instead.

### Step 3.8 — Run unit tests

- [ ] Run:

```bash
atlas-mvn test 2>&1 | tail -25
```

Expected: pass. The `JsonRpcHandlerTest` was deleted in Task 2; the remaining unit tests (`JiraRestClientTest`, `JiraMarkupConverterTest`, `SearchToolTest`) don't touch the transport.

If any test fails because it referenced the deleted `JsonRpcHandler`, delete that test or update it to use `McpToolAdapter` instead.

### Step 3.9 — Run the full e2e suite — first real verification

- [ ] Run:

```bash
just deploy-and-test 2>&1 | tee /tmp/e2e.log | tail -60
```

Expected: most of the 54 e2e tests pass. The spec predicts 5–10 will need shape updates. Likely categories of failure:

  - Tests that assert on the precise `Content-Type` of a tool response (was `application/json`, now `text/event-stream`)
  - Tests that assert on a specific JSON envelope shape if Jackson's serialization order is different
  - Tests for error responses with custom HTTP status codes that the SDK may emit differently

- [ ] For each failed test, read the failure message in `/tmp/e2e.log`, then either:

  1. Fix the test to match the SDK's actual output (preferred when the SDK output matches spec)
  2. Fix the implementation if the difference is a real bug

Iterate until `just e2e` reports `BUILD SUCCESS` with all tests passing.

### Step 3.10 — Smoke-test in Claude Desktop

This is the spec's manual acceptance criterion.

- [ ] In Claude Desktop, add the MCP server with URL `$JIRA_URL/plugins/servlet/mcp` (use the Bearer token / OAuth flow already configured).
- [ ] Call `get_issue` with a known issue key (e.g. `JIRA-1` or whatever exists in the test instance).
- [ ] Verify the response renders **as a widget**, not just text. If the widget renders the issue card with status badge, assignee, etc., MCP Apps parity is preserved.

If the widget doesn't render, check:

  1. `resources/list` returns the `ui://jira/issue-card` resource (it won't yet — `ResourceRegistry.toSpecifications()` is still stubbed, that's Task 4)
  2. `tools/list` for `get_issue` includes `_meta.ui.resourceUri` (this should work after Step 3.5)

Note: this smoke test partially fails until Task 4 — that's OK. The fully-functional widget is the Task 4 acceptance gate.

### Step 3.11 — Commit

- [ ] Run:

```bash
git status --short
git add src/
git status --short
git commit -m "$(cat <<'EOF'
refactor(tools): adapt 49 tools to SyncToolSpecification + jakarta sweep

McpTool interface gains two optional defaults for MCP Apps:
- uiResourceUri()           — 5 UI-linked tools override
- structuredContent()       — same 5 tools override; widget data
And one progress hook aligned with the SDK:
- executeWithSdkProgress()  — 4 batch tools override; uses
                              exchange.progressNotification(...)

McpToolAdapter is now complete: per tool, builds a Tool with name +
description + inputSchema + (destructiveHint annotation if applicable) +
(_meta.ui.resourceUri if UI-linked), and a call handler that wraps
McpTool.execute() and copies structuredContent into CallToolResult when
present.

ToolRegistry.toSpecifications() materializes the full visible-tool list,
respecting the existing capability gating (requiredPluginKey), config
filtering (disabled tools, read-only mode), and write-mode hiding.

All 49 tool files swept from javax.inject to jakarta.inject. Bodies of
execute() unchanged — same Jira REST calls, same response shapes.

E2E test harness updated to send Accept: application/json,
text/event-stream and to parse single-event SSE envelopes for tool
responses (initialize still returns application/json).

`just deploy-and-test` is green: 54/54 e2e tests pass against the live
Jira 11 instance.
EOF
)"
```

---

## Task 4: Commit 4 — MCP Apps resources + remaining jakarta sweep

**Goal:** `ResourceRegistry.toSpecifications()` returns the real list with the dual-metadata (`_meta.ui` for Claude, flat `openai/widget*` keys for ChatGPT). Sweep `OAuthServlet`, `AdminServlet`, `ConfigResource`, `McpPluginConfig`, `OAuthStateStore`, `JiraRestClient` from javax to jakarta. Verify the widget renders end-to-end in Claude Desktop.

**Files:**
- Modify: `src/main/java/com/atlassian/mcp/plugin/ResourceRegistry.java` (fill in `toSpecifications()` properly)
- Modify: `src/main/java/com/atlassian/mcp/plugin/rest/OAuthServlet.java` (javax → jakarta on imports)
- Modify: `src/main/java/com/atlassian/mcp/plugin/admin/AdminServlet.java`
- Modify: `src/main/java/com/atlassian/mcp/plugin/admin/ConfigResource.java`
- Modify: `src/main/java/com/atlassian/mcp/plugin/config/McpPluginConfig.java`
- Modify: `src/main/java/com/atlassian/mcp/plugin/config/OAuthStateStore.java`
- Modify: `src/main/java/com/atlassian/mcp/plugin/JiraRestClient.java`

### Step 4.1 — Read the current `ResourceRegistry.java` to capture the exact `_meta` shape

The spec mandates preserving the existing flat OpenAI keys. Verify them by reading the current code:

- [ ] Run:

```bash
grep -nE "openai/widget|_meta\.|meta\.put|meta\.set" src/main/java/com/atlassian/mcp/plugin/ResourceRegistry.java
```

Record the exact keys and values currently set. Expected keys (from earlier discovery): `openai/widgetDescription`, `openai/widgetPrefersBorder`, `openai/widgetCSP`, `openai/widgetDomain`. Plus `ui` (nested map) for Claude.

### Step 4.2 — Implement `ResourceRegistry.toSpecifications()` for real

- [ ] Replace the stub `toSpecifications()` (added in Step 2.2b) with the real version. Locate the existing code that builds the `_meta` map for a `ui://jira/issue-card@{hash}` resource — that logic exists today and just needs re-shaping into a `SyncResourceSpecification`:

```java
public java.util.List<io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification> toSpecifications() {
    java.util.List<io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification> specs = new java.util.ArrayList<>();
    for (UiResource ui : getResources()) {                       // existing internal API
        io.modelcontextprotocol.spec.McpSchema.Resource res =
            io.modelcontextprotocol.spec.McpSchema.Resource.builder(ui.uri(), ui.name())
                .description(ui.description())
                .mimeType("text/html")
                .meta(buildMeta(ui))                              // dual-metadata map
                .build();
        specs.add(new io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification(
            res,
            (exchange, request) -> readResource(ui)               // returns ReadResourceResult
        ));
    }
    return specs;
}

private Map<String, Object> buildMeta(UiResource ui) {
    java.util.Map<String, Object> meta = new java.util.HashMap<>();
    // Claude side — nested
    meta.put("ui", java.util.Map.of(
        "resourceUri", ui.uri(),
        "preferredSize", java.util.Map.of("width", 720, "height", 480)
    ));
    // ChatGPT side — flat openai/widget* keys (verbatim from pre-rebuild code)
    meta.put("openai/widgetDescription", ui.description());
    meta.put("openai/widgetPrefersBorder", true);
    meta.put("openai/widgetCSP", buildWidgetCsp(ui));
    meta.put("openai/widgetDomain", getBaseUrl());
    return meta;
}
```

(Adjust class names — `UiResource`, `getResources()`, `buildWidgetCsp`, `readResource` — to match the current `ResourceRegistry`'s actual API.)

- [ ] Apply the edit. If the current `ResourceRegistry` has helper methods (`buildWidgetCsp`, `readResource`), keep them. If their logic is inline, extract to private methods so the public `toSpecifications()` reads cleanly.

### Step 4.3 — Sanity-check Zone B files

Same as Step 3.4 — the mass sweep was done in Task 2 Step 2.0. Verify here:

```bash
grep -rnE "javax\.(servlet|ws\.rs|inject|annotation)" \
  src/main/java/com/atlassian/mcp/plugin/rest/OAuthServlet.java \
  src/main/java/com/atlassian/mcp/plugin/admin/ \
  src/main/java/com/atlassian/mcp/plugin/config/ \
  src/main/java/com/atlassian/mcp/plugin/JiraRestClient.java
```

Expected: no output.

### Step 4.4 — Compile

- [ ] Run:

```bash
atlas-mvn -DskipTests compile 2>&1 | tail -15
```

Expected: success. If any javax remains (likely from non-spec packages like `javax.crypto` — keep those), the grep filter in 4.3 was too greedy; un-sweep those false positives.

### Step 4.5 — Run full e2e suite + Claude Desktop smoke test

- [ ] Run:

```bash
just deploy-and-test 2>&1 | tee /tmp/e2e4.log | tail -30
```

Expected: 54/54 green. The MCP Apps tests (under "Security" / "Tools list" categories per CLAUDE.md) now exercise the resource path properly.

- [ ] Manual smoke test — Claude Desktop:

  1. Disconnect and reconnect the MCP server to refresh the resource list
  2. Call `get_issue JIRA-1` (or a known issue)
  3. Verify the issue card widget renders with: title, status badge, assignee avatar/link, priority, issue type icon
  4. If using ChatGPT, do the same — verify the OpenAI widget envelope works

If the widget renders text only (no card), inspect:

```bash
curl -s -X POST "$JIRA_URL/plugins/servlet/mcp" \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -H "MCP-Session-Id: $SID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"resources/list","params":{}}' | grep -oE "openai/widget[A-Za-z]+|\"ui\"" | sort -u
```

Expected: at least `openai/widgetDescription`, `openai/widgetPrefersBorder`, `openai/widgetCSP`, `openai/widgetDomain`, `"ui"`. If any of these are missing, the `buildMeta` map in Step 4.2 is incomplete — fix.

### Step 4.6 — Commit

- [ ] Run:

```bash
git status --short
git add src/
git commit -m "$(cat <<'EOF'
refactor(resources+rest): MCP Apps -> SyncResourceSpecification + jakarta sweep

ResourceRegistry.toSpecifications() now materializes the ui:// issue-card
resource as a SyncResourceSpecification, with the full dual-metadata:
  - nested "ui" object for Claude Desktop (resourceUri + preferredSize)
  - flat openai/widget* keys for ChatGPT (Description, PrefersBorder,
    CSP, Domain) — preserved verbatim from the pre-rebuild shape

javax -> jakarta sweep on OAuthServlet, OAuthAnonymousFilter (was earlier),
AdminServlet, ConfigResource, McpPluginConfig, OAuthStateStore,
JiraRestClient. Tree-wide grep "javax\." in src/main/java now empty.

`just deploy-and-test` green. Claude Desktop issue-card widget renders
end-to-end against live Jira 11.
EOF
)"
```

---

## Task 5: Commit 5 — Docs flip + version bump + CHANGELOG

**Goal:** Update CLAUDE.md "Hard-Won Lessons" with citations from the on-disk jakarta-era scanner reference. Update `docs/rkstack/plans/2026-04-06-oauth-proxy.md` to flip the "javax not jakarta" rule. Bump plugin version. Update CHANGELOG.

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/rkstack/plans/2026-04-06-oauth-proxy.md`
- Modify: `pom.xml` (version bump only)
- Modify: `CHANGELOG.md`
- Modify: `README.md` (target Jira version table)

### Step 5.1 — Verify each Hard-Won Lesson against the on-disk jakarta scanner reference

- [ ] For each Hard-Won Lesson in `CLAUDE.md`, decide: keep, update, flip, or delete. Use the jakarta-era scanner at `.upstream/atlassian-spring-scanner` (branch `6.0.x`) as the source of truth.

Concrete checks:

```bash
# 1. "javax, NOT jakarta" — FLIP
echo "Lesson 1: javax/jakarta — FLIP confirmed by Tasks 1–4"

# 2. "Spring Scanner requires scan-indexes XML"
grep -r "atlassian-scanner:scan-indexes" /Volumes/Devops/Git/Github/mrkhachaturov/atlassian-mcp-plugin/.upstream/atlassian-spring-scanner/atlassian-spring-scanner-test/ | head
# If still required → KEEP. If not used anywhere in 6.0.x scanner tests → mark "verify"

# 3. "Plugin key must match Bundle-SymbolicName" — KEEP (unchanged by jakarta)
echo "Lesson 3: KEEP"

# 4. "DynamicImport-Package is required"
grep -r "DynamicImport-Package" /Volumes/Devops/Git/Github/mrkhachaturov/atlassian-mcp-plugin/.upstream/atlassian-spring-scanner/*/pom.xml | head
# If the jakarta scanner poms still use it → KEEP. If not → UPDATE with caveat

# 5. "Anonymous REST access in Jira 10: @UnrestrictedAccess"
# Check if @UnrestrictedAccess still exists in Jira 11 atlassian-annotations
unzip -l ~/.m2/repository/com/atlassian/annotations/atlassian-annotations/*/atlassian-annotations-*.jar 2>/dev/null | grep -i "UnrestrictedAccess\|PermitAll" | head

# 6. "REST package scan must be specific"
# Whether Spring 6 / scanner 6.0.2 still has this conflict is hard to prove without a regression test.
# Mark as "VERIFY POST-MERGE" — keep the lesson, add a note.

# 7. "Version bumps bust JS/CSS cache" — KEEP (generic web caching, not platform-dependent)

# 8. "Plugin enable timeout: jira-migration plugin"
# Jira 11 may or may not ship the migration plugin. Check on the live instance:
# (post-deploy: look at UPM for "Atlassian Jira - Plugins - ATST Health Checks" or similar)

# 9. "Write tools must structure Jira payloads correctly" — KEEP (Jira REST API quirk)
```

- [ ] Apply the edits to `CLAUDE.md` per the verification results. Specifically:

  - Replace the **javax, NOT jakarta** section with:

    ```markdown
    ### jakarta, NOT javax
    Jira 11+ runs on Tomcat 10.1 + Spring 6 + Jakarta EE 10. Always use `jakarta.servlet`, `jakarta.ws.rs`, `jakarta.inject` imports — never `javax.*`. The platform BOM at `com.atlassian.platform.dependencies:platform-public-api:8.1.13` manages the spec-jar versions.
    ```

  - Replace **Always use `javax.*` imports, never `jakarta.*`** in Critical Rules with **Always use `jakarta.*` imports, never `javax.*`**.

  - Mark **REST package scan must be specific** with `(verify post-merge — Spring 6 may have relaxed this)`.

  - Update the **MCP endpoint** value in Key Identifiers from `POST /rest/mcp/1.0/` to `POST /plugins/servlet/mcp`.

  - Update the **Target Jira** value from `Data Center 10.x` to `Data Center 11.x`.

  - Update the **Architecture** table:
    - Replace `MCP endpoint | JAX-RS at /rest/mcp/1.0/ — Streamable HTTP (JSON-RPC 2.0 + SSE)` with `MCP endpoint | Servlet at /plugins/servlet/mcp — SDK transport (io.modelcontextprotocol.sdk:mcp-core 2.0.0-M2), registered programmatically via ServletModuleManager`

  - Update **Project Structure** to reflect deleted files (`McpResource.java`, `JsonRpcHandler.java`) and new files (`McpBootstrap`, `McpPluginLifecycle`, `McpToolAdapter`, `JiraAuthContextExtractor`, six filters).

### Step 5.2 — Update `docs/rkstack/plans/2026-04-06-oauth-proxy.md`

- [ ] Open the file. Find the line that mandates `javax.*` imports (it's around line 9 per the spec's prior research). Replace it with the jakarta inverse:

```markdown
Always use `jakarta.*` imports — never `javax.*`. Jira 11+ runs Jakarta EE 10.
```

- [ ] Apply the edit.

### Step 5.3 — Bump plugin version in `pom.xml`

The current version is `1.2.1-SNAPSHOT`. This is a major architecture change → bump to `1.3.0-SNAPSHOT`.

- [ ] Edit `pom.xml`:

```xml
<version>1.3.0-SNAPSHOT</version>
```

(Replaces `1.2.1-SNAPSHOT`.)

- [ ] Also update `SERVER_VERSION_FALLBACK` in `McpBootstrap.java` to match: `"1.3.0-SNAPSHOT"`. (If a `pluginVersion` injection mechanism is available, prefer that — but the literal is fine for now.)

### Step 5.4 — Update `CHANGELOG.md`

- [ ] Open `CHANGELOG.md`. Add a new entry at the top:

```markdown
## [1.3.0] — 2026-05-21

### Changed
- **Targets Jira Data Center 11.x.** The plugin no longer loads on Jira 10.x.
- Rebuilt MCP transport on the official MCP Java SDK
  (`io.modelcontextprotocol.sdk:mcp-core:2.0.0-M2`). The hand-rolled
  JSON-RPC dispatcher (`JsonRpcHandler`) and Streamable HTTP endpoint
  (`McpResource`) are deleted (~1,200 lines).
- **MCP endpoint moved** from `/rest/mcp/1.0/` (JAX-RS) to
  `/plugins/servlet/mcp` (registered programmatically via
  `ServletModuleManager`). Existing MCP client connections must update
  the URL.
- **SDK transport enforces MCP spec strictly:**
  - `Accept: application/json, text/event-stream` is required on every
    POST. Clients sending only `application/json` will receive 400.
  - Most non-initialize JSON-RPC responses are returned as
    `Content-Type: text/event-stream` (single-event SSE envelope around
    the JSON-RPC payload).

### Internal
- Java 17 → 21; Spring 5 → 6.2.15; Tomcat 9 → 10.1; jakarta everywhere.
- `javax.servlet`, `javax.ws.rs`, `javax.inject` swept to `jakarta.*` —
  31 imports across 14 files.
- New `SessionBindingFilter` enforces the existing security invariant
  that an MCP session ID cannot be replayed by a different Jira user.
- Atlassian platform BOM (`8.1.13`) imported — manages jakarta + Spring +
  Jackson + Atlassian dependency versions transitively.
```

### Step 5.5 — Update `README.md` target-Jira badge or version table

- [ ] Open `README.md`. Find any reference to "Jira Data Center 10.x" or "Jira 10" — replace with "Jira Data Center 11.x". Update the dependency-version table if present.

### Step 5.6 — Final `just deploy-and-test`

- [ ] Run:

```bash
just deploy-and-test 2>&1 | tee /tmp/e2e-final.log | tail -30
```

Expected: 54/54 green. This is the merge gate.

### Step 5.7 — Commit

- [ ] Run:

```bash
git status --short
git add CLAUDE.md docs/rkstack/plans/2026-04-06-oauth-proxy.md pom.xml CHANGELOG.md README.md src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java
git status --short
git commit -m "$(cat <<'EOF'
docs: flip jakarta rules in CLAUDE.md + bump to v1.3.0

CLAUDE.md Hard-Won Lessons updated:
- "javax, NOT jakarta" -> "jakarta, NOT javax" (flipped)
- Architecture table: MCP endpoint now /plugins/servlet/mcp (servlet,
  programmatic registration via ServletModuleManager), Target Jira 11.x
- Project Structure reflects deleted McpResource/JsonRpcHandler and new
  bootstrap/adapter/filter classes
- "REST package scan must be specific" marked verify-post-merge under
  Spring 6
- Critical Rules: "Always use jakarta.*" rule replaces the javax rule

docs/rkstack/plans/2026-04-06-oauth-proxy.md:9 — javax rule flipped to
jakarta.

pom.xml: 1.2.1-SNAPSHOT -> 1.3.0-SNAPSHOT (major architecture change).
McpBootstrap SERVER_VERSION_FALLBACK matches.

CHANGELOG.md: new 1.3.0 section. Calls out the moved MCP endpoint URL
(/rest/mcp/1.0/ -> /plugins/servlet/mcp) and the SDK's strict Accept
header / SSE response-shape changes — these are visible to MCP clients.

README.md target Jira updated 10.x -> 11.x.

Final `just deploy-and-test` green: 54/54 e2e tests pass against live
Jira 11.3.6.

Branch is ready to merge.
EOF
)"
```

---

## Post-merge checklist (outside this plan but worth recording)

After the branch merges to `main`:

- [ ] Tag the release: `git tag v1.3.0 && git push --tags`
- [ ] Update the Marketplace listing (if applicable) with the new minimum Jira version 11.0
- [ ] Notify users that the MCP endpoint URL changed from `/rest/mcp/1.0/` to `/plugins/servlet/mcp`
- [ ] Open follow-up tickets for the deferred work captured in the spec's "Out of scope" section:
  - `@ScopesAllowed` for OAuth 2.0 client credentials (2LO)
  - Native Jira Java APIs for hot-path read tools
  - Atlaskit / AUI Dropdown 2 migration
  - Bump `mcp-core` to 2.0 GA when published
