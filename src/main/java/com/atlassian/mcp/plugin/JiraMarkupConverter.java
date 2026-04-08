package com.atlassian.mcp.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bidirectional converter between Jira wiki markup and Markdown.
 * <p>
 * Ported from upstream mcp-atlassian's {@code JiraPreprocessor} in
 * {@code preprocessing/jira.py}. Uses the same regex-based approach with
 * code-block protection (extract → transform → restore).
 * <p>
 * Both methods are null/blank-safe and idempotent on non-markup input.
 */
public final class JiraMarkupConverter {

    private JiraMarkupConverter() {}

    // ── Valid Jira code block languages ──────────────────────────────────
    // Source: upstream VALID_JIRA_LANGUAGES set
    private static final Set<String> VALID_JIRA_LANGUAGES = Set.of(
            "actionscript", "actionscript3", "ada", "applescript",
            "bash", "sh", "c", "c#", "csharp", "cs", "c++", "cpp",
            "css", "sass", "less", "coldfusion", "delphi",
            "diff", "patch", "erlang", "erl", "go", "groovy",
            "haskell", "html", "xml", "java", "javafx",
            "javascript", "js", "json", "lua", "nyan",
            "objc", "objective-c", "perl", "php", "powershell", "ps1",
            "python", "py", "r", "rainbow", "ruby", "rb",
            "scala", "sql", "swift", "visualbasic", "vb",
            "yaml", "yml", "none"
    );

    // Mapping for unsupported languages to closest valid Jira alternative
    private static final Map<String, String> LANGUAGE_MAPPING = Map.ofEntries(
            Map.entry("dockerfile", "bash"), Map.entry("docker", "bash"),
            Map.entry("typescript", "javascript"), Map.entry("ts", "javascript"),
            Map.entry("tsx", "javascript"), Map.entry("jsx", "javascript"),
            Map.entry("kotlin", "java"), Map.entry("kt", "java"),
            Map.entry("makefile", "bash"), Map.entry("make", "bash"),
            Map.entry("cmake", "bash")
    );

    // ── Placeholder infrastructure ──────────────────────────────────────
    // Uses NUL-delimited placeholders identical to upstream's _extract_blocks

    private static String extractBlocks(String text, Pattern pattern,
                                        java.util.function.Function<Matcher, String> transformFn,
                                        List<String> storage, String prefix) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String transformed = transformFn.apply(m);
            String placeholder = "\0" + prefix + storage.size() + "\0";
            storage.add(transformed);
            m.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String restoreBlocks(String text, List<String> storage, String prefix) {
        // Restore in reverse order (highest index first) to avoid collisions
        for (int i = storage.size() - 1; i >= 0; i--) {
            text = text.replace("\0" + prefix + i + "\0", storage.get(i));
        }
        return text;
    }

    // ── Jira wiki markup → Markdown ─────────────────────────────────────

    /**
     * Convert Jira wiki markup to Markdown.
     * Mirrors upstream {@code JiraPreprocessor.jira_to_markdown()}.
     */
    public static String jiraToMarkdown(String input) {
        if (input == null || input.isEmpty()) return "";

        String output = input;
        List<String> codeBlocks = new ArrayList<>();
        List<String> inlineCodes = new ArrayList<>();

        // ── Protect code blocks from downstream transformations ──

        // {code:lang}...{code}
        output = extractBlocks(output,
                Pattern.compile("\\{code(?::([a-z]+))?\\}([\\s\\S]*?)\\{code\\}", Pattern.MULTILINE),
                m -> {
                    String lang = m.group(1) != null ? m.group(1) : "";
                    return "```" + lang + "\n" + m.group(2) + "\n```";
                },
                codeBlocks, "CODEBLOCK");

        // {noformat}...{noformat}
        output = extractBlocks(output,
                Pattern.compile("\\{noformat\\}([\\s\\S]*?)\\{noformat\\}"),
                m -> "```\n" + m.group(1) + "\n```",
                codeBlocks, "CODEBLOCK");

        // {{monospaced}} → `monospaced`
        output = extractBlocks(output,
                Pattern.compile("\\{\\{([^}]+)\\}\\}"),
                m -> "`" + m.group(1) + "`",
                inlineCodes, "INLINECODE");

        // ── Block quotes ──
        output = Pattern.compile("^bq\\.(.*?)$", Pattern.MULTILINE)
                .matcher(output).replaceAll("> $1\n");

        // ── Text formatting (bold, italic) ──
        // Jira: *bold* → **bold**, _italic_ → *italic*
        output = Pattern.compile("([*_])(.*?)\\1")
                .matcher(output).replaceAll(m ->
                        ("*".equals(m.group(1)) ? "**" : "*")
                                + m.group(2)
                                + ("*".equals(m.group(1)) ? "**" : "*"));

        // ── Lists (multi-level) ──
        // Jira: # numbered, * bulleted, ## nested numbered, etc.
        output = Pattern.compile("^((?:#|-|\\+|\\*)+) (.*)$", Pattern.MULTILINE)
                .matcher(output).replaceAll(m -> convertJiraListToMarkdown(m.group(1), m.group(2)));

        // ── Headers ──
        // h1. Title → # Title
        output = Pattern.compile("^h([0-6])\\.(.*)$", Pattern.MULTILINE)
                .matcher(output).replaceAll(m ->
                        "#".repeat(Integer.parseInt(m.group(1))) + m.group(2));

        // ── Citation ??text?? → <cite>text</cite> ──
        output = Pattern.compile("\\?\\?([^?]+(?:\\?[^?]+)*)\\?\\?")
                .matcher(output).replaceAll("<cite>$1</cite>");

        // ── Inserted text +text+ → <ins>text</ins> ──
        output = Pattern.compile("\\+([^+]*)\\+")
                .matcher(output).replaceAll("<ins>$1</ins>");

        // ── Superscript ^text^ → <sup>text</sup> ──
        output = Pattern.compile("\\^([^^]*)\\^")
                .matcher(output).replaceAll("<sup>$1</sup>");

        // ── Subscript ~text~ → <sub>text</sub> ──
        output = Pattern.compile("~([^~]*)~")
                .matcher(output).replaceAll("<sub>$1</sub>");

        // ── Strikethrough -text- → -text- (kept as-is in upstream) ──
        output = Pattern.compile("-([^-]*)-")
                .matcher(output).replaceAll("-$1-");

        // ── Quote blocks {quote}...{quote} → > prefixed ──
        output = Pattern.compile("\\{quote\\}([\\s\\S]*)\\{quote\\}", Pattern.MULTILINE)
                .matcher(output).replaceAll(m -> {
                    StringBuilder sb = new StringBuilder();
                    for (String line : m.group(1).split("\n")) {
                        sb.append("> ").append(line).append("\n");
                    }
                    // Remove trailing newline
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                        sb.setLength(sb.length() - 1);
                    }
                    return sb.toString();
                });

        // ── Panel blocks {panel:title=X}...{panel} ──
        output = Pattern.compile("\\{panel(?::([^}]*))?\\}([\\s\\S]*?)\\{panel\\}", Pattern.MULTILINE)
                .matcher(output).replaceAll(m -> convertPanel(m.group(1), m.group(2)));

        // ── Images ──
        // With alt text: !image.png|alt=text! → ![text](image.png)
        output = Pattern.compile("!([^|\\n\\s]+)\\|([^\\n!]*)alt=([^\\n!,]+?)(,([^\\n!]*))?!")
                .matcher(output).replaceAll("![$3]($1)");
        // With other params (ignore): !image.png|thumb! → ![](image.png)
        output = Pattern.compile("!([^|\\n\\s]+)\\|([^\\n!]*)!")
                .matcher(output).replaceAll("![]($1)");
        // Without params: !image.png! → ![](image.png)
        output = Pattern.compile("!([^\\n\\s!]+)!")
                .matcher(output).replaceAll("![]($1)");

        // ── Links ──
        // [text|url] → [text](url)
        output = Pattern.compile("\\[([^|]+)\\|(.+?)\\]")
                .matcher(output).replaceAll("[$1]($2)");
        // [url] (bare) → url
        output = Pattern.compile("\\[(.+?)\\]([^(])")
                .matcher(output).replaceAll("$1$2");

        // ── User mentions ──
        // [~username] → @username
        output = Pattern.compile("\\[~([^]]+)\\]")
                .matcher(output).replaceAll("@$1");

        // ── Colored text → <span style="color:..."> ──
        output = Pattern.compile("\\{color:([^}]+)\\}([\\s\\S]*?)\\{color\\}", Pattern.MULTILINE)
                .matcher(output).replaceAll("<span style=\"color:$1\">$2</span>");

        // ── Tables ──
        // Convert Jira table headers (||) to markdown table format
        String[] lines = output.split("\n", -1);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("||")) {
                line = line.replace("||", "|");
                result.add(line);
                // Add separator line for markdown tables
                int headerCells = countOccurrences(line, '|') - 1;
                if (headerCells > 0) {
                    result.add("|" + "---|".repeat(headerCells));
                }
            } else {
                result.add(line);
            }
        }
        output = String.join("\n", result);

        // ── Restore protected blocks ──
        output = restoreBlocks(output, codeBlocks, "CODEBLOCK");
        output = restoreBlocks(output, inlineCodes, "INLINECODE");

        return output;
    }

    // ── Markdown → Jira wiki markup ─────────────────────────────────────

    /**
     * Convert Markdown to Jira wiki markup.
     * Mirrors upstream {@code JiraPreprocessor.markdown_to_jira()}.
     */
    public static String markdownToJira(String input) {
        if (input == null || input.isEmpty()) return "";

        List<String> codeBlocks = new ArrayList<>();
        List<String> inlineCodes = new ArrayList<>();

        // ── Protect code blocks ──

        // ```lang\n...\n``` → {code:lang}...{code}
        String output = extractBlocks(input,
                Pattern.compile("```(\\w*)\\n([\\s\\S]+?)```"),
                m -> {
                    String syntax = m.group(1) != null ? m.group(1) : "";
                    String content = m.group(2);
                    String jiraLang = normalizeCodeLanguage(syntax);
                    String code = "{code";
                    if (jiraLang != null) code += ":" + jiraLang;
                    code += "}" + content + "{code}";
                    return code;
                },
                codeBlocks, "CODEBLOCK");

        // `inline` → {{inline}}
        output = extractBlocks(output,
                Pattern.compile("`([^`]+)`"),
                m -> "{{" + m.group(1) + "}}",
                inlineCodes, "INLINECODE");

        // ── Headers with = or - underlines (setext) ──
        output = Pattern.compile("^(.*?)\\n([=-])+$", Pattern.MULTILINE)
                .matcher(output).replaceAll(m ->
                        "h" + (m.group(2).charAt(0) == '=' ? 1 : 2) + ". " + m.group(1));

        // ── Headers with # prefix (ATX) — require space after # ──
        output = Pattern.compile("^(#+) (.*)$", Pattern.MULTILINE)
                .matcher(output).replaceAll(m ->
                        "h" + m.group(1).length() + ". " + m.group(2));

        // ── Bold and italic — skip list lines ──
        String[] boldLines = output.split("\n", -1);
        for (int i = 0; i < boldLines.length; i++) {
            String line = boldLines[i];
            // Skip lines starting with asterisks+space (Jira list syntax)
            if (Pattern.matches("^[*_]+\\s.*", line)) continue;
            boldLines[i] = Pattern.compile("([*_]+)(.*?)\\1")
                    .matcher(line).replaceAll(m ->
                            (m.group(1).length() == 1 ? "_" : "*")
                                    + m.group(2)
                                    + (m.group(1).length() == 1 ? "_" : "*"));
        }
        output = String.join("\n", boldLines);

        // ── Bulleted list ──
        output = Pattern.compile("^(\\s+)?[-+*] (.*)$", Pattern.MULTILINE)
                .matcher(output).replaceAll(m -> {
                    int indent = m.group(1) != null ? m.group(1).length() : 0;
                    int level = indent / 2 + 1;
                    return "*".repeat(level) + " " + m.group(2);
                });

        // ── Numbered list ──
        output = Pattern.compile("^(\\s+)?\\d+\\. (.*)$", Pattern.MULTILINE)
                .matcher(output).replaceAll(m -> {
                    int indent = m.group(1) != null ? m.group(1).length() : 0;
                    int level = indent / 2 + 1;
                    return "#".repeat(level) + " " + m.group(2);
                });

        // ── HTML tags to Jira markup ──
        output = output.replaceAll("<cite>(.*?)</cite>", "??$1??");
        output = output.replaceAll("<del>(.*?)</del>", "-$1-");
        output = output.replaceAll("<ins>(.*?)</ins>", "+$1+");
        output = output.replaceAll("<sup>(.*?)</sup>", "^$1^");
        output = output.replaceAll("<sub>(.*?)</sub>", "~$1~");

        // ── Colored text spans ──
        output = Pattern.compile("<span style=\"color:(#[^\"]+)\">" +
                        "([\\s\\S]*?)</span>", Pattern.MULTILINE)
                .matcher(output).replaceAll("{color:$1}$2{color}");

        // ── Strikethrough ~~text~~ → -text- ──
        output = Pattern.compile("~~(.*?)~~")
                .matcher(output).replaceAll("-$1-");

        // ── Images ──
        // ![](url) → !url!
        output = Pattern.compile("!\\[\\]\\(([^)\\n\\s]+)\\)")
                .matcher(output).replaceAll("!$1!");
        // ![alt](url) → !url|alt=text!
        output = Pattern.compile("!\\[([^]\\n]+)\\]\\(([^)\\n\\s]+)\\)")
                .matcher(output).replaceAll("!$2|alt=$1!");

        // ── Links ──
        // [text](url) → [text|url]
        output = Pattern.compile("\\[([^]]+)\\]\\(([^)]+)\\)")
                .matcher(output).replaceAll("[$1|$2]");
        // <url> → [url]
        output = Pattern.compile("<([^>]+)>")
                .matcher(output).replaceAll("[$1]");

        // ── Blockquotes > text → bq. text ──
        output = Pattern.compile("^> (.*)$", Pattern.MULTILINE)
                .matcher(output).replaceAll("bq. $1");

        // ── User mentions @username → [~username] ──
        output = Pattern.compile("@(\\w+)")
                .matcher(output).replaceAll("[~$1]");

        // ── Tables ──
        // Convert markdown tables to Jira format
        String[] tableLines = output.split("\n", -1);
        List<String> tableResult = new ArrayList<>();
        for (int i = 0; i < tableLines.length; i++) {
            // If next line is a separator row (|---|---|), convert header to ||
            if (i < tableLines.length - 1
                    && Pattern.matches("\\|[-\\s|]+\\|", tableLines[i + 1])) {
                tableResult.add(tableLines[i].replace("|", "||"));
                i++; // skip separator line
            } else {
                tableResult.add(tableLines[i]);
            }
        }
        output = String.join("\n", tableResult);

        // ── Restore protected blocks ──
        output = restoreBlocks(output, codeBlocks, "CODEBLOCK");
        output = restoreBlocks(output, inlineCodes, "INLINECODE");

        return output;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static String convertJiraListToMarkdown(String jiraBullets, String content) {
        int indentLevel = jiraBullets.length() - 1;
        String indent = " ".repeat(indentLevel * 2);
        char lastChar = jiraBullets.charAt(jiraBullets.length() - 1);
        String prefix = lastChar == '#' ? "1." : "-";
        return indent + prefix + " " + content;
    }

    private static String convertPanel(String params, String content) {
        String title = "";
        if (params != null) {
            Matcher m = Pattern.compile("title=([^|}]+)").matcher(params);
            if (m.find()) title = m.group(1).trim();
        }
        String trimmed = content.trim();
        if (!title.isEmpty()) {
            return "\n**" + title + "**\n" + trimmed + "\n";
        }
        return "\n" + trimmed + "\n";
    }

    private static String normalizeCodeLanguage(String lang) {
        if (lang == null || lang.isEmpty()) return null;
        String lower = lang.toLowerCase();
        if (VALID_JIRA_LANGUAGES.contains(lower)) return lower;
        return LANGUAGE_MAPPING.getOrDefault(lower, null);
    }

    private static int countOccurrences(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }
}
