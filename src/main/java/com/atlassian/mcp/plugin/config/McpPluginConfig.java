package com.atlassian.mcp.plugin.config;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import javax.inject.Inject;
import javax.inject.Named;
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
