# Response Trimming

`ResponseTrimmer` runs on all `JiraRestClient` responses to reduce payload
size for AI agents.

**Stripped recursively:** `avatarUrls`, `iconUrl`, `expand`, `groups`,
`applicationRoles`, avatar size keys (`48x48`, `32x32`, etc.).

**Note:** `self` links are **kept** for issue link types; `JiraUser`
extraction converts the 48x48 avatar URL to `avatar_url`.

**Stripped at top level:** `renderedFields`, `names`, `schema`, `editmeta`,
`versionedRepresentations`, `operations`.

**Field renames:** `issuetype` → `issue_type`, `fixVersions` → `fix_versions`.
