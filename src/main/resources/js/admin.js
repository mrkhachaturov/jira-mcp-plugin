(function ($) {
    var url = AJS.contextPath() + "/rest/mcp-admin/1.0/";

    $(function () {
        $.ajax({ url: url, dataType: "json", headers: { "X-Atlassian-Token": "no-check" } }).done(function (config) {
            $("#enabled").prop("checked", config.enabled);
            $("#allowedUsers").val(config.allowedUsers || "");
            $("#disabledTools").val(config.disabledTools || "");
            $("#readOnlyMode").prop("checked", config.readOnlyMode);
            $("#jiraBaseUrl").val(config.jiraBaseUrl || "");
        });

        $("#mcp-admin-form").on("submit", function (e) {
            e.preventDefault();
            $.ajax({
                url: url,
                type: "PUT",
                contentType: "application/json",
                headers: { "X-Atlassian-Token": "no-check" },
                data: JSON.stringify({
                    enabled: $("#enabled").is(":checked"),
                    allowedUsers: $("#allowedUsers").val(),
                    disabledTools: $("#disabledTools").val(),
                    readOnlyMode: $("#readOnlyMode").is(":checked"),
                    jiraBaseUrl: $("#jiraBaseUrl").val()
                }),
                processData: false
            }).done(function () {
                AJS.flag({ type: "success", title: "Configuration saved", close: "auto" });
            }).fail(function () {
                AJS.flag({ type: "error", title: "Failed to save configuration", close: "auto" });
            });
        });
    });
})(AJS.$ || jQuery);
