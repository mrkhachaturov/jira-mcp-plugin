package com.atlassian.mcp.plugin.tools;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * Proves that no tool advertises a parameter it does not consume. This is the guard for the bug
 * class that motivated {@link DeclarativeTool}: 22 parameters across ten tools were declared in a
 * hand-written schema, parsed into a local, and then dropped — so agents were told about
 * pagination and filters that silently did nothing.
 *
 * <p>Every {@link DeclarativeTool} on the classpath is driven with a filled-in argument map and
 * must read every parameter it declares. A tool whose validation legitimately short-circuits
 * before reading everything supplies a fixture in {@link #FIXTURES}.
 */
public class DeclarativeToolContractTest {

  /** Per-tool argument overrides for tools that generic values cannot get through. */
  private static final Map<String, Map<String, Object>> FIXTURES = Map.of();

  @Test
  public void everyDeclaredParamIsConsumed() throws Exception {
    List<DeclarativeTool> tools = discoverTools();
    assertFalse("no DeclarativeTool found — is the scan path right?", tools.isEmpty());

    Map<String, Set<String>> unconsumed = new LinkedHashMap<>();
    for (DeclarativeTool tool : tools) {
      Set<String> declared = new LinkedHashSet<>();
      for (ToolParam<?> param : tool.params()) declared.add(param.name());

      ToolArgs args = new ToolArgs(tool.params(), argumentsFor(tool));
      try {
        tool.run(args, "Bearer test");
      } catch (McpToolException | RuntimeException e) {
        // A tool may reject the synthetic arguments; what it read up to that point still counts.
      }

      Set<String> missed = new TreeSet<>(declared);
      missed.removeAll(args.readParams());
      if (!missed.isEmpty()) unconsumed.put(tool.name(), missed);
    }

    assertEquals(
        "these tools advertise parameters they never read — wire them up or drop them from params()",
        Map.of(),
        unconsumed);
  }

  @Test
  public void declaredNamesAreUniquePerTool() throws Exception {
    for (DeclarativeTool tool : discoverTools()) {
      Set<String> seen = new LinkedHashSet<>();
      for (ToolParam<?> param : tool.params()) {
        assertTrue(
            tool.name() + " declares '" + param.name() + "' twice", seen.add(param.name()));
      }
    }
  }

  private static Map<String, Object> argumentsFor(DeclarativeTool tool) {
    Map<String, Object> fixture = FIXTURES.get(tool.name());
    if (fixture != null) return fixture;

    Map<String, Object> args = new LinkedHashMap<>();
    for (ToolParam<?> param : tool.params()) {
      Object type = param.schema().get("type");
      args.put(param.name(), sampleValue(String.valueOf(type)));
    }
    return args;
  }

  private static Object sampleValue(String jsonType) {
    switch (jsonType) {
      case "integer":
      case "number":
        return 1;
      case "boolean":
        return Boolean.TRUE;
      case "array":
        return List.of();
      case "object":
        return Map.of();
      default:
        return "1";
    }
  }

  private static List<DeclarativeTool> discoverTools() throws Exception {
    JiraRestClient client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{}");
    when(client.post(anyString(), anyString(), any())).thenReturn("{}");
    when(client.put(anyString(), anyString(), any())).thenReturn("{}");

    List<DeclarativeTool> tools = new ArrayList<>();
    for (Class<?> type : scanToolClasses()) {
      if (!DeclarativeTool.class.isAssignableFrom(type)) continue;
      if (java.lang.reflect.Modifier.isAbstract(type.getModifiers())) continue;
      tools.add(instantiate(type, client));
    }
    return tools;
  }

  private static DeclarativeTool instantiate(Class<?> type, JiraRestClient client) throws Exception {
    Constructor<?> best = null;
    for (Constructor<?> candidate : type.getConstructors()) {
      if (best == null || candidate.getParameterCount() < best.getParameterCount()) {
        best = candidate;
      }
    }
    assertNotNull("no public constructor on " + type.getName(), best);

    Object[] arguments = new Object[best.getParameterCount()];
    Class<?>[] types = best.getParameterTypes();
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = types[i] == JiraRestClient.class ? client : mock(types[i]);
    }
    return (DeclarativeTool) best.newInstance(arguments);
  }

  private static List<Class<?>> scanToolClasses() throws Exception {
    String pkg = "com/atlassian/mcp/plugin/tools";
    // getResource() would stop at target/test-classes, which holds no tools at all.
    java.util.Enumeration<URL> roots =
        DeclarativeToolContractTest.class.getClassLoader().getResources(pkg);

    List<Class<?>> classes = new ArrayList<>();
    while (roots.hasMoreElements()) {
      collect(new File(roots.nextElement().toURI()), pkg.replace('/', '.'), classes);
    }
    assertFalse("tools package not on classpath", classes.isEmpty());
    return classes;
  }

  private static void collect(File dir, String pkg, List<Class<?>> out) throws Exception {
    File[] entries = dir.listFiles();
    if (entries == null) return;
    for (File entry : entries) {
      if (entry.isDirectory()) {
        collect(entry, pkg + "." + entry.getName(), out);
      } else if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
        out.add(Class.forName(pkg + "." + entry.getName().replace(".class", "")));
      }
    }
  }
}
