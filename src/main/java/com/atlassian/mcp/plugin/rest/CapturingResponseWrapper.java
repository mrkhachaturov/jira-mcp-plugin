package com.atlassian.mcp.plugin.rest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Captures the {@code MCP-Session-Id} header (and final status code) as the SDK
 * writes them, so {@link SessionBindingFilter} can record the binding
 * post-initialize without consuming the stream.
 */
public final class CapturingResponseWrapper extends HttpServletResponseWrapper {
    private String mcpSessionId;
    private int statusCode = HttpServletResponse.SC_OK;

    public CapturingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setHeader(String name, String value) {
        if ("MCP-Session-Id".equalsIgnoreCase(name)) {
            this.mcpSessionId = value;
        }
        super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        if ("MCP-Session-Id".equalsIgnoreCase(name)) {
            this.mcpSessionId = value;
        }
        super.addHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        this.statusCode = sc;
        super.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws java.io.IOException {
        this.statusCode = sc;
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws java.io.IOException {
        this.statusCode = sc;
        super.sendError(sc, msg);
    }

    @Override
    public int getStatus() {
        return statusCode;
    }

    public String capturedSessionId() {
        return mcpSessionId;
    }
}
