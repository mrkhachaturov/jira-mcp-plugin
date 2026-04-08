package com.atlassian.mcp.plugin;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Unit tests for JiraMarkupConverter — both jiraToMarkdown() and markdownToJira().
 * Each conversion direction is tested against the upstream mcp-atlassian Python
 * implementation's expected behavior.
 */
public class JiraMarkupConverterTest {

    // ── Null / empty handling ───────────────────────────────────────────

    @Test
    public void jiraToMarkdown_null() {
        assertEquals("", JiraMarkupConverter.jiraToMarkdown(null));
    }

    @Test
    public void jiraToMarkdown_empty() {
        assertEquals("", JiraMarkupConverter.jiraToMarkdown(""));
    }

    @Test
    public void markdownToJira_null() {
        assertEquals("", JiraMarkupConverter.markdownToJira(null));
    }

    @Test
    public void markdownToJira_empty() {
        assertEquals("", JiraMarkupConverter.markdownToJira(""));
    }

    @Test
    public void plainText_passesThrough() {
        String text = "This is plain text with no markup.";
        assertEquals(text, JiraMarkupConverter.jiraToMarkdown(text));
        assertEquals(text, JiraMarkupConverter.markdownToJira(text));
    }

    // ── Jira → Markdown ─────────────────────────────────────────────────

    @Test
    public void jiraToMd_headers() {
        assertEquals("# Biggest heading", JiraMarkupConverter.jiraToMarkdown("h1. Biggest heading"));
        assertEquals("## Bigger heading", JiraMarkupConverter.jiraToMarkdown("h2. Bigger heading"));
        assertEquals("### Big heading", JiraMarkupConverter.jiraToMarkdown("h3. Big heading"));
        assertEquals("#### Normal heading", JiraMarkupConverter.jiraToMarkdown("h4. Normal heading"));
        assertEquals("##### Small heading", JiraMarkupConverter.jiraToMarkdown("h5. Small heading"));
        assertEquals("###### Smallest heading", JiraMarkupConverter.jiraToMarkdown("h6. Smallest heading"));
    }

    @Test
    public void jiraToMd_bold() {
        assertEquals("**bold text**", JiraMarkupConverter.jiraToMarkdown("*bold text*"));
    }

    @Test
    public void jiraToMd_italic() {
        assertEquals("*italic text*", JiraMarkupConverter.jiraToMarkdown("_italic text_"));
    }

    @Test
    public void jiraToMd_monospaced() {
        assertEquals("`monospaced`", JiraMarkupConverter.jiraToMarkdown("{{monospaced}}"));
    }

    @Test
    public void jiraToMd_codeBlock() {
        String jira = "{code:python}\ndef hello():\n    print('hi')\n{code}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("```python"));
        assertTrue(md.contains("def hello():"));
        assertTrue(md.contains("```"));
    }

    @Test
    public void jiraToMd_codeBlockNoLang() {
        String jira = "{code}\nsome code\n{code}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("```\n"));
        assertTrue(md.contains("some code"));
    }

    @Test
    public void jiraToMd_noformat() {
        String jira = "{noformat}\npreformatted text\n{noformat}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("```\n"));
        assertTrue(md.contains("preformatted text"));
    }

    @Test
    public void jiraToMd_link() {
        assertEquals("[Atlassian](http://atlassian.com)",
                JiraMarkupConverter.jiraToMarkdown("[Atlassian|http://atlassian.com]"));
    }

    @Test
    public void jiraToMd_image() {
        assertEquals("![](screenshot.png)",
                JiraMarkupConverter.jiraToMarkdown("!screenshot.png!"));
    }

    @Test
    public void jiraToMd_imageWithAlt() {
        assertEquals("![logo](logo.png)",
                JiraMarkupConverter.jiraToMarkdown("!logo.png|alt=logo!"));
    }

    @Test
    public void jiraToMd_bulletList() {
        assertEquals("- item 1\n- item 2",
                JiraMarkupConverter.jiraToMarkdown("* item 1\n* item 2"));
    }

    @Test
    public void jiraToMd_numberedList() {
        assertEquals("1. first\n1. second",
                JiraMarkupConverter.jiraToMarkdown("# first\n# second"));
    }

    @Test
    public void jiraToMd_nestedList() {
        String jira = "* top\n** nested";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("- top"));
        assertTrue(md.contains("  - nested"));
    }

    @Test
    public void jiraToMd_blockQuote() {
        // Upstream captures the space after "bq." so output is ">  text"
        String md = JiraMarkupConverter.jiraToMarkdown("bq. This is a quote");
        assertTrue(md.contains(">  This is a quote"));
    }

    @Test
    public void jiraToMd_quoteBlock() {
        String jira = "{quote}\nquoted line 1\nquoted line 2\n{quote}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("> quoted line 1"));
        assertTrue(md.contains("> quoted line 2"));
    }

    @Test
    public void jiraToMd_panel() {
        String jira = "{panel:title=My Title}\nSome content\n{panel}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("**My Title**"));
        assertTrue(md.contains("Some content"));
    }

    @Test
    public void jiraToMd_panelNoTitle() {
        String jira = "{panel}\nSome content\n{panel}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("Some content"));
        assertFalse(md.contains("**"));
    }

    @Test
    public void jiraToMd_table() {
        String jira = "||Name||Age||\n|Alice|30|\n|Bob|25|";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("|Name|Age|"));
        assertTrue(md.contains("|---|"));
        assertTrue(md.contains("|Alice|30|"));
    }

    @Test
    public void jiraToMd_colorStripped() {
        String jira = "{color:red}red text{color}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("red text"));
    }

    @Test
    public void jiraToMd_userMention() {
        assertEquals("@jsmith",
                JiraMarkupConverter.jiraToMarkdown("[~jsmith]"));
    }

    @Test
    public void jiraToMd_strikethrough() {
        // Upstream keeps strikethrough as-is (-text-)
        String md = JiraMarkupConverter.jiraToMarkdown("-deleted text-");
        assertTrue(md.contains("-deleted text-"));
    }

    @Test
    public void jiraToMd_citation() {
        assertEquals("<cite>some citation</cite>",
                JiraMarkupConverter.jiraToMarkdown("??some citation??"));
    }

    @Test
    public void jiraToMd_insertedText() {
        assertEquals("<ins>inserted</ins>",
                JiraMarkupConverter.jiraToMarkdown("+inserted+"));
    }

    @Test
    public void jiraToMd_superscript() {
        assertEquals("<sup>super</sup>",
                JiraMarkupConverter.jiraToMarkdown("^super^"));
    }

    @Test
    public void jiraToMd_subscript() {
        assertEquals("<sub>sub</sub>",
                JiraMarkupConverter.jiraToMarkdown("~sub~"));
    }

    @Test
    public void jiraToMd_codeBlockProtectsContent() {
        // Bold markers inside code blocks should NOT be converted
        String jira = "{code}\n*not bold*\n_not italic_\n{code}";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("*not bold*"));
        assertTrue(md.contains("_not italic_"));
        assertFalse(md.contains("**not bold**"));
    }

    @Test
    public void jiraToMd_inlineCodeProtectsContent() {
        String jira = "Use {{*not bold*}} for emphasis";
        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("`*not bold*`"));
        assertFalse(md.contains("**"));
    }

    // ── Markdown → Jira ─────────────────────────────────────────────────

    @Test
    public void mdToJira_headers() {
        assertEquals("h1. Title", JiraMarkupConverter.markdownToJira("# Title"));
        assertEquals("h2. Title", JiraMarkupConverter.markdownToJira("## Title"));
        assertEquals("h3. Title", JiraMarkupConverter.markdownToJira("### Title"));
        assertEquals("h4. Title", JiraMarkupConverter.markdownToJira("#### Title"));
        assertEquals("h5. Title", JiraMarkupConverter.markdownToJira("##### Title"));
        assertEquals("h6. Title", JiraMarkupConverter.markdownToJira("###### Title"));
    }

    @Test
    public void mdToJira_setext_h1() {
        assertEquals("h1. Title", JiraMarkupConverter.markdownToJira("Title\n==="));
    }

    @Test
    public void mdToJira_setext_h2() {
        assertEquals("h2. Title", JiraMarkupConverter.markdownToJira("Title\n---"));
    }

    @Test
    public void mdToJira_bold() {
        assertEquals("*bold text*", JiraMarkupConverter.markdownToJira("**bold text**"));
    }

    @Test
    public void mdToJira_italic() {
        assertEquals("_italic text_", JiraMarkupConverter.markdownToJira("*italic text*"));
    }

    @Test
    public void mdToJira_inlineCode() {
        assertEquals("{{code}}", JiraMarkupConverter.markdownToJira("`code`"));
    }

    @Test
    public void mdToJira_codeBlock() {
        String md = "```python\ndef hello():\n    pass\n```";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("{code:python}"));
        assertTrue(jira.contains("def hello():"));
        assertTrue(jira.contains("{code}"));
    }

    @Test
    public void mdToJira_codeBlockNoLang() {
        String md = "```\nplain code\n```";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("{code}"));
        assertTrue(jira.contains("plain code"));
        assertFalse(jira.contains("{code:}"));
    }

    @Test
    public void mdToJira_codeBlock_unsupportedLanguage() {
        // TypeScript maps to javascript
        String md = "```typescript\nconst x = 1;\n```";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("{code:javascript}"));
    }

    @Test
    public void mdToJira_codeBlock_unknownLanguage() {
        // Totally unknown language → plain {code}
        String md = "```brainfuck\n+++.\n```";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.startsWith("{code}"));
        assertFalse(jira.contains("{code:brainfuck}"));
    }

    @Test
    public void mdToJira_link() {
        assertEquals("[Atlassian|http://atlassian.com]",
                JiraMarkupConverter.markdownToJira("[Atlassian](http://atlassian.com)"));
    }

    @Test
    public void mdToJira_angleLink() {
        assertEquals("[http://example.com]",
                JiraMarkupConverter.markdownToJira("<http://example.com>"));
    }

    @Test
    public void mdToJira_image() {
        assertEquals("!screenshot.png!",
                JiraMarkupConverter.markdownToJira("![](screenshot.png)"));
    }

    @Test
    public void mdToJira_imageWithAlt() {
        assertEquals("!logo.png|alt=Logo!",
                JiraMarkupConverter.markdownToJira("![Logo](logo.png)"));
    }

    @Test
    public void mdToJira_bulletList() {
        assertEquals("* item 1\n* item 2",
                JiraMarkupConverter.markdownToJira("- item 1\n- item 2"));
    }

    @Test
    public void mdToJira_numberedList() {
        assertEquals("# first\n# second",
                JiraMarkupConverter.markdownToJira("1. first\n2. second"));
    }

    @Test
    public void mdToJira_nestedBulletList() {
        String md = "- top\n  - nested";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("* top"));
        assertTrue(jira.contains("** nested"));
    }

    @Test
    public void mdToJira_strikethrough() {
        assertEquals("-deleted-",
                JiraMarkupConverter.markdownToJira("~~deleted~~"));
    }

    @Test
    public void mdToJira_table() {
        String md = "| Name | Age |\n|---|---|\n| Alice | 30 |";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("||"));
        assertTrue(jira.contains("| Alice | 30 |"));
        // Separator row should be removed
        assertFalse(jira.contains("|---|"));
    }

    @Test
    public void mdToJira_htmlCite() {
        assertEquals("??cited text??",
                JiraMarkupConverter.markdownToJira("<cite>cited text</cite>"));
    }

    @Test
    public void mdToJira_htmlDel() {
        assertEquals("-deleted-",
                JiraMarkupConverter.markdownToJira("<del>deleted</del>"));
    }

    @Test
    public void mdToJira_htmlIns() {
        assertEquals("+inserted+",
                JiraMarkupConverter.markdownToJira("<ins>inserted</ins>"));
    }

    @Test
    public void mdToJira_htmlSup() {
        assertEquals("^super^",
                JiraMarkupConverter.markdownToJira("<sup>super</sup>"));
    }

    @Test
    public void mdToJira_htmlSub() {
        assertEquals("~sub~",
                JiraMarkupConverter.markdownToJira("<sub>sub</sub>"));
    }

    @Test
    public void mdToJira_userMention() {
        assertEquals("[~jsmith]",
                JiraMarkupConverter.markdownToJira("@jsmith"));
    }

    @Test
    public void mdToJira_blockquote() {
        assertEquals("bq. This is a quote",
                JiraMarkupConverter.markdownToJira("> This is a quote"));
    }

    @Test
    public void mdToJira_codeBlockProtectsContent() {
        // Bold/italic inside code blocks should NOT be converted
        String md = "```\n**not jira bold**\n*not jira italic*\n```";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("**not jira bold**"));
        assertTrue(jira.contains("*not jira italic*"));
    }

    @Test
    public void mdToJira_inlineCodeProtectsContent() {
        String md = "Use `**not bold**` in code";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("{{**not bold**}}"));
    }

    // ── Round-trip tests ────────────────────────────────────────────────

    @Test
    public void roundTrip_headersPreserved() {
        // Markdown → Jira → Markdown should preserve headers
        String md = "## Section Title";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertEquals("h2. Section Title", jira);
        String back = JiraMarkupConverter.jiraToMarkdown(jira);
        assertEquals("## Section Title", back);
    }

    @Test
    public void roundTrip_codeBlockPreserved() {
        String md = "```java\npublic class Foo {}\n```";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("{code:java}"));
        String back = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(back.contains("```java"));
        assertTrue(back.contains("public class Foo {}"));
    }

    @Test
    public void roundTrip_linkPreserved() {
        String md = "[Google](https://google.com)";
        String jira = JiraMarkupConverter.markdownToJira(md);
        assertEquals("[Google|https://google.com]", jira);
        String back = JiraMarkupConverter.jiraToMarkdown(jira);
        assertEquals("[Google](https://google.com)", back);
    }

    // ── Multi-element document test ─────────────────────────────────────

    @Test
    public void jiraToMd_complexDocument() {
        String jira = "h2. Overview\n\n"
                + "This is *bold* and _italic_ text.\n\n"
                + "* Item 1\n* Item 2\n\n"
                + "{code:java}\npublic void run() {}\n{code}\n\n"
                + "[More info|https://example.com]";

        String md = JiraMarkupConverter.jiraToMarkdown(jira);
        assertTrue(md.contains("## Overview"));
        assertTrue(md.contains("**bold**"));
        assertTrue(md.contains("*italic*"));
        assertTrue(md.contains("- Item 1"));
        assertTrue(md.contains("```java"));
        assertTrue(md.contains("[More info](https://example.com)"));
    }

    @Test
    public void mdToJira_complexDocument() {
        String md = "## Overview\n\n"
                + "This is **bold** and *italic* text.\n\n"
                + "- Item 1\n- Item 2\n\n"
                + "```java\npublic void run() {}\n```\n\n"
                + "[More info](https://example.com)";

        String jira = JiraMarkupConverter.markdownToJira(md);
        assertTrue(jira.contains("h2. Overview"));
        assertTrue(jira.contains("*bold*"));
        assertTrue(jira.contains("_italic_"));
        assertTrue(jira.contains("* Item 1"));
        assertTrue(jira.contains("{code:java}"));
        assertTrue(jira.contains("[More info|https://example.com]"));
    }
}
