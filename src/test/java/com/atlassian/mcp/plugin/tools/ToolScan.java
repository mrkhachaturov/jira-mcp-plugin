package com.atlassian.mcp.plugin.tools;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Finds the concrete tools on the test classpath. */
final class ToolScan {

  private static final String PACKAGE = "com/atlassian/mcp/plugin/tools";

  private ToolScan() {}

  static List<Class<?>> declarativeToolClasses() throws Exception {
    return concreteSubtypesOf(DeclarativeTool.class);
  }

  /** Every tool, whichever base it extends, so a migration cannot drop one out of a check. */
  static List<Class<?>> mcpToolClasses() throws Exception {
    return concreteSubtypesOf(McpTool.class);
  }

  private static List<Class<?>> concreteSubtypesOf(Class<?> base) throws Exception {
    List<Class<?>> tools = new ArrayList<>();
    for (Class<?> type : allClasses()) {
      if (base.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
        tools.add(type);
      }
    }
    if (tools.isEmpty()) {
      throw new IllegalStateException("no " + base.getSimpleName() + " found on the classpath");
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
