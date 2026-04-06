(function ($) {
    var url = AJS.contextPath() + "/rest/mcp-admin/1.0/";
    var headers = { "X-Atlassian-Token": "no-check" };
    var allToolsMeta = [];
    var disabledSet = new Set();
    var allowedUsers = [];
    var allowedGroups = [];

    // ==================== TOOLS ====================

    function renderTools() {
        var filter = ($("#mcp-tool-filter").val() || "").toLowerCase();
        var $list = $("#mcp-tools-list").empty();

        if (allToolsMeta.length === 0) {
            $list.append('<div style="padding:12px; color:#6b778c;">No tools registered.</div>');
            return;
        }

        allToolsMeta.forEach(function (tool) {
            if (filter && tool.name.toLowerCase().indexOf(filter) < 0) return;
            var isDisabled = disabledSet.has(tool.name);
            var badge = tool.isWrite ? '<span class="aui-lozenge aui-lozenge-moved" style="font-size:10px; margin-left:6px;">write</span>' : '';
            var cls = "mcp-tool-row" + (isDisabled ? " mcp-tool-disabled" : "");
            $list.append(
                '<div class="' + cls + '" data-name="' + esc(tool.name) + '">'
                + '<span class="mcp-tool-toggle">' + (isDisabled ? '&#x2717;' : '&#x2713;') + '</span> '
                + '<span class="mcp-tool-label">' + esc(tool.name) + '</span>'
                + badge
                + '<span class="mcp-tool-desc">' + esc(tool.description) + '</span>'
                + '</div>'
            );
        });
    }

    // ==================== TAG PICKER (reusable for users & groups) ====================

    function renderTags(items, containerId, removeClass) {
        var $c = $("#" + containerId).empty();
        items.forEach(function (item, i) {
            $c.append(
                '<span class="mcp-tag">'
                + esc(item.label || item.value)
                + ' <a href="#" class="' + removeClass + '" data-index="' + i + '">&times;</a>'
                + '</span>'
            );
        });
    }

    function setupPicker(cfg) {
        var timer = null;
        $("#" + cfg.inputId).on("input", function () {
            clearTimeout(timer);
            var q = $(this).val().trim();
            timer = setTimeout(function () {
                if (!q || q.length < 2) { $("#" + cfg.sugId).hide().empty(); return; }
                $.ajax({
                    url: AJS.contextPath() + cfg.searchUrl + encodeURIComponent(q),
                    dataType: "json", headers: headers
                }).done(function (data) {
                    var results = cfg.extractResults(data);
                    var $sug = $("#" + cfg.sugId).empty();
                    var existing = {};
                    cfg.items().forEach(function (it) { existing[it.value] = true; });
                    results.forEach(function (r) {
                        if (existing[r.value]) return;
                        $sug.append(
                            '<div class="mcp-suggestion" data-value="' + esc(r.value)
                            + '" data-label="' + esc(r.label) + '">'
                            + esc(r.label) + (r.extra ? ' <span style="color:#6b778c;">(' + esc(r.extra) + ')</span>' : '')
                            + '</div>'
                        );
                    });
                    $sug.toggle($sug.children().length > 0);
                });
            }, 300);
        });

        $(document).on("click", "#" + cfg.sugId + " .mcp-suggestion", function () {
            cfg.items().push({ value: $(this).data("value"), label: $(this).data("label") });
            renderTags(cfg.items(), cfg.tagsId, cfg.removeClass);
            $("#" + cfg.inputId).val("");
            $("#" + cfg.sugId).hide().empty();
        });

        $(document).on("click", "." + cfg.removeClass, function (e) {
            e.preventDefault();
            cfg.items().splice($(this).data("index"), 1);
            renderTags(cfg.items(), cfg.tagsId, cfg.removeClass);
        });

        $("#" + cfg.inputId).on("blur", function () {
            setTimeout(function () { $("#" + cfg.sugId).hide(); }, 200);
        });
    }

    function esc(s) { return s ? $("<span>").text(s).html() : ""; }

    // ==================== INIT ====================

    $(function () {
        // Setup group picker
        setupPicker({
            inputId: "mcp-group-input",
            sugId: "mcp-group-suggestions",
            tagsId: "mcp-group-tags",
            removeClass: "mcp-remove-group",
            searchUrl: "/rest/api/2/groups/picker?maxResults=8&query=",
            items: function () { return allowedGroups; },
            extractResults: function (data) {
                return (data.groups || []).map(function (g) {
                    return { value: g.name, label: g.name };
                });
            }
        });

        // Setup user picker
        setupPicker({
            inputId: "mcp-user-input",
            sugId: "mcp-user-suggestions",
            tagsId: "mcp-user-tags",
            removeClass: "mcp-remove-user",
            searchUrl: "/rest/api/2/user/search?maxResults=8&username=",
            items: function () { return allowedUsers; },
            extractResults: function (data) {
                return (data || []).map(function (u) {
                    return { value: u.name, label: u.displayName, extra: u.name };
                });
            }
        });

        // Load config
        $.ajax({ url: url, dataType: "json", headers: headers }).done(function (config) {
            if (config.enabled) document.getElementById("enabled").checked = true;
            if (config.readOnlyMode) document.getElementById("readOnlyMode").checked = true;
            $("#jiraBaseUrl").val(config.jiraBaseUrl || "");

            allToolsMeta = config.allTools || [];
            var ds = config.disabledTools || "";
            if (ds) ds.split(",").forEach(function (t) { t = t.trim(); if (t) disabledSet.add(t); });
            renderTools();

            // Load groups
            var gs = config.allowedGroups || "";
            if (gs) {
                gs.split(",").forEach(function (g) {
                    g = g.trim();
                    if (g) allowedGroups.push({ value: g, label: g });
                });
                renderTags(allowedGroups, "mcp-group-tags", "mcp-remove-group");
            }

            // Load users
            var us = config.allowedUsers || "";
            if (us) {
                var keys = us.split(",").map(function (k) { return k.trim(); }).filter(Boolean);
                var loaded = 0;
                keys.forEach(function (key) {
                    $.ajax({
                        url: AJS.contextPath() + "/rest/api/2/user?username=" + encodeURIComponent(key),
                        dataType: "json", headers: headers
                    }).done(function (u) {
                        allowedUsers.push({ value: u.name, label: u.displayName });
                    }).fail(function () {
                        allowedUsers.push({ value: key, label: key });
                    }).always(function () {
                        loaded++;
                        if (loaded === keys.length) renderTags(allowedUsers, "mcp-user-tags", "mcp-remove-user");
                    });
                });
            }
            // OAuth
            $("#oauthClientId").val(config.oauthClientId || "");
            if (config.oauthClientSecretSet) {
                $("#oauth-secret-status").text("Secret is configured.");
            } else {
                $("#oauth-secret-status").text("No secret configured yet.");
            }
            if (config.oauthEnabled) {
                $("#oauth-status-group").show();
                $("#oauth-status").html('<span class="aui-lozenge aui-lozenge-success">Active</span>');
            }
            var baseUrl = window.location.origin + AJS.contextPath();
            var mcpUrl = baseUrl + "/rest/mcp/1.0/";
            $("#oauth-callback-url").text(mcpUrl + "oauth/callback");
            $("#oauth-mcp-config").text(JSON.stringify({
                "mcpServers": {
                    "jira": { "type": "http", "url": mcpUrl }
                }
            }, null, 2));
        }).fail(function (xhr) {
            $("#mcp-tools-list").html('<div style="padding:12px; color:#de350b;">Failed to load config (HTTP ' + xhr.status + ').</div>');
        });

        // Tool filter & toggle
        $("#mcp-tool-filter").on("input", renderTools);
        $(document).on("click", ".mcp-tool-row", function () {
            var name = $(this).data("name");
            if (disabledSet.has(name)) { disabledSet.delete(name); } else { disabledSet.add(name); }
            renderTools();
        });

        // Save
        $("#mcp-admin-form").on("submit", function (e) {
            e.preventDefault();
            $.ajax({
                url: url, type: "PUT", contentType: "application/json", headers: headers,
                data: JSON.stringify({
                    enabled: document.getElementById("enabled").checked,
                    allowedGroups: allowedGroups.map(function (g) { return g.value; }).join(","),
                    allowedUsers: allowedUsers.map(function (u) { return u.value; }).join(","),
                    disabledTools: Array.from(disabledSet).sort().join(","),
                    readOnlyMode: document.getElementById("readOnlyMode").checked,
                    jiraBaseUrl: $("#jiraBaseUrl").val(),
                    oauthClientId: $("#oauthClientId").val(),
                    oauthClientSecret: $("#oauthClientSecret").val()
                }),
                processData: false
            }).done(function () {
                AJS.flag({ type: "success", title: "Configuration saved", close: "auto" });
            }).fail(function (xhr) {
                AJS.flag({ type: "error", title: "Failed to save: " + xhr.status, close: "auto" });
            });
        });
    });
})(AJS.$ || jQuery);
