package com.atlassian.mcp.plugin.admin;

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserProfile user = userManager.getRemoteUser(req);
        if (user == null) {
            resp.sendRedirect(loginUriProvider.getLoginUri(getUri(req)).toASCIIString());
            return;
        }
        if (!userManager.isSystemAdmin(user.getUserKey())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "System admin access required");
            return;
        }
        resp.setContentType("text/html;charset=utf-8");
        renderer.render("templates/admin.vm", resp.getWriter());
    }

    private URI getUri(HttpServletRequest req) {
        StringBuffer buf = req.getRequestURL();
        if (req.getQueryString() != null) {
            buf.append("?").append(req.getQueryString());
        }
        return URI.create(buf.toString());
    }
}
