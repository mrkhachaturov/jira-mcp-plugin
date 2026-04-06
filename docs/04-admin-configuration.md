# Admin Configuration UI

## Overview

Jira DC plugins can add admin configuration pages using a combination of:
- A **servlet** to render the admin page (Velocity template)
- A **REST endpoint** to load/save configuration (AJAX)
- A **web-item** to add a link in the Jira admin menu
- **PluginSettings** (SAL) or **Active Objects** for persistence

## Complete Example: Admin Config Page

### 1. Plugin Descriptor (`atlassian-plugin.xml`)

```xml
<atlassian-plugin key="com.example.mcp-plugin" name="MCP Plugin" plugins-version="2">
    <plugin-info>
        <description>MCP Server for Jira DC</description>
        <version>1.0.0</version>
        <vendor name="Example" url="https://example.com"/>
    </plugin-info>

    <!-- i18n strings -->
    <resource type="i18n" name="i18n" location="mcp-plugin"/>

    <!-- Web resources (JS, CSS) -->
    <web-resource key="admin-resources" name="Admin Resources">
        <dependency>com.atlassian.auiplugin:ajs</dependency>
        <resource type="download" name="admin.css" location="/css/admin.css"/>
        <resource type="download" name="admin.js" location="/js/admin.js"/>
        <context>mcp-admin</context>
    </web-resource>

    <!-- Admin page servlet -->
    <servlet key="admin-servlet"
             class="com.example.mcp.admin.AdminServlet">
        <url-pattern>/mcp/admin</url-pattern>
    </servlet>

    <!-- REST API for config CRUD -->
    <rest key="admin-rest" path="/mcp-admin" version="1.0">
        <description>Admin configuration REST API</description>
    </rest>

    <!-- Link in Jira admin sidebar -->
    <web-item key="mcp-admin-link" name="MCP Configuration"
              section="system.admin/globalsettings" weight="10"
              application="jira">
        <label key="mcp.admin.label"/>
        <link linkId="mcp-admin-link">/plugins/servlet/mcp/admin</link>
    </web-item>
</atlassian-plugin>
```

### 2. Admin Servlet

```java
package com.example.mcp.admin;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.templaterenderer.TemplateRenderer;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        // Check authentication
        UserProfile user = userManager.getRemoteUser(req);
        if (user == null) {
            redirectToLogin(req, resp);
            return;
        }

        // Check admin permission
        if (!userManager.isSystemAdmin(user.getUserKey())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Render admin page
        resp.setContentType("text/html;charset=utf-8");
        renderer.render("admin.vm", resp.getWriter());
    }

    private void redirectToLogin(HttpServletRequest req,
                                  HttpServletResponse resp) throws IOException {
        resp.sendRedirect(loginUriProvider.getLoginUri(getUri(req)).toASCIIString());
    }

    private URI getUri(HttpServletRequest req) {
        StringBuffer builder = req.getRequestURL();
        if (req.getQueryString() != null) {
            builder.append("?").append(req.getQueryString());
        }
        return URI.create(builder.toString());
    }
}
```

### 3. Velocity Template (`src/main/resources/admin.vm`)

```html
<html>
<head>
    <title>$i18n.getText("mcp.admin.title")</title>
    <meta name="decorator" content="atl.admin" />
    $webResourceManager.requireResource(
        "com.example.mcp-plugin:admin-resources")
</head>
<body>
    <h2>$i18n.getText("mcp.admin.title")</h2>

    <form id="mcp-admin" class="aui">
        <div class="field-group">
            <label for="enabled">$i18n.getText("mcp.admin.enabled.label")</label>
            <input type="checkbox" id="enabled" name="enabled" class="checkbox">
        </div>

        <div class="field-group">
            <label for="allowedUsers">
                $i18n.getText("mcp.admin.allowed-users.label")
            </label>
            <textarea id="allowedUsers" name="allowedUsers"
                      class="textarea" rows="5"></textarea>
            <div class="description">
                $i18n.getText("mcp.admin.allowed-users.description")
            </div>
        </div>

        <div class="field-group">
            <label>$i18n.getText("mcp.admin.tools.label")</label>
            <div id="tool-checkboxes">
                <!-- Populated by JavaScript -->
            </div>
        </div>

        <div class="buttons-container">
            <div class="buttons">
                <input type="submit"
                       value="$i18n.getText('mcp.admin.save')"
                       class="aui-button aui-button-primary">
            </div>
        </div>
    </form>
</body>
</html>
```

### 4. REST Resource for Config

```java
package com.example.mcp.admin;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.xml.bind.annotation.*;

@Path("/")
public class ConfigResource {

    private static final String PLUGIN_KEY = "com.example.mcp-plugin";

    private final UserManager userManager;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final TransactionTemplate transactionTemplate;

    @Inject
    public ConfigResource(
            @ComponentImport UserManager userManager,
            @ComponentImport PluginSettingsFactory pluginSettingsFactory,
            @ComponentImport TransactionTemplate transactionTemplate) {
        this.userManager = userManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null || !userManager.isSystemAdmin(user.getUserKey())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        return Response.ok(transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            McpConfig config = new McpConfig();
            config.setEnabled(
                Boolean.parseBoolean((String) settings.get(PLUGIN_KEY + ".enabled")));
            config.setAllowedUsers(
                (String) settings.get(PLUGIN_KEY + ".allowedUsers"));
            config.setEnabledTools(
                (String) settings.get(PLUGIN_KEY + ".enabledTools"));
            return config;
        })).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(final McpConfig config,
                        @Context HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null || !userManager.isSystemAdmin(user.getUserKey())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            settings.put(PLUGIN_KEY + ".enabled",
                String.valueOf(config.isEnabled()));
            settings.put(PLUGIN_KEY + ".allowedUsers",
                config.getAllowedUsers());
            settings.put(PLUGIN_KEY + ".enabledTools",
                config.getEnabledTools());
            return null;
        });

        return Response.noContent().build();
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class McpConfig {
        @XmlElement private boolean enabled;
        @XmlElement private String allowedUsers;
        @XmlElement private String enabledTools;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAllowedUsers() { return allowedUsers; }
        public void setAllowedUsers(String allowedUsers) { this.allowedUsers = allowedUsers; }
        public String getEnabledTools() { return enabledTools; }
        public void setEnabledTools(String enabledTools) { this.enabledTools = enabledTools; }
    }
}
```

### 5. JavaScript (`src/main/resources/js/admin.js`)

```javascript
(function ($) {
    var baseUrl = AJS.contextPath() + "/rest/mcp-admin/1.0/";

    $(function () {
        // Load current config
        $.ajax({
            url: baseUrl,
            dataType: "json"
        }).done(function (config) {
            $("#enabled").prop("checked", config.enabled);
            $("#allowedUsers").val(config.allowedUsers || "");
            // Populate tool checkboxes...
        });

        // Save config
        $("#mcp-admin").on("submit", function (e) {
            e.preventDefault();
            $.ajax({
                url: baseUrl,
                type: "PUT",
                contentType: "application/json",
                data: JSON.stringify({
                    enabled: $("#enabled").is(":checked"),
                    allowedUsers: $("#allowedUsers").val(),
                    enabledTools: getSelectedTools()
                }),
                processData: false
            }).done(function () {
                AJS.flag({
                    type: "success",
                    title: "Configuration saved",
                    close: "auto"
                });
            });
        });
    });
})(AJS.$ || jQuery);
```

### 6. i18n Properties (`src/main/resources/mcp-plugin.properties`)

```properties
mcp.admin.label=MCP Configuration
mcp.admin.title=MCP Server Configuration
mcp.admin.enabled.label=Enable MCP Server
mcp.admin.allowed-users.label=Allowed Users
mcp.admin.allowed-users.description=One username per line. Leave empty to allow all users.
mcp.admin.tools.label=Enabled Tools
mcp.admin.save=Save
```

## Admin Menu Locations

| Section | Location | Description |
|---|---|---|
| `system.admin/globalsettings` | Jira Admin > System | General system settings |
| `system.admin/security` | Jira Admin > Security | Security settings |
| `system.admin/advanced` | Jira Admin > Advanced | Advanced settings |
| `system.admin/mail` | Jira Admin > Mail | Email settings |

## Maven Dependencies for Admin

```xml
<dependency>
    <groupId>com.atlassian.templaterenderer</groupId>
    <artifactId>atlassian-template-renderer-api</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
    <version>2.1</version>
    <scope>provided</scope>
</dependency>
```

## References

- Creating Admin Config Form: https://developer.atlassian.com/server/framework/atlassian-sdk/creating-an-admin-configuration-form/
- SAL Code Samples: https://developer.atlassian.com/server/framework/atlassian-sdk/sal-code-samples/
