package com.atlassian.mcp.plugin.tools;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Finds every concrete tool on the test classpath. */
final class ToolScan {

  private static final String PACKAGE = "com/atlassian/mcp/plugin/tools";

  private ToolScan() {}

  static List<Class<?>> mcpToolClasses() throws Exception {
    List<Class<?>> tools = new ArrayList<>();
    for (Class<?> type : allClasses()) {
      if (McpTool.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
        tools.add(type);
      }
    }
    if (tools.isEmpty()) {
      throw new IllegalStateException("no McpTool found on the classpath");
    }
    return tools;
  }

  private static List<Class<?>> allClasses() throws Exception {
    // getResource() would stop at target/test-classes, which holds no tools at all.
    Enumeration<URL> roots = ToolScan.class.getClassLoader().getResources(PACKAGE);
    List<Class<?>> classes = new ArrayList<>();
    while (roots.hasMoreElements()) {
      collect(new File(roots.nextElement().toURI()), PACKAGE.replace('/', '.'), classes);
    }
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
