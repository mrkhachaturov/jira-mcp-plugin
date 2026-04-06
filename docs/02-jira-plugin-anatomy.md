# Jira DC Plugin Anatomy

## Plugin Descriptor (`atlassian-plugin.xml`)

The central configuration file for all plugin modules. Located at
`src/main/resources/atlassian-plugin.xml`.

### Basic Structure

```xml
<atlassian-plugin key="${project.groupId}.${project.artifactId}"
                  name="${project.name}" plugins-version="2">
    <plugin-info>
        <description>${project.description}</description>
        <version>${project.version}</version>
        <vendor name="My Company" url="https://example.com"/>
        <param name="plugin-icon">images/pluginIcon.png</param>
        <param name="plugin-logo">images/pluginLogo.png</param>
    </plugin-info>

    <!-- Modules declared here -->
</atlassian-plugin>
```

## All Module Types

| Module Type | Element | Purpose |
|---|---|---|
| REST API | `<rest>` | JAX-RS REST endpoints |
| Servlet | `<servlet>` | HTTP servlets |
| Servlet Filter | `<servlet-filter>` | Request/response filters |
| Web Resource | `<web-resource>` | JS, CSS, images |
| Web Section | `<web-section>` | Menu section containers |
| Web Item | `<web-item>` | Menu links/buttons |
| Web Panel | `<web-panel>` | Content panels in page locations |
| Component | `<component>` | Internal Spring components |
| Component Import | `<component-import>` | Import from host/other plugins |
| Active Objects | `<ao>` | Database entity declarations |
| Module Type | `<module-type>` | Custom module type definitions |
| Tab Panel | `<component-tabpanel>` | Tabs on browse pages |
| i18n | `<resource type="i18n">` | Internationalization strings |

## Spring Component Model

Jira DC plugins use Spring via the Atlassian Spring Scanner.

### Annotations

```java
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
// Jira 10+: use jakarta.inject; Jira 9: use javax.inject
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("myComponent")
@ExportAsService({MyComponent.class})
public class MyComponentImpl implements MyComponent {

    private final ApplicationProperties applicationProperties;

    @Inject
    public MyComponentImpl(
            @ComponentImport ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }
}
```

| Annotation | Purpose |
|---|---|
| `@ComponentImport` | Import a component from the host application or another plugin |
| `@ExportAsService` | Export this component as an OSGi service |
| `@Named` | Give the bean a name (JSR-330) |
| `@Inject` | Constructor/field injection (JSR-330) |

### Maven Dependencies for Spring Scanner

```xml
<dependency>
    <groupId>com.atlassian.plugin</groupId>
    <artifactId>atlassian-spring-scanner-annotation</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>com.atlassian.plugin</groupId>
    <artifactId>atlassian-spring-scanner-runtime</artifactId>
    <scope>runtime</scope>
</dependency>
```

Plus the build plugin:

```xml
<plugin>
    <groupId>com.atlassian.plugin</groupId>
    <artifactId>atlassian-spring-scanner-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>atlassian-spring-scanner</goal></goals>
            <phase>process-classes</phase>
        </execution>
    </executions>
</plugin>
```

## OSGi Bundle Configuration

The `<instructions>` block in `pom.xml` controls OSGi packaging:

```xml
<instructions>
    <Atlassian-Plugin-Key>${atlassian.plugin.key}</Atlassian-Plugin-Key>
    <Export-Package>
        com.example.plugins.api,
    </Export-Package>
    <Import-Package>
        org.springframework.osgi.*;resolution:="optional",
        org.eclipse.gemini.blueprint.*;resolution:="optional",
        *
    </Import-Package>
    <Spring-Context>*</Spring-Context>
</instructions>
```

- `Export-Package`: Packages visible to other plugins (API only)
- `Import-Package`: Dependencies resolved from the OSGi container
- `Spring-Context`: Enable Spring context scanning

## Key Atlassian APIs

### SAL (Shared Access Layer)

Cross-product APIs that work across Jira, Confluence, etc.

```xml
<!-- Import in atlassian-plugin.xml (legacy) or use @ComponentImport -->
<component-import key="pluginSettingsFactory"
    interface="com.atlassian.sal.api.pluginsettings.PluginSettingsFactory" />
<component-import key="userManager"
    interface="com.atlassian.sal.api.user.UserManager" />
<component-import key="loginUriProvider"
    interface="com.atlassian.sal.api.auth.LoginUriProvider" />
```

| SAL Service | Purpose |
|---|---|
| `PluginSettingsFactory` | Persistent key-value storage for plugin config |
| `UserManager` | Get current user, check admin status |
| `LoginUriProvider` | Get login page URI for redirects |
| `ApplicationProperties` | App name, base URL, build info |
| `TransactionTemplate` | Transaction management |
| `TemplateRenderer` | Render Velocity templates |

### PluginSettings Usage

```java
PluginSettings settings = pluginSettingsFactory.createGlobalSettings();

// Store
settings.put("com.example.myplugin.configKey", "value");

// Retrieve
String value = (String) settings.get("com.example.myplugin.configKey");

// Project-scoped settings
PluginSettings projectSettings = pluginSettingsFactory.createSettingsForKey("MYPROJECT");
```

### User/Permission Checks

```java
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

@Inject
public MyServlet(@ComponentImport UserManager userManager) {
    this.userManager = userManager;
}

protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    UserProfile user = userManager.getRemoteUser(req);
    if (user == null) {
        // Not logged in - redirect to login
        redirectToLogin(req, resp);
        return;
    }
    if (!userManager.isSystemAdmin(user.getUserKey())) {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
    }
    // User is admin, proceed
}
```

## Active Objects (Database Storage)

ORM layer for plugin-private database tables.

### Entity Definition

```java
import net.java.ao.Entity;
import net.java.ao.Preload;

@Preload
public interface McpToolConfig extends Entity {
    String getToolName();
    void setToolName(String name);

    boolean isEnabled();
    void setEnabled(boolean enabled);

    String getAllowedUsers();
    void setAllowedUsers(String users);
}
```

### Declaration in `atlassian-plugin.xml`

```xml
<ao key="ao-module">
    <description>MCP Plugin Active Objects</description>
    <entity>com.example.plugins.ao.McpToolConfig</entity>
</ao>
```

### Maven Dependency

```xml
<dependency>
    <groupId>com.atlassian.activeobjects</groupId>
    <artifactId>activeobjects-plugin</artifactId>
    <scope>provided</scope>
</dependency>
```

## Security: Authentication in Jira DC

### Methods

| Method | Use Case |
|---|---|
| **Personal Access Tokens (PAT)** | Recommended for API integrations |
| **Basic Auth** | Username + password, scripts/bots only |
| **OAuth** | Full OAuth flow via Application Links |
| **Cookie-Based** | Browser JavaScript calls |

### PAT Authentication

Users create PATs via: Profile > Personal Access Tokens > Create Token

Used in HTTP headers:
```
Authorization: Bearer <PAT_TOKEN>
```

PATs respect the user's permissions - if a user can't do it in the UI, they can't do
it via PAT either.

### XSRF Protection

For servlets handling POST/PUT/DELETE, Jira requires form tokens (XSRF protection).
REST endpoints declared via `<rest>` module handle this automatically.

## References

- Plugin Descriptor: https://developer.atlassian.com/server/framework/atlassian-sdk/plugin-descriptor/
- SAL Developer Guide: https://developer.atlassian.com/server/framework/atlassian-sdk/sal-code-samples/
- Active Objects: https://developer.atlassian.com/server/framework/atlassian-sdk/active-objects/
- Spring Scanner: https://developer.atlassian.com/server/framework/atlassian-sdk/atlassian-spring-scanner/
