(function ($) {
    var url = AJS.contextPath() + "/rest/mcp-admin/1.0/";
    var headers = { "X-Atlassian-Token": "no-check" };
    var allToolsMeta = [];
    var disabledSet = new Set();
    var allowedUsers = []; // [{key, displayName}]

    // ==================== TOOL PICKER ====================

    function renderToolLists() {
        var enabledFilter = ($("#mcp-filter-enabled").val() || "").toLowerCase();
        var disabledFilter = ($("#mcp-filter-disabled").val() || "").toLowerCase();
        var $enabled = $("#mcp-enabled-tools").empty();
        var $disabled = $("#mcp-disabled-tools").empty();

        allToolsMeta.forEach(function (tool) {
            var badge = tool.isWrite ? ' <span class="aui-lozenge aui-lozenge-moved">write</span>' : '';
            var desc = tool.description ? '<div class="mcp-tool-desc">' + escapeHtml(tool.description) + '</div>' : '';
            var html = '<div class="mcp-tool-item" data-name="' + escapeHtml(tool.name) + '">'
                + '<span class="mcp-tool-name">' + escapeHtml(tool.name) + '</span>' + badge + desc + '</div>';

            if (disabledSet.has(tool.name)) {
                if (!disabledFilter || tool.name.toLowerCase().indexOf(disabledFilter) >= 0) {
                    $disabled.append(html);
                }
            } else {
                if (!enabledFilter || tool.name.toLowerCase().indexOf(enabledFilter) >= 0) {
                    $enabled.append(html);
                }
            }
        });
    }

    // ==================== USER PICKER ====================

    function renderUserTags() {
        var $container = $("#mcp-allowed-users").empty();
        if (allowedUsers.length === 0) {
            $container.append('<span class="mcp-user-empty">All authenticated users (no restrictions)</span>');
            return;
        }
        allowedUsers.forEach(function (u, i) {
            var tag = '<span class="mcp-user-tag">'
                + '<span class="mcp-user-tag-name">' + escapeHtml(u.displayName || u.key) + '</span>'
                + '<button type="button" class="mcp-user-remove" data-index="' + i + '">&times;</button>'
                + '</span>';
            $container.append(tag);
        });
    }

    var searchTimeout = null;
    function searchUsers(query) {
        if (!query || query.length < 2) {
            $("#mcp-user-suggestions").hide();
            return;
        }
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(function () {
            $.ajax({
                url: AJS.contextPath() + "/rest/api/2/user/search?username=" + encodeURIComponent(query) + "&maxResults=10",
                dataType: "json",
                headers: headers
            }).done(function (users) {
                var $sug = $("#mcp-user-suggestions").empty();
                if (!users || users.length === 0) {
                    $sug.append('<div class="mcp-user-sug-item mcp-user-sug-empty">No users found</div>');
                } else {
                    users.forEach(function (u) {
                        var already = allowedUsers.some(function (a) { return a.key === u.key; });
                        if (!already) {
                            var avatar = u.avatarUrls && u.avatarUrls["16x16"] ? '<img src="' + u.avatarUrls["16x16"] + '" class="mcp-user-avatar"> ' : '';
                            $sug.append('<div class="mcp-user-sug-item" data-key="' + escapeHtml(u.key) + '" data-name="' + escapeHtml(u.displayName) + '">'
                                + avatar + escapeHtml(u.displayName) + ' <span class="mcp-user-sug-username">(' + escapeHtml(u.name) + ')</span></div>');
                        }
                    });
                }
                $sug.show();
            });
        }, 300);
    }

    // ==================== HELPERS ====================

    function escapeHtml(str) {
        if (!str) return "";
        return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function getDisabledString() {
        return Array.from(disabledSet).sort().join(",");
    }

    function getAllowedUsersString() {
        return allowedUsers.map(function (u) { return u.key; }).join(",");
    }

    // ==================== INIT ====================

    $(function () {
        // Load config
        $.ajax({ url: url, dataType: "json", headers: headers }).done(function (config) {
            if (config.enabled) $("#enabled").attr("checked", "checked");
            if (config.readOnlyMode) $("#readOnlyMode").attr("checked", "checked");
            $("#jiraBaseUrl").val(config.jiraBaseUrl || "");

            // Tools
            allToolsMeta = config.allTools || [];
            var disabledStr = config.disabledTools || "";
            if (disabledStr) {
                disabledStr.split(",").forEach(function (t) {
                    var trimmed = t.trim();
                    if (trimmed) disabledSet.add(trimmed);
                });
            }
            renderToolLists();

            // Users — we have keys, need to resolve display names
            var usersStr = config.allowedUsers || "";
            if (usersStr) {
                var keys = usersStr.split(",").map(function (k) { return k.trim(); }).filter(Boolean);
                var resolved = 0;
                keys.forEach(function (key) {
                    $.ajax({
                        url: AJS.contextPath() + "/rest/api/2/user?key=" + encodeURIComponent(key),
                        dataType: "json",
                        headers: headers
                    }).done(function (u) {
                        allowedUsers.push({ key: u.key, displayName: u.displayName });
                    }).fail(function () {
                        allowedUsers.push({ key: key, displayName: key });
                    }).always(function () {
                        resolved++;
                        if (resolved === keys.length) renderUserTags();
                    });
                });
            } else {
                renderUserTags();
            }
        });

        // Tool filter
        $("#mcp-filter-enabled, #mcp-filter-disabled").on("input", renderToolLists);

        // Tool select
        $(document).on("click", ".mcp-tool-item", function () {
            $(this).toggleClass("selected");
        });

        // Tool disable/enable buttons
        $("#mcp-disable-tool").on("click", function () {
            $("#mcp-enabled-tools .mcp-tool-item.selected").each(function () {
                disabledSet.add($(this).data("name"));
            });
            renderToolLists();
        });
        $("#mcp-enable-tool").on("click", function () {
            $("#mcp-disabled-tools .mcp-tool-item.selected").each(function () {
                disabledSet.delete($(this).data("name"));
            });
            renderToolLists();
        });

        // User search
        $("#mcp-user-search").on("input", function () {
            searchUsers($(this).val());
        });

        // User suggestion click
        $(document).on("click", ".mcp-user-sug-item[data-key]", function () {
            allowedUsers.push({ key: $(this).data("key"), displayName: $(this).data("name") });
            renderUserTags();
            $("#mcp-user-search").val("");
            $("#mcp-user-suggestions").hide();
        });

        // User remove
        $(document).on("click", ".mcp-user-remove", function () {
            var idx = parseInt($(this).data("index"), 10);
            allowedUsers.splice(idx, 1);
            renderUserTags();
        });

        // Hide suggestions on click outside
        $(document).on("click", function (e) {
            if (!$(e.target).closest(".mcp-user-picker").length) {
                $("#mcp-user-suggestions").hide();
            }
        });

        // Save
        $("#mcp-admin-form").on("submit", function (e) {
            e.preventDefault();
            $.ajax({
                url: url,
                type: "PUT",
                contentType: "application/json",
                headers: headers,
                data: JSON.stringify({
                    enabled: $("#enabled").is("[checked]"),
                    allowedUsers: getAllowedUsersString(),
                    disabledTools: getDisabledString(),
                    readOnlyMode: $("#readOnlyMode").is("[checked]"),
                    jiraBaseUrl: $("#jiraBaseUrl").val()
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
