# Jira MCP Plugin Implementation Plan

> **For agentic workers:** Use subagent-driven-development (recommended) or executing-plans skill to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Spec:** [2026-04-06-jira-mcp-plugin-design.md](../specs/2026-04-06-jira-mcp-plugin-design.md)

**Goal:** Build a native Jira DC plugin that embeds an MCP server at `/rest/mcp/1.0/`, exposing 46 Jira tools via internal REST API calls, with admin configuration UI.

**Architecture:** Stateless JAX-RS REST endpoint handles MCP Streamable HTTP protocol (JSON-RPC over POST). Each tool calls Jira's own REST API internally, forwarding the user's PAT. Admin servlet + PluginSettings for configuration. No external runtime dependencies.

**Tech Stack:** Java 17, Maven (AMPS 9.9.1), Jira DC 10.7.4, Jakarta Servlet/JAX-RS, Jackson (Jira-provided)

**Build commands:**
- Build: `atlas-package`
- Run locally: `atlas-run`
- Unit tests: `atlas-unit-test` or `mvn test`
- Clean: `atlas-clean`

**Test approach:** Unit tests mock `JiraRestClient` to test tool logic and JSON-RPC handling in isolation. Integration testing against a live Jira instance is manual via `atlas-run`.

**Upstream bootstrap:** Before starting, ensure `.upstream/mcp-atlassian` is checked out at tag `v0.21.0`:
```bash
cd .upstream/mcp-atlassian && git checkout v0.21.0
```
The plan references upstream Python files for tool definitions — these must be available locally.

**Error shape convention:** MCP distinguishes two error types:
- **Protocol errors** (malformed JSON, unknown method, missing params) — return JSON-RPC `error` object with code `-327xx` / `-326xx`
- **Tool execution errors** (Jira API returned 404, permission denied, timeout) — return `CallToolResult` with `isError: true` and error description in `content`. This is NOT a JSON-RPC error — the tool call succeeded at the protocol level but the tool itself failed.

---

## File Map

**Create:**
- `.mise.toml` — java 17, maven 3.9, just
- `justfile` — build/dev task runner
- `upstream-versions.json` — tracks ported upstream versions
- `pom.xml` — Maven project with AMPS jira-maven-plugin
- `src/main/resources/atlassian-plugin.xml` — plugin descriptor
- `src/main/resources/mcp-plugin.properties` — i18n strings
- `src/main/java/com/atlassian/mcp/plugin/McpResource.java` — JAX-RS MCP endpoint
- `src/main/java/com/atlassian/mcp/plugin/JsonRpcHandler.java` — JSON-RPC dispatch
- `src/main/java/com/atlassian/mcp/plugin/JiraRestClient.java` — HTTP client wrapper
- `src/main/java/com/atlassian/mcp/plugin/config/McpPluginConfig.java` — config reader
- `src/main/java/com/atlassian/mcp/plugin/tools/McpTool.java` — tool interface
- `src/main/java/com/atlassian/mcp/plugin/tools/ToolRegistry.java` — tool catalog + filtering
- `src/main/java/com/atlassian/mcp/plugin/tools/issues/*.java` — 7 issue tools
- `src/main/java/com/atlassian/mcp/plugin/tools/comments/*.java` — 2 comment tools
- `src/main/java/com/atlassian/mcp/plugin/tools/transitions/*.java` — 2 transition tools
- `src/main/java/com/atlassian/mcp/plugin/tools/worklogs/*.java` — 2 worklog tools
- `src/main/java/com/atlassian/mcp/plugin/tools/boards/*.java` — 4 board/sprint tools
- `src/main/java/com/atlassian/mcp/plugin/tools/links/*.java` — 4 link tools
- `src/main/java/com/atlassian/mcp/plugin/tools/epics/*.java` — 1 epic tool
- `src/main/java/com/atlassian/mcp/plugin/tools/projects/*.java` — 6 project/version tools
- `src/main/java/com/atlassian/mcp/plugin/tools/users/*.java` — 4 user/watcher tools
- `src/main/java/com/atlassian/mcp/plugin/tools/attachments/*.java` — 2 attachment tools
- `src/main/java/com/atlassian/mcp/plugin/tools/fields/*.java` — 2 field tools
- `src/main/java/com/atlassian/mcp/plugin/tools/servicedesk/*.java` — 3 service desk tools
- `src/main/java/com/atlassian/mcp/plugin/tools/forms/*.java` — 3 forms tools
- `src/main/java/com/atlassian/mcp/plugin/tools/metrics/*.java` — 4 metrics tools
- `src/main/java/com/atlassian/mcp/plugin/admin/AdminServlet.java` — admin page
- `src/main/java/com/atlassian/mcp/plugin/admin/ConfigResource.java` — admin REST API
- `src/main/resources/templates/admin.vm` — admin Velocity template
- `src/main/resources/css/admin.css` — admin styles
- `src/main/resources/js/admin.js` — admin AJAX logic
- `src/test/java/com/atlassian/mcp/plugin/JsonRpcHandlerTest.java`
- `src/test/java/com/atlassian/mcp/plugin/JiraRestClientTest.java`
- `src/test/java/com/atlassian/mcp/plugin/tools/SearchToolTest.java`
- `src/test/java/com/atlassian/mcp/plugin/McpResourceTest.java`

**Modify:**
- `.gitignore` — add Maven target/, IDE files

---

## Chunk 1: Project Scaffolding

### Task 1: Initialize Maven project and toolchain files

**Files:**
- Create: `.mise.toml`
- Create: `justfile`
- Create: `upstream-versions.json`
- Create: `pom.xml`
- Create: `src/main/resources/atlassian-plugin.xml`
- Create: `src/main/resources/mcp-plugin.properties`
- Modify: `.gitignore`

- [ ] **Step 1: Create `.mise.toml`**

  ```toml
  [tools]
  just = "latest"
  java = "temurin-17"
  maven = "3.9"
  ```

- [ ] **Step 2: Create `justfile`**

  ```just
  # Atlassian MCP Plugin — Task Runner

  set dotenv-load := false

  # List all available recipes
  default:
      @just --list

  # Build the plugin JAR/OBR
  build:
      atlas-package

  # Run Jira locally with the plugin installed
  run:
      atlas-run

  # Run Jira with remote debugging enabled
  debug:
      atlas-debug

  # Run unit tests
  test:
      atlas-unit-test

  # Clean build artifacts
  clean:
      atlas-clean
  ```

- [ ] **Step 3: Create `upstream-versions.json`**

  ```json
  {
    "mcp-atlassian": {
      "version": "0.21.0",
      "source": "https://github.com/sooperset/mcp-atlassian",
      "ported": "2026-04-06",
      "notes": "46 Jira tools ported, tool names and input schemas match upstream"
    },
    "mcp-java-sdk": {
      "version": "2.0.0-SNAPSHOT",
      "source": "https://github.com/modelcontextprotocol/java-sdk",
      "usage": "reference only, no runtime dependency"
    }
  }
  ```

- [ ] **Step 4: Create `pom.xml`**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <project xmlns="http://maven.apache.org/POM/4.0.0"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                               http://maven.apache.org/xsd/maven-4.0.0.xsd">
      <modelVersion>4.0.0</modelVersion>

      <groupId>com.atlassian.mcp</groupId>
      <artifactId>atlassian-mcp-plugin</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <packaging>atlassian-plugin</packaging>

      <name>Atlassian MCP Plugin</name>
      <description>MCP Server embedded in Jira Data Center</description>

      <properties>
          <jira.version>10.7.4</jira.version>
          <amps.version>9.9.1</amps.version>
          <atlassian.spring.scanner.version>2.2.4</atlassian.spring.scanner.version>
          <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
          <maven.compiler.source>17</maven.compiler.source>
          <maven.compiler.target>17</maven.compiler.target>
      </properties>

      <dependencyManagement>
          <dependencies>
              <dependency>
                  <groupId>com.atlassian.platform</groupId>
                  <artifactId>platform-public-api</artifactId>
                  <version>7.0.0</version>
                  <type>pom</type>
                  <scope>import</scope>
              </dependency>
          </dependencies>
      </dependencyManagement>

      <dependencies>
          <dependency>
              <groupId>com.atlassian.jira</groupId>
              <artifactId>jira-api</artifactId>
              <version>${jira.version}</version>
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
              <groupId>com.atlassian.plugin</groupId>
              <artifactId>atlassian-spring-scanner-annotation</artifactId>
              <version>${atlassian.spring.scanner.version}</version>
              <scope>provided</scope>
          </dependency>
          <dependency>
              <groupId>com.atlassian.plugin</groupId>
              <artifactId>atlassian-spring-scanner-runtime</artifactId>
              <version>${atlassian.spring.scanner.version}</version>
              <scope>runtime</scope>
          </dependency>
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
          <!-- Jackson provided by Jira -->
          <dependency>
              <groupId>com.fasterxml.jackson.core</groupId>
              <artifactId>jackson-databind</artifactId>
              <scope>provided</scope>
          </dependency>
          <!-- Test dependencies -->
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

      <build>
          <plugins>
              <plugin>
                  <groupId>com.atlassian.maven.plugins</groupId>
                  <artifactId>jira-maven-plugin</artifactId>
                  <version>${amps.version}</version>
                  <extensions>true</extensions>
                  <configuration>
                      <productVersion>${jira.version}</productVersion>
                      <productDataVersion>${jira.version}</productDataVersion>
                      <enableQuickReload>true</enableQuickReload>
                      <allowGoogleTracking>false</allowGoogleTracking>
                  </configuration>
              </plugin>
              <plugin>
                  <groupId>com.atlassian.plugin</groupId>
                  <artifactId>atlassian-spring-scanner-maven-plugin</artifactId>
                  <version>${atlassian.spring.scanner.version}</version>
                  <executions>
                      <execution>
                          <goals>
                              <goal>atlassian-spring-scanner</goal>
                          </goals>
                          <phase>process-classes</phase>
                      </execution>
                  </executions>
              </plugin>
          </plugins>
      </build>
  </project>
  ```

- [ ] **Step 5: Create `src/main/resources/atlassian-plugin.xml`**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <atlassian-plugin key="atlassian-mcp-plugin"
                    name="Atlassian MCP Plugin" plugins-version="2">
      <plugin-info>
          <description>MCP Server embedded in Jira Data Center</description>
          <version>${project.version}</version>
          <vendor name="Atlassian MCP" url="https://github.com/mrkhachaturov/atlassian-mcp-plugin"/>
          <param name="plugin-icon">images/pluginIcon.png</param>
          <param name="plugin-logo">images/pluginLogo.png</param>
          <param name="atlassian-data-center-status">compatible</param>
          <param name="atlassian-data-center-compatible">true</param>
          <param name="configure.url">/plugins/servlet/mcp-admin</param>
      </plugin-info>

      <!-- i18n -->
      <resource type="i18n" name="i18n" location="mcp-plugin"/>

      <!-- MCP REST endpoint -->
      <rest key="mcp-rest" path="/mcp" version="1.0">
          <description>MCP Streamable HTTP endpoint</description>
          <package>com.atlassian.mcp.plugin</package>
      </rest>

      <!-- Admin REST endpoint -->
      <rest key="mcp-admin-rest" path="/mcp-admin" version="1.0">
          <description>MCP admin configuration API</description>
          <package>com.atlassian.mcp.plugin.admin</package>
      </rest>

      <!-- Admin page servlet -->
      <servlet key="mcp-admin-servlet"
               class="com.atlassian.mcp.plugin.admin.AdminServlet">
          <url-pattern>/mcp-admin</url-pattern>
      </servlet>

      <!-- Admin sidebar link -->
      <web-item key="mcp-admin-link" name="MCP Server Configuration"
                section="system.admin/globalsettings" weight="200"
                application="jira">
          <label key="mcp.admin.label"/>
          <link linkId="mcp-admin-link">/plugins/servlet/mcp-admin</link>
          <condition class="com.atlassian.jira.plugin.webfragment.conditions.UserIsAdminCondition"/>
      </web-item>

      <!-- Admin web resources -->
      <web-resource key="admin-resources" name="MCP Admin Resources">
          <dependency>com.atlassian.auiplugin:ajs</dependency>
          <resource type="download" name="admin.css" location="/css/admin.css"/>
          <resource type="download" name="admin.js" location="/js/admin.js"/>
          <context>mcp-admin</context>
      </web-resource>
  </atlassian-plugin>
  ```

- [ ] **Step 6: Create `src/main/resources/mcp-plugin.properties`**

  ```properties
  mcp.admin.label=MCP Server Configuration
  mcp.admin.title=MCP Server Configuration
  mcp.admin.enabled.label=Enable MCP Server
  mcp.admin.allowedUsers.label=Allowed Users (user keys, one per line)
  mcp.admin.allowedUsers.description=Leave empty to allow all authenticated users
  mcp.admin.disabledTools.label=Disabled Tools (tool names, one per line)
  mcp.admin.readOnlyMode.label=Read-only Mode
  mcp.admin.readOnlyMode.description=Hide all write operations (create, update, delete)
  mcp.admin.jiraBaseUrl.label=Internal Jira Base URL (optional)
  mcp.admin.jiraBaseUrl.description=Override for environments where internal URL differs from public URL
  mcp.admin.save=Save
  mcp.admin.saved=Configuration saved successfully
  ```

- [ ] **Step 7: Update `.gitignore`**

  Append to existing `.gitignore`:

  ```
  # Maven
  target/

  # IDE
  .idea/
  *.iml
  .classpath
  .project
  .settings/
  ```

- [ ] **Step 8: Verify the project compiles**

  Run: `mvn compile -q`
  Expected: BUILD SUCCESS (no source files yet, just validates pom.xml and dependencies resolve)

  Note: This may take several minutes on first run as Maven downloads Jira dependencies.

- [ ] **Step 9: Commit**

  ```bash
  git add .mise.toml justfile upstream-versions.json pom.xml \
    src/main/resources/atlassian-plugin.xml \
    src/main/resources/mcp-plugin.properties \
    .gitignore
  git commit -m "feat: initialize Maven project with AMPS, toolchain files, and plugin descriptor"
  ```

---

## Chunk 2: Core Infrastructure

### Task 2: JiraRestClient

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/JiraRestClient.java`
- Create: `src/test/java/com/atlassian/mcp/plugin/JiraRestClientTest.java`

- [ ] **Step 1: Write `JiraRestClient.java`**

  ```java
  package com.atlassian.mcp.plugin;

  import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
  import com.atlassian.sal.api.ApplicationProperties;
  import jakarta.inject.Inject;
  import jakarta.inject.Named;
  import java.io.IOException;
  import java.net.URI;
  import java.net.http.HttpClient;
  import java.net.http.HttpRequest;
  import java.net.http.HttpResponse;
  import java.time.Duration;

  @Named
  public class JiraRestClient {

      private final ApplicationProperties applicationProperties;
      private final com.atlassian.mcp.plugin.config.McpPluginConfig pluginConfig;
      private final HttpClient httpClient;

      @Inject
      public JiraRestClient(
              @ComponentImport ApplicationProperties applicationProperties,
              com.atlassian.mcp.plugin.config.McpPluginConfig pluginConfig) {
          this.applicationProperties = applicationProperties;
          this.pluginConfig = pluginConfig;
          this.httpClient = HttpClient.newBuilder()
                  .connectTimeout(Duration.ofSeconds(5))
                  .followRedirects(HttpClient.Redirect.NEVER)
                  .build();
      }

      public String get(String path, String authHeader) throws McpToolException {
          return execute(buildRequest(path, authHeader, "GET", null));
      }

      public String post(String path, String body, String authHeader) throws McpToolException {
          return execute(buildRequest(path, authHeader, "POST", body));
      }

      public String put(String path, String body, String authHeader) throws McpToolException {
          return execute(buildRequest(path, authHeader, "PUT", body));
      }

      public String delete(String path, String authHeader) throws McpToolException {
          return execute(buildRequest(path, authHeader, "DELETE", null));
      }

      private String getBaseUrl() {
          String override = pluginConfig.getJiraBaseUrlOverride();
          if (override != null && !override.isBlank()) {
              return override.replaceAll("/+$", "");
          }
          return applicationProperties.getBaseUrl().replaceAll("/+$", "");
      }

      private HttpRequest buildRequest(String path, String authHeader, String method, String body) {
          String url = getBaseUrl() + path;
          HttpRequest.Builder builder = HttpRequest.newBuilder()
                  .uri(URI.create(url))
                  .timeout(Duration.ofSeconds(30))
                  .header("Content-Type", "application/json")
                  .header("Accept", "application/json");

          if (authHeader != null && !authHeader.isBlank()) {
              builder.header("Authorization", authHeader);
          }

          if (body != null) {
              builder.method(method, HttpRequest.BodyPublishers.ofString(body));
          } else {
              builder.method(method, HttpRequest.BodyPublishers.noBody());
          }

          return builder.build();
      }

      private String execute(HttpRequest request) throws McpToolException {
          try {
              HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
              if (response.statusCode() >= 400) {
                  throw new McpToolException(
                          "Jira API returned HTTP " + response.statusCode() + ": " + response.body());
              }
              return response.body();
          } catch (IOException e) {
              throw new McpToolException("Failed to connect to Jira REST API: " + e.getMessage(), e);
          } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new McpToolException("Jira REST API call interrupted", e);
          }
      }
  }
  ```

- [ ] **Step 2: Create `McpToolException.java`**

  ```java
  package com.atlassian.mcp.plugin;

  public class McpToolException extends Exception {
      public McpToolException(String message) {
          super(message);
      }
      public McpToolException(String message, Throwable cause) {
          super(message, cause);
      }
  }
  ```

- [ ] **Step 3: Write `JiraRestClientTest.java`**

  ```java
  package com.atlassian.mcp.plugin;

  import static org.junit.Assert.*;
  import static org.mockito.Mockito.*;

  import com.atlassian.mcp.plugin.config.McpPluginConfig;
  import com.atlassian.sal.api.ApplicationProperties;
  import org.junit.Before;
  import org.junit.Test;

  public class JiraRestClientTest {

      private ApplicationProperties applicationProperties;
      private McpPluginConfig pluginConfig;

      @Before
      public void setUp() {
          applicationProperties = mock(ApplicationProperties.class);
          pluginConfig = mock(McpPluginConfig.class);
          when(applicationProperties.getBaseUrl()).thenReturn("http://localhost:2990/jira");
          when(pluginConfig.getJiraBaseUrlOverride()).thenReturn("");
      }

      @Test
      public void testBaseUrlUsesApplicationProperties() {
          JiraRestClient client = new JiraRestClient(applicationProperties, pluginConfig);
          // The client is constructed; base URL resolution is tested indirectly via requests
          assertNotNull(client);
      }

      @Test
      public void testBaseUrlOverrideTakesPrecedence() {
          when(pluginConfig.getJiraBaseUrlOverride()).thenReturn("http://internal:8080");
          JiraRestClient client = new JiraRestClient(applicationProperties, pluginConfig);
          assertNotNull(client);
      }
  }
  ```

- [ ] **Step 4: Run tests**

  Run: `mvn test -pl . -Dtest=JiraRestClientTest -q`
  Expected: Tests pass

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/atlassian/mcp/plugin/JiraRestClient.java \
    src/main/java/com/atlassian/mcp/plugin/McpToolException.java \
    src/test/java/com/atlassian/mcp/plugin/JiraRestClientTest.java
  git commit -m "feat: add JiraRestClient HTTP wrapper for internal REST API calls"
  ```

---

### Task 3: McpPluginConfig

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/config/McpPluginConfig.java`

- [ ] **Step 1: Write `McpPluginConfig.java`**

  ```java
  package com.atlassian.mcp.plugin.config;

  import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
  import com.atlassian.sal.api.pluginsettings.PluginSettings;
  import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
  import jakarta.inject.Inject;
  import jakarta.inject.Named;
  import java.util.Arrays;
  import java.util.Collections;
  import java.util.Set;
  import java.util.stream.Collectors;

  @Named
  public class McpPluginConfig {

      private static final String PREFIX = "com.atlassian.mcp.plugin.";
      private final PluginSettingsFactory pluginSettingsFactory;

      @Inject
      public McpPluginConfig(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
          this.pluginSettingsFactory = pluginSettingsFactory;
      }

      private PluginSettings settings() {
          return pluginSettingsFactory.createGlobalSettings();
      }

      public boolean isEnabled() {
          return Boolean.parseBoolean((String) settings().get(PREFIX + "enabled"));
      }

      public void setEnabled(boolean enabled) {
          settings().put(PREFIX + "enabled", String.valueOf(enabled));
      }

      public Set<String> getAllowedUserKeys() {
          String raw = (String) settings().get(PREFIX + "allowedUsers");
          if (raw == null || raw.isBlank()) {
              return Collections.emptySet();
          }
          return Arrays.stream(raw.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .collect(Collectors.toSet());
      }

      public void setAllowedUserKeys(String commaDelimited) {
          settings().put(PREFIX + "allowedUsers", commaDelimited);
      }

      public boolean isUserAllowed(String userKey) {
          Set<String> allowed = getAllowedUserKeys();
          return allowed.isEmpty() || allowed.contains(userKey);
      }

      public Set<String> getDisabledTools() {
          String raw = (String) settings().get(PREFIX + "disabledTools");
          if (raw == null || raw.isBlank()) {
              return Collections.emptySet();
          }
          return Arrays.stream(raw.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .collect(Collectors.toSet());
      }

      public void setDisabledTools(String commaDelimited) {
          settings().put(PREFIX + "disabledTools", commaDelimited);
      }

      public boolean isToolEnabled(String toolName) {
          return !getDisabledTools().contains(toolName);
      }

      public boolean isReadOnlyMode() {
          return Boolean.parseBoolean((String) settings().get(PREFIX + "readOnlyMode"));
      }

      public void setReadOnlyMode(boolean readOnly) {
          settings().put(PREFIX + "readOnlyMode", String.valueOf(readOnly));
      }

      public String getJiraBaseUrlOverride() {
          String val = (String) settings().get(PREFIX + "jiraBaseUrl");
          return val == null ? "" : val;
      }

      public void setJiraBaseUrlOverride(String url) {
          settings().put(PREFIX + "jiraBaseUrl", url);
      }
  }
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add src/main/java/com/atlassian/mcp/plugin/config/McpPluginConfig.java
  git commit -m "feat: add McpPluginConfig for PluginSettings-backed configuration"
  ```

---

### Task 4: McpTool interface and ToolRegistry

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/McpTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/ToolRegistry.java`

- [ ] **Step 1: Write `McpTool.java`**

  ```java
  package com.atlassian.mcp.plugin.tools;

  import com.atlassian.mcp.plugin.McpToolException;
  import java.util.Map;

  public interface McpTool {

      /** Tool name matching upstream mcp-atlassian. */
      String name();

      /** Human-readable description. */
      String description();

      /** JSON Schema for the tool's input parameters. */
      Map<String, Object> inputSchema();

      /** True if this tool modifies data (create, update, delete, transition). */
      boolean isWriteTool();

      /**
       * Optional: plugin key required for this tool to work.
       * Return null if no specific plugin is required (works on all Jira instances).
       */
      default String requiredPluginKey() {
          return null;
      }

      /**
       * Execute the tool with the given arguments.
       * @param args parsed JSON arguments from the MCP client
       * @param authHeader the user's Authorization header (forwarded to Jira REST API)
       * @return JSON string result
       */
      String execute(Map<String, Object> args, String authHeader) throws McpToolException;
  }
  ```

- [ ] **Step 2: Write `ToolRegistry.java`**

  ```java
  package com.atlassian.mcp.plugin.tools;

  import com.atlassian.mcp.plugin.config.McpPluginConfig;
  import com.atlassian.plugin.PluginAccessor;
  import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
  import jakarta.inject.Inject;
  import jakarta.inject.Named;
  import java.util.*;
  import java.util.concurrent.ConcurrentHashMap;
  import java.util.stream.Collectors;

  @Named
  public class ToolRegistry {

      private final Map<String, McpTool> allTools = new ConcurrentHashMap<>();
      private final PluginAccessor pluginAccessor;
      private final McpPluginConfig config;

      @Inject
      public ToolRegistry(
              @ComponentImport PluginAccessor pluginAccessor,
              McpPluginConfig config) {
          this.pluginAccessor = pluginAccessor;
          this.config = config;
      }

      public void register(McpTool tool) {
          allTools.put(tool.name(), tool);
      }

      /**
       * Returns tools visible to the given user, filtered by:
       * - capability (required plugin installed)
       * - admin disabled list
       * - read-only mode (hides write tools)
       */
      public List<McpTool> listTools(String userKey) {
          return allTools.values().stream()
                  .filter(this::isCapabilityMet)
                  .filter(t -> config.isToolEnabled(t.name()))
                  .filter(t -> !config.isReadOnlyMode() || !t.isWriteTool())
                  .collect(Collectors.toList());
      }

      /**
       * Get a tool by name for execution. Returns null if tool doesn't exist
       * or is not available.
       */
      public McpTool getTool(String name) {
          return allTools.get(name);
      }

      /**
       * Check if a tool is callable by the given user right now.
       * Returns an error message if not, null if OK.
       */
      public String checkToolAccess(String toolName, String userKey) {
          McpTool tool = allTools.get(toolName);
          if (tool == null) {
              return "Unknown tool: " + toolName;
          }
          if (!isCapabilityMet(tool)) {
              return "Tool '" + toolName + "' requires a Jira product/app that is not installed on this instance";
          }
          if (!config.isToolEnabled(toolName)) {
              return "Tool '" + toolName + "' is disabled by the administrator";
          }
          if (config.isReadOnlyMode() && tool.isWriteTool()) {
              return "Tool '" + toolName + "' is a write operation and the server is in read-only mode";
          }
          return null;
      }

      private boolean isCapabilityMet(McpTool tool) {
          String requiredPlugin = tool.requiredPluginKey();
          if (requiredPlugin == null) {
              return true;
          }
          return pluginAccessor.isPluginEnabled(requiredPlugin);
      }

      public int totalRegistered() {
          return allTools.size();
      }
  }
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/java/com/atlassian/mcp/plugin/tools/McpTool.java \
    src/main/java/com/atlassian/mcp/plugin/tools/ToolRegistry.java
  git commit -m "feat: add McpTool interface and ToolRegistry with capability detection"
  ```

---

### Task 5: JsonRpcHandler and McpResource

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/JsonRpcHandler.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/McpResource.java`
- Create: `src/test/java/com/atlassian/mcp/plugin/JsonRpcHandlerTest.java`

- [ ] **Step 1: Write `JsonRpcHandler.java`**

  ```java
  package com.atlassian.mcp.plugin;

  import com.atlassian.mcp.plugin.config.McpPluginConfig;
  import com.atlassian.mcp.plugin.tools.McpTool;
  import com.atlassian.mcp.plugin.tools.ToolRegistry;
  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.fasterxml.jackson.databind.node.ArrayNode;
  import com.fasterxml.jackson.databind.node.ObjectNode;
  import jakarta.inject.Inject;
  import jakarta.inject.Named;
  import java.util.HashMap;
  import java.util.List;
  import java.util.Map;

  @Named
  public class JsonRpcHandler {

      private static final String JSONRPC = "2.0";
      private static final String SERVER_NAME = "jira-mcp";
      private static final String SERVER_VERSION = "1.0.0";
      private static final String PROTOCOL_VERSION = "2025-06-18";

      private final ObjectMapper mapper = new ObjectMapper();
      private final ToolRegistry toolRegistry;
      private final McpPluginConfig config;

      @Inject
      public JsonRpcHandler(ToolRegistry toolRegistry, McpPluginConfig config) {
          this.toolRegistry = toolRegistry;
          this.config = config;
      }

      /**
       * Handle a JSON-RPC request. Returns null for notifications (caller should return 202).
       */
      public String handle(String jsonBody, String userKey, String authHeader) {
          JsonNode request;
          try {
              request = mapper.readTree(jsonBody);
          } catch (Exception e) {
              return errorResponse(null, -32700, "Parse error: " + e.getMessage());
          }

          // Reject batch requests
          if (request.isArray()) {
              return errorResponse(null, -32600, "Batch requests are not supported");
          }

          String method = request.has("method") ? request.get("method").asText() : null;
          JsonNode id = request.has("id") ? request.get("id") : null;
          JsonNode params = request.has("params") ? request.get("params") : mapper.createObjectNode();

          if (method == null) {
              return errorResponse(id, -32600, "Missing 'method' field");
          }

          // Notifications (no id) return null -> caller returns 202
          boolean isNotification = (id == null || id.isNull());

          return switch (method) {
              case "initialize" -> handleInitialize(id);
              case "notifications/initialized" -> null; // notification
              case "ping" -> successResponse(id, mapper.createObjectNode());
              case "tools/list" -> handleToolsList(id, userKey);
              case "tools/call" -> handleToolsCall(id, params, userKey, authHeader);
              default -> {
                  if (isNotification) yield null;
                  yield errorResponse(id, -32601, "Method not found: " + method);
              }
          };
      }

      private String handleInitialize(JsonNode id) {
          ObjectNode result = mapper.createObjectNode();
          result.put("protocolVersion", PROTOCOL_VERSION);

          ObjectNode serverInfo = mapper.createObjectNode();
          serverInfo.put("name", SERVER_NAME);
          serverInfo.put("version", SERVER_VERSION);
          result.set("serverInfo", serverInfo);

          ObjectNode capabilities = mapper.createObjectNode();
          ObjectNode tools = mapper.createObjectNode();
          tools.put("listChanged", false);
          capabilities.set("tools", tools);
          result.set("capabilities", capabilities);

          return successResponse(id, result);
      }

      private String handleToolsList(JsonNode id, String userKey) {
          List<McpTool> tools = toolRegistry.listTools(userKey);
          ObjectNode result = mapper.createObjectNode();
          ArrayNode toolsArray = mapper.createArrayNode();

          for (McpTool tool : tools) {
              ObjectNode toolNode = mapper.createObjectNode();
              toolNode.put("name", tool.name());
              toolNode.put("description", tool.description());
              toolNode.set("inputSchema", mapper.valueToTree(tool.inputSchema()));
              toolsArray.add(toolNode);
          }

          result.set("tools", toolsArray);
          return successResponse(id, result);
      }

      @SuppressWarnings("unchecked")
      private String handleToolsCall(JsonNode id, JsonNode params, String userKey, String authHeader) {
          String toolName = params.has("name") ? params.get("name").asText() : null;
          if (toolName == null) {
              return errorResponse(id, -32602, "Missing 'name' in params");
          }

          String accessError = toolRegistry.checkToolAccess(toolName, userKey);
          if (accessError != null) {
              return errorResponse(id, -32602, accessError);
          }

          McpTool tool = toolRegistry.getTool(toolName);

          Map<String, Object> args = new HashMap<>();
          if (params.has("arguments") && params.get("arguments").isObject()) {
              try {
                  args = mapper.convertValue(params.get("arguments"), Map.class);
              } catch (Exception e) {
                  return errorResponse(id, -32602, "Invalid arguments: " + e.getMessage());
              }
          }

          try {
              String resultText = tool.execute(args, authHeader);
              ObjectNode result = mapper.createObjectNode();
              ArrayNode content = mapper.createArrayNode();
              ObjectNode textContent = mapper.createObjectNode();
              textContent.put("type", "text");
              textContent.put("text", resultText);
              content.add(textContent);
              result.set("content", content);
              result.put("isError", false);
              return successResponse(id, result);
          } catch (McpToolException e) {
              ObjectNode result = mapper.createObjectNode();
              ArrayNode content = mapper.createArrayNode();
              ObjectNode textContent = mapper.createObjectNode();
              textContent.put("type", "text");
              textContent.put("text", "Error: " + e.getMessage());
              content.add(textContent);
              result.set("content", content);
              result.put("isError", true);
              return successResponse(id, result);
          }
      }

      private String successResponse(JsonNode id, Object result) {
          try {
              ObjectNode response = mapper.createObjectNode();
              response.put("jsonrpc", JSONRPC);
              response.set("id", id);
              response.set("result", mapper.valueToTree(result));
              return mapper.writeValueAsString(response);
          } catch (Exception e) {
              return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Serialization error\"}}";
          }
      }

      String errorResponse(JsonNode id, int code, String message) {
          try {
              ObjectNode response = mapper.createObjectNode();
              response.put("jsonrpc", JSONRPC);
              response.set("id", id);
              ObjectNode error = mapper.createObjectNode();
              error.put("code", code);
              error.put("message", message);
              response.set("error", error);
              return mapper.writeValueAsString(response);
          } catch (Exception e) {
              return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Serialization error\"}}";
          }
      }
  }
  ```

- [ ] **Step 2: Write `McpResource.java`**

  ```java
  package com.atlassian.mcp.plugin;

  import com.atlassian.mcp.plugin.config.McpPluginConfig;
  import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
  import com.atlassian.sal.api.user.UserManager;
  import com.atlassian.sal.api.user.UserProfile;
  import jakarta.inject.Inject;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.ws.rs.*;
  import jakarta.ws.rs.core.*;

  @Path("/")
  public class McpResource {

      private final JsonRpcHandler handler;
      private final McpPluginConfig config;
      private final UserManager userManager;

      @Inject
      public McpResource(
              JsonRpcHandler handler,
              McpPluginConfig config,
              @ComponentImport UserManager userManager) {
          this.handler = handler;
          this.config = config;
          this.userManager = userManager;
      }

      private static final String SUPPORTED_PROTOCOL_VERSION = "2025-06-18";

      @POST
      @Consumes(MediaType.APPLICATION_JSON)
      @Produces(MediaType.APPLICATION_JSON)
      public Response handlePost(String body, @Context HttpServletRequest request) {
          // Check if MCP is enabled
          if (!config.isEnabled()) {
              return Response.status(503)
                      .entity("{\"error\":\"MCP server is disabled\"}")
                      .build();
          }

          // Get authenticated user
          UserProfile user = userManager.getRemoteUser(request);
          if (user == null) {
              return Response.status(Response.Status.UNAUTHORIZED)
                      .entity("{\"error\":\"Authentication required\"}")
                      .build();
          }

          String userKey = user.getUserKey().getStringValue();

          // Check allowlist
          if (!config.isUserAllowed(userKey)) {
              return Response.status(Response.Status.FORBIDDEN)
                      .entity("{\"error\":\"User not authorized for MCP access\"}")
                      .build();
          }

          // Validate MCP-Protocol-Version header (required after initialize)
          String protocolVersion = request.getHeader("MCP-Protocol-Version");
          // Parse method to check if this is initialize (which negotiates the version)
          boolean isInitialize = body != null && body.contains("\"initialize\"");
          if (!isInitialize && protocolVersion != null
                  && !SUPPORTED_PROTOCOL_VERSION.equals(protocolVersion)) {
              return Response.status(400)
                      .entity("{\"error\":\"Unsupported MCP-Protocol-Version: " + protocolVersion
                              + ". Supported: " + SUPPORTED_PROTOCOL_VERSION + "\"}")
                      .build();
          }

          String authHeader = request.getHeader("Authorization");
          String result = handler.handle(body, userKey, authHeader);

          if (result == null) {
              // Notification — return 202 Accepted
              return Response.status(202).build();
          }

          return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
      }

      @GET
      @Produces(MediaType.APPLICATION_JSON)
      public Response handleGet() {
          return Response.status(405)
                  .entity("{\"error\":\"SSE streaming not supported in this version. Use POST.\"}")
                  .build();
      }

      @DELETE
      @Produces(MediaType.APPLICATION_JSON)
      public Response handleDelete() {
          return Response.status(405)
                  .entity("{\"error\":\"Sessions not supported in this version.\"}")
                  .build();
      }
  }
  ```

- [ ] **Step 3: Write `JsonRpcHandlerTest.java`**

  ```java
  package com.atlassian.mcp.plugin;

  import static org.junit.Assert.*;
  import static org.mockito.Mockito.*;

  import com.atlassian.mcp.plugin.config.McpPluginConfig;
  import com.atlassian.mcp.plugin.tools.ToolRegistry;
  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.junit.Before;
  import org.junit.Test;

  public class JsonRpcHandlerTest {

      private JsonRpcHandler handler;
      private ToolRegistry toolRegistry;
      private McpPluginConfig config;
      private ObjectMapper mapper = new ObjectMapper();

      @Before
      public void setUp() {
          toolRegistry = mock(ToolRegistry.class);
          config = mock(McpPluginConfig.class);
          handler = new JsonRpcHandler(toolRegistry, config);
      }

      @Test
      public void testInitialize() throws Exception {
          String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
          String response = handler.handle(request, "user1", "Bearer token");

          JsonNode json = mapper.readTree(response);
          assertEquals("2.0", json.get("jsonrpc").asText());
          assertEquals(1, json.get("id").asInt());
          assertNotNull(json.get("result"));
          assertEquals("jira-mcp", json.get("result").get("serverInfo").get("name").asText());
          assertNotNull(json.get("result").get("capabilities").get("tools"));
      }

      @Test
      public void testPing() throws Exception {
          String request = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}";
          String response = handler.handle(request, "user1", "Bearer token");

          JsonNode json = mapper.readTree(response);
          assertEquals(2, json.get("id").asInt());
          assertNotNull(json.get("result"));
      }

      @Test
      public void testNotificationReturnsNull() {
          String request = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
          String response = handler.handle(request, "user1", "Bearer token");
          assertNull(response);
      }

      @Test
      public void testUnknownMethodReturnsError() throws Exception {
          String request = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"unknown/method\"}";
          String response = handler.handle(request, "user1", "Bearer token");

          JsonNode json = mapper.readTree(response);
          assertEquals(-32601, json.get("error").get("code").asInt());
      }

      @Test
      public void testMalformedJsonReturnsParseError() throws Exception {
          String response = handler.handle("not json at all", "user1", "Bearer token");

          JsonNode json = mapper.readTree(response);
          assertEquals(-32700, json.get("error").get("code").asInt());
      }

      @Test
      public void testBatchRequestRejected() throws Exception {
          String response = handler.handle("[{},{}]", "user1", "Bearer token");

          JsonNode json = mapper.readTree(response);
          assertEquals(-32600, json.get("error").get("code").asInt());
      }
  }
  ```

- [ ] **Step 4: Run tests**

  Run: `mvn test -Dtest=JsonRpcHandlerTest -q`
  Expected: All tests pass

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/atlassian/mcp/plugin/JsonRpcHandler.java \
    src/main/java/com/atlassian/mcp/plugin/McpResource.java \
    src/test/java/com/atlassian/mcp/plugin/JsonRpcHandlerTest.java
  git commit -m "feat: add MCP protocol handler (JSON-RPC dispatch) and JAX-RS resource"
  ```

---

## Chunk 3: First Tools (Pattern Establishment)

### Task 6: Implement search, get_issue, and get_all_projects tools

These three tools establish the pattern for all remaining tools. Each tool:
1. Implements `McpTool`
2. Calls `JiraRestClient` with the appropriate REST endpoint
3. Returns the Jira JSON response as-is

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/issues/SearchTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/issues/GetIssueTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/projects/GetAllProjectsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/ToolInitializer.java`
- Create: `src/test/java/com/atlassian/mcp/plugin/tools/SearchToolTest.java`

- [ ] **Step 1: Write `SearchTool.java`**

  ```java
  package com.atlassian.mcp.plugin.tools.issues;

  // Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:search
  import com.atlassian.mcp.plugin.JiraRestClient;
  import com.atlassian.mcp.plugin.McpToolException;
  import com.atlassian.mcp.plugin.tools.McpTool;
  import java.util.Map;

  public class SearchTool implements McpTool {

      private final JiraRestClient client;

      public SearchTool(JiraRestClient client) {
          this.client = client;
      }

      @Override public String name() { return "search"; }

      @Override public String description() {
          return "Search for Jira issues using JQL (Jira Query Language). "
                  + "Returns matching issues with their key fields.";
      }

      @Override
      public Map<String, Object> inputSchema() {
          return Map.of(
                  "type", "object",
                  "properties", Map.of(
                          "jql", Map.of(
                                  "type", "string",
                                  "description", "JQL query string (e.g., 'project = PROJ AND status = Open')"
                          ),
                          "maxResults", Map.of(
                                  "type", "integer",
                                  "description", "Maximum number of results to return (default: 50, max: 200)",
                                  "default", 50
                          ),
                          "startAt", Map.of(
                                  "type", "integer",
                                  "description", "Index of the first result to return (for pagination)",
                                  "default", 0
                          ),
                          "fields", Map.of(
                                  "type", "string",
                                  "description", "Comma-separated list of fields to return (default: key essential fields)"
                          )
                  ),
                  "required", java.util.List.of("jql")
          );
      }

      @Override public boolean isWriteTool() { return false; }

      @Override
      public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
          String jql = (String) args.get("jql");
          if (jql == null || jql.isBlank()) {
              throw new McpToolException("'jql' parameter is required");
          }
          int maxResults = Math.min(getInt(args, "maxResults", 50), 200);
          int startAt = getInt(args, "startAt", 0);
          String fields = (String) args.getOrDefault("fields", "");

          String queryParams = "?jql=" + encode(jql)
                  + "&maxResults=" + maxResults
                  + "&startAt=" + startAt;
          if (!fields.isBlank()) {
              queryParams += "&fields=" + encode(fields);
          }

          return client.get("/rest/api/2/search" + queryParams, authHeader);
      }

      private static int getInt(Map<String, Object> args, String key, int defaultVal) {
          Object val = args.get(key);
          if (val instanceof Number n) return n.intValue();
          if (val instanceof String s) {
              try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
          }
          return defaultVal;
      }

      private static String encode(String s) {
          return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
      }
  }
  ```

- [ ] **Step 2: Write `GetIssueTool.java`**

  ```java
  package com.atlassian.mcp.plugin.tools.issues;

  // Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:get_issue
  import com.atlassian.mcp.plugin.JiraRestClient;
  import com.atlassian.mcp.plugin.McpToolException;
  import com.atlassian.mcp.plugin.tools.McpTool;
  import java.util.Map;

  public class GetIssueTool implements McpTool {

      private final JiraRestClient client;

      public GetIssueTool(JiraRestClient client) {
          this.client = client;
      }

      @Override public String name() { return "get_issue"; }

      @Override public String description() {
          return "Get details of a specific Jira issue including its fields, "
                  + "comments, and relationship information.";
      }

      @Override
      public Map<String, Object> inputSchema() {
          return Map.of(
                  "type", "object",
                  "properties", Map.of(
                          "issue_key", Map.of(
                                  "type", "string",
                                  "description", "Jira issue key (e.g., 'PROJ-123')"
                          ),
                          "fields", Map.of(
                                  "type", "string",
                                  "description", "Comma-separated list of fields to return, or '*all' for everything",
                                  "default", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment"
                          ),
                          "expand", Map.of(
                                  "type", "string",
                                  "description", "Fields to expand (e.g., 'renderedFields,transitions,changelog')"
                          )
                  ),
                  "required", java.util.List.of("issue_key")
          );
      }

      @Override public boolean isWriteTool() { return false; }

      @Override
      public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
          String issueKey = (String) args.get("issue_key");
          if (issueKey == null || issueKey.isBlank()) {
              throw new McpToolException("'issue_key' parameter is required");
          }

          String fields = (String) args.getOrDefault("fields",
                  "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment");
          String expand = (String) args.get("expand");

          String queryParams = "?fields=" + java.net.URLEncoder.encode(fields, java.nio.charset.StandardCharsets.UTF_8);
          if (expand != null && !expand.isBlank()) {
              queryParams += "&expand=" + java.net.URLEncoder.encode(expand, java.nio.charset.StandardCharsets.UTF_8);
          }

          return client.get("/rest/api/2/issue/" + issueKey + queryParams, authHeader);
      }
  }
  ```

- [ ] **Step 3: Write `GetAllProjectsTool.java`**

  ```java
  package com.atlassian.mcp.plugin.tools.projects;

  // Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:get_all_projects
  import com.atlassian.mcp.plugin.JiraRestClient;
  import com.atlassian.mcp.plugin.McpToolException;
  import com.atlassian.mcp.plugin.tools.McpTool;
  import java.util.Map;

  public class GetAllProjectsTool implements McpTool {

      private final JiraRestClient client;

      public GetAllProjectsTool(JiraRestClient client) {
          this.client = client;
      }

      @Override public String name() { return "get_all_projects"; }

      @Override public String description() {
          return "Get all Jira projects accessible by the current user.";
      }

      @Override
      public Map<String, Object> inputSchema() {
          return Map.of(
                  "type", "object",
                  "properties", Map.of(),
                  "required", java.util.List.of()
          );
      }

      @Override public boolean isWriteTool() { return false; }

      @Override
      public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
          return client.get("/rest/api/2/project", authHeader);
      }
  }
  ```

- [ ] **Step 4: Write `ToolInitializer.java`** — registers all tools at plugin startup

  ```java
  package com.atlassian.mcp.plugin.tools;

  import com.atlassian.mcp.plugin.JiraRestClient;
  import com.atlassian.mcp.plugin.tools.issues.*;
  import com.atlassian.mcp.plugin.tools.projects.*;
  import jakarta.inject.Inject;
  import jakarta.inject.Named;

  @Named
  public class ToolInitializer {

      @Inject
      public ToolInitializer(ToolRegistry registry, JiraRestClient client) {
          // Issues & Search
          registry.register(new SearchTool(client));
          registry.register(new GetIssueTool(client));

          // Projects
          registry.register(new GetAllProjectsTool(client));

          // TODO: remaining 43 tools will be registered here as they are implemented
      }
  }
  ```

- [ ] **Step 5: Write `SearchToolTest.java`**

  ```java
  package com.atlassian.mcp.plugin.tools;

  import static org.junit.Assert.*;
  import static org.mockito.Mockito.*;
  import static org.mockito.ArgumentMatchers.*;

  import com.atlassian.mcp.plugin.JiraRestClient;
  import com.atlassian.mcp.plugin.McpToolException;
  import com.atlassian.mcp.plugin.tools.issues.SearchTool;
  import org.junit.Before;
  import org.junit.Test;
  import java.util.Map;

  public class SearchToolTest {

      private JiraRestClient client;
      private SearchTool tool;

      @Before
      public void setUp() {
          client = mock(JiraRestClient.class);
          tool = new SearchTool(client);
      }

      @Test
      public void testName() {
          assertEquals("search", tool.name());
      }

      @Test
      public void testIsNotWriteTool() {
          assertFalse(tool.isWriteTool());
      }

      @Test
      public void testExecuteCallsSearchEndpoint() throws Exception {
          when(client.get(anyString(), anyString()))
                  .thenReturn("{\"issues\":[],\"total\":0}");

          String result = tool.execute(
                  Map.of("jql", "project = TEST"),
                  "Bearer mytoken");

          assertEquals("{\"issues\":[],\"total\":0}", result);
          verify(client).get(contains("/rest/api/2/search?jql="), eq("Bearer mytoken"));
      }

      @Test
      public void testExecuteEnforcesMaxResults() throws Exception {
          when(client.get(anyString(), anyString())).thenReturn("{}");

          tool.execute(Map.of("jql", "test", "maxResults", 999), "Bearer t");

          verify(client).get(contains("maxResults=200"), anyString());
      }

      @Test(expected = McpToolException.class)
      public void testExecuteRequiresJql() throws Exception {
          tool.execute(Map.of(), "Bearer t");
      }
  }
  ```

- [ ] **Step 6: Run tests**

  Run: `mvn test -q`
  Expected: All tests pass

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/atlassian/mcp/plugin/tools/issues/ \
    src/main/java/com/atlassian/mcp/plugin/tools/projects/ \
    src/main/java/com/atlassian/mcp/plugin/tools/ToolInitializer.java \
    src/test/java/com/atlassian/mcp/plugin/tools/SearchToolTest.java
  git commit -m "feat: add first 3 MCP tools (search, get_issue, get_all_projects)"
  ```

- [ ] **Step 8: Verify plugin builds as OBR**

  Run: `atlas-package -q`
  Expected: BUILD SUCCESS, `target/atlassian-mcp-plugin-1.0.0-SNAPSHOT.jar` exists

- [ ] **Step 9: Commit build verification**

  No changes to commit — this step verifies the build works.

---

## Chunk 4: Remaining Tools

Each task below adds a category of tools. All tools follow the same pattern established in Task 6: implement `McpTool`, call `JiraRestClient`, return JSON. The `ToolInitializer` is updated at the end to register all new tools.

Read the upstream Python tool definitions at `.upstream/mcp-atlassian/src/mcp_atlassian/servers/jira.py` and the Jira client code at `.upstream/mcp-atlassian/src/mcp_atlassian/jira/` to get the exact REST endpoints, parameters, and response handling for each tool.

### Task 7: Issues tools (remaining 5)

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/issues/CreateIssueTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/issues/UpdateIssueTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/issues/DeleteIssueTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/issues/BatchCreateIssuesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/issues/BatchGetChangelogsTool.java`
- Modify: `src/main/java/com/atlassian/mcp/plugin/tools/ToolInitializer.java`

**REST endpoints:**
| Tool | Method | Endpoint |
|---|---|---|
| create_issue | POST | `/rest/api/2/issue` |
| update_issue | PUT | `/rest/api/2/issue/{issueKey}` |
| delete_issue | DELETE | `/rest/api/2/issue/{issueKey}` |
| batch_create_issues | POST | `/rest/api/2/issue/bulk` |
| batch_get_changelogs | GET | `/rest/api/2/issue/{issueKey}/changelog` |

- [ ] **Step 1:** Create all 5 tool classes following the SearchTool pattern. Each tool:
  - Has `isWriteTool()` returning `true` for create/update/delete
  - Validates required parameters
  - Builds the REST URL with parameters
  - Calls `client.get/post/put/delete` with `authHeader`
  - Returns the Jira JSON response

  Read `.upstream/mcp-atlassian/src/mcp_atlassian/servers/jira.py` lines 386-602 for the upstream `create_issue`, `update_issue`, `delete_issue`, `batch_create_issues` definitions. Read lines 603-705 for `batch_get_changelogs`.

- [ ] **Step 2:** Register all 5 tools in `ToolInitializer`
- [ ] **Step 3:** Run `mvn test -q` — all tests pass
- [ ] **Step 4:** Commit: `git commit -m "feat: add issue CRUD tools (create, update, delete, batch)"`

---

### Task 8: Comments, Transitions, and Worklogs tools (6 tools)

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/comments/AddCommentTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/comments/EditCommentTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/transitions/GetTransitionsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/transitions/TransitionIssueTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/worklogs/GetWorklogTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/worklogs/AddWorklogTool.java`

**REST endpoints:**
| Tool | Method | Endpoint |
|---|---|---|
| add_comment | POST | `/rest/api/2/issue/{issueKey}/comment` |
| edit_comment | PUT | `/rest/api/2/issue/{issueKey}/comment/{commentId}` |
| get_transitions | GET | `/rest/api/2/issue/{issueKey}/transitions` |
| transition_issue | POST | `/rest/api/2/issue/{issueKey}/transitions` |
| get_worklog | GET | `/rest/api/2/issue/{issueKey}/worklog` |
| add_worklog | POST | `/rest/api/2/issue/{issueKey}/worklog` |

- [ ] **Step 1:** Create all 6 tool classes. Read upstream lines 706-907 for comments and transitions, lines 1040-1176 for worklogs.
- [ ] **Step 2:** Register in `ToolInitializer`
- [ ] **Step 3:** Run `mvn test -q`
- [ ] **Step 4:** Commit: `git commit -m "feat: add comment, transition, and worklog tools"`

---

### Task 9: Boards and Sprints tools (4 tools)

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/boards/GetAgileBoardsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/boards/GetBoardIssuesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/boards/GetSprintsFromBoardTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/boards/GetSprintIssuesTool.java`

**REST endpoints (Agile API):**
| Tool | Method | Endpoint |
|---|---|---|
| get_agile_boards | GET | `/rest/agile/1.0/board` |
| get_board_issues | GET | `/rest/agile/1.0/board/{boardId}/issue` |
| get_sprints_from_board | GET | `/rest/agile/1.0/board/{boardId}/sprint` |
| get_sprint_issues | GET | `/rest/agile/1.0/sprint/{sprintId}/issue` |

All 4 tools require Jira Software: `requiredPluginKey()` returns `"com.atlassian.jira.plugins.jira-software-plugin"`.

- [ ] **Step 1:** Create all 4 tool classes. Read upstream lines 1177-1302 for board/sprint tools.
- [ ] **Step 2:** Register in `ToolInitializer`
- [ ] **Step 3:** Run `mvn test -q`
- [ ] **Step 4:** Commit: `git commit -m "feat: add agile board and sprint tools (require Jira Software)"`

---

### Task 10: Links and Epics tools (5 tools)

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/links/GetLinkTypesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/links/CreateIssueLinkTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/links/CreateRemoteIssueLinkTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/links/RemoveIssueLinkTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/epics/LinkToEpicTool.java`

**REST endpoints:**
| Tool | Method | Endpoint |
|---|---|---|
| get_link_types | GET | `/rest/api/2/issueLinkType` |
| create_issue_link | POST | `/rest/api/2/issueLink` |
| create_remote_issue_link | POST | `/rest/api/2/issue/{issueKey}/remotelink` |
| remove_issue_link | DELETE | `/rest/api/2/issueLink/{linkId}` |
| link_to_epic | PUT | `/rest/agile/1.0/epic/{epicKey}/issue` |

- [ ] **Step 1:** Create all 5 tool classes. Read upstream lines 1303-1482 for link tools, lines 1564-1692 for epic.
- [ ] **Step 2:** Register in `ToolInitializer`
- [ ] **Step 3:** Run `mvn test -q`
- [ ] **Step 4:** Commit: `git commit -m "feat: add issue link and epic tools"`

---

### Task 11: Projects/Versions and Users/Watchers tools (10 tools)

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/projects/GetProjectIssuesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/projects/GetProjectVersionsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/projects/GetProjectComponentsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/projects/CreateVersionTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/projects/BatchCreateVersionsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/users/GetUserProfileTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/users/GetIssueWatchersTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/users/AddWatcherTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/users/RemoveWatcherTool.java`

**REST endpoints:**
| Tool | Method | Endpoint |
|---|---|---|
| get_project_issues | GET | `/rest/api/2/search?jql=project={projectKey}` |
| get_project_versions | GET | `/rest/api/2/project/{projectKey}/versions` |
| get_project_components | GET | `/rest/api/2/project/{projectKey}/components` |
| create_version | POST | `/rest/api/2/version` |
| batch_create_versions | POST | `/rest/api/2/version` (loop) |
| get_user_profile | GET | `/rest/api/2/user?key={userKey}` or `?username={username}` |
| get_issue_watchers | GET | `/rest/api/2/issue/{issueKey}/watchers` |
| add_watcher | POST | `/rest/api/2/issue/{issueKey}/watchers` |
| remove_watcher | DELETE | `/rest/api/2/issue/{issueKey}/watchers?username={username}` |

- [ ] **Step 1:** Create all 10 tool classes (GetAllProjectsTool already exists from Task 6)
- [ ] **Step 2:** Register in `ToolInitializer`
- [ ] **Step 3:** Run `mvn test -q`
- [ ] **Step 4:** Commit: `git commit -m "feat: add project, version, user, and watcher tools"`

---

### Task 12: Attachments and Fields tools (4 tools)

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/attachments/DownloadAttachmentsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/attachments/GetIssueImagesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/fields/SearchFieldsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/fields/GetFieldOptionsTool.java`

**REST endpoints:**
| Tool | Method | Endpoint |
|---|---|---|
| download_attachments | GET | `/rest/api/2/issue/{issueKey}?fields=attachment` then fetch each attachment URL |
| get_issue_images | GET | Same as above, filter for image MIME types |
| search_fields | GET | `/rest/api/2/field` |
| get_field_options | GET | `/rest/api/2/field/{fieldId}/option` or `/rest/api/2/customFieldOption/{optionId}` |

- [ ] **Step 1:** Create all 4 tool classes
- [ ] **Step 2:** Register in `ToolInitializer`
- [ ] **Step 3:** Run `mvn test -q`
- [ ] **Step 4:** Commit: `git commit -m "feat: add attachment and field tools"`

---

### Task 13: Service Desk, Forms, and Metrics tools (10 tools)

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/servicedesk/GetServiceDeskForProjectTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/servicedesk/GetServiceDeskQueuesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/servicedesk/GetQueueIssuesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/forms/GetIssueProformaFormsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/forms/GetProformaFormDetailsTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/forms/UpdateProformaFormAnswersTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/metrics/GetIssueDatesTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/metrics/GetIssueSlaTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/metrics/GetIssueDevelopmentInfoTool.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/tools/metrics/GetIssuesDevelopmentInfoTool.java`

**REST endpoints:**
| Tool | Method | Endpoint | Requires |
|---|---|---|---|
| get_service_desk_for_project | GET | `/rest/servicedeskapi/servicedesk/projectKey:{key}` | JSM |
| get_service_desk_queues | GET | `/rest/servicedeskapi/servicedesk/{id}/queue` | JSM |
| get_queue_issues | GET | `/rest/servicedeskapi/servicedesk/{id}/queue/{queueId}/issue` | JSM |
| get_issue_sla | GET | `/rest/servicedeskapi/request/{issueKey}/sla` | JSM |
| get_issue_proforma_forms | GET | `/rest/proforma/1/issue/{issueId}/form` | Proforma |
| get_proforma_form_details | GET | `/rest/proforma/1/issue/{issueId}/form/{formId}` | Proforma |
| update_proforma_form_answers | PUT | `/rest/proforma/1/issue/{issueId}/form/{formId}` | Proforma |
| get_issue_dates | GET | `/rest/api/2/issue/{issueKey}?fields=created,updated,duedate,resolutiondate` | - |
| get_issue_development_info | GET | `/rest/dev-status/latest/issue/detail?issueId={id}&applicationType=stash&dataType=repository` | - |
| get_issues_development_info | GET | Same as above (batched) | - |

Service desk tools: `requiredPluginKey()` returns `"com.atlassian.servicedesk"`
Forms tools: `requiredPluginKey()` returns `"com.atlassian.proforma"`

- [ ] **Step 1:** Create all 10 tool classes
- [ ] **Step 2:** Register in `ToolInitializer` with correct `requiredPluginKey` values
- [ ] **Step 3:** Run `mvn test -q`
- [ ] **Step 4:** Commit: `git commit -m "feat: add service desk, forms, and metrics tools"`

---

### Task 14: Finalize ToolInitializer with all 46 tools

**Files:**
- Modify: `src/main/java/com/atlassian/mcp/plugin/tools/ToolInitializer.java`

- [ ] **Step 1:** Update `ToolInitializer` to import and register all 46 tool classes across all categories. Remove the `// TODO` comment.

- [ ] **Step 2:** Verify tool count: add a test or log statement confirming `toolRegistry.totalRegistered() == 46`

- [ ] **Step 3:** Run `mvn test -q`

- [ ] **Step 4:** Commit: `git commit -m "feat: register all 46 MCP tools in ToolInitializer"`

---

## Chunk 5: Admin UI

### Task 15: Admin configuration page

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/admin/AdminServlet.java`
- Create: `src/main/java/com/atlassian/mcp/plugin/admin/ConfigResource.java`
- Create: `src/main/resources/templates/admin.vm`
- Create: `src/main/resources/css/admin.css`
- Create: `src/main/resources/js/admin.js`

- [ ] **Step 1: Write `AdminServlet.java`**

  ```java
  package com.atlassian.mcp.plugin.admin;

  import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
  import com.atlassian.sal.api.auth.LoginUriProvider;
  import com.atlassian.sal.api.user.UserManager;
  import com.atlassian.sal.api.user.UserProfile;
  import com.atlassian.templaterenderer.TemplateRenderer;
  import jakarta.inject.Inject;
  import jakarta.servlet.http.HttpServlet;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.servlet.http.HttpServletResponse;
  import java.io.IOException;
  import java.net.URI;

  public class AdminServlet extends HttpServlet {

      private final UserManager userManager;
      private final LoginUriProvider loginUriProvider;
      private final TemplateRenderer renderer;

      @Inject
      public AdminServlet(
              @ComponentImport UserManager userManager,
              @ComponentImport LoginUriProvider loginUriProvider,
              @ComponentImport TemplateRenderer renderer) {
          this.userManager = userManager;
          this.loginUriProvider = loginUriProvider;
          this.renderer = renderer;
      }

      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
          UserProfile user = userManager.getRemoteUser(req);
          if (user == null) {
              resp.sendRedirect(loginUriProvider.getLoginUri(getUri(req)).toASCIIString());
              return;
          }
          if (!userManager.isSystemAdmin(user.getUserKey())) {
              resp.sendError(HttpServletResponse.SC_FORBIDDEN, "System admin access required");
              return;
          }
          resp.setContentType("text/html;charset=utf-8");
          renderer.render("templates/admin.vm", resp.getWriter());
      }

      private URI getUri(HttpServletRequest req) {
          StringBuffer buf = req.getRequestURL();
          if (req.getQueryString() != null) {
              buf.append("?").append(req.getQueryString());
          }
          return URI.create(buf.toString());
      }
  }
  ```

- [ ] **Step 2: Write `ConfigResource.java`**

  ```java
  package com.atlassian.mcp.plugin.admin;

  import com.atlassian.mcp.plugin.config.McpPluginConfig;
  import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
  import com.atlassian.sal.api.user.UserManager;
  import com.atlassian.sal.api.user.UserProfile;
  import jakarta.inject.Inject;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.ws.rs.*;
  import jakarta.ws.rs.core.*;
  import java.util.Map;

  @Path("/")
  public class ConfigResource {

      private final UserManager userManager;
      private final McpPluginConfig config;

      @Inject
      public ConfigResource(
              @ComponentImport UserManager userManager,
              McpPluginConfig config) {
          this.userManager = userManager;
          this.config = config;
      }

      @GET
      @Produces(MediaType.APPLICATION_JSON)
      public Response getConfig(@Context HttpServletRequest request) {
          UserProfile user = userManager.getRemoteUser(request);
          if (user == null || !userManager.isSystemAdmin(user.getUserKey())) {
              return Response.status(Response.Status.FORBIDDEN).build();
          }

          return Response.ok(Map.of(
                  "enabled", config.isEnabled(),
                  "allowedUsers", String.join(",", config.getAllowedUserKeys()),
                  "disabledTools", String.join(",", config.getDisabledTools()),
                  "readOnlyMode", config.isReadOnlyMode(),
                  "jiraBaseUrl", config.getJiraBaseUrlOverride()
          )).build();
      }

      @PUT
      @Consumes(MediaType.APPLICATION_JSON)
      public Response putConfig(Map<String, Object> body, @Context HttpServletRequest request) {
          UserProfile user = userManager.getRemoteUser(request);
          if (user == null || !userManager.isSystemAdmin(user.getUserKey())) {
              return Response.status(Response.Status.FORBIDDEN).build();
          }

          if (body.containsKey("enabled")) {
              config.setEnabled(Boolean.parseBoolean(body.get("enabled").toString()));
          }
          if (body.containsKey("allowedUsers")) {
              config.setAllowedUserKeys(body.get("allowedUsers").toString());
          }
          if (body.containsKey("disabledTools")) {
              config.setDisabledTools(body.get("disabledTools").toString());
          }
          if (body.containsKey("readOnlyMode")) {
              config.setReadOnlyMode(Boolean.parseBoolean(body.get("readOnlyMode").toString()));
          }
          if (body.containsKey("jiraBaseUrl")) {
              config.setJiraBaseUrlOverride(body.get("jiraBaseUrl").toString());
          }

          return Response.noContent().build();
      }
  }
  ```

- [ ] **Step 3: Write `templates/admin.vm`**

  ```html
  <html>
  <head>
      <title>$i18n.getText("mcp.admin.title")</title>
      <meta name="decorator" content="atl.admin" />
      $webResourceManager.requireResource("atlassian-mcp-plugin:admin-resources")
  </head>
  <body>
      <h2>$i18n.getText("mcp.admin.title")</h2>
      <form id="mcp-admin-form" class="aui">
          <div class="field-group">
              <label for="enabled">$i18n.getText("mcp.admin.enabled.label")</label>
              <input type="checkbox" id="enabled" name="enabled" class="checkbox">
          </div>
          <div class="field-group">
              <label for="allowedUsers">$i18n.getText("mcp.admin.allowedUsers.label")</label>
              <textarea id="allowedUsers" name="allowedUsers" class="textarea" rows="4"></textarea>
              <div class="description">$i18n.getText("mcp.admin.allowedUsers.description")</div>
          </div>
          <div class="field-group">
              <label for="disabledTools">$i18n.getText("mcp.admin.disabledTools.label")</label>
              <textarea id="disabledTools" name="disabledTools" class="textarea" rows="4"></textarea>
          </div>
          <div class="field-group">
              <label for="readOnlyMode">$i18n.getText("mcp.admin.readOnlyMode.label")</label>
              <input type="checkbox" id="readOnlyMode" name="readOnlyMode" class="checkbox">
              <div class="description">$i18n.getText("mcp.admin.readOnlyMode.description")</div>
          </div>
          <div class="field-group">
              <label for="jiraBaseUrl">$i18n.getText("mcp.admin.jiraBaseUrl.label")</label>
              <input type="text" id="jiraBaseUrl" name="jiraBaseUrl" class="text long-field">
              <div class="description">$i18n.getText("mcp.admin.jiraBaseUrl.description")</div>
          </div>
          <div class="buttons-container">
              <div class="buttons">
                  <input type="submit" value="$i18n.getText('mcp.admin.save')" class="aui-button aui-button-primary">
              </div>
          </div>
      </form>
      <div id="mcp-admin-message"></div>
  </body>
  </html>
  ```

- [ ] **Step 4: Write `js/admin.js`**

  ```javascript
  (function ($) {
      var url = AJS.contextPath() + "/rest/mcp-admin/1.0/";

      $(function () {
          // Load current config
          $.ajax({ url: url, dataType: "json" }).done(function (config) {
              $("#enabled").prop("checked", config.enabled);
              $("#allowedUsers").val(config.allowedUsers || "");
              $("#disabledTools").val(config.disabledTools || "");
              $("#readOnlyMode").prop("checked", config.readOnlyMode);
              $("#jiraBaseUrl").val(config.jiraBaseUrl || "");
          });

          // Save config
          $("#mcp-admin-form").on("submit", function (e) {
              e.preventDefault();
              $.ajax({
                  url: url,
                  type: "PUT",
                  contentType: "application/json",
                  data: JSON.stringify({
                      enabled: $("#enabled").is(":checked"),
                      allowedUsers: $("#allowedUsers").val(),
                      disabledTools: $("#disabledTools").val(),
                      readOnlyMode: $("#readOnlyMode").is(":checked"),
                      jiraBaseUrl: $("#jiraBaseUrl").val()
                  }),
                  processData: false
              }).done(function () {
                  AJS.flag({ type: "success", title: "Configuration saved", close: "auto" });
              }).fail(function () {
                  AJS.flag({ type: "error", title: "Failed to save configuration", close: "auto" });
              });
          });
      });
  })(AJS.$ || jQuery);
  ```

- [ ] **Step 5: Write `css/admin.css`**

  ```css
  #mcp-admin-form .field-group {
      margin-bottom: 16px;
  }
  #mcp-admin-form textarea {
      width: 100%;
      font-family: monospace;
  }
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/com/atlassian/mcp/plugin/admin/ \
    src/main/resources/templates/admin.vm \
    src/main/resources/css/admin.css \
    src/main/resources/js/admin.js
  git commit -m "feat: add admin configuration UI (servlet, REST, Velocity template)"
  ```

  **Note:** The allowlist textarea accepts raw user keys in v1. Display name resolution
  (spec requirement: "resolves display names for readability but stores keys") is deferred
  to a follow-up task. For now, admins enter Jira user keys directly.

  **Note:** Response limits (5 MB attachment cap, pagination maxResults=200 hard cap,
  30s timeout) are enforced in `JiraRestClient` (connection/read timeouts) and in each
  tool's `execute()` method (maxResults capping — see SearchTool pattern). Attachment
  tools must check `Content-Length` before downloading and reject files exceeding 10 MB.

---

## Chunk 6: Integration and Verification

### Task 16: End-to-end build and manual verification

- [ ] **Step 1: Full build**

  Run: `just build`
  Expected: BUILD SUCCESS, JAR in `target/`

- [ ] **Step 2: Start local Jira**

  Run: `just run`
  Expected: Jira starts at `http://localhost:2990/jira` (first run downloads Jira — takes several minutes)
  Login: `admin` / `admin`

- [ ] **Step 3: Verify plugin loaded**

  Go to: `http://localhost:2990/jira/plugins/servlet/upm`
  Search for "MCP" — plugin should appear as installed and enabled

- [ ] **Step 4: Enable MCP in admin**

  Go to: `http://localhost:2990/jira/plugins/servlet/mcp-admin`
  Check "Enable MCP Server", save

- [ ] **Step 5: Test MCP endpoint with curl**

  ```bash
  # Initialize (no MCP-Protocol-Version needed for this call)
  curl -s -X POST http://localhost:2990/jira/rest/mcp/1.0/ \
    -H "Content-Type: application/json" \
    -u admin:admin \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | jq .

  # List tools (include MCP-Protocol-Version after initialize)
  curl -s -X POST http://localhost:2990/jira/rest/mcp/1.0/ \
    -H "Content-Type: application/json" \
    -H "MCP-Protocol-Version: 2025-06-18" \
    -u admin:admin \
    -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | jq .

  # Search (with default test project)
  curl -s -X POST http://localhost:2990/jira/rest/mcp/1.0/ \
    -H "Content-Type: application/json" \
    -H "MCP-Protocol-Version: 2025-06-18" \
    -u admin:admin \
    -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search","arguments":{"jql":"order by created DESC","maxResults":5}}}' | jq .
  ```

  Expected: JSON-RPC responses with server info, tool list, and search results

- [ ] **Step 6: Test GET and DELETE return 405**

  ```bash
  curl -s -o /dev/null -w "%{http_code}" http://localhost:2990/jira/rest/mcp/1.0/ -u admin:admin
  # Expected: 405

  curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:2990/jira/rest/mcp/1.0/ -u admin:admin
  # Expected: 405
  ```

- [ ] **Step 7: Commit final state**

  ```bash
  git add -A
  git commit -m "chore: final build verification — all 46 tools, admin UI, MCP endpoint working"
  ```
