# Atlassian Plugin SDK (AMPS)

## Overview

The Atlassian Plugin SDK enables developers to create extensions for Data Center products
including Jira Software, Confluence, Bitbucket, and Bamboo. Plugins use the P2 (Plugin
Framework 2) architecture and run inside the host application's JVM as OSGi bundles.

## System Requirements

- JDK 8 (for Jira 9.x) or JDK 17 (for Jira 10.x+)
- Port 2990 available (default Jira dev port)
- Maven (bundled with the SDK)

## Installation

Download from: https://developer.atlassian.com/server/framework/atlassian-sdk/

```bash
# macOS (Homebrew)
brew tap atlassian/tap
brew install atlassian-plugin-sdk

# Verify
atlas-version
```

## AMPS Versions

AMPS (Atlassian Maven Plugin Suite) and the Atlassian SDK are now **separate products**
with distinct version numbers.

| AMPS Version | Key Changes |
|---|---|
| 9.12.4 | Latest (April 2026), Bitbucket 11 support |
| 9.7.x | QuickReload 6.2.0+, recommended for current dev |
| 9.6.x | 4 new `<product>` element properties |
| 9.3.0 | Updated Tomcat mappings for Jira 11, Confluence 10 |
| 9.0.0 | JVM specification capability, Crowd 6.0.0 |
| 8.x | Older series, still supported |
| 6.x | Legacy (Jira 7.x era) |

### Jira Version Targets

| Jira Version | Java | Notes |
|---|---|---|
| 9.12 LTS | Java 11 | Current Long Term Support |
| 10.3 LTS | Java 17 | Jakarta EE migration |
| 10.7.4 | Java 17 | Your running version |
| 11.x | Java 17+ | Upcoming |

## Project Creation

```bash
# Create a new Jira plugin project
atlas-create-jira-plugin

# Prompts:
#   groupId: com.example.plugins
#   artifactId: my-jira-plugin
#   version: 1.0.0-SNAPSHOT
#   package: com.example.plugins.jira

# Add modules interactively
atlas-create-jira-plugin-module
```

### Generated Structure

```
my-jira-plugin/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/plugins/jira/
│   │   │   ├── api/MyPluginComponent.java
│   │   │   └── impl/MyPluginComponentImpl.java
│   │   └── resources/
│   │       ├── atlassian-plugin.xml
│   │       ├── META-INF/spring/plugin-context.xml
│   │       ├── css/
│   │       ├── js/
│   │       └── images/
│   └── test/
│       ├── java/it/  (integration tests)
│       └── java/ut/  (unit tests)
└── target/
```

## POM Configuration (Modern)

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example.plugins</groupId>
  <artifactId>my-jira-plugin</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>atlassian-plugin</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>com.atlassian.maven.plugins</groupId>
        <artifactId>jira-maven-plugin</artifactId>
        <version>9.6.6</version>
        <extensions>true</extensions>
        <configuration>
          <productVersion>10.7.4</productVersion>
          <productDataVersion>10.7.4</productDataVersion>
          <allowGoogleTracking>false</allowGoogleTracking>
          <enableQuickReload>true</enableQuickReload>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

## Key SDK Commands

| Command | Purpose |
|---------|---------|
| `atlas-run` | Start Jira with your plugin installed (localhost:2990/jira) |
| `atlas-debug` | Same as atlas-run with remote debugging enabled |
| `atlas-package` | Build the plugin JAR/OBR without running |
| `atlas-clean` | Clean build artifacts |
| `atlas-create-jira-plugin` | Create new Jira plugin project |
| `atlas-create-jira-plugin-module` | Add module to existing plugin |
| `atlas-unit-test` | Run unit tests |
| `atlas-integration-test` | Run integration tests |

### atlas-run Options

```bash
atlas-run [options]

--version / -v        # Jira version (default: RELEASE)
--container / -c      # Container (default: tomcat7x)
--http-port / -p      # HTTP port (default: 2990 for Jira)
--context-path        # App context path (default: /jira)
--jvmargs             # Additional JVM arguments
--plugins             # Extra plugins: GROUP:ARTIFACT:VERSION
--product             # Target product override
```

## Maven Repositories

```xml
<repositories>
  <repository>
    <id>atlassian-public</id>
    <url>https://packages.atlassian.com/maven-public/</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>

<pluginRepositories>
  <pluginRepository>
    <id>atlassian-public</id>
    <url>https://packages.atlassian.com/maven-public/</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </pluginRepository>
</pluginRepositories>
```

**Note:** As of Feb 2025, third-party packages are no longer mirrored via
packages.atlassian.com. Update to latest SDK for proper upstream dependency config.

### Deprecated Repositories (do not use)

- `maven.atlassian.com` (old hostname)
- `packages.atlassian.com/mvn/maven-external/`
- `packages.atlassian.com/mvn/maven-3rdparty/`

## Plugin Installation (Self-Hosted Jira DC)

For development/private plugins (no Marketplace):

1. **UPM Upload**: Jira Admin > Manage Apps > Upload App > select JAR/OBR file
2. **Requires**: `-Dupm.plugin.upload.enabled=true` on the Jira instance
3. **Optional**: `-Datlassian.upm.server.addon.signature.validation=false` to skip signature checks

Your Jira instance already has both flags enabled.

## Testing

```bash
# Unit tests only
atlas-unit-test

# Integration (wired) tests
atlas-integration-test

# Run specific test
atlas-unit-test -Dtest=MyTest
```

### Test Types

1. **Unit Tests** (`src/test/java/ut/`) - Standard JUnit/Mockito
2. **Integration Tests** (`src/test/java/it/`) - Run inside Jira container
3. **Wired Tests** - OSGi wired, access real Jira services

## References

- SDK Documentation Hub: https://developer.atlassian.com/server/framework/atlassian-sdk/
- Jira Platform Dev Docs: https://developer.atlassian.com/server/jira/platform/
- AMPS Release Notes: https://developer.atlassian.com/server/framework/atlassian-sdk/amps-sdk-release-notes/
- AMPS Build Config Reference: https://developer.atlassian.com/server/framework/atlassian-sdk/amps-build-configuration-reference/
