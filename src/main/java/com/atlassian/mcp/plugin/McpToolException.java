package com.atlassian.mcp.plugin;

public class McpToolException extends Exception {
    public McpToolException(String message) {
        super(message);
    }
    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
