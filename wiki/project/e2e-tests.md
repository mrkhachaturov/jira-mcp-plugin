# E2E Tests

14 tests in `src/test/java/.../e2e/McpEndpointE2ETest.java`. Requires env
vars from `.credentials/jira.env` (auto-loaded by mise). Tests skip
automatically when `JIRA_URL`/`JIRA_PAT_RKADMIN` are not set.

| Category          | What                                                                                                                                                                                 |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Protocol          | initialize, ping, invalid method                                                                                                                                                     |
| Tools list        | count, schema validation                                                                                                                                                             |
| Read tools        | get_all_projects, get_user_profile, search_fields, get_link_types, search, get_agile_boards                                                                                          |
| Response trimming | no self, no avatarUrls, no iconUrl                                                                                                                                                   |
| Issue CRUD        | create → get → comment → update → delete lifecycle                                                                                                                                   |
| Service desk      | get_service_desk_for_project                                                                                                                                                         |
| Error handling    | missing param, invalid key, unknown tool                                                                                                                                             |
| Access control    | CEO user via group allowlist                                                                                                                                                         |
| Security          | GET/DELETE without auth → 401, oversized body → 413, session-user binding → 403, trailing slash redirect → 307, OAuth well-known endpoints, DCR + PKCE enforcement, security headers |
| OAuth refresh     | metadata advertises refresh_token grant, error paths (missing token, bogus token, unsupported grant)                                                                                 |
