(function ($) {
    var url = AJS.contextPath() + "/rest/mcp-admin/1.0/";
    var headers = { "X-Atlassian-Token": "no-check" };
    var allToolsMeta = [];
    var disabledSet = new Set();

    // ==================== TOOLS ====================

    function renderTools() {
        var filter = ($("#mcp-tool-filter").val() || "").toLowerCase();
        var $list = $("#mcp-tools-list").empty();

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

    // ==================== USER PICKER ====================

    function initUserPicker(existingKeys) {
        var $picker = $("#mcp-allowed-users");

        if (existingKeys && existingKeys.length > 0) {
            var loaded = 0;
            existingKeys.forEach(function (key) {
                $.ajax({
                    url: AJS.contextPath() + "/rest/api/2/user?key=" + encodeURIComponent(key),
                    dataType: "json", headers: headers
                }).done(function (u) {
                    $picker.append($("<option>").val(u.name).text(u.displayName).attr("selected", "selected").data("userkey", u.key));
                }).fail(function () {
                    $picker.append($("<option>").val(key).text(key).attr("selected", "selected").data("userkey", key));
                }).always(function () {
                    loaded++;
                    if (loaded === existingKeys.length) createMultiSelect($picker);
                });
            });
        } else {
            createMultiSelect($picker);
        }
    }

    function createMultiSelect($picker) {
        try {
            new AJS.MultiSelect({
                element: $picker,
                itemAttrDisplayed: "label",
                showDropdownButton: false,
                ajaxOptions: {
                    url: AJS.contextPath() + "/rest/api/2/user/search",
                    query: true,
                    data: { maxResults: 10 },
                    formatResponse: function (data) {
                        var ret = [];
                        $(data).each(function (i, u) {
                            ret.push(new AJS.ItemDescriptor({
                                value: u.name,
                                label: u.displayName,
                                html: u.displayName + " (" + u.name + ")"
                            }));
                        });
                        return ret;
                    }
                }
            });
        } catch (e) {
            console.warn("AJS.MultiSelect not available:", e);
        }
    }

    function getSelectedUserKeys() {
        var keys = [];
        $("#mcp-allowed-users option:selected").each(function () {
            var key = $(this).data("userkey") || $(this).val();
            if (key) keys.push(key);
        });
        return keys.join(",");
    }

    function esc(s) { return s ? $("<span>").text(s).html() : ""; }

    // ==================== INIT ====================

    $(function () {
        $.ajax({ url: url, dataType: "json", headers: headers }).done(function (config) {
            if (config.enabled) document.getElementById("enabled").checked = true;
            if (config.readOnlyMode) document.getElementById("readOnlyMode").checked = true;
            $("#jiraBaseUrl").val(config.jiraBaseUrl || "");

            allToolsMeta = config.allTools || [];
            var ds = config.disabledTools || "";
            if (ds) ds.split(",").forEach(function (t) { t = t.trim(); if (t) disabledSet.add(t); });
            renderTools();

            var us = config.allowedUsers || "";
            var keys = us ? us.split(",").map(function (k) { return k.trim(); }).filter(Boolean) : [];
            initUserPicker(keys);
        });

        // Tool filter
        $("#mcp-tool-filter").on("input", renderTools);

        // Tool click-to-toggle
        $(document).on("click", ".mcp-tool-row", function () {
            var name = $(this).data("name");
            if (disabledSet.has(name)) {
                disabledSet.delete(name);
            } else {
                disabledSet.add(name);
            }
            renderTools();
        });

        // Save
        $("#mcp-admin-form").on("submit", function (e) {
            e.preventDefault();
            $.ajax({
                url: url, type: "PUT", contentType: "application/json", headers: headers,
                data: JSON.stringify({
                    enabled: document.getElementById("enabled").checked,
                    allowedUsers: getSelectedUserKeys(),
                    disabledTools: Array.from(disabledSet).sort().join(","),
                    readOnlyMode: document.getElementById("readOnlyMode").checked,
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
